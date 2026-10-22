package io.github.torintheone.petri.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorldTest {
    @Test
    fun `each step adds one game minute`() {
        val world = World()
        repeat(90) { world.step() }
        assertEquals(90L, world.stepCount)
        assertEquals(1.5, world.ageHours, 1e-9)
    }

    @Test
    fun `a snapshot names the two cells of every bond`() {
        val world = TestGenomes.quietWorld().apply { previewGrowth = SimConfig.PREVIEW_GROWTH_PER_HOUR }
        world.addCell(Preset.CHAIN.genome("chain"), 0f, 0f)
        repeat((6.0 / SimConfig.STEP_HOURS).toInt()) { world.step() }
        val snapshot = WorldSnapshot()
        world.writeSnapshot(snapshot)
        assertTrue(snapshot.bondCount > 2, "the chain should have bonds, has ${snapshot.bondCount}")
        assertEquals(world.stepCount, snapshot.stepCount)
        for (k in 0 until snapshot.bondCount) {
            val a = snapshot.bondEnds[2 * k]
            val b = snapshot.bondEnds[2 * k + 1]
            val o = k * WorldSnapshot.BOND_FLOATS
            assertEquals(snapshot.cells[a * WorldSnapshot.CELL_FLOATS], snapshot.bonds[o])
            assertEquals(snapshot.cells[a * WorldSnapshot.CELL_FLOATS + 1], snapshot.bonds[o + 1])
            if (b >= 0) {
                assertEquals(snapshot.cells[b * WorldSnapshot.CELL_FLOATS], snapshot.bonds[o + 2])
                assertEquals(snapshot.cells[b * WorldSnapshot.CELL_FLOATS + 1], snapshot.bonds[o + 3])
            }
        }
    }

    @Test
    fun `divisions and deaths are handed to the renderer as events`() {
        val world = World(SubstrateSettings(foodRate = 0f, lightBrightness = 0f), 5L)
        val mother = world.addCell(Genome.default(), 0f, 0f, mass = 3f)
        while (world.eventSerial == 0L && world.ageHours < 2.0) world.step()
        val snapshot = WorldSnapshot()
        world.writeSnapshot(snapshot)
        assertEquals(1L, snapshot.eventSerial)
        assertEquals(WorldSnapshot.EVENT_DIVISION, snapshot.events[0])
        assertEquals(mother, snapshot.eventUids[0], "child 1 keeps the mother's uid")
        val child = snapshot.eventUids[1]
        assertTrue(world.indexOfUid(child) >= 0)
        assertTrue(snapshot.events[3] > 0f, "the mother's radius")

        world.removeCell(child)
        world.writeSnapshot(snapshot)
        assertEquals(2L, snapshot.eventSerial)
        assertEquals(WorldSnapshot.EVENT_DEATH, snapshot.events[WorldSnapshot.EVENT_FLOATS])
        assertEquals(child, snapshot.eventUids[2])
    }

    @Test
    fun `settings outside their range are pulled back in`() {
        val world = World(SubstrateSettings(dishDiameterMm = 5f, maxCells = 9_999, viscosity = -3f))
        assertEquals(3f, world.settings.dishDiameterMm)
        assertEquals(5_000, world.settings.maxCells)
        assertEquals(0f, world.settings.viscosity)
    }

    @Test
    fun `synthesizer plants the initial mode and refuses points outside the dish`() {
        val world = World()
        val genome = TestGenomes.genome(initial = 3)
        val uid = world.addCell(genome, 10f, 20f)
        assertTrue(uid != 0)
        val info = world.cellInfo(uid)!!
        assertEquals(3, info.mode)
        assertEquals(SimConfig.SYNTHESIZED_MASS, info.mass)
        assertEquals(0, world.addCell(genome, 10_000f, 0f))
    }

    @Test
    fun `sterilize kills everything and resets the age but keeps settings`() {
        val world = World(SubstrateSettings(foodRate = 1f))
        world.populate(Genome.default(), cellCount = 20, foodFraction = 0.3f)
        repeat(100) { world.step() }
        world.sterilize()
        assertEquals(0, world.cellCount)
        assertEquals(0, world.foodCount)
        assertEquals(0, world.speciesCount)
        assertEquals(0.0, world.ageHours)
        assertEquals(1f, world.settings.foodRate)
    }

    @Test
    fun `same seed and same actions give the same dish`() {
        fun run(): World {
            val world = World(SubstrateSettings(foodRate = 0.5f), seed = 42L)
            world.populate(Genome.default(), cellCount = 30, foodFraction = 0.5f)
            repeat(600) { world.step() }
            return world
        }
        val a = run()
        val b = run()
        assertEquals(a.cellCount, b.cellCount)
        for (i in 0 until a.cellCount) {
            assertEquals(a.cells.x[i], b.cells.x[i])
            assertEquals(a.cells.y[i], b.cells.y[i])
            assertEquals(a.cells.mass[i], b.cells.mass[i])
        }
    }
}
