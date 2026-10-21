package io.github.torintheone.petri.render

import kotlin.math.hypot
import kotlin.math.min

/**
 * Where the microscope looks: the world point in the middle of the view and the magnification.
 * Gestures change it on the UI thread, the renderer reads it on the GL thread, hence the locking.
 *
 * World coordinates are micrometres with y pointing up; screen coordinates are pixels with y pointing down.
 */
class DishCamera {
    private var viewWidth = 0
    private var viewHeight = 0
    private var dishRadius = 0f
    private var centerX = 0f
    private var centerY = 0f

    /** Screen pixels per micrometre; 0 until the view size and the dish size are both known. */
    private var zoom = 0f

    /** Until the user moves the camera, it keeps the whole dish in view, e.g. after rotation. */
    private var followsFit = true

    @Synchronized
    fun setViewport(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        refresh()
    }

    @Synchronized
    fun setDishRadius(radius: Float) {
        if (radius == dishRadius) return
        dishRadius = radius
        refresh()
    }

    /** Shows the whole dish («двойной тап — вписать чашку в экран»). */
    @Synchronized
    fun fit() {
        followsFit = true
        refresh()
    }

    /**
     * Two-finger gesture step: the world point under ([fromX], [fromY]) moves to ([toX], [toY])
     * while the magnification changes by [scale].
     */
    @Synchronized
    fun moveAndZoom(fromX: Float, fromY: Float, toX: Float, toY: Float, scale: Float) {
        if (zoom == 0f) return
        val worldX = centerX + (fromX - viewWidth / 2f) / zoom
        val worldY = centerY - (fromY - viewHeight / 2f) / zoom
        zoom = (zoom * scale).coerceIn(minZoom(), maxZoom())
        centerX = worldX - (toX - viewWidth / 2f) / zoom
        centerY = worldY + (toY - viewHeight / 2f) / zoom
        followsFit = false
        clampCenter()
    }

    /**
     * Writes the world-to-clip-space transform as a column-major 3×3 matrix for `glUniformMatrix3fv`.
     * Returns false while the camera cannot see anything yet.
     */
    @Synchronized
    fun viewProjection(out: FloatArray): Boolean {
        if (zoom == 0f) return false
        val scaleX = 2f * zoom / viewWidth
        val scaleY = 2f * zoom / viewHeight
        out[0] = scaleX
        out[1] = 0f
        out[2] = 0f
        out[3] = 0f
        out[4] = scaleY
        out[5] = 0f
        out[6] = -centerX * scaleX
        out[7] = -centerY * scaleY
        out[8] = 1f
        return true
    }

    private fun refresh() {
        if (viewWidth <= 0 || viewHeight <= 0 || dishRadius <= 0f) return
        if (followsFit || zoom == 0f) {
            zoom = fitZoom()
            centerX = 0f
            centerY = 0f
        } else {
            zoom = zoom.coerceIn(minZoom(), maxZoom())
            clampCenter()
        }
    }

    private fun fitZoom(): Float = min(viewWidth, viewHeight) / (2f * dishRadius * FIT_MARGIN)

    private fun minZoom(): Float = fitZoom() * MIN_ZOOM_OF_FIT

    private fun maxZoom(): Float = maxOf(fitZoom(), min(viewWidth, viewHeight) / MIN_VISIBLE_SPAN_UM)

    /** Keeps the middle of the view over the dish, so the dish cannot be lost off screen. */
    private fun clampCenter() {
        val distance = hypot(centerX, centerY)
        if (distance > dishRadius) {
            centerX *= dishRadius / distance
            centerY *= dishRadius / distance
        }
    }

    private companion object {
        /** Empty border around the fitted dish, as a share of its diameter. */
        const val FIT_MARGIN = 1.08f

        /** How far the dish may shrink below the fitted size. */
        const val MIN_ZOOM_OF_FIT = 0.5f

        /** At the strongest magnification the short side of the view spans this many micrometres. */
        const val MIN_VISIBLE_SPAN_UM = 40f
    }
}
