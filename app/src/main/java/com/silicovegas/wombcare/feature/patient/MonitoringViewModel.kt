package com.silicovegas.wombcare.feature.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silicovegas.wombcare.core.ble.ConnectionState
import com.silicovegas.wombcare.core.data.AlertRepository
import com.silicovegas.wombcare.core.data.AuthRepository
import com.silicovegas.wombcare.core.data.PatientSyncRepository
import com.silicovegas.wombcare.core.data.SharingPreference
import com.silicovegas.wombcare.core.device.AlertDecision
import com.silicovegas.wombcare.core.device.AlertEvent
import com.silicovegas.wombcare.core.device.DeviceSourceProvider
import com.silicovegas.wombcare.core.notify.AlertNotifier
import com.silicovegas.wombcare.core.device.SessionEngine
import com.silicovegas.wombcare.core.device.SessionSnapshot
import com.silicovegas.wombcare.core.device.WombCareDeviceSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class MonitoringUiState(
    val connection: ConnectionState = ConnectionState.Idle,
    val session: SessionSnapshot? = null,
    val sourceLabel: String = "",
) {
    /** Live and receiving, but the first genuine window hasn't landed — show "waiting". */
    val waitingForFirstReading: Boolean
        get() = connection.isLive && (session == null || session.latest?.isPlaceholder != false)

    val isMonitoring: Boolean get() = connection == ConnectionState.Monitoring
}

/**
 * Drives the patient's live screen. It owns the [SessionEngine], subscribes to whichever
 * [WombCareDeviceSource] the demo toggle selects, and turns the reading stream into a
 * [MonitoringUiState] the dashboard renders. Alert events are surfaced on a separate
 * [SharedFlow] so the UI can fire a notification exactly once per episode rather than
 * re-notifying on every recomposition.
 */
@HiltViewModel
class MonitoringViewModel @Inject constructor(
    private val sourceProvider: DeviceSourceProvider,
    private val alertNotifier: AlertNotifier,
    private val authRepository: AuthRepository,
    private val syncRepository: PatientSyncRepository,
    private val alertRepository: AlertRepository,
    private val sharingPreference: SharingPreference,
) : ViewModel() {

    init { alertNotifier.ensureChannels() }

    private val engine = SessionEngine(
        now = { System.currentTimeMillis() },
        newSessionId = { UUID.randomUUID().toString() },
    )

    private val _ui = MutableStateFlow(MonitoringUiState())
    val ui: StateFlow<MonitoringUiState> = _ui.asStateFlow()

    private val _alerts = MutableSharedFlow<AlertEvent>(extraBufferCapacity = 8)
    val alerts: SharedFlow<AlertEvent> = _alerts.asSharedFlow()

    private var source: WombCareDeviceSource? = null
    private var readingJob: Job? = null
    private var stateJob: Job? = null

    /**
     * The last device the mother connected to, remembered across Stop so Start can reconnect
     * straight to it — no scan screen, and (because the bond persists) no PIN prompt. Cleared
     * only by [forgetDevice].
     */
    private var lastDeviceId: String? = null

    /** Whether starting now will open a real Bluetooth connection (needs BLE permissions). */
    fun usesRealBle(): Boolean = sourceProvider.isRealDevice()

    /** The remembered device to reconnect to, or null if we've never connected / after forget. */
    fun rememberedDeviceId(): String? = lastDeviceId

    /**
     * Start a session. [deviceId] is the Bluetooth address the user picked in the scan
     * screen; null means "demo mode" (the simulator ignores it) or "scan for one" (the BLE
     * source falls back to a filtered scan). Passing a specific address skips scanning and
     * connects straight to the chosen device.
     */
    fun start(deviceId: String? = null) {
        if (readingJob?.isActive == true) return
        if (deviceId != null) lastDeviceId = deviceId // remember for a no-rescan resume
        val src = sourceProvider.current()
        source = src
        _ui.update { it.copy(sourceLabel = src.sourceLabel) }

        stateJob = viewModelScope.launch {
            src.connectionState.collect { cs -> _ui.update { it.copy(connection = cs) } }
        }
        readingJob = viewModelScope.launch {
            src.readings.collect { reading ->
                val update = engine.onReading(reading)
                _ui.update { it.copy(session = update.snapshot) }
                update.alert?.let {
                    alertNotifier.notifyAlert(it) // system notification (no-op if unpermitted)
                    _alerts.emit(it)              // in-app banner
                }
                // Push to the cloud ONLY when signed in and sharing is on — otherwise nothing
                // about the session leaves the phone. Real readings only (skip placeholders).
                val uid = authRepository.currentUid
                if (uid != null && sharingPreference.isSharingEnabled() && !reading.isPlaceholder) {
                    runCatching { syncRepository.pushWindow(uid, update.snapshot, reading) }
                    // A confirmed alert also becomes a record the doctor's feed can see.
                    if (update.alert?.decision == AlertDecision.ALERT) {
                        runCatching {
                            alertRepository.writeAlert(uid, update.snapshot.sessionId, reading)
                        }
                    }
                }
            }
        }
        src.connect(deviceId)
    }

    fun stop() {
        source?.disconnect()
        readingJob?.cancel(); readingJob = null
        stateJob?.cancel(); stateJob = null
        engine.endSession()
        _ui.update { MonitoringUiState() }
    }

    /**
     * Forget the paired device: stop, then remove the bond so the next connect re-prompts
     * for the PIN. Resolves the current source directly (a session may not be running).
     */
    fun forgetDevice() {
        val src = source ?: sourceProvider.current()
        src.forget() // Settings surfaces the manual-unpair fallback; here we just tear down.
        lastDeviceId = null // forgotten → next Start must scan and re-pair
        readingJob?.cancel(); readingJob = null
        stateJob?.cancel(); stateJob = null
        engine.endSession()
        _ui.update { MonitoringUiState() }
    }

    override fun onCleared() {
        source?.disconnect()
    }
}
