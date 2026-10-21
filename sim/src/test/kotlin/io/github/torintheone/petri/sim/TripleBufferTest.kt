package io.github.torintheone.petri.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TripleBufferTest {
    private class Slot {
        var value = 0L
        var mirror = 0L
    }

    @Test
    fun `reader sees nothing new until the writer publishes`() {
        val buffer = TripleBuffer(::Slot)
        val before = buffer.latest()
        buffer.back().value = 7
        assertSame(before, buffer.latest())
        buffer.publish()
        assertEquals(7L, buffer.latest().value)
    }

    @Test
    fun `reader gets the newest of several publishes and keeps it`() {
        val buffer = TripleBuffer(::Slot)
        for (value in 1L..5L) {
            buffer.back().value = value
            buffer.publish()
        }
        assertEquals(5L, buffer.latest().value)
        assertEquals(5L, buffer.latest().value)
    }

    @Test
    fun `concurrent reader never sees a torn or older snapshot`() {
        val buffer = TripleBuffer(::Slot)
        val last = 200_000L
        val writer = Thread {
            for (value in 1L..last) {
                val slot = buffer.back()
                slot.value = value
                slot.mirror = -value
                buffer.publish()
            }
        }
        writer.start()
        var seen = 0L
        while (seen < last) {
            val slot = buffer.latest()
            val value = slot.value
            assertEquals(-value, slot.mirror, "torn snapshot")
            assertTrue(value >= seen, "snapshot $value is older than $seen")
            seen = value
        }
        writer.join()
    }
}
