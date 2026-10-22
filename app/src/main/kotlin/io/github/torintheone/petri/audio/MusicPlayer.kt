package io.github.torintheone.petri.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import io.github.torintheone.petri.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The calm background music: one seamless loop (res/raw/ambient.ogg, composed by scripts/music/ambient.py) that
 * plays while the app is in front and the person has music on. It fades in and out, and pauses while another
 * app plays sound. Call everything on the main thread.
 */
class MusicPlayer(private val context: Context, private val scope: CoroutineScope) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change -> onFocusChange(change) }
        .build()

    private var player: MediaPlayer? = null
    private var fade: Job? = null
    private var level = 0f
    private var visible = false
    private var hasFocus = false
    private var focusLost = false

    /** Music on or off. */
    var enabled = true
        set(value) {
            field = value
            update()
        }

    /** Loudness 0–1 on top of the phone's media volume. */
    var volume = DEFAULT_VOLUME
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (player?.isPlaying == true) fadeTo(field)
            update()
        }

    /** The app came to the front or went to the background. */
    fun setVisible(value: Boolean) {
        visible = value
        if (value) focusLost = false
        update()
    }

    /** Stops for good and lets go of the player. */
    fun release() {
        fade?.cancel()
        player?.release()
        player = null
        abandonFocus()
    }

    private fun update() {
        val shouldPlay = enabled && visible && volume > 0f && !focusLost
        if (shouldPlay) start() else stop()
    }

    private fun start() {
        if (!hasFocus) {
            hasFocus = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!hasFocus) return
        }
        val current = player ?: runCatching { MediaPlayer.create(context, R.raw.ambient, attributes, audio.generateAudioSessionId()) }
            .getOrNull()
            ?.also {
                it.isLooping = true
                level = 0f
                it.setVolume(0f, 0f)
                player = it
            }
            ?: return
        if (!current.isPlaying) current.start()
        fadeTo(volume)
    }

    private fun stop() {
        val current = player ?: return
        fadeTo(0f) {
            if (current.isPlaying) current.pause()
            abandonFocus()
            if (!visible) {
                // In the background the player goes away entirely; it comes back quickly when needed.
                current.release()
                if (player === current) player = null
            }
        }
    }

    private fun fadeTo(target: Float, then: (() -> Unit)? = null) {
        fade?.cancel()
        fade = scope.launch {
            val from = level
            val steps = (FADE_MS / FADE_STEP_MS).toInt()
            for (k in 1..steps) {
                level = from + (target - from) * k / steps
                player?.setVolume(level, level)
                delay(FADE_STEP_MS)
            }
            then?.invoke()
        }
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusLost = false
                update()
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusLost = true
                if (change == AudioManager.AUDIOFOCUS_LOSS) hasFocus = false
                update()
            }
        }
    }

    private fun abandonFocus() {
        if (hasFocus) audio.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    companion object {
        const val DEFAULT_VOLUME = 0.6f
        private const val FADE_MS = 1_500L
        private const val FADE_STEP_MS = 50L
    }
}
