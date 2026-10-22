package io.github.torintheone.petri

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.torintheone.petri.sim.Genome
import io.github.torintheone.petri.sim.GrowthPreview
import io.github.torintheone.petri.sim.Mode
import io.github.torintheone.petri.sim.PreviewFrame
import io.github.torintheone.petri.sim.SimConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * The genome being edited (SPEC «Геном»), which is also the one the synthesizer plants, and its growth preview.
 * The preview is recomputed in the background [PREVIEW_DELAY_MS] after the last change.
 */
class GenomeEditor(scope: CoroutineScope) {
    private val genomeState = MutableStateFlow(Genome.default())
    val genome: StateFlow<Genome> = genomeState.asStateFlow()

    /** The mode shown in the editor. */
    var editedMode by mutableIntStateOf(0)
        private set

    /** «Привязка значений»: angles in 15° steps, other sliders in 25 levels. */
    var snapping by mutableStateOf(true)

    /** The preview hour the slider shows. */
    var previewHour by mutableIntStateOf(DEFAULT_PREVIEW_HOUR)

    private val previewState = MutableStateFlow<List<PreviewFrame>>(emptyList())

    /** One frame per game hour, from 0; empty until the first preview is ready. */
    val preview: StateFlow<List<PreviewFrame>> = previewState.asStateFlow()

    /** True while a new preview is being computed. */
    var previewBusy by mutableStateOf(false)
        private set

    init {
        scope.launch {
            genomeState.collectLatest { genome ->
                if (previewState.value.isNotEmpty()) delay(PREVIEW_DELAY_MS)
                previewBusy = true
                previewState.value = withContext(Dispatchers.Default) { grow(genome) }
                previewBusy = false
            }
        }
    }

    /** Puts [genome] into the editor and shows its initial mode. */
    fun load(genome: Genome) {
        val clean = genome.sanitized()
        genomeState.value = clean
        editedMode = clean.initialMode
    }

    fun selectMode(index: Int) {
        editedMode = index.coerceIn(0, Mode.MODE_COUNT - 1)
    }

    /** Changes the edited mode; out-of-range values are clamped. */
    fun changeMode(change: (Mode) -> Mode) {
        val index = editedMode
        genomeState.update { it.withMode(index, change(it.modes[index]).sanitized(index)) }
    }

    fun makeInitial(index: Int) {
        genomeState.update { it.copy(initialMode = index) }
    }

    /**
     * The simple editor's «Новая клетка»: child [child] (1 or 2) of the edited mode becomes a new kind of cell in
     * the lowest mode the organism does not use. The new kind has the parent's type, a colour of its own and does
     * not divide. Returns false when the organism already uses every mode.
     */
    fun newChildKind(child: Int): Boolean {
        val index = editedMode
        var added = false
        genomeState.update { genome ->
            val free = genome.freeMode()
            added = free >= 0
            if (free < 0) return@update genome
            val parent = genome.modes[index]
            val kind = Mode.default(free).copy(cellType = parent.cellType, splitMass = Mode.SPLIT_NEVER)
            val linked = if (child == 1) parent.copy(child1Mode = free) else parent.copy(child2Mode = free)
            genome.withMode(free, kind).withMode(index, linked)
        }
        return added
    }

    fun rename(name: String) {
        genomeState.update { it.copy(name = name) }
    }

    private suspend fun grow(genome: Genome): List<PreviewFrame> {
        val preview = GrowthPreview(genome)
        val frames = ArrayList<PreviewFrame>(SimConfig.PREVIEW_HOURS)
        frames += preview.frame()
        repeat(SimConfig.PREVIEW_HOURS - 1) {
            yield()
            preview.advanceHour()
            frames += preview.frame()
        }
        return frames
    }

    private companion object {
        /** SPEC: the preview follows a change after about 300 ms, so dragging a slider does not restart it every frame. */
        const val PREVIEW_DELAY_MS = 300L
        const val DEFAULT_PREVIEW_HOUR = 12
    }
}
