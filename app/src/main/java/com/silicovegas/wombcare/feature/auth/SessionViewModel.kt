package com.silicovegas.wombcare.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silicovegas.wombcare.core.data.AuthRepository
import com.silicovegas.wombcare.core.data.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the app should be, given who (if anyone) is signed in. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val uid: String, val role: UserRole) : SessionState
}

/**
 * The single source of truth for top-level routing. It watches Firebase auth state and, on
 * sign-in, resolves the user's role from the database before declaring [SessionState.
 * SignedIn].
 *
 * The role read is RETRIED, and this matters: `createUserWithEmailAndPassword` flips auth
 * state to "signed in" the instant the account exists — which is BEFORE the signup flow has
 * finished writing `profiles/{uid}/role`. A single read here would see no role, conclude
 * "signed out", and bounce a brand-new user (especially a doctor, who has an extra write)
 * straight back to the auth screen. So we poll a few times, staying in [Loading], and only
 * fall back to [SignedOut] if the role genuinely never appears. `collectLatest` cancels an
 * in-flight resolution if auth state changes again (e.g. a fast sign-out).
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authState.collectLatest { uid ->
                if (uid == null) {
                    _state.value = SessionState.SignedOut
                    return@collectLatest
                }
                _state.value = SessionState.Loading
                _state.value = resolveRole(uid)
            }
        }
    }

    private suspend fun resolveRole(uid: String): SessionState {
        // ~2.5s of retries covers the signup write landing; a returning user hits it first try.
        repeat(MAX_ROLE_ATTEMPTS) { attempt ->
            val role = authRepository.loadRole(uid)
            if (role != UserRole.UNKNOWN) return SessionState.SignedIn(uid, role)
            if (attempt < MAX_ROLE_ATTEMPTS - 1) delay(ROLE_RETRY_MILLIS)
        }
        return SessionState.SignedOut
    }

    fun signOut() = authRepository.signOut()

    private companion object {
        const val MAX_ROLE_ATTEMPTS = 6
        const val ROLE_RETRY_MILLIS = 400L
    }
}
