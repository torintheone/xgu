package io.github.torintheone.petri.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.torintheone.petri.R
import io.github.torintheone.petri.data.Storage
import io.github.torintheone.petri.ui.theme.Lab
import io.github.torintheone.petri.ui.theme.LabType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** Number of positions of a snapped slider (SPEC «Привязка значений»: 25 levels; angles then go in 15° steps). */
const val SNAP_LEVELS = 25

/**
 * A parameter name. When [help] is given, a small «i» follows the name and a tap on either shows the help
 * (SPEC: «Тап по названию параметра — короткая справка»).
 */
@Composable
fun ParameterName(text: String, help: String?, modifier: Modifier = Modifier) {
    var showHelp by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (help != null) {
            modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(onClickLabel = stringResource(R.string.help_open)) { showHelp = true }
        } else {
            modifier
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Lab.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (help != null) {
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = Lab.TextFaint, modifier = Modifier.size(15.dp))
        }
    }
    if (showHelp && help != null) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.dialog_ok)) } },
            title = { Text(text) },
            text = { Text(help) },
            containerColor = Lab.Raised,
            textContentColor = Lab.TextDim,
        )
    }
}

/** A value in a small raised box, at the end of a parameter row. */
@Composable
fun ValueChip(text: String, modifier: Modifier = Modifier, active: Boolean = false) {
    Text(
        text = text,
        style = LabType.ReadingSmall,
        color = if (active) Lab.Accent else Lab.Text,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Lab.Raised)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * A slider with its name and value. [levels] > 1 makes it jump between that many evenly spaced positions;
 * 0 lets it move freely. [note] is a line of small text under the slider.
 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
    levels: Int = 0,
    help: String? = null,
    enabled: Boolean = true,
    note: String? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ParameterName(label, help, Modifier.weight(1f))
            ValueChip(valueText, Modifier.padding(start = 12.dp))
        }
        LabSlider(value = value, onChange = onChange, range = range, levels = levels, enabled = enabled)
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = Lab.TextDim,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/**
 * The slider of the instrument. It moves only by a sideways drag that starts anywhere on its track, and by as
 * much as the finger moves, so scrolling the list past it never changes a value. «−» and «+» move it one step
 * (one level, or 1/100 of the range); holding them repeats.
 */
