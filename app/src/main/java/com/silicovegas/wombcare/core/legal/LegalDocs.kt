package com.silicovegas.wombcare.core.legal

import com.silicovegas.wombcare.core.data.model.ConsentType

/**
 * In-app Terms and Privacy, versioned. The version travels into every `consents` record so
 * the audit trail says *which* text a user accepted — and a bump can trigger re-consent.
 *
 * These are plain, honest starter documents for a hackathon build, written to satisfy the
 * Play Store requirement and, above all, the non-negotiable framing from WOMBCARE_HANDOFF.md:
 * WombCare is a wellness aid, not a medical device. They are NOT a substitute for review by
 * a lawyer before any real-world deployment.
 */
object LegalDocs {

    /** Bump when the text materially changes; old consents then read as a prior version. */
    const val TERMS_VERSION = "2026-07-31"
    const val PRIVACY_VERSION = "2026-07-31"

    /** The medical-disclaimer consent isn't a document screen — it's the third checkbox. */
    const val DISCLAIMER_VERSION = "2026-07-31"

    fun versionFor(type: ConsentType): String = when (type) {
        ConsentType.TERMS -> TERMS_VERSION
        ConsentType.PRIVACY -> PRIVACY_VERSION
        ConsentType.NOT_A_MEDICAL_DEVICE -> DISCLAIMER_VERSION
        ConsentType.DATA_SHARING -> DISCLAIMER_VERSION
    }

    val TERMS = Document(
        title = "Terms of Service",
        version = TERMS_VERSION,
        body = """
            **Last updated: 31 July 2026**

            ## 1. What WombCare is
            WombCare is a home wellness-awareness aid for expectant mothers. It reads signals
            from a wearable device and shows fetal heart rate, movement (kick) counts, and a
            wellness indicator (Normal / Suspect / Pathologic).

            ## 2. What WombCare is NOT
            **WombCare is not a medical device and does not diagnose any condition.** It does
            not replace antenatal care, a doctor, a midwife, or emergency services. Never
            delay seeking care because of anything WombCare shows or does not show. If you
            feel something is wrong, contact your healthcare provider or emergency services
            immediately, regardless of what the app displays.

            ## 3. Your account
            You are responsible for keeping your login secure. Provide accurate information.
            If you register as a doctor, you confirm you are a registered healthcare
            professional entitled to view the data patients choose to share with you.

            ## 4. Sharing with a doctor
            Sharing is off by default and entirely your choice. When you share a code and
            approve a request, that clinician can view the readings you have chosen to share.
            You can revoke access at any time. Revoking stops future access; a clinician may
            have already seen data shared before revocation.

            ## 5. No warranty
            The service is provided "as is". Sensor readings and the wellness indicator can be
            wrong, delayed, or unavailable — for example when the device is worn incorrectly,
            during movement, or when the battery is low. We do not warrant accuracy,
            completeness, or availability.

            ## 6. Limitation of liability
            To the maximum extent permitted by law, WombCare and its team are not liable for
            any decision made, or not made, in reliance on the app.

            ## 7. Changes
            We may update these terms. Material changes will ask for your renewed acceptance.
        """.trimIndent(),
    )

    val PRIVACY = Document(
        title = "Privacy Policy",
        version = PRIVACY_VERSION,
        body = """
            **Last updated: 31 July 2026**

            ## 1. Our approach
            WombCare is built privacy-first. All interpretation of your body's signals happens
            **on the device you wear** — the raw heart and movement waveforms never leave it.

            ## 2. What we store
            - **On your phone:** your monitoring sessions and their summaries.
            - **In the cloud, only if you enable sharing:** small per-minute summaries
              (heart rate, kick count, wellness indicator, confidence) for the sessions you
              choose to share, so an approved doctor can view them.
            - Your name, email, and (for doctors) registration details.
            - A record of the consents you accepted, with dates and versions.

            ## 3. What we NEVER store off your device
            Raw ECG or movement waveforms. The on-device model. Anything from a session you
            did not choose to share.

            ## 4. Who can see your data
            Only you, and the specific doctors you approve by code. You can revoke any
            doctor's access at any time. No data is sold or used for advertising.

            ## 5. Deleting your data
            You can delete your account and data from Settings. Cloud copies of shared
            summaries are removed; local session history on your phone is erased.

            ## 6. Children and sensitive data
            WombCare handles health-related information about you and your pregnancy. We treat
            it as sensitive and restrict access to the people you explicitly authorise.

            ## 7. Contact
            For any privacy question, contact the WombCare team through the app store listing.
        """.trimIndent(),
    )

    fun byKey(key: String): Document? = when (key) {
        "terms" -> TERMS
        "privacy" -> PRIVACY
        else -> null
    }

    data class Document(val title: String, val version: String, val body: String)
}
