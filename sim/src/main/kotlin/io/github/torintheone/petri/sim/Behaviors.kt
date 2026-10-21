package io.github.torintheone.petri.sim

import kotlin.math.atan2
import kotlin.math.min

/**
 * What each cell type does (SPEC «Типы клеток»). [prepare] runs before physics and works out each cell's output
 * for this step; [afterMovement] handles contacts: eating, devouring, gluing and the walls.
 */
internal class Behaviors(val world: World) {
    fun prepare() {
        val c = world.cells
        val n = c.count
        val cleared = (Cells.FLAG_PROTECTED or Cells.FLAG_TAIL_BLOCKED).inv()
        for (i in 0 until n) {
            val mode = world.modeOf(i)
            c.hardness[i] = 1f + (mode.cytoskeleton - 0.5f) * SimConfig.CYTOSKELETON_HARDNESS
            c.friction[i] = 1f
            c.flags[i] = c.flags[i] and cleared
            c.activity2[i] = 0f
            c.activity3[i] = 0f
            when (mode.cellType) {
                CellType.FLAGELLOCYTE -> c.activity[i] = value(i, mode.swimForce).coerceIn(0f, 1f)
                CellType.BUOYOCYTE -> c.activity[i] = value(i, mode.density).coerceIn(0f, 1f)
                CellType.CILIOCYTE -> {
                    c.activity[i] = value(i, mode.leftCilia).coerceIn(-1f, 1f)
                    c.activity2[i] = value(i, mode.rightCilia).coerceIn(-1f, 1f)
                }
                CellType.MYOCYTE -> {
                    val hand = if (c.isMirrored(i)) -1f else 1f
                    c.activity[i] = value(i, mode.contraction).coerceIn(-1f, 1f)
                    c.activity2[i] = hand * value(i, mode.bend).coerceIn(-1f, 1f)
                    c.activity3[i] = value(i, mode.lift).coerceIn(0f, 1f)
                }
                else -> c.activity[i] = 0f
            }
        }
        for (i in 0 until n) {
            when (c.type[i]) {
                KERATINOCYTE -> {
                    c.flags[i] = c.flags[i] or Cells.FLAG_PROTECTED
                    forEachPartner(i) { j -> c.flags[j] = c.flags[j] or Cells.FLAG_PROTECTED }
                }
                MYOCYTE -> {
                    val lift = c.activity3[i]
                    if (lift > 0f) {
                        val friction = 1f - lift
                        c.friction[i] = min(c.friction[i], friction)
                        forEachPartner(i) { j -> c.friction[j] = min(c.friction[j], friction) }
                    }
                }
                FLAGELLOCYTE -> if (tailBlocked(i)) {
                    c.flags[i] = c.flags[i] or Cells.FLAG_TAIL_BLOCKED
                    c.activity[i] = 0f
                    if (world.settings.blockedFlagellumKills && world.previewGrowth < 0f) world.kill(i)
                }
            }
        }
    }

    fun afterMovement() {
        val c = world.cells
        val n = c.count
        val physics = world.physics
        if (world.settings.deadlyWalls && world.previewGrowth < 0f) {
            for (i in 0 until n) if (physics.touchingWall[i]) world.kill(i)
        }
        val food = world.food
        world.foodGrid.build(food.x, food.y, food.count, world.dishRadius + physics.maxRadius, 2f * physics.maxRadius)
        for (i in 0 until n) {
            if (world.isDead(i)) continue
            when (c.type[i]) {
                PHAGOCYTE -> eat(i)
                DEVOROCYTE -> devour(i)
                GLUEOCYTE -> glue(i)
            }
        }
    }

    /** A programmable value of cell i for this step. */
    fun value(i: Int, value: ProgrammableValue): Float = value.evaluate(input(i, value.input))

    fun input(i: Int, input: SignalInput): Float {
        val c = world.cells
        return when (input) {
            SignalInput.NONE -> 0f
            SignalInput.S1 -> c.signals[i * Cells.SIGNALS]
            SignalInput.S2 -> c.signals[i * Cells.SIGNALS + 1]
            SignalInput.S3 -> c.signals[i * Cells.SIGNALS + 2]
            SignalInput.S4 -> c.signals[i * Cells.SIGNALS + 3]
            SignalInput.MASS -> c.mass[i] / SimConfig.MAX_MASS
            SignalInput.AGE -> c.age[i] / SimConfig.MAX_AGE_HOURS
            SignalInput.NITRATES -> c.nitrates[i]
            SignalInput.BONDS -> c.bondCount[i] / Mode.MAX_BONDS.toFloat()
        }
    }

    /** Calls [action] with every cell bonded to cell i. */
    inline fun forEachPartner(i: Int, action: (Int) -> Unit) {
        val c = world.cells
        val b = world.bonds
        val base = i * Mode.MAX_BONDS
        for (k in 0 until c.bondCount[i]) {
            val id = c.bondIds[base + k]
            val other = if (b.a[id] == i) b.b[id] else b.a[id]
            if (other != Bonds.WALL) action(other)
        }
    }

