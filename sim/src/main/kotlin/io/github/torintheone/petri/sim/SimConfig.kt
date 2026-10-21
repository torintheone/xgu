package io.github.torintheone.petri.sim

/**
 * Every tunable number of the simulation lives here (SPEC: «Все числовые константы — в SimConfig.kt»).
 * The values copy the feel of the original game, not its exact numbers; adjust them freely.
 *
 * Units: world coordinates are micrometres (µm), game time is hours (h).
 */
object SimConfig {
    /** Game time covered by one simulation step, in hours: one game minute. */
    const val STEP_HOURS: Double = 1.0 / 60.0

    /** «Наблюдение»: game hours that pass per real second. */
    const val OBSERVE_HOURS_PER_SECOND: Double = 1.0

    /** How many times per real second a running simulation hands a fresh snapshot to the renderer and UI. */
    const val PUBLISH_RATE_HZ: Int = 60

    /**
     * «Наблюдение»: the most steps run in one frame to catch up after a stall. Time owed beyond that is dropped,
     * so a device that cannot keep up runs slower than real time instead of freezing.
     */
    const val MAX_CATCH_UP_STEPS: Int = 4

    /** How long [SimulationRunner.close] waits for the simulation thread to finish, in milliseconds. */
    const val STOP_TIMEOUT_MS: Long = 1_000

    /** Micrometres in a millimetre: settings are in mm, the world is in µm. */
    const val UM_PER_MM: Float = 1_000f
}
