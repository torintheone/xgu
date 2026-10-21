package io.github.torintheone.petri.sim

import kotlinx.serialization.Serializable

/**
 * A whole dish, ready to be written to a file (SPEC «Сохранение»): settings, clock, random generator, every cell,
 * bond and food particle, the genomes in use and the population graph. Arrays keep the world's own slot order and
 * the bond and genome pools keep their free lists, so a loaded dish runs on exactly like the one that was saved.
 */
@Serializable
class DishState(
    val formatVersion: Int,
    val settings: SubstrateSettings,
    val stepCount: Long,
    val rngState: Long,
    val lightAngle: Float,
    val nextUid: Int,
    val nextOrganism: Int,
    val foodDebt: Float,
    val genomes: GenomeTable,
    val cells: CellTable,
    val bonds: BondTable,
    val food: FoodTable,
    val history: HistoryTable,
)

@Serializable
class GenomeTable(
    val nextId: Long,
    /** Registry slots in order; null marks a free slot. */
    val slots: List<SavedGenome?>,
    /** Free slots in the order they will be reused. */
    val freeSlots: IntArray,
)

@Serializable
class SavedGenome(val id: Long, val parentId: Long, val genome: Genome)

/** Cells column by column; every array has one entry per cell except [signals] and [bondIds]. */
@Serializable
class CellTable(
    val uid: IntArray,
    val x: FloatArray,
    val y: FloatArray,
    val vx: FloatArray,
    val vy: FloatArray,
    val angle: FloatArray,
    val spin: FloatArray,
    val mass: FloatArray,
    val age: FloatArray,
    val nitrates: FloatArray,
    /** Genome registry slot. */
    val genome: IntArray,
    val mode: IntArray,
    val flags: IntArray,
    val organism: IntArray,
    /** S1–S4 of each cell in turn. */
    val signals: FloatArray,
    val bondCount: IntArray,
    /** The bond ids of each cell in turn, [bondCount] of them per cell. */
    val bondIds: IntArray,
) {
    val count: Int get() = uid.size
}

/** The bond pool: [alive] tells used slots from free ones. */
@Serializable
class BondTable(
    val alive: BooleanArray,
    val a: IntArray,
    val b: IntArray,
    val anchorA: FloatArray,
    val anchorB: FloatArray,
    val rest: FloatArray,
    val stiffness: FloatArray,
    val kind: IntArray,
    val free: IntArray,
)

@Serializable
class FoodTable(
    val x: FloatArray,
    val y: FloatArray,
    val vx: FloatArray,
    val vy: FloatArray,
    val mass: FloatArray,
    val coated: BooleanArray,
)

/** Population graph samples, oldest first. */
@Serializable
class HistoryTable(
    val hours: FloatArray,
    val cells: IntArray,
    val food: IntArray,
    val species: IntArray,
)

/** Copies the whole dish. Call it on the simulation thread; the copy shares nothing mutable with the world. */
fun World.saveState(): DishState {
    val c = cells
    val n = c.count
    val bondIds = IntArray((0 until n).sumOf { c.bondCount[it] })
    var k = 0
    for (i in 0 until n) {
        System.arraycopy(c.bondIds, i * Mode.MAX_BONDS, bondIds, k, c.bondCount[i])
        k += c.bondCount[i]
    }
    return DishState(
        formatVersion = DishCodec.VERSION,
        settings = settings,
        stepCount = stepCount,
        rngState = rng.state,
        lightAngle = lightAngle,
        nextUid = nextUid,
        nextOrganism = nextOrganism,
        foodDebt = foodDebt,
        genomes = genomes.table(),
        cells = CellTable(
            uid = c.uid.copyOf(n),
            x = c.x.copyOf(n),
            y = c.y.copyOf(n),
            vx = c.vx.copyOf(n),
            vy = c.vy.copyOf(n),
            angle = c.angle.copyOf(n),
            spin = c.spin.copyOf(n),
            mass = c.mass.copyOf(n),
            age = c.age.copyOf(n),
            nitrates = c.nitrates.copyOf(n),
            genome = c.genome.copyOf(n),
            mode = c.mode.copyOf(n),
            flags = c.flags.copyOf(n),
            organism = c.organism.copyOf(n),
            signals = c.signals.copyOf(n * Cells.SIGNALS),
            bondCount = c.bondCount.copyOf(n),
            bondIds = bondIds,
        ),
        bonds = bonds.table(),
        food = FoodTable(
            x = food.x.copyOf(food.count),
            y = food.y.copyOf(food.count),
            vx = food.vx.copyOf(food.count),
            vy = food.vy.copyOf(food.count),
            mass = food.mass.copyOf(food.count),
            coated = food.coated.copyOf(food.count),
        ),
        history = history.table(),
    )
}

/**
 * Builds the world this state describes. Damaged data (wrong sizes, links to missing cells, bonds or genomes,
 * impossible numbers) throws [FormatException] instead of producing a world that would crash later.
 */
