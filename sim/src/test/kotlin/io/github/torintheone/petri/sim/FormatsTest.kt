package io.github.torintheone.petri.sim

import io.github.torintheone.petri.sim.TestGenomes.genome
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Genome and dish files (SPEC «Сохранение»). */
class FormatsTest {
    @Test
    fun `a genome survives writing and reading`() {
        val genome = randomGenome(Rng(11L))
        val text = GenomeCodec.encode(genome)
        assertTrue(text.startsWith("{\"formatVersion\":1,"), text.take(40))
        assertEquals(genome, GenomeCodec.decode(text))
    }

    @Test
    fun `every preset survives writing and reading`() {
        for (preset in Preset.entries) {
            val genome = preset.genome(preset.name)
            assertEquals(genome, GenomeCodec.decode(GenomeCodec.encode(genome)), preset.name)
        }
    }

    @Test
    fun `a genome file lists every parameter`() {
        val text = GenomeCodec.encode(Genome.default())
        for (key in listOf("name", "initialMode", "modes", "cellType", "splitMass", "cytoskeleton", "swimForce", "leftCilia")) {
            assertTrue("\"$key\"" in text, "missing $key")
        }
    }

    @Test
    fun `broken genome files give a clear error instead of a crash`() {
        fun problem(text: String) = assertThrows<FormatException> { GenomeCodec.decode(text) }.problem
        assertEquals(FormatException.Problem.WRONG_KIND, problem("not json at all"))
        assertEquals(FormatException.Problem.WRONG_KIND, problem("[1, 2, 3]"))
        assertEquals(FormatException.Problem.WRONG_KIND, problem("{\"name\": \"no modes\"}"))
        assertEquals(FormatException.Problem.WRONG_KIND, problem(GenomeCodec.encode(Genome.default()).dropLast(10)))
        assertEquals(FormatException.Problem.DAMAGED, problem("{\"formatVersion\": 1, \"modes\": 5}"))
        assertEquals(FormatException.Problem.DAMAGED, problem("{\"formatVersion\": 1, \"modes\": [{\"splitMass\": \"heavy\"}]}"))
        assertEquals(FormatException.Problem.DAMAGED, problem("{\"formatVersion\": 0, \"modes\": []}"))
        assertEquals(FormatException.Problem.NEWER_VERSION, problem("{\"formatVersion\": 99, \"modes\": []}"))
    }

    @Test
    fun `odd but readable genome files are repaired`() {
        val text = """
            {"formatVersion": 1, "name": "hand made", "initialMode": 77, "future": true,
             "modes": [{"cellType": "SOMETHING_NEW", "splitMass": 99, "child1Mode": -3, "splitAngle": 400}]}
        """.trimIndent()
        val genome = GenomeCodec.decode(text)
        assertEquals("hand made", genome.name)
        assertEquals(Mode.MODE_COUNT, genome.modes.size)
        assertEquals(Mode.MODE_COUNT - 1, genome.initialMode)
        val first = genome.modes[0]
        assertEquals(CellType.PHAGOCYTE, first.cellType)
        assertEquals(Mode.SPLIT_NEVER, first.splitMass)
        assertEquals(0, first.child1Mode)
        assertEquals(40f, first.splitAngle, 1e-3f)
        assertEquals(Mode.default(5), genome.modes[5])
        assertTrue(genome.problems().isEmpty())
    }

    @Test
    fun `a file without a version is read as version 1`() {
        val text = GenomeCodec.encode(Genome.default()).replace("\"formatVersion\":1,", "")
        assertEquals(Genome.default(), GenomeCodec.decode(text))
    }

    @Test
    fun `a dish survives writing and reading and then runs on exactly the same`() {
        val original = busyWorld()
        val bytes = DishCodec.encode(original.saveState())
        val loaded = DishCodec.decode(bytes)
        assertSameDish(original, loaded)
        repeat(600) {
            original.step()
            loaded.step()
        }
        assertSameDish(original, loaded)
    }

    @Test
    fun `a saved dish keeps the free slots of the bond and genome pools`() {
        val world = busyWorld()
        // Make holes in the bond pool and in the genome registry.
        val bondIds = (0 until world.bonds.size).filter { world.bonds.alive[it] }
        world.removeBond(bondIds[0])
        world.removeBond(bondIds[bondIds.size / 2])
        val loner = world.addCell(genome(0, 0 to { it.copy(red = 0.123f) }), 0f, -300f)
        world.removeCell(loner)
        val state = world.saveState()
        assertTrue(state.bonds.free.size >= 2)
        assertTrue(state.genomes.freeSlots.isNotEmpty())
        val loaded = state.toWorld()
        repeat(300) {
            world.step()
            loaded.step()
        }
        assertSameDish(world, loaded)
    }

