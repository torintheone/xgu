package io.github.torintheone.petri.sim

/**
 * The campaign: six chapters that teach the game step by step, from the first planted cell to evolution and the
 * tricks of survival. CampaignTest wins every level with a known genome, so none is impossible.
 */
object Campaign {
    private val DISH = SubstrateSettings()

    /** Above the middle of the dish, where the light source starts. */
    private val TOP_ZONE = Zone(0f, 380f, 90f)
    private val BOTTOM_ZONE = Zone(0f, -350f, 110f)

    /** A little above the middle: high enough for a raft to start growing, too low for photocytes alone. */
    private val RAFT_ZONE = Zone(0f, 30f, 70f)

    /** Heavy cells sink fast in a thin medium; the light is a narrow beam from the top. */
    private val DEEP = DISH.copy(gravity = 1f, viscosity = 15f, lightBrightness = 1f, lightWidth = 0.5f)

    val chapters: List<List<Level>> = listOf(
        listOf(
            // Plant cells and let time run; a small dish keeps the food close.
            Level(
                id = "1-1",
                settings = DISH.copy(dishDiameterMm = 0.6f, foodRate = 0.4f),
                food = 0.5f,
                goals = listOf(Goal.Population(60)),
                hours = 48f,
                par = 20f,
                plantings = 3,
                allowedTypes = setOf(CellType.PHAGOCYTE),
                seed = 101L,
            ),
            // No food at all, only even light: the cells must become photocytes.
            Level(
                id = "1-2",
                settings = DISH.copy(foodRate = 0f, lightBrightness = 0.5f, lightWidth = 1f),
                food = 0f,
                goals = listOf(Goal.TypeCount(CellType.PHOTOCYTE, 80)),
                hours = 48f,
                par = 12f,
                plantings = 3,
                allowedTypes = setOf(CellType.PHAGOCYTE, CellType.PHOTOCYTE),
                seed = 102L,
            ),
            // Glued children make one organism.
            Level(
                id = "1-3",
                settings = DISH,
                food = 0.4f,
                goals = listOf(Goal.Organism(20)),
                hours = 48f,
                par = 12f,
                plantings = 2,
                allowedTypes = setOf(CellType.PHAGOCYTE, CellType.PHOTOCYTE),
                seed = 103L,
            ),
        ),
        listOf(
            // Gravity pulls everything down; a light bubble floats up.
            Level(
                id = "2-1",
                settings = DISH.copy(gravity = 1f, lightBrightness = 0.3f),
                food = 0.3f,
                goals = listOf(Goal.Reach(TOP_ZONE)),
                hours = 24f,
                par = 8f,
                plantings = 3,
                plantZone = BOTTOM_ZONE,
                allowedTypes = setOf(CellType.PHAGOCYTE, CellType.BUOYOCYTE),
                seed = 201L,
            ),
            // A flagellum carries the organism across the dish.
            Level(
                id = "2-2",
                settings = DISH.copy(lightBrightness = 0.3f),
                food = 0.4f,
                goals = listOf(Goal.Reach(TOP_ZONE)),
                hours = 48f,
                par = 16f,
                plantings = 4,
                plantZone = BOTTOM_ZONE,
                allowedTypes = setOf(CellType.PHAGOCYTE, CellType.FLAGELLOCYTE),
                seed = 202L,
            ),
            // One organism of hundreds of cells.
            Level(
                id = "2-3",
                settings = DISH.copy(lightBrightness = 0.7f),
                food = 0.3f,
                goals = listOf(Goal.Organism(300)),
                hours = 48f,
                par = 16f,
                plantings = 2,
                allowedTypes = setOf(CellType.PHAGOCYTE, CellType.PHOTOCYTE),
                seed = 203L,
            ),
        ),
        listOf(
            // Grazers already eat the food; the light is free.
            Level(
                id = "3-1",
                settings = DISH,
                food = 0.4f,
                placements = listOf(Placement(Rivals.grazers(), count = 40)),
                goals = listOf(Goal.Majority(0.6f, 150)),
                hours = 80f,
                par = 16f,
                plantings = 5,
                allowedTypes = setOf(CellType.PHAGOCYTE, CellType.PHOTOCYTE, CellType.FLAGELLOCYTE, CellType.LIPOCYTE),
                seed = 301L,
            ),
            // In the dark there is only food, and little time: cells that divide early multiply faster.
            Level(
                id = "3-2",
                settings = DISH.copy(dishDiameterMm = 0.6f, foodRate = 0.4f, lightBrightness = 0f),
                food = 0.5f,
                goals = listOf(Goal.Population(150)),
                hours = 18f,
                par = 15f,
                plantings = 3,
                allowedTypes = setOf(CellType.PHAGOCYTE, CellType.LIPOCYTE),
                seed = 302L,
            ),
            // The wall kills every cell that touches it.
            Level(
                id = "3-3",
                settings = DISH.copy(deadlyWalls = true),
                food = 0.4f,
                goals = listOf(Goal.Population(150)),
                hours = 48f,
                par = 12f,
                plantings = 3,
                allowedTypes = setOf(CellType.PHAGOCYTE, CellType.PHOTOCYTE, CellType.FLAGELLOCYTE),
                seed = 303L,
            ),
        ),
        listOf(
            // Radiation changes genomes: new species appear by themselves.
            Level(
                id = "4-1",
                settings = DISH.copy(radiation = 0.15f),
                food = 0.5f,
                goals = listOf(Goal.Species(6)),
                hours = 60f,
                par = 20f,
                plantings = 2,
                seed = 401L,
            ),
            // Strong radiation kills and breaks genomes: only a fast-growing population lasts.
            Level(
                id = "4-2",
                settings = DISH.copy(radiation = 0.35f),
                food = 0.4f,
                goals = listOf(Goal.Survive(48f, 100)),
                hours = 48f,
                par = 48f,
                plantings = 3,
                seed = 402L,
            ),
            // Everything at once: grazers, slow photocytes, little food and mutations.
            Level(
                id = "4-3",
                settings = DISH.copy(foodRate = 0.2f, radiation = 0.05f),
                food = 0.3f,
                placements = listOf(
                    Placement(Rivals.grazers(), count = 30),
                    Placement(Rivals.slowPhotocytes(), count = 15),
                ),
                goals = listOf(Goal.Population(300), Goal.Majority(0.6f, 300)),
                hours = 120f,
                par = 60f,
                plantings = 4,
                seed = 403L,
            ),
        ),
        listOf(
            // Every crumb is coated: phagocytes starve unless a secrocyte dissolves the coats with lipase.
            Level(
                id = "5-1",
                settings = DISH.copy(dishDiameterMm = 0.6f, foodRate = 0.4f, foodCoat = 1f, lightBrightness = 0f, viscosity = 25f),
                food = 0.5f,
                goals = listOf(Goal.Population(30)),
                hours = 48f,
                par = 20f,
                plantings = 3,
                allowedTypes = setOf(CellType.PHAGOCYTE, CellType.SECROCYTE),
                seed = 501L,
            ),
            // Food only at the start: cells that store it in lipocytes outlast the famine.
            Level(
                id = "5-2",
                settings = DISH.copy(dishDiameterMm = 0.6f, foodRate = 0f, lightBrightness = 0f),
                food = 1f,
                goals = listOf(Goal.Survive(120f, 20)),
                hours = 120f,
                par = 120f,
                plantings = 3,
                allowedTypes = setOf(CellType.PHAGOCYTE, CellType.LIPOCYTE),
                seed = 502L,
            ),
            // Photocytes sink out of the light and die; a raft held up by bubbles stays in it.
            Level(
                id = "5-3",
                settings = DEEP.copy(foodRate = 0f),
                food = 0f,
                goals = listOf(Goal.TypeCount(CellType.PHOTOCYTE, 40), Goal.Survive(36f, 40)),
                hours = 48f,
                par = 36f,
                plantings = 3,
                plantZone = RAFT_ZONE,
                allowedTypes = setOf(CellType.PHOTOCYTE, CellType.BUOYOCYTE),
                seed = 503L,
            ),
        ),
        listOf(
            // The famine again, under radiation: the stores must last, and the mutants count.
            Level(
                id = "6-1",
                settings = DISH.copy(dishDiameterMm = 0.6f, foodRate = 0f, lightBrightness = 0f, radiation = 0.08f),
                food = 1f,
                goals = listOf(Goal.Survive(120f, 20), Goal.Species(3)),
                hours = 120f,
                par = 120f,
                plantings = 3,
                allowedTypes = setOf(CellType.PHAGOCYTE, CellType.LIPOCYTE),
                seed = 601L,
            ),
            // The last level: depth, a narrow beam, grazers and radiation together.
            Level(
                id = "6-2",
                settings = DEEP.copy(foodRate = 0.2f, radiation = 0.02f),
                food = 0.3f,
                placements = listOf(Placement(Rivals.grazers(), count = 30)),
                goals = listOf(Goal.TypeCount(CellType.PHOTOCYTE, 120), Goal.Majority(0.6f, 150), Goal.Survive(60f, 150)),
                hours = 80f,
                par = 60f,
                plantings = 4,
                plantZone = RAFT_ZONE,
                seed = 602L,
            ),
        ),
    )

    /** Every level in playing order. */
    val levels: List<Level> = chapters.flatten()

    fun level(id: String): Level? = levels.firstOrNull { it.id == id }

    /** The level after [id], or null after the last one. */
    fun next(id: String): Level? {
        val index = levels.indexOfFirst { it.id == id }
        return if (index >= 0) levels.getOrNull(index + 1) else null
    }
}

/** The other inhabitants of the campaign's dishes. */
object Rivals {
    /** Brown phagocytes that split into free cells, like the default genome. */
    fun grazers(): Genome = genome("grazers") { modes ->
        modes[0] = modes[0].copy(red = 0.72f, green = 0.58f, blue = 0.45f)
    }

    /** Pale green photocytes that divide late, when they are heavy. */
    fun slowPhotocytes(): Genome = genome("slow photocytes") { modes ->
        modes[0] = modes[0].copy(cellType = CellType.PHOTOCYTE, splitMass = 3.3f, red = 0.55f, green = 0.75f, blue = 0.5f)
    }

    private fun genome(name: String, edit: (MutableList<Mode>) -> Unit): Genome {
        val modes = MutableList(Mode.MODE_COUNT) { Mode.default(it) }
        edit(modes)
        return Genome(name = name, modes = modes)
    }
}
