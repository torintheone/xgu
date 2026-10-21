package io.github.torintheone.petri.sim

import java.util.concurrent.atomic.AtomicInteger

/**
 * Hands snapshots from one writer thread (the simulation) to one reader thread (the renderer) without locks:
 * the writer fills [back] and calls [publish], the reader calls [latest]. Neither side ever waits for the other,
 * and the reader always sees a complete snapshot.
 */
class TripleBuffer<T>(factory: () -> T) {
    private val buffers: List<T> = List(3) { factory() }

    /** Index of the buffer between the two sides, plus [FRESH] when the writer has published into it. */
    private val middle = AtomicInteger(1)

    /** Owned by the writer. */
    private var backIndex = 0

    /** Owned by the reader. Volatile because the reader thread may change when the render view is recreated. */
    @Volatile
    private var frontIndex = 2

    /** Writer: the buffer to fill before the next [publish]. */
    fun back(): T = buffers[backIndex]

    /** Writer: makes the filled [back] buffer the newest snapshot. */
    fun publish() {
        backIndex = middle.getAndSet(backIndex or FRESH) and INDEX_MASK
    }

    /** Reader: the newest published snapshot; stays valid until the next call. */
    fun latest(): T {
        if ((middle.get() and FRESH) != 0) {
            frontIndex = middle.getAndSet(frontIndex) and INDEX_MASK
        }
        return buffers[frontIndex]
    }

    private companion object {
        const val INDEX_MASK = 0b011
        const val FRESH = 0b100
    }
}
