package io.github.torintheone.petri.sim

/** Small helpers for building genomes and dishes in tests. */
object TestGenomes {
    /** The default genome with some modes replaced: index to a function that edits the default mode. */
    fun genome(initial: Int = 0, vararg edits: Pair<Int, (Mode) -> Mode>): Genome {
        val modes = MutableList(Mode.MODE_COUNT) { Mode.default(it) }
        for ((index, edit) in edits) modes[index] = edit(modes[index])
        return Genome(initialMode = initial, modes = modes)
    }

    /** A dish where nothing happens unless the test asks for it: no food, no light, no upkeep, no aging. */
    fun quietWorld(seed: Long = 7L, settings: SubstrateSettings = SubstrateSettings()): World =
        World(
            settings.copy(foodRate = 0f, lightBrightness = 0f, aging = false, corpsesBecomeFood = false),
            seed,
        ).also { it.previewGrowth = 0f }

    fun World.totalMass(): Double = (0 until cells.count).sumOf { cells.mass[it].toDouble() }

    fun World.maxSpeed(): Float = (0 until cells.count).maxOfOrNull {
        kotlin.math.sqrt(cells.vx[it] * cells.vx[it] + cells.vy[it] * cells.vy[it])
    } ?: 0f

    fun World.allFinite(): Boolean = (0 until cells.count).all {
        cells.x[it].isFinite() && cells.y[it].isFinite() && cells.angle[it].isFinite() && cells.mass[it].isFinite()
    }

    fun World.stepHours(hours: Double) {
        repeat((hours / SimConfig.STEP_HOURS).toInt()) { step() }
    }
}
