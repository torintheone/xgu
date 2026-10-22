package io.github.torintheone.petri.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.torintheone.petri.sim.CellType
import io.github.torintheone.petri.sim.PreviewFrame
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Background of the preview and of thumbnails: the same dark medium as the dish. */
val MediumColor = Color(0xFF0B1418)

/** A small picture of an organism, fitted into the available space, over a faint grid like a micrograph. */
@Composable
fun OrganismThumbnail(frame: PreviewFrame?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(MediumColor)
        val step = size.minDimension / THUMBNAIL_GRID
        for (k in 1 until THUMBNAIL_GRID) {
            drawLine(ThumbnailGrid, Offset(step * k, 0f), Offset(step * k, size.height), strokeWidth = 1f)
            drawLine(ThumbnailGrid, Offset(0f, step * k), Offset(size.width, step * k), strokeWidth = 1f)
        }
        if (frame == null || frame.cellCount == 0) return@Canvas
        val scale = min(size.width, size.height) / (2f * max(frame.bodyExtent, MIN_EXTENT_UM) * FIT_MARGIN)
        drawOrganism(frame, scale, highlightMode = -1)
    }
}

/**
 * Draws [frame] at [scale] pixels per µm with its organism (see [PreviewFrame.bodyCenterX]) in the middle of the
 * canvas. Cells look like on the dish: a membrane in the mode colour, a nucleus, a dot at the front and a simple
 * mark for the type. Cells of [highlightMode] get a ring.
 */
fun DrawScope.drawOrganism(frame: PreviewFrame, scale: Float, highlightMode: Int) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    fun sx(x: Float) = cx + (x - frame.bodyCenterX) * scale
    fun sy(y: Float) = cy - (y - frame.bodyCenterY) * scale

    val bondWidth = max(1f, 2.5f * scale)
    for (k in 0 until frame.bondCount) {
        val o = k * PreviewFrame.BOND_FLOATS
        drawLine(
            color = BondColor,
            start = Offset(sx(frame.bonds[o]), sy(frame.bonds[o + 1])),
            end = Offset(sx(frame.bonds[o + 2]), sy(frame.bonds[o + 3])),
            strokeWidth = bondWidth,
            cap = StrokeCap.Round,
        )
    }
    for (i in 0 until frame.cellCount) {
        val o = i * PreviewFrame.CELL_FLOATS
        val center = Offset(sx(frame.cells[o]), sy(frame.cells[o + 1]))
        val r = frame.cells[o + 2] * scale
        val angle = frame.cells[o + 3]
        val color = Color(frame.cells[o + 4].coerceIn(0f, 1f), frame.cells[o + 5].coerceIn(0f, 1f), frame.cells[o + 6].coerceIn(0f, 1f))
        drawCell(center, r, angle, color, frame.type(i))
        if (frame.mode(i) == highlightMode) {
            drawCircle(HighlightColor, radius = r + max(2f, 0.25f * r), center = center, style = Stroke(width = max(1.5f, 0.12f * r)))
        }
    }
}

