package com.silicovegas.wombcare.core.device

import com.silicovegas.wombcare.core.ble.ClinicalUpdateParseResult
import com.silicovegas.wombcare.core.ble.ClinicalUpdateParser
import com.silicovegas.wombcare.core.ble.WellnessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertEvaluatorTest {

    private fun reading(
        nsp: WellnessStatus,
        confidence: Int = 85,
        signalLow: Boolean = false,
        minute: Int = 1,
    ) = (ClinicalUpdateParser.parse(
        ClinicalUpdateFrame.v1(nsp, confidence, fhrBpm = 150, kickCount = 0, deviceMinute = minute, signalLow = signalLow),
        receivedAtEpochMillis = 0,
    ) as ClinicalUpdateParseResult.Success).reading

    @Test
    fun `a single pathologic window does not alert — it needs two in a row`() {
        val e = AlertEvaluator()
        assertEquals(AlertDecision.ADVISORY, e.decisionFor(reading(WellnessStatus.PATHOLOGIC, minute = 1)))
        assertEquals(AlertDecision.ALERT, e.decisionFor(reading(WellnessStatus.PATHOLOGIC, minute = 2)))
    }

    @Test
    fun `a normal window between two pathologics resets the streak`() {
        val e = AlertEvaluator()
        e.decisionFor(reading(WellnessStatus.PATHOLOGIC, minute = 1))     // ADVISORY
        assertEquals(AlertDecision.NONE, e.decisionFor(reading(WellnessStatus.NORMAL, minute = 2)))
        // Next pathologic is again only the first — must not alert.
        assertEquals(AlertDecision.ADVISORY, e.decisionFor(reading(WellnessStatus.PATHOLOGIC, minute = 3)))
    }

    @Test
    fun `low signal turns a pathologic reading into a re-check, not an alarm`() {
        val e = AlertEvaluator()
        assertEquals(
            AlertDecision.RECHECK,
            e.decisionFor(reading(WellnessStatus.PATHOLOGIC, signalLow = true, minute = 1)),
        )
        // And even a second low-signal pathologic stays RECHECK, never ALERT.
        assertEquals(
            AlertDecision.RECHECK,
            e.decisionFor(reading(WellnessStatus.PATHOLOGIC, signalLow = true, minute = 2)),
        )
    }

    @Test
    fun `low confidence also downgrades a pathologic to re-check`() {
        val e = AlertEvaluator()
        assertEquals(
            AlertDecision.RECHECK,
            e.decisionFor(reading(WellnessStatus.PATHOLOGIC, confidence = 20, minute = 1)),
        )
    }

    @Test
    fun `suspect is advisory, normal is none`() {
        val e = AlertEvaluator()
        assertEquals(AlertDecision.ADVISORY, e.decisionFor(reading(WellnessStatus.SUSPECT)))
        assertEquals(AlertDecision.NONE, e.decisionFor(reading(WellnessStatus.NORMAL)))
    }
}
