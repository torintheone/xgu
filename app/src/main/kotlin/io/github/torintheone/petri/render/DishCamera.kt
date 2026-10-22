package io.github.torintheone.petri.render

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min

/**
 * Where the microscope looks: the world point in the middle of the view and the magnification.
 * Gestures and buttons change it on the UI thread, the renderer reads it on the GL thread, hence the locking.
 * A flung view glides on and slows down, and the buttons move it smoothly; the renderer drives both through
 * [advance] once per frame.
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

    /** Diagnostics: keep this cell in the middle of the view (0 for none). */
    @Volatile
    var followUid: Int = 0

    /** Asks the view for a new frame; set by the view that shows the dish. */
    @Volatile
    var invalidate: (() -> Unit)? = null

    // A fling: world velocity in µm per second, slowing down by [FLING_FRICTION].
    private var flingX = 0f
    private var flingY = 0f
    private var lastAdvanceNanos = 0L

    // A smooth move from one view to another (the zoom and «вся чашка» buttons).
    private var animating = false
    private var animStartNanos = 0L
    private var fromX = 0f
    private var fromY = 0f
    private var fromZoom = 0f
    private var toX = 0f
    private var toY = 0f
    private var toZoom = 0f
    private var toFit = false

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

    /**
     * Two-finger gesture step: the world point under ([fromX], [fromY]) moves to ([toX], [toY])
     * while the magnification changes by [scale].
     */
    @Synchronized
    fun moveAndZoom(fromX: Float, fromY: Float, toX: Float, toY: Float, scale: Float) {
        if (zoom == 0f) return
        stopMotion()
        val worldX = centerX + (fromX - viewWidth / 2f) / zoom
        val worldY = centerY - (fromY - viewHeight / 2f) / zoom
        zoom = (zoom * scale).coerceIn(minZoom(), maxZoom())
        centerX = worldX - (toX - viewWidth / 2f) / zoom
        centerY = worldY + (toY - viewHeight / 2f) / zoom
        followsFit = false
        clampCenter()
    }

    /** One finger moved by ([dx], [dy]) screen pixels: the dish moves with it. */
    @Synchronized
    fun panBy(dx: Float, dy: Float) {
        if (zoom == 0f) return
        stopMotion()
        centerX -= dx / zoom
        centerY += dy / zoom
        followsFit = false
        clampCenter()
    }

    /** The finger left the screen moving at ([vx], [vy]) pixels per second: the view glides on and slows down. */
    @Synchronized
    fun fling(vx: Float, vy: Float) {
        if (zoom == 0f || hypot(vx, vy) < MIN_FLING_PX_PER_SECOND) return
        animating = false
        flingX = -vx / zoom
        flingY = vy / zoom
        lastAdvanceNanos = System.nanoTime()
    }

    /** Stops a glide or a smooth move where it is. */
    @Synchronized
    fun stopMotion() {
        flingX = 0f
        flingY = 0f
        animating = false
    }

    /** Smoothly zooms in ([factor] above 1) or out around the middle of the view. */
    fun zoomBy(factor: Float) {
        synchronized(this) {
            if (zoom == 0f) return
            startAnimation(centerX, centerY, (zoom * factor).coerceIn(minZoom(), maxZoom()), fit = false)
        }
        invalidate?.invoke()
    }

    /** Smoothly shows the whole dish. */
    fun showWholeDish() {
        synchronized(this) {
            if (zoom == 0f) return
            startAnimation(0f, 0f, fitZoom(), fit = true)
        }
        invalidate?.invoke()
    }

    /** Shows the whole dish at once and keeps doing so, also when a dish of another size comes in. */
    fun fitWholeDish() {
        synchronized(this) {
            flingX = 0f
            flingY = 0f
            animating = false
            followsFit = true
            refresh()
        }
        invalidate?.invoke()
    }

    /** Moves a glide or a smooth move on to [now]; true while the view is still moving. Called once per frame. */
    @Synchronized
    fun advance(now: Long): Boolean {
        if (zoom == 0f) return false
        if (animating) {
            val t = ((now - animStartNanos).toFloat() / ANIMATION_NANOS).coerceIn(0f, 1f)
            val eased = 1f - (1f - t) * (1f - t) * (1f - t)
            zoom = exp(ln(fromZoom) + (ln(toZoom) - ln(fromZoom)) * eased)
            centerX = fromX + (toX - fromX) * eased
            centerY = fromY + (toY - fromY) * eased
            clampCenter()
            if (t >= 1f) {
                animating = false
                followsFit = toFit
            }
            return animating
        }
        if (flingX == 0f && flingY == 0f) return false
        val dt = ((now - lastAdvanceNanos) / NANOS_PER_SECOND).coerceIn(0f, MAX_FRAME_SECONDS)
        lastAdvanceNanos = now
        centerX += flingX * dt
        centerY += flingY * dt
        clampCenter()
        val keep = exp(-FLING_FRICTION * dt)
        flingX *= keep
        flingY *= keep
        if (hypot(flingX, flingY) * zoom < MIN_FLING_PX_PER_SECOND) {
            flingX = 0f
            flingY = 0f
        }
        return true
    }

    private fun startAnimation(x: Float, y: Float, newZoom: Float, fit: Boolean) {
        flingX = 0f
        flingY = 0f
        fromX = centerX
        fromY = centerY
        fromZoom = zoom
        toX = x
        toY = y
        toZoom = newZoom
        toFit = fit
        followsFit = false
        animStartNanos = System.nanoTime()
        animating = true
    }

    /** Centres the view on a world point without changing the magnification (following a cell). */
    @Synchronized
    fun centerOn(x: Float, y: Float) {
        if (zoom == 0f || animating) return
        flingX = 0f
        flingY = 0f
        centerX = x
        centerY = y
        followsFit = false
        clampCenter()
    }

    /** Screen pixels per micrometre; 0 until the camera can see. */
    @Synchronized
    fun pixelsPerUnit(): Float = zoom

    /** World coordinates of a screen point; null until the camera can see. */
    @Synchronized
    fun screenToWorld(sx: Float, sy: Float): Pair<Float, Float>? {
        if (zoom == 0f) return null
        return Pair(centerX + (sx - viewWidth / 2f) / zoom, centerY - (sy - viewHeight / 2f) / zoom)
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
        const val NANOS_PER_SECOND = 1e9f

        /** A smooth move of the buttons takes this long. */
        const val ANIMATION_NANOS = 280_000_000f

        /** How fast a glide slows down: its speed falls e times in 1/[FLING_FRICTION] seconds. */
        const val FLING_FRICTION = 4f

        /** A glide slower than this, in screen pixels per second, stops. */
        const val MIN_FLING_PX_PER_SECOND = 40f

        /** A long pause between frames does not throw a glide far. */
        const val MAX_FRAME_SECONDS = 0.1f

        /** Empty border around the fitted dish, as a share of its diameter. */
        const val FIT_MARGIN = 1.08f

        /** How far the dish may shrink below the fitted size. */
        const val MIN_ZOOM_OF_FIT = 0.5f

        /** At the strongest magnification the short side of the view spans this many micrometres. */
        const val MIN_VISIBLE_SPAN_UM = 40f
    }
}
