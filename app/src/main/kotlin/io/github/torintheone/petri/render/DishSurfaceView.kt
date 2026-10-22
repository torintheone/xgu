package io.github.torintheone.petri.render

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import io.github.torintheone.petri.sim.SimulationRunner
import kotlin.math.hypot

/** What one finger does on the dish; coordinates are in the world (µm), [slack] is a finger's width in µm. */
interface DishGestures {
    fun onTap(x: Float, y: Float, slack: Float)

    /** True while a finger that starts on a cell should drag that cell (the tweezers) instead of moving the view. */
    val dragsCells: Boolean

    fun onDragStart(x: Float, y: Float, slack: Float)

    fun onDrag(x: Float, y: Float)

    fun onDragEnd()

    /** The person moved or zoomed the view by hand, so the camera no longer follows a cell. */
    fun onCameraMoved()
}

/**
 * The microscope view of the dish. A tap works the current tool at once. One finger moves the dish and, let go
 * while moving, sends it gliding; with the tweezers a finger that starts on a cell drags the cell instead.
 * Two fingers zoom and move together. A double tap is just two taps.
 *
 * It redraws only when something changes: a new simulation snapshot, a camera move or a glide in progress.
 */
@SuppressLint("ViewConstructor")
class DishSurfaceView(
    context: Context,
    private val runner: SimulationRunner,
    private val camera: DishCamera,
    private val gestures: DishGestures,
) : GLSurfaceView(context) {
    private val requestFrame: () -> Unit = { requestRender() }
    private val renderer = DishRenderer(runner, camera, requestFrame)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val fingerRadius = FINGER_DP * context.resources.displayMetrics.density

    private val taps = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                world(e.x, e.y)?.let { (x, y) -> gestures.onTap(x, y, slack()) }
                return true
            }
        },
    ).apply {
        // Every tap counts at once: no waiting to see whether a second tap follows, no long press.
        setOnDoubleTapListener(null)
        setIsLongpressEnabled(false)
    }

    private enum class Drag { NONE, VIEW, CELL }

    private var drag = Drag.NONE
    private var oneFinger = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var velocity: VelocityTracker? = null

    private var pinching = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var lastSpan = 0f

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        runner.publishListener = requestFrame
        camera.invalidate = requestFrame
    }

    override fun onDetachedFromWindow() {
        if (runner.publishListener === requestFrame) runner.publishListener = null
        if (camera.invalidate === requestFrame) camera.invalidate = null
        velocity?.recycle()
        velocity = null
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        taps.onTouchEvent(event)
        val tracker = velocity ?: VelocityTracker.obtain().also { velocity = it }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) tracker.clear()
        tracker.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                oneFinger = true
                drag = Drag.NONE
                downX = event.x
                downY = event.y
                camera.stopMotion()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger turns the gesture into zoom and move.
                oneFinger = false
                endDrag()
                pinching = false
            }
            MotionEvent.ACTION_POINTER_UP -> pinching = false
            MotionEvent.ACTION_MOVE -> if (event.pointerCount >= 2) {
                pinch(event)
            } else if (oneFinger) {
                if (drag == Drag.NONE && hypot(event.x - downX, event.y - downY) > touchSlop) startDrag()
                when (drag) {
                    Drag.CELL -> world(event.x, event.y)?.let { (x, y) -> gestures.onDrag(x, y) }
                    Drag.VIEW -> {
                        camera.panBy(event.x - lastX, event.y - lastY)
                        lastX = event.x
                        lastY = event.y
                        requestRender()
                    }
                    Drag.NONE -> Unit
                }
            }
            MotionEvent.ACTION_UP -> {
                if (drag == Drag.VIEW) {
                    tracker.computeCurrentVelocity(MILLIS_PER_SECOND)
                    camera.fling(tracker.xVelocity, tracker.yVelocity)
                    requestRender()
                }
                endDrag()
                oneFinger = false
                pinching = false
            }
            MotionEvent.ACTION_CANCEL -> {
                endDrag()
                oneFinger = false
                pinching = false
            }
        }
        return true
    }

    /** The finger has moved far enough: it drags a cell (tweezers on a cell) or the view. */
    private fun startDrag() {
        val start = world(downX, downY)
        if (gestures.dragsCells && start != null && renderer.cellNear(start.first, start.second, slack())) {
            drag = Drag.CELL
            gestures.onDragStart(start.first, start.second, slack())
        } else {
            drag = Drag.VIEW
            // Start from the touch point, so the dish stays under the finger.
            lastX = downX
            lastY = downY
            gestures.onCameraMoved()
        }
    }

    private fun endDrag() {
        if (drag == Drag.CELL) gestures.onDragEnd()
        drag = Drag.NONE
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
            val scale = (span / lastSpan).coerceIn(MIN_SCALE_STEP, MAX_SCALE_STEP)
            camera.moveAndZoom(lastFocusX, lastFocusY, focusX, focusY, scale)
            requestRender()
        } else if (!pinching) {
            gestures.onCameraMoved()
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

        const val MILLIS_PER_SECOND = 1000
    }
}
