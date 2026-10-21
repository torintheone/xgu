package io.github.torintheone.petri.sim

/**
 * Every tunable number of the simulation lives here (SPEC: «Все числовые константы — в SimConfig.kt»).
 * The values copy the feel of the original game, not its exact numbers; adjust them freely.
 *
 * Units: positions in micrometres (µm), mass in nanograms (ng), game time in hours (h).
 * Physics runs in game minutes: one simulation step is one minute.
 */
object SimConfig {
    // ---- Time ----

    /** Game time covered by one simulation step, in hours: one game minute. */
    const val STEP_HOURS: Double = 1.0 / 60.0
    const val STEP_HOURS_F: Float = 1f / 60f

    /** Physics substeps per step: more substeps keep stiff bonds stable. */
    const val PHYSICS_SUBSTEPS: Int = 4

    /** «Наблюдение»: game hours that pass per real second. */
    const val OBSERVE_HOURS_PER_SECOND: Double = 1.0

    /** How many times per real second a running simulation hands a fresh snapshot to the renderer and UI. */
    const val PUBLISH_RATE_HZ: Int = 60

    /**
     * «Наблюдение»: the most steps run in one frame to catch up after a stall. Time owed beyond that is dropped,
     * so a device that cannot keep up runs slower than real time instead of freezing.
     */
    const val MAX_CATCH_UP_STEPS: Int = 4

    /** How long [SimulationRunner.close] waits for the simulation thread to finish, in milliseconds. */
    const val STOP_TIMEOUT_MS: Long = 1_000

    /** Micrometres in a millimetre: settings are in mm, the world is in µm. */
    const val UM_PER_MM: Float = 1_000f

    // ---- Capacity ----

    /** Cell slots; the «Макс. число клеток» setting can use up to this many. */
    const val CELL_CAPACITY: Int = 5_000
    const val FOOD_CAPACITY: Int = 5_000
    const val BOND_CAPACITY: Int = 30_000

    // ---- Cells ----

    /** Heaviest an ordinary cell gets, ng; extra food is wasted. */
    const val MAX_MASS: Float = 3.6f

    /** A cell lighter than this dies, ng. */
    const val MIN_MASS: Float = 0.3f

    /** Lipocytes store fat: their limit is this many times [MAX_MASS]. */
    const val LIPOCYTE_MASS_FACTOR: Float = 3f

    /** Mass of a cell placed by the synthesizer, ng. */
    const val SYNTHESIZED_MASS: Float = 3f

    /** r = k·√mass, so area is proportional to mass: a 3.6 ng cell is 25 µm across, 1/40 of a 1 mm dish. */
    const val RADIUS_PER_SQRT_NG: Float = 6.588f

    /** A cell divides at most this often, h. */
    const val MIN_SPLIT_AGE_HOURS: Float = 0.5f

    /** With «Старение» on, cells die at this age, h. */
    const val MAX_AGE_HOURS: Float = 240f

    /** Density of ordinary cells on the 0–1 scale of «Плотность среды»: slightly heavier than the default medium. */
    const val CELL_DENSITY: Float = 0.6f

    // ---- Collisions and bonds ----
    // Stiffness values are accelerations per µm (1/min²): forces scale with mass so that light and heavy cells
    // behave alike and stiff bonds stay stable with [PHYSICS_SUBSTEPS].

    /** Push between overlapping cells per µm of overlap, at average cytoskeleton. */
    const val REPULSION_STIFFNESS: Float = 2f

    /** Damping of the approach speed of touching cells, 1/min (kept small: stiffness is already taken implicitly). */
    const val REPULSION_DAMPING: Float = 0.5f

    /** How much the cytoskeleton changes hardness: factor = 1 + (cytoskeleton − 0.5)·this. */
    const val CYTOSKELETON_HARDNESS: Float = 1.2f

    /** Bond spring range for «Жёсткость адгезина» 0..1. */
    const val BOND_STIFFNESS_MIN: Float = 0.15f
    const val BOND_STIFFNESS_MAX: Float = 3f

    /** Damping of the stretching speed of a bond, 1/min. */
    const val BOND_DAMPING: Float = 0.5f

    /** Turning stiffness that keeps the attachment points facing each other, at «Жёсткость адгезина» 1, 1/min². */
    const val BOND_ANGULAR_STIFFNESS: Float = 3f

    /** Damping of a bonded cell's turning relative to the bond line, 1/min. */
    const val BOND_ANGULAR_DAMPING: Float = 1f

    /** Gap between the attachment points for «Длина адгезина» 1, µm (0 means the surfaces touch). */
    const val ADHESIN_GAP_MAX_UM: Float = 10f

    /** A bond stretched this far beyond its rest length tears, µm. */
    const val BOND_BREAK_STRETCH_UM: Float = 45f

    /** Push of the dish wall per µm of penetration, 1/min². */
    const val WALL_STIFFNESS: Float = 4f

    /** Pull of the tweezers towards the finger, 1/min², and the damping that stops overshoot, 1/min. */
    const val TWEEZERS_STIFFNESS: Float = 0.6f
    const val TWEEZERS_DAMPING: Float = 1.5f

