package io.github.torintheone.petri.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.torintheone.petri.PetriViewModel
import io.github.torintheone.petri.R
import io.github.torintheone.petri.Tool
import io.github.torintheone.petri.render.DishCamera
import io.github.torintheone.petri.render.DishSurfaceView
import io.github.torintheone.petri.sim.CellInfo
import io.github.torintheone.petri.sim.LevelOutcome
import io.github.torintheone.petri.sim.PopulationHistory
import io.github.torintheone.petri.sim.SimConfig
import io.github.torintheone.petri.sim.SimStats
import io.github.torintheone.petri.sim.TimeMode
import io.github.torintheone.petri.ui.theme.Lab
import io.github.torintheone.petri.ui.theme.LabType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * «Микроскоп»: the dish fills the screen. The readings float at the top and open the population graph; the scale
 * bar, the card of the inspected cell and the dock with the tools and the time controls sit at the bottom.
 */
@Composable
fun MicroscopeScreen(viewModel: PetriViewModel, modifier: Modifier = Modifier) {
    val stats by viewModel.stats.collectAsStateWithLifecycle(initialValue = SimStats.EMPTY)
    val pace by viewModel.pace.collectAsStateWithLifecycle(initialValue = 0.0)
    // The caption over the dock tells what the last tap on the dock chose, then fades away.
    var caption by remember { mutableStateOf(CaptionSubject.TOOL) }
    var captionStamp by remember { mutableIntStateOf(0) }
    val selectTool: (Tool) -> Unit = { tool ->
        viewModel.selectTool(tool)
        viewModel.choosingSpeed = false
        caption = CaptionSubject.TOOL
        captionStamp++
    }
    val selectTime: (TimeMode) -> Unit = { mode ->
        viewModel.selectTimeMode(mode)
        viewModel.choosingSpeed = false
        caption = CaptionSubject.TIME
        captionStamp++
    }
    val selectSpeed: (Double) -> Unit = { value ->
        viewModel.selectSpeed(value)
        viewModel.choosingSpeed = false
        caption = CaptionSubject.TIME
        captionStamp++
    }
    val cameraControls: @Composable (Modifier) -> Unit = { controlsModifier ->
        CameraControls(
            onZoomOut = viewModel::zoomOut,
            onZoomIn = viewModel::zoomIn,
            onWholeDish = viewModel::showWholeDish,
            modifier = controlsModifier,
        )
    }

    Box(modifier) {
        DishView(viewModel, Modifier.fillMaxSize())
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(OVERLAY_MARGIN),
        ) {
            val wide = maxWidth > maxHeight
            val sidePanel = min(SIDE_PANEL_MAX.value, (maxWidth - SIDE_PANEL_GAP).value / 2f).dp
            val dockButton = min(DOCK_BUTTON_MAX.value, ((maxWidth - DOCK_EXTRA) / DOCK_BUTTONS).value).dp
            val selected = stats.selected
            val card: @Composable (Modifier) -> Unit = { cardModifier ->
                if (selected != null) {
                    SpecimenCard(
                        info = selected,
                        following = viewModel.following,
                        onFollow = viewModel::toggleFollow,
                        onCopyGenome = viewModel::copySelectedGenome,
                        onClose = viewModel::closeDiagnostics,
                        modifier = cardModifier,
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .widthIn(max = if (wide) sidePanel else READINGS_MAX),
            ) {
                Readings(
                    stats = stats,
                    timeMode = viewModel.timeMode,
                    speed = viewModel.speed,
                    pace = pace,
                    graphShown = viewModel.graphShown,
                    history = viewModel.history,
                    onToggleGraph = viewModel::toggleGraph,
                )
                viewModel.activeLevel?.let { level ->
                    LevelGoals(
                        level = level,
                        status = viewModel.levelStatus,
                        onClick = {
                            val decided = viewModel.levelStatus?.outcome?.let { it != LevelOutcome.PLAYING } == true
                            if (decided) viewModel.showResult() else viewModel.briefing = level
                        },
                    )
                }
            }
            if (wide) {
                card(
                    Modifier
                        .align(Alignment.TopEnd)
                        .widthIn(max = sidePanel),
                )
                CameraScaleBar(viewModel.camera, Modifier.align(Alignment.BottomStart))
                cameraControls(Modifier.align(Alignment.BottomEnd))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = DOCK_COLUMN_MAX),
            ) {
                if (!wide) {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                        CameraScaleBar(viewModel.camera)
                        Spacer(Modifier.weight(1f))
                        cameraControls(Modifier)
                    }
                    card(Modifier.fillMaxWidth())
                }
                Caption(
                    subject = caption,
                    stamp = captionStamp,
                    tool = viewModel.tool,
                    timeMode = viewModel.timeMode,
                    speed = viewModel.speed,
                )
                if (viewModel.choosingSpeed) {
                    SpeedPicker(
                        timeMode = viewModel.timeMode,
                        speed = viewModel.speed,
                        onSpeed = selectSpeed,
                        onIncubate = { selectTime(TimeMode.INCUBATE) },
                        button = dockButton,
                    )
                }
                Dock(
                    tools = viewModel.tools,
                    tool = viewModel.tool,
                    onTool = selectTool,
                    timeMode = viewModel.timeMode,
                    speed = viewModel.speed,
                    onTimeMode = selectTime,
                    choosingSpeed = viewModel.choosingSpeed,
                    onChooseSpeed = { viewModel.choosingSpeed = !viewModel.choosingSpeed },
                    button = dockButton,
                )
            }
        }
    }
}

