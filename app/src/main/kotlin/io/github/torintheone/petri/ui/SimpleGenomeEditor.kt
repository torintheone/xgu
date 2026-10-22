package io.github.torintheone.petri.ui

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.torintheone.petri.R
import io.github.torintheone.petri.sim.CellType
import io.github.torintheone.petri.sim.Genome
import io.github.torintheone.petri.sim.Mode
import io.github.torintheone.petri.sim.SignalInput
import io.github.torintheone.petri.ui.theme.Lab
import io.github.torintheone.petri.ui.theme.LabType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The cells the organism is made of (the modes it uses), drawn as they look, with their numbers. The chosen one is
 * ringed and a star marks the one the organism grows from. A chosen mode the organism does not use is shown faded.
 */
@Composable
fun OrganismStrip(genome: Genome, edited: Int, onSelect: (Int) -> Unit) {
    val used = remember(genome) { genome.usedModes() }
    val shown = remember(used, edited) { (used.toList() + edited).distinct() }
    val state = rememberLazyListState()
    LaunchedEffect(edited, shown) {
        val at = shown.indexOf(edited)
        if (at >= 0) state.animateScrollToItem((at - 3).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxWidth()) {
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            items(shown, key = { it }) { index ->
                KindChip(
                    index = index,
                    mode = genome.modes[index],
                    chosen = index == edited,
                    initial = index == genome.initialMode,
                    faded = index !in used,
                    onClick = { onSelect(index) },
                )
            }
        }
        if (used.size == 1) {
            Text(
                text = stringResource(R.string.simple_kinds_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Lab.TextDim,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

/**
 * The simple editor of one mode: what the cell is (type and colour), when and how it divides, drawn before and
 * after the division, and what its two children become. Fine settings stay in the full editor.
 */
@Composable
fun SimpleModeEditor(
    genome: Genome,
    index: Int,
    onChange: ((Mode) -> Mode) -> Unit,
    onNewKind: (child: Int) -> Unit,
    onOpenFull: () -> Unit,
    allowedTypes: Set<CellType> = ALL_TYPES,
) {
    val mode = genome.modes[index]
    var child by rememberSaveable { mutableIntStateOf(1) }
    IdentityPanel(genome, index, mode, allowedTypes, onChange)
    DivisionPanel(genome, index, mode, child, { child = it }, onChange, onNewKind)
    TypeSettings(mode, onChange, onOpenFull)
}

/** Number, type and colour of the cell; a tap on the type opens the list of types. */
@Composable
private fun IdentityPanel(genome: Genome, index: Int, mode: Mode, allowedTypes: Set<CellType>, onChange: ((Mode) -> Mode) -> Unit) {
    val typeNames = stringArrayResource(R.array.cell_types)
    val descriptions = stringArrayResource(R.array.type_descriptions)
    var picking by remember { mutableStateOf(false) }
    Panel(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = stringResource(R.string.simple_change_type)) { picking = true },
        ) {
            CellIcon(mode.cellType, modeColor(mode), Modifier.size(56.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val title = if (genome.initialMode == index) R.string.simple_cell_initial else R.string.simple_cell_title
                Eyebrow(stringResource(title, index + 1))
                Text(typeNames[mode.cellType.ordinal], style = MaterialTheme.typography.titleMedium, color = Lab.Text)
                Text(descriptions[mode.cellType.ordinal], style = MaterialTheme.typography.bodySmall, color = Lab.TextDim)
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = Lab.TextDim,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp),
            )
        }
        Hairline(Modifier.padding(vertical = 12.dp))
        Eyebrow(stringResource(R.string.simple_color))
        ColorPalette(mode, { color -> onChange { it.copy(red = color.red, green = color.green, blue = color.blue) } }, Modifier.padding(top = 10.dp))
    }
    if (picking) {
        TypePicker(
            current = mode.cellType,
            color = modeColor(mode),
            allowed = allowedTypes,
            onPick = { type ->
                picking = false
                onChange { it.copy(cellType = type) }
            },
            onDismiss = { picking = false },
        )
    }
}

/** Ten ready colours; the one the cell has is ringed. */
@Composable
private fun ColorPalette(mode: Mode, onPick: (PaletteColor) -> Unit, modifier: Modifier = Modifier) {
    val names = stringArrayResource(R.array.palette_names)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 6.dp
        val swatch = minOf(SWATCH_MAX, (maxWidth - gap * (PALETTE.size - 1)) / PALETTE.size)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            PALETTE.forEachIndexed { k, color ->
                val chosen = color.matches(mode)
                Box(
                    Modifier
                        .size(swatch)
                        .border(2.dp, if (chosen) Lab.Accent else Color.Transparent, CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(color.color)
                        .semantics { contentDescription = names[k] }
                        .selectable(selected = chosen, role = Role.RadioButton) { onPick(color) },
                )
            }
        }
    }
}

/** Every cell type the level allows, drawn in the cell's colour, with a line about what it does. */
@Composable
private fun TypePicker(current: CellType, color: Color, allowed: Set<CellType>, onPick: (CellType) -> Unit, onDismiss: () -> Unit) {
    val names = stringArrayResource(R.array.cell_types)
    val descriptions = stringArrayResource(R.array.type_descriptions)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        title = { Text(stringResource(R.string.param_cell_type)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (type in CellType.entries) {
                    if (type !in allowed) continue
                    val chosen = type == current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(if (chosen) Lab.AccentSoft else Color.Transparent)
                            .selectable(selected = chosen, role = Role.RadioButton) { onPick(type) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        CellIcon(type, color, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(names[type.ordinal], style = MaterialTheme.typography.titleSmall, color = if (chosen) Lab.Accent else Lab.Text)
                            Text(descriptions[type.ordinal], style = MaterialTheme.typography.bodySmall, color = Lab.TextDim)
                        }
                    }
                }
            }
        },
        containerColor = Lab.Raised,
    )
}

/** When the cell divides, the picture of the division, which way it goes and what the children become. */
@Composable
private fun DivisionPanel(
    genome: Genome,
    index: Int,
    mode: Mode,
    child: Int,
    onChild: (Int) -> Unit,
    onChange: ((Mode) -> Mode) -> Unit,
    onNewKind: (Int) -> Unit,
) {
    val help = stringArrayResource(R.array.help_mode)
    SectionPanel(stringResource(R.string.section_division)) {
        if (mode.cellType == CellType.GAMETE) {
            Note(stringResource(R.string.simple_gamete_note))
        } else {
            SwitchRow(
                label = stringResource(R.string.simple_divides),
                checked = !mode.neverSplits,
                onChange = { on -> onChange { it.copy(splitMass = if (on) SplitTiming.NORMAL.mass else Mode.SPLIT_NEVER) } },
            )
            if (mode.neverSplits) {
                Note(stringResource(R.string.simple_never_note))
            } else {
                Segmented<SplitTiming?>(
                    options = SplitTiming.entries,
                    selected = SplitTiming.of(mode.splitMass),
                    label = { stringResource(requireNotNull(it).title) },
                    onSelect = { timing -> if (timing != null) onChange { it.copy(splitMass = timing.mass) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Note(splitNote(mode.splitMass))
                DivisionPicture(
                    genome = genome,
                    mode = mode,
                    child = child,
                    onDirection = { degrees -> onChange { it.copy(splitAngle = degrees) } },
                    onChild = onChild,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(PICTURE_HEIGHT),
                )
                Note(stringResource(R.string.simple_picture_hint))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    StepButton(R.drawable.ic_turn_left, R.string.simple_turn_split_left, enabled = true) {
                        onChange { it.copy(splitAngle = it.splitAngle + SPLIT_TURN) }
                    }
                    Text(
                        text = stringResource(R.string.simple_children_sides, sideName(mode.splitAngle - QUARTER), sideName(mode.splitAngle + QUARTER)),
                        style = LabType.ReadingSmall,
                        color = Lab.Text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    StepButton(R.drawable.ic_turn_right, R.string.simple_turn_split_right, enabled = true) {
                        onChange { it.copy(splitAngle = it.splitAngle - SPLIT_TURN) }
                    }
                }
                SwitchRow(stringResource(R.string.simple_glued), mode.makeAdhesin, { v -> onChange { it.copy(makeAdhesin = v) } }, help[HELP_ADHESIN])
                Hairline(Modifier.padding(vertical = 8.dp))
                Segmented(
                    options = listOf(1, 2),
                    selected = child,
                    label = { stringResource(R.string.simple_child, it) },
                    onSelect = onChild,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
                ChildControls(genome, index, mode, child, onChange, onNewKind)
            }
        }
    }
}

/** What child [child] becomes, how it turns and whether it breaks away. */
@Composable
private fun ChildControls(genome: Genome, index: Int, mode: Mode, child: Int, onChange: ((Mode) -> Mode) -> Unit, onNewKind: (Int) -> Unit) {
    val help = stringArrayResource(R.array.help_mode)
    val first = child == 1
    val childMode = if (first) mode.child1Mode else mode.child2Mode
    val angle = if (first) mode.child1Angle else mode.child2Angle
    val mirror = if (first) mode.child1Mirror else mode.child2Mirror
    val keepBonds = if (first) mode.child1KeepBonds else mode.child2KeepBonds
    val used = remember(genome) { genome.usedModes() }
    val kinds = remember(used, index, childMode) { (used.toList() + index + childMode).distinct() }

    ParameterName(stringResource(R.string.simple_becomes), help[HELP_CHILD_MODE], Modifier.padding(top = 12.dp))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(kinds, key = { it }) { kind ->
            KindChip(
                index = kind,
                mode = genome.modes[kind],
                chosen = kind == childMode,
                initial = false,
                onClick = { onChange { if (first) it.copy(child1Mode = kind) else it.copy(child2Mode = kind) } },
            )
        }
        item(key = "new") { NewKindChip(onClick = { onNewKind(child) }) }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        ParameterName(stringResource(R.string.simple_turn), help[HELP_CHILD_ANGLE], Modifier.weight(1f))
        StepButton(R.drawable.ic_turn_left, R.string.simple_turn_child_left, enabled = true) {
            onChange { turnChild(it, first, CHILD_TURN) }
        }
        Text(
            text = turnText(signedDegrees(if (mirror) -angle else angle)),
            style = LabType.ReadingSmall,
            color = Lab.Text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .width(TURN_LABEL)
                .clip(RoundedCornerShape(6.dp))
                .background(Lab.Raised)
                .padding(vertical = 3.dp),
        )
        StepButton(R.drawable.ic_turn_right, R.string.simple_turn_child_right, enabled = true) {
            onChange { turnChild(it, first, -CHILD_TURN) }
        }
    }
    SwitchRow(
        label = stringResource(R.string.simple_breaks_away),
        checked = !keepBonds,
        onChange = { away -> onChange { if (first) it.copy(child1KeepBonds = !away) else it.copy(child2KeepBonds = !away) } },
        help = stringResource(R.string.simple_breaks_away_help),
    )
}

/** The child turns [by] degrees to the left (negative: to the right); a mirrored child counts its angle clockwise. */
private fun turnChild(mode: Mode, first: Boolean, by: Float): Mode = if (first) {
    mode.copy(child1Angle = mode.child1Angle + if (mode.child1Mirror) -by else by)
} else {
    mode.copy(child2Angle = mode.child2Angle + if (mode.child2Mirror) -by else by)
}

/** Settings of the cell's type that are simple to show; for the others, the way to the full editor. */
@Composable
private fun TypeSettings(mode: Mode, onChange: ((Mode) -> Mode) -> Unit, onOpenFull: () -> Unit) {
    val typeName = stringArrayResource(R.array.cell_types)[mode.cellType.ordinal]
    when (mode.cellType) {
        CellType.FLAGELLOCYTE -> SectionPanel(typeName) {
            if (mode.swimForce.input == SignalInput.NONE) {
                SliderRow(
                    label = stringResource(R.string.simple_swim_force),
                    value = mode.swimForce.b,
                    range = 0f..1f,
                    valueText = decimal(mode.swimForce.b),
                    onChange = { v -> onChange { it.copy(swimForce = it.swimForce.copy(b = v)) } },
                    levels = TYPE_LEVELS,
                )
            } else {
                FullEditorNote(R.string.simple_signal_driven, onOpenFull)
            }
        }
        CellType.BUOYOCYTE -> SectionPanel(typeName) {
            if (mode.density.input == SignalInput.NONE) {
                SliderRow(
                    label = stringResource(R.string.simple_density),
                    value = mode.density.b,
                    range = 0f..1f,
                    valueText = decimal(mode.density.b),
                    onChange = { v -> onChange { it.copy(density = it.density.copy(b = v)) } },
                    levels = TYPE_LEVELS,
                    help = stringResource(R.string.simple_density_help),
                )
            } else {
                FullEditorNote(R.string.simple_signal_driven, onOpenFull)
            }
        }
        in ADVANCED_TYPES -> SectionPanel(typeName) { FullEditorNote(R.string.simple_advanced_type, onOpenFull) }
        else -> Unit
    }
}

@Composable
private fun FullEditorNote(@StringRes text: Int, onOpenFull: () -> Unit) {
    Note(stringResource(text))
    LabButton(
        text = stringResource(R.string.simple_open_full),
        onClick = onOpenFull,
        icon = R.drawable.ic_dna,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

/** A line of small dim text inside a panel. */
@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Lab.TextDim,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

/**
 * The division drawn twice: the mother before it, cut along a dashed line into side 1 and side 2, and the two
 * children after it, each in the colour and type of the mode it becomes, glued if they are. The front of every
 * cell looks up the picture at first, and small arrows show where each cell's front looks. A tap on the mother
 * sends child 1 to that side; a tap on a child chooses it for the settings below.
 */
@Composable
private fun DivisionPicture(
    genome: Genome,
    mode: Mode,
    child: Int,
    onDirection: (Float) -> Unit,
    onChild: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val currentMode by rememberUpdatedState(mode)
    val direction by rememberUpdatedState(onDirection)
    val choose by rememberUpdatedState(onChild)
    val first = genome.modes[mode.child1Mode]
    val second = genome.modes[mode.child2Mode]
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PicturePane(
            title = stringResource(R.string.simple_before),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val dx = tap.x - size.width / 2f
                            val dy = size.height / 2f - tap.y
                            if (hypot(dx, dy) < min(size.width, size.height) * MOTHER_SHARE * DEAD_CENTER) return@detectTapGestures
                            val degrees = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                            direction((degrees / SPLIT_TURN).roundToInt() * SPLIT_TURN)
                        }
                    },
            ) {
                val shape = shapeOf(size.width, size.height, mode)
                drawPaneGrid()
                drawCell(shape.center, shape.radius, HALF_PI, modeColor(mode), mode.cellType)
                drawFrontMark(shape.center, shape.radius, HALF_PI, FrontColor)
                val along = Offset(-shape.normal.y, shape.normal.x) * (shape.radius * SPLIT_LINE_REACH)
                drawLine(
                    color = SplitLineColor,
                    start = shape.center - along,
                    end = shape.center + along,
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
                drawBadge(measurer, "1", shape.center + shape.normal * (shape.radius * SIDE_LABEL), child == 1)
                drawBadge(measurer, "2", shape.center - shape.normal * (shape.radius * SIDE_LABEL), child == 2)
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription = null,
            tint = Lab.TextDim,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(20.dp)
                .rotate(-90f),
        )
        PicturePane(
            title = stringResource(R.string.simple_after),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val shape = shapeOf(size.width.toFloat(), size.height.toFloat(), currentMode)
                            val to1 = (tap - shape.child1).getDistance() - shape.radius1
                            val to2 = (tap - shape.child2).getDistance() - shape.radius2
                            choose(if (to1 <= to2) 1 else 2)
                        }
                    },
            ) {
                val shape = shapeOf(size.width, size.height, mode)
                drawPaneGrid()
                if (mode.makeAdhesin) {
                    drawLine(BondColor, shape.child1, shape.child2, strokeWidth = max(2f, 0.2f * min(shape.radius1, shape.radius2)), cap = StrokeCap.Round)
                }
                val angle1 = HALF_PI + (if (mode.child1Mirror) -1f else 1f) * mode.child1Angle * DEG
                val angle2 = HALF_PI + (if (mode.child2Mirror) -1f else 1f) * mode.child2Angle * DEG
                drawCell(shape.child1, shape.radius1, angle1, modeColor(first), first.cellType)
                drawFrontMark(shape.child1, shape.radius1, angle1, FrontColor)
                drawCell(shape.child2, shape.radius2, angle2, modeColor(second), second.cellType)
                drawFrontMark(shape.child2, shape.radius2, angle2, FrontColor)
                val (ringCenter, ringRadius) = if (child == 1) shape.child1 to shape.radius1 else shape.child2 to shape.radius2
                drawCircle(Lab.Accent, radius = ringRadius + 4.dp.toPx(), center = ringCenter, style = Stroke(width = 1.5.dp.toPx()))
                drawBadge(measurer, "1", shape.child1, child == 1)
                drawBadge(measurer, "2", shape.child2, child == 2)
            }
        }
    }
}

@Composable
private fun PicturePane(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .clip(shape)
            .background(MediumColor)
            .border(1.dp, Lab.Hairline, shape),
    ) {
        content()
        Eyebrow(
            title,
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        )
    }
}

