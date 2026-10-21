package io.github.torintheone.petri.sim

/**
 * All cells as parallel primitive arrays (structure of arrays), so the hot loops allocate nothing.
 * Live cells occupy slots 0 until [count]; removing a cell moves the last one into its slot.
 */
class Cells(val capacity: Int) {
    var count = 0
        internal set

    /** Stable identity of a cell, kept while the cell lives (slot numbers change). */
    val uid = IntArray(capacity)
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val vx = FloatArray(capacity)
    val vy = FloatArray(capacity)

    /** Direction of the cell's front, radians counter-clockwise from +x. */
    val angle = FloatArray(capacity)
    val spin = FloatArray(capacity)
    val mass = FloatArray(capacity)
    val age = FloatArray(capacity)
    val nitrates = FloatArray(capacity)
    val genome = IntArray(capacity)
    val mode = IntArray(capacity)

    /** [CellType] ordinal of the current mode, cached for the hot loops. */
    val type = IntArray(capacity)
    val flags = IntArray(capacity)
    val organism = IntArray(capacity)

    /** S1–S4 of cell i live at 4·i .. 4·i+3. */
    val signals = FloatArray(capacity * SIGNALS)
    val bondCount = IntArray(capacity)

    /** Bond ids of cell i live at [Mode.MAX_BONDS]·i onwards. */
    val bondIds = IntArray(capacity * Mode.MAX_BONDS)

    // Per-step working values.
    internal val radius = FloatArray(capacity)

    /** Collision hardness from the cytoskeleton: 1 at the default 0.5. */
    internal val hardness = FloatArray(capacity)
    internal val fx = FloatArray(capacity)
    internal val fy = FloatArray(capacity)
    internal val torque = FloatArray(capacity)

    // Implicit part of the springs acting on each cell this substep: stiffness matrix K (kxx, kxy, kyy),
    // the partners' share K·v (kvx, kvy), and the same for turning (turnStiffness, turnTarget).
    internal val kxx = FloatArray(capacity)
    internal val kxy = FloatArray(capacity)
    internal val kyy = FloatArray(capacity)
    internal val kvx = FloatArray(capacity)
    internal val kvy = FloatArray(capacity)
    internal val turnStiffness = FloatArray(capacity)
    internal val turnTarget = FloatArray(capacity)
    internal val massDelta = FloatArray(capacity)
    internal val nitrateDelta = FloatArray(capacity)
    internal val signalDelta = FloatArray(capacity * SIGNALS)

    /** Type-specific output this step: swim force, density, cilia, myocyte contraction and so on. */
    internal val activity = FloatArray(capacity)
    internal val activity2 = FloatArray(capacity)
    internal val activity3 = FloatArray(capacity)

    /** 1 normally; less for cells lifted off the bottom by a myocyte. */
    internal val friction = FloatArray(capacity)

    fun isMirrored(i: Int): Boolean = flags[i] and FLAG_MIRRORED != 0

    fun cellType(i: Int): CellType = CellType.of(type[i])

    /** Copies everything except the bond list of slot [from] into slot [to]. */
    internal fun copySlot(from: Int, to: Int) {
        uid[to] = uid[from]
        x[to] = x[from]
        y[to] = y[from]
        vx[to] = vx[from]
        vy[to] = vy[from]
        angle[to] = angle[from]
        spin[to] = spin[from]
        mass[to] = mass[from]
        age[to] = age[from]
        nitrates[to] = nitrates[from]
        genome[to] = genome[from]
        mode[to] = mode[from]
        type[to] = type[from]
        flags[to] = flags[from]
        organism[to] = organism[from]
        radius[to] = radius[from]
        hardness[to] = hardness[from]
        activity[to] = activity[from]
        activity2[to] = activity2[from]
        activity3[to] = activity3[from]
        friction[to] = friction[from]
        System.arraycopy(signals, from * SIGNALS, signals, to * SIGNALS, SIGNALS)
        bondCount[to] = bondCount[from]
        System.arraycopy(bondIds, from * Mode.MAX_BONDS, bondIds, to * Mode.MAX_BONDS, bondCount[from])
    }

    companion object {
        const val SIGNALS = 4

        /** Angles of this cell's genome are clockwise (its handedness is flipped). */
        const val FLAG_MIRRORED = 1

        /** Shielded by a keratinocyte: devorocytes, glue, viruses and secretions cannot touch it. */
        const val FLAG_PROTECTED = 2

        /** Removed at the end of the step. */
        const val FLAG_DEAD = 4

        /** Turns into food with all its mass when it dies (lipase on a lipocyte). */
        const val FLAG_ALL_TO_FOOD = 8

        /** A flagellocyte whose tail end is glued: the tail does not work. */
        const val FLAG_TAIL_BLOCKED = 16
    }
}

