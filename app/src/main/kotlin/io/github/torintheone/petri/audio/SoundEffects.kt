package io.github.torintheone.petri.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.annotation.RawRes
import io.github.torintheone.petri.R

/** The short sounds of the game, composed by scripts/sounds/effects.py. */
enum class Sound(@param:RawRes val resource: Int) {
    WIN(R.raw.sfx_win),
    LOSE(R.raw.sfx_lose),
    ACHIEVEMENT(R.raw.sfx_achievement),
    PLANT(R.raw.sfx_plant),
    TAP(R.raw.sfx_tap),
    DENY(R.raw.sfx_deny),
}

/**
 * Plays the game's sound effects: a won or lost level, an achievement, a planted cell, a refused planting and taps
 * on buttons. The sounds are loaded once, in the background; one asked for before it is ready is skipped.
 * Call everything on the main thread.
 */
class SoundEffects(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = IntArray(Sound.entries.size)
    private val ready = HashSet<Int>()
    private var lastTap = 0L

    /** Effects on or off. */
    var enabled = true

    /** Loudness 0–1 on top of the phone's media volume. */
    var volume = DEFAULT_VOLUME
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    init {
        pool.setOnLoadCompleteListener { _, sample, status -> if (status == 0) ready += sample }
        for (sound in Sound.entries) ids[sound.ordinal] = pool.load(context, sound.resource, 1)
    }

    /** Plays [sound]; taps closer together than [TAP_GAP_MS] play once, so held buttons do not rattle. */
    fun play(sound: Sound) {
        if (!enabled || volume <= 0f) return
        val id = ids[sound.ordinal]
        if (id !in ready) return
        if (sound == Sound.TAP) {
            val now = SystemClock.uptimeMillis()
            if (now - lastTap < TAP_GAP_MS) return
            lastTap = now
        }
        pool.play(id, volume, volume, if (sound == Sound.TAP) 0 else 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }

    companion object {
        const val DEFAULT_VOLUME = 0.7f
        private const val MAX_STREAMS = 4
        private const val TAP_GAP_MS = 60L
    }
}
