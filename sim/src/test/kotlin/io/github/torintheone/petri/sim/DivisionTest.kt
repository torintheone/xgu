package io.github.torintheone.petri.sim

import io.github.torintheone.petri.sim.TestGenomes.genome
import io.github.torintheone.petri.sim.TestGenomes.quietWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sqrt

/** SPEC «Геометрия деления»: positions, angles, mirroring and bond inheritance. */
class DivisionTest {
    private fun World.stepUntilCells(count: Int, maxSteps: Int = 2_000) {
        var steps = 0
        while (cells.count < count && steps++ < maxSteps) step()
        assertEquals(count, cells.count, "the cell should have divided")
    }

    private fun World.indexWhere(predicate: (Int) -> Boolean): Int = (0 until cells.count).first(predicate)

    @Test
    fun `children land on both sides of the split plane and just touch`() {
        val world = quietWorld()
        val g = genome(0, 0 to { m -> m.copy(splitMass = 2.5f, splitAngle = 90f) })
        world.addCell(g, 0f, 0f, angle = 0f, mass = 3f)
        world.stepUntilCells(2)
        val front = world.indexWhere { world.cells.x[it] > 0f }
        val back = world.indexWhere { world.cells.x[it] < 0f }
        val r = SimConfig.RADIUS_PER_SQRT_NG * sqrt(1.5f)
        assertEquals(1.5f, world.cells.mass[front], 1e-3f)
        assertEquals(1.5f, world.cells.mass[back], 1e-3f)
        assertEquals(r, world.cells.x[front], 0.05f)
        assertEquals(-r, world.cells.x[back], 0.05f)
        assertEquals(0f, world.cells.y[front], 0.05f)
        assertEquals(0f, world.cells.angle[front], 1e-3f)
        assertEquals(0f, world.cells.angle[back], 1e-3f)
    }

    @Test
    fun `split ratio shares the mass and keeps the centre of mass`() {
        val world = quietWorld()
        val g = genome(0, 0 to { m -> m.copy(splitMass = 2.5f, splitAngle = 90f, splitRatio = 0.25f) })
        world.addCell(g, 0f, 0f, angle = 0f, mass = 3f)
        world.stepUntilCells(2)
        val child1 = world.indexWhere { world.cells.x[it] > 0f }
        val child2 = world.indexWhere { world.cells.x[it] < 0f }
        assertEquals(0.75f, world.cells.mass[child1], 1e-3f)
        assertEquals(2.25f, world.cells.mass[child2], 1e-3f)
        val centre = world.cells.x[child1] * world.cells.mass[child1] + world.cells.x[child2] * world.cells.mass[child2]
        assertEquals(0f, centre, 0.05f)
        val gap = world.cells.x[child1] - world.cells.x[child2]
        val touching = world.radiusOf(0.75f) + world.radiusOf(2.25f)
        assertEquals(touching, gap, 0.05f)
    }

    @Test
    fun `child angles turn counter-clockwise, mirrored ones clockwise`() {
        val world = quietWorld()
        val g = genome(
            0,
            0 to { m -> m.copy(splitMass = 2.5f, splitAngle = 90f, child1Angle = 30f, child2Angle = 30f, child2Mirror = true) },
        )
        world.addCell(g, 0f, 0f, angle = 0f, mass = 3f)
        world.stepUntilCells(2)
        val child1 = world.indexWhere { world.cells.x[it] > 0f }
        val child2 = world.indexWhere { world.cells.x[it] < 0f }
        assertEquals((PI / 6).toFloat(), world.cells.angle[child1], 1e-3f)
        assertEquals((-PI / 6).toFloat(), world.cells.angle[child2], 1e-3f)
        assertFalse(world.cells.isMirrored(child1))
        assertTrue(world.cells.isMirrored(child2))
    }

