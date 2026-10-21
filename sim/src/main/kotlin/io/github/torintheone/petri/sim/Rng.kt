package io.github.torintheone.petri.sim

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Deterministic random numbers (SplitMix64). The whole state is one Long, so a saved dish continues
 * exactly where it stopped, and the same seed always gives the same world.
 */
class Rng(var state: Long) {
    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1 shl 24).toFloat()

    /** Uniform in [from, until). */
    fun nextFloat(from: Float, until: Float): Float = from + (until - from) * nextFloat()

    /** Uniform in [0, bound). */
    fun nextInt(bound: Int): Int = ((nextLong() ushr 1) % bound).toInt()

    fun nextBoolean(): Boolean = nextLong() < 0

    /** True with the given probability. */
    fun chance(probability: Float): Boolean = nextFloat() < probability

    /** Standard normal distribution (Box–Muller). */
    fun nextGaussian(): Float {
        val u1 = 1.0 - (nextLong() ushr 11) * (1.0 / (1L shl 53))
        val u2 = (nextLong() ushr 11) * (1.0 / (1L shl 53))
        return (sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)).toFloat()
    }

    private companion object {
        // 0x9E3779B97F4A7C15, 0xBF58476D1CE4E5B9 and 0x94D049BB133111EB as signed Longs.
        const val GOLDEN_GAMMA = -0x61C8864680B583EBL
        const val MIX_1 = -0x40A7B892E31B1A47L
        const val MIX_2 = -0x6B2FB644ECCEEE15L
    }
}
