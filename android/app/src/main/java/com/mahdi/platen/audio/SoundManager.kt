package com.mahdi.platen.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mahdi.platen.R
import com.mahdi.platen.domain.Strike
import com.mahdi.platen.domain.TypeEvent
import kotlin.random.Random

interface SoundPlayer {
    fun play(event: TypeEvent)
    fun configure(enabled: Boolean, playInSilentMode: Boolean) {}
    fun release() {}
}

/** Records events instead of playing them; used by tests and previews. */
class RecordingSoundPlayer : SoundPlayer {
    val played = mutableListOf<TypeEvent>()
    override fun play(event: TypeEvent) { played += event }
}

/**
 * Low-latency mechanical sounds via SoundPool. Samples are loaded once, up front.
 * With "play in silent mode" on, the pool's USAGE_MEDIA stream is used regardless of the ringer;
 * otherwise sounds are skipped whenever the ringer is silent or vibrate-only.
 */
class SoundManager(context: Context) : SoundPlayer {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private fun load(id: Int) = pool.load(appContext, id, 1)

    // Real recordings (CC0): Hermes Precisa 305 by Joseph Sardin / BigSoundBank, bell_3 by _stubb / Freesound.
    // Ten bright typebar strikes (spectral centroid 5.4-6.7 kHz), four of them with a low thunk. See CREDITS.md.
    private val keys = listOf(
        R.raw.key_01, R.raw.key_02, R.raw.key_03, R.raw.key_04, R.raw.key_05,
        R.raw.key_06, R.raw.key_07, R.raw.key_08, R.raw.key_09, R.raw.key_10,
    ).map(::load)
    private val backspaceKey = load(R.raw.backspace)
    private val space = load(R.raw.space)
    private val carriages = listOf(R.raw.carriage_return, R.raw.carriage_return_2).map(::load)
    private val bells = listOf(R.raw.bell, R.raw.bell_2, R.raw.bell_3).map(::load)
    private val jam = load(R.raw.jam)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var enabled = true
    @Volatile private var inSilent = false
    private var lastKey = -1
    private var lastJam = 0L

    override fun configure(enabled: Boolean, playInSilentMode: Boolean) {
        this.enabled = enabled
        this.inSilent = playInSilentMode
    }

    private fun allowed(): Boolean {
        if (!enabled) return false
        if (inSilent) return true
        return audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    override fun play(event: TypeEvent) {
        if (!allowed()) return
        val p = SoundMix.forEvent(event, Random)
        when (event) {
            TypeEvent.KEY, TypeEvent.STRIKE -> {
                var k: Int
                do { k = Random.nextInt(keys.size) } while (k == lastKey && keys.size > 1)
                lastKey = k
                // Sounded when the type bar hits the paper on screen, not when the key went down.
                val impact = Strike.RISE_MS.toLong()
                handler.postDelayed({ pool.play(keys[k], p.volume, p.volume, 1, 0, p.rate) }, impact)
                // An overstrike is a second hammer blow on the same spot: a quieter echo right after.
                if (event == TypeEvent.STRIKE) {
                    val k2 = (k + 1 + Random.nextInt(keys.size - 1)) % keys.size
                    handler.postDelayed({ pool.play(keys[k2], p.volume * 0.45f, p.volume * 0.45f, 1, 0, p.rate) }, impact + 22L)
                }
            }
            TypeEvent.BACKSPACE, TypeEvent.ERASE -> pool.play(backspaceKey, p.volume, p.volume, 1, 0, p.rate)
            TypeEvent.SPACE -> pool.play(space, p.volume, p.volume, 1, 0, p.rate)
            TypeEvent.RETURN -> pool.play(carriages[Random.nextInt(carriages.size)], p.volume, p.volume, 2, 0, p.rate)
            TypeEvent.BELL -> pool.play(bells[Random.nextInt(bells.size)], p.volume, p.volume, 3, 0, p.rate)
            TypeEvent.LIMIT -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastJam > 250) { lastJam = now; pool.play(jam, p.volume, p.volume, 2, 0, p.rate) }
            }
        }
    }

    override fun release() { handler.removeCallbacksAndMessages(null); pool.release() }
}

/** Per-event volume and playback-rate ranges (small random variation so no two strikes match). */
data class SoundMix(val volume: Float, val rate: Float) {
    companion object {
        fun range(event: TypeEvent): Pair<ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>> = when (event) {
            // Pitch ±4 %, volume ±10 % around 0.9.
            TypeEvent.KEY, TypeEvent.STRIKE -> 0.81f..0.99f to 0.96f..1.04f
            TypeEvent.BACKSPACE -> 0.60f..0.74f to 0.96f..1.04f
            TypeEvent.ERASE -> 0.50f..0.62f to 0.96f..1.04f
            TypeEvent.SPACE -> 0.81f..0.99f to 0.96f..1.04f
            TypeEvent.RETURN -> 0.92f..1f to 0.98f..1.02f
            TypeEvent.BELL -> 0.72f..0.82f to 0.995f..1.005f
            TypeEvent.LIMIT -> 0.95f..1f to 0.9f..1f
        }

        fun forEvent(event: TypeEvent, random: Random): SoundMix {
            val (v, r) = range(event)
            return SoundMix(
                v.start + random.nextFloat() * (v.endInclusive - v.start),
                r.start + random.nextFloat() * (r.endInclusive - r.start),
            )
        }
    }
}
