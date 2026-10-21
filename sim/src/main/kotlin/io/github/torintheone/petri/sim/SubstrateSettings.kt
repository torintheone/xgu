package io.github.torintheone.petri.sim

/**
 * Parameters of the dish and its medium (SPEC «Мир: чашка Петри и среда»).
 * Each stage adds the fields it starts to use.
 */
data class SubstrateSettings(
    /** Dish diameter in millimetres, within [DISH_DIAMETER_MM]. */
    val dishDiameterMm: Float = DEFAULT_DISH_DIAMETER_MM,
) {
    init {
        require(dishDiameterMm in DISH_DIAMETER_MM) {
            "Dish diameter $dishDiameterMm mm is outside $DISH_DIAMETER_MM"
        }
    }

    /** Inner radius of the dish in world units (µm). */
    val dishRadiusUm: Float get() = dishDiameterMm * SimConfig.UM_PER_MM / 2f

    companion object {
        /** Allowed dish diameters, mm. */
        val DISH_DIAMETER_MM: ClosedFloatingPointRange<Float> = 0.5f..3f

        /** Default dish diameter, mm: a full-grown cell is about 1/40 of it. */
        const val DEFAULT_DISH_DIAMETER_MM: Float = 1f
    }
}
