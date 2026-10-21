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
