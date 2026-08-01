package com.silicovegas.wombcare.core.device

import com.silicovegas.wombcare.core.ble.MotionState
import com.silicovegas.wombcare.core.ble.WellnessStatus

/** One scripted minute the simulator will emit, as fields (encoded to bytes at send time). */
data class DemoWindow(
    val nsp: WellnessStatus,
    val confidence: Int,
    val fhrBpm: Int,
    val kicksThisWindow: Int,
    val signalLow: Boolean = false,
    val motherActive: Boolean = false,
    val motionState: MotionState = MotionState.RESTING,
)

/**
 * The demo's clinical arc: reassuring start → a wobble → deterioration to Pathologic →
 * recovery. It's written to exercise every safety rule the app has, in order:
 *
 *  - minutes 0-2  NORMAL, healthy FHR, kicks accumulating — the happy path.
 *  - minute 3     a WALKING window with low confidence + signal-low — proves confidence
 *                 dims and motion shows "Active", and that a bad-signal minute is handled.
 *  - minute 4     first PATHOLOGIC — must NOT alert yet (confirm-and-persist needs two).
 *  - minute 5     second PATHOLOGIC in a row — THIS is where the alert fires.
 *  - minute 6     PATHOLOGIC but signal-low — becomes RECHECK, not another alarm.
 *  - minutes 7-8  SUSPECT then NORMAL — recovery; the alert streak resets.
 *
 * Deterministic and finite, so a demo is repeatable; the simulator loops back to a calm
 * NORMAL tail after the script so a long demo doesn't just stop.
 */
object DemoScript {

    val ARC: List<DemoWindow> = listOf(
        DemoWindow(WellnessStatus.NORMAL, confidence = 92, fhrBpm = 141, kicksThisWindow = 3),
        DemoWindow(WellnessStatus.NORMAL, confidence = 90, fhrBpm = 144, kicksThisWindow = 2),
        DemoWindow(WellnessStatus.NORMAL, confidence = 88, fhrBpm = 139, kicksThisWindow = 4),
        DemoWindow(
            WellnessStatus.NORMAL, confidence = 34, fhrBpm = 150, kicksThisWindow = 1,
            signalLow = true, motherActive = true, motionState = MotionState.WALKING,
        ),
        DemoWindow(WellnessStatus.PATHOLOGIC, confidence = 81, fhrBpm = 168, kicksThisWindow = 0),
        DemoWindow(WellnessStatus.PATHOLOGIC, confidence = 84, fhrBpm = 172, kicksThisWindow = 0),
        DemoWindow(
            WellnessStatus.PATHOLOGIC, confidence = 38, fhrBpm = 170, kicksThisWindow = 0,
            signalLow = true,
        ),
        DemoWindow(
            WellnessStatus.SUSPECT, confidence = 76, fhrBpm = 158, kicksThisWindow = 1,
            motionState = MotionState.SITTING,
        ),
        DemoWindow(WellnessStatus.NORMAL, confidence = 89, fhrBpm = 143, kicksThisWindow = 2),
    )

    /** Calm tail repeated after the arc so a long-running demo keeps producing data. */
    val TAIL = DemoWindow(WellnessStatus.NORMAL, confidence = 90, fhrBpm = 142, kicksThisWindow = 2)

    fun windowAt(index: Int): DemoWindow = ARC.getOrElse(index) { TAIL }
}
