package io.github.torintheone.petri.sim

import io.github.torintheone.petri.sim.TestGenomes.genome
import io.github.torintheone.petri.sim.TestGenomes.quietWorld
import io.github.torintheone.petri.sim.TestGenomes.stepHours
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Signals, sensors, neurocytes, stemocytes and secrocytes (SPEC «Сигналы», «Этап 6»). */
class SignalTest {
    private val neuronConstant = genome(
        0,
        0 to { m -> m.copy(cellType = CellType.NEUROCYTE, splitMass = Mode.SPLIT_NEVER, neuronOutput1 = ProgrammableValue.constant(1f)) },
        1 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) },
        2 to { m -> m.copy(cellType = CellType.LIPOCYTE, splitMass = Mode.SPLIT_NEVER) },
    )

    /** Cells of [modes] in a row along x, bonded one after another; returns their slots. */
    private fun World.row(g: Genome, vararg modes: Int): List<Int> {
        val pitch = 2f * radiusOf(2f) + 2f
        val cells = modes.mapIndexed { k, mode -> indexOfUid(addCell(g, k * pitch, 0f, angle = 0f, mass = 2f, mode = mode)) }
        for (k in 0 until cells.size - 1) addBond(cells[k], cells[k + 1], 0f, World.PI_F, 2f, 1f, Bonds.KIND_ADHESION)
        return cells
    }

    private fun World.s1(slot: Int): Float = cells.signals[slot * Cells.SIGNALS]

    @Test
    fun `a signal spreads along bonds, fades with distance and stops at a lipocyte`() {
        val world = quietWorld()
        val chain = world.row(neuronConstant, 0, 1, 1, 1)
        world.stepHours(2.0)
        val levels = chain.map { world.s1(it) }
        assertTrue(levels[0] > 0.2f, "the source carries its own output: $levels")
        for (k in 1 until levels.size) assertTrue(levels[k] in 0.01f..levels[k - 1], "levels fall along the chain: $levels")

        val insulated = quietWorld()
        val blocked = insulated.row(neuronConstant, 0, 2, 1)
        insulated.stepHours(2.0)
        assertEquals(0f, insulated.s1(blocked[2]), 1e-6f)
        assertEquals(0f, insulated.s1(blocked[1]), 1e-6f)
    }

    @Test
    fun `a signal fades when its source is gone`() {
        val world = quietWorld()
        val chain = world.row(neuronConstant, 0, 1)
        world.stepHours(1.0)
        assertTrue(world.s1(chain[1]) > 0.1f)
        val partnerUid = world.cells.uid[chain[1]]
        world.removeCell(world.cells.uid[chain[0]])
        world.stepHours(1.0)
        assertTrue(abs(world.s1(world.indexOfUid(partnerUid))) < 0.01f)
    }

    @Test
    fun `more bonds dilute a source's own level`() {
        fun sourceLevel(partners: Int): Float {
            val world = quietWorld()
            val source = world.indexOfUid(world.addCell(neuronConstant, 0f, 0f, angle = 0f, mass = 2f, mode = 0))
            repeat(partners) { k ->
                val a = k * World.TWO_PI / partners
                val j = world.indexOfUid(world.addCell(neuronConstant, 21f * cos(a), 21f * sin(a), angle = a + World.PI_F, mass = 2f, mode = 1))
                world.addBond(source, j, a, 0f, 2f, 1f, Bonds.KIND_ADHESION)
            }
            world.stepHours(2.0)
            return world.s1(world.indexOfUid(world.cells.uid[source]))
        }
        val lone = sourceLevel(0)
        val one = sourceLevel(1)
        val three = sourceLevel(3)
        assertEquals(1f, lone, 0.01f)
        assertTrue(three < one && one < lone, "dilution: lone $lone, one partner $one, three $three")
    }

    @Test
    fun `a senseocyte smells nearby food and a stereocyte tells left from right`() {
        fun level(type: CellType, foodX: Float, foodY: Float): Float {
            val world = World(SubstrateSettings(foodRate = 0f, mobileFood = false, lightBrightness = 0f), 1L)
            world.previewGrowth = 0f
            val g = genome(0, 0 to { m -> m.copy(cellType = type, splitMass = Mode.SPLIT_NEVER, senseTarget = SenseTarget.FOOD, senseGain = 0.5f) })
            val i = world.indexOfUid(world.addCell(g, 0f, 0f, angle = 0f, mass = 2f))
            repeat(5) { k -> world.addFood(foodX + 3f * k, foodY, 0.3f) }
            world.stepHours(1.0)
            return world.s1(world.indexOfUid(world.cells.uid[i]))
        }
        assertTrue(level(CellType.SENSEOCYTE, 40f, 0f) > 0.3f)
        assertTrue(level(CellType.SENSEOCYTE, 40f, 0f) > level(CellType.SENSEOCYTE, 120f, 0f))
        assertEquals(0f, level(CellType.SENSEOCYTE, 400f, 0f), 1e-4f)
        // The stereocyte faces +x: food at +y is on its left (plus), at −y on its right (minus).
        assertTrue(level(CellType.STEREOCYTE, 20f, 40f) > 0.1f)
        assertTrue(level(CellType.STEREOCYTE, 20f, -40f) < -0.1f)
        assertEquals(0f, level(CellType.STEREOCYTE, 60f, 0f), 0.02f)
    }

    /**
     * SPEC «Сигналы», control scenario: a stereocyte with a flagellocyte on each side; the right one takes
     * a ≈ 0.76 of the stereocyte's channel, the left one a ≈ −0.76. It must turn towards the food.
     */
    @Test
    fun `the smart swimmer turns towards food`() {
        fun turnTowardsFood(steering: Float): Float {
            val world = World(SubstrateSettings(foodRate = 0f, mobileFood = false, lightBrightness = 0f, dishDiameterMm = 3f), 6L)
            world.previewGrowth = 0f
            val g = genome(
                0,
                0 to { m -> m.copy(cellType = CellType.STEREOCYTE, splitMass = Mode.SPLIT_NEVER, senseTarget = SenseTarget.FOOD, senseGain = 1f) },
                1 to { m ->
                    m.copy(cellType = CellType.FLAGELLOCYTE, splitMass = Mode.SPLIT_NEVER, swimForce = ProgrammableValue(SignalInput.S1, a = steering, b = 0.5f))
                },
                2 to { m ->
                    m.copy(cellType = CellType.FLAGELLOCYTE, splitMass = Mode.SPLIT_NEVER, swimForce = ProgrammableValue(SignalInput.S1, a = -steering, b = 0.5f))
                },
            )
            // Facing +y: the right flagellocyte sits at +x, the left one at −x.
            val up = World.PI_F / 2f
            val gap = 2f * world.radiusOf(2f) + 2f
            val stereo = world.indexOfUid(world.addCell(g, 0f, 0f, angle = up, mass = 2f, mode = 0))
            val right = world.indexOfUid(world.addCell(g, gap, 0f, angle = up, mass = 2f, mode = 1))
            val left = world.indexOfUid(world.addCell(g, -gap, 0f, angle = up, mass = 2f, mode = 2))
            world.addBond(stereo, right, -World.PI_F / 2f, World.PI_F / 2f, 2f, 1f, Bonds.KIND_ADHESION)
            world.addBond(stereo, left, World.PI_F / 2f, -World.PI_F / 2f, 2f, 1f, Bonds.KIND_ADHESION)
            for (i in listOf(right, left)) world.cells.organism[i] = world.cells.organism[stereo]
            // A patch of food ahead and to the right.
            repeat(12) { k -> world.addFood(70f + 6f * (k % 4), 80f + 6f * (k / 4), 0.3f) }
            val uid = world.cells.uid[stereo]
            repeat(40) { world.step() }
            // Turning right (clockwise) is towards the food.
            return -World.wrapPi(world.cells.angle[world.indexOfUid(uid)] - up)
        }
        val steered = turnTowardsFood(0.76f)
        val blind = turnTowardsFood(0f)
        assertTrue(abs(blind) < 0.05f, "without steering the swimmer goes straight: $blind")
        assertTrue(steered > 0.15f, "with steering it turns towards the food: $steered rad")
    }

    @Test
    fun `the oscillator alternates between its two channels`() {
        val world = quietWorld()
        val g = genome(
            0,
            0 to { m ->
                m.copy(
                    cellType = CellType.NEUROCYTE,
                    splitMass = Mode.SPLIT_NEVER,
                    oscillatorFrequency = 1f,
                    oscillatorChannelA = SignalChannel.S2,
                    oscillatorChannelB = SignalChannel.S3,
                )
            },
        )
        val uid = world.addCell(g, 0f, 0f, angle = 0f, mass = 2f)
        val s2 = mutableListOf<Float>()
        val s3 = mutableListOf<Float>()
        repeat(60) {
            world.step()
            val i = world.indexOfUid(uid)
            s2 += world.cells.signals[i * Cells.SIGNALS + 1]
            s3 += world.cells.signals[i * Cells.SIGNALS + 2]
        }
        assertTrue(s2.max() > 0.3f && s2.min() < -0.3f, "S2 swings both ways: ${s2.min()}..${s2.max()}")
        for (k in s2.indices) assertEquals(-s2[k], s3[k], 1e-4f)
    }

    @Test
    fun `a stemocyte switches mode when its path signal passes 0_5`() {
        val world = quietWorld()
        val g = genome(
            0,
            0 to { m -> m.copy(cellType = CellType.NEUROCYTE, splitMass = Mode.SPLIT_NEVER, neuronOutput2 = ProgrammableValue.constant(1f)) },
            1 to { m ->
                m.copy(
                    cellType = CellType.STEMOCYTE,
                    splitMass = Mode.SPLIT_NEVER,
                    path1Mode = 2,
                    path1Signal = ProgrammableValue(SignalInput.S2, a = 4f, b = 0f),
                )
            },
            2 to { m -> m.copy(cellType = CellType.PHOTOCYTE, splitMass = Mode.SPLIT_NEVER) },
        )
        val lone = world.addCell(g, 0f, 200f, angle = 0f, mass = 2f, mode = 1)
        val pair = world.row(g, 0, 1)
        val stem = world.cells.uid[pair[1]]
        world.stepHours(1.0)
        assertEquals(CellType.PHOTOCYTE, world.cellInfo(stem)!!.type)
        assertEquals(2, world.cellInfo(stem)!!.mode)
        assertEquals(CellType.STEMOCYTE, world.cellInfo(lone)!!.type)
    }

    @Test
    fun `cyanide kills other organisms but not protected ones or its own`() {
        val world = World(SubstrateSettings(foodRate = 0f, lightBrightness = 0f, aging = false, corpsesBecomeFood = false), 3L)
        val g = genome(
            0,
            0 to { m -> m.copy(cellType = CellType.SECROCYTE, splitMass = Mode.SPLIT_NEVER, secretion = Substance.CYANIDE, secretionIntensity = 1f) },
            1 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) },
            2 to { m -> m.copy(cellType = CellType.KERATINOCYTE, splitMass = Mode.SPLIT_NEVER) },
        )
        val poisoner = world.indexOfUid(world.addCell(g, 0f, 0f, angle = 0f, mass = 3f, mode = 0))
        val friend = world.indexOfUid(world.addCell(g, 24f, 0f, angle = World.PI_F, mass = 3f, mode = 1))
        world.addBond(poisoner, friend, 0f, 0f, 2f, 1f, Bonds.KIND_ADHESION)
        world.cells.organism[friend] = world.cells.organism[poisoner]
        val friendUid = world.cells.uid[friend]
        val victim = world.addCell(g, -30f, 0f, angle = 0f, mass = 3f, mode = 1)
        // A shielded stranger: bonded to a keratinocyte.
        val guarded = world.indexOfUid(world.addCell(g, 0f, 34f, angle = 0f, mass = 3f, mode = 1))
        val guard = world.indexOfUid(world.addCell(g, 0f, 58f, angle = 0f, mass = 3f, mode = 2))
        world.addBond(guarded, guard, World.PI_F / 2f, -World.PI_F / 2f, 2f, 1f, Bonds.KIND_ADHESION)
        val guardedUid = world.cells.uid[guarded]
        world.stepHours(2.0)
        assertTrue(world.indexOfUid(victim) < 0, "the stranger is poisoned")
        assertTrue(world.indexOfUid(friendUid) >= 0, "the poisoner's own organism is safe")
        assertTrue(world.indexOfUid(guardedUid) >= 0, "a keratinocyte's partner is safe")
    }

    @Test
    fun `lipase strips food coats and dissolves strangers' lipocytes into food`() {
        val world = World(SubstrateSettings(foodRate = 0f, lightBrightness = 0f, aging = false, corpsesBecomeFood = false, mobileFood = false), 4L)
        val g = genome(
            0,
            0 to { m -> m.copy(cellType = CellType.SECROCYTE, splitMass = Mode.SPLIT_NEVER, secretion = Substance.LIPASE, secretionIntensity = 1f) },
            1 to { m -> m.copy(cellType = CellType.LIPOCYTE, splitMass = Mode.SPLIT_NEVER) },
        )
        world.addCell(g, 0f, 0f, angle = 0f, mass = 3f, mode = 0)
        val fat = world.addCell(g, 30f, 0f, angle = 0f, mass = 6f, mode = 1)
        world.addFood(0f, 25f, 0.5f, coated = true)
        world.stepHours(2.0)
        assertTrue(world.indexOfUid(fat) < 0, "the lipocyte is dissolved")
        val f = world.food
        assertTrue((0 until f.count).none { f.coated[it] }, "the coat is gone")
        // Its whole store became food, even though corpses normally leave nothing here.
        val foodMass = (0 until f.count).sumOf { f.mass[it].toDouble() }
        assertTrue(foodMass > 5.0, "the lipocyte's mass became food: $foodMass")
    }
}
