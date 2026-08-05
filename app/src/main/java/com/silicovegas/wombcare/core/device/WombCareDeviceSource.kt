package com.silicovegas.wombcare.core.device

import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ble.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The seam between "where readings come from" and everything above it.
 *
 * Both the real Bluetooth path ([BleDeviceSource]) and the demo path
 * ([SimulatedDeviceSource]) implement this, and — crucially — both emit
 * [ClinicalReading]s produced by the SAME `ClinicalUpdateParser`. Demo mode therefore
 * exercises the production decode path, not a parallel fake one, and swapping real hardware
 * in is a one-line change in the DI module. The UI never learns which it's talking to.
 */
interface WombCareDeviceSource {
    val connectionState: StateFlow<ConnectionState>
    val readings: Flow<ClinicalReading>

    /** Begin scanning/connecting (or, for the simulator, start emitting). */
    fun connect(deviceId: String? = null)

    fun disconnect()

    /**
     * Disconnect AND remove the Bluetooth bond, so the NEXT connection re-runs pairing and
     * prompts for the PIN again. This is the "Forget device" action — without it, once paired
     * the phone silently reuses the bond forever and you can neither re-enter the PIN nor
     * cleanly switch to a different unit.
     *
     * Returns `true` if the bond was removed (or there was nothing bonded to remove), `false`
     * if the OS blocked the removal — Android 13+ silently rejects the private `removeBond`
     * reflection, so the caller must then send the user to system Bluetooth settings to unpair
     * "WombCare" by hand. The demo source has no bond and always returns `true`.
     */
    fun forget(): Boolean

    /** Human label for settings/debug ("WombCare device" vs "Demo mode"). */
    val sourceLabel: String
}
