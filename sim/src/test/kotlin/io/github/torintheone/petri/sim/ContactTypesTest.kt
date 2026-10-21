package io.github.torintheone.petri.sim

import io.github.torintheone.petri.sim.TestGenomes.genome
import io.github.torintheone.petri.sim.TestGenomes.quietWorld
import io.github.torintheone.petri.sim.TestGenomes.stepHours
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.hypot

/** Virocytes, gametes and ciliocytes (SPEC «Этап 7»). */
class ContactTypesTest {
    @Test
    fun `a virocyte rewrites a stranger's genome and mode, but not a protected cell`() {
        // High salinity halves upkeep, so the expensive virocyte lives through the test.
        val world = World(SubstrateSettings(foodRate = 0f, lightBrightness = 0f, aging = false, corpsesBecomeFood = false, salinity = 1f), 3L)
        val virus = genome(
            0,
            0 to { m -> m.copy(cellType = CellType.VIROCYTE, splitMass = Mode.SPLIT_NEVER, virusMode = 5) },
            5 to { m -> m.copy(cellType = CellType.LIPOCYTE, splitMass = Mode.SPLIT_NEVER, child1Mode = 6, child2Mode = 6, red = 0.9f) },
            6 to { m -> m.copy(cellType = CellType.LIPOCYTE, splitMass = Mode.SPLIT_NEVER, red = 0.8f) },
        )
        val host = genome(
            0,
            0 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) },
            1 to { m -> m.copy(cellType = CellType.KERATINOCYTE, splitMass = Mode.SPLIT_NEVER) },
        )
        world.addCell(virus, 0f, 0f, angle = 0f, mass = 3f)
        val victim = world.addCell(host, 23f, 0f, angle = 0f, mass = 3f)
        val shielded = world.indexOfUid(world.addCell(host, -23f, 0f, angle = 0f, mass = 3f))
        val keratinocyte = world.indexOfUid(world.addCell(host, -46f, 0f, angle = 0f, mass = 3f, mode = 1))
        world.addBond(shielded, keratinocyte, World.PI_F, 0f, 2f, 1f, Bonds.KIND_ADHESION)
        val shieldedUid = world.cells.uid[shielded]
        world.stepHours(4.0)

        val infected = world.cellInfo(victim)!!
        assertEquals(5, infected.mode)
        assertEquals(CellType.LIPOCYTE, infected.type)
        val genome = world.genomeOfCell(victim)!!
        assertEquals(virus.modes[5], genome.modes[5])
        assertEquals(virus.modes[6], genome.modes[6])
        assertEquals(host.modes[1], genome.modes[1], "modes the virus does not reach stay the host's")
        assertEquals(0, world.cellInfo(shieldedUid)!!.mode, "a keratinocyte's partner is immune")
    }

    @Test
    fun `two gametes of different organisms fuse into one cell of the fusion mode`() {
        val world = quietWorld()
        val a = genome(
            0,
            0 to { m -> m.copy(cellType = CellType.GAMETE, fusionMode = 3, red = 0.1f) },
            3 to { m -> m.copy(cellType = CellType.PHOTOCYTE, splitMass = Mode.SPLIT_NEVER, red = 0.11f) },
        )
        val b = genome(
            0,
            0 to { m -> m.copy(cellType = CellType.GAMETE, fusionMode = 3, red = 0.2f) },
            3 to { m -> m.copy(cellType = CellType.BUOYOCYTE, splitMass = Mode.SPLIT_NEVER, red = 0.22f) },
        )
        val first = world.addCell(a, 0f, 0f, angle = 0f, mass = 1.5f)
        val second = world.addCell(b, 15f, 0f, angle = 0f, mass = 1.2f)
        world.stepHours(0.5)
        assertEquals(1, world.cellCount, "the gametes merged")
        val zygote = world.cellInfo(first) ?: world.cellInfo(second)!!
        assertEquals(3, zygote.mode)
        assertEquals(2.7f, zygote.mass, 0.01f)
        val genome = world.genomeOfCell(zygote.uid)!!
        for (k in 0 until Mode.MODE_COUNT) {
            assertTrue(genome.modes[k] == a.modes[k] || genome.modes[k] == b.modes[k], "mode $k comes from one parent")
        }
        assertTrue(zygote.type == CellType.PHOTOCYTE || zygote.type == CellType.BUOYOCYTE)
    }

    @Test
    fun `gametes never divide and do not fuse within one organism`() {
        val world = quietWorld()
        world.previewGrowth = 2f
        val g = genome(0, 0 to { m -> m.copy(cellType = CellType.GAMETE, splitMass = 1f) })
        val one = world.indexOfUid(world.addCell(g, 0f, 0f, angle = 0f, mass = 3f))
        val two = world.indexOfUid(world.addCell(g, 22f, 0f, angle = 0f, mass = 3f))
        world.cells.organism[two] = world.cells.organism[one]
        world.stepHours(3.0)
        assertEquals(2, world.cellCount)
    }

    @Test
    fun `a ciliocyte crawls along the wall it is pressed against`() {
        fun travelled(speed: Float): Float {
            // Gravity holds the ciliocyte against the bottom of a small dish.
            val world = quietWorld(settings = SubstrateSettings(dishDiameterMm = 0.5f, gravity = 1f, dynamicFriction = 0f))
            val crawler = genome(
                0,
                0 to { m ->
                    m.copy(
                        cellType = CellType.CILIOCYTE,
                        splitMass = Mode.SPLIT_NEVER,
                        leftCilia = ProgrammableValue.constant(speed),
                        rightCilia = ProgrammableValue.constant(speed),
                    )
                },
            )
            val bottom = -world.dishRadius + world.radiusOf(2f) + 0.5f
            val uid = world.addCell(crawler, 0f, bottom, angle = 0f, mass = 2f)
            world.stepHours(0.5)
            val i = world.indexOfUid(uid)
            return hypot(world.cells.x[i], world.cells.y[i] - bottom)
        }
        val still = travelled(0f)
        val moving = travelled(1f)
        assertTrue(still < 2f, "without cilia it stays: $still")
        assertTrue(moving > 15f, "with cilia it crawls along the wall: $moving")
    }
}
