package io.github.torintheone.petri.sim

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimulationRunnerTest {
    @Test
    fun `frozen world does not age but still runs commands`() {
        SimulationRunner(World()).use { runner ->
            runner.timeMode = TimeMode.FREEZE
            runner.start()
            Thread.sleep(200)
            assertEquals(0L, stepCountOf(runner))
        }
    }

    @Test
    fun `observation keeps pace with real time`() {
        SimulationRunner(World()).use { runner ->
            runner.timeMode = TimeMode.OBSERVE
            val startNanos = System.nanoTime()
            runner.start()
            Thread.sleep(500)
            val age = runner.stats.ageHours
            val realHours = (System.nanoTime() - startNanos) / 1e9 * SimConfig.OBSERVE_HOURS_PER_SECOND
            assertTrue(age > 0.0, "the world should age")
            assertTrue(age <= realHours, "age $age h ran ahead of real time $realHours h")
        }
    }

    @Test
    fun `incubation runs faster than observation`() {
        SimulationRunner(World()).use { runner ->
            runner.timeMode = TimeMode.INCUBATE
            runner.start()
            Thread.sleep(300)
            assertTrue(runner.stats.ageHours > 0.3 * SimConfig.OBSERVE_HOURS_PER_SECOND)
        }
    }

    @Test
    fun `pause stops the clock`() {
        SimulationRunner(World()).use { runner ->
            runner.timeMode = TimeMode.INCUBATE
            runner.start()
            Thread.sleep(100)
            runner.isPaused = true
            val pausedSteps = stepCountOf(runner)
            Thread.sleep(200)
            assertEquals(pausedSteps, stepCountOf(runner))
        }
    }

    @Test
    fun `listener hears about new snapshots`() {
        SimulationRunner(World()).use { runner ->
            val published = CountDownLatch(3)
            runner.publishListener = { published.countDown() }
            runner.timeMode = TimeMode.OBSERVE
            runner.start()
            assertTrue(published.await(5, TimeUnit.SECONDS))
        }
    }

    /** Reads the step count on the simulation thread, the only thread allowed to touch the world. */
    private fun stepCountOf(runner: SimulationRunner): Long {
        val steps = AtomicLong(-1)
        val done = CountDownLatch(1)
        runner.post { world ->
            steps.set(world.stepCount)
            done.countDown()
        }
        assertTrue(done.await(5, TimeUnit.SECONDS), "the simulation thread did not answer")
        return steps.get()
    }
}
