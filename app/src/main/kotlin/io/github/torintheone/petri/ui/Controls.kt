package io.github.torintheone.petri.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.torintheone.petri.R
import io.github.torintheone.petri.data.Storage
import kotlin.math.roundToInt

/** Number of positions of a snapped slider (SPEC «Привязка значений»: 25 levels; angles then go in 15° steps). */
const val SNAP_LEVELS = 25

/** A group title in a list of parameters. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/**
 * A parameter name. When [help] is given, the name is underlined with dots and a tap shows the help
 * (SPEC: «Тап по названию параметра — короткая справка»).
 */
@Composable
fun ParameterName(text: String, help: String?, modifier: Modifier = Modifier) {
    var showHelp by remember { mutableStateOf(false) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textDecoration = if (help != null) TextDecoration.Underline else null,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = if (help != null) modifier.clickable { showHelp = true } else modifier,
    )
    if (showHelp && help != null) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.dialog_ok)) } },
            title = { Text(text) },
            text = { Text(help) },
        )
    }
}

/**
 * A slider with its name and value. [levels] > 1 makes it jump between that many evenly spaced positions;
 * 0 lets it move freely.
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
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ParameterName(label, help, Modifier.weight(1f))
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = if (levels > 2) levels - 2 else 0,
            enabled = enabled,
        )
    }
}

/** An on/off parameter. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, help: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        ParameterName(label, help, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A parameter with a few named choices: the current one is a button that opens the list. */
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
        modifier = Modifier.fillMaxWidth(),
    ) {
        ParameterName(label, help, Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    optionIcon?.invoke(selected)
                    Text(optionText(selected), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (option in options) {
                    DropdownMenuItem(
                        text = { Text(optionText(option)) },
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
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

/** A yes/no question before something that cannot be undone. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        title = { Text(title) },
        text = { Text(text) },
    )
}

/** «0,50» with the phone's decimal separator. */
fun decimal(value: Float, digits: Int = 2): String = formatNumber(value.toDouble(), digits)

fun degrees(value: Float): String = "${value.roundToInt()}°"