    /** Safety limits for speed, µm/min, and turning, rad/min: nothing in a viscous dish moves faster. */
    const val MAX_SPEED: Float = 60f
    const val MAX_SPIN: Float = 3f

    // ---- Movers ----

    /**
     * Thrust of a flagellum at «Сила плавания» 1, ng·µm/min². In the default medium a lone 2.5 ng cell then swims
     * about 5 µm per game minute, and the two-cell «Пловец» at force 0.6 about 90 µm per game hour.
     */
    const val FLAGELLUM_FORCE: Float = 6f

    /** Extra upkeep of a flagellum at full force, ng/h before [COST_SCALE] (0.1 base + up to 0.15 more). */
    const val FLAGELLUM_COST_PER_HOUR: Float = 0.15f

    /** A bond attached within this angle of a flagellocyte's back blocks its tail, radians (60°). */
    const val TAIL_BLOCK_ANGLE: Float = 1.0472f

    /** Push of the cilia on a touched surface at speed 1, ng·µm/min². */
    const val CILIA_FORCE: Float = 1.2f

    /** How much a myocyte shortens (contraction 1) or lengthens (−1) its front and back bonds, µm. */
    const val MYOCYTE_PULL_UM: Float = 6f

    /** How far a myocyte turns its front and back attachment points at bend ±1, radians (35°). */
    const val MYOCYTE_BEND: Float = 0.61f

    // ---- Contacts ----

    /** Cells count as touching when their surfaces are closer than this, µm. */
    const val CONTACT_SLACK_UM: Float = 1f

    /** Mass a devorocyte drains from a touched cell, ng/h, and the share it keeps. */
    const val DEVOUR_PER_HOUR: Float = 6f
    const val DEVOUR_EFFICIENCY: Float = 0.8f

    /** A glueocyte stops gluing once it has this many bonds. */
    const val GLUE_MAX_BONDS: Int = 5

    // ---- Medium ----

    /** Stokes drag F = −viscosity·this·r·v (ng/min per µm of radius at viscosity 1). */
    const val VISCOSITY_DRAG: Float = 0.002f

    /** Turning drag compared with sliding drag. */
    const val ROTATIONAL_DRAG_FACTOR: Float = 1f

    /** Deceleration from «Динамическое трение» 1, µm/min². */
    const val DYNAMIC_FRICTION_DECELERATION: Float = 1f

    /** Pull per ng needed to move a resting cell at «Статическое трение» 1, µm/min². */
    const val STATIC_FRICTION_ACCELERATION: Float = 2f

    /** Below this speed a cell counts as resting for static friction, µm/min. */
    const val STATIC_FRICTION_SPEED: Float = 0.05f

    /** Acceleration per unit of density difference at «Гравитация» 1, µm/min². */
    const val GRAVITY_ACCELERATION: Float = 2f

    // ---- Food ----

    /** Food particles per hour at «Скорость появления пищи» 1. */
    const val FOOD_RATE_MAX_PER_HOUR: Float = 300f

    /** Mass of one food particle for «Размер частицы пищи» 0 and 1, ng. */
    const val FOOD_MASS_MIN: Float = 0.05f
    const val FOOD_MASS_MAX: Float = 1f

    /** Particles that appear together at «Кучность пищи» 1. */
    const val FOOD_CLUMP_MAX: Int = 20

    /** Spread of a clump at «Размер кучи» 1, µm. */
    const val FOOD_CLUMP_RADIUS_MAX_UM: Float = 150f

    /** Food radius = this·√mass, µm. */
    const val FOOD_RADIUS_PER_SQRT_NG: Float = 3.5f

    /** Food density on the 0–1 scale: mobile food slowly settles in the default medium. */
    const val FOOD_DENSITY: Float = 0.55f

    /** Share of a dead cell's mass left as food when «corpses become food» is on. */
    const val CORPSE_FOOD_FRACTION: Float = 0.5f

    /** Drift of mobile food at viscosity 50, µm²/min; thicker media slow it down. */
    const val FOOD_DIFFUSION: Float = 4f

    /** How long a food particle keeps its drift direction, min: longer looks smoother. */
    const val FOOD_DRIFT_MEMORY_MIN: Float = 15f

    // ---- Light ----

    /** Turns of the light source per hour at «Вращение света» 1. */
    const val LIGHT_MAX_TURNS_PER_HOUR: Float = 0.1f

    /** Beam width σ in dish radii for «Ширина света» 0 and just below 1 (at 1 the light is even). */
    const val LIGHT_SIGMA_MIN: Float = 0.15f
    const val LIGHT_SIGMA_MAX: Float = 3f

    /** Photocyte income in full light for a cell of maximum size, ng/h; smaller cells catch less. */
    const val PHOTOSYNTHESIS_PER_HOUR: Float = 5f

    // ---- Upkeep ----

    /** All upkeep costs are multiplied by this; the per-type ratios follow the original's descriptions. */
    const val COST_SCALE: Float = 0.4f