@Composable
private fun DishView(viewModel: PetriViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = remember(viewModel) { DishSurfaceView(context, viewModel.runner, viewModel.camera, viewModel) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // GLSurfaceView must follow the activity: release the GL thread while the app is not in front.
    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

/**
 * The readings of the dish: age (or the state of time), cells, food and species. A tap opens the population
 * graph under them, drawn in the colours of their dots.
 */
@Composable
private fun Readings(
    stats: SimStats,
    timeMode: TimeMode,
    speed: Double,
    pace: Double,
    graphShown: Boolean,
    history: Flow<List<PopulationHistory.Sample>>,
    onToggleGraph: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(
        glass = true,
        onClick = onToggleGraph,
        contentPadding = PaddingValues(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 10.dp),
        modifier = modifier.animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val (ageLabel, ageColor) = when {
                timeMode == TimeMode.FREEZE -> stringResource(R.string.hud_paused) to Lab.Amber
                timeMode == TimeMode.INCUBATE -> stringResource(R.string.hud_fast, measuredTimes(pace)) to Lab.Accent
                speed == 1.0 -> stringResource(R.string.stat_age) to Lab.TextDim
                else -> stringResource(R.string.hud_fast, speedLabel(speed)) to Lab.Accent
            }
            Readout(ageLabel, stringResource(R.string.hours_value, formatNumber(stats.ageHours, 1)), labelColor = ageColor)
            Readout(stringResource(R.string.stat_cells), stats.cellCount.toString(), dot = Lab.Accent)
            Readout(stringResource(R.string.stat_food), stats.foodCount.toString(), dot = Lab.Lime)
            Readout(stringResource(R.string.stat_species), stats.speciesCount.toString(), dot = Lab.Violet)
            Icon(
                painter = painterResource(R.drawable.ic_graph),
                contentDescription = stringResource(if (graphShown) R.string.graph_hide else R.string.graph_show),
                tint = if (graphShown) Lab.Accent else Lab.TextFaint,
                modifier = Modifier.size(18.dp),
            )
        }
        if (graphShown) {
            val samples by history.collectAsStateWithLifecycle(initialValue = emptyList())
            PopulationChart(
                samples = samples,
                cellsColor = Lab.Accent,
                foodColor = Lab.Lime,
                speciesColor = Lab.Violet,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
        }
    }
}

/** «7»: how many times faster than normal observation the dish runs, from the measured [hoursPerSecond]. */
private fun measuredTimes(hoursPerSecond: Double): String {
    val times = hoursPerSecond / SimConfig.OBSERVE_HOURS_PER_SECOND
    return formatNumber(times, if (times < SPEED_DECIMALS_BELOW) 1 else 0)
}

/** «¼», «1», «4»: a chosen speed, with vulgar fractions for slow motion. */
private fun speedLabel(speed: Double): String = when (speed) {
    0.125 -> "⅛"
    0.25 -> "¼"
    0.5 -> "½"
    else -> formatNumber(speed, 0)
}

/** Speeds offered for «Наблюдение»: slow motion, normal and faster. */
private val SPEEDS = listOf(0.125, 0.25, 0.5, 1.0, 2.0, 4.0)

/** What the caption over the dock talks about. */
private enum class CaptionSubject { TOOL, TIME }

/** The name and a one-line hint of the tool or time mode just chosen; fades after a few seconds. */
@Composable
private fun Caption(subject: CaptionSubject, stamp: Int, tool: Tool, timeMode: TimeMode, speed: Double) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(stamp) {
        visible = true
        delay(CAPTION_MS)
        visible = false
    }
    val (name, hint) = when (subject) {
        CaptionSubject.TOOL -> stringResource(tool.label) to stringResource(tool.hint)
        CaptionSubject.TIME -> when {
            timeMode != TimeMode.OBSERVE -> stringResource(timeMode.label) to stringResource(timeMode.hint)
            speed <= 1.0 -> stringResource(R.string.time_observe_speed, speedLabel(speed)) to
                stringResource(R.string.hint_speed_slow, (1.0 / speed).roundToInt())
            else -> stringResource(R.string.time_observe_speed, speedLabel(speed)) to
                stringResource(R.string.hint_speed_fast, speed.roundToInt())
        }
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Lab.Accent, fontSize = LabType.Eyebrow.fontSize, letterSpacing = LabType.Eyebrow.letterSpacing)) {
                    append(name.uppercase())
                }
                append("   ")
                append(hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = Lab.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(Lab.Glass, MaterialTheme.shapes.small)
                .border(1.dp, Lab.Hairline, MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** The tools (in a level only those it allows), then time: run or stop, and the speed, which opens the choice of speeds. */
@Composable
private fun Dock(
    tools: List<Tool>,
    tool: Tool,
    onTool: (Tool) -> Unit,
    timeMode: TimeMode,
    speed: Double,
    onTimeMode: (TimeMode) -> Unit,
    choosingSpeed: Boolean,
    onChooseSpeed: () -> Unit,
    button: Dp,
) {
    Panel(glass = true, shape = RoundedCornerShape(DOCK_RADIUS), contentPadding = PaddingValues(DOCK_PADDING)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (item in tools) {
                LabIconButton(
                    icon = item.icon,
                    contentDescription = stringResource(item.label),
                    onClick = { onTool(item) },
                    active = item == tool,
                    size = button,
                )
            }
            Box(
                Modifier
                    .padding(horizontal = DIVIDER_PADDING)
                    .size(width = 1.dp, height = 26.dp)
                    .background(Lab.HairlineStrong),
            )
            val running = timeMode != TimeMode.FREEZE
            LabIconButton(
                icon = if (running) R.drawable.ic_pause else R.drawable.ic_play,
                contentDescription = stringResource(if (running) R.string.time_freeze else R.string.time_observe),
                onClick = { onTimeMode(if (running) TimeMode.FREEZE else TimeMode.OBSERVE) },
                tint = if (running) Lab.Text else Lab.Amber,
                size = button,
            )
            val speedName = if (timeMode == TimeMode.INCUBATE) stringResource(R.string.time_incubate) else "×" + speedLabel(speed)
            SpeedButton(
                text = if (timeMode == TimeMode.INCUBATE) null else "×" + speedLabel(speed),
                contentDescription = stringResource(R.string.speed_button, speedName),
                active = choosingSpeed || timeMode == TimeMode.INCUBATE,
                onClick = onChooseSpeed,
                size = button,
            )
        }
    }
}

/** The speed of time on a round button: «×¼» and so on, or the fast-forward sign during incubation. */
@Composable
private fun SpeedButton(text: String?, contentDescription: String, active: Boolean, onClick: () -> Unit, size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) Lab.AccentSoft else Color.Transparent)
            .border(1.dp, if (active) Lab.Accent.copy(alpha = ACTIVE_EDGE) else Lab.HairlineStrong, CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        if (text != null) {
            Text(text, style = LabType.ReadingSmall, color = if (active) Lab.Accent else Lab.Text, maxLines = 1)
        } else {
            Icon(painterResource(R.drawable.ic_fast_forward), contentDescription = null, tint = Lab.Accent, modifier = Modifier.size(20.dp))
        }
    }
}