fun DishState.toWorld(): World {
    validate()
    val world = World(settings, seed = 0L)
    world.rng.state = rngState
    world.stepCount = stepCount
    world.lightAngle = lightAngle
    world.nextUid = nextUid
    world.nextOrganism = nextOrganism
    world.foodDebt = foodDebt
    world.genomes.restore(
        GenomeTable(
            nextId = genomes.nextId,
            slots = genomes.slots.map { saved -> saved?.let { SavedGenome(it.id, it.parentId, it.genome.sanitized()) } },
            freeSlots = genomes.freeSlots,
        ),
    )

    val c = world.cells
    val t = cells
    var k = 0
    for (i in 0 until t.count) {
        c.uid[i] = t.uid[i]
        c.x[i] = t.x[i]
        c.y[i] = t.y[i]
        c.vx[i] = t.vx[i]
        c.vy[i] = t.vy[i]
        c.angle[i] = t.angle[i]
        c.spin[i] = t.spin[i]
        c.mass[i] = t.mass[i]
        c.age[i] = t.age[i]
        c.nitrates[i] = t.nitrates[i]
        c.genome[i] = t.genome[i]
        world.genomes.acquire(t.genome[i])
        c.mode[i] = t.mode[i]
        c.type[i] = world.genomes[t.genome[i]].modes[t.mode[i]].cellType.ordinal
        c.flags[i] = t.flags[i] and Cells.FLAG_DEAD.inv()
        c.organism[i] = t.organism[i]
        System.arraycopy(t.signals, i * Cells.SIGNALS, c.signals, i * Cells.SIGNALS, Cells.SIGNALS)
        c.bondCount[i] = t.bondCount[i]
        System.arraycopy(t.bondIds, k, c.bondIds, i * Mode.MAX_BONDS, t.bondCount[i])
        k += t.bondCount[i]
        c.radius[i] = world.radiusOf(t.mass[i])
        c.hardness[i] = 1f
        c.friction[i] = 1f
        c.activity[i] = 0f
        c.activity2[i] = 0f
        c.activity3[i] = 0f
    }
    c.count = t.count
    world.bonds.restore(bonds)

    val f = world.food
    for (i in food.x.indices) {
        f.x[i] = food.x[i]
        f.y[i] = food.y[i]
        f.vx[i] = food.vx[i]
        f.vy[i] = food.vy[i]
        f.mass[i] = food.mass[i]
        f.coated[i] = food.coated[i]
        f.eaten[i] = false
    }
    f.count = food.x.size
    world.history.restore(history)
    return world
}

private fun DishState.validate() {
    fun check(condition: Boolean) {
        if (!condition) throw FormatException(FormatException.Problem.DAMAGED)
    }

    // Genomes: free slots are exactly the empty ones.
    val slots = genomes.slots
    val emptySlots = slots.indices.filter { slots[it] == null }.toSet()
    check(genomes.freeSlots.size == emptySlots.size && genomes.freeSlots.toSet() == emptySlots)
    check(slots.all { it == null || it.id < genomes.nextId })

    // Cells.
    val t = cells
    val n = t.count
    check(n <= SimConfig.CELL_CAPACITY)
    check(
        listOf(t.x, t.y, t.vx, t.vy, t.angle, t.spin, t.mass, t.age, t.nitrates).all { it.size == n } &&
            listOf(t.genome, t.mode, t.flags, t.organism, t.bondCount).all { it.size == n } &&
            t.signals.size == n * Cells.SIGNALS,
    )
    check(t.uid.all { it in 1 until nextUid } && t.uid.toSet().size == n)
    for (i in 0 until n) {
        check(t.x[i].isFinite() && t.y[i].isFinite() && t.vx[i].isFinite() && t.vy[i].isFinite())
        check(t.angle[i].isFinite() && t.spin[i].isFinite() && t.mass[i].isFinite() && t.mass[i] > 0f)
        check(t.age[i].isFinite() && t.nitrates[i].isFinite())
        check(t.genome[i] in slots.indices && slots[t.genome[i]] != null)
        check(t.mode[i] in 0 until Mode.MODE_COUNT)
        check(t.bondCount[i] in 0..Mode.MAX_BONDS)
    }
    check(t.signals.all { it.isFinite() })
    // Every genome slot in use belongs to at least one cell.
    check(slots.indices.filter { slots[it] != null }.toSet() == t.genome.toSet())

    // Bonds: both ends exist and list the bond, and every listed bond is alive and points back.
    val b = bonds
    val m = b.alive.size
    check(m <= SimConfig.BOND_CAPACITY)
    check(listOf(b.anchorA, b.anchorB, b.rest, b.stiffness).all { it.size == m } && listOf(b.a, b.b, b.kind).all { it.size == m })
    val deadSlots = (0 until m).filter { !b.alive[it] }.toSet()
    check(b.free.size == deadSlots.size && b.free.toSet() == deadSlots)
    check(t.bondIds.size == t.bondCount.sum())
    val listed = IntArray(m)
    var k = 0
    for (i in 0 until n) {
        for (q in 0 until t.bondCount[i]) {
            val id = t.bondIds[k++]
            check(id in 0 until m && b.alive[id] && (b.a[id] == i || b.b[id] == i))
            listed[id]++
        }
    }
    for (id in 0 until m) {
        if (!b.alive[id]) continue
        check(b.a[id] in 0 until n && (b.b[id] == Bonds.WALL || b.b[id] in 0 until n) && b.a[id] != b.b[id])
        check(listed[id] == if (b.b[id] == Bonds.WALL) 1 else 2)
        check(b.anchorA[id].isFinite() && b.anchorB[id].isFinite() && b.rest[id].isFinite() && b.stiffness[id].isFinite())
    }

    // Food and the graph.
    val f = food
    check(f.x.size <= SimConfig.FOOD_CAPACITY)
    check(listOf(f.y, f.vx, f.vy, f.mass).all { it.size == f.x.size } && f.coated.size == f.x.size)
    check(f.x.indices.all { f.x[it].isFinite() && f.y[it].isFinite() && f.vx[it].isFinite() && f.vy[it].isFinite() && f.mass[it] > 0f })
    val h = history
    check(h.hours.size <= SimConfig.HISTORY_LENGTH)
    check(h.cells.size == h.hours.size && h.food.size == h.hours.size && h.species.size == h.hours.size)

    check(stepCount >= 0 && lightAngle.isFinite() && foodDebt.isFinite() && nextOrganism > 0)
}