    /** Upkeep before [COST_SCALE] and salinity, ng/h. */
    fun baseCostPerHour(type: CellType): Float = when (type) {
        CellType.PHAGOCYTE -> 0.15f
        CellType.PHOTOCYTE -> 2.5f
        CellType.FLAGELLOCYTE -> 0.1f
        CellType.DEVOROCYTE -> 0.4f
        CellType.LIPOCYTE -> 0f
        CellType.KERATINOCYTE -> 0.01f
        CellType.BUOYOCYTE -> 0.05f
        CellType.GLUEOCYTE -> 0.01f
        CellType.NITROCYTE -> 0.3f
        CellType.SENSEOCYTE -> 0.1f
        CellType.STEREOCYTE -> 0.1f
        CellType.MYOCYTE -> 0.15f
        CellType.NEUROCYTE -> 0.2f
        CellType.STEMOCYTE -> 0.05f
        CellType.SECROCYTE -> 0.3f
        CellType.VIROCYTE -> 2f
        CellType.GAMETE -> 0.05f
        CellType.CILIOCYTE -> 0.15f
    }

    /** Salinity 0..1 multiplies upkeep by 1.5..0.5: low salinity makes life expensive. */
    fun salinityFactor(salinity: Float): Float = 1.5f - salinity

    /** Extra mass paid at division per unit of cytoskeleton above 0.5, ng. */
    const val CYTOSKELETON_DIVISION_COST: Float = 0.6f

    // ---- Sharing along bonds ----

    /** Speed at which bonded cells even out mass/priority, share of the difference per hour. */
    const val SHARE_RATE_PER_HOUR: Float = 3f

    /** No bond moves more than this share of the donor's mass per step. */
    const val SHARE_MAX_FRACTION_PER_STEP: Float = 0.02f

    /** «Приоритизировать»: below this mass a cell's priority is multiplied by [PRIORITY_BOOST], ng. */
    const val PRIORITY_DANGER_MASS: Float = 0.7f
    const val PRIORITY_BOOST: Float = 10f

    /** Priority used in place of 0 so that a zero-priority cell still holds a little. */
    const val PRIORITY_FLOOR: Float = 0.02f

    // ---- Nitrates ----

    /** Uptake from the medium at «Нитраты» 1, store fraction per hour. */
    const val NITRATE_UPTAKE_PER_HOUR: Float = 1f

    /** Nitrates a division uses up. */
    const val NITRATE_DIVISION_COST: Float = 0.3f

    /** Speed at which bonded cells even out nitrates, share of the difference per hour. */
    const val NITRATE_SHARE_PER_HOUR: Float = 2f

    /** Nitrates a nitrocyte makes and a secrocyte uses up, store fraction per hour. */
    const val NITROCYTE_NITRATES_PER_HOUR: Float = 2f
    const val SECROCYTE_NITRATES_PER_HOUR: Float = 0.5f

    // ---- Evolution ----

    /**
     * Chance per hour that radiation mutates a cell: linear for weak radiation, steep for strong. At 0.04 a dish of
     * 1000 cells gets about 7 mutations an hour (variation shows within tens of hours); at 1 every cell mutates
     * twice an hour and genomes fall apart.
     */
    fun mutationsPerHour(radiation: Float): Float = 0.1f * radiation + 1.9f * radiation * radiation

    /** Chance per hour that radiation kills a cell outright; only strong radiation matters. */
    fun radiationDeathsPerHour(radiation: Float): Float = 0.1f * radiation * radiation

    /** Share of mutations that change the structure (type, links, switches) when that is allowed. */
    const val STRUCTURAL_MUTATION_SHARE: Float = 0.3f

    /** Share of mutations that may hit any of the 40 modes; the rest hit a mode the organism uses. */
    const val ANY_MODE_MUTATION_SHARE: Float = 0.2f

    /** Spread of a point mutation as a share of the number's range. */
    const val POINT_MUTATION_SIGMA: Float = 0.08f

    /** «Заражение случайными клетками»: on average one newcomer per this many hours. */
    const val INFECTION_INTERVAL_HOURS: Float = 4f

    // ---- Growth preview (genome editor) ----

    /** The preview organism stops dividing at this many cells. */
    const val PREVIEW_MAX_CELLS: Int = 64

    /** The preview covers game hours 0 until this, one picture per hour (the slider shows 0–47 h). */
    const val PREVIEW_HOURS: Int = 48

    /** Mass every preview cell gains per hour, ng: «endless food and light». A 2.5 ng mode divides about every 1.3 h. */
    const val PREVIEW_GROWTH_PER_HOUR: Float = 1f

    /** The preview dish is the largest allowed, so the walls rarely get in the way, mm. */
    const val PREVIEW_DISH_DIAMETER_MM: Float = 3f

    // ---- Statistics ----

    /** How often the population graph gets a new point, h. */
    const val HISTORY_INTERVAL_HOURS: Float = 0.5f

    /** Points kept for the population graph. */
    const val HISTORY_LENGTH: Int = 480
}
