package io.github.torintheone.petri.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WorldTest {
    @Test
    fun `each step adds one game minute`() {
        val world = World()
        repeat(90) { world.step() }
        assertEquals(90L, world.stepCount)
        assertEquals(1.5, world.ageHours, 1e-9)
    }

    @Test
    fun `snapshot carries age and dish radius`() {
        val world = World(SubstrateSettings(dishDiameterMm = 2f))
        repeat(60) { world.step() }
        val snapshot = WorldSnapshot()
        world.writeSnapshot(snapshot)
        assertEquals(1.0, snapshot.ageHours, 1e-9)
        assertEquals(1_000f, snapshot.dishRadius)
        assertEquals(0, snapshot.cellCount)
        assertEquals(0, snapshot.foodCount)
    }

    @Test
    fun `dish diameter outside the allowed range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SubstrateSettings(dishDiameterMm = 5f) }
        assertThrows(IllegalArgumentException::class.java) { SubstrateSettings(dishDiameterMm = 0.1f) }
    }
}
