package com.silicovegas.wombcare.core.ble

/** Outcome of decoding one GATT notification. */
sealed interface ClinicalUpdateParseResult {
    data class Success(val reading: ClinicalReading) : ClinicalUpdateParseResult

    /** Firmware is newer than this app build. Prompt an update; never guess the layout. */
    data class UnsupportedVersion(val version: Int) : ClinicalUpdateParseResult

    data class Malformed(val reason: String) : ClinicalUpdateParseResult
}

/**
 * Decodes the `Clinical Update` characteristic. Both the real [BleDeviceSource] and the
 * demo simulator feed bytes through *this* function, so demo mode exercises production
 * decoding rather than a parallel fake path.
 *
 * Layout: docs/BLE_CONTRACT.md §3 (v1, 8 bytes), §4 (v2, 10 bytes), §5 (v3, 15 bytes), §6
 * (v4, 16 bytes — the current Wombcare_8PreFinal frame: v3 plus a maternal-HR byte and a
 * second flags byte). v3/v4 are NOT an append to v1: `flags` moves to byte 1 and carries the
 * NSP class in bits 3-4, so they are decoded on their own path.
 */
object ClinicalUpdateParser {

    const val PAYLOAD_V1_SIZE = 8
    const val PAYLOAD_V2_SIZE = 10
    const val PAYLOAD_V3_SIZE = 15
    const val PAYLOAD_V4_SIZE = 16

    // v1/v2 flag byte (byte 5).
    private const val FLAG_ALERT = 0x01
    private const val FLAG_SIGNAL_LOW = 0x02
    private const val FLAG_ACTIVE = 0x04

    // v3/v4 flag byte (byte 1) — different bit assignment entirely.
    private const val V3_FLAG_SENSOR_FAULT = 1 shl 2 // DSP rejected this window
    private const val V3_NSP_SHIFT = 3               // NSP occupies bits 3-4
    private const val V3_NSP_MASK = 0x3 shl V3_NSP_SHIFT
    private const val V3_FLAG_MOTION = 1 shl 5       // motion detected (low IMU trust)
    private const val V3_FLAG_SUST_BRADY = 1 shl 6   // sustained bradycardia
    private const val V3_FLAG_PERSIST_ALERT = 1 shl 7 // persistent clinical alert

    // v4 second flag byte (byte 15).
    private const val F2_FETAL_NOT_DETECTED = 1 shl 0 // sustained loss of fetal heartbeat
    private const val F2_MHR_VALID = 1 shl 2          // byte 14 is a real maternal HR
    private const val F2_FETAL_IS_MATERNAL = 1 shl 4  // sensor is on the mother, reposition

    /** `null` for a version this build cannot decode. */
    fun expectedSizeFor(version: Int): Int? = when (version) {
        1 -> PAYLOAD_V1_SIZE
        2 -> PAYLOAD_V2_SIZE
        3 -> PAYLOAD_V3_SIZE
        4 -> PAYLOAD_V4_SIZE
        else -> null
    }

    fun parse(bytes: ByteArray?, receivedAtEpochMillis: Long): ClinicalUpdateParseResult {
        if (bytes == null || bytes.isEmpty()) {
            return ClinicalUpdateParseResult.Malformed("empty notification")
        }

        // The version byte is read before anything else, exactly as the firmware header
        // instructs ("app checks first").
        val version = bytes.u8(0)
        val expectedSize = expectedSizeFor(version)
            ?: return ClinicalUpdateParseResult.UnsupportedVersion(version)

        // `>=` not `==`: a future firmware may append fields. Read by offset and tolerate
        // trailing bytes instead of rejecting a frame we can still decode.
        if (bytes.size < expectedSize) {
            return ClinicalUpdateParseResult.Malformed(
                "payload v$version needs $expectedSize bytes, got ${bytes.size}",
            )
        }

        val reading = if (version >= 3) parseModern(bytes, version, receivedAtEpochMillis)
        else parseLegacy(bytes, version, receivedAtEpochMillis)
        return ClinicalUpdateParseResult.Success(reading)
    }

