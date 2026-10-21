package io.github.torintheone.petri.sim

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Forces and motion (SPEC «Физика и обмен веществ»): elastic disks, adhesion bonds with oriented attachment points,
 * the dish wall, gravity and buoyancy, drag and friction, flagella, cilia and tweezers.
 *
 * Integration is semi-implicit with [SimConfig.PHYSICS_SUBSTEPS] substeps. Drag and every spring's own stiffness
 * are taken implicitly (each cell solves a 2×2 system, partners' velocities are taken from the previous substep),
 * so crowded colonies with many stiff bonds stay stable, while a whole organism still moves freely.
 */
internal class Physics(private val world: World) {
    /** Cells by position, as of the last substep. */
    val grid = SpatialGrid()

    /** Largest cell radius in the last substep, µm. */
    var maxRadius = 1f
        private set

    /** Cells that touched the wall during the last step. */
    val touchingWall = BooleanArray(SimConfig.CELL_CAPACITY)

    private var dragged = -1

    fun run() {
        val count = world.cells.count
        if (count == 0) {
            rebuildGrid()
            return
        }
        java.util.Arrays.fill(touchingWall, 0, count, false)
        dragged = world.indexOfUid(world.draggedUid)
        val h = STEP_MINUTES / SimConfig.PHYSICS_SUBSTEPS
        repeat(SimConfig.PHYSICS_SUBSTEPS) { substep(h) }
    }

    /** Recomputes radii and rebuilds the grid from the current positions. */
    fun rebuildGrid() {
        val c = world.cells
        var maxR = 1f
        for (i in 0 until c.count) {
            val r = world.radiusOf(c.mass[i])
            c.radius[i] = r
            if (r > maxR) maxR = r
        }
        maxRadius = maxR
        grid.build(c.x, c.y, c.count, 2f * maxR)
    }

    private fun substep(h: Float) {
        val c = world.cells
        val n = c.count
        if (n == 0) return
        for (i in 0 until n) {
            c.fx[i] = 0f
            c.fy[i] = 0f
            c.torque[i] = 0f
            c.kxx[i] = 0f
            c.kxy[i] = 0f
            c.kyy[i] = 0f
            c.kvx[i] = 0f
            c.kvy[i] = 0f
            c.turnStiffness[i] = 0f
            c.turnTarget[i] = 0f
        }
        rebuildGrid()
        bodyForces(n)
        contacts(n)
        bondForces()
        wall(n)
        integrate(n, h)
    }

    /** Records a spring of stiffness k along unit vector (nx, ny) acting on cell i, whose other end moves at (vx, vy). */
    private fun implicitSpring(i: Int, k: Float, nx: Float, ny: Float, vx: Float, vy: Float) {
        val c = world.cells
        c.kxx[i] += k * nx * nx
        c.kxy[i] += k * nx * ny
        c.kyy[i] += k * ny * ny
        val along = k * (nx * vx + ny * vy)
        c.kvx[i] += along * nx
        c.kvy[i] += along * ny
    }

    private fun bodyForces(n: Int) {
        val c = world.cells
        val gravity = world.settings.gravity * SimConfig.GRAVITY_ACCELERATION
        for (i in 0 until n) {
            val type = c.type[i]
            if (gravity > 0f) {
                val density = if (type == BUOYOCYTE) c.activity[i] else SimConfig.CELL_DENSITY
                c.fy[i] -= gravity * (density - world.mediumDensity(c.y[i])) * c.mass[i]
            }
            if (type == FLAGELLOCYTE) {
                val force = c.activity[i] * SimConfig.FLAGELLUM_FORCE
                if (force != 0f) {
                    c.fx[i] += force * cos(c.angle[i])
                    c.fy[i] += force * sin(c.angle[i])
                }
            }
        }
        val d = dragged
        if (d >= 0) {
            val k = c.mass[d] * SimConfig.TWEEZERS_STIFFNESS
            c.fx[d] += k * (world.dragX - c.x[d]) - c.mass[d] * SimConfig.TWEEZERS_DAMPING * c.vx[d]
            c.fy[d] += k * (world.dragY - c.y[d]) - c.mass[d] * SimConfig.TWEEZERS_DAMPING * c.vy[d]
            c.kxx[d] += k
            c.kyy[d] += k
        }
    }

    private fun contacts(n: Int) {
        val c = world.cells
        val reach = maxRadius
        for (i in 0 until n) {
            grid.forEachNear(c.x[i], c.y[i], c.radius[i] + reach) { j ->
                if (j > i) collide(i, j)
            }
        }
    }

    private fun collide(i: Int, j: Int) {
        val c = world.cells
        val dx = c.x[j] - c.x[i]
        val dy = c.y[j] - c.y[i]
        val reach = c.radius[i] + c.radius[j]
        val d2 = dx * dx + dy * dy
        if (d2 >= reach * reach) return
        var d = sqrt(d2)
        val nx: Float
        val ny: Float
        if (d > 1e-4f) {
            nx = dx / d
            ny = dy / d
        } else {
            // Exactly on top of each other: separate along a direction that differs per pair.
            val a = (i * 7 + j * 13) * 0.61803f
            nx = cos(a)
            ny = sin(a)
            d = 0f
        }
        val mi = c.mass[i]
        val mj = c.mass[j]
        val effectiveMass = 2f * mi * mj / (mi + mj)
        val k = effectiveMass * SimConfig.REPULSION_STIFFNESS * 0.5f * (c.hardness[i] + c.hardness[j])
        val approach = (c.vx[j] - c.vx[i]) * nx + (c.vy[j] - c.vy[i]) * ny
        var force = k * (reach - d) - effectiveMass * SimConfig.REPULSION_DAMPING * approach
        if (force < 0f) force = 0f
        c.fx[i] -= force * nx
        c.fy[i] -= force * ny
        c.fx[j] += force * nx
        c.fy[j] += force * ny
        implicitSpring(i, k, nx, ny, c.vx[j], c.vy[j])
        implicitSpring(j, k, nx, ny, c.vx[i], c.vy[i])
        if (c.type[i] == CILIOCYTE) cilia(i, nx, ny, j)
        if (c.type[j] == CILIOCYTE) cilia(j, -nx, -ny, i)
    }

    /**
     * A ciliocyte pushes itself along a surface it touches (SPEC «Цилиоцит»): contacts on its left use the left cilia,
     * on its right the right cilia. Positive speed moves the cell forward; the touched cell gets the opposite push.
     */
    private fun cilia(i: Int, nx: Float, ny: Float, other: Int) {
        val c = world.cells
        val fx = cos(c.angle[i])
        val fy = sin(c.angle[i])
        val left = fx * ny - fy * nx > 0f
        val speed = if (left) c.activity[i] else c.activity2[i]
        if (speed == 0f) return
        var tx = -ny
        var ty = nx
        if (tx * fx + ty * fy < 0f) {
            tx = -tx
            ty = -ty
        }
        val force = SimConfig.CILIA_FORCE * speed
        c.fx[i] += force * tx
        c.fy[i] += force * ty
        if (other >= 0) {
            c.fx[other] -= force * tx
            c.fy[other] -= force * ty
        }
    }

    private fun bondForces() {
        val b = world.bonds
        for (id in 0 until b.size) {
            if (b.alive[id]) bond(id)
        }
    }

    /**
     * A bond joins two attachment points on the cell surfaces (SPEC «Адгезионные связи»): a damped spring keeps the
     * cells at the bond's length, and a turning spring keeps each attachment point facing the other cell. The turning
     * springs push back on the partner's centre, so the angles between a cell's bonds hold the organism's shape.
     */
    private fun bond(id: Int) {
        val c = world.cells
        val b = world.bonds
        val i = b.a[id]
        val j = b.b[id]
        val wall = j == Bonds.WALL
        var anchorI = b.anchorA[id]
        var anchorJ = b.anchorB[id]
        var rest = b.rest[id]
        if (c.type[i] == MYOCYTE) {
            val front = cos(anchorI)
            if (front > MYOCYTE_SECTOR || front < -MYOCYTE_SECTOR) {
                rest -= c.activity[i] * SimConfig.MYOCYTE_PULL_UM
                anchorI += if (front > 0f) c.activity2[i] * SimConfig.MYOCYTE_BEND else -c.activity2[i] * SimConfig.MYOCYTE_BEND
            }
        }
        if (!wall && c.type[j] == MYOCYTE) {
            val front = cos(anchorJ)
            if (front > MYOCYTE_SECTOR || front < -MYOCYTE_SECTOR) {
                rest -= c.activity[j] * SimConfig.MYOCYTE_PULL_UM
                anchorJ += if (front > 0f) c.activity2[j] * SimConfig.MYOCYTE_BEND else -c.activity2[j] * SimConfig.MYOCYTE_BEND
            }
        }
        if (rest < 0f) rest = 0f

        val ri = c.radius[i]
        val angleI = c.angle[i] + anchorI
        val dishR = world.dishRadius
        var angleJ = 0f
        var rj = 0f
        val centreJx: Float
        val centreJy: Float
        var vjx = 0f
        var vjy = 0f
        if (wall) {
            centreJx = dishR * cos(anchorJ)
            centreJy = dishR * sin(anchorJ)
        } else {
            rj = c.radius[j]
            angleJ = c.angle[j] + anchorJ
            centreJx = c.x[j]
            centreJy = c.y[j]
            vjx = c.vx[j]
            vjy = c.vy[j]
        }

        // The distance spring works along the line between the centres; its rest length keeps the
        // attachment points «Длина адгезина» apart when they face each other.
        val ex = centreJx - c.x[i]
        val ey = centreJy - c.y[i]
        val dist2 = ex * ex + ey * ey
        val dist = sqrt(dist2)
        val restCentres = ri + rj + rest
        if (dist > restCentres + SimConfig.BOND_BREAK_STRETCH_UM) {
            world.removeBond(id)
            return
        }
        if (dist < 1e-3f) return
        val ux = ex / dist
        val uy = ey / dist
        val mi = c.mass[i]
        val mj = if (wall) mi else c.mass[j]
        val effectiveMass = if (wall) mi else 2f * mi * mj / (mi + mj)
        val stiffness = b.stiffness[id]
        val k = effectiveMass *
            (SimConfig.BOND_STIFFNESS_MIN + (SimConfig.BOND_STIFFNESS_MAX - SimConfig.BOND_STIFFNESS_MIN) * stiffness)
        val stretchSpeed = (vjx - c.vx[i]) * ux + (vjy - c.vy[i]) * uy
        val pull = k * (dist - restCentres) + effectiveMass * SimConfig.BOND_DAMPING * stretchSpeed
        c.fx[i] += pull * ux
        c.fy[i] += pull * uy
        if (wall) {
            implicitSpring(i, k, ux, uy, 0f, 0f)
        } else {
            c.fx[j] -= pull * ux
            c.fy[j] -= pull * uy
            implicitSpring(i, k, ux, uy, vjx, vjy)
            implicitSpring(j, k, ux, uy, c.vx[i], c.vy[i])
        }

        val turning = SimConfig.BOND_ANGULAR_STIFFNESS * stiffness
        if (turning <= 0f) return
        val line = atan2(ey, ex)
        val lineSpin = if (wall) {
            (ey * c.vx[i] - ex * c.vy[i]) / dist2
        } else {
            (ex * (c.vy[j] - c.vy[i]) - ey * (c.vx[j] - c.vx[i])) / dist2
        }
        val turnI = 0.5f * mi * ri * ri * turning
        val torqueI = turnI * World.wrapPi(line - angleI) -
            0.5f * mi * ri * ri * SimConfig.BOND_ANGULAR_DAMPING * (c.spin[i] - lineSpin)
        c.torque[i] += torqueI
        c.turnStiffness[i] += turnI
        c.turnTarget[i] += turnI * lineSpin
        var torqueJ = 0f
        if (!wall) {
            val turnJ = 0.5f * mj * rj * rj * turning
            torqueJ = turnJ * World.wrapPi(line + World.PI_F - angleJ) -
                0.5f * mj * rj * rj * SimConfig.BOND_ANGULAR_DAMPING * (c.spin[j] - lineSpin)
            c.torque[j] += torqueJ
            c.turnStiffness[j] += turnJ
            c.turnTarget[j] += turnJ * lineSpin
        }
        // Equal and opposite: swing the partner around so that the total angular momentum stays the same.
        val side = -(torqueI + torqueJ) / dist
        val px = -ey / dist * side
        val py = ex / dist * side
        if (!wall) {
            c.fx[j] += px
            c.fy[j] += py
        }
        c.fx[i] -= px
        c.fy[i] -= py
    }

    private fun wall(n: Int) {
        val c = world.cells
        val dishR = world.dishRadius
        for (i in 0 until n) {
            val limit = dishR - c.radius[i]
            if (limit <= 0f) continue
            val x = c.x[i]
            val y = c.y[i]
            val d2 = x * x + y * y
            if (d2 <= limit * limit) continue
            val d = sqrt(d2)
            val nx = x / d
            val ny = y / d
            val outward = c.vx[i] * nx + c.vy[i] * ny
            val k = c.mass[i] * SimConfig.WALL_STIFFNESS
            val push = k * (d - limit) + c.mass[i] * SimConfig.REPULSION_DAMPING * (if (outward > 0f) outward else 0f)
            c.fx[i] -= push * nx
            c.fy[i] -= push * ny
            implicitSpring(i, k, nx, ny, 0f, 0f)
            touchingWall[i] = true
            if (c.type[i] == CILIOCYTE) cilia(i, nx, ny, -1)
        }
    }

    private fun integrate(n: Int, h: Float) {
        val c = world.cells
        val s = world.settings
        val viscous = s.viscosity * SimConfig.VISCOSITY_DRAG
        val dynamic = s.dynamicFriction * SimConfig.DYNAMIC_FRICTION_DECELERATION * h
        val staticLimit = s.staticFriction * SimConfig.STATIC_FRICTION_ACCELERATION
        val restingSpeed2 = SimConfig.STATIC_FRICTION_SPEED * SimConfig.STATIC_FRICTION_SPEED
        val maxSpeed2 = SimConfig.MAX_SPEED * SimConfig.MAX_SPEED
        val h2 = h * h
        for (i in 0 until n) {
            val m = c.mass[i]
            val r = c.radius[i]
            val drag = viscous * r
            var vx = c.vx[i]
            var vy = c.vy[i]
            val fx = c.fx[i]
            val fy = c.fy[i]
            val friction = c.friction[i]
            val holdLimit = staticLimit * friction * m
            if (holdLimit > 0f && vx * vx + vy * vy < restingSpeed2 && fx * fx + fy * fy < holdLimit * holdLimit) {
                vx = 0f
                vy = 0f
            } else {
                // (m + h·drag + h²K)·v' = m·v + h·F + h²·K·v_partners
                val diagonal = m + h * drag
                val a11 = diagonal + h2 * c.kxx[i]
                val a12 = h2 * c.kxy[i]
                val a22 = diagonal + h2 * c.kyy[i]
                val b1 = m * vx + h * fx + h2 * c.kvx[i]
                val b2 = m * vy + h * fy + h2 * c.kvy[i]
                val det = a11 * a22 - a12 * a12
                vx = (b1 * a22 - b2 * a12) / det
                vy = (a11 * b2 - a12 * b1) / det
                val slowDown = dynamic * friction
                if (slowDown > 0f) {
                    val speed = sqrt(vx * vx + vy * vy)
                    if (speed <= slowDown) {
                        vx = 0f
                        vy = 0f
                    } else {
                        val keep = (speed - slowDown) / speed
                        vx *= keep
                        vy *= keep
                    }
                }
                val speed2 = vx * vx + vy * vy
                if (speed2 > maxSpeed2) {
                    val keep = SimConfig.MAX_SPEED / sqrt(speed2)
                    vx *= keep
                    vy *= keep
                }
            }
            c.vx[i] = vx
            c.vy[i] = vy
            c.x[i] += vx * h
            c.y[i] += vy * h

            val inertia = 0.5f * m * r * r
            val rotationalDrag = drag * r * r * SimConfig.ROTATIONAL_DRAG_FACTOR
            val turn = c.turnStiffness[i]
            var spin = (inertia * c.spin[i] + h * c.torque[i] + h2 * c.turnTarget[i]) /
                (inertia + h * rotationalDrag + h2 * turn)
            if (spin > SimConfig.MAX_SPIN) spin = SimConfig.MAX_SPIN else if (spin < -SimConfig.MAX_SPIN) spin = -SimConfig.MAX_SPIN
            c.spin[i] = spin
            var angle = c.angle[i] + spin * h
            if (angle > World.PI_F || angle <= -World.PI_F) angle = World.wrapPi(angle)
            c.angle[i] = angle
        }
    }

    private companion object {
        /** Physics time per simulation step: one game minute. */
        const val STEP_MINUTES = 1f

        val FLAGELLOCYTE = CellType.FLAGELLOCYTE.ordinal
        val BUOYOCYTE = CellType.BUOYOCYTE.ordinal
        val CILIOCYTE = CellType.CILIOCYTE.ordinal
        val MYOCYTE = CellType.MYOCYTE.ordinal

        /** A myocyte acts on bonds within ±60° of its front or back: cos 60° = 0.5. */
        const val MYOCYTE_SECTOR = 0.5f
    }
}
