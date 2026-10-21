package io.github.torintheone.petri.sim

import kotlin.math.min
import kotlin.math.sqrt

/**
 * Mass and nitrates (SPEC «Расход», «Обмен питанием по связям», «Нитраты», «Смерть»), plus food in the medium.
 * Mass is the cell's energy: income from light and food, upkeep every hour, and sharing along bonds that
 * evens out mass/priority without creating or losing any.
 */
internal class Metabolism(private val world: World) {
    fun run() {
        val c = world.cells
        val n = c.count
        if (n == 0) return
        val s = world.settings
        val dt = SimConfig.STEP_HOURS_F
        val preview = world.previewGrowth >= 0f
        val costFactor = SimConfig.COST_SCALE * SimConfig.salinityFactor(s.salinity)
        val uptake = s.nitrates * SimConfig.NITRATE_UPTAKE_PER_HOUR * dt
        val fullRadius = world.radiusOf(SimConfig.MAX_MASS)

        for (i in 0 until n) {
            c.age[i] += dt
            c.nitrateDelta[i] = 0f
            if (preview) {
                c.massDelta[i] = world.previewGrowth * dt
                c.nitrates[i] = 1f
                continue
            }
            val type = c.type[i]
            var perHour = -SimConfig.baseCostPerHour(CellType.of(type)) * costFactor
            when (type) {
                PHOTOCYTE -> perHour += SimConfig.PHOTOSYNTHESIS_PER_HOUR * world.lightAt(c.x[i], c.y[i]) *
                    c.radius[i] / fullRadius
                FLAGELLOCYTE -> perHour -= SimConfig.FLAGELLUM_COST_PER_HOUR * c.activity[i] * costFactor
                NITROCYTE -> c.nitrateDelta[i] += SimConfig.NITROCYTE_NITRATES_PER_HOUR * dt
                SECROCYTE -> c.nitrateDelta[i] -= SimConfig.SECROCYTE_NITRATES_PER_HOUR * dt
            }
            c.massDelta[i] = perHour * dt
            c.nitrateDelta[i] += uptake
        }

        share(dt)

        val aging = s.aging && !preview
        for (i in 0 until n) {
            val maxMass = CellType.of(c.type[i]).maxMass
            c.mass[i] = min(c.mass[i] + c.massDelta[i], maxMass)
            c.nitrates[i] = (c.nitrates[i] + c.nitrateDelta[i]).coerceIn(0f, 1f)
            if (preview) continue
            if (c.mass[i] < SimConfig.MIN_MASS) world.kill(i)
            if (aging && c.age[i] >= SimConfig.MAX_AGE_HOURS) world.kill(i)
        }
    }

    /**
     * Mass flows along each bond so that mass/priority evens out; the flow is proportional to the difference
     * and limited per step. Both ends change by the same amount, so the organism's mass is conserved.
     */
    private fun share(dt: Float) {
        val c = world.cells
        val b = world.bonds
        val rate = SimConfig.SHARE_RATE_PER_HOUR * dt
        val nitrateRate = SimConfig.NITRATE_SHARE_PER_HOUR * dt
        for (id in 0 until b.size) {
            if (!b.alive[id]) continue
            val i = b.a[id]
            val j = b.b[id]
            if (j == Bonds.WALL) continue
            val pi = priority(i)
            val pj = priority(j)
            var flow = rate * (c.mass[i] / pi - c.mass[j] / pj) * (pi * pj / (pi + pj))
            val limit = SimConfig.SHARE_MAX_FRACTION_PER_STEP * (if (flow > 0f) c.mass[i] else c.mass[j])
            flow = flow.coerceIn(-limit, limit)
            c.massDelta[i] -= flow
            c.massDelta[j] += flow

            val nitrateFlow = nitrateRate * (c.nitrates[i] - c.nitrates[j]) * 0.5f
            c.nitrateDelta[i] -= nitrateFlow
            c.nitrateDelta[j] += nitrateFlow
        }
    }

    /** Nutrient priority; «Приоритизировать» multiplies it while the cell is in danger of starving. */
    private fun priority(i: Int): Float {
        val mode = world.modeOf(i)
        var p = if (mode.nutrientPriority < SimConfig.PRIORITY_FLOOR) SimConfig.PRIORITY_FLOOR else mode.nutrientPriority
        if (mode.prioritize && world.cells.mass[i] < SimConfig.PRIORITY_DANGER_MASS) p *= SimConfig.PRIORITY_BOOST
        return p
    }

    /** Removes eaten particles, spawns new ones and moves mobile food. */
    fun updateFood() {
        val food = world.food
        food.compact()
        if (world.previewGrowth >= 0f) return
        spawnFood()
        if (world.settings.mobileFood) moveFood()
    }