/** The choice of speeds above the dock: slow motion, normal, faster and incubation. */
@Composable
private fun SpeedPicker(timeMode: TimeMode, speed: Double, onSpeed: (Double) -> Unit, onIncubate: () -> Unit, button: Dp) {
    Panel(glass = true, shape = RoundedCornerShape(DOCK_RADIUS), contentPadding = PaddingValues(DOCK_PADDING)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (option in SPEEDS) {
                val chosen = timeMode == TimeMode.OBSERVE && option == speed
                SpeedOption(
                    text = "×" + speedLabel(option),
                    chosen = chosen,
                    onClick = { onSpeed(option) },
                    size = button,
                )
            }
            LabIconButton(
                icon = R.drawable.ic_fast_forward,
                contentDescription = stringResource(R.string.time_incubate),
                onClick = onIncubate,
                active = timeMode == TimeMode.INCUBATE,
                tint = Lab.Text,
                size = button,
            )
        }
    }
}

@Composable
private fun SpeedOption(text: String, chosen: Boolean, onClick: () -> Unit, size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (chosen) Lab.AccentSoft else Color.Transparent)
            .border(1.dp, if (chosen) Lab.Accent.copy(alpha = ACTIVE_EDGE) else Color.Transparent, CircleShape)
            .selectable(selected = chosen, role = Role.RadioButton, onClick = onClick),
    ) {
        Text(text, style = LabType.ReadingSmall, color = if (chosen) Lab.Accent else Lab.Text, maxLines = 1)
    }
}

