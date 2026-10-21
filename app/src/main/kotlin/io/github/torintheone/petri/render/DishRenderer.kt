package io.github.torintheone.petri.render

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import io.github.torintheone.petri.sim.SimulationRunner
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Draws the dish: the dark culture medium and the glass wall around it.
 * The dish is one quad; the fragment shader shapes the circle, so its edge stays smooth at any zoom.
 */
class DishRenderer(
    private val runner: SimulationRunner,
    private val camera: DishCamera,
) : GLSurfaceView.Renderer {
    private val viewProjection = FloatArray(9)
    private var program = 0
    private var quadArray = 0
    private var viewProjectionLocation = -1
    private var radiusLocation = -1
    private var backgroundLocation = -1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = GlPrograms.build(VERTEX_SHADER, FRAGMENT_SHADER)
        viewProjectionLocation = GLES30.glGetUniformLocation(program, "uViewProjection")
        radiusLocation = GLES30.glGetUniformLocation(program, "uRadius")
        backgroundLocation = GLES30.glGetUniformLocation(program, "uBackground")

        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        quadArray = ids[0]
        GLES30.glBindVertexArray(quadArray)
        GLES30.glGenBuffers(1, ids, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            QUAD_CORNERS.size * Float.SIZE_BYTES,
            GlPrograms.floatBuffer(QUAD_CORNERS),
            GLES30.GL_STATIC_DRAW,
        )
        GLES30.glEnableVertexAttribArray(CORNER_ATTRIBUTE)
        GLES30.glVertexAttribPointer(CORNER_ATTRIBUTE, 2, GLES30.GL_FLOAT, false, 2 * Float.SIZE_BYTES, 0)
        GLES30.glBindVertexArray(0)

        GLES30.glClearColor(BACKGROUND[0], BACKGROUND[1], BACKGROUND[2], 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        camera.setViewport(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val snapshot = runner.latestSnapshot()
        camera.setDishRadius(snapshot.dishRadius)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (!camera.viewProjection(viewProjection)) return

        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix3fv(viewProjectionLocation, 1, false, viewProjection, 0)
        GLES30.glUniform1f(radiusLocation, snapshot.dishRadius)
        GLES30.glUniform3fv(backgroundLocation, 1, BACKGROUND, 0)
        GLES30.glBindVertexArray(quadArray)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, QUAD_CORNERS.size / 2)
        GLES30.glBindVertexArray(0)
    }

    private companion object {
        const val CORNER_ATTRIBUTE = 0

        /** Corners of a square from -1 to 1, as a triangle strip. */
        val QUAD_CORNERS = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

        /** Colour around the dish, RGB 0..1; also the clear colour, so the quad has no visible edge. */
        val BACKGROUND = floatArrayOf(0.016f, 0.024f, 0.031f)

        // Quad corners are scaled to 1.1 dish radii so the wall and its halo fit inside.
        val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec2 aCorner;
            uniform mat3 uViewProjection;
            uniform float uRadius;
            out vec2 vLocal;

            const float EXTENT = 1.1;

            void main() {
                vLocal = aCorner * EXTENT;
                vec3 clip = uViewProjection * vec3(vLocal * uRadius, 1.0);
                gl_Position = vec4(clip.xy, 0.0, 1.0);
            }
        """.trimIndent()

        // vLocal is measured in dish radii: the medium fills d < 1, the glass wall sits just outside.
        val FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;

            in vec2 vLocal;
            uniform vec3 uBackground;
            out vec4 fragColor;

            const vec3 MEDIUM_CENTER = vec3(0.050, 0.098, 0.118);
            const vec3 MEDIUM_EDGE = vec3(0.027, 0.058, 0.074);
            const vec3 WALL_SHADE = vec3(0.180, 0.260, 0.300);
            const vec3 WALL_LIGHT = vec3(0.640, 0.800, 0.860);
            const vec2 LIGHT_DIRECTION = vec2(-0.447, 0.894);
            const float WALL_WIDTH = 0.016;
            const float HALO_WIDTH = 0.06;
            const float HALO_STRENGTH = 0.12;

            void main() {
                float d = length(vLocal);
                float pixel = max(fwidth(d), 1e-6);
                float wall = max(WALL_WIDTH, 1.5 * pixel);

                vec3 color = uBackground;

                float inside = 1.0 - smoothstep(1.0 - pixel, 1.0, d);
                color = mix(color, mix(MEDIUM_CENTER, MEDIUM_EDGE, d * d), inside);

                float ring = smoothstep(1.0 - pixel, 1.0, d) * (1.0 - smoothstep(1.0 + wall - pixel, 1.0 + wall, d));
                vec2 normal = vLocal / max(d, 1e-6);
                float shine = pow(max(dot(normal, LIGHT_DIRECTION), 0.0), 3.0);
                color = mix(color, mix(WALL_SHADE, WALL_LIGHT, shine), ring);

                float halo = smoothstep(1.0 + wall - pixel, 1.0 + wall, d)
                    * (1.0 - smoothstep(1.0 + wall, 1.0 + wall + HALO_WIDTH, d));
                color += WALL_SHADE * HALO_STRENGTH * halo;

                fragColor = vec4(color, 1.0);
            }
        """.trimIndent()
    }
}
