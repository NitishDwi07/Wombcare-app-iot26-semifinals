package com.silicovegas.wombcare.feature.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silicovegas.wombcare.core.data.AuthRepository
import com.silicovegas.wombcare.core.data.CareLinkRepository
import com.silicovegas.wombcare.core.data.DoctorRepository
import com.silicovegas.wombcare.core.data.LinkRequestError
import com.silicovegas.wombcare.core.data.LinkRequestException
import com.silicovegas.wombcare.core.data.model.PatientListEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Result of a paste-a-code request, mapped to something the screen can display. */
sealed interface AddPatientResult {
    data object Idle : AddPatientResult
    data object Submitting : AddPatientResult
    data object Requested : AddPatientResult
    data class Error(val message: String) : AddPatientResult
}

@HiltViewModel
class DoctorViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val doctorRepo: DoctorRepository,
    private val careLinks: CareLinkRepository,
) : ViewModel() {

    private val _patients = MutableStateFlow<List<PatientListEntry>>(emptyList())
    val patients: StateFlow<List<PatientListEntry>> = _patients.asStateFlow()

    private val _addResult = MutableStateFlow<AddPatientResult>(AddPatientResult.Idle)
    val addResult: StateFlow<AddPatientResult> = _addResult.asStateFlow()

    init {
        val uid = auth.currentUid
        if (uid != null) {
            doctorRepo.linkedPatients(uid)
                .catch { /* offline/revoked: keep last */ }
                .onEach { _patients.value = it }
                .launchIn(viewModelScope)
        }
    }

    fun requestLink(rawCode: String) {
        val uid = auth.currentUid ?: return
        _addResult.value = AddPatientResult.Submitting
        viewModelScope.launch {
            val profile = auth.loadProfile(uid)
            val result = careLinks.requestLink(
                doctorUid = uid,
                doctorName = profile?.fullName ?: "Doctor",
                clinic = "",
                rawCode = rawCode,
                nowMillis = System.currentTimeMillis(),
            )
            _addResult.value = result.fold(
                onSuccess = { AddPatientResult.Requested },
                onFailure = { AddPatientResult.Error(friendly(it)) },
            )
        }
    }

    fun resetAdd() { _addResult.value = AddPatientResult.Idle }

    private fun friendly(t: Throwable): String = when (val e = (t as? LinkRequestException)?.error) {
        LinkRequestError.Malformed -> "That code doesn't look right. Check and try again."
        LinkRequestError.NotFound ->
            "No patient found for that code. Ask her to open Share, turn Sharing on, and read " +
                "you the current code."
        LinkRequestError.AlreadyLinked -> "You've already requested or have access to this patient."
        is LinkRequestError.Unknown -> messageFor(e.cause)
        null -> messageFor(t)
    }

    /**
     * Turn a raw failure into something specific enough to act on — a permission denial and a
     * dropped connection need different fixes, and collapsing both into "check your connection"
     * is what made this impossible to diagnose. The raw message is included as a last resort.
     */
    private fun messageFor(t: Throwable): String {
        val msg = ((t.message ?: "") + " " + (t.cause?.message ?: "")).lowercase()
        return when {
            "permission" in msg || "permission_denied" in msg ->
                "Access denied by the server. Make sure this account is a doctor account and " +
                    "the patient generated a fresh code with Sharing switched on."
            "network" in msg || "offline" in msg || "unavailable" in msg || "timeout" in msg ->
                "Couldn't reach the server. Check your connection and try again."
            "app check" in msg || "appcheck" in msg || "attestation" in msg ->
                "Blocked by App Check. This build isn't registered for verification yet."
            else -> "Couldn't send the request: ${t.message ?: "unknown error"}"
        }
    }
}
