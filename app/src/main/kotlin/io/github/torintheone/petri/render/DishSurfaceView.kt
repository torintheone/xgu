package io.github.torintheone.petri.render

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import io.github.torintheone.petri.sim.SimulationRunner
import kotlin.math.hypot

/**
 * The microscope view of the dish. One finger is left free for the tools of later stages;
 * two fingers zoom and move the camera together, a double tap fits the whole dish into the view.
 *
 * It redraws only when something changes: a new simulation snapshot or a camera move.
 */
@SuppressLint("ViewConstructor")
class DishSurfaceView(
    context: Context,
    private val runner: SimulationRunner,
    private val camera: DishCamera,
) : GLSurfaceView(context) {
    private val requestFrame: () -> Unit = { requestRender() }

    private val doubleTap = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                camera.fit()
                requestRender()
                return true
            }
        },
    )

    private var pinching = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var lastSpan = 0f

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(DishRenderer(runner, camera))
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        runner.publishListener = requestFrame
    }

    override fun onDetachedFromWindow() {
        if (runner.publishListener === requestFrame) runner.publishListener = null
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        doubleTap.onTouchEvent(event)
        when (event.actionMasked) {
            // The set of fingers changed: start measuring again from the next move.
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> pinching = false
            MotionEvent.ACTION_MOVE -> if (event.pointerCount >= 2) pinch(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pinching = false
        }
        return true
    }

    private fun pinch(event: MotionEvent) {
        val x0 = event.getX(0)
        val y0 = event.getY(0)
        val x1 = event.getX(1)
        val y1 = event.getY(1)
        val focusX = (x0 + x1) / 2f
        val focusY = (y0 + y1) / 2f
        val span = hypot(x1 - x0, y1 - y0)
        if (pinching && lastSpan > 0f && span > 0f) {
            val scale = (span / lastSpan).coerceIn(MIN_SCALE_STEP, MAX_SCALE_STEP)
            camera.moveAndZoom(lastFocusX, lastFocusY, focusX, focusY, scale)
            requestRender()
        }
        lastFocusX = focusX
        lastFocusY = focusY
        lastSpan = span
        pinching = true
    }

    private companion object {
        /** Limits one touch event's zoom step, so a jittery reading cannot jump the view. */
        const val MIN_SCALE_STEP = 0.5f
        const val MAX_SCALE_STEP = 2f
    }
}
