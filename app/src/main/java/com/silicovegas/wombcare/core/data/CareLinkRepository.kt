package com.silicovegas.wombcare.core.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.silicovegas.wombcare.core.data.model.CareLink
import com.silicovegas.wombcare.core.data.model.CareLinkStatus
import com.silicovegas.wombcare.core.util.ShareCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Why a pasted code could not be turned into a link request. */
sealed interface LinkRequestError {
    data object Malformed : LinkRequestError       // failed ShareCode.normalise
    data object NotFound : LinkRequestError         // no patient owns that code
    data object AlreadyLinked : LinkRequestError    // a link already exists
    data class Unknown(val cause: Throwable) : LinkRequestError
}

@Singleton
class CareLinkRepository @Inject constructor(
    private val db: FirebaseDatabase,
) {
    private val random = SecureRandom()

    /**
     * Give a patient a unique share code and claim it.
     *
     * Claiming is a plain `setValue`, NOT a transaction — and that distinction is load-
     * bearing. A transaction reads the node first, and the security rules only let a DOCTOR
     * read `/shareCodes` (so codes can't be enumerated); a patient claiming her own code
     * would have that read denied, which is exactly why code generation failed on-device.
     *
     * A plain write needs no read permission, and the rule `.write: !data.exists() &&
     * newData.patientUid == auth.uid` already makes it safe: the server rejects a write onto
     * an existing code, so a taken candidate fails and we retry with a fresh one. Concurrent
     * claims of the same candidate serialise server-side — the second sees the node now
     * exists and is denied — so two patients can never share a code.
     */
    suspend fun claimShareCodeFor(patientUid: String, maxAttempts: Int = 5): String {
        repeat(maxAttempts) {
            val candidate = ShareCode.generate(random)
            val claimed = runCatching {
                db.getReference(DbPaths.shareCode(candidate))
                    .setValue(mapOf("patientUid" to patientUid)).await()
            }.isSuccess
            if (claimed) {
                db.getReference(DbPaths.patient(patientUid)).child("shareCode")
                    .setValue(candidate).await()
                return candidate
            }
        }
        error("could not claim a unique share code after $maxAttempts attempts")
    }

    /**
     * Doctor pastes a code. Resolve it to a patient and create a PENDING link.
     *
     * Resolving is a single `get` on a known key — the security rules forbid listing
     * `shareCodes`, so this is the only way a doctor can turn a code into a patient, and
     * only for a code they were given. The pending link then waits for the patient to
     * approve; the doctor gains no data access from this call alone.
     */
    suspend fun requestLink(
        doctorUid: String,
        doctorName: String,
        clinic: String,
        rawCode: String,
        nowMillis: Long,
    ): Result<String> {
        val code = ShareCode.normalise(rawCode)
            ?: return Result.failure(LinkRequestException(LinkRequestError.Malformed))
        return runCatching {
            val snap = db.getReference(DbPaths.shareCode(code)).get().await()
            val patientUid = snap.child("patientUid").getValue(String::class.java)
                ?: throw LinkRequestException(LinkRequestError.NotFound)

            val existing = db.getReference(DbPaths.careLink(doctorUid, patientUid)).get().await()
            val status = CareLinkStatus.from(existing.child("status").getValue(String::class.java))
            if (status == CareLinkStatus.PENDING || status == CareLinkStatus.ACTIVE) {
                throw LinkRequestException(LinkRequestError.AlreadyLinked)
            }

            // Mirror the link under both the doctor's and the patient's index so each side
            // can list their own without a query the rules would have to open up.
            val link = mapOf(
                "status" to CareLinkStatus.PENDING.wire,
                "doctorName" to doctorName,
                "clinic" to clinic,
                "requestedAt" to nowMillis,
            )
            db.reference.updateChildren(
                mapOf(
                    DbPaths.careLink(doctorUid, patientUid) to link,
                    DbPaths.careLinkByPatient(patientUid, doctorUid) to
                        mapOf("status" to CareLinkStatus.PENDING.wire, "doctorName" to doctorName),
                ),
            ).await()
            patientUid
        }
    }

    /**
     * Patient approves a doctor. One atomic multi-location write flips the link to ACTIVE
     * AND adds the doctor to `authorizedDoctors` — the flag the read rules check. Doing both
     * in one `updateChildren` means a doctor can never be "active" without read access, or
     * hold read access without an active link.
     */
    suspend fun approve(patientUid: String, doctorUid: String, nowMillis: Long): Result<Unit> =
        runCatching {
            db.reference.updateChildren(
                mapOf(
                    DbPaths.authorizedDoctor(patientUid, doctorUid) to true,
                    "${DbPaths.careLink(doctorUid, patientUid)}/status" to CareLinkStatus.ACTIVE.wire,
                    "${DbPaths.careLink(doctorUid, patientUid)}/respondedAt" to nowMillis,
                    "${DbPaths.careLinkByPatient(patientUid, doctorUid)}/status" to CareLinkStatus.ACTIVE.wire,
                ),
            ).await()
        }

    /** Deny a pending request, or revoke an active one — the exact mirror of [approve]. */
    suspend fun revoke(patientUid: String, doctorUid: String, nowMillis: Long): Result<Unit> =
        runCatching {
            db.reference.updateChildren(
                mapOf(
                    DbPaths.authorizedDoctor(patientUid, doctorUid) to null, // arrayRemove equivalent
                    "${DbPaths.careLink(doctorUid, patientUid)}/status" to CareLinkStatus.REVOKED.wire,
                    "${DbPaths.careLink(doctorUid, patientUid)}/respondedAt" to nowMillis,
                    "${DbPaths.careLinkByPatient(patientUid, doctorUid)}/status" to CareLinkStatus.REVOKED.wire,
                ),
            ).await()
        }

    /** Ensure the patient has a share code, claiming one if absent. Idempotent. */
    suspend fun ensureShareCode(patientUid: String): String {
        val existing = db.getReference(DbPaths.patient(patientUid)).child("shareCode")
            .get().await().getValue(String::class.java)
        return existing ?: claimShareCodeFor(patientUid)
    }

    // --- read flows (live) ----------------------------------------------------------------

    /** Requests the patient hasn't answered yet — drives the approve/deny prompt. */
    fun pendingRequestsForPatient(patientUid: String): Flow<List<CareLink>> =
        db.getReference(DbPaths.careLinksForPatient(patientUid)).valueFlow().map { snap ->
            snap.children.mapNotNull { it.toCareLink(patientUid = patientUid, doctorUid = it.key) }
                .filter { it.status == CareLinkStatus.PENDING }
        }

    /** Doctors currently allowed — drives the "Revoke" list. */
    fun activeDoctorsForPatient(patientUid: String): Flow<List<CareLink>> =
        db.getReference(DbPaths.careLinksForPatient(patientUid)).valueFlow().map { snap ->
            snap.children.mapNotNull { it.toCareLink(patientUid = patientUid, doctorUid = it.key) }
                .filter { it.status == CareLinkStatus.ACTIVE }
        }

    private fun DataSnapshot.toCareLink(patientUid: String, doctorUid: String?): CareLink? {
        val status = CareLinkStatus.from(child("status").getValue(String::class.java))
        if (doctorUid == null || status == CareLinkStatus.UNKNOWN) return null
        return CareLink(
            doctorUid = doctorUid,
            patientUid = patientUid,
            doctorName = child("doctorName").getValue(String::class.java).orEmpty(),
            clinic = child("clinic").getValue(String::class.java).orEmpty(),
            status = status,
        )
    }

}

class LinkRequestException(val error: LinkRequestError) : Exception()
