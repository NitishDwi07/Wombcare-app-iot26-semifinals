package com.silicovegas.wombcare.feature.patient

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.silicovegas.wombcare.R
import com.silicovegas.wombcare.core.ble.WellnessStatus

/**
 * Plays the short lub-dub sample ([R.raw.heartbeat]) on demand, at a volume the caller sets.
 * The dashboard re-triggers [beat] once per heartbeat interval (derived from the live fetal
 * heart rate), so tempo follows the baby and volume follows the wellness status.
 *
 * [SoundPool] is the right tool here: a tiny sample, fired repeatedly with per-play volume,
 * low latency, and no MediaPlayer state machine to babysit. Always [release] when done.
 */
class HeartbeatPlayer(context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private var soundId = 0
    @Volatile private var loaded = false

    init {
        soundPool.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
        soundId = soundPool.load(context, R.raw.heartbeat, 1)
    }

    /** Fire one lub-dub at [volume] (0f..1f). No-op until the sample has finished loading. */
    fun beat(volume: Float) {
        if (loaded && volume > 0f) {
            soundPool.play(soundId, volume, volume, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool.release()
    }
}

/**
 * How loud the heartbeat should be for a given wellness status. Normal is full and reassuring;
 * Elevated and Pathological are progressively softer so the sound quietly mirrors the reading.
 * Unknown returns 0 (silent) — nothing meaningful to sonify.
 */
fun heartbeatVolumeFor(status: WellnessStatus?): Float = when (status) {
    WellnessStatus.NORMAL -> 1.0f
    WellnessStatus.SUSPECT -> 0.45f
    WellnessStatus.PATHOLOGIC -> 0.18f
    else -> 0f
}
