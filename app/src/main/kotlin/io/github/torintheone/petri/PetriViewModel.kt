package io.github.torintheone.petri

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.torintheone.petri.audio.MusicPlayer
import io.github.torintheone.petri.data.DishEntry
import io.github.torintheone.petri.data.GenbankEntry
import io.github.torintheone.petri.data.Storage
import io.github.torintheone.petri.render.DishCamera
import io.github.torintheone.petri.render.DishGestures
import io.github.torintheone.petri.sim.DishCodec
import io.github.torintheone.petri.sim.FormatException
import io.github.torintheone.petri.sim.Genome
import io.github.torintheone.petri.sim.GenomeCodec
import io.github.torintheone.petri.sim.GrowthPreview
import io.github.torintheone.petri.sim.PopulationHistory
import io.github.torintheone.petri.sim.Preset
import io.github.torintheone.petri.sim.PreviewFrame
import io.github.torintheone.petri.sim.SimStats
import io.github.torintheone.petri.sim.SimulationRunner
import io.github.torintheone.petri.sim.SubstrateSettings
import io.github.torintheone.petri.sim.TimeMode
import io.github.torintheone.petri.sim.World
import io.github.torintheone.petri.sim.saveState
import io.github.torintheone.petri.ui.Tab
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Microscope tools (SPEC «Инструменты микроскопа»). */
enum class Tool { SYNTHESIZER, TWEEZERS, FEED, DELETE, DIAGNOSTICS }

/** A short note for the snackbar: a string resource and an optional name to put into it. */
data class UiMessage(@param:StringRes val text: Int, val argument: String? = null)

/**
 * Owns the running simulation, the camera, the genome editor and the files, so they survive screen rotation and
 * tab switches. Taps and drags on the dish arrive here (as [DishGestures]) and become commands for the simulation.
 */
class PetriViewModel(application: Application) : AndroidViewModel(application), DishGestures {
    private val storage = Storage(application)

    /** File writes that should finish even when the screen goes away, such as the autosave. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One autosave at a time: two quick trips to the background must not write the same files at once. */
    private val autosaveLock = Mutex()

    /**
     * False until the last autosave is back in place. Leaving the app before that must not save the empty
     * starting dish over the person's real one.
     */
    @Volatile
    private var restored = false

    /** Completes once [restore] has put the last dish and genome back. */
    private val restoreDone = CompletableDeferred<Unit>()

    val runner: SimulationRunner
    val camera = DishCamera()
    val editor = GenomeEditor(viewModelScope)

    /** Small choices that outlive the app: music and the editor's look. */
    private val preferences = application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private val music = MusicPlayer(application, viewModelScope).apply {
        enabled = preferences.getBoolean(KEY_MUSIC, true)
        volume = preferences.getFloat(KEY_MUSIC_VOLUME, MusicPlayer.DEFAULT_VOLUME)
    }

    /** Background music on or off. */
    var musicOn by mutableStateOf(music.enabled)
        private set

    var musicVolume by mutableFloatStateOf(music.volume)
        private set

    /** The genome editor shows its simple view (true) or every parameter (false). */
    var simpleEditor by mutableStateOf(preferences.getBoolean(KEY_SIMPLE_EDITOR, true))
        private set

    /** The tab on screen; kept here so that it survives rotation and a launch request can switch it. */
    var tab by mutableStateOf(Tab.MICROSCOPE)

    /** The population graph under the readings of the microscope. */
    var graphShown by mutableStateOf(false)
        private set

    /** The choice of speeds above the dock is open. */
    var choosingSpeed by mutableStateOf(false)

    var timeMode by mutableStateOf(TimeMode.OBSERVE)
        private set

    /** How many times faster than real «Наблюдение» time runs; below 1 is slow motion. */
    var speed by mutableDoubleStateOf(1.0)
        private set

    var tool by mutableStateOf(Tool.SYNTHESIZER)
        private set

    /** Diagnostics: the camera follows the selected cell. */
    var following by mutableStateOf(false)
        private set

