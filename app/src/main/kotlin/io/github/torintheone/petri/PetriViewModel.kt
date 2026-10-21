package io.github.torintheone.petri

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.torintheone.petri.render.DishCamera
import io.github.torintheone.petri.render.DishGestures
import io.github.torintheone.petri.sim.Genome
import io.github.torintheone.petri.sim.SimStats
import io.github.torintheone.petri.sim.SimulationRunner
import io.github.torintheone.petri.sim.SubstrateSettings
import io.github.torintheone.petri.sim.TimeMode
import io.github.torintheone.petri.sim.World
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Microscope tools (SPEC «Инструменты микроскопа»). */
enum class Tool { SYNTHESIZER, TWEEZERS, FEED, DELETE, DIAGNOSTICS }

/**
 * Owns the running simulation, the camera and the microscope state, so they survive screen rotation and tab
 * switches. Taps and drags on the dish arrive here (as [DishGestures]) and become commands for the simulation thread.
 */
class PetriViewModel(application: Application) : AndroidViewModel(application), DishGestures {
    val runner: SimulationRunner
    val camera = DishCamera()

    var timeMode by mutableStateOf(TimeMode.OBSERVE)
        private set

    var tool by mutableStateOf(Tool.SYNTHESIZER)
        private set

    /** Diagnostics: the camera follows the selected cell. */
    var following by mutableStateOf(false)
        private set

    private val genomeState = MutableStateFlow(Genome.default())

    /** The genome the synthesizer plants (and, later, the one being edited). */
    val genome: StateFlow<Genome> = genomeState.asStateFlow()

    /** Status panel numbers, refreshed a few times per second while someone collects them. */
    val stats: Flow<SimStats>

    init {
        val world = World(SubstrateSettings(), seed = System.currentTimeMillis())
        world.populate(Genome.default(), cellCount = STARTING_CELLS, foodFraction = STARTING_FOOD)
        runner = SimulationRunner(world)
        runner.timeMode = timeMode
        runner.start()
        stats = flow {
            while (true) {
                emit(runner.stats)
                delay(STATS_REFRESH_MS)
            }
        }.distinctUntilChanged()
    }

    fun selectTimeMode(mode: TimeMode) {
        timeMode = mode
        runner.timeMode = mode
    }

    fun selectTool(newTool: Tool) {
        tool = newTool
    }

    /** The world stands still while the app is in the background. */
    fun onAppVisible(visible: Boolean) {
        runner.isPaused = !visible
    }

    // ---- Gestures on the dish ----

    override fun onTap(x: Float, y: Float, slack: Float) {
        when (tool) {
            Tool.SYNTHESIZER -> {
                val planted = genomeState.value
                runner.post { it.addCell(planted, x, y) }
            }
            Tool.FEED -> runner.post { world ->
                val uid = world.cellAt(x, y, slack)
                if (uid != 0) world.feedCell(uid, FEED_MASS) else world.addFood(x, y)
            }
            Tool.DELETE -> runner.post { world -> world.removeCell(world.cellAt(x, y, slack)) }
            Tool.DIAGNOSTICS -> runner.post { world -> select(world.cellAt(x, y, slack)) }
            Tool.TWEEZERS -> Unit
        }
    }

    override fun onDragStart(x: Float, y: Float, slack: Float) {
        if (tool != Tool.TWEEZERS) return
        runner.post { world ->
            val uid = world.cellAt(x, y, slack)
            if (uid != 0) world.drag(uid, x, y)
        }
    }

    override fun onDrag(x: Float, y: Float) {
        if (tool != Tool.TWEEZERS) return
        val carry = timeMode == TimeMode.FREEZE
        runner.post { it.dragTo(x, y, carry) }
    }

    override fun onDragEnd() {
        runner.post { it.stopDragging() }
    }

    // ---- Diagnostics ----

    private fun select(uid: Int) {
        runner.selectedUid = uid
        camera.followUid = uid
        following = uid != 0
    }

    fun closeDiagnostics() {
        select(0)
    }

    fun toggleFollow() {
        following = !following
        camera.followUid = if (following) runner.selectedUid else 0
    }

    /** «Копировать геном»: the selected cell's genome becomes the one the synthesizer plants. */
    fun copySelectedGenome() {
        val uid = runner.selectedUid
        runner.post { world -> world.genomeOfCell(uid)?.let { genomeState.value = it } }
    }

    override fun onCleared() {
        runner.close()
    }

    private companion object {
        const val STATS_REFRESH_MS = 100L
        const val STARTING_CELLS = 30
        const val STARTING_FOOD = 0.5f

        /** Mass the feeding tool gives a cell, ng. */
        const val FEED_MASS = 0.5f
    }
}
