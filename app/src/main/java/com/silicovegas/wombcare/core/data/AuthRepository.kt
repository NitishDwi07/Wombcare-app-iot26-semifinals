package com.silicovegas.wombcare.core.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.silicovegas.wombcare.core.data.model.ConsentType
import com.silicovegas.wombcare.core.data.model.DoctorInfo
import com.silicovegas.wombcare.core.data.model.UserProfile
import com.silicovegas.wombcare.core.data.model.UserRole
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Signed-in user with the app-specific role resolved from the database. */
data class AuthedUser(val uid: String, val role: UserRole, val profile: UserProfile?)

/**
 * Owns account lifecycle and the one write that must be atomic with signup: the
 * `profiles/{uid}/role`. The whole security model keys off that role, so a user who exists
 * in Auth but has no profile row is a broken state — [signUp] writes the profile before it
 * returns, and surfaces a failure if that write is rejected.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseDatabase,
) {
    val currentUid: String? get() = auth.currentUser?.uid

    /** Emits on every sign-in/out so navigation can route by presence + role. */
    val authState: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String): Result<String> = runCatching {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        auth.currentUser?.uid ?: error("sign-in returned no user")
    }

    /**
     * Create the account, write the profile, and record the accepted consents — as close to
     * atomic as the two systems allow. If the profile write fails, the freshly created Auth
     * user is deleted so a retry starts clean rather than colliding on the email.
     */
    suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        role: UserRole,
        acceptedConsents: List<Pair<ConsentType, String>>, // (type, docVersion)
        nowMillis: Long,
        doctorInfo: DoctorInfo? = null,
    ): Result<String> = runCatching {
        val cred = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val uid = cred.user?.uid ?: error("sign-up returned no user")
        try {
            val updates = hashMapOf<String, Any?>(
                DbPaths.profile(uid) to mapOf(
                    "role" to role.wire,
                    "fullName" to fullName.trim(),
                    "createdAt" to nowMillis,
                ),
            )
            acceptedConsents.forEachIndexed { i, (type, version) ->
                updates["${DbPaths.consents(uid)}/c$i"] = mapOf(
                    "docType" to type.wire,
                    "docVersion" to version,
                    "acceptedAt" to nowMillis,
                )
            }
            db.reference.updateChildren(updates).await()

            // Doctor details are a SEPARATE, BEST-EFFORT write. It's separate because the
            // doctors/{uid} rule checks the committed profile role, which only exists after
            // the write above lands. It's best-effort because the registration details are
            // non-essential — the role in the profile is what the whole app keys off. A
            // failure here must NOT roll back the account (which would leave the user unable
            // to sign up at all), so it's caught and ignored rather than rethrown.
            if (role == UserRole.DOCTOR && doctorInfo != null) {
                runCatching {
                    db.getReference(DbPaths.doctor(uid)).setValue(
                        mapOf(
                            "registrationNo" to doctorInfo.registrationNo.trim(),
                            "clinic" to doctorInfo.clinic.trim(),
                            "specialty" to doctorInfo.specialty.trim(),
                            "verified" to false,
                        ),
                    ).await()
                }
            }
            uid
        } catch (t: Throwable) {
            // Roll back the orphaned Auth user so the email is reusable.
            runCatching { auth.currentUser?.delete()?.await() }
            throw t
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() = auth.signOut()

    suspend fun loadRole(uid: String): UserRole = runCatching {
        val snap = db.getReference(DbPaths.profile(uid)).child("role").get().await()
        UserRole.from(snap.getValue(String::class.java))
    }.getOrDefault(UserRole.UNKNOWN)

    suspend fun loadProfile(uid: String): UserProfile? = runCatching {
        val snap = db.getReference(DbPaths.profile(uid)).get().await()
        UserProfile(
            uid = uid,
            role = UserRole.from(snap.child("role").getValue(String::class.java)),
            fullName = snap.child("fullName").getValue(String::class.java).orEmpty(),
            phone = snap.child("phone").getValue(String::class.java).orEmpty(),
            createdAt = snap.child("createdAt").getValue(Long::class.java) ?: 0L,
        )
    }.getOrNull()
}