    /** Calls [action] with every live cell whose surface is within [SimConfig.CONTACT_SLACK_UM] of cell i. */
    inline fun forEachTouching(i: Int, action: (Int) -> Unit) {
        val c = world.cells
        val physics = world.physics
        val xi = c.x[i]
        val yi = c.y[i]
        val ri = c.radius[i]
        physics.grid.forEachNear(xi, yi, ri + physics.maxRadius + SimConfig.CONTACT_SLACK_UM) { j ->
            if (j != i && j < c.count && !world.isDead(j)) {
                val dx = c.x[j] - xi
                val dy = c.y[j] - yi
                val reach = ri + c.radius[j] + SimConfig.CONTACT_SLACK_UM
                if (dx * dx + dy * dy < reach * reach) action(j)
            }
        }
    }

    private fun isProtected(j: Int): Boolean = world.cells.flags[j] and Cells.FLAG_PROTECTED != 0

    /** True when a bond is attached near the back of cell i, where a flagellocyte keeps its tail. */
    private fun tailBlocked(i: Int): Boolean {
        val c = world.cells
        val b = world.bonds
        val base = i * Mode.MAX_BONDS
        for (k in 0 until c.bondCount[i]) {
            val id = c.bondIds[base + k]
            val anchor = if (b.a[id] == i) b.anchorA[id] else b.anchorB[id]
            if (kotlin.math.abs(World.wrapPi(anchor - World.PI_F)) < SimConfig.TAIL_BLOCK_ANGLE) return true
        }
        return false
    }

    /** Phagocyte: swallows any uncoated food particle it touches. */
    private fun eat(i: Int) {
        val c = world.cells
        val food = world.food
        val maxMass = SimConfig.MAX_MASS
        if (c.mass[i] >= maxMass) return
        val xi = c.x[i]
        val yi = c.y[i]
        val ri = c.radius[i]
        world.foodGrid.forEachNear(xi, yi, ri + MAX_FOOD_RADIUS + SimConfig.CONTACT_SLACK_UM) { k ->
            if (!food.eaten[k] && !food.coated[k] && c.mass[i] < maxMass) {
                val dx = food.x[k] - xi
                val dy = food.y[k] - yi
                // Mobile food is pushed out to exactly touching distance, so touching counts as eating.
                val reach = ri + food.radius(k) + SimConfig.CONTACT_SLACK_UM
                if (dx * dx + dy * dy < reach * reach) {
                    food.eaten[k] = true
                    c.mass[i] = min(maxMass, c.mass[i] + food.mass[k])
                }
            }
        }
    }

    /** Devorocyte: drains mass from touching cells of other organisms unless a keratinocyte shields them. */
    private fun devour(i: Int) {
        val c = world.cells
        val maxMass = SimConfig.MAX_MASS
        val bite = SimConfig.DEVOUR_PER_HOUR * SimConfig.STEP_HOURS_F
        forEachTouching(i) { j ->
            if (c.organism[j] != c.organism[i] && !isProtected(j)) {
                val take = min(bite, c.mass[j])
                c.mass[j] -= take
                c.mass[i] = min(maxMass, c.mass[i] + take * SimConfig.DEVOUR_EFFICIENCY)
                if (c.mass[j] < SimConfig.MIN_MASS && world.previewGrowth < 0f) world.kill(j)
            }
        }
    }

    /** Glueocyte: sticks to touching unprotected cells of other organisms and to the wall, up to a few bonds. */
    private fun glue(i: Int) {
        val c = world.cells
        val mode = world.modeOf(i)
        if (c.bondCount[i] >= SimConfig.GLUE_MAX_BONDS) return
        if (world.physics.touchingWall[i] && !hasWallBond(i)) {
            val wallAngle = atan2(c.y[i], c.x[i])
            world.addBond(
                i, Bonds.WALL,
                anchorI = World.wrapPi(wallAngle - c.angle[i]),
                anchorJ = wallAngle,
                rest = 0f,
                stiffness = mode.adhesinStiffness,
                kind = Bonds.KIND_GLUE,
            )
        }
        forEachTouching(i) { j ->
            if (c.bondCount[i] < SimConfig.GLUE_MAX_BONDS && c.organism[j] != c.organism[i] &&
                !isProtected(j) && !world.bonded(i, j)
            ) {
                val line = atan2(c.y[j] - c.y[i], c.x[j] - c.x[i])
                world.addBond(
                    i, j,
                    anchorI = World.wrapPi(line - c.angle[i]),
                    anchorJ = World.wrapPi(line + World.PI_F - c.angle[j]),
                    rest = 0f,
                    stiffness = mode.adhesinStiffness,
                    kind = Bonds.KIND_GLUE,
                )
            }
        }
    }

    private fun hasWallBond(i: Int): Boolean {
        val c = world.cells
        val base = i * Mode.MAX_BONDS
        for (k in 0 until c.bondCount[i]) {
            if (world.bonds.b[c.bondIds[base + k]] == Bonds.WALL) return true
        }
        return false
    }

    private companion object {
        val PHAGOCYTE = CellType.PHAGOCYTE.ordinal
        val FLAGELLOCYTE = CellType.FLAGELLOCYTE.ordinal
        val DEVOROCYTE = CellType.DEVOROCYTE.ordinal
        val KERATINOCYTE = CellType.KERATINOCYTE.ordinal
        val GLUEOCYTE = CellType.GLUEOCYTE.ordinal
        val MYOCYTE = CellType.MYOCYTE.ordinal

        /** Largest food particle radius, µm. */
        val MAX_FOOD_RADIUS = SimConfig.FOOD_RADIUS_PER_SQRT_NG * kotlin.math.sqrt(SimConfig.FOOD_MASS_MAX)
    }
}