/** «−», «+» and «вся чашка»: the camera without gestures. */
@Composable
private fun CameraControls(onZoomOut: () -> Unit, onZoomIn: () -> Unit, onWholeDish: () -> Unit, modifier: Modifier = Modifier) {
    Panel(glass = true, shape = RoundedCornerShape(CAMERA_RADIUS), contentPadding = PaddingValues(2.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabIconButton(R.drawable.ic_minus, stringResource(R.string.zoom_out), onZoomOut, tint = Lab.Text, size = CAMERA_BUTTON, iconSize = 18.dp)
            LabIconButton(R.drawable.ic_plus, stringResource(R.string.zoom_in), onZoomIn, tint = Lab.Text, size = CAMERA_BUTTON, iconSize = 18.dp)
            LabIconButton(R.drawable.ic_fit, stringResource(R.string.whole_dish), onWholeDish, tint = Lab.Text, size = CAMERA_BUTTON, iconSize = 20.dp)
        }
    }
}

/** SPEC «Диагностика»: mode, type, mass, age, nitrates, S1–S4, bonds and «Копировать геном». */
@Composable
private fun SpecimenCard(
    info: CellInfo,
    following: Boolean,
    onFollow: () -> Unit,
    onCopyGenome: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeNames = stringArrayResource(R.array.cell_types)
    val membrane = Color(info.red.coerceIn(0f, 1f), info.green.coerceIn(0f, 1f), info.blue.coerceIn(0f, 1f))
    Panel(glass = true, contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 12.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(16.dp)
                    .background(membrane.copy(alpha = SWATCH_FILL), CircleShape)
                    .border(2.dp, membrane, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow(stringResource(R.string.specimen_title))
                Text(
                    text = stringResource(R.string.diagnostics_title, info.mode + 1, typeNames[info.type.ordinal]),
                    style = MaterialTheme.typography.titleSmall,
                    color = Lab.Text,
                    maxLines = 1,
                )
            }
            LabIconButton(R.drawable.ic_close, stringResource(R.string.close), onClose, size = 40.dp, iconSize = 18.dp)
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, end = 8.dp),
        ) {
            Readout(stringResource(R.string.diag_mass), stringResource(R.string.mass_value, formatNumber(info.mass.toDouble(), 2)))
            Readout(stringResource(R.string.stat_age), stringResource(R.string.hours_value, formatNumber(info.ageHours.toDouble(), 1)))
            Readout(stringResource(R.string.diag_nitrates), formatNumber(info.nitrates.toDouble(), 2))
            Readout(stringResource(R.string.diag_bonds), info.bonds.toString())
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, end = 8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SignalBar("S1", info.s1)
                SignalBar("S3", info.s3)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SignalBar("S2", info.s2)
                SignalBar("S4", info.s4)
            }
        }
        Text(
            text = if (info.parentGenomeId == 0L) {
                stringResource(R.string.diag_genome_original, info.genomeId)
            } else {
                stringResource(R.string.diag_genome, info.genomeId, info.parentGenomeId)
            },
            style = LabType.Mono,
            color = Lab.TextDim,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp, end = 8.dp),
        ) {
            LabButton(
                text = stringResource(R.string.diag_follow),
                onClick = onFollow,
                icon = R.drawable.ic_follow,
                kind = if (following) ButtonKind.ACTIVE else ButtonKind.SECONDARY,
            )
            LabButton(text = stringResource(R.string.diag_copy_genome), onClick = onCopyGenome, icon = R.drawable.ic_dna)
        }
    }
}