    /** v1 (8 bytes) and v2 (10 bytes): nsp at byte 1, flags at byte 5, timestamp at 6-7. */
    private fun parseLegacy(bytes: ByteArray, version: Int, receivedAt: Long): ClinicalReading {
        val flags = bytes.u8(5)
        val rawFhr = bytes.u8(3)
        return ClinicalReading(
            payloadVersion = version,
            status = statusOf(bytes.u8(1)),
            confidencePercent = bytes.u8(2).coerceIn(0, 100),
            // 0 means "no lock", not zero bpm. Nulling it here is the whole reason no chart
            // or average downstream can be dragged toward zero.
            fhrBpm = if (rawFhr == 0) null else rawFhr,
            kickCountInWindow = bytes.u8(4),
            alert = flags and FLAG_ALERT != 0,
            signalLow = flags and FLAG_SIGNAL_LOW != 0,
            motherActive = flags and FLAG_ACTIVE != 0,
            deviceMinute = bytes.u8(6) or (bytes.u8(7) shl 8),
            motionState = if (version >= 2) motionOf(bytes.u8(8)) else null,
            batteryPercent = if (version >= 2) bytes.u8(9).coerceIn(0, 100) else null,
            receivedAtEpochMillis = receivedAt,
        )
    }

    /**
     * v3 (15 bytes) and v4 (16 bytes, current Wombcare_8PreFinal). Bytes 0-13 are identical:
     * flags at byte 1 (NSP in bits 3-4), the CTG features scaled into bytes 5-10, timestamp at
     * 11-12, an explicit motion byte at 13. v4 adds byte 14 (maternal HR, valid only when the
     * MHR_VALID bit of the byte-15 flags2 is set) and byte 15 (flags2). Battery is NOT in the
     * payload — it rides the standard Battery Service. On a sensor-fault window bytes 5-10 are
     * meaningless zeros (CTG → null), but the maternal HR is still valid (measured separately).
     */
    private fun parseModern(bytes: ByteArray, version: Int, receivedAt: Long): ClinicalReading {
        val flags = bytes.u8(1)
        val flags2 = if (version >= 4) bytes.u8(15) else 0
        val rawFhr = bytes.u8(3)
        val sensorFault = flags and V3_FLAG_SENSOR_FAULT != 0
        val rawMeanHr = bytes.u8(9)
        val mhrValid = flags2 and F2_MHR_VALID != 0

        fun ctg(raw: Int, divisor: Double): Double? =
            if (sensorFault) null else raw / divisor

        return ClinicalReading(
            payloadVersion = version,
            status = statusOf((flags and V3_NSP_MASK) shr V3_NSP_SHIFT), // 3 -> Suspect
            confidencePercent = bytes.u8(2).coerceIn(0, 100),
            fhrBpm = if (rawFhr == 0) null else rawFhr,
            kickCountInWindow = bytes.u8(4),
            alert = flags and V3_FLAG_PERSIST_ALERT != 0 || flags and V3_FLAG_SUST_BRADY != 0,
            // A sensor fault is exactly the "signal unreliable" condition the UI dims for.
            signalLow = sensorFault,
            motherActive = flags and V3_FLAG_MOTION != 0,
            deviceMinute = bytes.u8(11) or (bytes.u8(12) shl 8),
            motionState = motionOf(bytes.u8(13)),
            batteryPercent = null, // standard Battery Service, merged in by BleDeviceSource
            receivedAtEpochMillis = receivedAt,
            meanHrBpm = if (sensorFault || rawMeanHr == 0) null else rawMeanHr,
            mstvBpm = ctg(bytes.u8(5), 10.0), // byte 5 is bpm x10
            mltvBpm = ctg(bytes.u8(6), 4.0),  // byte 6 is bpm x4
            accelPerMin = ctg(bytes.u8(7), 10.0),
            decelPerMin = ctg(bytes.u8(8), 10.0),
            hrSdBpm = ctg(bytes.u8(10), 10.0),
            // v4 only: maternal HR (byte 14) is trustworthy only when the device says so.
            maternalHrBpm = if (version >= 4 && mhrValid) bytes.u8(14).takeIf { it > 0 } else null,
            fetalNotDetected = flags2 and F2_FETAL_NOT_DETECTED != 0,
            fetalIsMaternal = flags2 and F2_FETAL_IS_MATERNAL != 0,
        )
    }

    private fun statusOf(raw: Int): WellnessStatus = when (raw) {
        0 -> WellnessStatus.NORMAL
        2 -> WellnessStatus.PATHOLOGIC
        // 1 = Suspect, and 3 ("analysis failed") is shown as Suspect too: never "Unknown"
        // (confusing) and never "Normal" (unsafe) — a caution the mother/doctor can act on.
        else -> WellnessStatus.SUSPECT
    }

    private fun motionOf(raw: Int): MotionState = when (raw) {
        0 -> MotionState.RESTING
        1 -> MotionState.SITTING
        2 -> MotionState.WALKING
        else -> MotionState.UNKNOWN
    }

    /** Kotlin's `Byte` is signed; every payload field is unsigned. */
    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF
}
