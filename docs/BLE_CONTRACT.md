# WombCare BLE Contract (app ↔ MG26)

> **Single source of truth for the app's BLE layer.** If firmware changes, this file
> changes first, then `core/ble/WombCareGatt.kt`. Nothing else in the app hardcodes
> a UUID or a byte offset.

---

## 1. ⚠️ Two unresolved conflicts (must be closed before Phase 4 ships)

### Conflict A — the UUIDs disagree in three places

| Source | Service UUID | Clinical Update UUID |
|---|---|---|
| `uuid.txt` (Kunal, 31-07-2026 — **assumed newest**) | `6ea24b23-e506-45ac-8a65-fb4061843c70` | `e1c1eb40-11fe-413a-892c-739648b001c0` |
| `Wombcare_6PreFinal/config/btconf/gatt_configuration.btconf` (actual flashed firmware) | `cd3c5a03-11f5-48ae-95bc-07a592bae6d0` | `d1dd1e97-9fee-479c-9e05-64f67170f1f8` |
| `icc-26-WombCare-Silicovegas/config/btconf/...` (git `main`) | **absent — no WombCare service at all** | absent |

**App decision:** use the `uuid.txt` pair as `PRIMARY`, keep the btconf pair as
`LEGACY_FALLBACK`, and have the scanner accept **either** service UUID. Costs ~10
lines and removes a whole class of demo-day failure. Ask Nikolaos/Kunal to make
`btconf` match `uuid.txt` and commit it to `main`.

### Conflict B — the field list you sent is not the payload the firmware sends

Your message describes **6 separate one-byte characteristics** (FHR, Kick Count,
Anomaly Score *as INDICATE*, Confidence, Motion State, Battery Level).

The firmware sends **one 8-byte NOTIFY characteristic** (`WombCareBlePayload_t`,
`wombcare_ble.h:51-60`; `btconf` declares `length="8"`, `<notify>` only). It contains
**no motion state and no battery byte**. Battery Service `0x180F` is listed as
*optional* in the header comment and is **not** in `btconf`.

So, of the six values you listed:

| Value you described | Where the app actually gets it today |
|---|---|
| Fetal Heart Rate (BPM) | ✅ byte 3 |
| Kick Count | ✅ byte 4 |
| Anomaly Score (NSP) | ✅ byte 1 — via **NOTIFY**, not INDICATE (no ack to send) |
| Confidence (0-100) | ✅ byte 2 |
| Motion State (REST/SIT/WALK) | ⚠️ **not sent.** Only a 1-bit approximation: `flags` bit 2 `ACTIVE` = "not resting" |
| Battery Level (0-100) | ❌ **not sent at all** |

**App decision:** parse v1 exactly as the firmware sends it. Motion tile shows
`Resting` / `Active` (from the flag) and Battery shows `—` until firmware catches up.
Then request **payload v2** below; the `version` byte makes it a clean, non-breaking
upgrade and the app supports both simultaneously.

---

## 2. Connection profile

| Property | Value |
|---|---|
| Advertised device name | `WombCare` (`btconf` Device Name, `length=8`) |
| Service advertised in adv data? | Yes (`advertise="true"`) → filter the scan by service UUID |
| Update cadence | **1 notification per 60-second window** (`app.c:206` gates on `minute_window_ready`) |
| Bonding | **Required.** The device demands a bonded, encrypted, MITM-passkey link (see §7). First connection prompts for the device PIN; later connections auto-reconnect. |
| Late-subscriber behaviour | On CCCD enable, firmware immediately notifies the **last known payload** (`wombcare_ble.c:186-192`). If nothing has been measured yet this is an all-zero frame with `version=1`, `fhr=0` → app must render "waiting for first reading", not "FHR 0". |
| Device starts | **Asleep.** Mother must press **BTN0** on the board to begin monitoring. BLE advertises regardless. |
| While connected | Firmware stops advertising; restarts on disconnect. |

## 3. Payload v1 — 8 bytes, little-endian, packed

```
byte  field         type    range / meaning
────  ────────────  ──────  ─────────────────────────────────────────────────────
 0    version       u8      == 1. If unknown → ignore frame, log, show "update app"
 1    nsp           u8      0 = Normal, 1 = Suspect, 2 = Pathologic
 2    confidence    u8      0..100 (%)  — ML confidence × IMU motion trust
 3    fhr_bpm       u8      baseline fetal heart rate, bpm. **0 = invalid/no lock**
 4    kick_count    u8      fetal movements detected in this 60 s window
 5    flags         u8      bit0 ALERT (nsp==Pathologic, persisted)
                            bit1 SIGNAL_LOW (low IMU/signal trust)
                            bit2 ACTIVE (mother active, i.e. NOT resting)
 6-7  timestamp     u16 LE  device minute counter since boot (NOT wall clock)
```

