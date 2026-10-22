package io.github.torintheone.petri.sim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every level of the campaign is won by a genome a player could build in the simple editor, planted where a player
 * would plant it. A few levels also check that the obvious wrong answer fails, which is the point of the level.
 */
class CampaignTest {
    /** Plants [genome] at [spots] and runs the level until it is decided; returns the verdict. */
    private fun play(id: String, genome: Genome, spots: List<Pair<Float, Float>>): LevelStatus {
        val level = Campaign.level(id)!!
        val world = level.createWorld()
        for ((x, y) in spots) assertEquals(PlantResult.PLANTED, world.plant(genome, x, y), "planting at $x, $y")
        val session = world.level!!
        val end = level.hours!! + 1.0
        while (session.status.outcome == LevelOutcome.PLAYING && world.ageHours < end) world.step()
        return session.status
    }

    private fun assertWon(status: LevelStatus) {
        assertEquals(LevelOutcome.WON, status.outcome, "level ${status.levelId}: ${status.goals}")
        assertTrue(status.stars >= 1)
    }

    private fun genome(edit: (MutableList<Mode>) -> Unit): Genome {
        val modes = MutableList(Mode.MODE_COUNT) { Mode.default(it) }
        edit(modes)
        return Genome(modes = modes)
    }

    private val photocytes = genome { it[0] = it[0].copy(cellType = CellType.PHOTOCYTE) }
    private val colony = Preset.PHOTOCYTE_COLONY.genome("colony")
    private val nearLight = listOf(0f to 330f, -150f to 260f, 150f to 260f)
    private val smallDish = listOf(-100f to -80f, 100f to -80f, 0f to 120f)
    private val inBottomZone = listOf(0f to -350f, -60f to -330f, 60f to -330f, 0f to -280f)

    @Test
    fun `1-1 phagocytes fill the small dish`() = assertWon(play("1-1", Genome.default(), smallDish))

    @Test
    fun `1-2 photocytes live on light alone, phagocytes starve`() {
        assertWon(play("1-2", photocytes, nearLight))
        assertEquals(LevelOutcome.LOST, play("1-2", Genome.default(), nearLight).outcome)
    }

    @Test
    fun `1-3 a glued colony is one organism`() = assertWon(play("1-3", colony, nearLight.take(2)))

    @Test
    fun `2-1 a bubble floats up, a phagocyte does not`() {
        val bubble = genome {
            it[0] = it[0].copy(cellType = CellType.BUOYOCYTE, splitMass = Mode.SPLIT_NEVER, density = ProgrammableValue.constant(0.1f))
        }
        assertWon(play("2-1", bubble, inBottomZone.take(1)))
        assertEquals(LevelOutcome.LOST, play("2-1", Genome.default(), inBottomZone.take(3)).outcome)
    }

    @Test
    fun `2-2 swimmers cross the dish`() = assertWon(play("2-2", Preset.SWIMMER.genome("swimmer"), inBottomZone))

    @Test
    fun `2-3 a colony grows into a giant`() = assertWon(play("2-3", colony, nearLight.take(2)))

    @Test
    fun `3-1 photocytes take the light the grazers leave`() =
        assertWon(play("3-1", photocytes, nearLight + listOf(0f to 150f, -250f to 100f)))

    @Test
    fun `3-2 only early division is fast enough`() {
        val early = genome { it[0] = it[0].copy(splitMass = 1.6f) }
        assertWon(play("3-2", early, smallDish))
        assertEquals(LossReason.TIME_UP, play("3-2", Genome.default(), smallDish).loss)
    }

    @Test
    fun `3-3 cells away from the deadly wall survive, swimmers do not`() {
        val inside = listOf(0f to 250f, -120f to 200f, 120f to 200f)
        assertWon(play("3-3", photocytes, inside))
        assertEquals(LevelOutcome.LOST, play("3-3", Preset.SWIMMER.genome("swimmer"), inside).outcome)
    }

    @Test
    fun `4-1 radiation brings new species`() = assertWon(play("4-1", Genome.default(), nearLight.take(2)))

    @Test
    fun `4-2 a fast colony outlasts strong radiation`() = assertWon(play("4-2", colony, nearLight))

    @Test
    fun `4-3 photocytes win the crowded dish`() = assertWon(play("4-3", photocytes, nearLight + listOf(0f to 150f)))

    @Test
    fun `the hints of the first chapters lead to a win`() {
        // 1-3: only «Потомки склеены» switched on for photocytes, nothing turned.
        val gluedPhotocytes = genome { it[0] = it[0].copy(cellType = CellType.PHOTOCYTE, makeAdhesin = true) }
        assertWon(play("1-3", gluedPhotocytes, nearLight.take(2)))
        // 2-1: a bubble that does not divide, its density lowered a few steps.
        val bubble = genome {
            it[0] = it[0].copy(cellType = CellType.BUOYOCYTE, splitMass = Mode.SPLIT_NEVER, density = ProgrammableValue.constant(0.3f))
        }
        assertWon(play("2-1", bubble, inBottomZone.take(3)))
        // 2-2: built in the simple editor: split front to back, glued; child 1 a new phagocyte, child 2 a new
        // flagellocyte; new kinds do not divide.
        val swimmer = genome {
            it[0] = it[0].copy(makeAdhesin = true, splitAngle = 90f, child1Mode = 1, child2Mode = 2)
            it[1] = it[1].copy(splitMass = Mode.SPLIT_NEVER)
            it[2] = it[2].copy(cellType = CellType.FLAGELLOCYTE, splitMass = Mode.SPLIT_NEVER)
        }
        assertWon(play("2-2", swimmer, inBottomZone))
    }

    @Test
    fun `the campaign is in order`() {
        assertEquals(4, Campaign.chapters.size)
        assertTrue(Campaign.chapters.all { it.size == 3 })
        val ids = Campaign.levels.map { it.id }
        assertEquals(ids.toSet().size, ids.size, "ids are unique")
        assertEquals("1-2", Campaign.next("1-1")?.id)
        assertEquals("2-1", Campaign.next("1-3")?.id)
        assertNull(Campaign.next("4-3"))
        assertNotNull(Campaign.level("3-2"))
        for (level in Campaign.levels) {
            val radius = level.settings.dishRadiusUm
            val zones = listOfNotNull(level.plantZone) + level.goals.filterIsInstance<Goal.Reach>().map { it.zone }
            for (zone in zones) {
                assertTrue(kotlin.math.hypot(zone.x, zone.y) + zone.radius < radius, "${level.id}: $zone is inside the dish")
            }
            assertTrue(level.hours != null && level.par <= level.hours!!, "${level.id} has a time limit and a fair par")
        }
    }
}
