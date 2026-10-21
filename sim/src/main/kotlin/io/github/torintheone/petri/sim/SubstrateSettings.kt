package io.github.torintheone.petri.sim

import kotlinx.serialization.Serializable

/**
 * Parameters of the dish and its medium (SPEC «Мир: чашка Петри и среда»). Defaults make a lively dish
 * of single-celled organisms; every value can be changed on the «Среда» tab.
 */
@Serializable
data class SubstrateSettings(
    // Dish.
    val dishDiameterMm: Float = 1f,
    val maxCells: Int = 1000,
    val maxFood: Int = 1000,
    // Food.
    val foodRate: Float = 0.3f,
    val foodSize: Float = 0.3f,
    val foodClumping: Float = 0f,
    val foodClumpSize: Float = 0.3f,
    val foodCoat: Float = 0f,
    val mobileFood: Boolean = true,
    // Light.
    val lightBrightness: Float = 0.6f,
    val lightWidth: Float = 0.6f,
    val lightRotation: Float = 0f,
    // Physics.
    val viscosity: Float = 50f,
    val dynamicFriction: Float = 0.1f,
    val staticFriction: Float = 0f,
    val gravity: Float = 0f,
    val mediumDensity: Float = 0.5f,
    val densityGradient: Float = 0f,
    // Chemistry.
    val salinity: Float = 0.5f,
    val nitrates: Float = 0.5f,
    // Evolution.
    val radiation: Float = 0f,
    val pointMutationsOnly: Boolean = false,
    val randomInfection: Boolean = false,
    val genePool: Set<CellType> = CellType.entries.toSet(),
    // Rules.
    val aging: Boolean = true,
    val deadlyWalls: Boolean = false,
    /** What happens to a flagellocyte whose tail end is glued to another cell: it dies, or the tail just stops. */
    val blockedFlagellumKills: Boolean = false,
    /** A dead cell leaves a food particle with part of its mass. */
    val corpsesBecomeFood: Boolean = true,
) {
    /** Inner radius of the dish in world units (µm). */
    val dishRadiusUm: Float get() = dishDiameterMm * SimConfig.UM_PER_MM / 2f

    /** Every value forced into its allowed range. */
    fun sanitized(): SubstrateSettings = copy(
        dishDiameterMm = dishDiameterMm.clampTo(DISH_DIAMETER_MM),
        maxCells = maxCells.coerceIn(MAX_CELLS),
        maxFood = maxFood.coerceIn(MAX_FOOD),
        foodRate = foodRate.clampTo(UNIT),
        foodSize = foodSize.clampTo(UNIT),
        foodClumping = foodClumping.clampTo(UNIT),
        foodClumpSize = foodClumpSize.clampTo(UNIT),
        foodCoat = foodCoat.clampTo(UNIT),
        lightBrightness = lightBrightness.clampTo(UNIT),
        lightWidth = lightWidth.clampTo(UNIT),
        lightRotation = lightRotation.clampTo(UNIT),
        viscosity = viscosity.clampTo(VISCOSITY),
        dynamicFriction = dynamicFriction.clampTo(UNIT),
        staticFriction = staticFriction.clampTo(UNIT),
        gravity = gravity.clampTo(UNIT),
        mediumDensity = mediumDensity.clampTo(UNIT),
        densityGradient = densityGradient.clampTo(UNIT),
        salinity = salinity.clampTo(UNIT),
        nitrates = nitrates.clampTo(UNIT),
        radiation = radiation.clampTo(UNIT),
    )

    companion object {
        val DISH_DIAMETER_MM = 0.5f..3f
        val MAX_CELLS = 100..5000
        val MAX_FOOD = 100..5000
        val VISCOSITY = 0f..200f
        val UNIT = 0f..1f
    }
}