### Parsing rules the app must honour

1. **Little-endian** for `timestamp` — `ByteBuffer.wrap(v).order(LITTLE_ENDIAN)`.
   Everything else is a single byte; read as `b.toInt() and 0xFF` (Kotlin `Byte` is signed).
2. **`fhr_bpm == 0` means invalid, not zero heart rate.** Render `--`, never plot it,
   and never let it drag a chart or an average down.
3. **`timestamp` is a minutes-since-boot counter, not a clock.** The app stamps every
   reading with its own `recorded_at` (phone wall clock) for display and DB, and uses
   `timestamp` only for: ordering within a session, gap detection (missed minutes), and
   **reboot detection** (timestamp went backwards → the device restarted → start a new
   session). It wraps at 65535 min ≈ 45.5 days.
4. **`nsp` is what triggers the alert.** `2` (Pathologic) → local notification +
   `alerts` row. `1` (Suspect) → in-app amber banner, no push. Confirm-and-persist logic
   lives in the app, not the firmware.
5. **`SIGNAL_LOW` set → dim the confidence figure and add "signal quality low"**, because
   maternal movement makes the classification unreliable. Do not hide the value.
6. **`kick_count` is per-window.** The session total is the app's running sum — the
   firmware does not accumulate across windows.
7. Frame length may be **> 8** in future (v2). Read by offset, never assert
   `size == 8`; assert `size >= expectedSizeFor(version)`.

## 4. Payload v2 — requested from firmware (10 bytes)

Ask Nikolaos for exactly this, so the app gains motion + battery with no protocol churn:

```
 0-7   (identical to v1, but version = 2)
 8     motion_state  u8   0 = RESTING, 1 = SITTING, 2 = WALKING
 9     battery_pct   u8   0..100
```

Firmware change is small: bump `WOMBCARE_BLE_PAYLOAD_VERSION` to `2`, add the two
fields to `WombCareBlePayload_t`, and change `btconf` `<value length="10">`.
`wombcare_imu.c` already computes the motion/trust score, so the data exists.

**Alternative accepted by the app:** standard **Battery Service `0x180F` /
Battery Level `0x2A19`** (Read + Notify) instead of byte 9. The app will read it if
present and ignore it if absent. Motion still needs byte 8.

## 5. Android permissions & lifecycle notes

| Concern | Handling |
|---|---|
| Android 12+ (API 31+) | `BLUETOOTH_SCAN` (with `neverForLocation`), `BLUETOOTH_CONNECT` — runtime prompts |
| Android 8–11 | `ACCESS_FINE_LOCATION` is mandatory for BLE scanning + location services ON |
| Screen off / app backgrounded | A 60-min session must survive → **foreground service** with a persistent "Monitoring" notification holding the GATT connection |
| Doze / battery optimisation | Foreground service + optional "ignore battery optimisation" prompt before a long session |
| One notification per minute | Long-lived idle connection. Request a relaxed connection interval; do not treat 60 s of silence as a dropout. **Dropout threshold = 150 s** (2.5 missed windows) |
| Reconnect | Auto-reconnect with backoff to the last bonded/known MAC; surface state as `Scanning / Connecting / Connected / Monitoring / Signal lost` |

## 6. Reference constants (Kotlin, Phase 0 deliverable)

```kotlin
object WombCareGatt {
    // uuid.txt (31-07-2026) — primary
    val SERVICE          = uuid("6ea24b23-e506-45ac-8a65-fb4061843c70")
    val CLINICAL_UPDATE  = uuid("e1c1eb40-11fe-413a-892c-739648b001c0")

    // gatt_configuration.btconf as currently flashed — accepted as fallback
    val SERVICE_LEGACY         = uuid("cd3c5a03-11f5-48ae-95bc-07a592bae6d0")
    val CLINICAL_UPDATE_LEGACY = uuid("d1dd1e97-9fee-479c-9e05-64f67170f1f8")

    val BATTERY_SERVICE = uuid("0000180F-0000-1000-8000-00805F9B34FB") // optional
    val BATTERY_LEVEL   = uuid("00002A19-0000-1000-8000-00805F9B34FB") // optional

    const val DEVICE_NAME       = "WombCare"
    const val WINDOW_SECONDS    = 60
    const val DROPOUT_MILLIS    = 150_000L
    const val FHR_NORMAL_LOW    = 110
    const val FHR_NORMAL_HIGH   = 160
}
```

