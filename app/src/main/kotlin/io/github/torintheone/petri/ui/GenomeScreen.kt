package io.github.torintheone.petri.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
            onHour = { editor.previewHour = it },
            onTapMode = editor::selectMode,
            modifier = paneModifier,
        )
    }
    val header: @Composable () -> Unit = {
        EditorHeader(
            genome = genome,
            snapping = editor.snapping,
            onSnapping = { editor.snapping = it },
            onRename = viewModel.editor::rename,
        )
    }
    val strip: @Composable () -> Unit = { ModeStrip(genome, editor.editedMode, editor::selectMode) }
    val parameters: @Composable (Modifier) -> Unit = { listModifier ->
        Column(listModifier.verticalScroll(rememberScrollState())) {
            ModeEditor(
                genome = genome,
                index = editor.editedMode,
                snapping = editor.snapping,
                onChange = editor::changeMode,
                onMakeInitial = { editor.makeInitial(editor.editedMode) },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    BoxWithConstraints(
        modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End)),
    ) {
        // Read here: the layout scopes below hide the constraints of BoxWithConstraints.
        val previewHeight = minOf(maxHeight * 0.4f, 380.dp)
        if (maxWidth > maxHeight) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                preview(Modifier.weight(0.45f).fillMaxHeight())
                Column(Modifier.weight(0.55f).fillMaxHeight()) {
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
                preview(Modifier.fillMaxWidth().height(previewHeight))
                strip()
                parameters(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EditorHeader(genome: Genome, snapping: Boolean, onSnapping: (Boolean) -> Unit, onRename: (String) -> Unit) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                text = genome.name.ifBlank { stringResource(R.string.genome_untitled) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            IconButton(onClick = { renaming = true }) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.editor_rename))
            }
        }
        FilterChip(
            selected = snapping,
            onClick = { onSnapping(!snapping) },
            label = { Text(stringResource(R.string.editor_snapping)) },
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

/** The growth preview with its time slider; a tap on a cell opens that cell's mode. */
@Composable
private fun PreviewPane(
    frames: List<PreviewFrame>,
    hour: Int,
    editedMode: Int,
    busy: Boolean,
    onHour: (Int) -> Unit,
    onTapMode: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frame = frames.getOrNull(hour) ?: frames.lastOrNull()
    // One scale for all hours, so the organism visibly grows while the slider moves.
    val extent = remember(frames) { max(frames.maxOfOrNull { it.extent } ?: 0f, MIN_EXTENT_UM) }
    Column(modifier) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MediumColor),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInputForCells(frame, extent, onTapMode),
            ) {
                if (frame != null) drawOrganism(frame, min(size.width, size.height) / (2f * extent * FIT_MARGIN), editedMode)
            }
            Text(
                text = stringResource(R.string.hours_value, (frame?.hour ?: hour).toString()),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            )
            if (busy || frame == null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        Slider(
            value = hour.toFloat(),
            onValueChange = { onHour(it.roundToInt()) },
            valueRange = 0f..(SimConfig.PREVIEW_HOURS - 1).toFloat(),
            steps = SimConfig.PREVIEW_HOURS - 2,
        )
    }
}

private fun Modifier.pointerInputForCells(frame: PreviewFrame?, extent: Float, onTapMode: (Int) -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(frame, extent) {
            detectTapGestures { offset ->
                val shown = frame ?: return@detectTapGestures
                val scale = min(size.width, size.height) / (2f * extent * FIT_MARGIN)
                val x = shown.centerX + (offset.x - size.width / 2f) / scale
                val y = shown.centerY - (offset.y - size.height / 2f) / scale
                val i = shown.cellAt(x, y, slack = TAP_SLACK_PX / scale)
                if (i >= 0) onTapMode(shown.mode(i))
            }
        },
    )

/** M1–M40 as coloured circles; faded ones cannot be reached from the initial mode. */
@Composable
private fun ModeStrip(genome: Genome, edited: Int, onSelect: (Int) -> Unit) {
    val reachable = remember(genome) { genome.reachableModes(genome.initialMode) }
    val state = rememberLazyListState()
    LaunchedEffect(edited) { state.animateScrollToItem((edited - 3).coerceAtLeast(0)) }
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
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
    val text = if (color.luminance() > 0.45f) Color.Black else Color.White
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (reachable) 1f else 0.3f))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.15f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            text = "${index + 1}",
            color = text.copy(alpha = if (reachable) 1f else 0.6f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (initial) FontWeight.Bold else FontWeight.Normal,
        )
        if (initial) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(text),
            )
        }
    }
}

