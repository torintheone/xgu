package io.github.torintheone.petri.sim

import io.github.torintheone.petri.sim.TestGenomes.genome
import io.github.torintheone.petri.sim.TestGenomes.stepHours
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Levels: the player's cells, the synthesizer's rules, goals and the verdict. */
class LevelTest {
    /** A quiet test level: no food, no light, nothing placed. */
    private fun level(
        goals: List<Goal>,
        hours: Float? = 10f,
        plantings: Int? = null,
        plantZone: Zone? = null,
        allowedTypes: Set<CellType> = CellType.entries.toSet(),
    ) = Level(
        id = "test",
        settings = SubstrateSettings(foodRate = 0f, lightBrightness = 0f, aging = false),
        goals = goals,
        food = 0f,
        hours = hours,
        par = 2f,
        plantings = plantings,
        plantZone = plantZone,
        allowedTypes = allowedTypes,
    )

    private val loner = genome(0, 0 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) })

    private fun World.playerCount(): Int = (0 until cells.count).count { cells.flags[it] and Cells.FLAG_PLAYER != 0 }

    @Test
    fun `planted cells and their children are the player's, placed cells are not`() {
        val rivals = Placement(loner, count = 5)
        val world = level(listOf(Goal.Population(100))).copy(placements = listOf(rivals)).createWorld()
        world.previewGrowth = 1f
        assertEquals(PlantResult.PLANTED, world.plant(Genome.default(), 0f, 0f))
        world.stepHours(4.0)
        assertTrue(world.cellCount > 8, "the planted cell divided")
        assertEquals(world.cellCount - 5, world.playerCount())
        assertEquals(world.playerCount(), world.level!!.status.playerCells)
    }

    @Test
    fun `the player's cells stay theirs in a saved dish`() {
        val world = World(SubstrateSettings(), 3L)
        world.plant(loner, 0f, 0f)
        world.addCell(loner, 100f, 0f)
        val loaded = DishCodec.decode(DishCodec.encode(world.saveState()))
        assertEquals(1, loaded.playerCount())
    }

    @Test
    fun `the synthesizer keeps to the level's rules`() {
        val zone = Zone(0f, 0f, 100f)
        val world = level(listOf(Goal.Population(100)), plantings = 2, plantZone = zone, allowedTypes = setOf(CellType.PHAGOCYTE)).createWorld()
        val photocyte = genome(0, 0 to { m -> m.copy(cellType = CellType.PHOTOCYTE) })
        assertEquals(PlantResult.OUTSIDE_ZONE, world.plant(loner, 300f, 0f))
        assertEquals(PlantResult.FORBIDDEN_TYPES, world.plant(photocyte, 0f, 0f))
        assertEquals(setOf(CellType.PHOTOCYTE), world.level!!.level.forbiddenTypes(photocyte))
        assertEquals(PlantResult.PLANTED, world.plant(loner, 0f, 0f))
        assertEquals(PlantResult.PLANTED, world.plant(loner, 50f, 0f))
        assertEquals(PlantResult.NO_PLANTINGS_LEFT, world.plant(loner, -50f, 0f))
        assertEquals(0, world.level!!.plantingsLeft)
        assertEquals(2, world.cellCount)
    }

    @Test
    fun `a forbidden type deep in the organism counts too`() {
        val world = level(listOf(Goal.Population(100)), allowedTypes = setOf(CellType.PHAGOCYTE)).createWorld()
        val hidden = genome(0, 0 to { m -> m.copy(child2Mode = 3) }, 3 to { m -> m.copy(cellType = CellType.DEVOROCYTE) })
        assertEquals(PlantResult.FORBIDDEN_TYPES, world.plant(hidden, 0f, 0f))
    }

    @Test
    fun `a met goal wins the level and the win stays`() {
        val world = level(listOf(Goal.Population(2))).createWorld()
        world.plant(loner, 0f, 0f)
        assertEquals(LevelOutcome.PLAYING, world.level!!.status.outcome)
        world.plant(loner, 100f, 0f)
        val won = world.level!!.status
        assertEquals(LevelOutcome.WON, won.outcome)
        assertEquals(3, won.stars, "won at once, well within par")
        assertEquals(PlantResult.LEVEL_OVER, world.plant(loner, -100f, 0f))
        world.removeCell(world.cells.uid[0])
        world.stepHours(1.0)
        assertEquals(LevelOutcome.WON, world.level!!.status.outcome)
    }

    @Test
    fun `the level is lost when time runs out`() {
        val world = level(listOf(Goal.Population(50)), hours = 2f).createWorld()
        world.plant(loner, 0f, 0f)
        world.stepHours(1.9)
        assertEquals(LevelOutcome.PLAYING, world.level!!.status.outcome)
        // The verdict comes at the first check after the limit, within ten game minutes.
        world.stepHours(0.4)
        assertEquals(LevelOutcome.LOST, world.level!!.status.outcome)
        assertEquals(LossReason.TIME_UP, world.level!!.status.loss)
    }

    @Test
    fun `the level is lost when the cells are gone and no plantings are left`() {
        val world = level(listOf(Goal.Population(50)), hours = null, plantings = 1).createWorld()
        world.plant(loner, 0f, 0f)
        world.removeCell(world.cells.uid[0])
        world.judgeLevel()
        assertEquals(LossReason.NO_CELLS, world.level!!.status.loss)
    }

    @Test
    fun `with plantings left an empty dish is not lost`() {
        val world = level(listOf(Goal.Population(50)), hours = null, plantings = 2).createWorld()
        world.plant(loner, 0f, 0f)
        world.removeCell(world.cells.uid[0])
        world.judgeLevel()
        assertEquals(LevelOutcome.PLAYING, world.level!!.status.outcome)
    }

    @Test
    fun `an organism counts the cells joined by bonds`() {
        val glued = genome(0, 0 to { m -> m.copy(makeAdhesin = true, splitMass = Mode.SPLIT_BY_AGE) })
        val world = level(listOf(Goal.Organism(4)), hours = null).createWorld()
        world.previewGrowth = 1f
        world.previewCellLimit = 5
        world.plant(glued, 0f, 0f)
        world.plant(loner, 200f, 0f)
        world.stepHours(3.0)
        val goal = world.level!!.status.goals.single()
        assertEquals(4f, goal.value, "four glued cells; the loner is apart")
        assertTrue(goal.done)
    }

    @Test
    fun `reaching a zone shows the distance left`() {
        val world = level(listOf(Goal.Reach(Zone(0f, 300f, 50f))), hours = null).createWorld()
        world.plant(loner, 0f, 0f)
        val far = world.level!!.status.goals.single()
        assertEquals(250f, far.value, 0.5f)
        assertFalse(far.done)
        world.moveCellTo(world.cells.uid[0], 0f, 280f)
        world.judgeLevel()
        assertTrue(world.level!!.status.goals.single().done)
    }

    @Test
    fun `majority, rivals and species count the right cells`() {
        val rivals = Placement(loner, count = 3, zone = Zone(-200f, 0f, 50f))
        val world = level(listOf(Goal.Majority(0.5f, 2), Goal.FewRivals(3), Goal.Species(2)), hours = null)
            .copy(placements = listOf(rivals)).createWorld()
        world.plant(loner, 100f, 0f)
        world.plant(genome(0, 0 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER, red = 0.1f) }), 150f, 0f)
        world.plant(loner, 200f, 0f)
        val goals = world.level!!.status.goals
        assertEquals(0.5f, goals[0].value, 1e-4f)
        assertTrue(goals[0].done, "3 of 6 cells are the player's")
        assertEquals(3f, goals[1].value)
        assertTrue(goals[1].done)
        assertEquals(2f, goals[2].value, "two different genomes")
        assertEquals(LevelOutcome.WON, world.level!!.status.outcome)
    }

    @Test
    fun `stars follow the par`() {
        val level = level(listOf(Goal.Population(1)))
        assertEquals(3, level.stars(2.0))
        assertEquals(2, level.stars(3.9))
        assertEquals(1, level.stars(4.1))
    }

    @Test
    fun `a level dish starts with its medium, food and rivals`() {
        val level = Campaign.level("3-1")!!
        val world = level.createWorld()
        assertEquals(level.settings, world.settings)
        assertEquals(40, world.cellCount)
        assertEquals(0, world.playerCount())
        assertTrue(world.foodCount > 300)
        assertEquals(LevelOutcome.PLAYING, world.level!!.status.outcome)
        assertEquals(5, world.level!!.plantingsLeft)
    }
}
