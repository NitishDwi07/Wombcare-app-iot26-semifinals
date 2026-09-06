package com.silicovegas.wombcare.core.data

import android.content.Context

/**
 * Remembers whether the patient has seen the "How to use" walkthrough, so it auto-opens once
 * on first launch and never nags again. A plain [android.content.SharedPreferences] flag read
 * synchronously from a composable — a single boolean doesn't justify DataStore or DI.
 */
class HowToPreference(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("wombcare_howto", Context.MODE_PRIVATE)

    fun hasSeen(): Boolean = prefs.getBoolean(KEY_SEEN, false)
    fun markSeen() { prefs.edit().putBoolean(KEY_SEEN, true).apply() }

    private companion object { const val KEY_SEEN = "seen" }
}
