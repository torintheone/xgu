package io.github.torintheone.petri.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.torintheone.petri.R
import io.github.torintheone.petri.sim.PopulationHistory
import io.github.torintheone.petri.ui.theme.Lab
import io.github.torintheone.petri.ui.theme.LabType

/**
 * The population graph (SPEC «Этап 7»): cells, food particles and species over the recorded hours.
 * Each line has its own scale, so all three stay readable; the readings above it give the current values
 * and the labels in the corner the highest ones.
 */
@Composable
fun PopulationChart(
    samples: List<PopulationHistory.Sample>,
    cellsColor: Color,
    foodColor: Color,
    speciesColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (samples.size < 2) {
            Text(
                text = stringResource(R.string.graph_empty),
                style = MaterialTheme.typography.bodySmall,
                color = Lab.TextDim,
            )
            return@Column
        }
        Text(
            text = stringResource(
                R.string.graph_peaks,
                samples.maxOf { it.cells },
                samples.maxOf { it.food },
                samples.maxOf { it.species },
            ),
            style = LabType.Mono,
            color = Lab.TextFaint,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT),
        ) {
            val first = samples.first().hour
            val span = (samples.last().hour - first).coerceAtLeast(MIN_SPAN_HOURS)
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
            for (k in 0..GRID_LINES) {
                val y = size.height * k / GRID_LINES
                drawLine(Lab.Hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = if (k == GRID_LINES) null else dash)
            }

            fun points(value: (PopulationHistory.Sample) -> Int): List<Offset> {
                val top = samples.maxOf(value).coerceAtLeast(1).toFloat()
                return samples.map { sample ->
                    Offset((sample.hour - first) / span * size.width, size.height * (1f - HEADROOM * value(sample) / top))
                }
            }

            fun path(points: List<Offset>, closeToBottom: Boolean): Path = Path().apply {
                points.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
                if (closeToBottom) {
                    lineTo(points.last().x, size.height)
                    lineTo(points.first().x, size.height)
                    close()
                }
            }

            val stroke = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round)
            drawPath(path(points { it.food }, closeToBottom = false), foodColor, style = stroke)
            drawPath(path(points { it.species }, closeToBottom = false), speciesColor, style = stroke)
            val cellPoints = points { it.cells }
            drawPath(path(cellPoints, closeToBottom = true), Brush.verticalGradient(listOf(cellsColor.copy(alpha = AREA_ALPHA), Color.Transparent)))
            val cells = path(cellPoints, closeToBottom = false)
            drawPath(cells, cellsColor, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Text(stringResource(R.string.hours_value, formatNumber(samples.first().hour.toDouble(), 0)), style = LabType.Mono, color = Lab.TextFaint)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.hours_value, formatNumber(samples.last().hour.toDouble(), 0)), style = LabType.Mono, color = Lab.TextFaint)
        }
    }
}

private val CHART_HEIGHT = 110.dp
private const val GRID_LINES = 4
private const val MIN_SPAN_HOURS = 1e-3f

/** The highest point of a line stays a little below the top edge. */
private const val HEADROOM = 0.92f
private const val AREA_ALPHA = 0.22f
