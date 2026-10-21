package io.github.torintheone.petri.sim

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.LockSupport

/** How fast game time flows (SPEC «Режимы времени»). */
enum class TimeMode {
    /** «Заморозка»: everything stands still. */
    FREEZE,

    /** «Наблюдение»: [SimConfig.OBSERVE_HOURS_PER_SECOND] game hours per real second. */
    OBSERVE,

    /** «Инкубация»: as fast as the device can go. */
    INCUBATE,
}

/** Numbers for the status panel. Replaced as a whole, never mutated, so any thread may read them. */
data class SimStats(
    val ageHours: Double,
    val cellCount: Int,
    val foodCount: Int,
) {
    companion object {
        val EMPTY = SimStats(ageHours = 0.0, cellCount = 0, foodCount = 0)
    }
}

/**
 * Runs a [World] on its own background thread, so the UI and the renderer never wait for the simulation.
 *
 * Other threads change the world only through [post]; the renderer reads [latestSnapshot], the UI reads [stats].
 */
class SimulationRunner(private val world: World) : AutoCloseable {
    private val snapshots = TripleBuffer(::WorldSnapshot)
    private val commands = ConcurrentLinkedQueue<(World) -> Unit>()

    @Volatile
    private var running = false

    @Volatile
    private var thread: Thread? = null

    /** Requested speed of game time. */
    @Volatile
    var timeMode: TimeMode = TimeMode.OBSERVE
        set(value) {
            field = value
            wake()
        }

    /** True while the app is hidden: the world stands still whatever [timeMode] says. */
    @Volatile
    var isPaused: Boolean = false
        set(value) {
            field = value
            wake()
        }

    /** The latest numbers for the status panel. */
    @Volatile
    var stats: SimStats = SimStats.EMPTY
        private set

    /** Called on the simulation thread after each new snapshot, e.g. to request a redraw. */
    @Volatile
    var publishListener: (() -> Unit)? = null

    fun start() {
        check(thread == null) { "SimulationRunner is already started" }
        running = true
        publish()
        thread = Thread(::loop, THREAD_NAME).apply {
            isDaemon = true
            start()
        }
    }

    /** Runs [command] on the simulation thread before the next step. */
    fun post(command: (World) -> Unit) {
        commands.add(command)
        wake()
    }

    /** The newest snapshot. Call it from one thread only, the renderer's (see [TripleBuffer]). */
    fun latestSnapshot(): WorldSnapshot = snapshots.latest()

    /** Stops the simulation thread; the runner cannot be started again. */
    override fun close() {
        running = false
        val simulationThread = thread ?: return
        LockSupport.unpark(simulationThread)
        simulationThread.join(SimConfig.STOP_TIMEOUT_MS)
    }

    private fun wake() {
        thread?.let(LockSupport::unpark)
    }

    private fun loop() {
        var lastTick = System.nanoTime()
        var owedSteps = 0.0
        while (running) {
            val changed = runCommands()
            when (if (isPaused) TimeMode.FREEZE else timeMode) {
                TimeMode.FREEZE -> {
                    if (changed) publish()
                    LockSupport.park(this)
                    lastTick = System.nanoTime()
                    owedSteps = 0.0
                }

                TimeMode.OBSERVE -> {
                    val frameStart = System.nanoTime()
                    owedSteps += (frameStart - lastTick) * OBSERVE_STEPS_PER_NANO
                    lastTick = frameStart
                    val steps = minOf(owedSteps.toInt(), SimConfig.MAX_CATCH_UP_STEPS)
                    owedSteps = minOf(owedSteps - steps, SimConfig.MAX_CATCH_UP_STEPS.toDouble())
                    repeat(steps) { world.step() }
                    if (steps > 0 || changed) publish()
                    val frameRest = FRAME_NANOS - (System.nanoTime() - frameStart)
                    if (frameRest > 0) LockSupport.parkNanos(this, frameRest)
                }

                TimeMode.INCUBATE -> {
                    val frameEnd = System.nanoTime() + FRAME_NANOS
                    do {
                        world.step()
                    } while (System.nanoTime() < frameEnd && keepIncubating())
                    publish()
                    lastTick = System.nanoTime()
                    owedSteps = 0.0
                }
            }
        }
    }

    /** False as soon as something needs the loop's attention: a command, a new mode, a pause or [close]. */
    private fun keepIncubating(): Boolean =
        running && !isPaused && timeMode == TimeMode.INCUBATE && commands.isEmpty()

    private fun runCommands(): Boolean {
        var ran = false
        while (true) {
            val command = commands.poll() ?: break
            command(world)
            ran = true
        }
        return ran
    }

    private fun publish() {
        world.writeSnapshot(snapshots.back())
        snapshots.publish()
        stats = SimStats(world.ageHours, world.cellCount, world.foodCount)
        publishListener?.invoke()
    }

    private companion object {
        const val THREAD_NAME = "petri-simulation"
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val FRAME_NANOS = NANOS_PER_SECOND / SimConfig.PUBLISH_RATE_HZ
        const val OBSERVE_STEPS_PER_NANO =
            SimConfig.OBSERVE_HOURS_PER_SECOND / SimConfig.STEP_HOURS / NANOS_PER_SECOND
    }
}