    /** Status panel numbers, refreshed a few times per second while someone collects them. */
    val stats: Flow<SimStats>

    /** Game hours per real second, measured every half second while someone collects it. */
    val pace: Flow<Double> = flow {
        var lastAge = runner.stats.ageHours
        var lastTime = System.nanoTime()
        while (true) {
            delay(SPEED_SAMPLE_MS)
            val age = runner.stats.ageHours
            val time = System.nanoTime()
            // A dish loaded or sterilized in between makes the age jump back: no speed then.
            emit(((age - lastAge) / ((time - lastTime) / NANOS_PER_SECOND)).coerceAtLeast(0.0))
            lastAge = age
            lastTime = time
        }
    }.distinctUntilChanged()

    /** The population graph, refreshed every half second while it is shown. */
    val history: Flow<List<PopulationHistory.Sample>> = flow {
        while (true) {
            emit(runner.history)
            delay(HISTORY_REFRESH_MS)
        }
    }.distinctUntilChanged { old, new -> old === new }

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    private val genbankState = MutableStateFlow<List<GenbankEntry>>(emptyList())
    val genbank: StateFlow<List<GenbankEntry>> = genbankState.asStateFlow()

    /** Pictures for the genome bank, by [thumbnailKey]; filled in the background. */
    val thumbnails: SnapshotStateMap<String, PreviewFrame> = mutableStateMapOf()

    private val dishesState = MutableStateFlow<List<DishEntry>>(emptyList())
    val dishes: StateFlow<List<DishEntry>> = dishesState.asStateFlow()

    private val settingsState = MutableStateFlow(SubstrateSettings())

    /** The medium of the running dish, as the «Среда» tab shows it. */
    val settings: StateFlow<SubstrateSettings> = settingsState.asStateFlow()

    init {
        runner = SimulationRunner(World(SubstrateSettings(), seed = System.currentTimeMillis()))
        runner.timeMode = timeMode
        runner.start()
        stats = flow {
            while (true) {
                emit(runner.stats)
                delay(STATS_REFRESH_MS)
            }
        }.distinctUntilChanged()
        viewModelScope.launch { restore() }
    }

    /** Brings back the dish and genome from the last autosave, or starts a fresh dish. */
    private suspend fun restore() {
        val (dish, genome) = withContext(Dispatchers.IO) {
            // A missing, unreadable or broken autosave just means a fresh start.
            val dish = runCatching { storage.readAutosaveDish()?.let(DishCodec::decode) }.getOrNull()
            val genome = runCatching { storage.readAutosaveGenome()?.let(GenomeCodec::decode) }.getOrNull()
            dish to genome
        }
        genome?.let(editor::load)
        if (dish != null) {
            settingsState.value = dish.settings
            runner.replaceWorld(dish)
        } else {
            runner.post { it.populate(Genome.default(), cellCount = STARTING_CELLS, foodFraction = STARTING_FOOD) }
        }
        restored = true
        restoreDone.complete(Unit)
        withContext(Dispatchers.IO) { runCatching { offerPresets() } }
        refreshGenbank()
        refreshDishes()
    }

    /** Example genomes go into the bank once each: all on the first launch, new ones after an update. */
    private fun offerPresets() {
        val offered = storage.offeredPresets()
        val fresh = Preset.entries.filter { it.name !in offered }
        if (fresh.isEmpty()) return
        val existing = storage.genomes().map { it.name }.toSet()
        for (preset in fresh) {
            val name = getApplication<Application>().getString(preset.title)
            if (name !in existing) storage.saveGenome(name, preset.genome(name))
        }
        storage.markPresetsOffered(fresh.map { it.name })
    }

    fun selectTimeMode(mode: TimeMode) {
        timeMode = mode
        runner.timeMode = mode
    }

    /** «Наблюдение» at [value] times its normal pace; also lets time run if it stood still. */
    fun selectSpeed(value: Double) {
        speed = value
        runner.speed = value
        selectTimeMode(TimeMode.OBSERVE)
    }

    // ---- Camera buttons ----

