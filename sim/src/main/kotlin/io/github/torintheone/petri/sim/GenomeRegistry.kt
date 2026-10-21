package io.github.torintheone.petri.sim

/**
 * Genomes in use, shared by reference (SPEC «Геномы в памяти»). Cells store a slot number; a slot is freed
 * when its last cell dies. Each genome gets an id and remembers its parent's id, which gives the species
 * count and the lineage.
 */
class GenomeRegistry {
    private var genomes = arrayOfNulls<Genome>(INITIAL_SLOTS)
    private var ids = LongArray(INITIAL_SLOTS)
    private var parentIds = LongArray(INITIAL_SLOTS)
    private var refs = IntArray(INITIAL_SLOTS)
    private var size = 0
    private val freeSlots = ArrayDeque<Int>()

    /** Slots by genome content, so that equal genomes planted by the synthesizer share one species. */
    private val slotByGenome = HashMap<Genome, Int>()

    var nextId = 1L
        internal set

    /** Distinct genomes carried by living cells: the species count. */
    var liveCount = 0
        private set

    operator fun get(slot: Int): Genome = genomes[slot]!!

    fun id(slot: Int): Long = ids[slot]

    fun parentId(slot: Int): Long = parentIds[slot]

    /** The slot holding [genome], adding it as a new species when it is not there yet. */
    internal fun slotFor(genome: Genome, parentId: Long = 0L): Int =
        slotByGenome[genome] ?: add(genome, nextId++, parentId)

    /** Adds a genome under a known id (used when loading a saved dish). */
    internal fun add(genome: Genome, id: Long, parentId: Long): Int {
        val slot = if (freeSlots.isNotEmpty()) freeSlots.removeFirst() else grow()
        genomes[slot] = genome
        ids[slot] = id
        parentIds[slot] = parentId
        refs[slot] = 0
        slotByGenome.putIfAbsent(genome, slot)
        if (id >= nextId) nextId = id + 1
        return slot
    }

    internal fun acquire(slot: Int) {
        if (refs[slot]++ == 0) liveCount++
    }

    internal fun release(slot: Int) {
        if (--refs[slot] == 0) {
            liveCount--
            val genome = genomes[slot]!!
            if (slotByGenome[genome] == slot) slotByGenome.remove(genome)
            genomes[slot] = null
            freeSlots.addLast(slot)
        }
    }

    internal fun clear() {
        java.util.Arrays.fill(genomes, null)
        java.util.Arrays.fill(refs, 0)
        size = 0
        freeSlots.clear()
        slotByGenome.clear()
        liveCount = 0
    }

    /** The exact slot layout, so that a loaded dish reuses slots in the same order as the saved one. */
    internal fun table(): GenomeTable = GenomeTable(
        nextId = nextId,
        slots = List(size) { slot -> genomes[slot]?.let { SavedGenome(ids[slot], parentIds[slot], it) } },
        freeSlots = freeSlots.toIntArray(),
    )

    /** Replaces everything with a saved layout. Reference counts start at 0: the loaded cells acquire their slots. */
    internal fun restore(table: GenomeTable) {
        clear()
        for (saved in table.slots) {
            val slot = grow()
            if (saved != null) {
                genomes[slot] = saved.genome
                ids[slot] = saved.id
                parentIds[slot] = saved.parentId
                slotByGenome.putIfAbsent(saved.genome, slot)
            }
        }
        for (slot in table.freeSlots) freeSlots.addLast(slot)
        nextId = table.nextId
    }

    /** True when [slot] holds a genome. */
    internal fun isUsed(slot: Int): Boolean = slot in 0 until size && genomes[slot] != null

    /** Slots that living cells use, in slot order. */
    fun liveSlots(): List<Int> = (0 until size).filter { genomes[it] != null && refs[it] > 0 }

    private fun grow(): Int {
        if (size == genomes.size) {
            val newSize = genomes.size * 2
            genomes = genomes.copyOf(newSize)
            ids = ids.copyOf(newSize)
            parentIds = parentIds.copyOf(newSize)
            refs = refs.copyOf(newSize)
        }
        return size++
    }

    private companion object {
        const val INITIAL_SLOTS = 64
    }
}
