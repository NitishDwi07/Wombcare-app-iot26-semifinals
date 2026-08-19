package com.silicovegas.wombcare.feature.doctor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.data.AuthRepository
import com.silicovegas.wombcare.core.data.DoctorRepository
import com.silicovegas.wombcare.core.data.model.LiveStatus
import com.silicovegas.wombcare.core.data.model.PatientThresholds
import com.silicovegas.wombcare.core.data.model.SessionSummary
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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DoctorDetailUiState(
    val patientUid: String = "",
    val live: LiveStatus? = null,
    val readings: List<ClinicalReading> = emptyList(),
    val sessions: List<SessionSummary> = emptyList(),
    val thresholds: PatientThresholds = PatientThresholds.DEFAULT,
    /**
     * When the doctor last tapped Reset. The live session view hides everything received at
     * or before this instant, so old/previous-session numbers clear until FRESH data arrives.
     * It never deletes the patient's stored data — only what this screen currently shows.
     */
    val clearedAtMillis: Long = 0L,
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
    private val auth: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val patientUid: String = savedStateHandle["patientUid"] ?: ""
    private val doctorUid: String? = auth.currentUid

    private val _ui = MutableStateFlow(DoctorDetailUiState(patientUid = patientUid))
    val ui: StateFlow<DoctorDetailUiState> = _ui.asStateFlow()

    /**
     * Clear the current session view. Marks "now" as the cut-off; the screen then shows only
     * data received after this, so a stale/previous session's numbers disappear until the
     * patient sends something new. Does NOT touch stored data or the patient's record.
     */
    fun clearSession() {
        _ui.value = _ui.value.copy(clearedAtMillis = System.currentTimeMillis())
    }

    /** Persist the doctor's thresholds for this patient. Optimistic: the live flow confirms. */
    fun saveThresholds(t: PatientThresholds) {
        val doc = doctorUid ?: return
        _ui.value = _ui.value.copy(thresholds = t)
        viewModelScope.launch { runCatching { doctorRepo.setThresholds(doc, patientUid, t) } }
    }

    init {
        val liveFlow = doctorRepo.patientLive(patientUid)

        if (doctorUid != null) {
            doctorRepo.thresholds(doctorUid, patientUid)
                .catch { }
                .onEach { t -> _ui.value = _ui.value.copy(thresholds = t) }
                .launchIn(viewModelScope)
        }

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

        // Session history for the trends card (cheap per-session summaries, not readings).
        doctorRepo.recentSessions(patientUid)
            .catch { }
            .onEach { sessions -> _ui.value = _ui.value.copy(sessions = sessions) }
            .launchIn(viewModelScope)
    }
}
