package io.github.torintheone.petri.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.torintheone.petri.PetriViewModel
import io.github.torintheone.petri.R
import io.github.torintheone.petri.data.Storage
import io.github.torintheone.petri.sim.CellType
import io.github.torintheone.petri.sim.Genome
import io.github.torintheone.petri.sim.Mode
import io.github.torintheone.petri.sim.PreviewFrame
import io.github.torintheone.petri.sim.ProgrammableValue
import io.github.torintheone.petri.sim.SenseTarget
import io.github.torintheone.petri.sim.SignalChannel
import io.github.torintheone.petri.sim.SignalInput
import io.github.torintheone.petri.sim.SimConfig
import io.github.torintheone.petri.sim.Substance
import io.github.torintheone.petri.sim.ValueShape
import io.github.torintheone.petri.ui.theme.Lab
import io.github.torintheone.petri.ui.theme.LabType
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** «Геном»: growth preview on top, the 40 modes below it, then every parameter of the chosen mode. */
@Composable
fun GenomeScreen(viewModel: PetriViewModel, modifier: Modifier = Modifier) {
    val editor = viewModel.editor
    val genome by editor.genome.collectAsStateWithLifecycle()
    val frames by editor.preview.collectAsStateWithLifecycle()
    val preview: @Composable (Modifier) -> Unit = { paneModifier ->
        PreviewPane(
            frames = frames,
            hour = editor.previewHour,
            editedMode = editor.editedMode,
            busy = editor.previewBusy,
            onTapMode = editor::selectMode,
            modifier = paneModifier,
        )
    }
    val scrubber: @Composable () -> Unit = {
        GrowthScrubber(hour = editor.previewHour, onHour = { editor.previewHour = it })
    }
    val simple = viewModel.simpleEditor
    val header: @Composable () -> Unit = {
        EditorHeader(
            genome = genome,
            simple = simple,
            onSimple = viewModel::changeEditorLook,
            onRename = viewModel.editor::rename,
        )
    }
    val strip: @Composable () -> Unit = {
        if (simple) {
            OrganismStrip(genome, editor.editedMode, editor::selectMode)
        } else {
            ModeStrip(genome, editor.editedMode, editor::selectMode)
        }
    }
    val parameters: @Composable (Modifier) -> Unit = { listModifier ->
        // Each editor starts at its top.
        val scroll = rememberSaveable(simple, saver = ScrollState.Saver) { ScrollState(0) }
        Column(
            modifier = listModifier.verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (simple) {
                SimpleModeEditor(
                    genome = genome,
                    index = editor.editedMode,
                    onChange = editor::changeMode,
                    onNewKind = viewModel::newChildKind,
                    onOpenFull = { viewModel.changeEditorLook(false) },
                )
            } else {
                SwitchRow(
                    label = stringResource(R.string.editor_snapping),
                    checked = editor.snapping,
                    onChange = { editor.snapping = it },
                    help = stringResource(R.string.editor_snapping_help),
                )
                ModeEditor(
                    genome = genome,
                    index = editor.editedMode,
                    snapping = editor.snapping,
                    onChange = editor::changeMode,
                    onMakeInitial = { editor.makeInitial(editor.editedMode) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    BoxWithConstraints(modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Read here: the layout scopes below hide the constraints of BoxWithConstraints.
        val previewHeight = minOf(maxHeight * PREVIEW_SHARE, PREVIEW_MAX_HEIGHT)
        if (maxWidth > maxHeight) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .padding(bottom = 8.dp),
                ) {
                    preview(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    scrubber()
                }
                Column(
                    Modifier
                        .weight(0.55f)
                        .fillMaxHeight(),
                ) {
                    header()
                    strip()
                    parameters(Modifier.weight(1f))
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
            ) {
                header()
                preview(
                    Modifier
                        .fillMaxWidth()
                        .height(previewHeight),
                )
                scrubber()
                strip()
                parameters(Modifier.weight(1f))
            }
        }
    }
}

/** The genome's name (a tap renames it) and the choice between the simple and the full editor. */
@Composable
private fun EditorHeader(genome: Genome, simple: Boolean, onSimple: (Boolean) -> Unit, onRename: (String) -> Unit) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = stringResource(R.string.editor_rename)) { renaming = true }
                .padding(vertical = 2.dp),
        ) {
            Eyebrow(stringResource(R.string.tab_genome))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = genome.name.ifBlank { stringResource(R.string.genome_untitled) },
                    style = MaterialTheme.typography.titleLarge,
                    color = Lab.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = null,
                    tint = Lab.TextFaint,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(16.dp),
                )
            }
        }
        Segmented(
            options = listOf(true, false),
            selected = simple,
            label = { stringResource(if (it) R.string.editor_simple else R.string.editor_full) },
            onSelect = onSimple,
            modifier = Modifier
                .padding(start = 12.dp)
                .width(LOOK_SWITCH_WIDTH),
        )
    }
    if (renaming) {
        NameDialog(
            title = stringResource(R.string.editor_rename),
            initial = genome.name,
            note = null,
            onConfirm = { name ->
                renaming = false
                onRename(name.take(Storage.MAX_NAME_LENGTH))
            },
            onDismiss = { renaming = false },
        )
    }
}

