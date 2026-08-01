package com.silicovegas.wombcare.core.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.silicovegas.wombcare.core.data.model.UserRole
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Delete my data" — the GDPR-shaped erase from Settings.
 *
 * It unwinds the whole cloud footprint a patient can reach, then deletes the auth account.
 * Each step is best-effort (`runCatching`) so one failure — say an already-absent node —
 * doesn't strand the account half-deleted; the account deletion itself is what the user is
 * really asking for and is attempted last regardless.
 *
 * Order matters: read the share code BEFORE deleting the patient node (which holds it), and
 * null out the doctor-side link mirrors (which the patient is allowed to write) so no
 * dangling "active" link survives pointing at a patient who no longer exists.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseDatabase,
) {
    suspend fun deleteMyData(uid: String, role: UserRole): Result<Unit> = runCatching {
        if (role == UserRole.PATIENT) deletePatientData(uid) else deleteDoctorData(uid)

        // Common to both roles.
        runCatching { db.getReference(DbPaths.profile(uid)).removeValue().await() }
        runCatching { db.getReference(DbPaths.consents(uid)).removeValue().await() }

        // Finally, the auth account. If this fails (e.g. requires recent login), the caller
        // surfaces it and the user can re-authenticate and retry.
        auth.currentUser?.delete()?.await()
        Unit
    }

    private suspend fun deletePatientData(uid: String) {
        // Free the share code first — it lives on the patient node we're about to remove.
        val code = runCatching {
            db.getReference(DbPaths.patient(uid)).child("shareCode").get().await()
                .getValue(String::class.java)
        }.getOrNull()
        if (code != null) runCatching { db.getReference(DbPaths.shareCode(code)).removeValue().await() }

        // Null every doctor-side link mirror the patient granted (patient is allowed to).
        runCatching {
            val links = db.getReference(DbPaths.careLinksForPatient(uid)).get().await()
            for (doctor in links.children) {
                val doctorUid = doctor.key ?: continue
                runCatching {
                    db.getReference(DbPaths.careLink(doctorUid, uid)).removeValue().await()
                }
            }
        }
        runCatching { db.getReference(DbPaths.careLinksForPatient(uid)).removeValue().await() }

        // The patient node: readings, sessions, alerts, liveStatus, authorizedDoctors — all of it.
        runCatching { db.getReference(DbPaths.patient(uid)).removeValue().await() }
    }

    private suspend fun deleteDoctorData(uid: String) {
        runCatching { db.getReference(DbPaths.doctor(uid)).removeValue().await() }
        runCatching { db.getReference(DbPaths.careLinksForDoctor(uid)).removeValue().await() }
    }
}
