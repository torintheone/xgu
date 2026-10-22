package io.github.torintheone.petri.sim

import kotlin.math.max
import kotlin.math.sqrt

/** A round part of the dish, centre and radius in µm: where the player may plant, or where to get to. */
data class Zone(val x: Float, val y: Float, val radius: Float) {
    fun contains(px: Float, py: Float): Boolean {
        val dx = px - x
        val dy = py - y
        return dx * dx + dy * dy <= radius * radius
    }

    /** How far a point is from the zone's edge, µm; 0 inside. */
    fun distanceTo(px: Float, py: Float): Float {
        val dx = px - x
        val dy = py - y
        return max(0f, sqrt(dx * dx + dy * dy) - radius)
    }
}

/** Cells a level puts into the dish before the player comes. They are not the player's. */
data class Placement(
    val genome: Genome,
    val count: Int,
    /** Where they land; anywhere clear of the wall when null. */
    val zone: Zone? = null,
    val mass: Float = SimConfig.SYNTHESIZED_MASS,
)

/**
 * What a level asks for. Goals count only the player's cells: those the synthesizer planted and their descendants,
 * mutants included. A level is won when all its goals are met at the same moment.
 */
sealed interface Goal {
    /** At least [cells] of the player's cells at once. */
    data class Population(val cells: Int) : Goal

    /** At least [cells] of the player's cells of [type] at once. */
    data class TypeCount(val type: CellType, val cells: Int) : Goal

    /** One organism of at least [cells] of the player's cells held together by bonds. */
    data class Organism(val cells: Int) : Goal

    /** The player's cells weigh at least [mass] ng together. */
    data class Biomass(val mass: Float) : Goal

    /** The middle of one of the player's cells inside [zone]. */
    data class Reach(val zone: Zone) : Goal

    /** The player's cells are at least [share] of all cells in the dish, and there are at least [cells] of them. */
    data class Majority(val share: Float, val cells: Int) : Goal

    /** No more than [cells] of the other cells are left in the dish. */
    data class FewRivals(val cells: Int) : Goal

    /** The player's cells carry at least [count] different genomes at once: mutations at work. */
    data class Species(val count: Int) : Goal

    /** At hour [hours] the player still has at least [cells] cells. */
    data class Survive(val hours: Float, val cells: Int) : Goal
}

/**
 * One level of the campaign: the dish it starts with, the rules, and what the player must achieve. The app keeps
 * the texts; [id] names the level there and in the saved progress.
 */
data class Level(
    val id: String,
    val settings: SubstrateSettings,
    val goals: List<Goal>,
    /** Food in the dish at the start, as a share of the food limit. */
    val food: Float = 0.3f,
    val placements: List<Placement> = emptyList(),
    /** Game hours for the goals; null for no limit. */
    val hours: Float? = null,
    /** Hours a good solution needs: finishing within it gives three stars, within twice it two. */
    val par: Float,
    /** How many cells the synthesizer may plant; null for any number. */
    val plantings: Int? = null,
    /** The synthesizer works only here; anywhere when null. */
    val plantZone: Zone? = null,
    /** Cell types the player's genomes may use. */
    val allowedTypes: Set<CellType> = CellType.entries.toSet(),
    /** The genome the editor holds when the level starts; the default genome when null. */
    val starter: Genome? = null,
    /** Microscope tools allowed besides the synthesizer, diagnostics and the control of time. */
    val feeding: Boolean = false,
    val tweezers: Boolean = false,
    val removal: Boolean = false,
    val seed: Long = 1L,
) {
    /** The dish as the level begins: its medium, the food and the other cells, with the level attached. */
    fun createWorld(): World {
        val world = World(settings, seed)
        for (placement in placements) {
            repeat(placement.count) {
                val (x, y) = placement.zone?.let { zone ->
                    val (dx, dy) = world.randomPointInDisk(zone.radius)
                    Pair(zone.x + dx, zone.y + dy)
                } ?: world.randomPointInDisk(world.dishRadius * SimConfig.STARTING_CELLS_RADIUS_SHARE)
                world.addCell(placement.genome, x, y, mass = placement.mass)
            }
        }
        world.populate(Genome.default(), cellCount = 0, foodFraction = food)
        world.level = LevelSession(this)
        world.judgeLevel()
        return world
    }

    /** Cell types the organism of [genome] would use that this level does not allow. */
    fun forbiddenTypes(genome: Genome): Set<CellType> =
        genome.usedModes().map { genome.modes[it].cellType }.filterTo(LinkedHashSet()) { it !in allowedTypes }

    /** 3 stars within [par] hours, 2 within twice that, otherwise 1. */
    fun stars(hours: Double): Int = when {
        hours <= par -> 3
        hours <= 2.0 * par -> 2
        else -> 1
    }
}

