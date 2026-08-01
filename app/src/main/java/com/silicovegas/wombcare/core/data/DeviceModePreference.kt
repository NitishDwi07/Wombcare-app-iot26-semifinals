package com.silicovegas.wombcare.core.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The demo-mode toggle (Settings → Demo mode). A plain [SharedPreferences] boolean rather
 * than DataStore, because the device layer needs it *synchronously* when deciding which
 * source to open on connect, and a single boolean doesn't justify a suspend read.
 *
 * Defaults to **demo ON**: the app is fully demoable out of the box, and the real firmware
 * BLE stack isn't finished yet, so a fresh install should show live-looking data rather
 * than an empty scan.
 */
@Singleton
class DeviceModePreference @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("wombcare_device", Context.MODE_PRIVATE)

    fun isDemoMode(): Boolean = prefs.getBoolean(KEY_DEMO, true)

    fun setDemoMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEMO, enabled).apply()
    }

    private companion object {
        const val KEY_DEMO = "demo_mode"
    }
}