fun modeColor(mode: Mode): Color = Color(mode.red.coerceIn(0f, 1f), mode.green.coerceIn(0f, 1f), mode.blue.coerceIn(0f, 1f))

/** Every parameter of one mode (SPEC «Общие параметры режима» and the parameters of its type). */
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

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.editor_mode_title, index + 1), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        FilterChip(
            selected = genome.initialMode == index,
            onClick = onMakeInitial,
            label = { Text(stringResource(R.string.editor_initial)) },
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

    SectionTitle(stringResource(R.string.section_division))
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

    SectionTitle(stringResource(R.string.section_adhesin))
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

    SectionTitle(stringResource(R.string.section_nutrition))
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

    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(stringResource(R.string.section_cell), Modifier.weight(1f))
        Box(
            Modifier
                .padding(top = 12.dp)
                .size(width = 40.dp, height = 20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(modeColor(mode)),
        )
    }
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

    TypeParameters(genome, mode, typeNames[mode.cellType.ordinal], snapping, onChange)
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
    SectionTitle(title)
    ModeChoiceRow(stringResource(R.string.param_child_mode), genome, childMode, onMode, help[HELP_CHILD_MODE])
    SliderRow(stringResource(R.string.param_child_angle), angle, Mode.ANGLE, degrees(angle), onAngle, levels, help[HELP_CHILD_ANGLE])
    SwitchRow(stringResource(R.string.param_keep_bonds), keepBonds, onKeepBonds, help[HELP_KEEP_BONDS])
    SwitchRow(stringResource(R.string.param_mirror), mirror, onMirror, help[HELP_MIRROR])
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
        optionIcon = { index ->
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(modeColor(genome.modes[index])),
            )
        },
    )
}

/** Parameters of the mode's cell type (SPEC «Типы клеток»). */
@Composable
private fun TypeParameters(genome: Genome, mode: Mode, typeName: String, snapping: Boolean, onChange: ((Mode) -> Mode) -> Unit) {
    val levels = if (snapping) SNAP_LEVELS else 0
    val help = stringArrayResource(R.array.help_type)
    val channels = SignalChannel.entries
    val title: @Composable () -> Unit = { SectionTitle(typeName) }
    when (mode.cellType) {
        CellType.FLAGELLOCYTE -> {
            title()
            ProgrammableEditor(stringResource(R.string.param_swim_force), mode.swimForce, 0f..1f, snapping, { v -> onChange { it.copy(swimForce = v) } }, help[HELP_SWIM])
        }
        CellType.BUOYOCYTE -> {
            title()
            ProgrammableEditor(stringResource(R.string.param_density), mode.density, 0f..1f, snapping, { v -> onChange { it.copy(density = v) } }, help[HELP_DENSITY])
        }
        CellType.MYOCYTE -> {
            title()
            ProgrammableEditor(stringResource(R.string.param_contraction), mode.contraction, -1f..1f, snapping, { v -> onChange { it.copy(contraction = v) } }, help[HELP_CONTRACTION])
            ProgrammableEditor(stringResource(R.string.param_bend), mode.bend, -1f..1f, snapping, { v -> onChange { it.copy(bend = v) } }, help[HELP_BEND])
            ProgrammableEditor(stringResource(R.string.param_lift), mode.lift, 0f..1f, snapping, { v -> onChange { it.copy(lift = v) } }, help[HELP_LIFT])
        }
        CellType.NEUROCYTE -> {
            title()
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
                )
            }
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
            title()
            ModeChoiceRow(stringResource(R.string.param_path_mode, 1), genome, mode.path1Mode, { v -> onChange { it.copy(path1Mode = v) } }, help[HELP_PATH])
            ProgrammableEditor(stringResource(R.string.param_path_signal, 1), mode.path1Signal, -1f..1f, snapping, { v -> onChange { it.copy(path1Signal = v) } }, help[HELP_PATH])
            ModeChoiceRow(stringResource(R.string.param_path_mode, 2), genome, mode.path2Mode, { v -> onChange { it.copy(path2Mode = v) } }, help[HELP_PATH])
            ProgrammableEditor(stringResource(R.string.param_path_signal, 2), mode.path2Signal, -1f..1f, snapping, { v -> onChange { it.copy(path2Signal = v) } }, help[HELP_PATH])
        }
        CellType.SENSEOCYTE, CellType.STEREOCYTE -> {
            title()
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
            title()
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
            title()
            ModeChoiceRow(stringResource(R.string.param_virus_mode), genome, mode.virusMode, { v -> onChange { it.copy(virusMode = v) } }, help[HELP_VIRUS])
        }
        CellType.GAMETE -> {
            title()
            ModeChoiceRow(stringResource(R.string.param_fusion_mode), genome, mode.fusionMode, { v -> onChange { it.copy(fusionMode = v) } }, help[HELP_GAMETE])
        }
        CellType.CILIOCYTE -> {
            title()
            ProgrammableEditor(stringResource(R.string.param_left_cilia), mode.leftCilia, -1f..1f, snapping, { v -> onChange { it.copy(leftCilia = v) } }, help[HELP_CILIA])
            ProgrammableEditor(stringResource(R.string.param_right_cilia), mode.rightCilia, -1f..1f, snapping, { v -> onChange { it.copy(rightCilia = v) } }, help[HELP_CILIA])
        }
        else -> Unit
    }
}

