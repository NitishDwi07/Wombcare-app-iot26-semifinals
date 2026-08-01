package com.silicovegas.wombcare.feature.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silicovegas.wombcare.core.data.AuthRepository
import com.silicovegas.wombcare.core.data.DoctorRepository
import com.silicovegas.wombcare.core.data.model.AlertRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsFeedViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val doctorRepo: DoctorRepository,
) : ViewModel() {

    private val _alerts = MutableStateFlow<List<AlertRecord>>(emptyList())
    val alerts: StateFlow<List<AlertRecord>> = _alerts.asStateFlow()

    init {
        auth.currentUid?.let { uid ->
            doctorRepo.alertsFeed(uid)
                .catch { }
                .onEach { _alerts.value = it }
                .launchIn(viewModelScope)
        }
    }

    fun acknowledge(alert: AlertRecord) {
        val uid = auth.currentUid ?: return
        viewModelScope.launch {
            runCatching {
                doctorRepo.acknowledgeAlert(alert.patientUid, alert.id, uid, System.currentTimeMillis())
            }
        }
    }
}
