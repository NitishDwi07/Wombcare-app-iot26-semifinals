package com.silicovegas.wombcare.core.device

import com.silicovegas.wombcare.core.ble.ClinicalUpdateParseResult
import com.silicovegas.wombcare.core.ble.ClinicalUpdateParser
import com.silicovegas.wombcare.core.ble.MotionState
import com.silicovegas.wombcare.core.ble.WellnessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Encode (simulator) and decode (production parser) are mirror images of the wire contract.
 * If either drifts, this round trip breaks — which is the point of testing them together.
 */
class ClinicalUpdateFrameTest {

    private fun roundTrip(bytes: ByteArray) =
        (ClinicalUpdateParser.parse(bytes, 0) as ClinicalUpdateParseResult.Success).reading

    @Test
    fun `a normal window round-trips field for field`() {
        val r = roundTrip(
            ClinicalUpdateFrame.v1(
                nsp = WellnessStatus.NORMAL, confidence = 87, fhrBpm = 142,
                kickCount = 8, deviceMinute = 513,
            ),
        )
        assertEquals(WellnessStatus.NORMAL, r.status)
        assertEquals(87, r.confidencePercent)
        assertEquals(142, r.fhrBpm)
        assertEquals(8, r.kickCountInWindow)
        assertEquals(513, r.deviceMinute) // proves the LE encode matches the LE decode
    }

    @Test
    fun `pathologic sets the alert flag on the wire`() {
        val r = roundTrip(
            ClinicalUpdateFrame.v1(WellnessStatus.PATHOLOGIC, 80, 170, 0, deviceMinute = 5),
        )
        assertEquals(WellnessStatus.PATHOLOGIC, r.status)
        assertTrue(r.alert)
    }

    @Test
    fun `zero fhr encodes and decodes as no lock`() {
        val r = roundTrip(ClinicalUpdateFrame.v1(WellnessStatus.NORMAL, 0, 0, 0, deviceMinute = 0))
        assertNull(r.fhrBpm)
        assertTrue(r.isPlaceholder)
    }

    @Test
    fun `signal-low and active flags survive the round trip independently`() {
        val r = roundTrip(
            ClinicalUpdateFrame.v1(
                WellnessStatus.NORMAL, 40, 150, 1, deviceMinute = 4,
                signalLow = true, motherActive = true,
            ),
        )
        assertTrue(r.signalLow)
        assertTrue(r.motherActive)
        assertFalse(r.confidenceIsTrustworthy)
    }

    // ---- Payload v3 (Wombcare_8PreFinal, 15 bytes) --------------------------------------

    @Test
    fun `v3 decodes nsp from the flag byte and the CTG block to real units`() {
        val r = roundTrip(
            ClinicalUpdateFrame.v3(
                nsp = WellnessStatus.SUSPECT, confidence = 77, fhrBpm = 148, kickCount = 6,
                deviceMinute = 300, motionState = MotionState.SITTING,
                meanHrBpm = 150, mstvBpm = 6.5, mltvBpm = 12.0,
                accelPerMin = 3.0, decelPerMin = 1.0, hrSdBpm = 4.5,
            ),
        )
        assertEquals(3, r.payloadVersion)
        assertEquals(WellnessStatus.SUSPECT, r.status) // proves NSP came out of flags bits 3-4
        assertEquals(77, r.confidencePercent)
        assertEquals(148, r.fhrBpm)
        assertEquals(6, r.kickCountInWindow)
        assertEquals(300, r.deviceMinute)
        assertEquals(MotionState.SITTING, r.motionState)
        assertEquals(150, r.meanHrBpm)
        // Scaled bytes round-trip to their real units (within the 1/scale quantisation).
        assertEquals(6.5, r.mstvBpm!!, 0.05)
        assertEquals(12.0, r.mltvBpm!!, 0.25) // mltv is x4, so step is 0.25
        assertEquals(3.0, r.accelPerMin!!, 0.05)
        assertEquals(1.0, r.decelPerMin!!, 0.05)
        assertEquals(4.5, r.hrSdBpm!!, 0.05)
    }

    @Test
    fun `v3 analysis-failed is shown as Suspect, never Unknown or Normal`() {
        // Build a raw v3 frame with NSP = 3 in flag bits 3-4.
        val bytes = ByteArray(ClinicalUpdateParser.PAYLOAD_V3_SIZE)
        bytes[0] = 3
        bytes[1] = (3 shl 3).toByte() // NSP = 3 (analysis failed)
        bytes[3] = 140.toByte()
        val r = roundTrip(bytes)
        assertEquals(WellnessStatus.SUSPECT, r.status)
    }

    @Test
    fun `v3 sensor fault nulls the CTG block so zeros are not shown as measured`() {
        val bytes = ByteArray(ClinicalUpdateParser.PAYLOAD_V3_SIZE)
        bytes[0] = 3
        bytes[1] = (1 shl 2).toByte() // FLAG_SENSOR_FAULT
        val r = roundTrip(bytes)
        assertTrue(r.signalLow)
        assertNull(r.mstvBpm)
        assertNull(r.mltvBpm)
        assertNull(r.accelPerMin)
        assertNull(r.meanHrBpm)
    }
}
