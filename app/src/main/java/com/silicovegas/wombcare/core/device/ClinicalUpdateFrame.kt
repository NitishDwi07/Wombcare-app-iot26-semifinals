package com.silicovegas.wombcare.core.device

import com.silicovegas.wombcare.core.ble.MotionState
import com.silicovegas.wombcare.core.ble.WellnessStatus

/**
 * Builds the exact 8-byte payload the firmware sends (docs/BLE_CONTRACT.md §3).
 *
 * This is the simulator's single point of contact with the wire format: it produces real
 * bytes that the production `ClinicalUpdateParser` then decodes. Keeping encode and decode
 * as separate, mirror-image pieces of code means a round-trip test can catch either side
 * drifting from the contract.
 */
object ClinicalUpdateFrame {

    const val FLAG_ALERT = 0x01
    const val FLAG_SIGNAL_LOW = 0x02
    const val FLAG_ACTIVE = 0x04

    fun v1(
        nsp: WellnessStatus,
        confidence: Int,
        fhrBpm: Int,          // 0 == no lock / invalid
        kickCount: Int,
        deviceMinute: Int,
        signalLow: Boolean = false,
        motherActive: Boolean = false,
    ): ByteArray {
        var flags = 0
        if (nsp == WellnessStatus.PATHOLOGIC) flags = flags or FLAG_ALERT
        if (signalLow) flags = flags or FLAG_SIGNAL_LOW
        if (motherActive) flags = flags or FLAG_ACTIVE

        return byteArrayOf(
            1,                                    // version
            nspByte(nsp),
            confidence.coerceIn(0, 100).toByte(),
            fhrBpm.coerceIn(0, 255).toByte(),
            kickCount.coerceIn(0, 255).toByte(),
            flags.toByte(),
            (deviceMinute and 0xFF).toByte(),     // little-endian lo
            ((deviceMinute shr 8) and 0xFF).toByte(), // hi
        )
    }

    /**
     * Payload v2 (10 bytes) — v1 plus a motion-state byte and a battery-percent byte
     * (docs/BLE_CONTRACT.md §4). The simulator emits this so demo mode shows real Motion and
     * Battery values; real v1 hardware still degrades gracefully (those tiles read "--").
     */
    fun v2(
        nsp: WellnessStatus,
        confidence: Int,
        fhrBpm: Int,
        kickCount: Int,
        deviceMinute: Int,
        motionState: MotionState,
        batteryPct: Int,
        signalLow: Boolean = false,
        motherActive: Boolean = false,
    ): ByteArray {
        val v1 = v1(nsp, confidence, fhrBpm, kickCount, deviceMinute, signalLow, motherActive)
        v1[0] = 2 // bump version
        return v1 + byteArrayOf(
            motionByte(motionState),
            batteryPct.coerceIn(0, 100).toByte(),
        )
    }

    private fun nspByte(nsp: WellnessStatus): Byte = when (nsp) {
        WellnessStatus.NORMAL -> 0
        WellnessStatus.SUSPECT -> 1
        WellnessStatus.PATHOLOGIC -> 2
        WellnessStatus.UNKNOWN -> 0
    }

    private fun motionByte(m: MotionState): Byte = when (m) {
        MotionState.RESTING, MotionState.UNKNOWN -> 0
        MotionState.SITTING -> 1
        MotionState.WALKING -> 2
    }
}