/**
 * The growth preview as seen in an eyepiece: a grid every round number of micrometres, the hour, the number of
 * cells and a scale bar. A tap on a cell opens that cell's mode.
 */
@Composable
private fun PreviewPane(
    frames: List<PreviewFrame>,
    hour: Int,
    editedMode: Int,
    busy: Boolean,
    onTapMode: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frame = frames.getOrNull(hour) ?: frames.lastOrNull()
    // One scale for all hours, so the organism visibly grows while the slider moves; buds that drift away
    // may leave the picture.
    val extent = remember(frames) { max(frames.maxOfOrNull { it.bodyExtent } ?: 0f, MIN_EXTENT_UM) }
    val shape = MaterialTheme.shapes.medium
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(MediumColor)
            .border(1.dp, Lab.Hairline, shape),
    ) {
        val scale = with(LocalDensity.current) { min(maxWidth.toPx(), maxHeight.toPx()) } / (2f * extent * FIT_MARGIN)
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInputForCells(frame, extent, onTapMode),
        ) {
            drawGraticule(scale)
            if (frame != null) drawOrganism(frame, scale, editedMode)
        }
        Text(
            text = stringResource(R.string.preview_hour, frame?.hour ?: hour),
            style = LabType.Mono,
            color = Lab.Text,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
        ) {
            if (busy || frame == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = Lab.Accent,
                )
            }
            if (frame != null) {
                Text(stringResource(R.string.preview_cells, frame.cellCount), style = LabType.Mono, color = Lab.TextDim)
            }
        }
        ScaleBar(
            pixelsPerUm = scale,
            maxLength = PREVIEW_SCALE_BAR,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
        )
    }
}