    @Test
    fun `a mirrored cell splits to the mirror side`() {
        val g = genome(
            0,
            0 to { m -> m.copy(splitMass = 2.5f, splitAngle = 0f, child1Mode = 1, child2Mode = 2, child1Angle = 45f) },
            1 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) },
            2 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) },
        )
        for (mirrored in listOf(false, true)) {
            val world = quietWorld()
            val uid = world.addCell(g, 0f, 0f, angle = 0f, mass = 3f)
            if (mirrored) {
                val i = world.indexOfUid(uid)
                world.cells.flags[i] = world.cells.flags[i] or Cells.FLAG_MIRRORED
            }
            world.stepUntilCells(2)
            val child1 = world.indexWhere { world.cells.mode[it] == 1 }
            // Split angle 0 puts child 1 on the right of the front; a mirrored cell puts it on the left.
            if (mirrored) assertTrue(world.cells.y[child1] > 0f) else assertTrue(world.cells.y[child1] < 0f)
            val turn = (PI / 4).toFloat()
            assertEquals(if (mirrored) -turn else turn, world.cells.angle[child1], 1e-3f)
            assertEquals(mirrored, world.cells.isMirrored(child1))
        }
    }

    @Test
    fun `bonds go to the child on their side, or break when that child does not keep them`() {
        for (keep in listOf(true, false)) {
            val world = quietWorld()
            // The partners have no nutrient priority, so sharing cannot drain the mother below her split mass.
            val g = genome(
                0,
                0 to { m -> m.copy(splitMass = 2.5f, splitAngle = 90f, child1KeepBonds = keep) },
                1 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER, nutrientPriority = 0f, prioritize = false) },
            )
            val mother = world.addCell(g, 0f, 0f, angle = 0f, mass = 3f)
            val front = world.addCell(g, 30f, 0f, angle = PI.toFloat(), mass = 2f, mode = 1)
            val back = world.addCell(g, -30f, 0f, angle = 0f, mass = 2f, mode = 1)
            val m = world.indexOfUid(mother)
            world.addBond(m, world.indexOfUid(front), 0f, 0f, 10f, 0.5f, Bonds.KIND_ADHESION)
            world.addBond(m, world.indexOfUid(back), PI.toFloat(), 0f, 10f, 0.5f, Bonds.KIND_ADHESION)
            world.stepUntilCells(4)

            val frontIndex = world.indexOfUid(front)
            val backIndex = world.indexOfUid(back)
            val child1 = world.indexWhere { it != frontIndex && it != backIndex && world.cells.x[it] > 0f }
            val child2 = world.indexWhere { it != frontIndex && it != backIndex && world.cells.x[it] < 0f }
            assertEquals(keep, world.bonded(child1, frontIndex), "front bond with keep=$keep")
            assertFalse(world.bonded(child2, frontIndex))
            assertTrue(world.bonded(child2, backIndex))
            assertFalse(world.bonded(child1, backIndex))
        }
    }

    @Test
    fun `adhesin joins the two children with this mode's length and stiffness`() {
        val world = quietWorld()
        val g = genome(
            0,
            0 to { m -> m.copy(splitMass = 2.5f, splitAngle = 90f, makeAdhesin = true, adhesinLength = 0.3f, adhesinStiffness = 0.8f, child1Mode = 1, child2Mode = 1) },
            1 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) },
        )
        world.addCell(g, 0f, 0f, angle = 0f, mass = 3f)
        world.stepUntilCells(2)
        assertTrue(world.bonded(0, 1))
        val id = (0 until world.bonds.size).first { world.bonds.alive[it] }
        assertEquals(0.3f * SimConfig.ADHESIN_GAP_MAX_UM, world.bonds.rest[id], 1e-4f)
        assertEquals(0.8f, world.bonds.stiffness[id], 1e-6f)
        // The attachment points face each other.
        val i = world.bonds.a[id]
        val j = world.bonds.b[id]
        val pointI = world.cells.angle[i] + world.bonds.anchorA[id]
        val towardsJ = kotlin.math.atan2(world.cells.y[j] - world.cells.y[i], world.cells.x[j] - world.cells.x[i])
        assertEquals(0f, World.wrapPi(pointI - towardsJ), 1e-3f)
        assertEquals(world.cells.organism[i], world.cells.organism[j])
    }

    @Test
    fun `a child lighter than the minimum dies at once`() {
        val world = quietWorld()
        val g = genome(0, 0 to { m -> m.copy(splitMass = 1f, splitRatio = 0.1f, splitAngle = 90f) })
        val mother = world.addCell(g, 0f, 0f, angle = 0f, mass = 2f)
        repeat(40) { world.step() }
        // Child 1 got 0.2 ng and died in the same step; child 2 carries on with 1.8 ng.
        assertEquals(1, world.cells.count)
        assertTrue(world.cells.uid[0] != mother)
        assertEquals(1.8f, world.cells.mass[0], 1e-3f)
        assertTrue(hypot(world.cells.x[0], world.cells.y[0]) < 20f)
    }
}
