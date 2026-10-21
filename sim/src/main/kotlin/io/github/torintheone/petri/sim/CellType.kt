package io.github.torintheone.petri.sim

import kotlinx.serialization.Serializable

/** The 18 cell types (SPEC «Типы клеток»). Every cell does exactly one job. */
@Serializable
enum class CellType {
    PHAGOCYTE,
    PHOTOCYTE,
    FLAGELLOCYTE,
    DEVOROCYTE,
    LIPOCYTE,
    KERATINOCYTE,
    BUOYOCYTE,
    GLUEOCYTE,
    NITROCYTE,
    SENSEOCYTE,
    STEREOCYTE,
    MYOCYTE,
    NEUROCYTE,
    STEMOCYTE,
    SECROCYTE,
    VIROCYTE,
    GAMETE,
    CILIOCYTE,
    ;

    /** Lipocytes store fat, so they may grow far heavier than other cells. */
    val maxMass: Float
        get() = if (this == LIPOCYTE) SimConfig.MAX_MASS * SimConfig.LIPOCYTE_MASS_FACTOR else SimConfig.MAX_MASS

    companion object {
        private val VALUES = entries.toTypedArray()

        fun of(ordinal: Int): CellType = VALUES[ordinal]
    }
}