/** Adhesion bonds in a pool; a freed slot is reused by the next bond. */
class Bonds(val capacity: Int) {
    /** Slots 0 until [size] have been used; check [alive]. */
    var size = 0
        private set
    var liveCount = 0
        private set

    val a = IntArray(capacity)

    /** The other cell, or [WALL] for a glueocyte stuck to the dish wall. */
    val b = IntArray(capacity)

    /** Attachment point on each cell, radians in that cell's own frame; for a wall bond [anchorB] is the wall angle. */
    val anchorA = FloatArray(capacity)
    val anchorB = FloatArray(capacity)

    /** Rest gap between the two attachment points, µm. */
    val rest = FloatArray(capacity)

    /** «Жёсткость адгезина» 0..1. */
    val stiffness = FloatArray(capacity)
    val kind = IntArray(capacity)
    val alive = BooleanArray(capacity)

    private val free = IntArray(capacity)
    private var freeCount = 0

    val isFull: Boolean get() = freeCount == 0 && size == capacity

    internal fun allocate(): Int {
        val id = if (freeCount > 0) free[--freeCount] else size++
        alive[id] = true
        liveCount++
        return id
    }

    internal fun release(id: Int) {
        alive[id] = false
        free[freeCount++] = id
        liveCount--
    }

    internal fun clear() {
        java.util.Arrays.fill(alive, 0, size, false)
        size = 0
        freeCount = 0
        liveCount = 0
    }

    /** The pool exactly as it is, free list included, so a loaded dish hands out bond ids in the same order. */
    internal fun table(): BondTable = BondTable(
        alive = alive.copyOf(size),
        a = a.copyOf(size),
        b = b.copyOf(size),
        anchorA = anchorA.copyOf(size),
        anchorB = anchorB.copyOf(size),
        rest = rest.copyOf(size),
        stiffness = stiffness.copyOf(size),
        kind = kind.copyOf(size),
        free = free.copyOf(freeCount),
    )

    internal fun restore(table: BondTable) {
        clear()
        val n = table.alive.size
        table.alive.copyInto(alive)
        table.a.copyInto(a)
        table.b.copyInto(b)
        table.anchorA.copyInto(anchorA)
        table.anchorB.copyInto(anchorB)
        table.rest.copyInto(rest)
        table.stiffness.copyInto(stiffness)
        table.kind.copyInto(kind)
        table.free.copyInto(free)
        size = n
        freeCount = table.free.size
        liveCount = table.alive.count { it }
    }

    companion object {
        const val WALL = -1

        /** Made at division by «Делать адгезин». */
        const val KIND_ADHESION = 0

        /** Made by a glueocyte on contact. */
        const val KIND_GLUE = 1
    }
}

/** Food particles; removing one moves the last particle into its slot. */
class Food(val capacity: Int) {
    var count = 0
        internal set
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val vx = FloatArray(capacity)
    val vy = FloatArray(capacity)
    val mass = FloatArray(capacity)

    /** A lipid coat: phagocytes cannot eat it until lipase removes the coat. */
    val coated = BooleanArray(capacity)
    internal val eaten = BooleanArray(capacity)

    fun radius(i: Int): Float = SimConfig.FOOD_RADIUS_PER_SQRT_NG * kotlin.math.sqrt(mass[i])

    internal fun add(px: Float, py: Float, m: Float, isCoated: Boolean): Int {
        if (count >= capacity) return -1
        val i = count++
        x[i] = px
        y[i] = py
        vx[i] = 0f
        vy[i] = 0f
        mass[i] = m
        coated[i] = isCoated
        eaten[i] = false
        return i
    }

    internal fun removeAt(i: Int) {
        val last = count - 1
        if (i != last) {
            x[i] = x[last]
            y[i] = y[last]
            vx[i] = vx[last]
            vy[i] = vy[last]
            mass[i] = mass[last]
            coated[i] = coated[last]
            eaten[i] = eaten[last]
        }
        count--
    }

    /** Drops the particles marked as eaten. */
    internal fun compact() {
        var i = 0
        while (i < count) {
            if (eaten[i]) removeAt(i) else i++
        }
    }
}
