package com.silicovegas.wombcare

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release builds attest with Play Integrity. Requires the app's signing SHA-256 to be
 * registered in Firebase console → App Check (and the app installed via a path Play
 * Integrity trusts). Until App Check enforcement is enabled in the console this is a no-op
 * in practice, so a sideloaded release still reaches Firebase.
 */
fun installAppCheck(app: Application) {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