    fun zoomIn() {
        onCameraMoved()
        camera.zoomBy(ZOOM_STEP)
    }

    fun zoomOut() {
        onCameraMoved()
        camera.zoomBy(1f / ZOOM_STEP)
    }

    fun showWholeDish() {
        onCameraMoved()
        camera.showWholeDish()
    }

    fun selectTool(newTool: Tool) {
        tool = newTool
    }

    fun toggleGraph() {
        graphShown = !graphShown
    }

    /**
     * Opens what a launch [intent] asks for, once the last dish is back: [EXTRA_TAB] (a [Tab] name),
     * [EXTRA_PRESET] (a [Preset] name, put into the editor), [EXTRA_INSPECT] (diagnostics on the cell nearest
     * the middle of the dish, with the graph), [EXTRA_SPEED] (a speed of «Наблюдение»), [EXTRA_SPEEDS]
     * (the choice of speeds open) and [EXTRA_EDITOR] («simple» or «full» genome editor, for this run only).
     * The screenshot script (scripts/screenshots.sh) uses them.
     */
    fun handleLaunch(intent: Intent?) {
        val extras = intent?.extras ?: return
        val requestedTab = extras.getString(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        val preset = extras.getString(EXTRA_PRESET)?.let { name -> Preset.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        val inspect = extras.getBoolean(EXTRA_INSPECT, false)
        val requestedSpeed = extras.getFloat(EXTRA_SPEED, 0f)
        val speeds = extras.getBoolean(EXTRA_SPEEDS, false)
        val look = extras.getString(EXTRA_EDITOR)
        viewModelScope.launch {
            restoreDone.await()
            requestedTab?.let { tab = it }
            if (requestedSpeed > 0f) selectSpeed(requestedSpeed.toDouble())
            choosingSpeed = speeds
            when (look) {
                EDITOR_SIMPLE -> simpleEditor = true
                EDITOR_FULL -> simpleEditor = false
            }
            preset?.let { editor.load(it.genome(getApplication<Application>().getString(it.title))) }
            if (inspect) {
                tool = Tool.DIAGNOSTICS
                graphShown = true
                runner.post { world ->
                    val uid = world.cellAt(0f, 0f, slack = Float.MAX_VALUE)
                    viewModelScope.launch { select(uid) }
                }
            }
        }
    }

    /** The world stands still while the app is in the background, and the dish and genome are saved. */
    fun onAppVisible(visible: Boolean) {
        runner.isPaused = !visible
        music.setVisible(visible)
        if (!visible) autosave()
    }

    fun changeMusicOn(on: Boolean) {
        musicOn = on
        music.enabled = on
        preferences.edit().putBoolean(KEY_MUSIC, on).apply()
    }

    fun changeMusicVolume(value: Float) {
        musicVolume = value
        music.volume = value
        preferences.edit().putFloat(KEY_MUSIC_VOLUME, value).apply()
    }

    fun changeEditorLook(simple: Boolean) {
        simpleEditor = simple
        preferences.edit().putBoolean(KEY_SIMPLE_EDITOR, simple).apply()
    }

    /** The simple editor's «Новый вид клетки» for child [child] (1 or 2) of the edited mode. */
    fun newChildKind(child: Int) {
        if (!editor.newChildKind(child)) say(R.string.msg_no_free_mode)
    }

    private fun autosave() {
        if (!restored) return
        val genome = editor.genome.value
        runner.post { world ->
            val state = world.saveState()
            ioScope.launch {
                autosaveLock.withLock { runCatching { storage.writeAutosave(DishCodec.encode(state), genome) } }
            }
        }
    }

    // ---- Gestures on the dish ----

    override val dragsCells: Boolean
        get() = tool == Tool.TWEEZERS

    override fun onCameraMoved() {
        following = false
        camera.followUid = 0
    }

    override fun onTap(x: Float, y: Float, slack: Float) {
        when (tool) {
            Tool.SYNTHESIZER -> {
                val planted = editor.genome.value
                runner.post { it.addCell(planted, x, y) }
            }
            Tool.FEED -> runner.post { world ->
                val uid = world.cellAt(x, y, slack)
                if (uid != 0) world.feedCell(uid, FEED_MASS) else world.addFood(x, y)
            }
            Tool.DELETE -> runner.post { world -> world.removeCell(world.cellAt(x, y, slack)) }
            Tool.DIAGNOSTICS -> runner.post { world ->
                val uid = world.cellAt(x, y, slack)
                viewModelScope.launch { select(uid) }
            }
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

    /** «Копировать геном» and «Загрузить с микроскопа»: the selected cell's genome goes into the editor. */
    fun copySelectedGenome() {
        val uid = runner.selectedUid
        if (uid == 0) {
            say(R.string.msg_select_cell_first)
            return
        }
        runner.post { world ->
            val genome = world.genomeOfCell(uid)
            viewModelScope.launch {
                if (genome == null) {
                    say(R.string.msg_cell_gone)
                } else {
                    editor.load(genome)
                    say(R.string.msg_genome_copied)
                }
            }
        }
    }

    // ---- Genome bank ----

    fun saveGenome(name: String) {
        val clean = name.trim().take(Storage.MAX_NAME_LENGTH)
        if (clean.isEmpty()) return
        editor.rename(clean)
        val genome = editor.genome.value
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { storage.saveGenome(clean, genome) }.isSuccess }
            say(if (saved) R.string.msg_genome_saved else R.string.msg_save_failed, clean)
            refreshGenbank()
        }
    }

    fun loadGenome(entry: GenbankEntry) {
        editor.load(entry.genome.copy(name = entry.name))
        say(R.string.msg_genome_loaded, entry.name)
    }

    fun loadDefaultGenome() {
        editor.load(Genome.default())
        say(R.string.msg_genome_default)
    }

    fun deleteGenome(entry: GenbankEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { storage.deleteGenome(entry.name) } }
            refreshGenbank()
        }
    }

