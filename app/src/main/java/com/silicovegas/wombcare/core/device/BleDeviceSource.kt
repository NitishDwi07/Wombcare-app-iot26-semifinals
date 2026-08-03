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
    private var controlChar: BluetoothGattCharacteristic? = null

    /** CCCD subscriptions still to enable (Android runs GATT ops one at a time). */
    private val cccdQueue = ArrayDeque<BluetoothGattCharacteristic>()

    /** Latest battery % from the standard Battery Service, merged into each reading. */
    @Volatile private var lastBatteryPercent: Int? = null

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
            // Remember the control characteristic (may be absent on BTN0-only firmware).
            controlChar = findControl(g)

            val clinical = findClinicalUpdate(g) ?: run {
                _connectionState.value = ConnectionState.Failed("clinical update characteristic not found")
                return
            }
            // Subscribe to Clinical Update AND — if the device exposes it — the standard
            // Battery Level characteristic (0x2A19), which is how the firmware sends battery.
            // Android allows only ONE GATT op at a time, so CCCD writes are queued and driven
            // one-by-one from onDescriptorWrite.
            cccdQueue.clear()
            cccdQueue.addLast(clinical)
            findBatteryLevel(g)?.let { cccdQueue.addLast(it) }
            subscribeNext(g)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            // One CCCD write finished → move to the next queued subscription. When the queue
            // drains, notifications are all on, so this is the safe moment to ask the device
            // to START monitoring (replacing BTN0). No-op if there's no control characteristic.
            if (cccdQueue.isEmpty()) {
                writeControl(g, WombCareGatt.CMD_START_MONITORING)
            } else {
                subscribeNext(g)
            }
        }

        @Deprecated("Deprecated in API 33; kept for broad device support")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            route(c.uuid, c.value)
        }

        // API 33+ overload.
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            route(c.uuid, value)
        }
    }

    /** Enable notifications on the next queued characteristic (one CCCD write at a time). */
    @SuppressLint("MissingPermission")
    private fun subscribeNext(g: BluetoothGatt) {
        val c = cccdQueue.removeFirstOrNull() ?: return
        g.setCharacteristicNotification(c, true)
        val cccd = c.getDescriptor(CCCD_UUID)
        if (cccd != null) {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        } else {
            // No CCCD on this characteristic — skip straight to the next.
            if (cccdQueue.isEmpty()) writeControl(g, WombCareGatt.CMD_START_MONITORING)
            else subscribeNext(g)
        }
    }

    /** Route an incoming notification by which characteristic it came from. */
    private fun route(uuid: UUID, bytes: ByteArray?) {
        when {
            uuid in WombCareGatt.ACCEPTED_CLINICAL_UPDATE -> publish(bytes)
            uuid == WombCareGatt.BATTERY_LEVEL -> {
                // Standard Battery Level: one byte, 0..100. Remember it and merge into the
                // next clinical reading so the dashboard's battery tile reflects the device.
                bytes?.firstOrNull()?.let { lastBatteryPercent = it.toInt() and 0xFF }
            }
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

    private fun findControl(g: BluetoothGatt): BluetoothGattCharacteristic? {
        for (service in g.services) {
            if (service.uuid !in WombCareGatt.ACCEPTED_SERVICES) continue
            service.getCharacteristic(WombCareGatt.CONTROL)?.let { return it }
        }
        return null
    }

    /** Standard Battery Level characteristic (0x2A19), wherever it lives. May be absent. */
    private fun findBatteryLevel(g: BluetoothGatt): BluetoothGattCharacteristic? {
        for (service in g.services) {
            service.getCharacteristic(WombCareGatt.BATTERY_LEVEL)?.let { return it }
        }
        return null
    }

    /**
     * Write a start/stop command to the control characteristic — the app-side of replacing
     * BTN0. Safe no-op when the characteristic is absent (BTN0-only firmware), so this never
     * breaks a device that doesn't support it yet. Handles the API 33 write-signature split.
     */
    @SuppressLint("MissingPermission")
    private fun writeControl(g: BluetoothGatt, cmd: Byte) {
        val c = controlChar ?: return
        val payload = byteArrayOf(cmd)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                c.value = payload
                @Suppress("DEPRECATION")
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
        } catch (e: SecurityException) {
            // Permission is requested upstream; ignore here rather than crash.
        }
    }

    private fun publish(bytes: ByteArray?) {
        when (val r = ClinicalUpdateParser.parse(bytes, System.currentTimeMillis())) {
            is ClinicalUpdateParseResult.Success -> {
                // Merge in the battery from the standard Battery Service if the payload itself
                // didn't carry one (v1) — so the dashboard shows the device's battery either way.
                val reading = if (r.reading.batteryPercent == null && lastBatteryPercent != null) {
                    r.reading.copy(batteryPercent = lastBatteryPercent)
                } else {
                    r.reading
                }
                _readings.tryEmit(reading)
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
        // Best-effort: tell the device to STOP monitoring (mirrors BTN0 off) before we drop
        // the link. Firmware should also sleep sensors on disconnect as a fallback.
        gatt?.let { writeControl(it, WombCareGatt.CMD_STOP_MONITORING) }
        controlChar = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = ConnectionState.Idle
    }
}
