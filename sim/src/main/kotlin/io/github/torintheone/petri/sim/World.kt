package io.github.torintheone.petri.sim

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** Everything the diagnostics card shows about one cell. */
data class CellInfo(
    val uid: Int,
    val type: CellType,
    val mode: Int,
    val mass: Float,
    val ageHours: Float,
    val nitrates: Float,
    val s1: Float,
    val s2: Float,
    val s3: Float,
    val s4: Float,
    val bonds: Int,
    val genomeId: Long,
    val parentGenomeId: Long,
    val x: Float,
    val y: Float,
)

/**
 * The whole state of one dish and the rules that move it forward (SPEC «Мир», «Физика и обмен веществ»).
 * Not thread-safe: only the simulation thread may touch it (see [SimulationRunner.post]).
 * Deterministic: the same seed and the same actions give the same dish.
 */
class World(
    settings: SubstrateSettings = SubstrateSettings(),
    seed: Long = 1L,
) {
    var settings: SubstrateSettings = settings.sanitized()
        set(value) {
            field = value.sanitized()
        }

    val rng = Rng(seed)
    val cells = Cells(SimConfig.CELL_CAPACITY)
    val bonds = Bonds(SimConfig.BOND_CAPACITY)
    val food = Food(SimConfig.FOOD_CAPACITY)
    val genomes = GenomeRegistry()
    val history = PopulationHistory(SimConfig.HISTORY_LENGTH)

    /** Steps simulated since the dish was created or sterilized. Counting steps keeps the age exact. */
    var stepCount: Long = 0L
        internal set

    /** Where the light source stands on the rim, radians; it starts at the top. */
    var lightAngle: Float = (PI / 2).toFloat()
        internal set

    internal var nextUid = 1
    internal var nextOrganism = 1

    /** Food spawn events owed but not placed yet (the rate rarely gives whole events per step). */
    internal var foodDebt = 0f

    /** Tweezers: the dragged cell (0 for none) and where the finger is. */
    internal var draggedUid = 0
    internal var dragX = 0f
    internal var dragY = 0f

    /**
     * Growth preview (SPEC «Геном»): when ≥ 0, every cell gains this many ng per hour, pays no upkeep
     * and never dies, as if food and light were endless.
     */
    var previewGrowth: Float = -1f

    /** Growth preview: the organism stops dividing at this many cells; 0 leaves the limit to the settings. */
    var previewCellLimit: Int = 0

    /** Most cells the dish may hold now. */
    internal val cellLimit: Int get() = if (previewCellLimit > 0) previewCellLimit else settings.maxCells

    internal val physics = Physics(this)
    internal val behaviors = Behaviors(this)
    private val metabolism = Metabolism(this)
    private val divisions = Divisions(this)
    internal val foodGrid = SpatialGrid()

    val ageHours: Double get() = stepCount * SimConfig.STEP_HOURS
    val cellCount: Int get() = cells.count
    val foodCount: Int get() = food.count
    val speciesCount: Int get() = genomes.liveCount
    val dishRadius: Float get() = settings.dishRadiusUm

    // ---- Simulation ----

    /** Advances the world by one game minute. */
    fun step() {
        stepCount++
        behaviors.prepare()
        physics.run()
        behaviors.afterMovement()
        metabolism.run()
        divisions.run()
        removeDead()
        metabolism.updateFood()
        advanceLight()
        if (stepCount % HISTORY_STEPS == 0L) history.add(ageHours.toFloat(), cells.count, food.count, genomes.liveCount)
    }

    /** Light at a point, 0..1 (SPEC «Модель света»). */
    fun lightAt(px: Float, py: Float): Float {
        val s = settings
        if (s.lightBrightness <= 0f) return 0f
        if (s.lightWidth >= 1f) return s.lightBrightness
        val sigma = lightSigma()
        val dx = px - dishRadius * cos(lightAngle)
        val dy = py - dishRadius * sin(lightAngle)
        return s.lightBrightness * exp(-(dx * dx + dy * dy) / (2f * sigma * sigma))
    }

    /** Beam width σ in µm; grows with «Ширина света». */
    fun lightSigma(): Float {
        val w = settings.lightWidth
        return dishRadius * (SimConfig.LIGHT_SIGMA_MIN + (SimConfig.LIGHT_SIGMA_MAX - SimConfig.LIGHT_SIGMA_MIN) * w * w)
    }

    /** Density of the medium at height y: it grows towards the bottom with «Градиент плотности». */
    internal fun mediumDensity(py: Float): Float =
        settings.mediumDensity + settings.densityGradient * (dishRadius - py) / (2f * dishRadius)

    private fun advanceLight() {
        val turnsPerStep = settings.lightRotation * SimConfig.LIGHT_MAX_TURNS_PER_HOUR * SimConfig.STEP_HOURS_F
        if (turnsPerStep == 0f) return
        lightAngle += turnsPerStep * TWO_PI
        if (lightAngle > TWO_PI) lightAngle -= TWO_PI
    }

    // ---- Actions from the microscope tools ----

    /** Plants a cell of [genome] in its initial mode; returns its uid, or 0 when the dish is full or the point is outside. */
    fun addCell(
        genome: Genome,
        px: Float,
        py: Float,
        angle: Float = rng.nextFloat(0f, TWO_PI),
        mass: Float = SimConfig.SYNTHESIZED_MASS,
        mode: Int = genome.initialMode,
    ): Int {
        if (cells.count >= cellLimit || px * px + py * py > dishRadius * dishRadius) return 0
        val slot = genomes.slotFor(genome)
        val i = spawnCell(slot, mode, px, py, angle, mass, nextOrganism++, mirrored = false, nitrates = 1f)
        return if (i >= 0) cells.uid[i] else 0
    }

    /** Puts a food particle at a point (the «подкормка» tool on empty medium). */
    fun addFood(px: Float, py: Float, mass: Float = foodParticleMass(), coated: Boolean = false): Boolean {
        if (food.count >= settings.maxFood || px * px + py * py > dishRadius * dishRadius) return false
        return food.add(px, py, mass, coated) >= 0
    }

    /** Adds mass to a cell (the «подкормка» tool on a cell). */
    fun feedCell(uid: Int, amount: Float) {
        val i = indexOfUid(uid)
        if (i < 0) return
        cells.mass[i] = (cells.mass[i] + amount).coerceAtMost(cells.cellType(i).maxMass)
    }

    fun removeCell(uid: Int) {
        val i = indexOfUid(uid)
        if (i >= 0) removeCellAt(i)
    }

    /** The cell under a point, with a little slack for fingers; 0 when there is none. */
    fun cellAt(px: Float, py: Float, slack: Float = 0f): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (i in 0 until cells.count) {
            val dx = cells.x[i] - px
            val dy = cells.y[i] - py
            val d = sqrt(dx * dx + dy * dy) - radiusOf(cells.mass[i])
            if (d < slack && d < bestDistance) {
                best = i
                bestDistance = d
            }
        }
        return if (best >= 0) cells.uid[best] else 0
    }

    fun indexOfUid(uid: Int): Int {
        if (uid == 0) return -1
        for (i in 0 until cells.count) if (cells.uid[i] == uid) return i
        return -1
    }

    fun cellInfo(uid: Int): CellInfo? {
        val i = indexOfUid(uid)
        if (i < 0) return null
        val s = i * Cells.SIGNALS
        return CellInfo(
            uid = uid,
            type = cells.cellType(i),
            mode = cells.mode[i],
            mass = cells.mass[i],
            ageHours = cells.age[i],
            nitrates = cells.nitrates[i],
            s1 = cells.signals[s],
            s2 = cells.signals[s + 1],
            s3 = cells.signals[s + 2],
            s4 = cells.signals[s + 3],
            bonds = cells.bondCount[i],
            genomeId = genomes.id(cells.genome[i]),
            parentGenomeId = genomes.parentId(cells.genome[i]),
            x = cells.x[i],
            y = cells.y[i],
        )
    }

    fun genomeOfCell(uid: Int): Genome? {
        val i = indexOfUid(uid)
        return if (i < 0) null else genomes[cells.genome[i]]
    }

    /** Tweezers: pull the cell towards the finger until [stopDragging]. */
    fun drag(uid: Int, toX: Float, toY: Float) {
        draggedUid = uid
        dragX = toX
        dragY = toY
    }

    /**
     * Moves the tweezers' target while a cell is held. With time frozen physics does not run,
     * so [carry] puts the cell straight under the finger.
     */
    fun dragTo(toX: Float, toY: Float, carry: Boolean) {
        if (draggedUid == 0) return
        dragX = toX
        dragY = toY
        if (carry) moveCellTo(draggedUid, toX, toY)
    }

    val isDragging: Boolean get() = draggedUid != 0

    fun stopDragging() {
        draggedUid = 0
    }

    /** Puts a cell at a point and stops it (tweezers while the dish is frozen). */
    fun moveCellTo(uid: Int, toX: Float, toY: Float) {
        val i = indexOfUid(uid)
        if (i < 0) return
        val limit = dishRadius - radiusOf(cells.mass[i])
        val d2 = toX * toX + toY * toY
        val scale = if (limit > 0f && d2 > limit * limit) limit / sqrt(d2) else 1f
        cells.x[i] = toX * scale
        cells.y[i] = toY * scale
        cells.vx[i] = 0f
        cells.vy[i] = 0f
    }

    /**
     * New settings from the «Среда» tab. When the dish gets smaller, cells and food outside it are moved
     * just inside the new wall, so nothing is crushed by it.
     */
    fun applySettings(newSettings: SubstrateSettings) {
        settings = newSettings
        val dishR = dishRadius
        for (i in 0 until cells.count) {
            val limit = dishR - radiusOf(cells.mass[i])
            val d2 = cells.x[i] * cells.x[i] + cells.y[i] * cells.y[i]
            if (limit > 0f && d2 > limit * limit) {
                val scale = limit / sqrt(d2)
                cells.x[i] *= scale
                cells.y[i] *= scale
            }
        }
        for (k in 0 until food.count) {
            val limit = dishR - food.radius(k)
            val d2 = food.x[k] * food.x[k] + food.y[k] * food.y[k]
            if (limit > 0f && d2 > limit * limit) {
                val scale = limit / sqrt(d2)
                food.x[k] *= scale
                food.y[k] *= scale
            }
        }
    }

    /** Kills every cell and particle and resets the age; settings stay (SPEC «стерилизовать»). */
    fun sterilize() {
        cells.count = 0
        bonds.clear()
        food.count = 0
        genomes.clear()
        history.clear()
        stepCount = 0L
        foodDebt = 0f
        draggedUid = 0
        lightAngle = (PI / 2).toFloat()
    }

    /** Fills a fresh dish: [cellCount] cells of [genome] and food up to [foodFraction] of the limit. */
    fun populate(genome: Genome, cellCount: Int, foodFraction: Float) {
        val r = dishRadius * 0.85f
        repeat(cellCount) {
            val (px, py) = randomPointInDisk(r)
            addCell(genome, px, py)
        }
        val foodTarget = (settings.maxFood * foodFraction).toInt()
        while (food.count < foodTarget) {
            val (px, py) = randomPointInDisk(dishRadius * 0.97f)
            if (!addFood(px, py, coated = rng.chance(settings.foodCoat))) break
        }
    }

    // ---- Internals shared by the systems ----

    internal fun radiusOf(mass: Float): Float = SimConfig.RADIUS_PER_SQRT_NG * sqrt(mass)

    internal fun modeOf(i: Int): Mode = genomes[cells.genome[i]].modes[cells.mode[i]]

    internal fun foodParticleMass(): Float =
        SimConfig.FOOD_MASS_MIN + settings.foodSize * (SimConfig.FOOD_MASS_MAX - SimConfig.FOOD_MASS_MIN)

    internal fun randomPointInDisk(radius: Float): Pair<Float, Float> {
        val r = radius * sqrt(rng.nextFloat())
        val a = rng.nextFloat(0f, TWO_PI)
        return Pair(r * cos(a), r * sin(a))
    }

    /** Creates a cell; returns its slot or −1 when there is no room. */
    internal fun spawnCell(
        genomeSlot: Int,
        mode: Int,
        px: Float,
        py: Float,
        angle: Float,
        mass: Float,
        organism: Int,
        mirrored: Boolean,
        nitrates: Float,
    ): Int {
        if (cells.count >= cells.capacity) return -1
        val i = cells.count++
        cells.uid[i] = nextUid++
        cells.x[i] = px
        cells.y[i] = py
        cells.vx[i] = 0f
        cells.vy[i] = 0f
        cells.angle[i] = angle
        cells.spin[i] = 0f
        cells.mass[i] = mass
        cells.age[i] = 0f
        cells.nitrates[i] = nitrates
        cells.genome[i] = genomeSlot
        genomes.acquire(genomeSlot)
        cells.mode[i] = mode
        cells.type[i] = genomes[genomeSlot].modes[mode].cellType.ordinal
        cells.flags[i] = if (mirrored) Cells.FLAG_MIRRORED else 0
        cells.organism[i] = organism
        cells.bondCount[i] = 0
        cells.radius[i] = radiusOf(mass)
        cells.hardness[i] = 1f
        cells.activity[i] = 0f
        cells.activity2[i] = 0f
        cells.activity3[i] = 0f
        cells.friction[i] = 1f
        java.util.Arrays.fill(cells.signals, i * Cells.SIGNALS, (i + 1) * Cells.SIGNALS, 0f)
        return i
    }

    /** Switches a cell to another mode of its genome (division, stemocytes). */
    internal fun setMode(i: Int, mode: Int) {
        cells.mode[i] = mode
        cells.type[i] = genomes[cells.genome[i]].modes[mode].cellType.ordinal
    }

    /** Gives a cell another genome (mutation, virus, fusion). */
    internal fun setGenome(i: Int, slot: Int) {
        genomes.acquire(slot)
        genomes.release(cells.genome[i])
        cells.genome[i] = slot
        cells.type[i] = genomes[slot].modes[cells.mode[i]].cellType.ordinal
    }

    /** Adds a bond; returns its id or −1 when a cell already has [Mode.MAX_BONDS] bonds or the pool is full. */
    internal fun addBond(i: Int, j: Int, anchorI: Float, anchorJ: Float, rest: Float, stiffness: Float, kind: Int): Int {
        if (cells.bondCount[i] >= Mode.MAX_BONDS || bonds.isFull) return -1
        if (j != Bonds.WALL && cells.bondCount[j] >= Mode.MAX_BONDS) return -1
        val id = bonds.allocate()
        bonds.a[id] = i
        bonds.b[id] = j
        bonds.anchorA[id] = anchorI
        bonds.anchorB[id] = anchorJ
        bonds.rest[id] = rest
        bonds.stiffness[id] = stiffness
        bonds.kind[id] = kind
        attachBond(i, id)
        if (j != Bonds.WALL) attachBond(j, id)
        return id
    }

    internal fun removeBond(id: Int) {
        detachBond(bonds.a[id], id)
        if (bonds.b[id] != Bonds.WALL) detachBond(bonds.b[id], id)
        bonds.release(id)
    }

    /** True when cells i and j share a bond. */
    internal fun bonded(i: Int, j: Int): Boolean {
        val base = i * Mode.MAX_BONDS
        for (k in 0 until cells.bondCount[i]) {
            val id = cells.bondIds[base + k]
            if (bonds.a[id] == j || bonds.b[id] == j) return true
        }
        return false
    }

    internal fun attachBond(i: Int, id: Int) {
        cells.bondIds[i * Mode.MAX_BONDS + cells.bondCount[i]++] = id
    }

    internal fun detachBond(i: Int, id: Int) {
        val base = i * Mode.MAX_BONDS
        val n = cells.bondCount[i]
        for (k in 0 until n) {
            if (cells.bondIds[base + k] == id) {
                cells.bondIds[base + k] = cells.bondIds[base + n - 1]
                cells.bondCount[i] = n - 1
                return
            }
        }
    }

    internal fun kill(i: Int) {
        cells.flags[i] = cells.flags[i] or Cells.FLAG_DEAD
    }

    internal fun isDead(i: Int): Boolean = cells.flags[i] and Cells.FLAG_DEAD != 0

    /** Removes the cell in slot i right away; the last cell moves into the slot. */
    internal fun removeCellAt(i: Int) {
        while (cells.bondCount[i] > 0) removeBond(cells.bondIds[i * Mode.MAX_BONDS + cells.bondCount[i] - 1])
        genomes.release(cells.genome[i])
        val last = cells.count - 1
        if (i != last) {
            cells.copySlot(last, i)
            val base = i * Mode.MAX_BONDS
            for (k in 0 until cells.bondCount[i]) {
                val id = cells.bondIds[base + k]
                if (bonds.a[id] == last) bonds.a[id] = i
                if (bonds.b[id] == last) bonds.b[id] = i
            }
        }
        cells.count = last
    }

    private fun removeDead() {
        var i = 0
        while (i < cells.count) {
            val flags = cells.flags[i]
            if (flags and Cells.FLAG_DEAD == 0) {
                i++
                continue
            }
            if (previewGrowth < 0f && settings.corpsesBecomeFood) {
                val share = if (flags and Cells.FLAG_ALL_TO_FOOD != 0) 1f else SimConfig.CORPSE_FOOD_FRACTION
                val m = cells.mass[i] * share
                if (m > 0f) addFood(cells.x[i], cells.y[i], m)
            }
            removeCellAt(i)
        }
    }

    // ---- Rendering ----

    /** Copies what the renderer and UI need into [out]. */
    fun writeSnapshot(out: WorldSnapshot) {
        out.ageHours = ageHours
        out.dishRadius = dishRadius
        out.cellCount = cells.count
        out.foodCount = food.count
        out.speciesCount = genomes.liveCount
        out.lightAngle = lightAngle
        out.lightBrightness = settings.lightBrightness
        out.lightSigma = if (settings.lightWidth >= 1f) 0f else lightSigma()

        out.ensureCells(cells.count)
        val c = out.cells
        for (i in 0 until cells.count) {
            val mode = modeOf(i)
            val o = i * WorldSnapshot.CELL_FLOATS
            c[o] = cells.x[i]
            c[o + 1] = cells.y[i]
            c[o + 2] = radiusOf(cells.mass[i])
            c[o + 3] = cells.angle[i]
            c[o + 4] = mode.red
            c[o + 5] = mode.green
            c[o + 6] = mode.blue
            c[o + 7] = cells.type[i].toFloat()
            c[o + 8] = cells.activity[i]
            c[o + 9] = cells.age[i]
            c[o + 10] = (cells.flags[i] and WorldSnapshot.VISIBLE_FLAGS).toFloat()
            out.cellUids[i] = cells.uid[i]
        }

        out.ensureBonds(bonds.liveCount)
        var n = 0
        val b = out.bonds
        for (id in 0 until bonds.size) {
            if (!bonds.alive[id]) continue
            val i = bonds.a[id]
            val j = bonds.b[id]
            val o = n * WorldSnapshot.BOND_FLOATS
            b[o] = cells.x[i]
            b[o + 1] = cells.y[i]
            if (j == Bonds.WALL) {
                b[o + 2] = dishRadius * cos(bonds.anchorB[id])
                b[o + 3] = dishRadius * sin(bonds.anchorB[id])
            } else {
                b[o + 2] = cells.x[j]
                b[o + 3] = cells.y[j]
            }
            b[o + 4] = bonds.kind[id].toFloat()
            n++
        }
        out.bondCount = n

        out.ensureFood(food.count)
        val f = out.food
        for (k in 0 until food.count) {
            val o = k * WorldSnapshot.FOOD_FLOATS
            f[o] = food.x[k]
            f[o + 1] = food.y[k]
            f[o + 2] = food.radius(k)
            f[o + 3] = if (food.coated[k]) 1f else 0f
        }
    }

    internal companion object {
        const val PI_F = 3.1415927f
        const val TWO_PI = 6.2831855f
        val HISTORY_STEPS = (SimConfig.HISTORY_INTERVAL_HOURS / SimConfig.STEP_HOURS).roundToLong()

        /** Wraps an angle into (−π, π]. */
        fun wrapPi(a: Float): Float {
            var r = a
            while (r > PI_F) r -= TWO_PI
            while (r <= -PI_F) r += TWO_PI
            return r
        }

        fun direction(dx: Float, dy: Float): Float = atan2(dy, dx)
    }
}
