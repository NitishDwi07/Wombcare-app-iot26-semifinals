package com.silicovegas.wombcare.core.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.annotation.RequiresPermission
import com.silicovegas.wombcare.core.ble.WombCareGatt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A device found while scanning — what the picker shows the user. */
data class DiscoveredDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    /** Advertises the WombCare service or is named WombCare — the ones we highlight. */
    val isWombCare: Boolean,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: "Unknown device"
}

/**
 * Scans for nearby BLE devices and streams a de-duplicated, sorted list — the data behind
 * the "choose your device" screen (like nRF Connect / the Si Labs app show).
 *
 * Deliberately does NOT filter by the WombCare service UUID. If the firmware's advertising
 * is mis-configured (the service isn't in the advertisement), a filtered scan would show
 * nothing; instead we show every *named* device and simply mark the ones that look like
 * WombCare, so the user can always find theirs by name. WombCare devices sort to the top,
 * then by signal strength.
 */
@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun scan(): Flow<List<DiscoveredDevice>> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || adapter?.isEnabled != true) {
            close()
            return@callbackFlow
        }

        val found = LinkedHashMap<String, DiscoveredDevice>()

        val callback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull()
                val advertisesWombCare = result.scanRecord?.serviceUuids
                    ?.any { it.uuid in WombCareGatt.ACCEPTED_SERVICES } == true
                val namedWombCare = name?.contains("womb", ignoreCase = true) == true
                val isWombCare = advertisesWombCare || namedWombCare

                // Skip the noise: only surface named devices (or anything WombCare-ish).
                if (name.isNullOrBlank() && !isWombCare) return

                found[device.address] = DiscoveredDevice(
                    address = device.address,
                    name = name,
                    rssi = result.rssi,
                    isWombCare = isWombCare,
                )
                trySend(
                    found.values.sortedWith(
                        compareByDescending<DiscoveredDevice> { it.isWombCare }.thenByDescending { it.rssi },
                    ),
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close()
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, callback) // null filters = show everything
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }

        awaitClose {
            runCatching { scanner.stopScan(callback) }
        }
    }
}
