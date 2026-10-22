package io.github.torintheone.petri.sim

/**
 * What the renderer needs from the world, copied on the simulation thread.
 * Instances are reused through [TripleBuffer]; arrays only grow, so steady running allocates nothing.
 */
class WorldSnapshot {
    /** Grows by one with every snapshot handed out, so a reader can tell a new one from one it has seen. */
    var serial: Long = 0L

    /** [System.nanoTime] when it was handed out. */
    var publishedNanos: Long = 0L

    /** Steps the world had taken; unchanged when only a command (a tweezers move, a new cell) changed the world. */
    var stepCount: Long = 0L

    var ageHours: Double = 0.0
    var dishRadius: Float = 0f
    var cellCount: Int = 0
    var foodCount: Int = 0
    var bondCount: Int = 0
    var speciesCount: Int = 0
    var lightAngle: Float = 0f
    var lightBrightness: Float = 0f

    /** Beam width σ in µm; 0 means even light over the whole dish. */
    var lightSigma: Float = 0f

    /** [CELL_FLOATS] per cell: x, y, radius, angle, red, green, blue, type, activity, age, flags. */
    var cells = FloatArray(0)
        private set
    var cellUids = IntArray(0)
        private set

    /** [BOND_FLOATS] per bond: x1, y1, x2, y2, kind. */
    var bonds = FloatArray(0)
        private set

    /** Two per bond: the places in [cells] of its two cells; the second is −1 for a bond to the wall. */
    var bondEnds = IntArray(0)
        private set

    /** [FOOD_FLOATS] per particle: x, y, radius, coated. */
    var food = FloatArray(0)
        private set

    internal fun ensureCells(n: Int) {
        if (cellUids.size < n) {
            val size = grow(n)
            cells = FloatArray(size * CELL_FLOATS)
            cellUids = IntArray(size)
        }
    }

    internal fun ensureBonds(n: Int) {
        if (bonds.size < n * BOND_FLOATS) {
            val size = grow(n)
            bonds = FloatArray(size * BOND_FLOATS)
            bondEnds = IntArray(size * 2)
        }
    }

    internal fun ensureFood(n: Int) {
        if (food.size < n * FOOD_FLOATS) food = FloatArray(grow(n) * FOOD_FLOATS)
    }

    private fun grow(n: Int): Int = maxOf(64, n + n / 2)

    companion object {
        const val CELL_FLOATS = 11
        const val BOND_FLOATS = 5
        const val FOOD_FLOATS = 4

        /** Cell flags the renderer draws. */
        const val VISIBLE_FLAGS = Cells.FLAG_MIRRORED or Cells.FLAG_PROTECTED or Cells.FLAG_TAIL_BLOCKED
    }
}

/** The population graph: a ring of the latest samples (SPEC «график численности»). */
class PopulationHistory(val capacity: Int) {
    private val hours = FloatArray(capacity)
    private val cells = IntArray(capacity)
    private val food = IntArray(capacity)
    private val species = IntArray(capacity)
    private var start = 0

    var size = 0
        private set

    internal fun add(hour: Float, cellCount: Int, foodCount: Int, speciesCount: Int) {
        val slot = (start + size) % capacity
        hours[slot] = hour
        cells[slot] = cellCount
        food[slot] = foodCount
        species[slot] = speciesCount
        if (size < capacity) size++ else start = (start + 1) % capacity
    }

    internal fun clear() {
        start = 0
        size = 0
    }

    internal fun table(): HistoryTable {
        val list = samples()
        return HistoryTable(
            hours = FloatArray(list.size) { list[it].hour },
            cells = IntArray(list.size) { list[it].cells },
            food = IntArray(list.size) { list[it].food },
            species = IntArray(list.size) { list[it].species },
        )
    }

    internal fun restore(table: HistoryTable) {
        clear()
        for (k in table.hours.indices) add(table.hours[k], table.cells[k], table.food[k], table.species[k])
    }

    /** Samples oldest first. */
    fun samples(): List<Sample> = List(size) {
        val slot = (start + it) % capacity
        Sample(hours[slot], cells[slot], food[slot], species[slot])
    }

    data class Sample(val hour: Float, val cells: Int, val food: Int, val species: Int)
}
