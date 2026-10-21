package io.github.torintheone.petri.sim

import kotlin.math.ceil
import kotlin.math.max

/**
 * Uniform grid over the dish for neighbour search (SPEC: «пространственная хеш-сетка»).
 * Rebuilt with a counting sort; buffers grow once and are reused afterwards.
 */
internal class SpatialGrid {
    var cellSize = 1f
        private set
    var columns = 0
        private set
    var originX = 0f
        private set
    var originY = 0f
        private set

    /** Items of bucket k are items[start[k] until start[k + 1]]. */
    var start = IntArray(1)
        private set
    var items = IntArray(0)
        private set
    private var bucketOf = IntArray(0)

    /** Sorts [count] points into buckets of [size] covering a square of half-width [halfExtent] around the centre. */
    fun build(xs: FloatArray, ys: FloatArray, count: Int, halfExtent: Float, size: Float) {
        cellSize = size
        columns = ceil(2f * halfExtent / size).toInt() + 2
        originX = -halfExtent - size
        originY = -halfExtent - size
        val buckets = columns * columns
        if (start.size < buckets + 1) start = IntArray(buckets + 1)
        if (items.size < count) {
            items = IntArray(max(count, items.size * 2))
            bucketOf = IntArray(items.size)
        }
        java.util.Arrays.fill(start, 0, buckets + 1, 0)
        for (i in 0 until count) {
            val bucket = bucketIndex(xs[i], ys[i])
            bucketOf[i] = bucket
            start[bucket + 1]++
        }
        // Prefix sums: start[k + 1] is now the end of bucket k.
        for (k in 1..buckets) start[k] += start[k - 1]
        // Fill each bucket from its end; afterwards start[k + 1] is the beginning of bucket k.
        for (i in count - 1 downTo 0) {
            val bucket = bucketOf[i]
            items[--start[bucket + 1]] = i
        }
        for (k in 0 until buckets) start[k] = start[k + 1]
        start[buckets] = count
    }

    fun column(x: Float): Int = ((x - originX) / cellSize).toInt().coerceIn(0, columns - 1)

    fun row(y: Float): Int = ((y - originY) / cellSize).toInt().coerceIn(0, columns - 1)

    private fun bucketIndex(x: Float, y: Float): Int = row(y) * columns + column(x)

    /** Calls [action] with every item whose bucket lies within [radius] of (x, y). */
    inline fun forEachNear(x: Float, y: Float, radius: Float, action: (Int) -> Unit) {
        if (columns == 0) return
        val c0 = column(x - radius)
        val c1 = column(x + radius)
        val r0 = row(y - radius)
        val r1 = row(y + radius)
        for (r in r0..r1) {
            val rowStart = r * columns
            for (c in c0..c1) {
                val bucket = rowStart + c
                for (k in start[bucket] until start[bucket + 1]) action(items[k])
            }
        }
    }
}
