package io.github.torintheone.petri.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.torintheone.petri.PetriViewModel
import io.github.torintheone.petri.R
import io.github.torintheone.petri.data.GenbankEntry
import io.github.torintheone.petri.sim.Genome
import io.github.torintheone.petri.sim.PreviewFrame
import io.github.torintheone.petri.sim.SimStats
import io.github.torintheone.petri.ui.theme.Lab
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
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Eyebrow(stringResource(R.string.tab_genbank), Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp)) }
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
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, top = 10.dp)) {
                Eyebrow(stringResource(R.string.genbank_saved), Modifier.weight(1f, fill = false))
                if (entries.isNotEmpty()) ValueChip(entries.size.toString(), Modifier.padding(start = 8.dp))
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.genbank_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Lab.TextDim,
                    modifier = Modifier.padding(horizontal = 2.dp),
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
    Panel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Thumbnail(picture, CURRENT_THUMBNAIL)
            Column(Modifier.weight(1f)) {
                Eyebrow(stringResource(R.string.genbank_in_editor))
                Text(
                    text = genome.name.ifBlank { stringResource(R.string.genome_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    color = Lab.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = genomeSummary(genome), style = MaterialTheme.typography.bodySmall, color = Lab.TextDim)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 14.dp),
        ) {
            LabButton(stringResource(R.string.genbank_save), onClick = onSave, icon = R.drawable.ic_save, kind = ButtonKind.PRIMARY)
            LabButton(stringResource(R.string.genbank_share), onClick = onShare, icon = R.drawable.ic_share)
            LabButton(stringResource(R.string.genbank_from_microscope), onClick = onFromMicroscope, icon = R.drawable.ic_tab_microscope, enabled = microscopeReady)
            LabButton(stringResource(R.string.genbank_default), onClick = onDefault, icon = R.drawable.ic_reset)
            LabButton(stringResource(R.string.genbank_import), onClick = onImport, icon = R.drawable.ic_import)
        }
        if (!microscopeReady) {
            Text(
                text = stringResource(R.string.genbank_microscope_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Lab.TextFaint,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** A saved genome: a tap opens it in the editor. */
@Composable
private fun GenbankRow(entry: GenbankEntry, picture: PreviewFrame?, onLoad: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Panel(
        onClick = onLoad,
        contentPadding = PaddingValues(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumbnail(picture, ROW_THUMBNAIL)
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall, color = Lab.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = genomeSummary(entry.genome), style = MaterialTheme.typography.bodySmall, color = Lab.TextDim)
            }
            LabIconButton(R.drawable.ic_share, stringResource(R.string.genbank_share), onShare, size = 40.dp, iconSize = 20.dp)
            LabIconButton(R.drawable.ic_tool_delete, stringResource(R.string.genbank_delete), onDelete, size = 40.dp, iconSize = 20.dp)
        }
    }
}

/** An organism picture in a small framed window. */
@Composable
private fun Thumbnail(picture: PreviewFrame?, size: Dp) {
    val shape = MaterialTheme.shapes.small
    OrganismThumbnail(
        picture,
        Modifier
            .size(size)
            .clip(shape)
            .border(1.dp, Lab.Hairline, shape),
    )
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

private val CURRENT_THUMBNAIL = 76.dp
private val ROW_THUMBNAIL = 56.dp
