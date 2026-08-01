package com.silicovegas.wombcare.core.data

/**
 * Every Realtime Database path in one place. Nothing else in the app builds a path from
 * string literals — a typo in a path is a silent "permission denied" at runtime that the
 * compiler can't catch, so the paths live here and are used by reference.
 *
 * The tree mirrors database.rules.json exactly; if you add a node, add its rule in the same
 * change.
 */
object DbPaths {
    fun profile(uid: String) = "profiles/$uid"
    fun doctor(uid: String) = "doctors/$uid"

    fun patient(uid: String) = "patients/$uid"
    fun authorizedDoctors(patientUid: String) = "patients/$patientUid/authorizedDoctors"
    fun authorizedDoctor(patientUid: String, doctorUid: String) =
        "patients/$patientUid/authorizedDoctors/$doctorUid"
    fun liveStatus(patientUid: String) = "patients/$patientUid/liveStatus"
    fun sessions(patientUid: String) = "patients/$patientUid/sessions"
    fun session(patientUid: String, sessionId: String) =
        "patients/$patientUid/sessions/$sessionId"
    fun readings(patientUid: String, sessionId: String) =
        "patients/$patientUid/sessions/$sessionId/readings"
    fun reading(patientUid: String, sessionId: String, minute: Int) =
        "patients/$patientUid/sessions/$sessionId/readings/$minute"
    fun alerts(patientUid: String) = "patients/$patientUid/alerts"
    fun alert(patientUid: String, alertId: String) = "patients/$patientUid/alerts/$alertId"

    fun shareCode(code: String) = "shareCodes/$code"

    fun careLink(doctorUid: String, patientUid: String) = "careLinks/$doctorUid/$patientUid"
    fun careLinksForDoctor(doctorUid: String) = "careLinks/$doctorUid"
    fun careLinkByPatient(patientUid: String, doctorUid: String) =
        "careLinksByPatient/$patientUid/$doctorUid"
    fun careLinksForPatient(patientUid: String) = "careLinksByPatient/$patientUid"

    fun consents(uid: String) = "consents/$uid"
}