/** A signal level −1…1 as a bar from the middle: cyan to the right for plus, amber to the left for minus. */
@Composable
private fun SignalBar(name: String, value: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, style = LabType.Mono, color = Lab.TextDim)
        Canvas(
            Modifier
                .weight(1f)
                .height(6.dp),
        ) {
            val middle = size.width / 2f
            val level = value.coerceIn(-1f, 1f)
            val end = middle + level * middle
            drawRect(Lab.Hairline, size = size)
            drawRect(
                color = if (level >= 0f) Lab.Accent else Lab.Amber,
                topLeft = Offset(min(middle, end), 0f),
                size = Size(abs(end - middle), size.height),
            )
            drawLine(Lab.TextFaint, Offset(middle, 0f), Offset(middle, size.height), strokeWidth = 1.dp.toPx())
        }
        Text(
            text = (if (value > 0f) "+" else "") + formatNumber(value.toDouble(), 2),
            style = LabType.Mono,
            color = Lab.Text,
            textAlign = TextAlign.End,
            modifier = Modifier.width(SIGNAL_TEXT_WIDTH),
        )
    }
}

/** The scale bar of the microscope, following the magnification. */
@Composable
private fun CameraScaleBar(camera: DishCamera, modifier: Modifier = Modifier) {
    val pixelsPerUm by produceState(0f, camera) {
        while (true) {
            withFrameNanos { }
            value = camera.pixelsPerUnit()
        }
    }
    if (pixelsPerUm > 0f) ScaleBar(pixelsPerUm, modifier)
}

/** A bar of a round length with its label, as on micrographs: the longest 1, 2 or 5 × 10ⁿ µm up to [maxLength]. */
@Composable
fun ScaleBar(pixelsPerUm: Float, modifier: Modifier = Modifier, maxLength: Dp = SCALE_BAR_MAX) {
    val density = LocalDensity.current
    val lengthUm = roundLength(with(density) { maxLength.toPx() } / pixelsPerUm)
    if (lengthUm <= 0f) return
    val width = with(density) { (lengthUm * pixelsPerUm).toDp() }
    val label = if (lengthUm >= UM_PER_MM) {
        stringResource(R.string.mm_value, formatNumber(lengthUm / UM_PER_MM.toDouble(), 0))
    } else {
        stringResource(R.string.um_value, lengthUm.roundToInt())
    }
    Column(modifier) {
        Text(
            text = label,
            style = LabType.Mono.copy(shadow = Shadow(Color.Black, blurRadius = TEXT_SHADOW_BLUR)),
            color = Lab.Text,
        )
        Canvas(
            Modifier
                .padding(top = 3.dp)
                .size(width = width, height = 7.dp),
        ) {
            val y = size.height - 1.5.dp.toPx()
            val outline = Color.Black.copy(alpha = OUTLINE_ALPHA)
            for ((color, extra) in listOf(outline to 1.5.dp.toPx(), Lab.Text to 0f)) {
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx() + extra, cap = StrokeCap.Square)
                drawLine(color, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.5.dp.toPx() + extra)
                drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1.5.dp.toPx() + extra)
            }
        }
    }
}

