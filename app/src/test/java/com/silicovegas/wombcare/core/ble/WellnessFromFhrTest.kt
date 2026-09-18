package com.silicovegas.wombcare.core.ble

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The live-device FHR band rule (WombCare app display rule, not the firmware NSP):
 * 110–200 bpm is Normal; below 110 or above 200 is Elevated. Pathologic is never shown by
 * this live rule. Boundaries are inclusive.
 */
class WellnessFromFhrTest {

    @Test
    fun `110 to 200 inclusive is normal`() {
        assertEquals(WellnessStatus.NORMAL, wellnessFromFhr(110))
        assertEquals(WellnessStatus.NORMAL, wellnessFromFhr(150))
        assertEquals(WellnessStatus.NORMAL, wellnessFromFhr(180))
        assertEquals(WellnessStatus.NORMAL, wellnessFromFhr(200))
    }

    @Test
    fun `below 110 is elevated (bradycardia)`() {
        assertEquals(WellnessStatus.SUSPECT, wellnessFromFhr(109))
        assertEquals(WellnessStatus.SUSPECT, wellnessFromFhr(80))
    }

    @Test
    fun `above 200 is elevated (tachycardia)`() {
        assertEquals(WellnessStatus.SUSPECT, wellnessFromFhr(201))
        assertEquals(WellnessStatus.SUSPECT, wellnessFromFhr(240))
    }

    @Test
    fun `never returns pathologic`() {
        for (bpm in 30..300) {
            assertEquals(false, wellnessFromFhr(bpm) == WellnessStatus.PATHOLOGIC)
        }
    }
}
