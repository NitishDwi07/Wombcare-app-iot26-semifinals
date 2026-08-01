package com.silicovegas.wombcare

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds use the App Check debug provider. On first run it prints a debug token to
 * logcat; register that token in Firebase console → App Check to let a dev build through
 * once enforcement is turned on. (Enforcement is off by default, so this "just works" for
 * local development until you flip it on.)
 */
fun installAppCheck(app: Application) {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
