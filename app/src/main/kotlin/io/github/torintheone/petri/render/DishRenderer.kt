package io.github.torintheone.petri.render

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import io.github.torintheone.petri.sim.SimulationRunner
import io.github.torintheone.petri.sim.WorldSnapshot
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Draws the dish from the latest simulation snapshot: medium and light, food, bonds, cells, then the effects of
 * divisions and deaths. All cells go out in one instanced draw call; their look is computed in the fragment shader
 * (see [Shaders]).
 *
 * Between two steps of the world the cells glide from where they were shown to their new places, so slow time
 * looks smooth: each move takes as long as the world took to make that step. [requestFrame] asks for the next
 * frame while something still moves. [effectsOn] switches the effects (see [CellEffects]).
 */
class DishRenderer(
    private val runner: SimulationRunner,
    private val camera: DishCamera,
    private val requestFrame: () -> Unit,
    private val effectsOn: () -> Boolean,
) : GLSurfaceView.Renderer {
    private val viewProjection = FloatArray(9)
    private val zoneData = FloatArray(WorldSnapshot.MAX_ZONES * WorldSnapshot.ZONE_FLOATS)
    private val effects = CellEffects()
    private val effectInstances = FloatArray(CellEffects.MAX_EFFECTS * CellEffects.INSTANCE)

    // Where the cells were shown in the last frame, by place in the snapshot shown, with the cell that held it.
    private var shownX = FloatArray(0)
    private var shownY = FloatArray(0)
    private var shownAngle = FloatArray(0)
    private var shownUids = IntArray(0)
    private var shownCount = 0

    // The move in progress: from where each cell was shown when the newest snapshot came to its new place.
    private var fromX = FloatArray(0)
    private var fromY = FloatArray(0)
    private var fromAngle = FloatArray(0)
    private var fromValid = BooleanArray(0)
    private var seenSerial = -1L
    private var seenStep = -1L
    private var lastPublishedNanos = 0L
    private var moveStartNanos = 0L
    private var moveNanos = 0L

    // The cell and bond records of this frame, with the gliding positions written in.
    private var frameCells = FloatArray(0)
    private var frameBonds = FloatArray(0)

    /** Cells as shown in the last frame, for [cellNear]; two buffers take turns so a reader never sees a half-written one. */
    private val hitBuffers = arrayOf(HitFrame(), HitFrame())
    private var hitTurn = 0

    @Volatile
    private var hitFrame: HitFrame? = null

    private class HitFrame {
        var cells = FloatArray(0)
        var count = 0
    }

    private var dishProgram = 0
    private var foodProgram = 0
    private var bondProgram = 0
    private var cellProgram = 0
    private var effectProgram = 0

    private var dishArray = 0
    private var foodArray = 0
    private var bondArray = 0
    private var cellArray = 0
    private var effectArray = 0
    private var foodBuffer = 0
    private var bondBuffer = 0
    private var cellBuffer = 0
    private var effectBuffer = 0

    private var foodData = floats(256)
    private var bondData = floats(256)
    private var cellData = floats(256)
    private var effectData = floats(CellEffects.MAX_EFFECTS * CellEffects.INSTANCE)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        dishProgram = GlPrograms.build(Shaders.DISH_VERTEX, Shaders.DISH_FRAGMENT)
        foodProgram = GlPrograms.build(Shaders.FOOD_VERTEX, Shaders.FOOD_FRAGMENT)
        bondProgram = GlPrograms.build(Shaders.BOND_VERTEX, Shaders.BOND_FRAGMENT)
        cellProgram = GlPrograms.build(Shaders.CELL_VERTEX, Shaders.CELL_FRAGMENT)
        effectProgram = GlPrograms.build(Shaders.EFFECT_VERTEX, Shaders.EFFECT_FRAGMENT)

        val ids = IntArray(5)
        GLES30.glGenVertexArrays(5, ids, 0)
        dishArray = ids[0]
        foodArray = ids[1]
        bondArray = ids[2]
        cellArray = ids[3]
        effectArray = ids[4]
        GLES30.glGenBuffers(5, ids, 0)
        val quadBuffer = ids[0]
        foodBuffer = ids[1]
        bondBuffer = ids[2]
        cellBuffer = ids[3]
        effectBuffer = ids[4]

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadBuffer)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            QUADS.size * Float.SIZE_BYTES,
            GlPrograms.floatBuffer(QUADS),
            GLES30.GL_STATIC_DRAW,
        )

        // Dish and cells use the centred quad, bonds the one that runs from 0 to 1 along the bond.
        GLES30.glBindVertexArray(dishArray)
        bindQuad(quadBuffer, CENTRED_QUAD)
        GLES30.glBindVertexArray(cellArray)
        bindQuad(quadBuffer, CENTRED_QUAD)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, cellBuffer)
        instanced(1, 4, WorldSnapshot.CELL_FLOATS, 0)
        instanced(2, 3, WorldSnapshot.CELL_FLOATS, 4)
        instanced(3, 4, WorldSnapshot.CELL_FLOATS, 7)
        GLES30.glBindVertexArray(effectArray)
        bindQuad(quadBuffer, CENTRED_QUAD)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, effectBuffer)
        instanced(1, 4, CellEffects.INSTANCE, 0)
        instanced(2, 4, CellEffects.INSTANCE, 4)
        GLES30.glBindVertexArray(bondArray)
        bindQuad(quadBuffer, SEGMENT_QUAD)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bondBuffer)
        instanced(1, 4, WorldSnapshot.BOND_FLOATS, 0)
        instanced(2, 1, WorldSnapshot.BOND_FLOATS, 4)
        GLES30.glBindVertexArray(foodArray)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, foodBuffer)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, WorldSnapshot.FOOD_FLOATS * Float.SIZE_BYTES, 0)
        GLES30.glBindVertexArray(0)

        GLES30.glClearColor(BACKGROUND[0], BACKGROUND[1], BACKGROUND[2], 1f)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        camera.setViewport(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val snapshot = runner.latestSnapshot()
        camera.setDishRadius(snapshot.dishRadius)
        takeSnapshot(snapshot, now)
        val alpha = if (moveNanos <= 0L) 1f else ((now - moveStartNanos).toFloat() / moveNanos).coerceIn(0f, 1f)
        glide(snapshot, alpha, now)

        val selected = selectedIndex(snapshot, runner.selectedUid)
        if (selected >= 0 && camera.followUid != 0 && camera.followUid == snapshot.cellUids[selected]) {
            val o = selected * WorldSnapshot.CELL_FLOATS
            camera.centerOn(frameCells[o], frameCells[o + 1])
        }
        val cameraMoving = camera.advance(now)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (camera.viewProjection(viewProjection)) {
            val pixelsPerUnit = camera.pixelsPerUnit()
            drawDish(snapshot)
            GLES30.glEnable(GLES30.GL_BLEND)
            drawFood(snapshot, pixelsPerUnit)
            drawBonds(snapshot, pixelsPerUnit)
            drawCells(snapshot, pixelsPerUnit, selected)
            drawEffects(now, pixelsPerUnit)
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glBindVertexArray(0)
        }
        if (alpha < 1f || cameraMoving || effects.busy) requestFrame()
    }

    /**
     * True when a cell, as shown in the last frame, lies within [slack] µm of the world point ([x], [y]).
     * Safe to call from any thread.
     */
    fun cellNear(x: Float, y: Float, slack: Float): Boolean {
        val frame = hitFrame ?: return false
        val cells = frame.cells
        for (i in 0 until frame.count) {
            val o = i * HIT_FLOATS
            if (hypot(cells[o] - x, cells[o + 1] - y) - cells[o + 2] < slack) return true
        }
        return false
    }

    /** A new snapshot starts a new glide: from where each cell is shown now to where the world put it. */
    private fun takeSnapshot(snapshot: WorldSnapshot, now: Long) {
        if (snapshot.serial == seenSerial) return
        effects.take(snapshot, now, effectsOn())
        // Only a real step glides; a command (a tweezers move, a fresh cell) and a long pause jump at once.
        val stepped = seenSerial >= 0 && snapshot.stepCount != seenStep
        val interval = snapshot.publishedNanos - lastPublishedNanos
        val glides = stepped && interval in 1..MAX_GLIDE_NANOS
        seenSerial = snapshot.serial
        seenStep = snapshot.stepCount
        lastPublishedNanos = snapshot.publishedNanos

        val n = snapshot.cellCount
        if (fromValid.size < n) {
            val size = n + n / 2 + 16
            fromX = FloatArray(size)
            fromY = FloatArray(size)
            fromAngle = FloatArray(size)
            fromValid = BooleanArray(size)
        }
        for (i in 0 until n) {
            val same = glides && i < shownCount && shownUids[i] == snapshot.cellUids[i]
            fromValid[i] = same
            if (same) {
                fromX[i] = shownX[i]
                fromY[i] = shownY[i]
                fromAngle[i] = shownAngle[i]
            }
        }
        moveStartNanos = now
        moveNanos = if (glides) interval else 0L
    }

    /**
     * Writes this frame's cell and bond records: positions part of the way ([alpha]) along the glide, newborn cells
     * a little smaller or bigger while they swell.
     */
    private fun glide(snapshot: WorldSnapshot, alpha: Float, now: Long) {
        val n = snapshot.cellCount
        val floats = n * WorldSnapshot.CELL_FLOATS
        if (frameCells.size < floats) frameCells = FloatArray(floats + floats / 2 + WorldSnapshot.CELL_FLOATS * 16)
        System.arraycopy(snapshot.cells, 0, frameCells, 0, floats)
        if (shownUids.size < n) {
            val size = n + n / 2 + 16
            shownX = FloatArray(size)
            shownY = FloatArray(size)
            shownAngle = FloatArray(size)
            shownUids = IntArray(size)
        }
        val hit = hitBuffers[hitTurn]
        if (hit.cells.size < n * HIT_FLOATS) hit.cells = FloatArray((n + n / 2 + 16) * HIT_FLOATS)
        for (i in 0 until n) {
            val o = i * WorldSnapshot.CELL_FLOATS
            if (alpha < 1f && i < fromValid.size && fromValid[i]) {
                frameCells[o] = fromX[i] + (frameCells[o] - fromX[i]) * alpha
                frameCells[o + 1] = fromY[i] + (frameCells[o + 1] - fromY[i]) * alpha
                frameCells[o + 3] = fromAngle[i] + shortestTurn(fromAngle[i], frameCells[o + 3]) * alpha
            }
            if (effects.busy) frameCells[o + 2] *= effects.scale(snapshot.cellUids[i], now)
            shownX[i] = frameCells[o]
            shownY[i] = frameCells[o + 1]
            shownAngle[i] = frameCells[o + 3]
            shownUids[i] = snapshot.cellUids[i]
            hit.cells[i * HIT_FLOATS] = frameCells[o]
            hit.cells[i * HIT_FLOATS + 1] = frameCells[o + 1]
            hit.cells[i * HIT_FLOATS + 2] = frameCells[o + 2]
        }
        shownCount = n
        hit.count = n
        hitFrame = hit
        hitTurn = 1 - hitTurn

        // Bonds follow their cells; a bond to the wall keeps its wall end.
        val bondFloats = snapshot.bondCount * WorldSnapshot.BOND_FLOATS
        if (frameBonds.size < bondFloats) frameBonds = FloatArray(bondFloats + bondFloats / 2 + WorldSnapshot.BOND_FLOATS * 16)
        System.arraycopy(snapshot.bonds, 0, frameBonds, 0, bondFloats)
        for (k in 0 until snapshot.bondCount) {
            val o = k * WorldSnapshot.BOND_FLOATS
            val a = snapshot.bondEnds[2 * k]
            val b = snapshot.bondEnds[2 * k + 1]
            if (a in 0 until n) {
                frameBonds[o] = frameCells[a * WorldSnapshot.CELL_FLOATS]
                frameBonds[o + 1] = frameCells[a * WorldSnapshot.CELL_FLOATS + 1]
            }
            if (b in 0 until n) {
                frameBonds[o + 2] = frameCells[b * WorldSnapshot.CELL_FLOATS]
                frameBonds[o + 3] = frameCells[b * WorldSnapshot.CELL_FLOATS + 1]
            }
        }
    }

    /** The turn from angle [from] to angle [to] the short way round, radians. */
    private fun shortestTurn(from: Float, to: Float): Float {
        var turn = (to - from) % TWO_PI
        if (turn > PI_F) turn -= TWO_PI
        if (turn < -PI_F) turn += TWO_PI
        return turn
    }

    private fun drawDish(snapshot: WorldSnapshot) {
        val radius = snapshot.dishRadius
        GLES30.glUseProgram(dishProgram)
        GLES30.glUniformMatrix3fv(uniform(dishProgram, "uViewProjection"), 1, false, viewProjection, 0)
        GLES30.glUniform1f(uniform(dishProgram, "uRadius"), radius)
        GLES30.glUniform3fv(uniform(dishProgram, "uBackground"), 1, BACKGROUND, 0)
        GLES30.glUniform2f(uniform(dishProgram, "uLightSource"), cos(snapshot.lightAngle), sin(snapshot.lightAngle))
        GLES30.glUniform1f(uniform(dishProgram, "uLightBrightness"), snapshot.lightBrightness)
        GLES30.glUniform1f(uniform(dishProgram, "uLightSigma"), if (radius > 0f) snapshot.lightSigma / radius else 0f)
        val zones = if (radius > 0f) snapshot.zoneCount else 0
        for (k in 0 until zones * WorldSnapshot.ZONE_FLOATS) {
            // Positions and radii in dish radii; the kind stays as it is.
            zoneData[k] = if (k % WorldSnapshot.ZONE_FLOATS == ZONE_KIND) snapshot.zones[k] else snapshot.zones[k] / radius
        }
        GLES30.glUniform1i(uniform(dishProgram, "uZoneCount"), zones)
        if (zones > 0) GLES30.glUniform4fv(uniform(dishProgram, "uZones"), zones, zoneData, 0)
        GLES30.glBindVertexArray(dishArray)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawFood(snapshot: WorldSnapshot, pixelsPerUnit: Float) {
        val count = snapshot.foodCount
        if (count == 0) return
        foodData = upload(foodBuffer, foodData, snapshot.food, count * WorldSnapshot.FOOD_FLOATS)
        GLES30.glUseProgram(foodProgram)
        GLES30.glUniformMatrix3fv(uniform(foodProgram, "uViewProjection"), 1, false, viewProjection, 0)
        GLES30.glUniform1f(uniform(foodProgram, "uPixelsPerUnit"), pixelsPerUnit)
        GLES30.glBindVertexArray(foodArray)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, count)
    }

    private fun drawBonds(snapshot: WorldSnapshot, pixelsPerUnit: Float) {
        val count = snapshot.bondCount
        if (count == 0) return
        bondData = upload(bondBuffer, bondData, frameBonds, count * WorldSnapshot.BOND_FLOATS)
        GLES30.glUseProgram(bondProgram)
        GLES30.glUniformMatrix3fv(uniform(bondProgram, "uViewProjection"), 1, false, viewProjection, 0)
        GLES30.glUniform1f(uniform(bondProgram, "uPixelsPerUnit"), pixelsPerUnit)
        GLES30.glBindVertexArray(bondArray)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, count)
    }

    private fun drawCells(snapshot: WorldSnapshot, pixelsPerUnit: Float, selected: Int) {
        val count = snapshot.cellCount
        if (count == 0) return
        val floats = count * WorldSnapshot.CELL_FLOATS
        cellData = ensure(cellData, floats)
        cellData.clear()
        cellData.put(frameCells, 0, floats)
        if (selected >= 0) {
            val flagsAt = selected * WorldSnapshot.CELL_FLOATS + FLAGS_OFFSET
            cellData.put(flagsAt, frameCells[flagsAt] + SELECTED_FLAG)
        }
        cellData.flip()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, cellBuffer)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floats * Float.SIZE_BYTES, cellData, GLES30.GL_STREAM_DRAW)
        GLES30.glUseProgram(cellProgram)
        GLES30.glUniformMatrix3fv(uniform(cellProgram, "uViewProjection"), 1, false, viewProjection, 0)
        GLES30.glUniform1f(uniform(cellProgram, "uPixelsPerUnit"), pixelsPerUnit)
        GLES30.glBindVertexArray(cellArray)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, count)
    }

    private fun drawEffects(now: Long, pixelsPerUnit: Float) {
        val count = effects.pack(now, effectInstances)
        if (count == 0) return
        effectData = upload(effectBuffer, effectData, effectInstances, count * CellEffects.INSTANCE)
        GLES30.glUseProgram(effectProgram)
        GLES30.glUniformMatrix3fv(uniform(effectProgram, "uViewProjection"), 1, false, viewProjection, 0)
        GLES30.glUniform1f(uniform(effectProgram, "uPixelsPerUnit"), pixelsPerUnit)
        GLES30.glBindVertexArray(effectArray)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, count)
    }

    private fun selectedIndex(snapshot: WorldSnapshot, uid: Int): Int {
        if (uid == 0) return -1
        val uids = snapshot.cellUids
        for (i in 0 until snapshot.cellCount) if (uids[i] == uid) return i
        return -1
    }

    /** Copies [count] floats into the GL buffer through a reusable direct buffer; returns the (maybe grown) buffer. */
    private fun upload(buffer: Int, staging: FloatBuffer, source: FloatArray, count: Int): FloatBuffer {
        val data = ensure(staging, count)
        data.clear()
        data.put(source, 0, count)
        data.flip()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, count * Float.SIZE_BYTES, data, GLES30.GL_STREAM_DRAW)
        return data
    }

    private fun ensure(buffer: FloatBuffer, count: Int): FloatBuffer =
        if (buffer.capacity() >= count) buffer else floats(maxOf(count, buffer.capacity() * 2))

    private fun bindQuad(buffer: Int, offsetVertices: Int) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * Float.SIZE_BYTES, offsetVertices * 2 * Float.SIZE_BYTES)
    }

    /** Per-instance attribute: [size] floats starting [offset] floats into each [stride]-float record. */
    private fun instanced(location: Int, size: Int, stride: Int, offset: Int) {
        GLES30.glEnableVertexAttribArray(location)
        GLES30.glVertexAttribPointer(
            location, size, GLES30.GL_FLOAT, false, stride * Float.SIZE_BYTES, offset * Float.SIZE_BYTES,
        )
        GLES30.glVertexAttribDivisor(location, 1)
    }

    private fun uniform(program: Int, name: String): Int = GLES30.glGetUniformLocation(program, name)

    private companion object {
        /** Two quads in one buffer, as triangle strips: centred (−1..1) and along a segment (0..1 × −1..1). */
        val QUADS = floatArrayOf(
            -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f,
            0f, -1f, 1f, -1f, 0f, 1f, 1f, 1f,
        )
        const val CENTRED_QUAD = 0
        const val SEGMENT_QUAD = 4

        /** Index of the flags inside a cell record, and the bit that marks the cell under diagnostics. */
        const val FLAGS_OFFSET = 10
        const val SELECTED_FLAG = 256f

        /** A glide never takes longer than this; after a longer pause the cells jump to their places. */
        const val MAX_GLIDE_NANOS = 400_000_000L

        /** x, y and radius of each cell for [cellNear]. */
        const val HIT_FLOATS = 3

        /** Place of the kind in a zone's floats (see [WorldSnapshot.zones]). */
        const val ZONE_KIND = 3

        const val PI_F = 3.1415927f
        const val TWO_PI = 6.2831855f

        /** Colour around the dish, RGB 0..1; also the clear colour, so the dish quad has no visible edge. */
        val BACKGROUND = floatArrayOf(0.016f, 0.024f, 0.031f)

        fun floats(count: Int): FloatBuffer =
            ByteBuffer.allocateDirect(count * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }
}