    /** Writes [genome] to a file for the share sheet; [ready] runs on the main thread. */
    fun shareGenome(genome: Genome, ready: (File) -> Unit) {
        viewModelScope.launch {
            val fallback = getApplication<Application>().getString(R.string.genome_untitled)
            val file = withContext(Dispatchers.IO) { runCatching { storage.shareableGenome(genome, fallback) }.getOrNull() }
            if (file != null) ready(file) else say(R.string.msg_file_error)
        }
    }

    /** Reads a genome file the person picked, adds it to the bank and opens it in the editor. */
    fun importGenome(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val genome = GenomeCodec.decode(storage.readPicked(uri, Storage.MAX_GENOME_BYTES).decodeToString())
                    val fallback = getApplication<Application>().getString(R.string.genome_imported)
                    val name = genome.name.trim().take(Storage.MAX_NAME_LENGTH).ifEmpty { fallback }
                    storage.saveGenome(name, genome)
                    genome.copy(name = name)
                }
            }
            result.onSuccess { genome ->
                editor.load(genome)
                say(R.string.msg_import_ok, genome.name)
                refreshGenbank()
            }.onFailure { error -> say(problemMessage(error)) }
        }
    }

    fun thumbnailKey(entry: GenbankEntry): String = "${entry.name}@${entry.modified}"

    private suspend fun refreshGenbank() {
        val list = withContext(Dispatchers.IO) { runCatching { storage.genomes() }.getOrDefault(emptyList()) }
        genbankState.value = list
        for (entry in list) {
            val key = thumbnailKey(entry)
            if (key in thumbnails) continue
            val picture = withContext(Dispatchers.Default) { runCatching { GrowthPreview.thumbnail(entry.genome) }.getOrNull() }
            if (picture != null) thumbnails[key] = picture
        }
    }

    // ---- Dishes ----

    fun saveDish(name: String) {
        val clean = name.trim().take(Storage.MAX_NAME_LENGTH)
        if (clean.isEmpty()) return
        runner.post { world ->
            val state = world.saveState()
            viewModelScope.launch {
                val saved = withContext(Dispatchers.IO) { runCatching { storage.saveDish(clean, DishCodec.encode(state)) }.isSuccess }
                say(if (saved) R.string.msg_dish_saved else R.string.msg_save_failed, clean)
                refreshDishes()
            }
        }
    }

    fun loadDish(entry: DishEntry) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { DishCodec.decode(storage.readDish(entry.name)) } }
            result.onSuccess { world ->
                select(0)
                settingsState.value = world.settings
                runner.replaceWorld(world)
                say(R.string.msg_dish_loaded, entry.name)
            }.onFailure { say(R.string.msg_dish_file_wrong) }
        }
    }

    fun deleteDish(entry: DishEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { storage.deleteDish(entry.name) } }
            refreshDishes()
        }
    }

    /** «Стерилизовать»: no cells, no food, age 0; the settings stay. */
    fun sterilize() {
        select(0)
        runner.post { it.sterilize() }
        say(R.string.msg_dish_sterilized)
    }

    // ---- Medium ----

    /** Changes the medium of the running dish (the «Среда» tab); values are clamped to their ranges. */
    fun changeSettings(change: (SubstrateSettings) -> SubstrateSettings) {
        val next = change(settingsState.value).sanitized()
        settingsState.value = next
        runner.post { it.applySettings(next) }
    }

    fun resetSettings() {
        changeSettings { SubstrateSettings() }
    }

    private suspend fun refreshDishes() {
        dishesState.value = withContext(Dispatchers.IO) { runCatching { storage.dishes() }.getOrDefault(emptyList()) }
    }

    // ---- Messages ----

    private fun say(@StringRes text: Int, argument: String? = null) {
        messageChannel.trySend(UiMessage(text, argument))
    }

    @StringRes
    private fun problemMessage(error: Throwable): Int = when ((error as? FormatException)?.problem) {
        FormatException.Problem.WRONG_KIND -> R.string.msg_file_wrong_kind
        FormatException.Problem.NEWER_VERSION -> R.string.msg_file_newer
        FormatException.Problem.DAMAGED -> R.string.msg_file_damaged
        null -> if (error is IOException) R.string.msg_file_error else R.string.msg_file_damaged
    }

    override fun onCleared() {
        music.release()
        runner.close()
    }

    companion object {
        /** Launch extras, see [handleLaunch]. */
        const val EXTRA_TAB = "petri.tab"
        const val EXTRA_PRESET = "petri.preset"
        const val EXTRA_INSPECT = "petri.inspect"
        const val EXTRA_SPEED = "petri.speed"
        const val EXTRA_SPEEDS = "petri.speeds"
        const val EXTRA_EDITOR = "petri.editor"
        private const val EDITOR_SIMPLE = "simple"
        private const val EDITOR_FULL = "full"

        private const val PREFERENCES = "settings"
        private const val KEY_MUSIC = "music"
        private const val KEY_MUSIC_VOLUME = "music_volume"
        private const val KEY_SIMPLE_EDITOR = "simple_editor"

        /** One press of «+» or «−» zooms by this much. */
        private const val ZOOM_STEP = 1.6f

        private const val STATS_REFRESH_MS = 100L
        private const val HISTORY_REFRESH_MS = 500L
        private const val SPEED_SAMPLE_MS = 500L
        private const val NANOS_PER_SECOND = 1e9
        private const val STARTING_CELLS = 30
        private const val STARTING_FOOD = 0.5f

        /** Mass the feeding tool gives a cell, ng. */
        private const val FEED_MASS = 0.5f
    }
}

/** Name of an example genome in the genome bank. */
@get:StringRes
val Preset.title: Int
    get() = when (this) {
        Preset.SINGLE_PHAGOCYTES -> R.string.preset_single
        Preset.CHAIN -> R.string.preset_chain
        Preset.ARC -> R.string.preset_arc
        Preset.PHOTOCYTE_COLONY -> R.string.preset_photocyte_colony
        Preset.SWIMMER -> R.string.preset_swimmer
        Preset.SMART_SWIMMER -> R.string.preset_smart_swimmer
    }
