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
 * Layout: docs/BLE_CONTRACT.md §3 (v1, 8 bytes) and §4 (v2, 10 bytes).
 */
object ClinicalUpdateParser {

    const val PAYLOAD_V1_SIZE = 8
    const val PAYLOAD_V2_SIZE = 10

    private const val FLAG_ALERT = 0x01
    private const val FLAG_SIGNAL_LOW = 0x02
    private const val FLAG_ACTIVE = 0x04

    /** `null` for a version this build cannot decode. */
    fun expectedSizeFor(version: Int): Int? = when (version) {
        1 -> PAYLOAD_V1_SIZE
        2 -> PAYLOAD_V2_SIZE
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

        val flags = bytes.u8(5)
        val rawFhr = bytes.u8(3)

        return ClinicalUpdateParseResult.Success(
            ClinicalReading(
                payloadVersion = version,
                status = statusOf(bytes.u8(1)),
                confidencePercent = bytes.u8(2).coerceIn(0, 100),
                // 0 means "no lock", not zero bpm. Nulling it here is the whole reason
                // no chart or average downstream can be dragged toward zero.
                fhrBpm = if (rawFhr == 0) null else rawFhr,
                kickCountInWindow = bytes.u8(4),
                alert = flags and FLAG_ALERT != 0,
                signalLow = flags and FLAG_SIGNAL_LOW != 0,
                motherActive = flags and FLAG_ACTIVE != 0,
                // uint16 little-endian: bytes 6-7.
                deviceMinute = bytes.u8(6) or (bytes.u8(7) shl 8),
                motionState = if (version >= 2) motionOf(bytes.u8(8)) else null,
                batteryPercent = if (version >= 2) bytes.u8(9).coerceIn(0, 100) else null,
                receivedAtEpochMillis = receivedAtEpochMillis,
            ),
        )
    }

    private fun statusOf(raw: Int): WellnessStatus = when (raw) {
        0 -> WellnessStatus.NORMAL
        1 -> WellnessStatus.SUSPECT
        2 -> WellnessStatus.PATHOLOGIC
        else -> WellnessStatus.UNKNOWN
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
