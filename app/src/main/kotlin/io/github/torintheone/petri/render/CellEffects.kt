package io.github.torintheone.petri.render

import io.github.torintheone.petri.sim.WorldSnapshot

/**
 * Short effects over the dish, in real time whatever the speed of the world: a ring of light where a cell divides,
 * the cell falling apart where it dies, and newborn cells swelling to their size. Used on the GL thread only.
 */
internal class CellEffects {
    // Effects in progress: [RECORD] floats each (x, y, radius, kind, red, green, blue) and when each began.
    private val records = FloatArray(MAX_EFFECTS * RECORD)
    private val starts = LongArray(MAX_EFFECTS)
    private var count = 0
    private var seenSerial = -1L

    // Cells just born by division, swelling from [POP_FROM] of their size.
    private val bornUids = IntArray(MAX_BORN)
    private val bornNanos = LongArray(MAX_BORN)
    private var bornCount = 0

    /** True while something still moves: effects to finish or cells to swell. */
    val busy: Boolean get() = count > 0 || bornCount > 0

    /** Takes the divisions and deaths of [snapshot] that came after the ones already taken; none when [enabled] is off. */
    fun take(snapshot: WorldSnapshot, now: Long, enabled: Boolean) {
        val newest = snapshot.eventSerial
        // The first snapshot, another dish, or effects switched off: start from here without replaying the past.
        if (seenSerial < 0 || newest < seenSerial || !enabled) {
            seenSerial = newest
            if (!enabled) clear()
            return
        }
        val fresh = minOf(newest - seenSerial, MAX_NEW_PER_FRAME.toLong()).toInt()
        seenSerial = newest
        for (k in fresh - 1 downTo 0) {
            val place = ((newest - 1 - k) % WorldSnapshot.EVENT_RING).toInt()
            val o = place * WorldSnapshot.EVENT_FLOATS
            add(snapshot.events, o, now)
            if (snapshot.events[o] == WorldSnapshot.EVENT_DIVISION) {
                born(snapshot.eventUids[2 * place], now)
                born(snapshot.eventUids[2 * place + 1], now)
            }
        }
    }

    /** How big cell [uid] is drawn compared with its real size: a little less, then a little more, while it is newborn. */
    fun scale(uid: Int, now: Long): Float {
        for (k in 0 until bornCount) {
            if (bornUids[k] != uid) continue
            val t = (now - bornNanos[k]).toFloat() / POP_NANOS
            return if (t >= 1f) 1f else POP_FROM + (1f - POP_FROM) * easeOutBack(t)
        }
        return 1f
    }

    /**
     * Writes the effects still running into [out], [INSTANCE] floats each (x, y, radius, kind, red, green, blue,
     * progress 0–1), drops the finished ones and returns how many there are.
     */
    fun pack(now: Long, out: FloatArray): Int {
        var kept = 0
        for (k in 0 until count) {
            val r = k * RECORD
            val duration = if (records[r + 3] == WorldSnapshot.EVENT_DIVISION) DIVISION_NANOS else DEATH_NANOS
            val t = (now - starts[k]).toFloat() / duration
            if (t >= 1f || t < 0f) continue
            if (kept != k) {
                System.arraycopy(records, r, records, kept * RECORD, RECORD)
                starts[kept] = starts[k]
            }
            val o = kept * INSTANCE
            System.arraycopy(records, kept * RECORD, out, o, RECORD)
            out[o + RECORD] = t
            kept++
        }
        count = kept
        // Newborns that have finished swelling are forgotten.
        var keptBorn = 0
        for (k in 0 until bornCount) {
            if (now - bornNanos[k] >= POP_NANOS) continue
            bornUids[keptBorn] = bornUids[k]
            bornNanos[keptBorn] = bornNanos[k]
            keptBorn++
        }
        bornCount = keptBorn
        return kept
    }

    private fun add(events: FloatArray, o: Int, now: Long) {
        if (count == MAX_EFFECTS) return
        val r = count * RECORD
        records[r] = events[o + 1]
        records[r + 1] = events[o + 2]
        records[r + 2] = events[o + 3]
        records[r + 3] = events[o]
        records[r + 4] = events[o + 4]
        records[r + 5] = events[o + 5]
        records[r + 6] = events[o + 6]
        starts[count] = now
        count++
    }

    private fun born(uid: Int, now: Long) {
        if (uid == 0) return
        // When full, the oldest newborn stops swelling early.
        if (bornCount == MAX_BORN) {
            System.arraycopy(bornUids, 1, bornUids, 0, MAX_BORN - 1)
            System.arraycopy(bornNanos, 1, bornNanos, 0, MAX_BORN - 1)
            bornCount--
        }
        bornUids[bornCount] = uid
        bornNanos[bornCount] = now
        bornCount++
    }

    private fun clear() {
        count = 0
        bornCount = 0
    }

    /** Overshoots a little and settles: a lively swell. */
    private fun easeOutBack(t: Float): Float {
        val u = t - 1f
        return 1f + BACK_C3 * u * u * u + BACK_C1 * u * u
    }

    companion object {
        /** Floats of one effect as the shader gets it: the record plus its progress. */
        const val RECORD = 7
        const val INSTANCE = RECORD + 1
        const val MAX_EFFECTS = 320

        /** During incubation hundreds of cells divide per frame: only the newest few get an effect. */
        private const val MAX_NEW_PER_FRAME = 24
        private const val MAX_BORN = 48

        private const val DIVISION_NANOS = 550_000_000f
        private const val DEATH_NANOS = 750_000_000f
        private const val POP_NANOS = 320_000_000f
        private const val POP_FROM = 0.72f
        private const val BACK_C1 = 1.70158f
        private const val BACK_C3 = BACK_C1 + 1f
    }
}
