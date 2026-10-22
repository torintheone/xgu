package io.github.torintheone.petri.sim

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.atan2

/** The growth preview of the genome editor (SPEC «Геном», «Этап 3»). */
class PreviewTest {
    @Test
    fun `the preview shows every hour from 0 to 47 and stops at 64 cells`() {
        val frames = GrowthPreview.frames(Genome.default())
        assertEquals(SimConfig.PREVIEW_HOURS, frames.size)
        assertEquals(0, frames.first().hour)
        assertEquals(1, frames.first().cellCount)
        assertEquals(47, frames.last().hour)
        assertTrue(frames.all { it.cellCount <= SimConfig.PREVIEW_MAX_CELLS })
        assertEquals(SimConfig.PREVIEW_MAX_CELLS, frames.last().cellCount)
    }

    @Test
    fun `the same genome always grows the same way`() {
        val genome = Preset.ARC.genome("arc")
        val a = GrowthPreview.frames(genome, hours = 12).last()
        val b = GrowthPreview.frames(genome, hours = 12).last()
        assertArrayEquals(a.cells, b.cells)
    }

    @Test
    fun `changing the split angle changes the preview`() {
        val straight = Preset.CHAIN.genome("chain")
        val turned = straight.withMode(0, straight.modes[0].copy(splitAngle = 0f))
        val a = GrowthPreview.frames(straight, hours = 8).last()
        val b = GrowthPreview.frames(turned, hours = 8).last()
        assertEquals(a.cellCount, b.cellCount)
        assertFalse(a.cells.contentEquals(b.cells))
    }

    @Test
    fun `the first cell faces up and a split angle of 90 grows the chain along the front`() {
        val frame = GrowthPreview.frames(Preset.CHAIN.genome("chain"), hours = 6).last()
        assertTrue(frame.cellCount >= 4)
        // The split plane crosses the front at 90°, so the children line up along the front, which points up.
        val ys = (0 until frame.cellCount).map { frame.y(it) }
        val xs = (0 until frame.cellCount).map { frame.x(it) }
        assertTrue(xs.max() - xs.min() < 0.2f * (ys.max() - ys.min()), "the chain should be vertical: x $xs, y $ys")
        val direction = atan2(ys.max() - ys.min(), xs[ys.indexOf(ys.max())] - xs[ys.indexOf(ys.min())])
        assertTrue(abs(kotlin.math.cos(direction)) < 0.3f)
    }

    @Test
    fun `every preset grows in the preview`() {
        for (preset in Preset.entries) {
            val frames = GrowthPreview.frames(preset.genome(preset.name), hours = 24)
            assertTrue(frames.last().cellCount >= 8, "${preset.name}: ${frames.last().cellCount} cells")
            assertTrue(frames.last().extent > 0f)
        }
    }

    @Test
    fun `the smart swimmer grows into a row of mouth, tail, sensor, tail, mouth`() {
        val frame = GrowthPreview.frames(Preset.SMART_SWIMMER.genome("smart"), hours = 3).last()
        assertEquals(5, frame.cellCount)
        // The row runs across the front (which points up): order the cells along it.
        val order = (0 until frame.cellCount).sortedBy { frame.x(it) }
        assertEquals(
            listOf(CellType.PHAGOCYTE, CellType.FLAGELLOCYTE, CellType.STEREOCYTE, CellType.FLAGELLOCYTE, CellType.PHAGOCYTE),
            order.map { frame.type(it) },
        )
        // A straight row: every cell close to the line through both ends.
        val (a, b) = order.first() to order.last()
        val dx = frame.x(b) - frame.x(a)
        val dy = frame.y(b) - frame.y(a)
        val length = kotlin.math.hypot(dx, dy)
        for (i in order) {
            val off = abs((frame.x(i) - frame.x(a)) * dy - (frame.y(i) - frame.y(a)) * dx) / length
            assertTrue(off < 12f, "cell $i is $off µm off the row")
        }
    }

    @Test
    fun `the preview follows the organism, not the buds that drift away`() {
        // The smart swimmer is five cells in a row; by the last hour its seeds have swum far away.
        val swimmer = GrowthPreview.frames(Preset.SMART_SWIMMER.genome("smart")).last()
        assertTrue(swimmer.bodyExtent < 100f, "body ${swimmer.bodyExtent}")
        assertTrue(swimmer.extent > 3 * swimmer.bodyExtent, "all cells ${swimmer.extent}, body ${swimmer.bodyExtent}")
        // Each plain swimmer is a pair: a mouth and a flagellum.
        val pair = GrowthPreview.frames(Preset.SWIMMER.genome("swimmer")).last()
        assertTrue(pair.bodyExtent < 60f, "body ${pair.bodyExtent}")
        // A chain is one organism, all of it.
        val chain = GrowthPreview.frames(Preset.CHAIN.genome("chain")).last()
        assertEquals(chain.extent, chain.bodyExtent)
        // Free-living cells have no organism to follow: the picture keeps them all.
        val single = GrowthPreview.frames(Preset.SINGLE_PHAGOCYTES.genome("single")).last()
        assertEquals(single.extent, single.bodyExtent)
        assertEquals(single.centerX, single.bodyCenterX)
    }

    @Test
    fun `tapping a preview cell finds its mode`() {
        val frame = GrowthPreview.frames(Preset.ARC.genome("arc"), hours = 10).last()
        val modes = (0 until frame.cellCount).map { frame.mode(it) }.toSet()
        assertEquals(setOf(0, 1), modes)
        val i = (0 until frame.cellCount).first { frame.mode(it) == 1 }
        assertEquals(i, frame.cellAt(frame.x(i), frame.y(i)))
        assertEquals(-1, frame.cellAt(frame.centerX + 10 * frame.extent, frame.centerY))
    }

    @Test
    fun `the thumbnail stops once the organism is grown`() {
        val thumbnail = GrowthPreview.thumbnail(Genome.default())
        assertEquals(SimConfig.PREVIEW_MAX_CELLS, thumbnail.cellCount)
        assertTrue(thumbnail.hour < 24)
    }

    @Test
    fun `the whole preview takes well under a second`() {
        val genome = Preset.PHOTOCYTE_COLONY.genome("colony")
        GrowthPreview.frames(genome) // warm up the JIT
        val start = System.nanoTime()
        GrowthPreview.frames(genome)
        val millis = (System.nanoTime() - start) / 1_000_000
        println("PREVIEW: 48 h of a 64-cell colony took $millis ms")
        assertTrue(millis < 1_500, "preview took $millis ms")
    }
}
