package io.github.torintheone.petri.sim

import kotlin.math.hypot
import kotlin.math.roundToInt

/** One picture of the growth preview: every cell and bond, plus the middle and size of the organism. */
class PreviewFrame(
    val hour: Int,
    val cellCount: Int,
    /** [CELL_FLOATS] per cell: x, y, radius, angle, red, green, blue, type, mode, mirrored (0 or 1). */
    val cells: FloatArray,
    val bondCount: Int,
    /** [BOND_FLOATS] per bond: x1, y1, x2, y2. */
    val bonds: FloatArray,
) {
    /** Middle of the organism, µm. */
    val centerX: Float
    val centerY: Float

    /** Distance from the middle to the farthest cell edge, µm. */
    val extent: Float

    init {
        var sx = 0f
        var sy = 0f
        for (i in 0 until cellCount) {
            sx += cells[i * CELL_FLOATS]
            sy += cells[i * CELL_FLOATS + 1]
        }
        centerX = if (cellCount > 0) sx / cellCount else 0f
        centerY = if (cellCount > 0) sy / cellCount else 0f
        var far = 0f
        for (i in 0 until cellCount) {
            val o = i * CELL_FLOATS
            far = maxOf(far, hypot(cells[o] - centerX, cells[o + 1] - centerY) + cells[o + 2])
        }
        extent = far
    }

    fun x(i: Int): Float = cells[i * CELL_FLOATS]

    fun y(i: Int): Float = cells[i * CELL_FLOATS + 1]

    fun radius(i: Int): Float = cells[i * CELL_FLOATS + 2]

    fun angle(i: Int): Float = cells[i * CELL_FLOATS + 3]

    fun type(i: Int): CellType = CellType.of(cells[i * CELL_FLOATS + 7].toInt())

    fun mode(i: Int): Int = cells[i * CELL_FLOATS + 8].toInt()

    /** The cell under a point, or −1. */
    fun cellAt(px: Float, py: Float, slack: Float = 0f): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (i in 0 until cellCount) {
            val d = hypot(x(i) - px, y(i) - py) - radius(i)
            if (d < slack && d < bestDistance) {
                best = i
                bestDistance = d
            }
        }
        return best
    }

    companion object {
        const val CELL_FLOATS = 10
        const val BOND_FLOATS = 4
    }
}

/**
 * The growth preview of the genome editor (SPEC «Геном»): a separate small world where one cell of the genome,
 * front pointing up, grows with endless food and light up to [SimConfig.PREVIEW_MAX_CELLS] cells.
 * Deterministic: the same genome always grows the same way.
 */
class GrowthPreview(genome: Genome) {
    private val world = World(
        SETTINGS,
        SimConfig.PREVIEW_SEED,
        cellCapacity = PREVIEW_CAPACITY,
        bondCapacity = PREVIEW_CAPACITY * Mode.MAX_BONDS / 2,
        foodCapacity = 1,
    ).apply {
        previewGrowth = SimConfig.PREVIEW_GROWTH_PER_HOUR
        previewCellLimit = SimConfig.PREVIEW_MAX_CELLS
    }

    /** Game hours simulated so far. */
    var hour = 0
        private set

    init {
        world.addCell(genome.sanitized(), 0f, 0f, angle = World.PI_F / 2f, mass = SimConfig.SYNTHESIZED_MASS)
    }

    fun advanceHour() {
        repeat(STEPS_PER_HOUR) { world.step() }
        hour++
    }

    fun frame(): PreviewFrame {
        val c = world.cells
        val n = c.count
        val cells = FloatArray(n * PreviewFrame.CELL_FLOATS)
        for (i in 0 until n) {
            val mode = world.modeOf(i)
            val o = i * PreviewFrame.CELL_FLOATS
            cells[o] = c.x[i]
            cells[o + 1] = c.y[i]
            cells[o + 2] = world.radiusOf(c.mass[i])
            cells[o + 3] = c.angle[i]
            cells[o + 4] = mode.red
            cells[o + 5] = mode.green
            cells[o + 6] = mode.blue
            cells[o + 7] = c.type[i].toFloat()
            cells[o + 8] = c.mode[i].toFloat()
            cells[o + 9] = if (c.isMirrored(i)) 1f else 0f
        }
        val b = world.bonds
        val bonds = FloatArray(b.liveCount * PreviewFrame.BOND_FLOATS)
        var m = 0
        for (id in 0 until b.size) {
            if (!b.alive[id] || b.b[id] == Bonds.WALL) continue
            val o = m * PreviewFrame.BOND_FLOATS
            bonds[o] = c.x[b.a[id]]
            bonds[o + 1] = c.y[b.a[id]]
            bonds[o + 2] = c.x[b.b[id]]
            bonds[o + 3] = c.y[b.b[id]]
            m++
        }
        return PreviewFrame(hour, n, cells, m, bonds)
    }

    companion object {
        val STEPS_PER_HOUR = (1.0 / SimConfig.STEP_HOURS).roundToInt()
        /** Cells the preview world has room for: the limit plus a margin for the step that reaches it. */
        private const val PREVIEW_CAPACITY = SimConfig.PREVIEW_MAX_CELLS * 2

        /** A calm dish: no food or light of its own (cells grow anyway), no aging, no deadly walls. */
        private val SETTINGS = SubstrateSettings(
            dishDiameterMm = SimConfig.PREVIEW_DISH_DIAMETER_MM,
            maxCells = SubstrateSettings.MAX_CELLS.first,
            maxFood = SubstrateSettings.MAX_FOOD.first,
            foodRate = 0f,
            lightBrightness = 0f,
            aging = false,
            deadlyWalls = false,
            corpsesBecomeFood = false,
            radiation = 0f,
            randomInfection = false,
        )

        /** Grows [genome] for [hours] and returns a picture for every hour from 0. */
        fun frames(genome: Genome, hours: Int = SimConfig.PREVIEW_HOURS): List<PreviewFrame> {
            val preview = GrowthPreview(genome)
            return buildList {
                add(preview.frame())
                repeat(hours - 1) {
                    preview.advanceHour()
                    add(preview.frame())
                }
            }
        }

        /** A small picture for the genome bank: the organism once it is grown or after [maxHours]. */
        fun thumbnail(genome: Genome, maxHours: Int = SimConfig.THUMBNAIL_MAX_HOURS): PreviewFrame {
            val preview = GrowthPreview(genome)
            while (preview.hour < maxHours && preview.world.cells.count < SimConfig.PREVIEW_MAX_CELLS) preview.advanceHour()
            return preview.frame()
        }
    }
}
