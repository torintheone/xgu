package io.github.torintheone.petri.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.torintheone.petri.R
import io.github.torintheone.petri.sim.PopulationHistory

/**
 * The population graph (SPEC «Этап 7»): cells, food particles and species over the recorded hours.
 * Each line has its own scale, so all three stay readable; the legend gives the latest and highest values.
 */
@Composable
fun PopulationGraph(samples: List<PopulationHistory.Sample>, modifier: Modifier = Modifier) {
    val cellsColor = MaterialTheme.colorScheme.primary
    val foodColor = MaterialTheme.colorScheme.tertiary
    val speciesColor = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = GRAPH_ALPHA),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(cellsColor, stringResource(R.string.stat_cells), samples.lastOrNull()?.cells, samples.maxOfOrNull { it.cells })
                Legend(foodColor, stringResource(R.string.stat_food), samples.lastOrNull()?.food, samples.maxOfOrNull { it.food })
                Legend(speciesColor, stringResource(R.string.stat_species), samples.lastOrNull()?.species, samples.maxOfOrNull { it.species })
            }
            if (samples.size < 2) {
                Text(
                    text = stringResource(R.string.graph_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                ) {
                    val first = samples.first().hour
                    val span = (samples.last().hour - first).coerceAtLeast(1e-3f)
                    for (k in 0..GRID_LINES) {
                        val y = size.height * k / GRID_LINES
                        drawLine(grid, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y))
                    }
                    fun line(color: Color, value: (PopulationHistory.Sample) -> Int) {
                        val top = (samples.maxOf(value)).coerceAtLeast(1).toFloat()
                        val path = Path()
                        samples.forEachIndexed { index, sample ->
                            val x = (sample.hour - first) / span * size.width
                            val y = size.height * (1f - value(sample) / top)
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
                    }
                    line(foodColor) { it.food }
                    line(speciesColor) { it.species }
                    line(cellsColor) { it.cells }
                }
                Text(
                    text = stringResource(
                        R.string.graph_span,
                        formatNumber(samples.first().hour.toDouble(), 0),
                        formatNumber(samples.last().hour.toDouble(), 0),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, latest: Int?, highest: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (latest == null) "—" else stringResource(R.string.graph_value, latest, highest ?: latest),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private const val GRAPH_ALPHA = 0.88f
private const val GRID_LINES = 4