/** Where the mother and her children sit in a pane, in pixels; [normal] points from side 2 to side 1. */
private class DivisionShape(
    val center: Offset,
    val radius: Float,
    val normal: Offset,
    val child1: Offset,
    val radius1: Float,
    val child2: Offset,
    val radius2: Float,
)

/** The mother in the middle with her front up; the children touch, as the division places them in the dish. */
private fun shapeOf(width: Float, height: Float, mode: Mode): DivisionShape {
    val center = Offset(width / 2f, height / 2f)
    val radius = min(width, height) * MOTHER_SHARE
    val ratio = mode.splitRatio
    // Radius grows with the square root of the mass. With the front up, child 1 goes the way of «Угол деления».
    val radius1 = radius * sqrt(ratio)
    val radius2 = radius * sqrt(1f - ratio)
    val angle = mode.splitAngle * DEG
    val normal = Offset(cos(angle), -sin(angle))
    val reach = radius1 + radius2
    return DivisionShape(
        center = center,
        radius = radius,
        normal = normal,
        child1 = center + normal * (reach * (1f - ratio)),
        radius1 = radius1,
        child2 = center - normal * (reach * ratio),
        radius2 = radius2,
    )
}

private fun DrawScope.drawPaneGrid() {
    val step = size.minDimension / PANE_GRID
    var x = size.width / 2f % step
    while (x < size.width) {
        drawLine(PaneGridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = size.height / 2f % step
    while (y < size.height) {
        drawLine(PaneGridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

/** A child's number in a small dark disc, bright for the chosen child. */
private fun DrawScope.drawBadge(measurer: TextMeasurer, text: String, at: Offset, chosen: Boolean) {
    val radius = 9.dp.toPx()
    drawCircle(BadgeFill, radius = radius, center = at)
    drawCircle(if (chosen) Lab.Accent else Lab.HairlineStrong, radius = radius, center = at, style = Stroke(width = 1.dp.toPx()))
    val layout = measurer.measure(text, style = if (chosen) BadgeChosenText else BadgeText)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

/** A kind of cell: the cell drawn in its colour with its number; [chosen] rings it, [initial] adds a star. */
@Composable
private fun KindChip(index: Int, mode: Mode, chosen: Boolean, initial: Boolean, onClick: () -> Unit, faded: Boolean = false) {
    val typeName = stringArrayResource(R.array.cell_types)[mode.cellType.ordinal]
    val description = stringResource(R.string.simple_kind, index + 1, typeName)
    Box(Modifier.size(KIND_CHIP)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (chosen) Lab.AccentSoft else Lab.Raised)
                .border(if (chosen) 2.dp else 1.dp, if (chosen) Lab.Accent else Lab.Hairline, CircleShape)
                .semantics { contentDescription = description }
                .selectable(selected = chosen, role = Role.RadioButton, onClick = onClick),
        ) {
            CellIcon(
                mode.cellType,
                modeColor(mode),
                Modifier
                    .fillMaxSize()
                    .alpha(if (faded) FADED_ALPHA else 1f),
            )
        }
        Text(
            text = "${index + 1}",
            style = LabType.Mono,
            color = Lab.Text,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .clip(RoundedCornerShape(4.dp))
                .background(BadgeFill)
                .padding(horizontal = 3.dp),
        )
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

/** «New kind of cell»: a dashed circle with a plus. */
@Composable
private fun NewKindChip(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(KIND_CHIP)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.simple_new_kind), onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = Lab.HairlineStrong,
                radius = size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_plus),
            contentDescription = stringResource(R.string.simple_new_kind),
            tint = Lab.Accent,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A cell of [type] in [color] with its front up, as the dish draws it. */
@Composable
fun CellIcon(type: CellType, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCell(center, size.minDimension * ICON_CELL_SHARE, HALF_PI, color, type)
    }
}

/** The simple choices of «Масса деления». */
private enum class SplitTiming(@param:StringRes val title: Int, val mass: Float) {
    AT_ONCE(R.string.simple_split_at_once, Mode.SPLIT_BY_AGE),
    EARLY(R.string.simple_split_early, 1.6f),
    NORMAL(R.string.simple_split_normal, 2.5f),
    LATE(R.string.simple_split_late, 3.3f),
    ;

    companion object {
        fun of(mass: Float): SplitTiming? = SplitTiming.entries.firstOrNull { abs(it.mass - mass) < MASS_MATCH }
    }
}

@Composable
private fun splitNote(mass: Float): String = if (mass <= Mode.SPLIT_BY_AGE) {
    stringResource(R.string.simple_split_at_once_note)
} else {
    stringResource(R.string.simple_split_mass_note, decimal(mass, 1))
}

/** Where a child sits relative to the mother's front, [degrees] counter-clockwise: «впереди», «слева»… */
@Composable
private fun sideName(degrees: Float): String {
    val names = stringArrayResource(R.array.simple_sides)
    val wrapped = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN
    val sector = wrapped / SPLIT_TURN
    val k = sector.roundToInt()
    return if (abs(sector - k) < 0.01f) names[k % names.size] else degrees(wrapped)
}

/** How a child turns: [turn] degrees in (−180°, 180°], to the left when positive. */
@Composable
private fun turnText(turn: Float): String {
    val t = turn.roundToInt()
    return when {
        t == 0 -> stringResource(R.string.simple_turn_none)
        abs(t) == 180 -> stringResource(R.string.simple_turn_around)
        t > 0 -> stringResource(R.string.simple_turn_left, t)
        else -> stringResource(R.string.simple_turn_right, -t)
    }
}

/** [value] degrees brought into (−180°, 180°]. */
private fun signedDegrees(value: Float): Float {
    var v = value % FULL_TURN
    if (v > HALF_TURN) v -= FULL_TURN
    if (v <= -HALF_TURN) v += FULL_TURN
    return v
}

/** A colour of the palette, 0–1 per channel. */
private class PaletteColor(val red: Float, val green: Float, val blue: Float) {
    val color = Color(red, green, blue)

    fun matches(mode: Mode): Boolean =
        abs(mode.red - red) < COLOR_MATCH && abs(mode.green - green) < COLOR_MATCH && abs(mode.blue - blue) < COLOR_MATCH
}

/** Order: see R.array.palette_names. */
private val PALETTE = listOf(
    PaletteColor(0.37f, 0.88f, 0.92f),
    PaletteColor(0.45f, 0.85f, 0.4f),
    PaletteColor(0.75f, 0.9f, 0.35f),
    PaletteColor(0.95f, 0.85f, 0.4f),
    PaletteColor(0.95f, 0.6f, 0.3f),
    PaletteColor(0.95f, 0.4f, 0.4f),
    PaletteColor(0.95f, 0.5f, 0.75f),
    PaletteColor(0.65f, 0.5f, 0.95f),
    PaletteColor(0.4f, 0.6f, 1f),
    PaletteColor(0.9f, 0.92f, 0.95f),
)

private val ALL_TYPES = CellType.entries.toSet()

/** Types whose settings (signals, channels, other modes) only the full editor shows. */
private val ADVANCED_TYPES = setOf(
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

private const val HALF_PI = 1.5707964f
private const val DEG = 0.017453292f
private const val FULL_TURN = 360f
private const val HALF_TURN = 180f
private const val QUARTER = 90f

/** «Повернуть деление» and a tap on the mother move child 1 by an eighth of a turn. */
private const val SPLIT_TURN = 45f

/** A child turns in steps of 15°, like snapped angles in the full editor. */
private const val CHILD_TURN = 15f

private const val MASS_MATCH = 0.05f
private const val COLOR_MATCH = 0.02f
private const val TYPE_LEVELS = 11

private val PICTURE_HEIGHT = 156.dp
private val KIND_CHIP = 44.dp
private val SWATCH_MAX = 36.dp
private val TURN_LABEL = 124.dp

/** The mother's radius as a share of the pane; the children then fit with room for their arrows. */
private const val MOTHER_SHARE = 0.22f

/** A tap this close to the mother's middle (as a share of her radius) says nothing about a side. */
private const val DEAD_CENTER = 0.2f

private const val SPLIT_LINE_REACH = 1.35f
private const val SIDE_LABEL = 0.55f
private const val ICON_CELL_SHARE = 0.26f
private const val PANE_GRID = 6
private const val FADED_ALPHA = 0.4f

private val FrontColor = Color(0xB3FFFFFF)
private val SplitLineColor = Color(0xD9FFFFFF)
private val PaneGridColor = Color(0x0FFFFFFF)
private val BadgeFill = Color(0xD905090C)
private val BadgeText = TextStyle(color = Color(0xFFE2EBEE), fontSize = LabType.ReadingSmall.fontSize, fontWeight = LabType.ReadingSmall.fontWeight)
private val BadgeChosenText = BadgeText.copy(color = Color(0xFF5FE1EA))

// Positions in R.array.help_mode, as in GenomeScreen.kt.
private const val HELP_ADHESIN = 4
private const val HELP_CHILD_MODE = 5
private const val HELP_CHILD_ANGLE = 6
