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
import kotlin.math.sin

/**
 * Draws the dish from the latest simulation snapshot: medium and light, food, bonds, then cells.
 * All cells go out in one instanced draw call; their look is computed in the fragment shader (see [Shaders]).
 */
class DishRenderer(
    private val runner: SimulationRunner,
    private val camera: DishCamera,
) : GLSurfaceView.Renderer {
    private val viewProjection = FloatArray(9)

    private var dishProgram = 0
    private var foodProgram = 0
    private var bondProgram = 0
    private var cellProgram = 0

    private var dishArray = 0
    private var foodArray = 0
    private var bondArray = 0
    private var cellArray = 0
    private var foodBuffer = 0
    private var bondBuffer = 0
    private var cellBuffer = 0

    private var foodData = floats(256)
    private var bondData = floats(256)
    private var cellData = floats(256)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        dishProgram = GlPrograms.build(Shaders.DISH_VERTEX, Shaders.DISH_FRAGMENT)
        foodProgram = GlPrograms.build(Shaders.FOOD_VERTEX, Shaders.FOOD_FRAGMENT)
        bondProgram = GlPrograms.build(Shaders.BOND_VERTEX, Shaders.BOND_FRAGMENT)
        cellProgram = GlPrograms.build(Shaders.CELL_VERTEX, Shaders.CELL_FRAGMENT)

        val ids = IntArray(4)
        GLES30.glGenVertexArrays(4, ids, 0)
        dishArray = ids[0]
        foodArray = ids[1]
        bondArray = ids[2]
        cellArray = ids[3]
        GLES30.glGenBuffers(4, ids, 0)
        val quadBuffer = ids[0]
        foodBuffer = ids[1]
        bondBuffer = ids[2]
        cellBuffer = ids[3]

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
        val snapshot = runner.latestSnapshot()
        camera.setDishRadius(snapshot.dishRadius)
        val selected = selectedIndex(snapshot, runner.selectedUid)
        if (selected >= 0 && camera.followUid != 0 && camera.followUid == snapshot.cellUids[selected]) {
            val o = selected * WorldSnapshot.CELL_FLOATS
            camera.centerOn(snapshot.cells[o], snapshot.cells[o + 1])
        }
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (!camera.viewProjection(viewProjection)) return
        val pixelsPerUnit = camera.pixelsPerUnit()

        drawDish(snapshot)
        GLES30.glEnable(GLES30.GL_BLEND)
        drawFood(snapshot, pixelsPerUnit)
        drawBonds(snapshot, pixelsPerUnit)
        drawCells(snapshot, pixelsPerUnit, selected)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindVertexArray(0)
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
        bondData = upload(bondBuffer, bondData, snapshot.bonds, count * WorldSnapshot.BOND_FLOATS)
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
        cellData.put(snapshot.cells, 0, floats)
        if (selected >= 0) {
            val flagsAt = selected * WorldSnapshot.CELL_FLOATS + FLAGS_OFFSET
            cellData.put(flagsAt, snapshot.cells[flagsAt] + SELECTED_FLAG)
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

        /** Colour around the dish, RGB 0..1; also the clear colour, so the dish quad has no visible edge. */
        val BACKGROUND = floatArrayOf(0.016f, 0.024f, 0.031f)

        fun floats(count: Int): FloatBuffer =
            ByteBuffer.allocateDirect(count * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }
}
