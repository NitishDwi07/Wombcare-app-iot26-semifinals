package com.silicovegas.wombcare.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The BLE contract, enforced. Every test here corresponds to a rule in
 * docs/BLE_CONTRACT.md §3 — if firmware changes, one of these should go red.
 */
class ClinicalUpdateParserTest {

    private val at = 1_754_000_000_000L

    private fun v1(
        version: Int = 1,
        nsp: Int = 0,
        confidence: Int = 87,
        fhr: Int = 142,
        kicks: Int = 8,
        flags: Int = 0,
        minute: Int = 0,
    ) = byteArrayOf(
        version.toByte(),
        nsp.toByte(),
        confidence.toByte(),
        fhr.toByte(),
        kicks.toByte(),
        flags.toByte(),
        (minute and 0xFF).toByte(),
        (minute shr 8 and 0xFF).toByte(),
    )

    private fun success(bytes: ByteArray): ClinicalReading {
        val result = ClinicalUpdateParser.parse(bytes, at)
        assertTrue("expected Success, got $result", result is ClinicalUpdateParseResult.Success)
        return (result as ClinicalUpdateParseResult.Success).reading
    }

    @Test
    fun `decodes a typical v1 window`() {
        val r = success(v1())
        assertEquals(1, r.payloadVersion)
        assertEquals(WellnessStatus.NORMAL, r.status)
        assertEquals(87, r.confidencePercent)
        assertEquals(142, r.fhrBpm)
        assertEquals(8, r.kickCountInWindow)
        assertEquals(at, r.receivedAtEpochMillis)
    }

    @Test
    fun `fhr of zero means no lock, not zero bpm`() {
        val r = success(v1(fhr = 0))
        assertNull(r.fhrBpm)
        assertFalse(r.hasValidFhr)
    }

    @Test
    fun `fhr above 127 stays unsigned`() {
        // 200 as a signed Byte is -56. Getting 200 back proves the masking works.
        assertEquals(200, success(v1(fhr = 200)).fhrBpm)
        assertEquals(255, success(v1(fhr = 255)).fhrBpm)
    }

    @Test
    fun `timestamp is little-endian`() {
        // bytes 6-7 = 0x01,0x02 -> 0x0201 = 513, not 0x0102 = 258.
        assertEquals(513, success(v1(minute = 513)).deviceMinute)
        assertEquals(65535, success(v1(minute = 65535)).deviceMinute)
    }

    @Test
    fun `flag bits decode independently`() {
        val r = success(v1(nsp = 2, flags = 0x01 or 0x02 or 0x04))
        assertEquals(WellnessStatus.PATHOLOGIC, r.status)
        assertTrue(r.alert)
        assertTrue(r.signalLow)
        assertTrue(r.motherActive)
        assertFalse(r.confidenceIsTrustworthy)

        val quiet = success(v1(flags = 0x00))
        assertFalse(quiet.alert)
        assertFalse(quiet.signalLow)
        assertFalse(quiet.motherActive)
        assertTrue(quiet.confidenceIsTrustworthy)
    }

    @Test
    fun `all-zero subscribe frame is a placeholder, not a reading of zero`() {
        // What the firmware pushes on CCCD enable before any window has completed.
        val r = success(byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0))
        assertTrue(r.isPlaceholder)
        assertNull(r.fhrBpm)
    }

    @Test
    fun `a real reading is never a placeholder`() {
        assertFalse(success(v1()).isPlaceholder)
    }

    @Test
    fun `v1 has no motion or battery and derives motion from confidence`() {
        // No motion byte in v1 → motion is inferred from confidence (movement lowers it).
        val resting = success(v1(confidence = 90, flags = 0x00))
        assertNull(resting.motionState)
        assertNull(resting.batteryPercent)
        assertEquals(MotionDisplay.RESTING, resting.motionDisplay)   // >= 85

        assertEquals(MotionDisplay.SITTING, success(v1(confidence = 70)).motionDisplay) // 65..84
        assertEquals(MotionDisplay.WALKING, success(v1(confidence = 40)).motionDisplay) // < 65

        // The SIGNAL_LOW flag (0x02) forces WALKING even when confidence is high.
        assertEquals(MotionDisplay.WALKING, success(v1(confidence = 95, flags = 0x02)).motionDisplay)
    }

    @Test
    fun `v2 adds motion state and battery`() {
        val bytes = v1(version = 2) + byteArrayOf(2, 85)
        val r = success(bytes)
        assertEquals(2, r.payloadVersion)
        assertEquals(MotionState.WALKING, r.motionState)
        assertEquals(MotionDisplay.WALKING, r.motionDisplay)
        assertEquals(85, r.batteryPercent)
    }

    @Test
    fun `trailing bytes are tolerated, not rejected`() {
        // A future firmware appending fields must not break this build.
        val r = success(v1() + byteArrayOf(9, 9, 9))
        assertEquals(142, r.fhrBpm)
    }

    @Test
    fun `a truncated frame is malformed`() {
        val result = ClinicalUpdateParser.parse(byteArrayOf(1, 0, 87, 142.toByte()), at)
        assertTrue(result is ClinicalUpdateParseResult.Malformed)
    }

    @Test
    fun `an empty notification is malformed`() {
        assertTrue(
            ClinicalUpdateParser.parse(ByteArray(0), at) is ClinicalUpdateParseResult.Malformed,
        )
        assertTrue(
            ClinicalUpdateParser.parse(null, at) is ClinicalUpdateParseResult.Malformed,
        )
    }

    @Test
    fun `an unknown version is refused rather than guessed`() {
        val result = ClinicalUpdateParser.parse(v1(version = 9), at)
        assertTrue(result is ClinicalUpdateParseResult.UnsupportedVersion)
        assertEquals(9, (result as ClinicalUpdateParseResult.UnsupportedVersion).version)
    }

    @Test
    fun `out-of-range values are contained`() {
        // An unexpected NSP is surfaced as Suspect (a caution), never Unknown or Normal.
        val r = success(v1(nsp = 7, confidence = 250))
        assertEquals(WellnessStatus.SUSPECT, r.status)
        assertEquals(100, r.confidencePercent)
    }
}
