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
import io.github.torintheone.petri.audio.Sound
import io.github.torintheone.petri.audio.SoundEffects
import io.github.torintheone.petri.data.CampaignProgress
import io.github.torintheone.petri.data.DishEntry
import io.github.torintheone.petri.data.GenbankEntry
import io.github.torintheone.petri.data.Storage
import io.github.torintheone.petri.render.DishCamera
import io.github.torintheone.petri.render.DishGestures
import io.github.torintheone.petri.sim.Campaign
import io.github.torintheone.petri.sim.CellType
import io.github.torintheone.petri.sim.DishCodec
import io.github.torintheone.petri.sim.FormatException
import io.github.torintheone.petri.sim.Genome
import io.github.torintheone.petri.sim.GenomeCodec
import io.github.torintheone.petri.sim.GrowthPreview
import io.github.torintheone.petri.sim.Level
import io.github.torintheone.petri.sim.LevelOutcome
import io.github.torintheone.petri.sim.LevelSession
import io.github.torintheone.petri.sim.LevelStatus
import io.github.torintheone.petri.sim.PlantResult
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

    private val sounds = SoundEffects(application).apply {
        enabled = preferences.getBoolean(KEY_SOUNDS, true)
        volume = preferences.getFloat(KEY_SOUNDS_VOLUME, SoundEffects.DEFAULT_VOLUME)
    }

    /** Sound effects on or off. */
    var soundsOn by mutableStateOf(sounds.enabled)
        private set

    var soundsVolume by mutableFloatStateOf(sounds.volume)
        private set

    /** Divisions and deaths shown with their effects on the dish. */
    var effectsOn by mutableStateOf(preferences.getBoolean(KEY_EFFECTS, true))
        private set

    /** The genome editor shows its simple view (true) or every parameter (false). */
    var simpleEditor by mutableStateOf(preferences.getBoolean(KEY_SIMPLE_EDITOR, true))
        private set

    // ---- Campaign ----

    private val progress = CampaignProgress(application)

    /** The campaign level being played, or null in the free dish. */
    var activeLevel by mutableStateOf<Level?>(null)
        private set

    /** The latest verdict on [activeLevel], renewed a few times a second. */
    var levelStatus by mutableStateOf<LevelStatus?>(null)
        private set

    /** A finished level whose result waits to be read; null when there is none. */
    var levelResult by mutableStateOf<LevelStatus?>(null)
        private set

    /** The level whose description is open, from the campaign or from the goals on the microscope. */
    var briefing by mutableStateOf<Level?>(null)

    /** Stars earned per level id; a level without an entry is not won yet. */
    val stars: SnapshotStateMap<String, Int> = mutableStateMapOf()

    /** The result of the running level has been shown and recorded. */
    private var resultHandled = false

    /** A level was just started: verdicts left over from the dish before are ignored until the new one speaks. */
    private var freshRun = false

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
        for (level in Campaign.levels) progress.stars(level.id).takeIf { it > 0 }?.let { stars[level.id] = it }
        viewModelScope.launch { restore() }
        viewModelScope.launch { watchLevel() }
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
        // A level that was being played goes on in its dish, which the autosave kept.
        val level = progress.activeLevel?.let(Campaign::level)
        if (level != null && dish != null) {
            val used = progress.plantingsUsed
            runner.post { world ->
                world.level = LevelSession(level, used)
                world.judgeLevel()
            }
            activeLevel = level
        } else {
            progress.activeLevel = null
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
        if (newTool in tools) tool = newTool
    }

    /** Tools the dock offers: in a level only those its rules allow. */
    val tools: List<Tool>
        get() {
            val level = activeLevel ?: return Tool.entries
            return Tool.entries.filter { tool ->
                when (tool) {
                    Tool.SYNTHESIZER, Tool.DIAGNOSTICS -> true
                    Tool.TWEEZERS -> level.tweezers
                    Tool.FEED -> level.feeding
                    Tool.DELETE -> level.removal
                }
            }
        }

    /** Cell types the genome editor offers: in a level only those its rules allow. */
    val allowedTypes: Set<CellType>
        get() = activeLevel?.allowedTypes ?: ALL_TYPES

    fun toggleGraph() {
        graphShown = !graphShown
    }

    /**
     * Opens what a launch [intent] asks for, once the last dish is back: [EXTRA_TAB] (a [Tab] name),
     * [EXTRA_PRESET] (a [Preset] name, put into the editor), [EXTRA_INSPECT] (diagnostics on the cell nearest
     * the middle of the dish, with the graph), [EXTRA_SPEED] (a speed of «Наблюдение»), [EXTRA_SPEEDS]
     * (the choice of speeds open), [EXTRA_EDITOR] («simple» or «full» genome editor, for this run only),
     * [EXTRA_LEVEL] (a campaign level to start), [EXTRA_BRIEFING] (a level whose description to open) and
     * [EXTRA_PLANT] (cells of the edited genome to plant, «x,y/x,y» in µm). The screenshot script
     * (scripts/screenshots.sh) uses them.
     */
    fun handleLaunch(intent: Intent?) {
        val extras = intent?.extras ?: return
        val requestedTab = extras.getString(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        val preset = extras.getString(EXTRA_PRESET)?.let { name -> Preset.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        val inspect = extras.getBoolean(EXTRA_INSPECT, false)
        val requestedSpeed = extras.getFloat(EXTRA_SPEED, 0f)
        val speeds = extras.getBoolean(EXTRA_SPEEDS, false)
        val look = extras.getString(EXTRA_EDITOR)
        val levelToStart = extras.getString(EXTRA_LEVEL)?.let(Campaign::level)
        val describe = extras.getString(EXTRA_BRIEFING)?.let(Campaign::level)
        val plantAt = extras.getString(EXTRA_PLANT)?.split('/')?.mapNotNull { pair ->
            val numbers = pair.split(',').mapNotNull { it.trim().toFloatOrNull() }
            if (numbers.size == 2) numbers[0] to numbers[1] else null
        }.orEmpty()
        viewModelScope.launch {
            restoreDone.await()
            levelToStart?.let { startLevel(it) }
            requestedTab?.let { tab = it }
            if (requestedSpeed > 0f) selectSpeed(requestedSpeed.toDouble())
            choosingSpeed = speeds
            when (look) {
                EDITOR_SIMPLE -> simpleEditor = true
                EDITOR_FULL -> simpleEditor = false
            }
            preset?.let { editor.load(it.genome(getApplication<Application>().getString(it.title))) }
            describe?.let { briefing = it }
            for ((x, y) in plantAt) plant(x, y)
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

    fun changeSoundsOn(on: Boolean) {
        soundsOn = on
        sounds.enabled = on
        preferences.edit().putBoolean(KEY_SOUNDS, on).apply()
        click()
    }

    fun changeSoundsVolume(value: Float) {
        soundsVolume = value
        sounds.volume = value
        preferences.edit().putFloat(KEY_SOUNDS_VOLUME, value).apply()
        click()
    }

    fun changeEffectsOn(on: Boolean) {
        effectsOn = on
        preferences.edit().putBoolean(KEY_EFFECTS, on).apply()
    }

    /** The quiet tick of a button. */
    fun click() {
        sounds.play(Sound.TAP)
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
        if (tool !in tools) return
        when (tool) {
            Tool.SYNTHESIZER -> plant(x, y)
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
        if (activeLevel != null) {
            say(R.string.msg_level_locked)
            return
        }
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
        if (activeLevel != null) {
            say(R.string.msg_level_locked)
            return
        }
        select(0)
        runner.post { it.sterilize() }
        say(R.string.msg_dish_sterilized)
    }

    // ---- Medium ----

    /** Changes the medium of the running dish (the «Среда» tab); values are clamped to their ranges. */
    fun changeSettings(change: (SubstrateSettings) -> SubstrateSettings) {
        if (activeLevel != null) return
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

    // ---- Campaign ----

    /** The synthesizer: plants a cell of the edited genome, keeping to the level's rules. */
    private fun plant(x: Float, y: Float) {
        val genome = editor.genome.value
        runner.post { world ->
            val result = world.plant(genome, x, y)
            val used = world.level?.plantingsUsed
            viewModelScope.launch { afterPlanting(result, genome, used) }
        }
    }

    private fun afterPlanting(result: PlantResult, genome: Genome, plantingsUsed: Int?) {
        val level = activeLevel
        if (level != null && plantingsUsed != null) progress.plantingsUsed = plantingsUsed
        sounds.play(if (result == PlantResult.PLANTED) Sound.PLANT else Sound.DENY)
        when (result) {
            PlantResult.PLANTED, PlantResult.NO_ROOM -> Unit
            PlantResult.NO_PLANTINGS_LEFT -> say(R.string.msg_no_plantings)
            PlantResult.OUTSIDE_ZONE -> say(R.string.msg_outside_zone)
            PlantResult.LEVEL_OVER -> say(R.string.msg_level_over)
            PlantResult.FORBIDDEN_TYPES -> {
                val names = getApplication<Application>().resources.getStringArray(R.array.cell_types)
                say(R.string.msg_forbidden_types, level?.forbiddenTypes(genome).orEmpty().joinToString { names[it.ordinal] })
            }
        }
    }

    /**
     * Starts [level] in a fresh dish of its own, with the level's starting genome in the editor. The free dish and
     * genome are put aside and come back when the level is left.
     */
    fun startLevel(level: Level) {
        if (!restored) return
        if (activeLevel == null) {
            val genome = editor.genome.value
            runner.post { world ->
                val state = world.saveState()
                ioScope.launch { runCatching { storage.writeSandbox(DishCodec.encode(state), genome) } }
            }
        }
        val world = level.createWorld()
        levelStatus = world.level?.status
        select(0)
        settingsState.value = level.settings
        runner.replaceWorld(world)
        editor.load(level.starter ?: Genome.default())
        activeLevel = level
        levelResult = null
        briefing = null
        resultHandled = false
        freshRun = true
        progress.activeLevel = level.id
        progress.plantingsUsed = 0
        tool = Tool.SYNTHESIZER
        if (timeMode == TimeMode.FREEZE) selectTimeMode(TimeMode.OBSERVE)
        camera.fitWholeDish()
        tab = Tab.MICROSCOPE
        autosave()
    }

    fun restartLevel() {
        activeLevel?.let(::startLevel)
    }

    /** The level after the one just played, or null after the last. */
    val nextLevel: Level?
        get() = activeLevel?.let { Campaign.next(it.id) }

    /** Leaves the level: the free dish and genome put aside at its start come back. */
    fun leaveLevel() {
        if (activeLevel == null) return
        activeLevel = null
        levelStatus = null
        levelResult = null
        briefing = null
        progress.activeLevel = null
        progress.plantingsUsed = 0
        viewModelScope.launch {
            val (dish, genome) = withContext(Dispatchers.IO) {
                val dish = runCatching { storage.readSandboxDish()?.let(DishCodec::decode) }.getOrNull()
                val genome = runCatching { storage.readSandboxGenome()?.let(GenomeCodec::decode) }.getOrNull()
                dish to genome
            }
            val world = dish ?: World(SubstrateSettings(), seed = System.currentTimeMillis()).apply {
                populate(Genome.default(), cellCount = STARTING_CELLS, foodFraction = STARTING_FOOD)
            }
            select(0)
            settingsState.value = world.settings
            runner.replaceWorld(world)
            editor.load(genome ?: Genome.default())
            camera.fitWholeDish()
            withContext(Dispatchers.IO) { runCatching { storage.clearSandbox() } }
            autosave()
        }
    }

    /** Closes the result of a finished level; the dish stays on screen. */
    fun dismissResult() {
        levelResult = null
    }

    /** Opens the result of the finished level again (a tap on its goals). */
    fun showResult() {
        val status = levelStatus ?: return
        if (status.outcome != LevelOutcome.PLAYING) levelResult = status
    }

    /** Is level [index] in [Campaign.levels] open: the first one, and every one after a won level. */
    fun isUnlocked(level: Level): Boolean {
        val index = Campaign.levels.indexOf(level)
        return index <= 0 || stars.containsKey(Campaign.levels[index - 1].id)
    }

    /** Follows the verdict on the level being played: its goals on screen, then its result once it is decided. */
    private suspend fun watchLevel() {
        while (true) {
            delay(LEVEL_REFRESH_MS)
            val level = activeLevel ?: continue
            val status = runner.stats.level?.takeIf { it.levelId == level.id } ?: continue
            if (freshRun) {
                if (status.outcome != LevelOutcome.PLAYING) continue
                freshRun = false
            }
            levelStatus = status
            if (status.outcome == LevelOutcome.PLAYING || resultHandled) continue
            resultHandled = true
            if (status.outcome == LevelOutcome.WON) {
                progress.recordWin(level.id, status.stars)
                stars[level.id] = maxOf(stars[level.id] ?: 0, status.stars)
                sounds.play(Sound.WIN)
            } else {
                sounds.play(Sound.LOSE)
            }
            levelResult = status
        }
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
        sounds.release()
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
        const val EXTRA_LEVEL = "petri.level"
        const val EXTRA_BRIEFING = "petri.briefing"
        const val EXTRA_PLANT = "petri.plant"
        private const val EDITOR_SIMPLE = "simple"
        private const val EDITOR_FULL = "full"

        private const val PREFERENCES = "settings"
        private const val KEY_MUSIC = "music"
        private const val KEY_MUSIC_VOLUME = "music_volume"
        private const val KEY_SIMPLE_EDITOR = "simple_editor"
        private const val KEY_SOUNDS = "sounds"
        private const val KEY_SOUNDS_VOLUME = "sounds_volume"
        private const val KEY_EFFECTS = "effects"

        /** One press of «+» or «−» zooms by this much. */
        private const val ZOOM_STEP = 1.6f

        private const val STATS_REFRESH_MS = 100L
        private const val LEVEL_REFRESH_MS = 250L
        private val ALL_TYPES = CellType.entries.toSet()
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