    @Test
    fun `broken dish files give a clear error instead of a crash`() {
        fun problem(data: ByteArray) = assertThrows<FormatException> { DishCodec.decode(data) }.problem
        val good = DishCodec.encode(busyWorld().saveState())
        assertEquals(FormatException.Problem.WRONG_KIND, problem("hello".toByteArray()))
        assertEquals(FormatException.Problem.WRONG_KIND, problem(GenomeCodec.encode(Genome.default()).toByteArray()))
        assertEquals(FormatException.Problem.WRONG_KIND, problem(gzip(GenomeCodec.encode(Genome.default()))))
        assertEquals(FormatException.Problem.DAMAGED, problem(good.copyOf(good.size / 2)))

        val json = Formats.compact.encodeToString(DishState.serializer(), busyWorld().saveState())
        assertEquals(FormatException.Problem.NEWER_VERSION, problem(gzip(json.replace("\"formatVersion\":1", "\"formatVersion\":2"))))
        // A bond that points at a cell that does not exist.
        val state = busyWorld().saveState()
        val a = state.bonds.a.copyOf().also { it[state.bonds.alive.indexOfFirst { alive -> alive }] = 9_999 }
        val broken = DishState(
            state.formatVersion, state.settings, state.stepCount, state.rngState, state.lightAngle, state.nextUid,
            state.nextOrganism, state.foodDebt, state.genomes, state.cells,
            BondTable(
                state.bonds.alive, a, state.bonds.b, state.bonds.anchorA, state.bonds.anchorB, state.bonds.rest,
                state.bonds.stiffness, state.bonds.kind, state.bonds.free,
            ),
            state.food, state.history,
        )
        assertEquals(FormatException.Problem.DAMAGED, problem(DishCodec.encode(broken)))
    }

    @Test
    fun `a sterile dish survives writing and reading`() {
        val world = World(SubstrateSettings(), seed = 5L)
        val loaded = DishCodec.decode(DishCodec.encode(world.saveState()))
        assertEquals(0, loaded.cellCount)
        repeat(120) {
            world.step()
            loaded.step()
        }
        assertSameDish(world, loaded)
    }

    /**
     * Dish files leave out values equal to their defaults, so changing a default changes the meaning of old files.
     * If this test fails after a deliberate change: raise [DishCodec.VERSION] and add a migration that writes the
     * old default into older files, then update the expected text here.
     */
    @Test
    fun `defaults of saved classes stay put`() {
        val mode = Formats.full.encodeToString(Mode.serializer(), Mode())
        val settings = Formats.full.encodeToString(SubstrateSettings.serializer(), SubstrateSettings())
        println("DEFAULTS mode: $mode")
        println("DEFAULTS settings: $settings")
        assertEquals(resource("defaults-mode.json"), mode)
        assertEquals(resource("defaults-settings.json"), settings)
    }

    @Test
    fun `genomes inside a dish file are short`() {
        val state = busyWorld().saveState()
        val genomeText = Formats.compact.encodeToString(SavedGenome.serializer(), state.genomes.slots.first { it != null }!!)
        assertTrue(genomeText.length < GenomeCodec.encode(Genome.default()).length / 4, "compact genome: ${genomeText.length} chars")
        assertNotEquals(0, DishCodec.encode(state).size)
    }

    private fun busyWorld(): World {
        val world = World(SubstrateSettings(foodRate = 0.5f, foodCoat = 0.3f), seed = 99L)
        val chain = genome(0, 0 to { m -> m.copy(makeAdhesin = true, splitAngle = 90f, splitMass = 2f) })
        val glue = genome(0, 0 to { m -> m.copy(cellType = CellType.GLUEOCYTE, splitMass = Mode.SPLIT_NEVER) })
        world.populate(Genome.default(), cellCount = 20, foodFraction = 0.3f)
        repeat(4) { world.addCell(chain, -200f + it * 120f, 150f, mass = 3f) }
        world.addCell(glue, world.dishRadius - 20f, 0f, mass = 3f)
        repeat(3) { k -> world.cells.signals[k * Cells.SIGNALS + k] = 0.25f * (k + 1) }
        repeat(1_500) { world.step() }
        assertTrue(world.bonds.liveCount > 4, "the test dish should have bonds")
        return world
    }

    private fun assertSameDish(expected: World, actual: World) {
        val a = expected.saveState()
        val b = actual.saveState()
        assertEquals(Formats.compact.encodeToString(DishState.serializer(), a), Formats.compact.encodeToString(DishState.serializer(), b))
        assertArrayEquals(a.cells.x, b.cells.x)
        assertArrayEquals(a.cells.y, b.cells.y)
        assertEquals(expected.ageHours, actual.ageHours)
        assertEquals(expected.speciesCount, actual.speciesCount)
    }

    private fun gzip(text: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(text.toByteArray()) }
        return bytes.toByteArray()
    }

    private fun randomGenome(rng: Rng): Genome {
        val modes = List(Mode.MODE_COUNT) { index ->
            var mode = Mode.default(index).copy(
                cellType = CellType.of(rng.nextInt(CellType.entries.size)),
                makeAdhesin = rng.nextBoolean(),
                child1Mode = rng.nextInt(Mode.MODE_COUNT),
                child2Mode = rng.nextInt(Mode.MODE_COUNT),
                child1KeepBonds = rng.nextBoolean(),
                child2Mirror = rng.nextBoolean(),
                prioritize = rng.nextBoolean(),
                swimForce = ProgrammableValue(SignalInput.S2, ValueShape.STEP, 0.1f, -0.2f, 0.3f),
            )
            for (number in ModeNumbers.ALL) {
                val value = number.range.start + rng.nextFloat() * (number.range.endInclusive - number.range.start)
                mode = number.set(mode, if (number.isInteger) value.toInt().toFloat() else value)
            }
            mode
        }
        return Genome(name = "Случайный «геном» №1", initialMode = rng.nextInt(Mode.MODE_COUNT), modes = modes).sanitized()
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResource("/$name")) { "missing test resource $name" }.readText().trim()
}
