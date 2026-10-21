package io.github.torintheone.petri.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.torintheone.petri.PetriViewModel
import io.github.torintheone.petri.R
import io.github.torintheone.petri.Tool
import io.github.torintheone.petri.render.DishSurfaceView
import io.github.torintheone.petri.sim.CellInfo
import io.github.torintheone.petri.sim.SimStats
import io.github.torintheone.petri.sim.TimeMode
import java.util.Locale

/** «Микроскоп»: the dish fills the screen; the status panel floats on top, the tools at the bottom. */
@Composable
fun MicroscopeScreen(viewModel: PetriViewModel, modifier: Modifier = Modifier) {
    val stats by viewModel.stats.collectAsStateWithLifecycle(initialValue = SimStats.EMPTY)
    var showGraph by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        DishView(viewModel, Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                .padding(8.dp)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPanel(
                stats = stats,
                timeMode = viewModel.timeMode,
                onTimeModeSelected = viewModel::selectTimeMode,
                graphShown = showGraph,
                onToggleGraph = { showGraph = !showGraph },
            )
            if (showGraph) {
                val history by viewModel.history.collectAsStateWithLifecycle(initialValue = emptyList())
                PopulationGraph(history, Modifier.fillMaxWidth())
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                .padding(8.dp)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            stats.selected?.let { info ->
                DiagnosticsCard(
                    info = info,
                    following = viewModel.following,
                    onFollow = viewModel::toggleFollow,
                    onCopyGenome = viewModel::copySelectedGenome,
                    onClose = viewModel::closeDiagnostics,
                )
            }
            ToolBar(selected = viewModel.tool, onSelect = viewModel::selectTool)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusPanel(
    stats: SimStats,
    timeMode: TimeMode,
    onTimeModeSelected: (TimeMode) -> Unit,
    graphShown: Boolean,
    onToggleGraph: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = PANEL_ALPHA),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stat(R.string.stat_age, stringResource(R.string.hours_value, formatNumber(stats.ageHours, 1)))
                Stat(R.string.stat_cells, stats.cellCount.toString())
                Stat(R.string.stat_food, stats.foodCount.toString())
                Stat(R.string.stat_species, stats.speciesCount.toString())
                IconButton(onClick = onToggleGraph) {
                    Icon(
                        painter = painterResource(R.drawable.ic_graph),
                        contentDescription = stringResource(if (graphShown) R.string.graph_hide else R.string.graph_show),
                        tint = if (graphShown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TimeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mode == timeMode,
                        onClick = { onTimeModeSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = TimeMode.entries.size),
                        icon = {},
                        label = {
                            Text(
                                text = stringResource(mode.label),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(@StringRes label: Int, value: String) {
    Column {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ToolBar(selected: Tool, onSelect: (Tool) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = PANEL_ALPHA),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tool.entries.forEach { tool ->
                val active = tool == selected
                Surface(
                    onClick = { onSelect(tool) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0f),
                    contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(painterResource(tool.icon), contentDescription = null, modifier = Modifier.size(22.dp))
                        Text(
                            text = stringResource(tool.label),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** SPEC «Диагностика»: mode, type, mass, age, nitrates, S1–S4, bonds and «Копировать геном». */
@Composable
private fun DiagnosticsCard(
    info: CellInfo,
    following: Boolean,
    onFollow: () -> Unit,
    onCopyGenome: () -> Unit,
    onClose: () -> Unit,
) {
    val typeNames = stringArrayResource(R.array.cell_types)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = PANEL_ALPHA),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.diagnostics_title, info.mode + 1, typeNames[info.type.ordinal]),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat(R.string.diag_mass, stringResource(R.string.mass_value, formatNumber(info.mass.toDouble(), 2)))
                Stat(R.string.stat_age, stringResource(R.string.hours_value, formatNumber(info.ageHours.toDouble(), 1)))
                Stat(R.string.diag_nitrates, formatNumber(info.nitrates.toDouble(), 2))
                Stat(R.string.diag_bonds, info.bonds.toString())
            }
            Text(
                text = stringResource(
                    R.string.diag_signals,
                    formatNumber(info.s1.toDouble(), 2),
                    formatNumber(info.s2.toDouble(), 2),
                    formatNumber(info.s3.toDouble(), 2),
                    formatNumber(info.s4.toDouble(), 2),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = stringResource(R.string.diag_genome, info.genomeId, info.parentGenomeId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                FilterChip(selected = following, onClick = onFollow, label = { Text(stringResource(R.string.diag_follow)) })
                TextButton(onClick = onCopyGenome) { Text(stringResource(R.string.diag_copy_genome)) }
            }
        }
    }
}

private val TimeMode.label: Int
    @StringRes get() = when (this) {
        TimeMode.FREEZE -> R.string.time_freeze
        TimeMode.OBSERVE -> R.string.time_observe
        TimeMode.INCUBATE -> R.string.time_incubate
    }

private val Tool.label: Int
    @StringRes get() = when (this) {
        Tool.SYNTHESIZER -> R.string.tool_synthesizer
        Tool.TWEEZERS -> R.string.tool_tweezers
        Tool.FEED -> R.string.tool_feed
        Tool.DELETE -> R.string.tool_delete
        Tool.DIAGNOSTICS -> R.string.tool_diagnostics
    }

private val Tool.icon: Int
    @DrawableRes get() = when (this) {
        Tool.SYNTHESIZER -> R.drawable.ic_tool_synthesizer
        Tool.TWEEZERS -> R.drawable.ic_tool_tweezers
        Tool.FEED -> R.drawable.ic_tool_feed
        Tool.DELETE -> R.drawable.ic_tool_delete
        Tool.DIAGNOSTICS -> R.drawable.ic_tool_diagnostics
    }

internal fun formatNumber(value: Double, decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", value)

private const val PANEL_ALPHA = 0.88f
