package com.silicovegas.wombcare.core.ble

import java.util.UUID

/**
 * The frozen BLE contract. Nothing else in the app hardcodes a UUID or a byte offset.
 *
 * Ground truth: `docs/BLE_CONTRACT.md`. Firmware side: `wombcare_ble.h`,
 * `config/btconf/gatt_configuration.btconf`.
 *
 * Two UUID pairs are accepted on purpose — `uuid.txt` (31-07-2026) and the pair currently
 * present in the flashed `btconf` disagree, and git `main` has no WombCare service at all.
 * Accepting both costs nothing and removes a demo-day failure mode. Once firmware commits
 * a single set, delete the legacy pair.
 */
object WombCareGatt {

    /** From `uuid.txt` (31-07-2026) — treated as the intended final pair. */
    val SERVICE: UUID = UUID.fromString("6ea24b23-e506-45ac-8a65-fb4061843c70")
    val CLINICAL_UPDATE: UUID = UUID.fromString("e1c1eb40-11fe-413a-892c-739648b001c0")

    /** From `gatt_configuration.btconf` as currently flashed. Accepted as a fallback. */
    val SERVICE_LEGACY: UUID = UUID.fromString("cd3c5a03-11f5-48ae-95bc-07a592bae6d0")
    val CLINICAL_UPDATE_LEGACY: UUID = UUID.fromString("d1dd1e97-9fee-479c-9e05-64f67170f1f8")

    /** Optional standard Battery Service. Read if the device exposes it, ignore if not. */
    val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    /**
     * Control characteristic (Write) — lets the app start/stop monitoring in place of the
     * physical BTN0 on the device. The app writes [CMD_START_MONITORING] on Start and
     * [CMD_STOP_MONITORING] on Stop; the firmware wakes/sleeps the sensors accordingly.
     *
     * >>> FIRMWARE TODO: add this Write characteristic to the WombCare service and handle
     *     the two commands (wake sensors on 0x01, sleep on 0x00). Until it exists the app
     *     simply skips the write — a device with only BTN0 keeps working unchanged.
     */
    val CONTROL: UUID = UUID.fromString("e1c1eb41-11fe-413a-892c-739648b001c1")

    const val CMD_STOP_MONITORING: Byte = 0x00
    const val CMD_START_MONITORING: Byte = 0x01

    val ACCEPTED_SERVICES: List<UUID> = listOf(SERVICE, SERVICE_LEGACY)
    val ACCEPTED_CLINICAL_UPDATE: List<UUID> = listOf(CLINICAL_UPDATE, CLINICAL_UPDATE_LEGACY)

    /** Advertised device name (`btconf` Device Name characteristic). */
    const val DEVICE_NAME = "WombCare"

    /** Firmware pushes exactly one notification per completed 60-second window. */
    const val WINDOW_SECONDS = 60

    /**
     * Silence longer than this means the link is dead, not merely idle — 2.5 missed
     * windows. Anything shorter would report a dropout during normal operation.
     */
    const val DROPOUT_MILLIS = 150_000L

    /** Clinical context band drawn behind the FHR chart. Not a threshold we judge on. */
    const val FHR_NORMAL_LOW = 110
    const val FHR_NORMAL_HIGH = 160

    /** Pathologic must repeat this many consecutive windows before we alert. */
    const val ALERT_CONSECUTIVE_WINDOWS = 2

    /** Below this confidence a Pathologic window becomes "re-check", not an alarm. */
    const val ALERT_MIN_CONFIDENCE = 40
}