/** The longest of 1, 2, 5, 10, 20, 50… not above [limit]. */
private fun roundLength(limit: Float): Float {
    if (limit <= 0f || limit.isNaN() || limit.isInfinite()) return 0f
    val magnitude = 10.0.pow(floor(log10(limit.toDouble()))).toFloat()
    for (multiple in floatArrayOf(5f, 2f, 1f)) {
        if (multiple * magnitude <= limit) return multiple * magnitude
    }
    return magnitude
}

private val TimeMode.label: Int
    @StringRes get() = when (this) {
        TimeMode.FREEZE -> R.string.time_freeze
        TimeMode.OBSERVE -> R.string.time_observe
        TimeMode.INCUBATE -> R.string.time_incubate
    }

private val TimeMode.hint: Int
    @StringRes get() = when (this) {
        TimeMode.FREEZE -> R.string.hint_freeze
        TimeMode.OBSERVE -> R.string.hint_observe
        TimeMode.INCUBATE -> R.string.hint_incubate
    }

private val Tool.label: Int
    @StringRes get() = when (this) {
        Tool.SYNTHESIZER -> R.string.tool_synthesizer
        Tool.TWEEZERS -> R.string.tool_tweezers
        Tool.FEED -> R.string.tool_feed
        Tool.DELETE -> R.string.tool_delete
        Tool.DIAGNOSTICS -> R.string.tool_diagnostics
    }

private val Tool.hint: Int
    @StringRes get() = when (this) {
        Tool.SYNTHESIZER -> R.string.hint_synthesizer
        Tool.TWEEZERS -> R.string.hint_tweezers
        Tool.FEED -> R.string.hint_feed
        Tool.DELETE -> R.string.hint_delete
        Tool.DIAGNOSTICS -> R.string.hint_diagnostics
    }

private val Tool.icon: Int
    @DrawableRes get() = when (this) {
        Tool.SYNTHESIZER -> R.drawable.ic_tool_synthesizer
        Tool.TWEEZERS -> R.drawable.ic_tool_tweezers
        Tool.FEED -> R.drawable.ic_tool_feed
        Tool.DELETE -> R.drawable.ic_tool_delete
        Tool.DIAGNOSTICS -> R.drawable.ic_tool_diagnostics
    }

private val OVERLAY_MARGIN = 10.dp
private val READINGS_MAX = 420.dp
private val SIDE_PANEL_MAX = 360.dp
private val SIDE_PANEL_GAP = 12.dp
private val DOCK_COLUMN_MAX = 460.dp

/** The dock: seven buttons, a divider with its padding, the panel's own padding and edge. */
private const val DOCK_BUTTONS = 7
private val DOCK_BUTTON_MAX = 46.dp
private val DIVIDER_PADDING = 6.dp
private val DOCK_PADDING = 4.dp
private val DOCK_EXTRA = DIVIDER_PADDING * 2 + 1.dp + DOCK_PADDING * 2 + 2.dp
private val DOCK_RADIUS = 26.dp

private const val CAPTION_MS = 4500L
private const val ACTIVE_EDGE = 0.6f
private val CAMERA_BUTTON = 40.dp
private val CAMERA_RADIUS = 22.dp
private const val SPEED_DECIMALS_BELOW = 10.0
private const val SWATCH_FILL = 0.35f
private val SIGNAL_TEXT_WIDTH = 40.dp

private val SCALE_BAR_MAX = 96.dp
private const val UM_PER_MM = 1000f
private const val TEXT_SHADOW_BLUR = 4f
private const val OUTLINE_ALPHA = 0.55f
