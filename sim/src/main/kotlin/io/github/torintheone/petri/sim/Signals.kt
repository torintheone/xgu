package io.github.torintheone.petri.sim

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Signals S1–S4 (SPEC «Сигналы»): sources inject into their own cell, the substances even out along bonds
 * (lipocytes insulate) and decay over time. Also the cells that act on signals or on the medium around them:
 * senseocytes and stereocytes, neurocytes, stemocytes and secrocytes.
 *
 * Runs after movement, when both neighbour grids are fresh; the new signals drive the next step's behaviour.
 */
internal class Signals(private val world: World) {
    /** What each cell puts into each channel this step, before clamping to ±1. */
    private val injection = FloatArray(world.cells.capacity * Cells.SIGNALS)

    /** Signal levels before this step's exchange along bonds. */
    private val before = FloatArray(world.cells.capacity * Cells.SIGNALS)

    fun run() {
        val c = world.cells
        val n = c.count
        if (n == 0) return
        java.util.Arrays.fill(injection, 0, n * Cells.SIGNALS, 0f)
        for (i in 0 until n) {
            if (world.isDead(i)) continue
            when (c.type[i]) {
                SENSEOCYTE -> sensor(i, lateral = false)
                STEREOCYTE -> sensor(i, lateral = true)
                NEUROCYTE -> neuron(i)
                SECROCYTE -> secrete(i)
            }
        }
        exchange()
        decay(n)
        for (i in 0 until n) if (c.type[i] == STEMOCYTE && !world.isDead(i)) stem(i)
    }

    // ---- Spreading ----

    /**
     * Each bond moves its two cells' levels towards each other. A cell's share of a bond is divided by its number of
     * bonds, so every cell keeps a stable average of its neighbours and a busy cell dilutes its own output.
     */
    private fun exchange() {
        val c = world.cells
        val b = world.bonds
        val n = c.count
        System.arraycopy(c.signals, 0, before, 0, n * Cells.SIGNALS)
        val rate = SimConfig.SIGNAL_SHARE_PER_STEP
        for (id in 0 until b.size) {
            if (!b.alive[id]) continue
            val i = b.a[id]
            val j = b.b[id]
            if (j == Bonds.WALL || c.type[i] == LIPOCYTE || c.type[j] == LIPOCYTE) continue
            val share = rate / max(c.bondCount[i], c.bondCount[j])
            val oi = i * Cells.SIGNALS
            val oj = j * Cells.SIGNALS
            for (k in 0 until Cells.SIGNALS) {
                val flow = share * (before[oj + k] - before[oi + k])
                c.signals[oi + k] += flow
                c.signals[oj + k] -= flow
            }
        }
    }

    /** Sources pull their level towards their output; every level relaxes towards it at the same rate. */
    private fun decay(n: Int) {
        val c = world.cells
        val keep = SimConfig.SIGNAL_DECAY_PER_HOUR * SimConfig.STEP_HOURS_F
        for (k in 0 until n * Cells.SIGNALS) {
            val target = injection[k].coerceIn(-1f, 1f)
            c.signals[k] += keep * (target - c.signals[k])
        }
    }

    private fun inject(i: Int, channel: SignalChannel, amount: Float) {
        injection[i * Cells.SIGNALS + channel.ordinal] += amount
    }

    // ---- Senses ----

    /**
     * Senseocyte (lateral = false): how strongly the target is around. Stereocyte (lateral = true): the same,
     * signed by side: targets on the cell's left give plus, on its right minus (see DECISIONS.md).
     */
    private fun sensor(i: Int, lateral: Boolean) {
        val mode = world.modeOf(i)
        val strength = senseStrength(i, mode, lateral)
        inject(i, mode.senseChannel, mode.senseGain * SimConfig.SENSE_GAIN_MAX * strength)
    }

