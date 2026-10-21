package io.github.torintheone.petri.sim

/**
 * The whole state of one dish. Not thread-safe: only the thread that runs the simulation may touch it
 * (see [SimulationRunner.post]).
 */
class World(
    val settings: SubstrateSettings = SubstrateSettings(),
) {
    /** Steps simulated since the dish was created. Counting steps keeps the age exact. */
    var stepCount: Long = 0L
        private set

    /** Age of the substrate in game hours. */
    val ageHours: Double get() = stepCount * SimConfig.STEP_HOURS

    val cellCount: Int get() = 0

    val foodCount: Int get() = 0

    /** Advances the world by [SimConfig.STEP_HOURS]. */
    fun step() {
        stepCount++
    }

    /** Copies what the renderer and UI need into [out]. */
    fun writeSnapshot(out: WorldSnapshot) {
        out.ageHours = ageHours
        out.dishRadius = settings.dishRadiusUm
        out.cellCount = cellCount
        out.foodCount = foodCount
    }
}
