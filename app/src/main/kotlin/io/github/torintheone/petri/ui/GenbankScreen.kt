package io.github.torintheone.petri.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.torintheone.petri.PetriViewModel
import io.github.torintheone.petri.R
import io.github.torintheone.petri.data.GenbankEntry
import io.github.torintheone.petri.sim.Genome
import io.github.torintheone.petri.sim.PreviewFrame
import io.github.torintheone.petri.sim.SimStats
import java.io.File

/**
 * «Генбанк» (SPEC «Интерфейс»): the genome in the editor with its actions, then the saved genomes with pictures.
 * A tap on a saved genome opens it in the editor.
 */
@Composable
fun GenbankScreen(viewModel: PetriViewModel, modifier: Modifier = Modifier) {
    val entries by viewModel.genbank.collectAsStateWithLifecycle()
    val genome by viewModel.editor.genome.collectAsStateWithLifecycle()
    val frames by viewModel.editor.preview.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle(initialValue = SimStats.EMPTY)
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.share_chooser)
    var saving by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GenbankEntry?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importGenome(uri)
    }
    val share: (Genome) -> Unit = { shared -> viewModel.shareGenome(shared) { file -> context.shareFile(file, chooserTitle) } }

    LazyColumn(
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End)),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            CurrentGenomeCard(
                genome = genome,
                picture = frames.lastOrNull(),
                microscopeReady = stats.selected != null,
                onSave = { saving = true },
                onShare = { share(genome) },
                onFromMicroscope = viewModel::copySelectedGenome,
                onDefault = viewModel::loadDefaultGenome,
                onImport = { importer.launch(arrayOf("*/*")) },
            )
        }
        item { SectionTitle(stringResource(R.string.genbank_saved)) }
        if (entries.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.genbank_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(entries, key = { it.name }) { entry ->
            GenbankRow(
                entry = entry,
                picture = viewModel.thumbnails[viewModel.thumbnailKey(entry)],
                onLoad = { viewModel.loadGenome(entry) },
                onShare = { share(entry.genome.copy(name = entry.name)) },
                onDelete = { deleting = entry },
            )
        }
    }

    if (saving) {
        NameDialog(
            title = stringResource(R.string.dialog_save_genome),
            initial = genome.name,
            note = stringResource(R.string.dialog_overwrite_note),
            onConfirm = { name ->
                saving = false
                viewModel.saveGenome(name)
            },
            onDismiss = { saving = false },
        )
    }
    deleting?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_title, entry.name),
            text = stringResource(R.string.dialog_delete_genome_text),
            confirm = stringResource(R.string.genbank_delete),
            onConfirm = { viewModel.deleteGenome(entry) },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrentGenomeCard(
    genome: Genome,
    picture: PreviewFrame?,
    microscopeReady: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onFromMicroscope: () -> Unit,
    onDefault: () -> Unit,
    onImport: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrganismThumbnail(
                    picture,
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.genbank_in_editor),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = genome.name.ifBlank { stringResource(R.string.genome_untitled) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = genomeSummary(genome),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onSave) { Text(stringResource(R.string.genbank_save)) }
                OutlinedButton(onClick = onShare) { Text(stringResource(R.string.genbank_share)) }
                OutlinedButton(onClick = onFromMicroscope, enabled = microscopeReady) { Text(stringResource(R.string.genbank_from_microscope)) }
                OutlinedButton(onClick = onDefault) { Text(stringResource(R.string.genbank_default)) }
                OutlinedButton(onClick = onImport) { Text(stringResource(R.string.genbank_import)) }
            }
            if (!microscopeReady) {
                Text(
                    text = stringResource(R.string.genbank_microscope_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GenbankRow(entry: GenbankEntry, picture: PreviewFrame?, onLoad: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLoad),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            OrganismThumbnail(
                picture,
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = genomeSummary(entry.genome),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare) {
                Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.genbank_share))
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_tool_delete), contentDescription = stringResource(R.string.genbank_delete))
            }
        }
    }
}

/** «Фагоцит · режимов: 2»: the initial cell type and how many modes the organism uses. */
@Composable
private fun genomeSummary(genome: Genome): String {
    val types = stringArrayResource(R.array.cell_types)
    val used = remember(genome) { genome.reachableModes(genome.initialMode).size }
    return stringResource(R.string.genome_summary, types[genome.modes[genome.initialMode].cellType.ordinal], used)
}

/** Opens the system share sheet for an exported genome file. */
private fun Context.shareFile(file: File, title: String) {
    val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, title))
}
