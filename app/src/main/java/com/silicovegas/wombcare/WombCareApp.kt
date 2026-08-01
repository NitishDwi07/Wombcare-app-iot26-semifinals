package com.silicovegas.wombcare

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * App Check attests that requests come from a genuine build of this app, not a scraped
 * config talking straight to the REST API. It's the backstop that makes the "a doctor who
 * knows a code can resolve it" rule safe.
 *
 * The provider differs per build type (Play Integrity for release, the debug provider for
 * dev), and the debug provider isn't on the release classpath — so the actual install lives
 * in [installAppCheck], which has a separate implementation in the `debug` and `release`
 * source sets. This class stays variant-agnostic.
 */
@HiltAndroidApp
class WombCareApp : Application() {
    override fun onCreate() {
        super.onCreate()
        installAppCheck(this)
    }
}
