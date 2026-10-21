package io.github.torintheone.petri.render

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import io.github.torintheone.petri.sim.SimulationRunner
import kotlin.math.hypot

/** What one finger does on the dish; coordinates are in the world (µm), [slack] is a finger's width in µm. */
interface DishGestures {
    fun onTap(x: Float, y: Float, slack: Float)

    fun onDragStart(x: Float, y: Float, slack: Float)

    fun onDrag(x: Float, y: Float)

    fun onDragEnd()
}

/**
 * The microscope view of the dish. One finger works the current tool (taps and drags go to [gestures]);
 * two fingers zoom and move the camera together, a double tap fits the whole dish into the view.
 *
 * It redraws only when something changes: a new simulation snapshot or a camera move.
 */
@SuppressLint("ViewConstructor")
class DishSurfaceView(
    context: Context,
    private val runner: SimulationRunner,
    private val camera: DishCamera,
    private val gestures: DishGestures,
) : GLSurfaceView(context) {
    private val requestFrame: () -> Unit = { requestRender() }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val fingerRadius = FINGER_DP * context.resources.displayMetrics.density

    private val taps = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                world(e.x, e.y)?.let { (x, y) -> gestures.onTap(x, y, slack()) }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                camera.followUid = 0
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
    private var oneFinger = false
    private var dragging = false
    private var downX = 0f
    private var downY = 0f

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
        taps.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                oneFinger = true
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger turns the gesture into camera control.
                oneFinger = false
                endDrag()
                pinching = false
            }
            MotionEvent.ACTION_POINTER_UP -> pinching = false
            MotionEvent.ACTION_MOVE -> if (event.pointerCount >= 2) {
                pinch(event)
            } else if (oneFinger) {
                if (!dragging && hypot(event.x - downX, event.y - downY) > touchSlop) {
                    dragging = true
                    world(downX, downY)?.let { (x, y) -> gestures.onDragStart(x, y, slack()) }
                }
                if (dragging) world(event.x, event.y)?.let { (x, y) -> gestures.onDrag(x, y) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endDrag()
                oneFinger = false
                pinching = false
            }
        }
        return true
    }

    private fun endDrag() {
        if (dragging) {
            dragging = false
            gestures.onDragEnd()
        }
    }

    private fun world(sx: Float, sy: Float): Pair<Float, Float>? = camera.screenToWorld(sx, sy)

    private fun slack(): Float {
        val zoom = camera.pixelsPerUnit()
        return if (zoom > 0f) fingerRadius / zoom else 0f
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
            camera.followUid = 0
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

        /** How far from a cell a tap still picks it, dp. */
        const val FINGER_DP = 14f
    }
}
