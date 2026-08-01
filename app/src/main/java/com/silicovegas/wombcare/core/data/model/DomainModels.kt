package com.silicovegas.wombcare.core.data.model

/** Which half of the app a signed-in user belongs to. Stored at `profiles/{uid}/role`. */
enum class UserRole { PATIENT, DOCTOR, UNKNOWN;
    companion object {
        fun from(raw: String?) = when (raw) {
            "patient" -> PATIENT
            "doctor" -> DOCTOR
            else -> UNKNOWN
        }
    }
    val wire: String get() = when (this) {
        PATIENT -> "patient"; DOCTOR -> "doctor"; UNKNOWN -> "unknown"
    }
}

/** Lifecycle of a doctor↔patient pairing. */
enum class CareLinkStatus { PENDING, ACTIVE, REVOKED, UNKNOWN;
    companion object {
        fun from(raw: String?) = when (raw) {
            "pending" -> PENDING
            "active" -> ACTIVE
            "revoked" -> REVOKED
            else -> UNKNOWN
        }
    }
    val wire: String get() = when (this) {
        PENDING -> "pending"; ACTIVE -> "active"; REVOKED -> "revoked"; UNKNOWN -> "unknown"
    }
}

/** The four documents a user must consent to. Each acceptance is one audit record. */
enum class ConsentType(val wire: String) {
    TERMS("terms"),
    PRIVACY("privacy"),
    DATA_SHARING("data_sharing"),
    NOT_A_MEDICAL_DEVICE("not_a_medical_device"),
}

data class UserProfile(
    val uid: String = "",
    val role: UserRole = UserRole.UNKNOWN,
    val fullName: String = "",
    val phone: String = "",
    val createdAt: Long = 0L,
)

data class DoctorInfo(
    val registrationNo: String = "",
    val clinic: String = "",
    val specialty: String = "",
    val verified: Boolean = false,
)

/**
 * The small map the patient's phone stamps on her own node each minute, so a doctor's
 * patient list shows a live dot from ONE node read instead of subscribing to the whole
 * readings stream. See PLAN.md §2.
 */
data class LiveStatus(
    val sessionId: String? = null,
    val nsp: Int = 0,
    val fhrBpm: Int? = null,
    val kickTotal: Int = 0,
    val confidence: Int = 0,
    val lastReadingAt: Long = 0L,
)

data class CareLink(
    val doctorUid: String = "",
    val patientUid: String = "",
    val doctorName: String = "",
    val clinic: String = "",
    val status: CareLinkStatus = CareLinkStatus.UNKNOWN,
    val requestedAt: Long = 0L,
    val respondedAt: Long = 0L,
)

/** A confirmed alert episode, as the doctor's feed shows it. */
data class AlertRecord(
    val id: String,
    val patientUid: String,
    val patientName: String,
    val sessionId: String,
    val nsp: Int,
    val createdAt: Long,
    val acknowledgedBy: String? = null,
    val acknowledgedAt: Long? = null,
) {
    val acknowledged: Boolean get() = acknowledgedBy != null
}

/** A row in the doctor's patient list — patient identity plus their latest live summary. */
data class PatientListEntry(
    val patientUid: String,
    val fullName: String,
    val pregnancyWeeks: Int?,
    val live: LiveStatus?,
) {
    /** "Live" only if the last reading is recent; otherwise the doctor sees "last seen". */
    fun isLive(nowMillis: Long, freshnessMillis: Long): Boolean =
        live != null && live.lastReadingAt > 0 &&
            (nowMillis - live.lastReadingAt) <= freshnessMillis
}
