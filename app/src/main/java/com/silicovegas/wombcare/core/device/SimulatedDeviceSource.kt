package com.silicovegas.wombcare.core.device

import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ble.ClinicalUpdateParseResult
import com.silicovegas.wombcare.core.ble.ClinicalUpdateParser
import com.silicovegas.wombcare.core.ble.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A fully working WombCare "device" with no hardware. It plays [DemoScript.ARC] by
 * **encoding each scripted window to the real 8-byte payload and decoding it with the
 * production [ClinicalUpdateParser]** — so demo mode runs the exact code path a real device
 * would, invalid-FHR nulling and all. This is what makes the app demoable while the
 * firmware BLE stack is still being finished, without a second, drifting fake data path.
 *
 * The clock is compressed: [windowIntervalMillis] defaults to 3 s so the whole clinical arc
 * plays in ~30 s for a demo. Pass 60_000 for real-time behaviour.
 */
class SimulatedDeviceSource(
    private val scope: CoroutineScope,
    private val windowIntervalMillis: Long = 3_000L,
    private val now: () -> Long = { System.currentTimeMillis() },
) : WombCareDeviceSource {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _readings = MutableSharedFlow<ClinicalReading>(extraBufferCapacity = 16)
    override val readings: Flow<ClinicalReading> = _readings.asSharedFlow()

    override val sourceLabel: String = "Demo mode"

    private var job: Job? = null

    override fun connect(deviceId: String?) {
        if (job?.isActive == true) return
        _connectionState.value = ConnectionState.Connecting
        job = scope.launch {
            // A real connect takes a moment; mimic it so the UI's Connecting state is visible.
            delay(minOf(windowIntervalMillis / 4, 800L))
            _connectionState.value = ConnectionState.Connected

            // The firmware pushes the last-known (all-zero) frame on subscribe. Reproduce it,
            // so the app's "waiting for first reading" placeholder path is exercised too.
            emit(ClinicalUpdateFrame.v1(
                nsp = com.silicovegas.wombcare.core.ble.WellnessStatus.NORMAL,
                confidence = 0, fhrBpm = 0, kickCount = 0, deviceMinute = 0,
            ))

            var minute = 0
            while (isActive) {
                delay(windowIntervalMillis)
                val w = DemoScript.windowAt(minute)
                // Emit payload v2 so demo mode shows Motion and Battery too — a slow, believable
                // battery drain from ~92% rather than a static number.
                val battery = (92 - minute / 4).coerceIn(70, 92)
                emit(
                    ClinicalUpdateFrame.v2(
                        nsp = w.nsp,
                        confidence = w.confidence,
                        fhrBpm = w.fhrBpm,
                        kickCount = w.kicksThisWindow,
                        deviceMinute = minute + 1, // 0 reserved for the subscribe frame
                        motionState = w.motionState,
                        batteryPct = battery,
                        signalLow = w.signalLow,
                        motherActive = w.motherActive,
                    ),
                )
                _connectionState.value = ConnectionState.Monitoring
                minute++
            }
        }
    }

    override fun disconnect() {
        job?.cancel()
        job = null
        _connectionState.value = ConnectionState.Idle
    }

    /** Encode → decode through the production parser, then publish the reading. */
    private suspend fun emit(bytes: ByteArray) {
        when (val r = ClinicalUpdateParser.parse(bytes, now())) {
            is ClinicalUpdateParseResult.Success -> _readings.emit(r.reading)
            else -> Unit // the simulator only ever produces valid frames; ignore otherwise
        }
    }
}