private fun DrawScope.drawCell(center: Offset, r: Float, angle: Float, color: Color, type: CellType) {
    // Screen y points down, so the front direction flips its y.
    val fx = cos(angle)
    val fy = -sin(angle)
    fun at(along: Float, across: Float) = Offset(center.x + (fx * along - fy * across) * r, center.y + (fy * along + fx * across) * r)
    val thin = max(1f, 0.08f * r)

    // Marks that sit outside the membrane go first.
    when (type) {
        CellType.FLAGELLOCYTE -> {
            drawLine(color, at(-1f, 0f), at(-1.5f, 0.18f), strokeWidth = thin * 1.4f, cap = StrokeCap.Round)
            drawLine(color, at(-1.5f, 0.18f), at(-1.95f, -0.12f), strokeWidth = thin * 1.2f, cap = StrokeCap.Round)
        }
        CellType.CILIOCYTE -> for (k in 0 until 12) {
            val a = k * (2f * Math.PI.toFloat() / 12f)
            drawLine(color, at(cos(a), sin(a)), at(1.25f * cos(a), 1.25f * sin(a)), strokeWidth = thin, cap = StrokeCap.Round)
        }
        CellType.VIROCYTE -> for (k in 0 until 8) {
            val a = k * (2f * Math.PI.toFloat() / 8f)
            drawLine(color, at(0.9f * cos(a), 0.9f * sin(a)), at(1.3f * cos(a), 1.3f * sin(a)), strokeWidth = thin * 1.5f, cap = StrokeCap.Round)
        }
        CellType.SENSEOCYTE -> {
            drawLine(color, at(0.9f, 0.3f), at(1.45f, 0.55f), strokeWidth = thin, cap = StrokeCap.Round)
            drawLine(color, at(0.9f, -0.3f), at(1.45f, -0.55f), strokeWidth = thin, cap = StrokeCap.Round)
        }
        CellType.STEREOCYTE -> drawLine(color, at(0.9f, 0f), at(1.6f, 0f), strokeWidth = thin * 1.3f, cap = StrokeCap.Round)
        CellType.GLUEOCYTE -> drawCircle(
            color,
            radius = r * 1.12f,
            center = center,
            style = Stroke(width = thin, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.2f, r * 0.15f))),
        )
        else -> Unit
    }

    drawCircle(color.copy(alpha = 0.32f), radius = r, center = center)
    val membrane = if (type == CellType.KERATINOCYTE) 0.22f * r else 0.12f * r
    drawCircle(color, radius = r - membrane / 2f, center = center, style = Stroke(width = max(1f, membrane)))

    when (type) {
        CellType.PHOTOCYTE -> for (k in 0 until 3) {
            val a = k * (2f * Math.PI.toFloat() / 3f) + 0.5f
            drawCircle(ChloroplastColor, radius = 0.17f * r, center = at(0.52f * cos(a), 0.52f * sin(a)))
        }
        CellType.LIPOCYTE -> drawCircle(FatColor, radius = 0.55f * r, center = at(-0.15f, 0.1f))
        CellType.BUOYOCYTE -> drawCircle(Color.White.copy(alpha = 0.55f), radius = 0.42f * r, center = center, style = Stroke(width = thin))
        CellType.DEVOROCYTE -> {
            drawLine(Color.White.copy(alpha = 0.8f), at(0.95f, 0.25f), at(0.6f, 0f), strokeWidth = thin * 1.3f, cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.8f), at(0.95f, -0.25f), at(0.6f, 0f), strokeWidth = thin * 1.3f, cap = StrokeCap.Round)
        }
        CellType.MYOCYTE -> for (k in -1..1) {
            drawLine(color.copy(alpha = 0.9f), at(-0.6f, 0.35f * k), at(0.6f, 0.35f * k), strokeWidth = thin, cap = StrokeCap.Round)
        }
        CellType.NEUROCYTE -> for (k in 0 until 5) {
            val a = k * (2f * Math.PI.toFloat() / 5f)
            drawLine(Color.White.copy(alpha = 0.6f), center, at(0.75f * cos(a), 0.75f * sin(a)), strokeWidth = thin * 0.8f)
        }
        CellType.NITROCYTE -> for (k in 0 until 4) {
            val a = k * (Math.PI.toFloat() / 2f) + 0.78f
            drawCircle(NitrateColor, radius = 0.12f * r, center = at(0.55f * cos(a), 0.55f * sin(a)))
        }
        CellType.SECROCYTE -> for (k in 0 until 3) {
            drawCircle(color.copy(alpha = 0.8f), radius = 0.1f * r, center = at(0.55f, 0.35f * (k - 1)))
        }
        CellType.STEMOCYTE -> drawCircle(Color.White.copy(alpha = 0.45f), radius = 0.62f * r, center = center, style = Stroke(width = thin))
        CellType.GAMETE -> drawCircle(Color.White.copy(alpha = 0.35f), radius = 0.7f * r, center = at(0.15f, 0f), style = Stroke(width = thin))
        else -> Unit
    }

    // Nucleus and the front dot.
    val nucleus = if (type == CellType.GAMETE) 0.2f else 0.3f
    drawCircle(NucleusColor, radius = nucleus * r, center = if (type == CellType.LIPOCYTE) at(0.35f, -0.2f) else center)
    drawCircle(Color.White.copy(alpha = 0.85f), radius = max(1f, 0.1f * r), center = at(0.78f, 0f))
}

private val BondColor = Color(0x80E0F2F1)
private val ThumbnailGrid = Color(0x0FFFFFFF)
private const val THUMBNAIL_GRID = 6
private val HighlightColor = Color(0xFFFFFFFF)
private val NucleusColor = Color(0xCC10202A)
private val ChloroplastColor = Color(0xFF3FBF4A)
private val FatColor = Color(0x99FFE7A0)
private val NitrateColor = Color(0xFF9FD8FF)

/** The organism never fills the picture edge to edge. */
const val FIT_MARGIN = 1.12f

/** A lone cell is not drawn huge: the picture always spans at least this radius, µm. */
const val MIN_EXTENT_UM = 40f
