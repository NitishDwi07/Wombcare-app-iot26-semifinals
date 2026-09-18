package com.silicovegas.wombcare.core.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The "Heartbeat sound" toggle (Settings → Sound). When on, the dashboard plays a soft
 * lub-dub in time with the live fetal heart rate — louder when the status is Normal and
 * progressively quieter for Elevated / Pathological, so the sound itself is reassuring.
 *
 * Backed by [SharedPreferences] but exposed as a [StateFlow] so the live screen reacts the
 * moment the user flips the switch. Defaults to **off**: sound is opt-in, never a surprise.
 */
@Singleton
class HeartbeatSoundPreference @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("wombcare_sound", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_HEARTBEAT, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_HEARTBEAT, on).apply()
        _enabled.value = on
    }

    private companion object {
        const val KEY_HEARTBEAT = "heartbeat_sound"
    }
}
