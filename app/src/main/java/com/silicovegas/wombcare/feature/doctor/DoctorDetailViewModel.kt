package com.silicovegas.wombcare.feature.doctor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.data.DoctorRepository
import com.silicovegas.wombcare.core.data.model.LiveStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class DoctorDetailUiState(
    val patientUid: String = "",
    val live: LiveStatus? = null,
    val readings: List<ClinicalReading> = emptyList(),
)

/**
 * The doctor's live view of one patient. It subscribes to the patient's `liveStatus`, and —
 * following whichever session that points at — to that session's readings, so the charts
 * track the mother's current session as it grows. If she revokes access, the rules cancel
 * both listeners and the streams simply stop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DoctorDetailViewModel @Inject constructor(
    private val doctorRepo: DoctorRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val patientUid: String = savedStateHandle["patientUid"] ?: ""

    private val _ui = MutableStateFlow(DoctorDetailUiState(patientUid = patientUid))
    val ui: StateFlow<DoctorDetailUiState> = _ui.asStateFlow()

    init {
        val liveFlow = doctorRepo.patientLive(patientUid)

        liveFlow
            .catch { }
            .onEach { live -> _ui.value = _ui.value.copy(live = live) }
            .launchIn(viewModelScope)

        // Re-point the readings subscription whenever the live session id changes.
        liveFlow
            .flatMapLatest { live ->
                val sid = live?.sessionId
                if (sid.isNullOrEmpty()) flowOf(emptyList()) else doctorRepo.sessionReadings(patientUid, sid)
            }
            .catch { }
            .onEach { readings -> _ui.value = _ui.value.copy(readings = readings) }
            .launchIn(viewModelScope)
    }
}
