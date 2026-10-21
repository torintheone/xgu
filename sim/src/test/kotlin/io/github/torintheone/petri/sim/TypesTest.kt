package io.github.torintheone.petri.sim

import io.github.torintheone.petri.sim.TestGenomes.genome
import io.github.torintheone.petri.sim.TestGenomes.quietWorld
import io.github.torintheone.petri.sim.TestGenomes.stepHours
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The basic cell types and the medium (SPEC «Этап 4»). */
class TypesTest {
    @Test
    fun `the swimmer swims, eats and multiplies`() {
        val world = World(SubstrateSettings(foodRate = 0.5f), 21L)
        world.populate(Genome.default(), cellCount = 0, foodFraction = 0.6f)
        val swimmer = Preset.SWIMMER.genome("swimmer")
        world.addCell(swimmer, 0f, 0f, angle = 0f, mass = 3f)
        world.stepHours(1.0)
        val flagella = { (0 until world.cellCount).count { world.cells.cellType(it) == CellType.FLAGELLOCYTE } }
        assertEquals(1, flagella(), "the seed has grown a tail")

        // Follow the first swimmer's tail for a few hours: it should travel well beyond its own size.
        val tail = (0 until world.cellCount).first { world.cells.cellType(it) == CellType.FLAGELLOCYTE }
        val tailUid = world.cells.uid[tail]
        val startX = world.cells.x[tail]
        val startY = world.cells.y[tail]
        var travelled = 0f
        repeat(4 * 60) {
            world.step()
            val i = world.indexOfUid(tailUid)
            if (i >= 0) travelled = maxOf(travelled, hypot(world.cells.x[i] - startX, world.cells.y[i] - startY))
        }
        assertTrue(travelled > 150f, "the swimmer should swim away, it moved $travelled µm")

        world.stepHours(60.0)
        assertTrue(flagella() >= 4, "swimmers should multiply: ${flagella()} tails, ${world.cellCount} cells")
    }