    private fun spawnFood() {
        val s = world.settings
        if (s.foodRate <= 0f) return
        val clump = 1 + (s.foodClumping * SimConfig.FOOD_CLUMP_MAX).toInt()
        world.foodDebt += s.foodRate * SimConfig.FOOD_RATE_MAX_PER_HOUR * SimConfig.STEP_HOURS_F / clump
        val food = world.food
        val dishR = world.dishRadius
        val spread = s.foodClumpSize * SimConfig.FOOD_CLUMP_RADIUS_MAX_UM
        while (world.foodDebt >= 1f) {
            world.foodDebt -= 1f
            val (cx, cy) = world.randomPointInDisk(dishR * 0.97f)
            for (k in 0 until clump) {
                var px = cx
                var py = cy
                if (k > 0 && spread > 0f) {
                    val (ox, oy) = world.randomPointInDisk(spread)
                    px += ox
                    py += oy
                }
                if (px * px + py * py >= dishR * dishR * 0.97f * 0.97f) continue
                val coated = world.rng.chance(s.foodCoat)
                // A particle that lands on a phagocyte is eaten at once, even when the medium is full of food.
                if (coated || !feedPhagocyteAt(px, py)) {
                    if (food.count < s.maxFood) food.add(px, py, world.foodParticleMass(), coated)
                }
            }
        }
    }

    /** Feeds the phagocyte covering a point, if any; returns false when the point is on open medium. */
    private fun feedPhagocyteAt(px: Float, py: Float): Boolean {
        val c = world.cells
        val physics = world.physics
        var fed = false
        physics.grid.forEachNear(px, py, physics.maxRadius) { j ->
            if (!fed && j < c.count && c.type[j] == PHAGOCYTE && c.mass[j] < SimConfig.MAX_MASS) {
                val dx = px - c.x[j]
                val dy = py - c.y[j]
                if (dx * dx + dy * dy < c.radius[j] * c.radius[j]) {
                    c.mass[j] = min(SimConfig.MAX_MASS, c.mass[j] + world.foodParticleMass())
                    fed = true
                }
            }
        }
        return fed
    }

    /**
     * Mobile food (SPEC «Подвижная пища»): it drifts at random, smoothly and slower in a thicker medium,
     * settles or floats with gravity, and is pushed aside by cells.
     */
    private fun moveFood() {
        val food = world.food
        val c = world.cells
        val s = world.settings
        val physics = world.physics
        val gravity = s.gravity * SimConfig.GRAVITY_ACCELERATION
        val viscous = s.viscosity * SimConfig.VISCOSITY_DRAG
        val dishR = world.dishRadius
        val memory = SimConfig.FOOD_DRIFT_MEMORY_MIN
        val diffusion = SimConfig.FOOD_DIFFUSION * REFERENCE_VISCOSITY / maxOf(s.viscosity, MIN_VISCOSITY)
        val keep = 1f - 1f / memory
        val kick = sqrt(2f * diffusion) / memory
        val rng = world.rng
        for (k in 0 until food.count) {
            val m = food.mass[k]
            val r = food.radius(k)
            var vx = food.vx[k] * keep + kick * rng.nextGaussian()
            var vy = food.vy[k] * keep + kick * rng.nextGaussian()
            // Gravity: the particle sinks or floats at its terminal speed.
            val sink = if (gravity > 0f) {
                gravity * (SimConfig.FOOD_DENSITY - world.mediumDensity(food.y[k])) * m / maxOf(viscous * r, MIN_DRAG)
            } else {
                0f
            }
            var px = food.x[k] + vx
            var py = food.y[k] + vy - sink
            physics.grid.forEachNear(px, py, r + physics.maxRadius) { j ->
                if (j < c.count) {
                    val dx = px - c.x[j]
                    val dy = py - c.y[j]
                    val reach = r + c.radius[j]
                    val d2 = dx * dx + dy * dy
                    if (d2 < reach * reach && d2 > 1e-6f) {
                        val d = sqrt(d2)
                        px = c.x[j] + dx / d * reach
                        py = c.y[j] + dy / d * reach
                        vx = c.vx[j]
                        vy = c.vy[j]
                    }
                }
            }
            val limit = dishR - r
            val d2 = px * px + py * py
            if (d2 > limit * limit) {
                val scale = limit / sqrt(d2)
                px *= scale
                py *= scale
                vx = 0f
                vy = 0f
            }
            food.x[k] = px
            food.y[k] = py
            food.vx[k] = vx
            food.vy[k] = vy
        }
    }

    private companion object {
        const val REFERENCE_VISCOSITY = 50f
        const val MIN_VISCOSITY = 5f
        const val MIN_DRAG = 0.01f

        val PHAGOCYTE = CellType.PHAGOCYTE.ordinal
        val PHOTOCYTE = CellType.PHOTOCYTE.ordinal
        val FLAGELLOCYTE = CellType.FLAGELLOCYTE.ordinal
        val NITROCYTE = CellType.NITROCYTE.ordinal
        val SECROCYTE = CellType.SECROCYTE.ordinal
    }
}
