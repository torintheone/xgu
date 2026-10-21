package io.github.torintheone.petri.sim

import io.github.torintheone.petri.sim.TestGenomes.genome
import io.github.torintheone.petri.sim.TestGenomes.quietWorld
import io.github.torintheone.petri.sim.TestGenomes.stepHours
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Whole-dish behaviour of stages 1–2 (SPEC «Этапы и критерии приёмки»). */
class ScenarioTest {
    @Test
    fun `photocyte colony in a narrow beam grows to the light and levels off`() {
        val settings = SubstrateSettings(foodRate = 0f, lightBrightness = 1f, lightWidth = 0.2f, maxCells = 2_000)
        val world = World(settings, 3L)
        val g = genome(0, 0 to { m -> m.copy(cellType = CellType.PHOTOCYTE, makeAdhesin = true, splitMass = 2.5f, splitAngle = 0f) })
        val r = world.dishRadius
        world.addCell(g, 0f, r - 150f, angle = 0f, mass = 3f)
        world.stepHours(24.0)
        val c = world.cells
        val nearest = (0 until c.count).minOf { r - c.y[it] }
        assertTrue(nearest < 30f, "the colony should reach the light source, nearest cell is $nearest µm away")
        val counts = mutableListOf<Int>()
        repeat(6) {
            world.stepHours(6.0)
            counts += world.cellCount
        }
        assertTrue(counts.min() > 100, "the colony survives: $counts")
        assertTrue(counts.max() < 1_500, "the light limits the colony well below the cell limit: $counts")
        assertTrue(counts.max() - counts.min() < 0.25f * counts.average(), "the size levels off: $counts")
    }

    @Test
    fun `phagocytes in the default dish multiply on the drifting food`() {
        val world = World(SubstrateSettings(), 5L)
        world.populate(Genome.default(), cellCount = 30, foodFraction = 0.5f)
        world.stepHours(48.0)
        assertTrue(world.cellCount > 100, "phagocytes should multiply, have ${world.cellCount}")
    }

    @Test
    fun `without nitrates a cell stops dividing but does not die`() {
        val world = quietWorld(settings = SubstrateSettings(nitrates = 0f))
        world.previewGrowth = -1f
        val g = genome(0, 0 to { m -> m.copy(cellType = CellType.LIPOCYTE, splitMass = 2f) })
        val uid = world.addCell(g, 0f, 0f, angle = 0f, mass = 3f)
        world.cells.nitrates[world.indexOfUid(uid)] = 0f
        world.stepHours(10.0)
        assertEquals(1, world.cellCount)
    }

    @Test
    fun `the cell limit stops divisions`() {
        val world = quietWorld(settings = SubstrateSettings(maxCells = 100, dishDiameterMm = 3f))
        world.previewGrowth = 4f
        world.addCell(Genome.default(), 0f, 0f, angle = 0f, mass = 3f)
        world.stepHours(20.0)
        assertEquals(100, world.cellCount)
    }

    @Test
    fun `with aging on a cell that never divides dies at 240 h`() {
        val world = quietWorld(settings = SubstrateSettings())
        world.previewGrowth = -1f
        world.settings = world.settings.copy(aging = true, salinity = 1f)
        val g = genome(0, 0 to { m -> m.copy(cellType = CellType.LIPOCYTE, splitMass = Mode.SPLIT_NEVER) })
        world.addCell(g, 0f, 0f, angle = 0f, mass = 3f)
        world.stepHours(239.0)
        assertEquals(1, world.cellCount)
        world.stepHours(2.0)
        assertEquals(0, world.cellCount)
    }

    @Test
    fun `with deadly walls a cell that touches the rim dies`() {
        val world = quietWorld(settings = SubstrateSettings(deadlyWalls = true))
        world.previewGrowth = -1f
        world.addCell(Genome.default(), 0f, 0f, angle = 0f, mass = 2f)
        val edge = world.dishRadius - 5f
        world.addCell(genome(0, 0 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) }), edge, 0f, angle = 0f, mass = 2f)
        world.step()
        assertEquals(1, world.cellCount)
    }

    @Test
    fun `food appears at the set rate`() {
        val world = World(SubstrateSettings(foodRate = 1f, maxFood = 5_000, mobileFood = false), 9L)
        world.stepHours(2.0)
        val expected = 2f * SimConfig.FOOD_RATE_MAX_PER_HOUR
        assertTrue(world.foodCount > 0.9f * expected && world.foodCount < 1.1f * expected, "food count ${world.foodCount}")
    }
}
