# WombCare — Android App

Companion app for the WombCare fetal-wellness wearable (Silicon Labs EFR32MG26).
Team SilicoVegas · Silicon Labs × FPT IoT Challenge 2026.

> **WombCare is a home wellness-awareness / early-warning aid, not a diagnostic device.**
> It flags patterns associated with reduced fetal wellbeing so a mother seeks care sooner.
> No screen, report, or string in this app may claim a diagnosis.

Two apps in one binary, chosen by role at signup:
- **Patient** (the mother) — connects to the device over BLE, sees live fetal heart rate,
  kick count and wellness status, keeps history, and shares a unique code with her doctor.
- **Doctor** — pastes that code, and after the mother approves, sees her readings live plus
  session history and alerts.

## Documentation

| Doc | What's in it |
|---|---|
| [docs/PLAN.md](docs/PLAN.md) | Phase-wise build plan, Firestore model + security rules, design system, why a DB is needed |
| [docs/FEATURES.md](docs/FEATURES.md) | Every feature, tiered `T1`/`T2`/`T3`, traced to the data that feeds it, plus what's deliberately out of scope |
| [docs/BLE_CONTRACT.md](docs/BLE_CONTRACT.md) | The device↔app protocol. **Change this before changing any BLE code.** |

## Stack

Kotlin · Jetpack Compose (Material 3) · Hilt · Room · DataStore · Firebase (Auth,
**Realtime Database**, FCM, App Check) · `minSdk 26`, `targetSdk 35`, JDK 17.

> **Why Realtime Database, not Firestore:** Firestore now requires a billing account even
> to create the database. RTDB runs on the free Spark plan, and for this workload (one
> 8-byte reading per minute, seen live on another phone) it's the better fit anyway — see
> [docs/PLAN.md §2](docs/PLAN.md). Every feature and every security rule from the original
> design carries over unchanged.

## Build

```bash
./gradlew testDebugUnitTest assembleDebug     # unit tests + debug APK
./gradlew installDebug                        # to a connected device
```

`local.properties` (Android SDK path) is machine-local and not committed. Firebase's
`google-services.json` is likewise not committed — see Phase 2 in the plan.

## Status — Phases 0 and 1 complete

Clean build, no warnings, **14 parser unit tests green**, debug APK produced.
`MainActivity` currently launches the **component gallery** — every component in every
state, with live Patient/Doctor and light/dark toggles. Phase 3 replaces it with the
navigation graph and moves the gallery behind a debug-only entry point.

**Phase 0 — the BLE contract, frozen in code**
- `core/ble/WombCareGatt.kt` — contract constants, accepting both disputed UUID pairs.
- `core/ble/ClinicalUpdateParser.kt` — decodes payload v1 (8 B), forward-supports v2 (10 B),
  and encodes the firmware's two traps in the type system: `fhr == 0` becomes `null`
  (no lock, *not* zero bpm), and the all-zero frame the firmware pushes on subscribe is
  flagged `isPlaceholder` so it can never render as "FHR 0, Normal".
- `core/ble/ConnectionState.kt` — distinguishes `Connected` (link up, device still asleep
  awaiting BTN0) from `Monitoring` (windows arriving) from `SignalLost` (quiet past 150 s).

**Phase 1 — the design system**
- `core/ui/theme/` — tokens, rose (patient) + sky (doctor) palettes, light and dark,
  tabular figures so live numbers don't jitter, and no dynamic colour so "what colour is
  Suspect" is constant across every device.
- `core/ui/format/StatusVisuals.kt` — accent, container, icon and word travel together in
  one `StatusVisual`, so no call site can render clinical status as colour alone.
- `core/ui/components/` — `StatusHeroCard`, `StatusPill`, `StatTile`, `BigNumber`,
  `SectionCard`, `ConnectionChip`, `PrimaryButton`/`SecondaryButton`/`DangerButton`,
  `ConsentCheckbox`, `EmptyState`, `ShimmerBox`, `DisclaimerFootnote`/`DisclaimerCard`.
- `core/ui/gallery/ComponentGallery.kt` — the review surface.

Behaviours baked into components rather than left to screens: a null measurement renders
`--`; a low-confidence reading is dimmed but never hidden; TalkBack reads a tile as one
phrase ("Fetal heart rate, 142 BPM") and a missing value as "no reading"; the disclaimer is
a component, not a sentence each screen must remember.

**Phase 2 — the backend (in progress)**
- `database.rules.json` — the full security model. **23 rules unit tests green** against
  the Firebase emulator (`firebase/test/rules.test.js`), proving the app's core promise:
  a doctor reads a patient's data only after approval, share codes resolve but can't be
  enumerated, doctors can't write clinical data, revoke cuts access instantly, consents are
  write-once. Run: `firebase emulators:exec --only auth,database "npm test --prefix firebase"`.
- `core/util/ShareCode.kt` — Crockford-base32 code generator + input normaliser (folds
  I/L/O/U look-alikes), 7 unit tests.
- `core/data/` — Hilt Firebase module, `AuthRepository` (signup writes profile + consents
  atomically, rolls back the Auth user if the profile write is rejected), and
  `CareLinkRepository` (share-code claim transaction, and approve/revoke as single atomic
  multi-path writes so a doctor can never be "active" without read access or vice-versa).

**Still needs you:** `firebase login` in a terminal, then I deploy the rules and seed data.
Everything above was built and tested with **no login** — the emulator runs offline.

## Open dependencies on the firmware team

See [docs/PLAN.md §6](docs/PLAN.md). The two that block real-hardware testing:
1. **UUIDs disagree** across `uuid.txt`, the flashed `btconf`, and git `main`.
2. **Motion state and battery are not transmitted** — payload v2 requested.

Neither blocks app development: the Phase 4 simulator feeds synthetic frames through the
same parser as real BLE.