    private fun senseStrength(i: Int, mode: Mode, lateral: Boolean): Float {
        val c = world.cells
        val x = c.x[i]
        val y = c.y[i]
        // The cell's left: its front turned 90° counter-clockwise.
        val lx = -sin(c.angle[i])
        val ly = cos(c.angle[i])
        val radius = SimConfig.SENSE_RADIUS_UM
        var total = 0f

        fun add(ox: Float, oy: Float, weight: Float) {
            val dx = ox - x
            val dy = oy - y
            val d2 = dx * dx + dy * dy
            if (d2 > radius * radius || d2 < 1e-6f) return
            val scale = SimConfig.SENSE_SCALE_UM
            val w = weight / (1f + d2 / (scale * scale))
            total += if (lateral) w * (dx * lx + dy * ly) / sqrt(d2) else w
        }

        when (mode.senseTarget) {
            SenseTarget.CELLS -> world.physics.grid.forEachNear(x, y, radius) { j ->
                if (j != i && j < c.count && !world.isDead(j)) {
                    val other = world.modeOf(j)
                    val difference = (abs(other.red - mode.senseRed) + abs(other.green - mode.senseGreen) + abs(other.blue - mode.senseBlue)) / 3f
                    if (difference <= mode.senseColorThreshold) add(c.x[j], c.y[j], 1f)
                }
            }
            SenseTarget.FOOD, SenseTarget.COATED_FOOD -> {
                val coated = mode.senseTarget == SenseTarget.COATED_FOOD
                val food = world.food
                world.foodGrid.forEachNear(x, y, radius) { k ->
                    if (k < food.count && !food.eaten[k] && food.coated[k] == coated) add(food.x[k], food.y[k], 1f)
                }
                // Secrocytes that give off the smell of food count as food.
                if (!coated) {
                    world.physics.grid.forEachNear(x, y, radius) { j ->
                        if (j != i && j < c.count && c.type[j] == SECROCYTE && c.nitrates[j] > 0f) {
                            val other = world.modeOf(j)
                            if (other.secretion == Substance.FOOD_SMELL) add(c.x[j], c.y[j], other.secretionIntensity * SimConfig.FOOD_SMELL_STRENGTH)
                        }
                    }
                }
            }
            SenseTarget.LIGHT -> {
                val light = world.lightAt(x, y)
                total = if (lateral) {
                    val dx = world.dishRadius * cos(world.lightAngle) - x
                    val dy = world.dishRadius * sin(world.lightAngle) - y
                    val d = sqrt(dx * dx + dy * dy)
                    if (d > 1e-3f) light * (dx * lx + dy * ly) / d else 0f
                } else {
                    light
                }
            }
            SenseTarget.SPEED -> {
                val vx = c.vx[i]
                val vy = c.vy[i]
                total = if (lateral) (vx * lx + vy * ly) / SimConfig.SPEED_SENSE_SCALE else sqrt(vx * vx + vy * vy) / SimConfig.SPEED_SENSE_SCALE
            }
            SenseTarget.WALL -> {
                val d = sqrt(x * x + y * y)
                val gap = max(0f, world.dishRadius - d - c.radius[i])
                val scale = SimConfig.SENSE_SCALE_UM
                val closeness = 1f / (1f + gap * gap / (scale * scale))
                total = if (lateral && d > 1e-3f) closeness * (x * lx + y * ly) / d else closeness
            }
        }
        return total
    }

    // ---- Neurocyte ----

    /** Four programmable outputs, one per channel, plus the oscillator: a sine into channel A and its opposite into B. */
    private fun neuron(i: Int) {
        val mode = world.modeOf(i)
        val behaviors = world.behaviors
        inject(i, SignalChannel.S1, behaviors.value(i, mode.neuronOutput1))
        inject(i, SignalChannel.S2, behaviors.value(i, mode.neuronOutput2))
        inject(i, SignalChannel.S3, behaviors.value(i, mode.neuronOutput3))
        inject(i, SignalChannel.S4, behaviors.value(i, mode.neuronOutput4))
        if (mode.oscillatorFrequency > 0f) {
            val cyclesPerHour = mode.oscillatorFrequency * SimConfig.OSCILLATOR_MAX_CYCLES_PER_HOUR
            val wave = sin(World.TWO_PI * cyclesPerHour * world.cells.age[i])
            inject(i, mode.oscillatorChannelA, wave)
            inject(i, mode.oscillatorChannelB, -wave)
        }
    }

    // ---- Stemocyte ----