enum class LevelOutcome { PLAYING, WON, LOST }

/** Why a level was lost. */
enum class LossReason {
    /** The time ran out before the goals were met. */
    TIME_UP,

    /** All the player's cells died and no plantings are left. */
    NO_CELLS,
}

/** Where one goal stands: [value] against [target], in the goal's own units. */
data class GoalProgress(val goal: Goal, val value: Float, val target: Float, val done: Boolean)

/** The verdict on a level at one moment. Never mutated, so any thread may read it. */
data class LevelStatus(
    val levelId: String,
    val goals: List<GoalProgress>,
    /** Game hours since the level began. */
    val hours: Double,
    val outcome: LevelOutcome,
    val playerCells: Int,
    /** Plantings the synthesizer has left; null when there is no limit. */
    val plantingsLeft: Int?,
    val loss: LossReason? = null,
    /** Stars for a won level, 0 otherwise. */
    val stars: Int = 0,
)

/** A level being played in a dish: its rules, the plantings used so far and the latest verdict. */
class LevelSession(val level: Level, plantingsUsed: Int = 0) {
    var plantingsUsed: Int = plantingsUsed
        internal set

    /** The latest verdict; once the level is won or lost it stays so. */
    var status: LevelStatus = LevelStatus(
        levelId = level.id,
        goals = level.goals.map { GoalProgress(it, 0f, 0f, done = false) },
        hours = 0.0,
        outcome = LevelOutcome.PLAYING,
        playerCells = 0,
        plantingsLeft = level.plantings?.let { (it - plantingsUsed).coerceAtLeast(0) },
    )
        internal set

    val plantingsLeft: Int? get() = level.plantings?.let { (it - plantingsUsed).coerceAtLeast(0) }
}

/** What the synthesizer did with a tap (see [World.plant]). */
enum class PlantResult {
    PLANTED,

    /** The dish is full, or the point is outside it. */
    NO_ROOM,

    /** The level allows no more plantings. */
    NO_PLANTINGS_LEFT,

    /** The level allows planting only in its zone. */
    OUTSIDE_ZONE,

    /** The genome uses a cell type the level does not allow. */
    FORBIDDEN_TYPES,

    /** The level is already won or lost. */
    LEVEL_OVER,
}

/** Works out a [LevelStatus] from the dish: counts the player's cells and checks every goal. */
internal class Referee(private val world: World) {
    private val typeCounts = IntArray(CellType.entries.size)
    private val genomeSlots = HashSet<Int>()
    private var parent = IntArray(0)
    private var componentSize = IntArray(0)