@Composable
fun LabSlider(
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    levels: Int = 0,
    enabled: Boolean = true,
    steppers: Boolean = true,
) {
    val span = (range.endInclusive - range.start).coerceAtLeast(Float.MIN_VALUE)
    val current by rememberUpdatedState(value.coerceIn(range.start, range.endInclusive))
    val report by rememberUpdatedState(onChange)
    var dragging by remember { mutableStateOf(false) }
    val step = if (levels > 1) span / (levels - 1) else span / FINE_STEPS
    val snap: (Float) -> Float = { raw ->
        val clamped = raw.coerceIn(range.start, range.endInclusive)
        if (levels > 1) (range.start + ((clamped - range.start) / step).roundToInt() * step).coerceIn(range.start, range.endInclusive) else clamped
    }
    val accent = if (enabled) Lab.Accent else Lab.TextFaint

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (steppers) StepButton(R.drawable.ic_minus, R.string.step_down, enabled) { report(snap(current - step)) }
        Box(
            Modifier
                .weight(1f)
                .height(SLIDER_HEIGHT)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(current, range, if (levels > 2) levels - 2 else 0)
                    if (enabled) {
                        setProgress { target ->
                            report(snap(target))
                            true
                        }
                    }
                }
                .pointerInput(range, levels, enabled) {
                    if (!enabled) return@pointerInput
                    var raw = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            raw = current
                            dragging = true
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, amount ->
                        change.consume()
                        val track = (size.width - 2 * THUMB_INSET.toPx()).coerceAtLeast(1f)
                        raw = (raw + amount / track * span).coerceIn(range.start, range.endInclusive)
                        val next = snap(raw)
                        if (next != current) report(next)
                    }
                }
                .drawBehind {
                    val inset = THUMB_INSET.toPx()
                    val left = inset
                    val right = size.width - inset
                    val y = size.height / 2f - TICK_ROOM.toPx() / 2f
                    val x = left + (current - range.start) / span * (right - left)

                    // A ruler under the track: every level when there are few, tenths otherwise.
                    val marks = if (levels in 2..MAX_TICKS) levels else RULER_MARKS
                    val last = marks - 1
                    for (k in 0..last) {
                        val tx = left + (right - left) * k / last
                        val major = k == 0 || k == last || (last % 4 == 0 && k % (last / 4) == 0) || (last % 2 == 0 && k == last / 2)
                        val top = y + TICK_GAP.toPx()
                        val length = (if (major) MAJOR_TICK else MINOR_TICK).toPx()
                        drawLine(
                            color = if (major) Lab.HairlineStrong else Lab.Hairline,
                            start = Offset(tx, top),
                            end = Offset(tx, top + length),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    drawLine(Lab.HairlineStrong, Offset(left, y), Offset(right, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    // A signed range fills from zero, the others from the left end.
                    val origin = if (range.start < 0f && range.endInclusive > 0f) left + (0f - range.start) / span * (right - left) else left
                    drawLine(accent, Offset(origin, y), Offset(x, y), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    if (dragging) drawCircle(accent.copy(alpha = GLOW_ALPHA), radius = GLOW_RADIUS.toPx(), center = Offset(x, y))
                    drawCircle(Lab.Background, radius = THUMB_RADIUS.toPx() + 2.dp.toPx(), center = Offset(x, y))
                    drawCircle(accent, radius = THUMB_RADIUS.toPx(), center = Offset(x, y))
                },
        )
        if (steppers) StepButton(R.drawable.ic_plus, R.string.step_up, enabled) { report(snap(current + step)) }
    }
}

/** «−» or «+» next to a slider: a tap is one step, holding repeats; a scroll that starts on it does nothing. */
@Composable
private fun StepButton(@DrawableRes icon: Int, @StringRes description: Int, enabled: Boolean, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    val label = stringResource(description)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(STEP_BUTTON)
            .clip(CircleShape)
            .semantics {
                contentDescription = label
                role = Role.Button
                if (enabled) {
                    onClick {
                        step()
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    var repeated = false
                    val repeat = scope.launch {
                        delay(REPEAT_START_MS)
                        repeated = true
                        while (true) {
                            step()
                            delay(REPEAT_EVERY_MS)
                        }
                    }
                    val up = waitForUpOrCancellation()
                    repeat.cancel()
                    if (up != null && !repeated) {
                        up.consume()
                        step()
                    }
                }
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .border(1.dp, if (enabled) Lab.HairlineStrong else Lab.Hairline, CircleShape),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (enabled) Lab.TextDim else Lab.TextFaint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** An on/off parameter; the whole row switches it. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, help: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        ParameterName(label, help, Modifier.weight(1f))
        LabSwitch(checked, Modifier.padding(start = 12.dp))
    }
}

/** A parameter with a few named choices: the current one sits in a field that opens the list. */
@Composable
fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionText: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    help: String? = null,
    optionIcon: (@Composable (T) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        ParameterName(label, help, Modifier.weight(1f))
        Box(Modifier.padding(start = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(Lab.Raised)
                    .border(1.dp, Lab.HairlineStrong, MaterialTheme.shapes.small)
                    .clickable { open = true }
                    .padding(start = 10.dp, end = 6.dp),
            ) {
                optionIcon?.invoke(selected)
                Text(
                    text = optionText(selected),
                    style = MaterialTheme.typography.labelLarge,
                    color = Lab.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = CHOICE_MAX_WIDTH),
                )
                Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = Lab.TextDim, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (option in options) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionText(option),
                                color = if (option == selected) Lab.Accent else Lab.Text,
                            )
                        },
                        onClick = {
                            open = false
                            onSelect(option)
                        },
                        leadingIcon = if (optionIcon != null) {
                            { optionIcon(option) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

/** Asks for a name; [onConfirm] gets the trimmed text. */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    note: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial.take(Storage.MAX_NAME_LENGTH)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(Storage.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.dialog_name)) },
                    singleLine = true,
                )
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = Lab.TextDim)
                }
            }
        },
        containerColor = Lab.Raised,
    )
}

/** A yes/no question before something that cannot be undone; the «yes» is red. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirm, color = Lab.Danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        title = { Text(title) },
        text = { Text(text) },
        containerColor = Lab.Raised,
        textContentColor = Lab.TextDim,
    )
}

/** A number with [decimals] digits after the phone's decimal separator. */
fun formatNumber(value: Double, decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", value)

/** «0,50» with the phone's decimal separator. */
fun decimal(value: Float, digits: Int = 2): String = formatNumber(value.toDouble(), digits)

fun degrees(value: Float): String = "${value.roundToInt()}°"

/** A free slider moves 1/100 of its range per «−» or «+». */
private const val FINE_STEPS = 100f

/** Above this many levels the ruler under a slider shows tenths instead of every level. */
private const val MAX_TICKS = 25
private const val RULER_MARKS = 11

private const val REPEAT_START_MS = 400L
private const val REPEAT_EVERY_MS = 70L

private val SLIDER_HEIGHT = 40.dp
private val STEP_BUTTON = 40.dp
private val THUMB_RADIUS = 7.dp
private val THUMB_INSET = 12.dp
private val GLOW_RADIUS = 15.dp
private const val GLOW_ALPHA = 0.22f
private val TICK_ROOM = 8.dp
private val TICK_GAP = 7.dp
private val MAJOR_TICK = 5.dp
private val MINOR_TICK = 3.dp
private val CHOICE_MAX_WIDTH = 180.dp