---

## 7. BLE SECURITY — bonding + encryption + passkey (implemented)

**The threat:** UUIDs are public (in every copy of the app) and discoverable by any BLE
scanner, so they are NOT a secret and cannot be the security boundary. Without link-layer
protection, any nearby BLE client could subscribe to Clinical Update and read the fetal
data. The boundary must be an **encrypted, bonded, passkey-authenticated** connection.

### What is implemented (firmware `Wombcare_6PreFinal`)

| Piece | File | Setting |
|---|---|---|
| Require MITM + bonding + Secure Connections | `wombcare_ble.c` (system_boot) | `sl_bt_sm_configure(0x0F, sl_bt_sm_io_capability_displayonly)` |
| Fixed device passkey (Passkey-Entry pairing) | `wombcare_ble.c` | `sl_bt_sm_set_passkey(WOMBCARE_BLE_PASSKEY)` — printed on the device |
| Bondable | `wombcare_ble.c` | `sl_bt_sm_set_bondable_mode(1)` |
| Characteristic requires encryption | `gatt_configuration.btconf` | `<notify authenticated="true" bonded="true" encrypted="true"/>` |

The characteristic setting is the actual lock: the stack refuses to enable notifications on
any connection that has not bonded with the passkey.

### What the firmware team MUST also do

1. **Add the Security Manager feature** to the project `.slcp` (the `bluetooth_feature_sm`
   component). Without it the `sl_bt_sm_*` calls won't link — this is part of adding the BLE
   stack (handoff §8), not extra work.
2. **Print the passkey on each device** (label / packaging). For production, flash a
   **UNIQUE passkey per unit** (derive from serial) and print each unit's own code — a
   single shared secret in firmware is weaker, since a leak applies to every unit.

### App side (implemented)

- `BleDeviceSource` starts bonding on connect if the device isn't bonded yet, so the system
  passkey prompt appears promptly; Android holds GATT ops until bonding completes.
- New `ConnectionState.Pairing` ("Pairing — enter device PIN") is surfaced in the UI.

### User experience

- **First time (per phone):** connect → Android PIN dialog → mother types the passkey
  printed on the device → bonded + encrypted. ~10 seconds, once.
- **Every time after:** auto-reconnect, encrypted, no PIN.
- **A nearby attacker:** can see the device advertising but the encrypted characteristic
  refuses to deliver data without the bond → gets nothing.
- **Demo mode is unaffected** — the simulator uses no BLE, so demos need no pairing.

---

## 8. Control characteristic — app-driven start/stop (replaces BTN0)

So the mother can start monitoring from the **app** instead of pressing BTN0 on the device.
The app side is implemented; the firmware side must be added in **`Wombcare_6PreFinal`**.

| Property | Value |
|---|---|
| Characteristic | `Control` — **Write** |
| UUID | `e1c1eb41-11fe-413a-892c-739648b001c1` (in the WombCare service; app: `WombCareGatt.CONTROL`) |
| Command: start | `0x01` (`CMD_START_MONITORING`) — wake sensors, begin a session |
| Command: stop | `0x00` (`CMD_STOP_MONITORING`) — sleep sensors |

**App behaviour (done):** on connect, after enabling notifications, `BleDeviceSource` writes
`0x01`; on Stop/disconnect it writes `0x00` (best-effort). If the characteristic is absent
(BTN0-only firmware) the app skips the write — nothing breaks.

**Firmware TODO (`Wombcare_6PreFinal`):**
1. Add a Write characteristic with the UUID above to the WombCare service in
   `config/btconf/gatt_configuration.btconf` (id `control` → `gattdb_control`).
2. In `wombcare_ble.c`, handle `sl_bt_evt_gatt_server_attribute_value` for it: on `0x01` set
   `s_monitoring_requested = true` (the same flag BTN0 flips in `app.c`); on `0x00` set it false.
3. Keep BTN0 working too — the two just drive the same request flag.
4. Mark it `encrypted="true"` like Clinical Update (§7), so only a bonded phone can control it.

### Related: "send the previous reading immediately" (point 2)

The firmware already notifies the last-known payload on CCCD subscribe (`wombcare_ble.c`), so
a reconnecting phone sees data instantly — only the very first 60 s after power-on has no
value yet (the device needs a full minute to compute the first result). If you also want the
device to *refresh the last value every second* between the 1-minute computations (so the
screen always looks live), add a 1 s sleeptimer in `Wombcare_6PreFinal` that re-sends
`s_last_payload`. The app already renders whatever cadence the device sends — no app change
needed.
