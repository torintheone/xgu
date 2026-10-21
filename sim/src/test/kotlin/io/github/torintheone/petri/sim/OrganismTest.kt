package io.github.torintheone.petri.sim

import io.github.torintheone.petri.sim.TestGenomes.allFinite
import io.github.torintheone.petri.sim.TestGenomes.genome
import io.github.torintheone.petri.sim.TestGenomes.maxSpeed
import io.github.torintheone.petri.sim.TestGenomes.quietWorld
import io.github.torintheone.petri.sim.TestGenomes.totalMass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** Bonds, sharing and the shapes that grow from a genome (SPEC «Этап 1», «Этап 2»). */
class OrganismTest {
    @Test
    fun `sharing along bonds evens out mass per priority and keeps the total`() {
        val world = quietWorld()
        val g = genome(
            0,
            0 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER, nutrientPriority = 0.2f, prioritize = false) },
            1 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER, nutrientPriority = 0.5f, prioritize = false) },
            2 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER, nutrientPriority = 0.9f, prioritize = false) },
        )
        val a = world.indexOfUid(world.addCell(g, -25f, 0f, angle = 0f, mass = 3.2f, mode = 0))
        val b = world.indexOfUid(world.addCell(g, 0f, 0f, angle = 0f, mass = 1.0f, mode = 1))
        val c = world.indexOfUid(world.addCell(g, 25f, 0f, angle = 0f, mass = 0.8f, mode = 2))
        world.addBond(a, b, 0f, World.PI_F, 3f, 0.5f, Bonds.KIND_ADHESION)
        world.addBond(b, c, 0f, World.PI_F, 3f, 0.5f, Bonds.KIND_ADHESION)
        val before = world.totalMass()
        repeat(6_000) { world.step() }
        assertEquals(before, world.totalMass(), 1e-3)
        val ratios = listOf(0 to 0.2f, 1 to 0.5f, 2 to 0.9f).map { (mode, p) ->
            val i = (0 until world.cells.count).first { world.cells.mode[it] == mode }
            world.cells.mass[i] / p
        }
        val spread = ratios.max() - ratios.min()
        assertTrue(spread < 0.05f * ratios.average().toFloat(), "mass/priority should even out: $ratios")
    }

    @Test
    fun `a starving cell with prioritize on does not give away its last mass`() {
        // A low-priority cell next to a greedy one: without «Приоритизировать» it is drained almost to death.
        fun finalMass(prioritize: Boolean): Float {
            val world = quietWorld()
            val g = genome(
                0,
                0 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER, nutrientPriority = 1f, prioritize = false) },
                1 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER, nutrientPriority = 0.1f, prioritize = prioritize) },
            )
            val greedy = world.indexOfUid(world.addCell(g, -12f, 0f, angle = 0f, mass = 2.6f, mode = 0))
            val modest = world.indexOfUid(world.addCell(g, 12f, 0f, angle = 0f, mass = 1f, mode = 1))
            world.addBond(greedy, modest, 0f, World.PI_F, 3f, 0.5f, Bonds.KIND_ADHESION)
            repeat(3_000) { world.step() }
            return world.cells.mass[(0 until world.cells.count).first { world.cells.mode[it] == 1 }]
        }
        val without = finalMass(prioritize = false)
        val with = finalMass(prioritize = true)
        assertTrue(without < 0.4f, "without prioritize the cell is drained: $without")
        assertTrue(with > without + 0.2f, "with prioritize it keeps more: $with vs $without")
    }

    @Test
    fun `a chain of 50 cells with the stiffest bonds does not fly apart in 1000 steps`() {
        val world = quietWorld(settings = SubstrateSettings(dishDiameterMm = 3f))
        val g = genome(0, 0 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) })
        val mass = 1.2f
        val r = world.radiusOf(mass)
        val gap = SimConfig.ADHESIN_GAP_MAX_UM
        val pitch = 2f * r + gap
        val start = -24.5f * pitch
        val indices = (0 until 50).map { world.indexOfUid(world.addCell(g, start + it * pitch, 0f, angle = 0f, mass = mass)) }
        for (k in 0 until 49) {
            world.addBond(indices[k], indices[k + 1], 0f, World.PI_F, gap, 1f, Bonds.KIND_ADHESION)
        }
        // Kick one end so the springs have something to work against.
        world.cells.vy[indices[0]] = 5f
        world.cells.spin[indices[25]] = 2f
        repeat(1_000) { world.step() }
        assertTrue(world.allFinite())
        assertEquals(49, world.bonds.liveCount)
        val length = hypot(world.cells.x[indices[49]] - world.cells.x[indices[0]], world.cells.y[indices[49]] - world.cells.y[indices[0]])
        assertTrue(length > 0.8f * 49 * pitch && length < 1.1f * 49 * pitch, "chain length $length")
        assertTrue(world.maxSpeed() < 1f, "the chain should come to rest, max speed ${world.maxSpeed()}")
    }

    @Test
    fun `M1 into M1 plus M1 with adhesin grows into a calm colony`() {
        val world = quietWorld(settings = SubstrateSettings(dishDiameterMm = 2f))
        world.previewGrowth = 3f
        val g = genome(0, 0 to { m -> m.copy(makeAdhesin = true, splitMass = 2.5f, splitAngle = 0f) })
        world.addCell(g, 0f, 0f, angle = 0f, mass = 2f)
        var steps = 0
        while (world.cells.count < 64 && steps++ < 20_000) world.step()
        assertTrue(world.cells.count >= 64, "colony should reach 64 cells, has ${world.cells.count}")
        world.previewGrowth = 0f
        repeat(300) { world.step() }
        assertTrue(world.allFinite())
        // No explosions or jitter: everything moves slowly and nothing sits deep inside a neighbour.
        assertTrue(world.maxSpeed() < 2f, "max speed ${world.maxSpeed()}")
        val c = world.cells
        for (i in 0 until c.count) {
            for (j in i + 1 until c.count) {
                val d = hypot(c.x[i] - c.x[j], c.y[i] - c.y[j])
                assertTrue(d > 0.55f * (c.radius[i] + c.radius[j]), "cells $i and $j overlap: $d")
            }
        }
    }

    @Test
    fun `split angle 90 grows a straight chain`() {
        val world = quietWorld(settings = SubstrateSettings(dishDiameterMm = 3f))
        world.previewGrowth = 3f
        val g = genome(0, 0 to { m -> m.copy(makeAdhesin = true, splitMass = 2.5f, splitAngle = 90f, adhesinStiffness = 1f) })
        world.addCell(g, 0f, 0f, angle = 0f, mass = 2f)
        growAndSettle(world, 16)
        val order = chainOrder(world)
        assertEquals(16, order.size)
        assertTrue(turning(world, order) < 0.05f, "a straight chain should not bend: ${turning(world, order)} rad")
        // Bonded neighbours sit at the adhesin length apart.
        val c = world.cells
        for (k in 0 until order.size - 1) {
            val a = order[k]
            val b = order[k + 1]
            val gap = hypot(c.x[a] - c.x[b], c.y[a] - c.y[b]) - c.radius[a] - c.radius[b]
            assertEquals(0.5f * SimConfig.ADHESIN_GAP_MAX_UM, gap, 0.5f)
        }
    }

    @Test
    fun `child angle 15 grows the chain into an arc`() {
        // The tip keeps dividing; each new tip turns by the child angle, the cells left behind stop dividing.
        val world = quietWorld(settings = SubstrateSettings(dishDiameterMm = 3f))
        world.previewGrowth = 3f
        val g = genome(
            0,
            0 to { m ->
                m.copy(makeAdhesin = true, splitMass = 2.5f, splitAngle = 90f, child1Angle = 15f, child2Mode = 1, adhesinStiffness = 1f)
            },
            1 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) },
        )
        world.addCell(g, 0f, 0f, angle = 0f, mass = 2f)
        growAndSettle(world, 12)
        // The stem keeps feeding the tip through the bonds, so the chain may still add a cell or two.
        val order = chainOrder(world)
        assertTrue(order.size >= 12)
        // Each bond turns about 15° from the previous one.
        val perJoint = turning(world, order) / (order.size - 2)
        assertEquals(0.2618f, perJoint, 0.06f, "turn per joint")
    }

    private fun growAndSettle(world: World, cells: Int) {
        var steps = 0
        while (world.cells.count < cells && steps++ < 20_000) world.step()
        world.previewGrowth = 0f
        repeat(600) { world.step() }
        assertTrue(world.allFinite())
    }

    /** Cells of a chain from one end to the other, following the bonds. */
    private fun chainOrder(world: World): List<Int> {
        val c = world.cells
        val ends = (0 until c.count).filter { c.bondCount[it] == 1 }
        assertEquals(2, ends.size, "a chain has two ends")
        val order = mutableListOf(ends[0])
        var previous = -1
        while (order.size < c.count) {
            val current = order.last()
            var next = -1
            for (k in 0 until c.bondCount[current]) {
                val id = c.bondIds[current * Mode.MAX_BONDS + k]
                val other = if (world.bonds.a[id] == current) world.bonds.b[id] else world.bonds.a[id]
                if (other != previous && other != current) next = other
            }
            if (next < 0) break
            previous = current
            order += next
        }
        return order
    }

    /** Total turning along the chain: the sum of the angles between consecutive bonds, radians. */
    private fun turning(world: World, order: List<Int>): Float {
        val c = world.cells
        fun direction(k: Int) = atan2(c.y[order[k + 1]] - c.y[order[k]], c.x[order[k + 1]] - c.x[order[k]])
        var total = 0f
        for (k in 0 until order.size - 2) total += World.wrapPi(direction(k + 1) - direction(k))
        return abs(total)
    }

    @Test
    fun `cells stay inside the dish`() {
        val world = quietWorld(settings = SubstrateSettings(dishDiameterMm = 0.5f))
        world.previewGrowth = 4f
        world.addCell(Genome.default(), 0f, 0f, angle = 0f, mass = 3f)
        repeat(4_000) { world.step() }
        val r = world.dishRadius
        for (i in 0 until world.cells.count) {
            val d = sqrt(world.cells.x[i] * world.cells.x[i] + world.cells.y[i] * world.cells.y[i])
            assertTrue(d < r, "cell $i at $d is outside the dish of radius $r")
        }
    }
}
