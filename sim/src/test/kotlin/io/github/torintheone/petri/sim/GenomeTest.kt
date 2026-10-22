package io.github.torintheone.petri.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Which modes an organism uses, as the simple editor shows them. */
class GenomeTest {
    @Test
    fun `the default organism uses only its first mode`() {
        val genome = Genome.default()
        assertEquals(listOf(0), genome.usedModes().toList())
        assertEquals(1, genome.freeMode())
    }

    @Test
    fun `used modes start with the initial one and follow the divisions`() {
        val genome = Preset.SWIMMER.genome("swimmer")
        assertEquals(listOf(0, 1, 2), genome.usedModes().toList())
        assertEquals(3, genome.freeMode())

        val fromTail = genome.copy(initialMode = 2)
        assertEquals(listOf(2), fromTail.usedModes().toList())
        assertEquals(0, fromTail.freeMode())
    }

    @Test
    fun `a virocyte also uses the modes it spreads`() {
        val base = Genome.default()
        val genome = base
            .withMode(0, base.modes[0].copy(cellType = CellType.VIROCYTE, virusMode = 5))
            .withMode(5, base.modes[5].copy(child1Mode = 6, child2Mode = 5))
        assertEquals(setOf(0, 5, 6), genome.usedModes())
        assertEquals(1, genome.freeMode())
    }

    @Test
    fun `an organism that uses every mode has none free`() {
        val modes = List(Mode.MODE_COUNT) { Mode.default(it).copy(child1Mode = (it + 1) % Mode.MODE_COUNT) }
        assertEquals(-1, Genome(modes = modes).freeMode())
    }
}
