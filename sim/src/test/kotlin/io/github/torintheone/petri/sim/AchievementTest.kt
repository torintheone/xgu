package io.github.torintheone.petri.sim

import io.github.torintheone.petri.sim.Achievement.ALL_STARS
import io.github.torintheone.petri.sim.Achievement.ALL_TYPES
import io.github.torintheone.petri.sim.Achievement.CAMPAIGN
import io.github.torintheone.petri.sim.Achievement.CELLS_100
import io.github.torintheone.petri.sim.Achievement.CELLS_1000
import io.github.torintheone.petri.sim.Achievement.CELL_TYPES
import io.github.torintheone.petri.sim.Achievement.CHAPTER
import io.github.torintheone.petri.sim.Achievement.FIRST_LEVEL
import io.github.torintheone.petri.sim.Achievement.FUSION
import io.github.torintheone.petri.sim.Achievement.MUTANT
import io.github.torintheone.petri.sim.Achievement.OLD_DISH
import io.github.torintheone.petri.sim.Achievement.ONE_PLANTING
import io.github.torintheone.petri.sim.Achievement.ORGANISM
import io.github.torintheone.petri.sim.Achievement.SPECIES
import io.github.torintheone.petri.sim.Achievement.THREE_STARS
import io.github.torintheone.petri.sim.Achievement.TWEEZERS
import io.github.torintheone.petri.sim.Achievement.VIRUS
import io.github.torintheone.petri.sim.TestGenomes.genome
import io.github.torintheone.petri.sim.TestGenomes.quietWorld
import io.github.torintheone.petri.sim.TestGenomes.stepHours
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Achievements: what earns them, each only once, and what the dish counts for them. */
class AchievementTest {
    private fun stats(
        cells: Int = 0,
        organism: Int = 0,
        types: Int = 0,
        species: Int = 0,
        hours: Double = 0.0,
        infections: Long = 0L,
        fusions: Long = 0L,
        mutations: Long = 0L,
    ) = SimStats(
        ageHours = hours,
        cellCount = cells,
        foodCount = 0,
        speciesCount = species,
        largestOrganism = organism,
        cellTypes = types,
        infections = infections,
        fusions = fusions,
        mutations = mutations,
    )

    /** [level] won with [stars] after [plantings] plantings. */
    private fun won(level: Level, stars: Int, plantings: Int) = LevelStatus(
        levelId = level.id,
        goals = emptyList(),
        hours = 5.0,
        outcome = LevelOutcome.WON,
        playerCells = 10,
        plantingsLeft = level.plantings!! - plantings,
        stars = stars,
    )

    @Test
    fun `the numbers of the dish earn their achievements once`() {
        val book = AchievementBook()
        assertEquals(emptyList<Achievement>(), book.onStats(stats(cells = 99)))
        assertEquals(99, book.progress(CELLS_100))
        assertEquals(listOf(CELLS_100), book.onStats(stats(cells = 150)))
        assertEquals(emptyList<Achievement>(), book.onStats(stats(cells = 160)), "earned only once")
        assertEquals(CELLS_100.target, book.progress(CELLS_100), "progress stops at the target")
        assertEquals(160, book.progress(CELLS_1000))

        val earned = book.onStats(
            stats(cells = 10, organism = 50, types = 5, species = 6, hours = 1000.5, infections = 1, fusions = 2, mutations = 3),
        )
        assertEquals(setOf(ORGANISM, CELL_TYPES, SPECIES, OLD_DISH, VIRUS, FUSION, MUTANT), earned.toSet())
        assertEquals(160, book.progress(CELLS_1000), "progress never falls")
        assertEquals(emptyList<Achievement>(), book.onStats(stats(cells = 1000)).filter { it != CELLS_1000 })
        assertTrue(book.isEarned(CELLS_1000))
    }

    @Test
    fun `winning levels earns the campaign achievements`() {
        val book = AchievementBook()
        val chapter = Campaign.chapters.first()
        val stars = mutableMapOf(chapter[0].id to 3)
        assertEquals(setOf(FIRST_LEVEL, THREE_STARS, ONE_PLANTING), book.onLevelWon(chapter[0], won(chapter[0], 3, 1), stars).toSet())
        assertFalse(book.isEarned(CHAPTER))

        stars[chapter[1].id] = 2
        assertEquals(emptyList<Achievement>(), book.onLevelWon(chapter[1], won(chapter[1], 2, 2), stars))
        stars[chapter[2].id] = 1
        assertEquals(listOf(CHAPTER), book.onLevelWon(chapter[2], won(chapter[2], 1, 2), stars))
        assertEquals(3, book.progress(CAMPAIGN))
        assertEquals(6, book.progress(ALL_STARS))
        assertFalse(book.isEarned(CAMPAIGN))
    }

