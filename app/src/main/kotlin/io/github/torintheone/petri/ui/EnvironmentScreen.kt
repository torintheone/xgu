package io.github.torintheone.petri.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.torintheone.petri.BuildConfig
import io.github.torintheone.petri.PetriViewModel
import io.github.torintheone.petri.R
import io.github.torintheone.petri.data.DishEntry
import io.github.torintheone.petri.sim.SimStats

/** «Среда» (SPEC «Интерфейс»): the dish buttons on top, then the parameters of the medium. */
@Composable
fun EnvironmentScreen(viewModel: PetriViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DishCard(viewModel)
        Text(
            text = stringResource(R.string.placeholder_environment),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.version_label, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** «Сохранить чашку», «Загрузить», «Стерилизовать». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DishCard(viewModel: PetriViewModel) {
    val stats by viewModel.stats.collectAsStateWithLifecycle(initialValue = SimStats.EMPTY)
    val dishes by viewModel.dishes.collectAsStateWithLifecycle()
    var saving by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var sterilizing by rememberSaveable { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.env_dish), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    R.string.env_dish_state,
                    formatNumber(stats.ageHours, 1),
                    stats.cellCount,
                    stats.foodCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { saving = true }) { Text(stringResource(R.string.env_save_dish)) }
                OutlinedButton(onClick = { loading = true }) { Text(stringResource(R.string.env_load_dish)) }
                OutlinedButton(onClick = { sterilizing = true }) { Text(stringResource(R.string.env_sterilize)) }
            }
        }
    }

    if (saving) {
        NameDialog(
            title = stringResource(R.string.dialog_save_dish),
            initial = stringResource(R.string.dish_default_name, formatNumber(stats.ageHours, 0)),
            note = stringResource(R.string.dialog_overwrite_dish_note),
            onConfirm = { name ->
                saving = false
                viewModel.saveDish(name)
            },
            onDismiss = { saving = false },
        )
    }
    if (loading) {
        LoadDishDialog(
            dishes = dishes,
            onLoad = { entry ->
                loading = false
                viewModel.loadDish(entry)
            },
            onDelete = viewModel::deleteDish,
            onDismiss = { loading = false },
        )
    }
    if (sterilizing) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_sterilize_title),
            text = stringResource(R.string.dialog_sterilize_text),
            confirm = stringResource(R.string.env_sterilize),
            onConfirm = viewModel::sterilize,
            onDismiss = { sterilizing = false },
        )
    }
}

@Composable
private fun LoadDishDialog(dishes: List<DishEntry>, onLoad: (DishEntry) -> Unit, onDelete: (DishEntry) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<DishEntry?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        title = { Text(stringResource(R.string.dialog_load_dish)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.dialog_load_dish_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dishes.isEmpty()) {
                    Text(stringResource(R.string.dishes_empty), style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(dishes, key = { it.name }) { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLoad(entry) }
                                    .padding(vertical = 4.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = DateUtils.formatDateTime(
                                            context,
                                            entry.modified,
                                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
                                        ) + " · " + Formatter.formatShortFileSize(context, entry.bytes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { deleting = entry }) {
                                    Icon(painterResource(R.drawable.ic_tool_delete), contentDescription = stringResource(R.string.genbank_delete))
                                }
                            }
                        }
                    }
                }
            }
        },
    )
    deleting?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_title, entry.name),
            text = stringResource(R.string.dialog_delete_dish_text),
            confirm = stringResource(R.string.genbank_delete),
            onConfirm = { onDelete(entry) },
            onDismiss = { deleting = null },
        )
    }
}