/**
 * A programmable value (SPEC «Программируемые значения»): a constant, or a linear or step function of one input,
 * with a small graph of the function.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgrammableEditor(
    label: String,
    value: ProgrammableValue,
    constantRange: ClosedFloatingPointRange<Float>,
    snapping: Boolean,
    onChange: (ProgrammableValue) -> Unit,
    help: String? = null,
) {
    val levels = if (snapping) SNAP_LEVELS else 0
    val inputs = stringArrayResource(R.array.pv_inputs)
    Column(Modifier.fillMaxWidth()) {
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
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ValueShape.entries.forEachIndexed { index, shape ->
                    SegmentedButton(
                        selected = value.shape == shape,
                        onClick = { onChange(value.copy(shape = shape).sanitized()) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ValueShape.entries.size),
                        icon = {},
                        label = {
                            Text(stringResource(if (shape == ValueShape.LINEAR) R.string.pv_shape_linear else R.string.pv_shape_step))
                        },
                    )
                }
            }
            val step = value.shape == ValueShape.STEP
            val ab = if (step) ProgrammableValue.STEP_AB else ProgrammableValue.LINEAR_AB
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = stringResource(if (step) R.string.pv_formula_step else R.string.pv_formula_linear),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FunctionGraph(value, Modifier.size(width = 96.dp, height = 48.dp))
            }
            SliderRow("a", value.a, ab, decimal(value.a), { v -> onChange(value.copy(a = v)) }, levels)
            SliderRow("b", value.b, ab, decimal(value.b), { v -> onChange(value.copy(b = v)) }, levels)
            if (step) SliderRow("c", value.c, ProgrammableValue.STEP_C, decimal(value.c), { v -> onChange(value.copy(c = v)) }, levels)
        }
    }
}

/** The function of a programmable value for inputs −1…1, outputs clipped to −1…1. */
@Composable
private fun FunctionGraph(value: ProgrammableValue, modifier: Modifier = Modifier) {
    val axis = MaterialTheme.colorScheme.outlineVariant
    val line = MaterialTheme.colorScheme.primary
    Canvas(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        fun px(input: Float) = (input + 1f) / 2f * size.width
        fun py(output: Float) = (1f - (output.coerceIn(-1f, 1f) + 1f) / 2f) * size.height
        drawLine(axis, Offset(0f, py(0f)), Offset(size.width, py(0f)))
        drawLine(axis, Offset(px(0f), 0f), Offset(px(0f), size.height))
        val path = Path()
        for (k in 0..GRAPH_SAMPLES) {
            val input = -1f + 2f * k / GRAPH_SAMPLES
            val point = Offset(px(input), py(value.evaluate(input)))
            if (k == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
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
