package io.github.torintheone.petri.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import io.github.torintheone.petri.render.DishCamera
import io.github.torintheone.petri.render.DishSurfaceView
import io.github.torintheone.petri.sim.SimStats
import io.github.torintheone.petri.sim.SimulationRunner
import io.github.torintheone.petri.sim.TimeMode
import java.util.Locale

/** «Микроскоп»: the dish fills the screen, the status panel floats on top. */
@Composable
fun MicroscopeScreen(viewModel: PetriViewModel, modifier: Modifier = Modifier) {
    val stats by viewModel.stats.collectAsStateWithLifecycle(initialValue = SimStats.EMPTY)
    Box(modifier) {
        DishView(viewModel.runner, viewModel.camera, Modifier.fillMaxSize())
        StatusPanel(
            stats = stats,
            timeMode = viewModel.timeMode,
            onTimeModeSelected = viewModel::selectTimeMode,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                .padding(8.dp)
                .widthIn(max = 560.dp),
        )
    }
}

@Composable
private fun DishView(runner: SimulationRunner, camera: DishCamera, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = remember(runner, camera) { DishSurfaceView(context, runner, camera) }
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
            ) {
                Stat(R.string.stat_age, stringResource(R.string.hours_value, formatHours(stats.ageHours)))
                Stat(R.string.stat_cells, stats.cellCount.toString())
                Stat(R.string.stat_food, stats.foodCount.toString())
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

private val TimeMode.label: Int
    @StringRes get() = when (this) {
        TimeMode.FREEZE -> R.string.time_freeze
        TimeMode.OBSERVE -> R.string.time_observe
        TimeMode.INCUBATE -> R.string.time_incubate
    }

private fun formatHours(hours: Double): String = String.format(Locale.getDefault(), "%.1f", hours)

private const val PANEL_ALPHA = 0.88f
