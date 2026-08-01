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

    private fun friendly(t: Throwable): String = when ((t as? LinkRequestException)?.error) {
        LinkRequestError.Malformed -> "That code doesn't look right. Check and try again."
        LinkRequestError.NotFound -> "No patient found for that code."
        LinkRequestError.AlreadyLinked -> "You've already requested or have access to this patient."
        else -> "Couldn't send the request. Check your connection and try again."
    }
}