    fun judge(session: LevelSession) {
        if (session.status.outcome != LevelOutcome.PLAYING) return
        val c = world.cells
        val level = session.level
        typeCounts.fill(0)
        genomeSlots.clear()
        var mine = 0
        var others = 0
        var mass = 0.0
        for (i in 0 until c.count) {
            if (world.isDead(i)) continue
            if (c.flags[i] and Cells.FLAG_PLAYER == 0) {
                others++
                continue
            }
            mine++
            mass += c.mass[i]
            typeCounts[c.type[i]]++
            genomeSlots += c.genome[i]
        }
        val hours = world.ageHours
        val progress = level.goals.map { goal ->
            when (goal) {
                is Goal.Population -> GoalProgress(goal, mine.toFloat(), goal.cells.toFloat(), mine >= goal.cells)
                is Goal.TypeCount -> {
                    val n = typeCounts[goal.type.ordinal]
                    GoalProgress(goal, n.toFloat(), goal.cells.toFloat(), n >= goal.cells)
                }
                is Goal.Organism -> {
                    val n = largestOrganism()
                    GoalProgress(goal, n.toFloat(), goal.cells.toFloat(), n >= goal.cells)
                }
                is Goal.Biomass -> GoalProgress(goal, mass.toFloat(), goal.mass, mass >= goal.mass)
                is Goal.Reach -> {
                    val distance = closestTo(goal.zone)
                    GoalProgress(goal, distance, 0f, distance <= 0f)
                }
                is Goal.Majority -> {
                    val share = if (mine + others > 0) mine.toFloat() / (mine + others) else 0f
                    GoalProgress(goal, share, goal.share, share >= goal.share && mine >= goal.cells)
                }
                is Goal.FewRivals -> GoalProgress(goal, others.toFloat(), goal.cells.toFloat(), others <= goal.cells)
                is Goal.Species -> {
                    val n = genomeSlots.size
                    GoalProgress(goal, n.toFloat(), goal.count.toFloat(), n >= goal.count)
                }
                is Goal.Survive -> GoalProgress(goal, hours.toFloat(), goal.hours, hours >= goal.hours && mine >= goal.cells)
            }
        }
        val left = session.plantingsLeft
        val won = progress.all { it.done }
        val outcome: LevelOutcome
        val loss: LossReason?
        when {
            won -> {
                outcome = LevelOutcome.WON
                loss = null
            }
            level.hours != null && hours >= level.hours -> {
                outcome = LevelOutcome.LOST
                loss = LossReason.TIME_UP
            }
            mine == 0 && left == 0 -> {
                outcome = LevelOutcome.LOST
                loss = LossReason.NO_CELLS
            }
            else -> {
                outcome = LevelOutcome.PLAYING
                loss = null
            }
        }
        session.status = LevelStatus(
            levelId = level.id,
            goals = progress,
            hours = hours,
            outcome = outcome,
            playerCells = mine,
            plantingsLeft = left,
            loss = loss,
            stars = if (won) level.stars(hours) else 0,
        )
    }

    /** The player's cell closest to [zone]: its distance to the zone's edge, µm; 0 when one is inside. */
    private fun closestTo(zone: Zone): Float {
        val c = world.cells
        var best = Float.MAX_VALUE
        for (i in 0 until c.count) {
            if (world.isDead(i) || c.flags[i] and Cells.FLAG_PLAYER == 0) continue
            val d = zone.distanceTo(c.x[i], c.y[i])
            if (d < best) best = d
        }
        return if (best == Float.MAX_VALUE) Float.POSITIVE_INFINITY else best
    }

    /** Cells in the biggest group of the player's cells joined by bonds. */
    private fun largestOrganism(): Int {
        val c = world.cells
        val b = world.bonds
        val n = c.count
        if (parent.size < n) {
            parent = IntArray(c.capacity)
            componentSize = IntArray(c.capacity)
        }
        for (i in 0 until n) parent[i] = i
        for (id in 0 until b.size) {
            if (!b.alive[id]) continue
            val i = b.a[id]
            val j = b.b[id]
            if (j == Bonds.WALL || !isPlayerAlive(i) || !isPlayerAlive(j)) continue
            val ri = root(i)
            val rj = root(j)
            if (ri != rj) parent[ri] = rj
        }
        componentSize.fill(0, 0, n)
        var best = 0
        for (i in 0 until n) {
            if (!isPlayerAlive(i)) continue
            val r = root(i)
            componentSize[r]++
            if (componentSize[r] > best) best = componentSize[r]
        }
        return best
    }

    private fun isPlayerAlive(i: Int): Boolean = !world.isDead(i) && world.cells.flags[i] and Cells.FLAG_PLAYER != 0

    private fun root(start: Int): Int {
        var r = start
        while (parent[r] != r) r = parent[r]
        // Path compression: later finds go straight to the root.
        var i = start
        while (parent[i] != r) {
            val next = parent[i]
            parent[i] = r
            i = next
        }
        return r
    }
}
