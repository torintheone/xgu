package io.github.torintheone.petri

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.github.torintheone.petri.render.DishCamera
import io.github.torintheone.petri.sim.SimStats
import io.github.torintheone.petri.sim.SimulationRunner
import io.github.torintheone.petri.sim.SubstrateSettings
import io.github.torintheone.petri.sim.TimeMode
import io.github.torintheone.petri.sim.World
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Owns the running simulation and the camera, so both survive screen rotation and tab switches. */
class PetriViewModel : ViewModel() {
    val runner = SimulationRunner(World(SubstrateSettings()))
    val camera = DishCamera()

    var timeMode by mutableStateOf(runner.timeMode)
        private set

    /** Status panel numbers, refreshed a few times per second while someone collects them. */
    val stats: Flow<SimStats> = flow {
        while (true) {
            emit(runner.stats)
            delay(STATS_REFRESH_MS)
        }
    }.distinctUntilChanged()

    init {
        runner.start()
    }

    fun selectTimeMode(mode: TimeMode) {
        timeMode = mode
        runner.timeMode = mode
    }

    /** The world stands still while the app is in the background. */
    fun onAppVisible(visible: Boolean) {
        runner.isPaused = !visible
    }

    override fun onCleared() {
        runner.close()
    }

    private companion object {
        const val STATS_REFRESH_MS = 100L
    }
}
