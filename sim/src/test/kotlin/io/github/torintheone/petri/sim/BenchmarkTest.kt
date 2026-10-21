package io.github.torintheone.petri.sim

import io.github.torintheone.petri.sim.TestGenomes.genome
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** SPEC «Бенчмарк шага на 2000 клеток с выводом времени в лог CI». */
class BenchmarkTest {
    @Test
    fun `step time with 2000 cells`() {
        val world = World(SubstrateSettings(dishDiameterMm = 3f, maxCells = 5_000, maxFood = 2_000), 11L)
        // Half the cells in 4-cell organisms, half free; nobody divides so the count stays put.
        val loner = genome(0, 0 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER, cellType = CellType.LIPOCYTE) })
        repeat(1_000) {
            val (x, y) = world.randomPointInDisk(world.dishRadius * 0.9f)
            world.addCell(loner, x, y)
        }
        repeat(250) {
            val (x, y) = world.randomPointInDisk(world.dishRadius * 0.85f)
            val first = world.indexOfUid(world.addCell(loner, x, y, angle = 0f, mass = 1.5f))
            var previous = first
            for (k in 1 until 4) {
                val next = world.indexOfUid(world.addCell(loner, x + 21f * k, y, angle = 0f, mass = 1.5f))
                world.addBond(previous, next, 0f, World.PI_F, 5f, 0.5f, Bonds.KIND_ADHESION)
                previous = next
            }
        }
        world.populate(loner, 0, 0.5f)
        repeat(200) { world.step() }
        val steps = 300
        val start = System.nanoTime()
        repeat(steps) { world.step() }
        val msPerStep = (System.nanoTime() - start) / 1e6 / steps
        println("BENCHMARK: ${world.cellCount} cells, ${world.bonds.liveCount} bonds, ${world.foodCount} food: " +
            "${"%.2f".format(msPerStep)} ms per step (${"%.0f".format(1000 / msPerStep)} steps/s; observation needs 60)")
        assertTrue(msPerStep < 100.0, "a step takes $msPerStep ms")
    }
}
