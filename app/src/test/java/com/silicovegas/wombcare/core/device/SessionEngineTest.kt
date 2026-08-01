package com.silicovegas.wombcare.core.device

import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ble.ClinicalUpdateParseResult
import com.silicovegas.wombcare.core.ble.ClinicalUpdateParser
import com.silicovegas.wombcare.core.ble.WellnessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {

    private var clock = 1_000L
    private var idCounter = 0
    private fun engine() = SessionEngine(now = { clock }, newSessionId = { "s${idCounter++}" })

    private fun reading(
        nsp: WellnessStatus = WellnessStatus.NORMAL,
        fhr: Int = 140,
        kicks: Int = 0,
        minute: Int,
        confidence: Int = 85,
        signalLow: Boolean = false,
    ): ClinicalReading = (ClinicalUpdateParser.parse(
        ClinicalUpdateFrame.v1(nsp, confidence, fhr, kicks, minute, signalLow),
        receivedAtEpochMillis = clock,
    ) as ClinicalUpdateParseResult.Success).reading

    @Test
    fun `kicks accumulate across windows`() {
        val e = engine()
        e.onReading(reading(kicks = 3, minute = 1))
        e.onReading(reading(kicks = 2, minute = 2))
        val snap = e.onReading(reading(kicks = 4, minute = 3)).snapshot
        assertEquals(9, snap.totalKicks)
        assertEquals(3, snap.windowCount)
    }

    @Test
    fun `average FHR ignores invalid (no-lock) windows`() {
        val e = engine()
        e.onReading(reading(fhr = 140, minute = 1))
        e.onReading(reading(fhr = 0, minute = 2))   // no lock -> parsed to null, excluded
        val snap = e.onReading(reading(fhr = 160, minute = 3)).snapshot
        assertEquals(150, snap.averageFhr) // (140+160)/2, the 0 does not drag it down
    }

    @Test
    fun `average FHR is null when no window ever locked`() {
        val e = engine()
        val snap = e.onReading(reading(fhr = 0, minute = 1)).snapshot
        assertNull(snap.averageFhr)
    }

    @Test
    fun `worst status is the most severe seen`() {
        val e = engine()
        e.onReading(reading(nsp = WellnessStatus.NORMAL, minute = 1))
        e.onReading(reading(nsp = WellnessStatus.SUSPECT, minute = 2))
        e.onReading(reading(nsp = WellnessStatus.NORMAL, minute = 3))
        assertEquals(
            WellnessStatus.SUSPECT,
            e.onReading(reading(nsp = WellnessStatus.NORMAL, minute = 4)).snapshot.worstStatus,
        )
    }

    @Test
    fun `a backwards device-minute starts a new session (reboot)`() {
        val e = engine()
        val first = e.onReading(reading(minute = 40)).snapshot.sessionId
        e.onReading(reading(minute = 41))
        val update = e.onReading(reading(minute = 1)) // device rebooted -> minute reset
        assertTrue(update.startedNewSession)
        assertEquals(1, update.snapshot.windowCount) // fresh session, only this reading
        assertTrue(update.snapshot.sessionId != first)
    }

    @Test
    fun `the demo arc alerts exactly once, on the second consecutive pathologic`() {
        val e = engine()
        val decisions = mutableListOf<AlertDecision>()
        val alerts = mutableListOf<AlertEvent>()

        // Play the real demo script through encode -> parse -> engine.
        DemoScript.ARC.forEachIndexed { i, w ->
            val bytes = ClinicalUpdateFrame.v1(
                w.nsp, w.confidence, w.fhrBpm, w.kicksThisWindow,
                deviceMinute = i + 1, signalLow = w.signalLow, motherActive = w.motherActive,
            )
            val reading = (ClinicalUpdateParser.parse(bytes, clock) as ClinicalUpdateParseResult.Success).reading
            val update = e.onReading(reading)
            decisions += update.snapshot.lastDecision
            update.alert?.let { alerts += it }
        }

        // Arc index 4 = first Pathologic (ADVISORY), index 5 = second (ALERT),
        // index 6 = Pathologic but signal-low (RECHECK).
        assertEquals(AlertDecision.ADVISORY, decisions[4])
        assertEquals(AlertDecision.ALERT, decisions[5])
        assertEquals(AlertDecision.RECHECK, decisions[6])
        assertEquals(1, alerts.count { it.decision == AlertDecision.ALERT })
    }
}
