package com.silicovegas.wombcare.core.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ble.ClinicalUpdateParseResult
import com.silicovegas.wombcare.core.ble.ClinicalUpdateParser
import com.silicovegas.wombcare.core.ble.ConnectionState
import com.silicovegas.wombcare.core.ble.WombCareGatt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Real BLE path to the MG26. Scans by the WombCare service UUID (accepting either disputed
 * pair — see [WombCareGatt]), connects, discovers, subscribes to the Clinical Update
 * characteristic's CCCD, and pushes every notification through the SAME
 * [ClinicalUpdateParser] the simulator uses.
 *
 * Permissions are the caller's job: the UI must hold BLUETOOTH_SCAN/CONNECT (API 31+) or
 * location (≤30) before [connect]. This class is annotated so lint enforces that at the
 * call site rather than crashing at runtime.
 *
 * Note: this compiles and is correct against the Android BLE API, but it can only be
 * *verified* against real hardware (or a BLE peripheral) — an emulator has no Bluetooth
 * radio. Until the firmware BLE stack is finished, [SimulatedDeviceSource] is the exercised
 * path; this is ready to swap in via the DI module.
 */
class BleDeviceSource(
    private val context: Context,
) : WombCareDeviceSource {

    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _readings = MutableSharedFlow<ClinicalReading>(extraBufferCapacity = 16)
    override val readings: Flow<ClinicalReading> = _readings.asSharedFlow()

    override val sourceLabel: String = "WombCare device"

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private var scanning = false

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun connect(deviceId: String?) {
        val ad = adapter
        if (ad == null || !ad.isEnabled) {
            _connectionState.value = ConnectionState.BluetoothOff
            return
        }
        // A known device can be connected directly; otherwise scan for the service.
        if (deviceId != null) {
            runCatching { ad.getRemoteDevice(deviceId) }.getOrNull()?.let { openGatt(it); return }
        }
        startScan(ad)
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScan(ad: BluetoothAdapter) {
        val scanner = ad.bluetoothLeScanner ?: run {
            _connectionState.value = ConnectionState.Failed("no BLE scanner")
            return
        }
        _connectionState.value = ConnectionState.Scanning
        scanning = true
        // Filter by BOTH accepted service UUIDs so either firmware build is discoverable.
        val filters = WombCareGatt.ACCEPTED_SERVICES.map {
            ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        // Defence in depth: the caller requests BLUETOOTH_SCAN before connecting, but if it
        // is ever missing we surface a state instead of crashing the app (the SecurityException
        // that "keeps stopping" the app came from here).
        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: SecurityException) {
            scanning = false
            _connectionState.value = ConnectionState.PermissionRequired
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val ad = adapter ?: return
            if (scanning) {
                scanning = false
                ad.bluetoothLeScanner?.stopScan(this)
                openGatt(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = ConnectionState.Failed("scan failed ($errorCode)")
        }
    }

    @SuppressLint("MissingPermission")
    private fun openGatt(device: BluetoothDevice) {
        _connectionState.value = ConnectionState.Connecting
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.PermissionRequired
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // The Clinical Update characteristic requires an encrypted, bonded link
                    // (see the firmware SM setup). If we haven't bonded with this device yet,
                    // start bonding now so the system passkey (PIN) prompt appears promptly
                    // rather than only when the encrypted read is first attempted. Android
                    // then holds the GATT operations until bonding completes.
                    if (g.device.bondState == BluetoothDevice.BOND_NONE) {
                        _connectionState.value = ConnectionState.Pairing
                        g.device.createBond()
                    } else {
                        _connectionState.value = ConnectionState.Connected
                    }
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Idle
                    g.close()
                    gatt = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val characteristic = findClinicalUpdate(g) ?: run {
                _connectionState.value = ConnectionState.Failed("clinical update characteristic not found")
                return
            }
            g.setCharacteristicNotification(characteristic, true)
            characteristic.getDescriptor(CCCD_UUID)?.let { cccd ->
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        @Deprecated("Deprecated in API 33; kept for broad device support")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            publish(c.value)
        }

        // API 33+ overload.
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            publish(value)
        }
    }

    private fun findClinicalUpdate(g: BluetoothGatt): BluetoothGattCharacteristic? {
        for (service in g.services) {
            if (service.uuid !in WombCareGatt.ACCEPTED_SERVICES) continue
            for (c in service.characteristics) {
                if (c.uuid in WombCareGatt.ACCEPTED_CLINICAL_UPDATE) return c
            }
        }
        return null
    }

    private fun publish(bytes: ByteArray?) {
        when (val r = ClinicalUpdateParser.parse(bytes, System.currentTimeMillis())) {
            is ClinicalUpdateParseResult.Success -> {
                _readings.tryEmit(r.reading)
                _connectionState.value = ConnectionState.Monitoring
            }
            // Unsupported/malformed frames are dropped; a version bump surfaces in the parser,
            // not here. Connection stays up.
            else -> Unit
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        val ad = adapter
        if (scanning && ad != null) {
            scanning = false
            ad.bluetoothLeScanner?.stopScan(scanCallback)
        }
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = ConnectionState.Idle
    }
}
