package io.github.torintheone.petri.sim

import io.github.torintheone.petri.sim.TestGenomes.stepHours
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** Radiation, mutations and random infection (SPEC «Мутации и эволюция», «Этап 5»). */
class MutationTest {
    @Test
    fun `a genome stays valid after 10 000 mutations`() {
        val rng = Rng(77L)
        val settings = SubstrateSettings()
        var genome = Preset.SWIMMER.genome("swimmer")
        var mode = genome.initialMode
        repeat(10_000) {
            genome = Mutator.mutate(genome, mode, rng, settings)
            assertTrue(genome.problems().isEmpty(), "invalid after ${it + 1} mutations: ${genome.problems()}")
            assertEquals(Mode.MODE_COUNT, genome.modes.size)
            // The cell may follow its children's links, as a real lineage would.
            mode = genome.modes[mode].child1Mode
        }
        // After so many mutations the genome has visibly changed.
        assertNotEquals(Preset.SWIMMER.genome("swimmer"), genome)
        assertEquals(genome, GenomeCodec.decode(GenomeCodec.encode(genome)))
    }

    @Test
    fun `point mutations change only numbers`() {
        val rng = Rng(5L)
        val settings = SubstrateSettings(pointMutationsOnly = true)
        val original = Preset.ARC.genome("arc")
        var genome = original
        repeat(2_000) { genome = Mutator.mutate(genome, 0, rng, settings) }
        assertTrue(onlyNumbersDiffer(original, genome), "point mutations must not touch types, links or switches")
        assertNotEquals(original, genome)
    }

    @Test
    fun `structural mutations use only the gene pool`() {
        val rng = Rng(9L)
        val pool = setOf(CellType.PHOTOCYTE, CellType.LIPOCYTE)
        val settings = SubstrateSettings(genePool = pool)
        var genome = Genome.default()
        repeat(3_000) { genome = Mutator.mutate(genome, 0, rng, settings) }
        val types = genome.modes.map { it.cellType }.toSet()
        assertTrue((types - pool - CellType.PHAGOCYTE).isEmpty(), "types outside the pool: $types")
        assertTrue(types.any { it in pool }, "the pool types should appear: $types")
    }

    @Test
    fun `random genomes are valid and use only the gene pool`() {
        val rng = Rng(3L)
        val pool = setOf(CellType.FLAGELLOCYTE, CellType.PHAGOCYTE, CellType.GLUEOCYTE)
        repeat(500) {
            val genome = Mutator.randomGenome(rng, pool)!!
            assertTrue(genome.problems().isEmpty())
            val used = genome.reachableModes(genome.initialMode)
            assertTrue(used.all { genome.modes[it].cellType in pool })
        }
        assertEquals(null, Mutator.randomGenome(rng, emptySet()))
    }

    @Test
    fun `weak radiation brings visible variety within 50 hours`() {
        val world = World(SubstrateSettings(radiation = 0.04f), 12L)
        val start = Preset.PHOTOCYTE_COLONY.genome("colony")
        world.populate(Genome.default(), cellCount = 40, foodFraction = 0.5f)
        repeat(4) { world.addCell(start, -150f + 100f * it, 300f, mass = 3f) }
        world.stepHours(50.0)
        assertTrue(world.cellCount > 100, "the dish is alive: ${world.cellCount}")
        assertTrue(world.speciesCount >= 10, "mutants should appear: ${world.speciesCount} species")
        // Colours drift: some living cells differ clearly from the colours their ancestors started with.
        val original = listOf(Genome.default(), start).flatMap { g -> g.modes.map { Triple(it.red, it.green, it.blue) } }.toSet()
        val c = world.cells
        val shifted = (0 until c.count).count { i ->
            val m = world.genomes[c.genome[i]].modes[c.mode[i]]
            original.none { (r, g, b) -> abs(r - m.red) + abs(g - m.green) + abs(b - m.blue) < 0.05f }
        }
        assertTrue(shifted > 0, "some cells should have a new colour")
    }

    @Test
    fun `full radiation wrecks the population`() {
        fun cellsAfter(radiation: Float): Int {
            val world = World(SubstrateSettings(radiation = radiation), 31L)
            world.populate(Preset.SWIMMER.genome("swimmer"), cellCount = 30, foodFraction = 0.5f)
            world.stepHours(50.0)
            return world.cellCount
        }
        val calm = cellsAfter(0f)
        val burnt = cellsAfter(1f)
        assertTrue(burnt < calm / 2, "radiation 1 should cripple the population: $burnt vs $calm")
    }

    @Test
    fun `random infection brings newcomers from the gene pool`() {
        val pool = setOf(CellType.PHOTOCYTE, CellType.BUOYOCYTE)
        val world = World(SubstrateSettings(randomInfection = true, genePool = pool, foodRate = 0f), 8L)
        world.stepHours(40.0)
        assertTrue(world.speciesCount >= 3, "several newcomers in 40 h: ${world.speciesCount}")
        for (slot in world.genomes.liveSlots()) {
            val genome = world.genomes[slot]
            assertTrue(genome.reachableModes(genome.initialMode).all { genome.modes[it].cellType in pool })
        }
    }

    @Test
    fun `a mutated cell gets a new genome whose parent is the old one`() {
        val world = World(SubstrateSettings(radiation = 1f, foodRate = 0f, lightBrightness = 0f), 2L)
        val uid = world.addCell(Genome.default(), 0f, 0f, mass = 3f)
        val firstId = world.cellInfo(uid)!!.genomeId
        var steps = 0
        while (world.cellInfo(uid)?.genomeId == firstId && steps++ < 600) world.step()
        val info = world.cellInfo(uid)
        if (info != null) {
            assertNotEquals(firstId, info.genomeId)
            assertTrue(info.parentGenomeId >= firstId)
        }
    }

    /** True when [genome] differs from [original] only in numbers, not in types, links, switches or inputs. */
    private fun onlyNumbersDiffer(original: Genome, genome: Genome): Boolean =
        original.initialMode == genome.initialMode && original.modes.zip(genome.modes).all { (a, b) ->
            ModeNumbers.ALL.fold(b) { mode, number -> number.set(mode, number.get(a)) } == a
        }
}
