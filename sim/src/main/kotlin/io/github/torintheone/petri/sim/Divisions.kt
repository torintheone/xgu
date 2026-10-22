package io.github.torintheone.petri.sim

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Cell division (SPEC «Условия деления», «Геометрия деления»).
 *
 * The split plane runs through the centre at «Угол деления» to the front; child 1 lands on one side, child 2 on the other,
 * just touching, with the mass shared by «Соотношение деления». Each bond of the mother goes to the child on whose side
 * its attachment point lies, unless that child does not keep bonds. «Зеркально» makes a child count every angle
 * clockwise: its own turn and all angles of its future divisions.
 */
internal class Divisions(private val world: World) {
    private val inherited = IntArray(Mode.MAX_BONDS)

    fun run() {
        val c = world.cells
        val limit = world.cellLimit
        val parents = c.count
        for (i in 0 until parents) {
            if (world.isDead(i)) continue
            val mode = world.modeOf(i)
            if (!ready(i, mode)) continue
            if (c.count >= limit || c.count >= c.capacity) return
            divide(i, mode)
        }
    }

    private fun ready(i: Int, mode: Mode): Boolean {
        val c = world.cells
        if (mode.cellType == CellType.GAMETE || mode.neverSplits) return false
        if (c.age[i] < SimConfig.MIN_SPLIT_AGE_HOURS) return false
        if (!mode.splitsByAge && c.mass[i] < mode.splitMass) return false
        if (world.previewGrowth < 0f && c.nitrates[i] < SimConfig.NITRATE_DIVISION_COST) return false
        return c.bondCount[i] < mode.maxBonds
    }

    /** Divides the cell in slot i; child 1 keeps the slot, child 2 gets a new one. */
    fun divide(i: Int, mode: Mode) {
        val c = world.cells
        val hand = if (c.isMirrored(i)) -1f else 1f
        val theta = c.angle[i]
        val towardsChild1 = theta + hand * (radians(mode.splitAngle) - HALF_PI)
        val nx = cos(towardsChild1)
        val ny = sin(towardsChild1)

        val motherRadius = world.radiusOf(c.mass[i])
        val mass = c.mass[i] - max(0f, mode.cytoskeleton - 0.5f) * SimConfig.CYTOSKELETON_DIVISION_COST
        if (mass <= 0f) {
            world.kill(i)
            return
        }
        val m1 = mass * mode.splitRatio
        val m2 = mass - m1
        val reach = world.radiusOf(max(m1, 0f)) + world.radiusOf(max(m2, 0f))
        val px = c.x[i]
        val py = c.y[i]
        val x1 = px + nx * reach * m2 / mass
        val y1 = py + ny * reach * m2 / mass
        val x2 = px - nx * reach * m1 / mass
        val y2 = py - ny * reach * m1 / mass

        val mirrored = c.isMirrored(i)
        val angle1 = theta + hand * (if (mode.child1Mirror) -1f else 1f) * radians(mode.child1Angle)
        val angle2 = theta + hand * (if (mode.child2Mirror) -1f else 1f) * radians(mode.child2Angle)
        val nitrates = if (world.previewGrowth >= 0f) 1f else max(0f, c.nitrates[i] - SimConfig.NITRATE_DIVISION_COST) / 2f

        val j = world.spawnCell(
            genomeSlot = c.genome[i],
            mode = mode.child2Mode,
            px = x2,
            py = y2,
            angle = World.wrapPi(angle2),
            mass = m2,
            organism = c.organism[i],
            mirrored = mirrored xor mode.child2Mirror,
            nitrates = nitrates,
        )
        if (j < 0) return
        c.flags[j] = c.flags[j] or (c.flags[i] and Cells.FLAG_PLAYER)
        c.vx[j] = c.vx[i]
        c.vy[j] = c.vy[i]
        c.spin[j] = c.spin[i]
        System.arraycopy(c.signals, i * Cells.SIGNALS, c.signals, j * Cells.SIGNALS, Cells.SIGNALS)

        c.x[i] = x1
        c.y[i] = y1
        c.angle[i] = World.wrapPi(angle1)
        c.mass[i] = m1
        c.age[i] = 0f
        c.nitrates[i] = nitrates
        c.flags[i] = if (mirrored xor mode.child1Mirror) {
            c.flags[i] or Cells.FLAG_MIRRORED
        } else {
            c.flags[i] and Cells.FLAG_MIRRORED.inv()
        }
        world.setMode(i, mode.child1Mode)

        inheritBonds(i, j, nx, ny, theta, mode)
        world.recordDivision(px, py, motherRadius, mode, c.uid[i], c.uid[j])

        if (mode.makeAdhesin) {
            val line = atan2(y2 - y1, x2 - x1)
            world.addBond(
                i, j,
                anchorI = World.wrapPi(line - c.angle[i]),
                anchorJ = World.wrapPi(line + World.PI_F - c.angle[j]),
                rest = mode.adhesinLength * SimConfig.ADHESIN_GAP_MAX_UM,
                stiffness = mode.adhesinStiffness,
                kind = Bonds.KIND_ADHESION,
            )
        } else {
            // A child that left with no bond starts an organism of its own.
            if (c.bondCount[j] == 0) {
                c.organism[j] = world.nextOrganism++
            } else if (c.bondCount[i] == 0) {
                c.organism[i] = world.nextOrganism++
            }
        }

        if (m1 < SimConfig.MIN_MASS) world.kill(i)
        if (m2 < SimConfig.MIN_MASS) world.kill(j)
    }

    private fun inheritBonds(i: Int, j: Int, nx: Float, ny: Float, motherAngle: Float, mode: Mode) {
        val c = world.cells
        val b = world.bonds
        val count = c.bondCount[i]
        System.arraycopy(c.bondIds, i * Mode.MAX_BONDS, inherited, 0, count)
        for (k in 0 until count) {
            val id = inherited[k]
            val isA = b.a[id] == i
            val anchor = if (isA) b.anchorA[id] else b.anchorB[id]
            val pointAngle = motherAngle + anchor
            val toChild1 = cos(pointAngle) * nx + sin(pointAngle) * ny >= 0f
            if (!(if (toChild1) mode.child1KeepBonds else mode.child2KeepBonds)) {
                world.removeBond(id)
                continue
            }
            val child = if (toChild1) i else j
            if (child == j) {
                world.detachBond(i, id)
                if (isA) b.a[id] = j else b.b[id] = j
                world.attachBond(j, id)
            }
            // The attachment point keeps the direction it had on the mother, so the genome's angles,
            // not the momentary positions of the neighbours, decide the organism's shape.
            val kept = World.wrapPi(pointAngle - c.angle[child])
            if (isA) b.anchorA[id] = kept else b.anchorB[id] = kept
        }
    }

    private companion object {
        const val HALF_PI = 1.5707964f
        const val DEGREES_TO_RADIANS = 0.017453292f

        fun radians(degrees: Float): Float = degrees * DEGREES_TO_RADIANS
    }
}
