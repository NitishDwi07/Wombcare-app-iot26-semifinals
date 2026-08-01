# WombCare — Android App

Companion app for the **WombCare** fetal-wellness wearable (Silicon Labs EFR32MG26).
Team **SilicoVegas** · Silicon Labs × FPT IoT Challenge 2026.

> **WombCare is a home wellness-awareness / early-warning aid, not a diagnostic device.**
> It flags patterns associated with reduced fetal wellbeing so a mother seeks care sooner.
> No screen, report, or string in this app claims a diagnosis.

Two apps in one binary, chosen by role at signup:

- **Patient** (the mother) — connects to the wearable over Bluetooth, sees live fetal heart
  rate, kick count and a wellness status (Normal / Suspect / Pathologic), keeps history, and
  shares a unique code with her doctor.
- **Doctor** — pastes that code and, once the mother approves, sees her readings live plus
  session history and alerts.

## 📲 Try it now (free)

**Download & install on any Android phone (8.0+):** **https://wombcare-icc26.web.app**

No wearable needed — **Demo mode is on by default**, so tapping *Start monitoring* plays a
full simulated session (live charts, a real alert) end to end. To see the doctor link:
install on a second phone, sign up as a doctor, and paste the mother's share code.

Screenshots of every screen: [docs/SCREENSHOTS.md](docs/SCREENSHOTS.md).

## Status — feature-complete

Both apps, authentication, live monitoring, doctor sharing, and alerts are built, tested,
and deployed. Clean builds, **0 warnings**, **41 unit tests + 23 security-rules tests green**.

| Area | State |
|---|---|
| Onboarding · consent gate · auth (email/password) · role routing | ✅ |
| Patient dashboard — live status, 6 tiles, FHR/NSP/kick charts, session summary | ✅ |
| BLE device layer + demo simulator (behind one interface) | ✅ (simulator verified; real BLE awaits firmware) |
| Firebase Realtime Database backend — rules, App Check | ✅ deployed |
| Doctor app — patient list, add-by-code, live view, alerts feed | ✅ |
| Share code · approve / revoke · alerts · delete-my-data | ✅ |
| BLE pairing security (bonding + encryption + passkey) | ✅ app-side; firmware change written, awaits flash |

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Hilt · Coroutines/Flow · DataStore ·
**Firebase** (Auth · Realtime Database · App Check) · `minSdk 26`, `targetSdk 35`, JDK 17.

> **Why Realtime Database, not Firestore:** Firestore now requires a billing account even to
> create the database; RTDB runs on the free Spark plan and fits this workload (one 8-byte
> reading per minute, seen live on another phone). Every feature and every security rule
> carries over unchanged.

## Architecture (short)

```
[sensors] → MG26 wearable (on-device DSP + TinyML) → BLE 8 bytes/min
                                                        │
                                       ┌────────────────┴───────────────┐
                                       ▼ real                            ▼ demo
                              BleDeviceSource                   SimulatedDeviceSource
                                       └──────── same parser ───────────┘
                                                        │  ClinicalReading
                                          SessionEngine + AlertEvaluator
                                                        │
                                          Compose UI (reactive) ──► Room-free offline via
                                                        │            Firebase persistence
                                    (only if Sharing ON) ▼
                                       Firebase RTDB ── rules + App Check ──► Doctor's phone
```

- **One device interface, two implementations.** Real BLE and the demo simulator both emit
  readings through the *same* production parser, so demo mode exercises real code — and the
  app can't tell which source it's using.
- **Reactive top-level routing.** `SessionViewModel` watches Firebase auth state; the nav
  host swaps the whole signed-out/signed-in subtree when it changes (and picks the rose or
  sky theme by role). Sign-out from anywhere, or a token expiring, just works.
- **Honest data by construction.** `fhr == 0` (no lock) becomes `null` and renders `--`, never
  `0`; the FHR line breaks across gaps instead of interpolating; clinical status is always
  colour **+ icon + word**, never colour alone.

## Security

Two boundaries, both closed:

- **Cloud (phone ↔ Firebase ↔ doctor):** security rules enforce "a doctor reads a patient's
  data only after an active, approved link"; share codes can be *resolved* but never
  *enumerated*; doctors can't write clinical data; revoke cuts access instantly; consents are
  write-once. App Check blocks non-app clients. Proven by 23 emulator rules tests.
- **Device ↔ phone (BLE):** the Clinical Update characteristic requires a **bonded,
  encrypted, passkey-authenticated** link, so a nearby scanner can't read fetal data. See
  [docs/BLE_CONTRACT.md §7](docs/BLE_CONTRACT.md). (App side implemented; the firmware change
  is written and awaits the firmware team's flash.)

## Build & run

```bash
./gradlew testDebugUnitTest assembleDebug     # unit tests + debug APK
./gradlew installDebug                        # install on a connected device/emulator
```

**Two machine-local files are required and are NOT in the repo** (gitignored — one is a
secret, one is per-machine):

1. **`app/google-services.json`** — download from the [Firebase console](https://console.firebase.google.com)
   for project `wombcare-icc26` (Project settings → Your apps → `com.silicovegas.wombcare`).
   Required for the app to reach the backend.
2. **`local.properties`** — your Android SDK path. Android Studio creates it automatically on
   first open; or write `sdk.dir=/path/to/Android/Sdk`.

For a **signed release build**, also copy `keystore.properties.template` → `keystore.properties`
and point it at a release keystore. The team's release keystore is **not** in the repo and
must be kept safe — every future update must be signed with the same key.

## Testing

```bash
./gradlew testDebugUnitTest                                   # 41 JVM unit tests
firebase emulators:exec --only auth,database \
    "npm test --prefix firebase"                              # 23 RTDB security-rules tests
```

The unit tests cover the BLE parser (incl. the all-zero subscribe frame, unsigned bytes,
v1/v2), the session engine (reboot split, invalid-FHR handling, the demo alert timing), the
alert evaluator (confirm-and-persist, signal gating), the share-code generator, and form
validation. The rules tests prove the access model above.

## Documentation

| Doc | What's in it |
|---|---|
| [docs/PLAN.md](docs/PLAN.md) | Phase-wise plan, Firestore/RTDB model + rules, design system |
| [docs/FEATURES.md](docs/FEATURES.md) | Every feature, tiered, traced to the data that feeds it |
| [docs/BLE_CONTRACT.md](docs/BLE_CONTRACT.md) | Device↔app protocol + BLE security. **Change this before any BLE code.** |
| [docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md) | 4-minute two-phone demo runsheet for judges |
| [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md) | Firebase project setup steps |
| [docs/SCREENSHOTS.md](docs/SCREENSHOTS.md) | Every screen, captured |

## Dependencies on the firmware team (parallel track)

The app runs today on the simulator. Real-hardware readings need the firmware team to:
confirm the final BLE UUIDs, ship **payload v2** (adds motion + battery), get the BLE stack
compiling with the **Security Manager** component, and enable the bonding/passkey change
already written in `wombcare_ble.c`. Detail in [docs/BLE_CONTRACT.md](docs/BLE_CONTRACT.md).