/** Lines every round number of micrometres through the middle of the picture, every fifth one brighter. */
private fun DrawScope.drawGraticule(scale: Float) {
    val step = gridStep(GRID_MIN_SPACING.toPx() / scale) * scale
    val cx = size.width / 2f
    val cy = size.height / 2f
    val across = (maxOf(cx, cy) / step).toInt() + 1
    for (k in -across..across) {
        val color = when {
            k == 0 -> GridAxis
            k % GRID_MAJOR_EVERY == 0 -> GridMajor
            else -> GridMinor
        }
        val x = cx + k * step
        val y = cy + k * step
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
}

/** The smallest of 1, 2, 5, 10, 20, 50… µm not below [minimum]. */
private fun gridStep(minimum: Float): Float {
    var magnitude = 1f
    while (magnitude * 10f <= minimum) magnitude *= 10f
    while (magnitude > minimum && magnitude > 0.01f) magnitude /= 10f
    for (multiple in floatArrayOf(1f, 2f, 5f, 10f)) {
        if (multiple * magnitude >= minimum) return multiple * magnitude
    }
    return 10f * magnitude
}

/** The hour of the preview: ▶ plays the growth from the start, the slider picks any hour. */
@Composable
private fun GrowthScrubber(hour: Int, onHour: (Int) -> Unit) {
    var playing by remember { mutableStateOf(false) }
    val last = SimConfig.PREVIEW_HOURS - 1
    val currentHour by rememberUpdatedState(hour)
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var shown = if (currentHour >= last) 0 else currentHour
        onHour(shown)
        while (shown < last) {
            delay(PLAY_STEP_MS)
            shown++
            onHour(shown)
        }
        playing = false
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        LabIconButton(
            icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play,
            contentDescription = stringResource(if (playing) R.string.preview_stop else R.string.preview_play),
            onClick = { playing = !playing },
            tint = Lab.Accent,
            size = 40.dp,
            iconSize = 18.dp,
        )
        LabSlider(
            value = hour.toFloat(),
            onChange = {
                playing = false
                onHour(it.roundToInt())
            },
            range = 0f..last.toFloat(),
            levels = SimConfig.PREVIEW_HOURS,
            steppers = false,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.hours_value, hour.toString()),
            style = LabType.ReadingSmall,
            color = Lab.Text,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}

private fun Modifier.pointerInputForCells(frame: PreviewFrame?, extent: Float, onTapMode: (Int) -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(frame, extent) {
            detectTapGestures { offset ->
                val shown = frame ?: return@detectTapGestures
                val scale = min(size.width, size.height) / (2f * extent * FIT_MARGIN)
                val x = shown.bodyCenterX + (offset.x - size.width / 2f) / scale
                val y = shown.bodyCenterY - (offset.y - size.height / 2f) / scale
                val i = shown.cellAt(x, y, slack = TAP_SLACK_PX / scale)
                if (i >= 0) onTapMode(shown.mode(i))
            }
        },
    )

/** M1–M40 as coloured cells; faded ones cannot be reached from the initial mode, a star marks the initial one. */
@Composable
private fun ModeStrip(genome: Genome, edited: Int, onSelect: (Int) -> Unit) {
    val reachable = remember(genome) { genome.reachableModes(genome.initialMode) }
    val state = rememberLazyListState()
    LaunchedEffect(edited) { state.animateScrollToItem((edited - 3).coerceAtLeast(0)) }
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        items(Mode.MODE_COUNT) { index ->
            ModeChip(
                index = index,
                mode = genome.modes[index],
                selected = index == edited,
                initial = index == genome.initialMode,
                reachable = index in reachable,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun ModeChip(index: Int, mode: Mode, selected: Boolean, initial: Boolean, reachable: Boolean, onClick: () -> Unit) {
    val color = modeColor(mode)
    val alpha = if (reachable) 1f else UNREACHABLE_ALPHA
    Box(Modifier.size(MODE_CHIP)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, if (selected) Lab.Accent else Color.Transparent, CircleShape)
                .padding(if (selected) 4.dp else 2.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = MEMBRANE_FILL * alpha))
                .border(2.dp, color.copy(alpha = alpha), CircleShape)
                .clickable(onClick = onClick),
        ) {
            Text(
                text = "${index + 1}",
                color = Lab.Text.copy(alpha = if (reachable) 1f else UNREACHABLE_TEXT_ALPHA),
                style = LabType.ReadingSmall,
                fontWeight = if (initial) FontWeight.Bold else FontWeight.Medium,
            )
        }
        if (initial) {
            Icon(
                painter = painterResource(R.drawable.ic_star),
                contentDescription = stringResource(R.string.editor_initial),
                tint = Lab.Amber,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(12.dp),
            )
        }
    }
}

fun modeColor(mode: Mode): Color = Color(mode.red.coerceIn(0f, 1f), mode.green.coerceIn(0f, 1f), mode.blue.coerceIn(0f, 1f))

/** Every parameter of one mode (SPEC «Общие параметры режима» and the parameters of its type), group by group. */
@Composable
private fun ModeEditor(
    genome: Genome,
    index: Int,
    snapping: Boolean,
    onChange: ((Mode) -> Mode) -> Unit,
    onMakeInitial: () -> Unit,
) {
    val mode = genome.modes[index]
    val levels = if (snapping) SNAP_LEVELS else 0
    val typeNames = stringArrayResource(R.array.cell_types)
    val help = stringArrayResource(R.array.help_mode)
    val initial = genome.initialMode == index

    Panel(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModeSwatch(mode, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow(stringResource(R.string.editor_mode_title, index + 1))
                Text(
                    text = typeNames[mode.cellType.ordinal],
                    style = MaterialTheme.typography.titleMedium,
                    color = Lab.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LabButton(
                text = stringResource(R.string.editor_initial),
                onClick = onMakeInitial,
                icon = R.drawable.ic_star,
                kind = if (initial) ButtonKind.ACTIVE else ButtonKind.SECONDARY,
            )
        }
        ChoiceRow(
            label = stringResource(R.string.param_cell_type),
            options = CellType.entries,
            selected = mode.cellType,
            optionText = { typeNames[it.ordinal] },
            onSelect = { type -> onChange { it.copy(cellType = type) } },
            help = help[HELP_TYPE],
        )
    }

    SectionPanel(stringResource(R.string.section_division)) {
        SliderRow(
            label = stringResource(R.string.param_split_mass),
            value = mode.splitMass,
            range = Mode.SPLIT_MASS,
            valueText = splitMassText(mode.splitMass),
            onChange = { v -> onChange { it.copy(splitMass = v) } },
            levels = levels,
            help = help[HELP_SPLIT_MASS],
        )
        SliderRow(
            label = stringResource(R.string.param_split_ratio),
            value = mode.splitRatio,
            range = Mode.SPLIT_RATIO,
            valueText = ratioText(mode.splitRatio),
            onChange = { v -> onChange { it.copy(splitRatio = v) } },
            levels = levels,
            help = help[HELP_SPLIT_RATIO],
        )
        SliderRow(
            label = stringResource(R.string.param_split_angle),
            value = mode.splitAngle,
            range = Mode.ANGLE,
            valueText = degrees(mode.splitAngle),
            onChange = { v -> onChange { it.copy(splitAngle = v) } },
            levels = levels,
            help = help[HELP_SPLIT_ANGLE],
        )
        SwitchRow(stringResource(R.string.param_make_adhesin), mode.makeAdhesin, { v -> onChange { it.copy(makeAdhesin = v) } }, help[HELP_ADHESIN])
    }
    ChildParameters(
        title = stringResource(R.string.section_child, 1),
        genome = genome,
        childMode = mode.child1Mode,
        angle = mode.child1Angle,
        keepBonds = mode.child1KeepBonds,
        mirror = mode.child1Mirror,
        levels = levels,
        help = help,
        onMode = { v -> onChange { it.copy(child1Mode = v) } },
        onAngle = { v -> onChange { it.copy(child1Angle = v) } },
        onKeepBonds = { v -> onChange { it.copy(child1KeepBonds = v) } },
        onMirror = { v -> onChange { it.copy(child1Mirror = v) } },
    )
    ChildParameters(
        title = stringResource(R.string.section_child, 2),
        genome = genome,
        childMode = mode.child2Mode,
        angle = mode.child2Angle,
        keepBonds = mode.child2KeepBonds,
        mirror = mode.child2Mirror,
        levels = levels,
        help = help,
        onMode = { v -> onChange { it.copy(child2Mode = v) } },
        onAngle = { v -> onChange { it.copy(child2Angle = v) } },
        onKeepBonds = { v -> onChange { it.copy(child2KeepBonds = v) } },
        onMirror = { v -> onChange { it.copy(child2Mirror = v) } },
    )

    SectionPanel(stringResource(R.string.section_adhesin)) {
        SliderRow(
            label = stringResource(R.string.param_adhesin_stiffness),
            value = mode.adhesinStiffness,
            range = Mode.UNIT,
            valueText = decimal(mode.adhesinStiffness),
            onChange = { v -> onChange { it.copy(adhesinStiffness = v) } },
            levels = levels,
            help = help[HELP_ADHESIN_STIFFNESS],
        )
        SliderRow(
            label = stringResource(R.string.param_adhesin_length),
            value = mode.adhesinLength,
            range = Mode.UNIT,
            valueText = decimal(mode.adhesinLength),
            onChange = { v -> onChange { it.copy(adhesinLength = v) } },
            levels = levels,
            help = help[HELP_ADHESIN_LENGTH],
        )
        SliderRow(
            label = stringResource(R.string.param_max_bonds),
            value = mode.maxBonds.toFloat(),
            range = 0f..Mode.MAX_BONDS.toFloat(),
            valueText = mode.maxBonds.toString(),
            onChange = { v -> onChange { it.copy(maxBonds = v.roundToInt()) } },
            levels = Mode.MAX_BONDS + 1,
            help = help[HELP_MAX_BONDS],
        )
    }

    SectionPanel(stringResource(R.string.section_nutrition)) {
        SliderRow(
            label = stringResource(R.string.param_priority),
            value = mode.nutrientPriority,
            range = Mode.UNIT,
            valueText = decimal(mode.nutrientPriority),
            onChange = { v -> onChange { it.copy(nutrientPriority = v) } },
            levels = levels,
            help = help[HELP_PRIORITY],
        )
        SwitchRow(stringResource(R.string.param_prioritize), mode.prioritize, { v -> onChange { it.copy(prioritize = v) } }, help[HELP_PRIORITIZE])
    }

    SectionPanel(
        title = stringResource(R.string.section_cell),
        trailing = {
            Box(
                Modifier
                    .size(width = 40.dp, height = 18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(modeColor(mode)),
            )
        },
    ) {
        SliderRow(
            label = stringResource(R.string.param_cytoskeleton),
            value = mode.cytoskeleton,
            range = Mode.UNIT,
            valueText = decimal(mode.cytoskeleton),
            onChange = { v -> onChange { it.copy(cytoskeleton = v) } },
            levels = levels,
            help = help[HELP_CYTOSKELETON],
        )
        SliderRow(stringResource(R.string.param_red), mode.red, Mode.UNIT, decimal(mode.red), { v -> onChange { it.copy(red = v) } }, levels, help[HELP_COLOR])
        SliderRow(stringResource(R.string.param_green), mode.green, Mode.UNIT, decimal(mode.green), { v -> onChange { it.copy(green = v) } }, levels, help[HELP_COLOR])
        SliderRow(stringResource(R.string.param_blue), mode.blue, Mode.UNIT, decimal(mode.blue), { v -> onChange { it.copy(blue = v) } }, levels, help[HELP_COLOR])
    }

    TypeParameters(genome, mode, typeNames[mode.cellType.ordinal], snapping, onChange)
}

/** The mode's colour as a small cell: a pale inside and a membrane. */
@Composable
private fun ModeSwatch(mode: Mode, modifier: Modifier = Modifier) {
    val color = modeColor(mode)
    Box(
        modifier
            .background(color.copy(alpha = MEMBRANE_FILL), CircleShape)
            .border(2.dp, color, CircleShape),
    )
}

@Composable
private fun ChildParameters(
    title: String,
    genome: Genome,
    childMode: Int,
    angle: Float,
    keepBonds: Boolean,
    mirror: Boolean,
    levels: Int,
    help: Array<String>,
    onMode: (Int) -> Unit,
    onAngle: (Float) -> Unit,
    onKeepBonds: (Boolean) -> Unit,
    onMirror: (Boolean) -> Unit,
) {
    SectionPanel(title) {
        ModeChoiceRow(stringResource(R.string.param_child_mode), genome, childMode, onMode, help[HELP_CHILD_MODE])
        SliderRow(stringResource(R.string.param_child_angle), angle, Mode.ANGLE, degrees(angle), onAngle, levels, help[HELP_CHILD_ANGLE])
        SwitchRow(stringResource(R.string.param_keep_bonds), keepBonds, onKeepBonds, help[HELP_KEEP_BONDS])
        SwitchRow(stringResource(R.string.param_mirror), mirror, onMirror, help[HELP_MIRROR])
    }
}

/** A choice of one of the 40 modes, each shown with its colour. */
@Composable
private fun ModeChoiceRow(label: String, genome: Genome, selected: Int, onSelect: (Int) -> Unit, help: String? = null) {
    ChoiceRow(
        label = label,
        options = (0 until Mode.MODE_COUNT).toList(),
        selected = selected,
        optionText = { "M${it + 1}" },
        onSelect = onSelect,
        help = help,
        optionIcon = { index -> ModeSwatch(genome.modes[index], Modifier.size(14.dp)) },
    )
}

/** Parameters of the mode's cell type (SPEC «Типы клеток»), in a group named after the type; none for simple types. */
@Composable
private fun TypeParameters(genome: Genome, mode: Mode, typeName: String, snapping: Boolean, onChange: ((Mode) -> Mode) -> Unit) {
    if (mode.cellType !in TYPES_WITH_PARAMETERS) return
    val levels = if (snapping) SNAP_LEVELS else 0
    val help = stringArrayResource(R.array.help_type)
    val channels = SignalChannel.entries
    SectionPanel(typeName) {
        when (mode.cellType) {
            CellType.FLAGELLOCYTE -> {
                ProgrammableEditor(stringResource(R.string.param_swim_force), mode.swimForce, 0f..1f, snapping, { v -> onChange { it.copy(swimForce = v) } }, help[HELP_SWIM])
            }
            CellType.BUOYOCYTE -> {
                ProgrammableEditor(stringResource(R.string.param_density), mode.density, 0f..1f, snapping, { v -> onChange { it.copy(density = v) } }, help[HELP_DENSITY])
            }
            CellType.MYOCYTE -> {
                ProgrammableEditor(stringResource(R.string.param_contraction), mode.contraction, -1f..1f, snapping, { v -> onChange { it.copy(contraction = v) } }, help[HELP_CONTRACTION])
                ProgrammableEditor(stringResource(R.string.param_bend), mode.bend, -1f..1f, snapping, { v -> onChange { it.copy(bend = v) } }, help[HELP_BEND], divider = true)
                ProgrammableEditor(stringResource(R.string.param_lift), mode.lift, 0f..1f, snapping, { v -> onChange { it.copy(lift = v) } }, help[HELP_LIFT], divider = true)
            }
            CellType.NEUROCYTE -> {
                val outputs = listOf(mode.neuronOutput1, mode.neuronOutput2, mode.neuronOutput3, mode.neuronOutput4)
                outputs.forEachIndexed { k, output ->
                    ProgrammableEditor(
                        label = stringResource(R.string.param_neuron_output, channels[k].name),
                        value = output,
                        constantRange = -1f..1f,
                        snapping = snapping,
                        onChange = { v ->
                            onChange {
                                when (k) {
                                    0 -> it.copy(neuronOutput1 = v)
                                    1 -> it.copy(neuronOutput2 = v)
                                    2 -> it.copy(neuronOutput3 = v)
                                    else -> it.copy(neuronOutput4 = v)
                                }
                            }
                        },
                        help = help[HELP_NEURON_OUTPUT],
                        divider = k > 0,
                    )
                }
                Hairline(Modifier.padding(vertical = 6.dp))
                SliderRow(
                    label = stringResource(R.string.param_oscillator_frequency),
                    value = mode.oscillatorFrequency,
                    range = Mode.UNIT,
                    valueText = decimal(mode.oscillatorFrequency),
                    onChange = { v -> onChange { it.copy(oscillatorFrequency = v) } },
                    levels = levels,
                    help = help[HELP_OSCILLATOR],
                )
                ChoiceRow(stringResource(R.string.param_oscillator_channel_a), channels, mode.oscillatorChannelA, { it.name }, { v -> onChange { it.copy(oscillatorChannelA = v) } })
                ChoiceRow(stringResource(R.string.param_oscillator_channel_b), channels, mode.oscillatorChannelB, { it.name }, { v -> onChange { it.copy(oscillatorChannelB = v) } })
            }
            CellType.STEMOCYTE -> {
                ModeChoiceRow(stringResource(R.string.param_path_mode, 1), genome, mode.path1Mode, { v -> onChange { it.copy(path1Mode = v) } }, help[HELP_PATH])
                ProgrammableEditor(stringResource(R.string.param_path_signal, 1), mode.path1Signal, -1f..1f, snapping, { v -> onChange { it.copy(path1Signal = v) } }, help[HELP_PATH])
                Hairline(Modifier.padding(vertical = 6.dp))
                ModeChoiceRow(stringResource(R.string.param_path_mode, 2), genome, mode.path2Mode, { v -> onChange { it.copy(path2Mode = v) } }, help[HELP_PATH])
                ProgrammableEditor(stringResource(R.string.param_path_signal, 2), mode.path2Signal, -1f..1f, snapping, { v -> onChange { it.copy(path2Signal = v) } }, help[HELP_PATH])
            }
            CellType.SENSEOCYTE, CellType.STEREOCYTE -> {
                val targets = stringArrayResource(R.array.sense_targets)
                ChoiceRow(stringResource(R.string.param_sense_target), SenseTarget.entries, mode.senseTarget, { targets[it.ordinal] }, { v -> onChange { it.copy(senseTarget = v) } }, help[HELP_SENSE_TARGET])
                ChoiceRow(stringResource(R.string.param_signal_channel), channels, mode.senseChannel, { it.name }, { v -> onChange { it.copy(senseChannel = v) } })
                SliderRow(stringResource(R.string.param_sense_gain), mode.senseGain, Mode.UNIT, decimal(mode.senseGain), { v -> onChange { it.copy(senseGain = v) } }, levels, help[HELP_SENSE_GAIN])
                if (mode.senseTarget == SenseTarget.CELLS) {
                    SliderRow(stringResource(R.string.param_sense_red), mode.senseRed, Mode.UNIT, decimal(mode.senseRed), { v -> onChange { it.copy(senseRed = v) } }, levels, help[HELP_SENSE_COLOR])
                    SliderRow(stringResource(R.string.param_sense_green), mode.senseGreen, Mode.UNIT, decimal(mode.senseGreen), { v -> onChange { it.copy(senseGreen = v) } }, levels, help[HELP_SENSE_COLOR])
                    SliderRow(stringResource(R.string.param_sense_blue), mode.senseBlue, Mode.UNIT, decimal(mode.senseBlue), { v -> onChange { it.copy(senseBlue = v) } }, levels, help[HELP_SENSE_COLOR])
                    SliderRow(
                        stringResource(R.string.param_sense_threshold), mode.senseColorThreshold, Mode.UNIT, decimal(mode.senseColorThreshold),
                        { v -> onChange { it.copy(senseColorThreshold = v) } }, levels, help[HELP_SENSE_COLOR],
                    )
                }
            }
            CellType.SECROCYTE -> {
                val substances = stringArrayResource(R.array.substances)
                ChoiceRow(stringResource(R.string.param_secretion), Substance.entries, mode.secretion, { substances[it.ordinal] }, { v -> onChange { it.copy(secretion = v) } }, help[HELP_SECRETION])
                SliderRow(
                    stringResource(R.string.param_secretion_intensity), mode.secretionIntensity, Mode.UNIT, decimal(mode.secretionIntensity),
                    { v -> onChange { it.copy(secretionIntensity = v) } }, levels, help[HELP_SECRETION],
                )
                if (mode.secretion == Substance.SIGNAL) {
                    ChoiceRow(stringResource(R.string.param_signal_channel), channels, mode.secretionChannel, { it.name }, { v -> onChange { it.copy(secretionChannel = v) } })
                }
            }
            CellType.VIROCYTE -> {
                ModeChoiceRow(stringResource(R.string.param_virus_mode), genome, mode.virusMode, { v -> onChange { it.copy(virusMode = v) } }, help[HELP_VIRUS])
            }
            CellType.GAMETE -> {
                ModeChoiceRow(stringResource(R.string.param_fusion_mode), genome, mode.fusionMode, { v -> onChange { it.copy(fusionMode = v) } }, help[HELP_GAMETE])
            }
            CellType.CILIOCYTE -> {
                ProgrammableEditor(stringResource(R.string.param_left_cilia), mode.leftCilia, -1f..1f, snapping, { v -> onChange { it.copy(leftCilia = v) } }, help[HELP_CILIA])
                ProgrammableEditor(stringResource(R.string.param_right_cilia), mode.rightCilia, -1f..1f, snapping, { v -> onChange { it.copy(rightCilia = v) } }, help[HELP_CILIA], divider = true)
            }
            else -> Unit
        }
    }
}

/** Types whose modes have parameters of their own, shown in a group after the common ones. */
private val TYPES_WITH_PARAMETERS = setOf(
    CellType.FLAGELLOCYTE,
    CellType.BUOYOCYTE,
    CellType.MYOCYTE,
    CellType.NEUROCYTE,
    CellType.STEMOCYTE,
    CellType.SENSEOCYTE,
    CellType.STEREOCYTE,
    CellType.SECROCYTE,
    CellType.VIROCYTE,
    CellType.GAMETE,
    CellType.CILIOCYTE,
)

/**
 * A programmable value (SPEC «Программируемые значения»): a constant, or a linear or step function of one input,
 * with a small graph of the function. [divider] draws a line above it, between values of one group.
 */
@Composable
private fun ProgrammableEditor(
    label: String,
    value: ProgrammableValue,
    constantRange: ClosedFloatingPointRange<Float>,
    snapping: Boolean,
    onChange: (ProgrammableValue) -> Unit,
    help: String? = null,
    divider: Boolean = false,
) {
    val levels = if (snapping) SNAP_LEVELS else 0
    val inputs = stringArrayResource(R.array.pv_inputs)
    Column(Modifier.fillMaxWidth()) {
        if (divider) Hairline(Modifier.padding(vertical = 6.dp))
        ChoiceRow(
            label = label,
            options = SignalInput.entries,
            selected = value.input,
            optionText = { stringResource(R.string.pv_input_value, inputs[it.ordinal]) },
            onSelect = { input -> onChange(value.copy(input = input).sanitized()) },
            help = help,
        )
        if (value.input == SignalInput.NONE) {
            SliderRow(stringResource(R.string.pv_value), value.b, constantRange, decimal(value.b), { v -> onChange(value.copy(b = v)) }, levels)
        } else {
            Segmented(
                options = ValueShape.entries,
                selected = value.shape,
                label = { shape -> stringResource(if (shape == ValueShape.LINEAR) R.string.pv_shape_linear else R.string.pv_shape_step) },
                onSelect = { shape -> onChange(value.copy(shape = shape).sanitized()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
            val step = value.shape == ValueShape.STEP
            val ab = if (step) ProgrammableValue.STEP_AB else ProgrammableValue.LINEAR_AB
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    text = stringResource(if (step) R.string.pv_formula_step else R.string.pv_formula_linear),
                    style = LabType.Mono,
                    color = Lab.TextDim,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                )
                FunctionGraph(value, Modifier.size(width = 104.dp, height = 56.dp))
            }
            SliderRow("a", value.a, ab, decimal(value.a), { v -> onChange(value.copy(a = v)) }, levels)
            SliderRow("b", value.b, ab, decimal(value.b), { v -> onChange(value.copy(b = v)) }, levels)
            if (step) SliderRow("c", value.c, ProgrammableValue.STEP_C, decimal(value.c), { v -> onChange(value.copy(c = v)) }, levels)
        }
    }
}

/** The function of a programmable value for inputs −1…1, outputs clipped to −1…1, on a small grid. */
@Composable
private fun FunctionGraph(value: ProgrammableValue, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Canvas(
        modifier
            .clip(shape)
            .background(Lab.Raised)
            .border(1.dp, Lab.Hairline, shape),
    ) {
        fun px(input: Float) = (input + 1f) / 2f * size.width
        fun py(output: Float) = (1f - (output.coerceIn(-1f, 1f) + 1f) / 2f) * size.height
        for (k in 1 until FUNCTION_GRID) {
            val x = size.width * k / FUNCTION_GRID
            val y = size.height * k / FUNCTION_GRID
            drawLine(Lab.Hairline, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            drawLine(Lab.Hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        drawLine(Lab.HairlineStrong, Offset(0f, py(0f)), Offset(size.width, py(0f)), strokeWidth = 1.dp.toPx())
        drawLine(Lab.HairlineStrong, Offset(px(0f), 0f), Offset(px(0f), size.height), strokeWidth = 1.dp.toPx())
        val path = Path()
        for (k in 0..GRAPH_SAMPLES) {
            val input = -1f + 2f * k / GRAPH_SAMPLES
            val point = Offset(px(input), py(value.evaluate(input)))
            if (k == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, Lab.Accent, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun splitMassText(value: Float): String = when {
    value <= Mode.SPLIT_BY_AGE -> stringResource(R.string.value_by_age)
    value >= Mode.SPLIT_NEVER -> stringResource(R.string.value_never)
    else -> stringResource(R.string.mass_value, decimal(value))
}

private fun ratioText(ratio: Float): String {
    val first = (ratio * 100f).roundToInt()
    return "$first : ${100 - first}"
}

private const val TAP_SLACK_PX = 24f
private const val GRAPH_SAMPLES = 48
private const val FUNCTION_GRID = 4

/** The preview takes this share of the screen height in portrait, up to [PREVIEW_MAX_HEIGHT]. */
private const val PREVIEW_SHARE = 0.36f
private val PREVIEW_MAX_HEIGHT = 360.dp
private val PREVIEW_SCALE_BAR = 72.dp

/** Grid lines of the preview are at least this far apart; every fifth one is brighter. */
private val GRID_MIN_SPACING = 28.dp
private const val GRID_MAJOR_EVERY = 5
private val GridMinor = Color(0x0DFFFFFF)
private val GridMajor = Color(0x1AFFFFFF)
private val GridAxis = Color(0x26FFFFFF)

/** ▶ in the preview shows one game hour per this many milliseconds. */
private const val PLAY_STEP_MS = 110L

private val MODE_CHIP = 40.dp

/** «Простой | Полный» in the header. */
private val LOOK_SWITCH_WIDTH = 176.dp
private const val MEMBRANE_FILL = 0.3f
private const val UNREACHABLE_ALPHA = 0.3f
private const val UNREACHABLE_TEXT_ALPHA = 0.55f

// Positions in R.array.help_mode.
private const val HELP_TYPE = 0
private const val HELP_SPLIT_MASS = 1
private const val HELP_SPLIT_RATIO = 2
private const val HELP_SPLIT_ANGLE = 3
private const val HELP_ADHESIN = 4
private const val HELP_CHILD_MODE = 5
private const val HELP_CHILD_ANGLE = 6
private const val HELP_KEEP_BONDS = 7
private const val HELP_MIRROR = 8
private const val HELP_ADHESIN_STIFFNESS = 9
private const val HELP_ADHESIN_LENGTH = 10
private const val HELP_MAX_BONDS = 11
private const val HELP_PRIORITY = 12
private const val HELP_PRIORITIZE = 13
private const val HELP_CYTOSKELETON = 14
private const val HELP_COLOR = 15

// Positions in R.array.help_type.
private const val HELP_SWIM = 0
private const val HELP_DENSITY = 1
private const val HELP_CONTRACTION = 2
private const val HELP_BEND = 3
private const val HELP_LIFT = 4
private const val HELP_NEURON_OUTPUT = 5
private const val HELP_OSCILLATOR = 6
private const val HELP_PATH = 7
private const val HELP_SENSE_TARGET = 8
private const val HELP_SENSE_GAIN = 9
private const val HELP_SENSE_COLOR = 10
private const val HELP_SECRETION = 11
private const val HELP_VIRUS = 12
private const val HELP_GAMETE = 13
private const val HELP_CILIA = 14