    @Test
    fun `devorocytes wipe out an unprotected colony but not one with keratinocytes`() {
        fun survivors(protectedColony: Boolean): Int {
            // Gravity keeps the hunters pressed onto the colony at the bottom of a small dish.
            val settings = SubstrateSettings(
                dishDiameterMm = 0.5f,
                foodRate = 0f,
                lightBrightness = 0f,
                aging = false,
                corpsesBecomeFood = false,
                gravity = 1f,
            )
            val world = World(settings, 4L)
            val prey = genome(
                0,
                0 to { m -> m.copy(cellType = if (protectedColony) CellType.KERATINOCYTE else CellType.PHAGOCYTE, splitMass = Mode.SPLIT_NEVER) },
                1 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) },
            )
            val hunter = genome(0, 0 to { m -> m.copy(cellType = CellType.DEVOROCYTE, splitMass = Mode.SPLIT_NEVER) })
            // A centre cell with four phagocytes bonded around it: one organism near the bottom.
            val bottom = -world.dishRadius + 40f
            val centre = world.indexOfUid(world.addCell(prey, 0f, bottom, angle = 0f, mass = 3f, mode = 0))
            val colony = mutableListOf(world.cells.uid[centre])
            repeat(4) { k ->
                val a = k * World.PI_F / 2f
                val i = world.indexOfUid(world.addCell(prey, 25f * cos(a), bottom + 25f * sin(a), angle = a + World.PI_F, mass = 3f, mode = 1))
                world.addBond(centre, i, World.wrapPi(a), 0f, 2f, 1f, Bonds.KIND_ADHESION)
                world.cells.organism[i] = world.cells.organism[centre]
                colony += world.cells.uid[i]
            }
            // Hunters dropped from above.
            repeat(5) { k -> world.addCell(hunter, -60f + 30f * k, bottom + 70f, angle = 0f, mass = 3f) }
            world.stepHours(8.0)
            return colony.count { world.indexOfUid(it) >= 0 }
        }
        val eaten = survivors(protectedColony = false)
        val shielded = survivors(protectedColony = true)
        assertTrue(eaten <= 1, "an unprotected colony is eaten: $eaten of 5 left")
        assertEquals(5, shielded, "keratinocyte bonds protect their partners")
    }

    @Test
    fun `buoyocytes float or sink with gravity, ordinary cells sink slowly`() {
        fun height(density: Float?): Float {
            val world = quietWorld(settings = SubstrateSettings(gravity = 1f, mediumDensity = 0.5f, dynamicFriction = 0f))
            val g = if (density == null) {
                genome(0, 0 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) })
            } else {
                genome(0, 0 to { m -> m.copy(cellType = CellType.BUOYOCYTE, splitMass = Mode.SPLIT_NEVER, density = ProgrammableValue.constant(density)) })
            }
            val uid = world.addCell(g, 0f, 0f, angle = 0f, mass = 2f)
            world.stepHours(3.0)
            return world.cells.y[world.indexOfUid(uid)]
        }
        val floater = height(0f)
        val sinker = height(1f)
        val ordinary = height(null)
        assertTrue(floater > 100f, "a light buoyocyte rises: $floater")
        assertTrue(sinker < -100f, "a heavy buoyocyte sinks: $sinker")
        assertTrue(ordinary < 0f && ordinary > sinker, "an ordinary cell sinks, but slower: $ordinary vs $sinker")
    }

    @Test
    fun `a lipocyte feeds its partner from its store`() {
        val world = World(SubstrateSettings(foodRate = 0f, lightBrightness = 0f, aging = false), 2L)
        val g = genome(
            0,
            0 to { m -> m.copy(cellType = CellType.LIPOCYTE, splitMass = Mode.SPLIT_NEVER) },
            1 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) },
        )
        val fat = world.indexOfUid(world.addCell(g, -20f, 0f, angle = 0f, mass = 10f, mode = 0))
        val lean = world.addCell(g, 15f, 0f, angle = World.PI_F, mass = 1f, mode = 1)
        world.addBond(fat, world.indexOfUid(lean), 0f, 0f, 2f, 0.5f, Bonds.KIND_ADHESION)
        assertEquals(CellType.LIPOCYTE.maxMass, 10.8f, 1e-4f)
        world.stepHours(48.0)
        // Without the store the phagocyte would have starved long ago; instead it is full.
        val i = world.indexOfUid(lean)
        assertTrue(i >= 0, "the phagocyte is alive")
        assertTrue(world.cells.mass[i] > 1.5f, "the phagocyte got mass from the lipocyte: ${world.cells.mass[i]}")
    }

    @Test
    fun `a nitrocyte lets its partner divide without nitrates in the medium`() {
        fun divisions(withNitrocyte: Boolean): Int {
            val world = quietWorld(settings = SubstrateSettings(nitrates = 0f, dishDiameterMm = 2f))
            world.previewGrowth = -1f
            world.settings = world.settings.copy(salinity = 1f)
            val g = genome(
                0,
                0 to { m -> m.copy(cellType = if (withNitrocyte) CellType.NITROCYTE else CellType.LIPOCYTE, splitMass = Mode.SPLIT_NEVER) },
                1 to { m -> m.copy(cellType = CellType.LIPOCYTE, splitMass = 2f, child1Mode = 2, child2Mode = 2) },
                2 to { m -> m.copy(cellType = CellType.LIPOCYTE, splitMass = Mode.SPLIT_NEVER) },
            )
            val source = world.indexOfUid(world.addCell(g, -20f, 0f, angle = 0f, mass = 8f, mode = 0))
            val cell = world.addCell(g, 15f, 0f, angle = World.PI_F, mass = 3f, mode = 1)
            world.addBond(source, world.indexOfUid(cell), 0f, 0f, 2f, 0.5f, Bonds.KIND_ADHESION)
            for (i in 0 until world.cellCount) world.cells.nitrates[i] = 0f
            world.stepHours(10.0)
            return world.cellCount - 2
        }
        assertEquals(0, divisions(withNitrocyte = false))
        assertTrue(divisions(withNitrocyte = true) >= 1)
    }

    @Test
    fun `a glueocyte sticks to other organisms and to the wall`() {
        val world = quietWorld(settings = SubstrateSettings(dishDiameterMm = 0.5f))
        val glue = genome(0, 0 to { m -> m.copy(cellType = CellType.GLUEOCYTE, splitMass = Mode.SPLIT_NEVER) })
        val other = genome(0, 0 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) })
        val edge = world.dishRadius - world.radiusOf(2f)
        world.addCell(glue, edge + 0.5f, 0f, angle = 0f, mass = 2f)
        world.addCell(other, edge - 18f, 0f, angle = 0f, mass = 2f)
        world.stepHours(0.5)
        val b = world.bonds
        val bonds = (0 until b.size).filter { b.alive[it] }
        assertTrue(bonds.any { b.b[it] == Bonds.WALL }, "glued to the wall")
        assertTrue(bonds.any { b.b[it] != Bonds.WALL }, "glued to the other cell")
    }

    @Test
    fun `a bond at the back stops the flagellum, and kills it when the rule says so`() {
        fun run(kills: Boolean): Pair<Int, Float> {
            val world = quietWorld(settings = SubstrateSettings(blockedFlagellumKills = kills))
            world.previewGrowth = -1f
            val g = genome(
                0,
                0 to { m -> m.copy(cellType = CellType.FLAGELLOCYTE, splitMass = Mode.SPLIT_NEVER, swimForce = ProgrammableValue.constant(1f)) },
                1 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) },
            )
            val swimmer = world.indexOfUid(world.addCell(g, 0f, 0f, angle = 0f, mass = 2f, mode = 0))
            val anchor = world.indexOfUid(world.addCell(g, -20f, 0f, angle = 0f, mass = 2f, mode = 1))
            world.addBond(swimmer, anchor, World.PI_F, 0f, 2f, 1f, Bonds.KIND_ADHESION)
            world.stepHours(1.0)
            return world.cellCount to world.cells.x.copyOf(world.cellCount).max()
        }
        val (aliveCount, front) = run(kills = false)
        assertEquals(2, aliveCount)
        assertTrue(front < 15f, "a blocked flagellum does not swim: front cell at $front")
        assertEquals(1, run(kills = true).first)
    }

    @Test
    fun `static friction holds a resting cell against a weak pull`() {
        fun moved(staticFriction: Float): Float {
            val world = quietWorld(
                settings = SubstrateSettings(staticFriction = staticFriction, dynamicFriction = 0f, gravity = 1f, mediumDensity = 0.4f),
            )
            val g = genome(0, 0 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) })
            val uid = world.addCell(g, 0f, 0f, angle = 0f, mass = 2f)
            world.stepHours(1.0)
            return -world.cells.y[world.indexOfUid(uid)]
        }
        assertTrue(moved(0f) > 20f, "without friction the cell sinks: ${moved(0f)}")
        assertEquals(0f, moved(1f), 1e-3f)
    }

    @Test
    fun `a smaller dish moves cells and food inside`() {
        val world = World(SubstrateSettings(dishDiameterMm = 2f), 1L)
        world.populate(Genome.default(), cellCount = 50, foodFraction = 0.5f)
        world.applySettings(world.settings.copy(dishDiameterMm = 0.5f))
        val r = world.dishRadius
        for (i in 0 until world.cellCount) assertTrue(hypot(world.cells.x[i], world.cells.y[i]) <= r + 1e-3f)
        for (k in 0 until world.foodCount) assertTrue(hypot(world.food.x[k], world.food.y[k]) <= r + 1e-3f)
        repeat(60) { world.step() }
        assertTrue((0 until world.cellCount).all { world.cells.x[it].isFinite() && world.cells.y[it].isFinite() })
    }
}
