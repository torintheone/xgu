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