    /** Switches to a path's mode, without dividing, as soon as that path's value exceeds the threshold. */
    private fun stem(i: Int) {
        val mode = world.modeOf(i)
        val behaviors = world.behaviors
        val target = when {
            behaviors.value(i, mode.path1Signal) > SimConfig.STEMOCYTE_THRESHOLD -> mode.path1Mode
            behaviors.value(i, mode.path2Signal) > SimConfig.STEMOCYTE_THRESHOLD -> mode.path2Mode
            else -> return
        }
        if (target != world.cells.mode[i]) world.setMode(i, target)
    }

    // ---- Secrocyte ----

    /**
     * Gives off its substance around itself while it has nitrates: the smell of food (sensed as food), cyanide
     * (drains unprotected cells of other organisms), a signal (raises a channel of nearby unprotected cells) or
     * lipase (strips the coat off food and dissolves other organisms' unprotected lipocytes into food).
     */
    private fun secrete(i: Int) {
        val c = world.cells
        if (c.nitrates[i] <= 0f) return
        val mode = world.modeOf(i)
        val intensity = mode.secretionIntensity
        if (intensity <= 0f || mode.secretion == Substance.FOOD_SMELL) return
        val x = c.x[i]
        val y = c.y[i]
        val reach = c.radius[i] + SimConfig.SECRETION_RADIUS_UM
        val dt = SimConfig.STEP_HOURS_F
        val physics = world.physics
        when (mode.secretion) {
            Substance.CYANIDE -> physics.grid.forEachNear(x, y, reach + physics.maxRadius) { j ->
                if (j != i && j < c.count && reaches(i, j, reach) && c.organism[j] != c.organism[i] && !isProtected(j)) {
                    c.mass[j] -= intensity * SimConfig.CYANIDE_DRAIN_PER_HOUR * dt
                    if (c.mass[j] < SimConfig.MIN_MASS && world.previewGrowth < 0f) world.kill(j)
                }
            }
            Substance.SIGNAL -> physics.grid.forEachNear(x, y, reach + physics.maxRadius) { j ->
                if (j < c.count && reaches(i, j, reach) && (j == i || !isProtected(j))) inject(j, mode.secretionChannel, intensity)
            }
            Substance.LIPASE -> {
                val food = world.food
                world.foodGrid.forEachNear(x, y, reach) { k ->
                    if (k < food.count && food.coated[k]) {
                        val dx = food.x[k] - x
                        val dy = food.y[k] - y
                        if (dx * dx + dy * dy < reach * reach && world.rng.chance(intensity * SimConfig.LIPASE_PER_HOUR * dt)) food.coated[k] = false
                    }
                }
                if (world.previewGrowth >= 0f) return
                physics.grid.forEachNear(x, y, reach + physics.maxRadius) { j ->
                    if (j != i && j < c.count && c.type[j] == LIPOCYTE && reaches(i, j, reach) &&
                        c.organism[j] != c.organism[i] && !isProtected(j) && world.rng.chance(intensity * SimConfig.LIPASE_PER_HOUR * dt)
                    ) {
                        c.flags[j] = c.flags[j] or Cells.FLAG_ALL_TO_FOOD
                        world.kill(j)
                    }
                }
            }
            Substance.FOOD_SMELL -> Unit
        }
    }

    /** True when cell j's surface is within [reach] of cell i's centre. */
    private fun reaches(i: Int, j: Int, reach: Float): Boolean {
        val c = world.cells
        val dx = c.x[j] - c.x[i]
        val dy = c.y[j] - c.y[i]
        val limit = reach + c.radius[j]
        return dx * dx + dy * dy < limit * limit
    }

    private fun isProtected(j: Int): Boolean = world.cells.flags[j] and Cells.FLAG_PROTECTED != 0

    private fun abs(v: Float): Float = if (v < 0f) -v else v

    private companion object {
        val SENSEOCYTE = CellType.SENSEOCYTE.ordinal
        val STEREOCYTE = CellType.STEREOCYTE.ordinal
        val NEUROCYTE = CellType.NEUROCYTE.ordinal
        val STEMOCYTE = CellType.STEMOCYTE.ordinal
        val SECROCYTE = CellType.SECROCYTE.ordinal
        val LIPOCYTE = CellType.LIPOCYTE.ordinal
    }
}
