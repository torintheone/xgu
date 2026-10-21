package io.github.torintheone.petri.sim

/**
 * What the renderer needs from the world, copied on the simulation thread.
 * Instances are reused through [TripleBuffer], so they never escape to other code.
 */
class WorldSnapshot {
    var ageHours: Double = 0.0
    var dishRadius: Float = 0f
    var cellCount: Int = 0
    var foodCount: Int = 0
}
