package com.silicovegas.wombcare.core.device

import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ble.ClinicalUpdateParseResult
import com.silicovegas.wombcare.core.ble.ClinicalUpdateParser
import com.silicovegas.wombcare.core.ble.ConnectionState
import com.silicovegas.wombcare.core.ble.MotionState
import com.silicovegas.wombcare.core.ble.WellnessStatus
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
                // Emit payload v3 (the Wombcare_8PreFinal frame) so demo mode exercises the
                // real 15-byte decode, including the CTG analytics and the explicit motion
                // byte. Battery is injected onto the parsed reading to mimic the device's
                // SEPARATE Battery Service channel (a slow, believable drain from ~92%).
                val battery = (92 - minute / 4).coerceIn(70, 92)
                val ctg = ctgFor(w)
                emit(
                    ClinicalUpdateFrame.v3(
                        nsp = w.nsp,
                        confidence = w.confidence,
                        fhrBpm = w.fhrBpm,
                        kickCount = w.kicksThisWindow,
                        deviceMinute = minute + 1, // 0 reserved for the subscribe frame
                        motionState = w.motionState,
                        meanHrBpm = ctg.meanHr,
                        mstvBpm = ctg.mstv,
                        mltvBpm = ctg.mltv,
                        accelPerMin = ctg.accel,
                        decelPerMin = ctg.decel,
                        hrSdBpm = ctg.hrSd,
                        // Believable maternal HR: a resting ~82 bpm that ticks up a little when
                        // she's moving (motion != resting), so demo shows a live mother's pulse.
                        maternalHrBpm = if (w.motionState == MotionState.RESTING) 82 else 92,
                        signalLow = w.signalLow,
                    ),
                    batteryPercent = battery,
                )
                _connectionState.value = ConnectionState.Monitoring
                minute++
            }
        }
    }

    /** Plausible CTG analytics for a demo window (bpm units), telling a coherent clinical
     *  story: healthy windows have good variability + accelerations and no decelerations;
     *  pathologic windows show reduced variability, decelerations and no accelerations. */
    private data class Ctg(
        val meanHr: Int, val accel: Double, val decel: Double,
        val mstv: Double, val mltv: Double, val hrSd: Double,
    )

    private fun ctgFor(w: DemoWindow): Ctg = when (w.nsp) {
        WellnessStatus.NORMAL -> Ctg(w.fhrBpm, accel = 3.0, decel = 0.0, mstv = 6.5, mltv = 12.0, hrSd = 4.5)
        WellnessStatus.SUSPECT -> Ctg(w.fhrBpm, accel = 1.0, decel = 1.0, mstv = 3.5, mltv = 8.0, hrSd = 6.0)
        WellnessStatus.PATHOLOGIC -> Ctg(w.fhrBpm, accel = 0.0, decel = 2.5, mstv = 1.5, mltv = 4.0, hrSd = 8.0)
        WellnessStatus.UNKNOWN -> Ctg(w.fhrBpm, accel = 0.0, decel = 0.0, mstv = 0.0, mltv = 0.0, hrSd = 0.0)
    }

    override fun disconnect() {
        job?.cancel()
        job = null
        _connectionState.value = ConnectionState.Idle
    }

    /** No bond in demo mode — forget is just a disconnect, and always "succeeds". */
    override fun forget(): Boolean {
        disconnect()
        return true
    }

    /**
     * Encode → decode through the production parser, then publish the reading. [batteryPercent]
     * is merged onto the decoded reading the same way [BleDeviceSource] merges the standard
     * Battery Service value — the v3 payload itself never carries battery.
     */
    private suspend fun emit(bytes: ByteArray, batteryPercent: Int? = null) {
        when (val r = ClinicalUpdateParser.parse(bytes, now())) {
            is ClinicalUpdateParseResult.Success ->
                _readings.emit(
                    if (batteryPercent != null) r.reading.copy(batteryPercent = batteryPercent)
                    else r.reading,
                )
            else -> Unit // the simulator only ever produces valid frames; ignore otherwise
        }
    }
}
