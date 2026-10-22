package io.github.torintheone.petri.sim

/** The three shelves of achievements. */
enum class AchievementGroup { CAMPAIGN, DISH, DISCOVERY }

/**
 * Achievements: milestones of the campaign, of life in the dish and of finding out what cells can do. Each has a
 * [target]; its progress climbs towards it, and the achievement is earned once it gets there. The app keeps the
 * texts.
 */
enum class Achievement(val group: AchievementGroup, val target: Int = 1) {
    /** Win a level of the campaign. */
    FIRST_LEVEL(AchievementGroup.CAMPAIGN),

    /** Win a level with three stars. */
    THREE_STARS(AchievementGroup.CAMPAIGN),

    /** Win a level with a single planting. */
    ONE_PLANTING(AchievementGroup.CAMPAIGN),

    /** Win every level of one chapter. */
    CHAPTER(AchievementGroup.CAMPAIGN),

    /** Win every level. */
    CAMPAIGN(AchievementGroup.CAMPAIGN, target = Campaign.levels.size),

    /** Three stars in every level. */
    ALL_STARS(AchievementGroup.CAMPAIGN, target = Campaign.levels.size * Level.MAX_STARS),

    /** 100 cells in the dish at once. */
    CELLS_100(AchievementGroup.DISH, target = 100),

    /** 1000 cells in the dish at once. */
    CELLS_1000(AchievementGroup.DISH, target = 1000),

    /** An organism of 50 cells held together by bonds. */
    ORGANISM(AchievementGroup.DISH, target = 50),

    /** Five cell types alive at once. */
    CELL_TYPES(AchievementGroup.DISH, target = 5),

    /** Five species (genomes) alive at once. */
    SPECIES(AchievementGroup.DISH, target = 5),

    /** A dish 1000 game hours old. */
    OLD_DISH(AchievementGroup.DISH, target = 1000),

    /** Save a genome to the genome bank. */
    GENOME_SAVED(AchievementGroup.DISCOVERY),

    /** Move a cell with the tweezers. */
    TWEEZERS(AchievementGroup.DISCOVERY),

    /** Look at a cell with diagnostics. */
    DIAGNOSTICS(AchievementGroup.DISCOVERY),

    /** Plant cells of every type. */
    ALL_TYPES(AchievementGroup.DISCOVERY, target = CellType.entries.size),

    /** A virus infects a cell. */
    VIRUS(AchievementGroup.DISCOVERY),

    /** Two gametes fuse. */
    FUSION(AchievementGroup.DISCOVERY),

    /** Radiation changes a genome. */
    MUTANT(AchievementGroup.DISCOVERY),
}

/**
 * The progress of every [Achievement]: events go in, the achievements they complete come out, each one once.
 * Progress only grows. Not thread-safe: the app uses it from its main thread.
 */
class AchievementBook(saved: Map<Achievement, Int> = emptyMap(), plantedTypes: Set<CellType> = emptySet()) {
    private val values = IntArray(Achievement.entries.size)
    private val planted = plantedTypes.toMutableSet()

    /** True when progress has grown since the last [takeChanged]. */
    private var changed = false

    init {
        for ((achievement, value) in saved) values[achievement.ordinal] = value.coerceIn(0, achievement.target)
        // Types planted before count even if the saved number lags behind.
        values[Achievement.ALL_TYPES.ordinal] = maxOf(values[Achievement.ALL_TYPES.ordinal], planted.size)
    }

    fun progress(achievement: Achievement): Int = values[achievement.ordinal]

    fun isEarned(achievement: Achievement): Boolean = values[achievement.ordinal] >= achievement.target

    val earnedCount: Int get() = Achievement.entries.count(::isEarned)

    /** Progress of every achievement, to be saved. */
    fun progressMap(): Map<Achievement, Int> = Achievement.entries.associateWith { values[it.ordinal] }

    /** Cell types planted so far, to be saved. */
    val plantedTypes: Set<CellType> get() = planted

    /** True once after progress has grown: time to save. */
    fun takeChanged(): Boolean = changed.also { changed = false }

    /** The latest numbers of the dish. */
    fun onStats(stats: SimStats): List<Achievement> {
        val earned = ArrayList<Achievement>(0)
        raise(Achievement.CELLS_100, stats.cellCount, earned)
        raise(Achievement.CELLS_1000, stats.cellCount, earned)
        raise(Achievement.ORGANISM, stats.largestOrganism, earned)
        raise(Achievement.CELL_TYPES, stats.cellTypes, earned)
        raise(Achievement.SPECIES, stats.speciesCount, earned)
        raise(Achievement.OLD_DISH, stats.ageHours.toInt(), earned)
        if (stats.infections > 0) raise(Achievement.VIRUS, 1, earned)
        if (stats.fusions > 0) raise(Achievement.FUSION, 1, earned)
        if (stats.mutations > 0) raise(Achievement.MUTANT, 1, earned)
        return earned
    }

    /** [level] was won as [status] tells; [stars] holds the best stars of every won level, this one included. */
    fun onLevelWon(level: Level, status: LevelStatus, stars: Map<String, Int>): List<Achievement> {
        val earned = ArrayList<Achievement>(0)
        val plantings = level.plantings
        if (plantings != null && status.plantingsLeft != null && plantings - status.plantingsLeft == 1) {
            raise(Achievement.ONE_PLANTING, 1, earned)
        }
        earned += onCampaignProgress(stars)
        return earned
    }

    /** The stars of the campaign, from [onLevelWon] or once at the start for levels won before achievements. */
    fun onCampaignProgress(stars: Map<String, Int>): List<Achievement> {
        val earned = ArrayList<Achievement>(0)
        val won = Campaign.levels.filter { (stars[it.id] ?: 0) > 0 }
        if (won.isNotEmpty()) raise(Achievement.FIRST_LEVEL, 1, earned)
        if (won.any { (stars[it.id] ?: 0) >= Level.MAX_STARS }) raise(Achievement.THREE_STARS, 1, earned)
        if (Campaign.chapters.any { chapter -> chapter.all { it in won } }) raise(Achievement.CHAPTER, 1, earned)
        raise(Achievement.CAMPAIGN, won.size, earned)
        raise(Achievement.ALL_STARS, Campaign.levels.sumOf { (stars[it.id] ?: 0).coerceAtMost(Level.MAX_STARS) }, earned)
        return earned
    }

    /** The synthesizer planted a cell of [genome]: every type its organism uses counts. */
    fun onPlanted(genome: Genome): List<Achievement> {
        val earned = ArrayList<Achievement>(0)
        for (mode in genome.usedModes()) planted += genome.modes[mode].cellType
        raise(Achievement.ALL_TYPES, planted.size, earned)
        return earned
    }

    /** Something done once is enough: a saved genome, the tweezers, diagnostics. */
    fun onDone(achievement: Achievement): List<Achievement> {
        val earned = ArrayList<Achievement>(0)
        raise(achievement, achievement.target, earned)
        return earned
    }

    private fun raise(achievement: Achievement, value: Int, earned: MutableList<Achievement>) {
        val old = values[achievement.ordinal]
        val new = value.coerceAtMost(achievement.target)
        if (new <= old) return
        values[achievement.ordinal] = new
        changed = true
        if (new >= achievement.target) earned += achievement
    }
}