    @Test
    fun `a chapter counts only when all its levels are won`() {
        // One level won in every chapter: no chapter is done.
        val book = AchievementBook()
        book.onCampaignProgress(Campaign.chapters.associate { it.first().id to 3 })
        assertFalse(book.isEarned(CHAPTER))
    }

    @Test
    fun `levels won before achievements existed count at the start`() {
        val book = AchievementBook()
        val earned = book.onCampaignProgress(Campaign.levels.associate { it.id to 3 })
        assertEquals(setOf(FIRST_LEVEL, THREE_STARS, CHAPTER, CAMPAIGN, ALL_STARS), earned.toSet())
        assertFalse(book.isEarned(ONE_PLANTING), "how a level was won is not known afterwards")
    }

    @Test
    fun `planted cell types add up across plantings`() {
        val book = AchievementBook()
        book.onPlanted(Genome.default())
        assertEquals(1, book.progress(ALL_TYPES))
        val pair = genome(
            0,
            0 to { m -> m.copy(cellType = CellType.PHOTOCYTE, child2Mode = 1) },
            1 to { m -> m.copy(cellType = CellType.BUOYOCYTE) },
        )
        book.onPlanted(pair)
        assertEquals(setOf(CellType.PHAGOCYTE, CellType.PHOTOCYTE, CellType.BUOYOCYTE), book.plantedTypes)
        val earned = CellType.entries.flatMap { type -> book.onPlanted(genome(0, 0 to { m -> m.copy(cellType = type) })) }
        assertEquals(listOf(ALL_TYPES), earned)
    }

    @Test
    fun `progress survives saving`() {
        val book = AchievementBook()
        book.onStats(stats(cells = 500))
        book.onDone(TWEEZERS)
        book.onPlanted(Genome.default())
        assertTrue(book.takeChanged())
        assertFalse(book.takeChanged(), "nothing new since")

        val again = AchievementBook(book.progressMap(), book.plantedTypes)
        assertEquals(500, again.progress(CELLS_1000))
        assertTrue(again.isEarned(TWEEZERS))
        assertEquals(1, again.progress(ALL_TYPES))
        assertEquals(book.earnedCount, again.earnedCount)
        assertEquals(emptyList<Achievement>(), again.onDone(TWEEZERS))
        assertFalse(again.takeChanged())
    }

    @Test
    fun `the dish counts its biggest organism and its cell types`() {
        val world = quietWorld()
        val photocyte = genome(0, 0 to { m -> m.copy(cellType = CellType.PHOTOCYTE) })
        val buoy = genome(0, 0 to { m -> m.copy(cellType = CellType.BUOYOCYTE) })
        val a = world.indexOfUid(world.addCell(Genome.default(), 0f, 0f, angle = 0f))
        val b = world.indexOfUid(world.addCell(Genome.default(), 20f, 0f, angle = 0f))
        val c = world.indexOfUid(world.addCell(photocyte, 40f, 0f, angle = 0f))
        world.addCell(buoy, 200f, 0f)
        world.addCell(buoy, -200f, 0f)
        world.addBond(a, b, 0f, World.PI_F, 2f, 1f, Bonds.KIND_ADHESION)
        world.addBond(b, c, 0f, World.PI_F, 2f, 1f, Bonds.KIND_ADHESION)
        assertEquals(3, world.largestOrganism())
        assertEquals(3, world.cellTypeCount())
    }

    @Test
    fun `the dish counts infections, fusions and mutations`() {
        val world = World(SubstrateSettings(foodRate = 0f, lightBrightness = 0f, aging = false, corpsesBecomeFood = false, salinity = 1f), 3L)
        val virus = genome(0, 0 to { m -> m.copy(cellType = CellType.VIROCYTE, splitMass = Mode.SPLIT_NEVER, virusMode = 5) })
        val gamete = genome(0, 0 to { m -> m.copy(cellType = CellType.GAMETE, fusionMode = 3) })
        world.addCell(virus, 0f, 0f, angle = 0f, mass = 3f)
        world.addCell(genome(0, 0 to { m -> m.copy(splitMass = Mode.SPLIT_NEVER) }), 23f, 0f, angle = 0f, mass = 3f)
        world.addCell(gamete, 200f, 0f, angle = 0f, mass = 1.5f)
        world.addCell(gamete, 215f, 0f, angle = 0f, mass = 1.5f)
        world.stepHours(4.0)
        assertTrue(world.infectionCount > 0)
        assertEquals(1L, world.fusionCount)
        assertEquals(0L, world.mutationCount)

        val radiant = World(SubstrateSettings(radiation = 1f), 5L)
        radiant.populate(Genome.default(), cellCount = 30, foodFraction = 0.5f)
        radiant.stepHours(4.0)
        assertTrue(radiant.mutationCount > 0)
    }
}
