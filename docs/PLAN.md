# WombCare App — Phase-wise Build Plan

**Stack:** Native Android · Kotlin · Jetpack Compose (Material 3)
**Backend:** **Firebase** — Auth · Firestore · FCM · App Check
**Roles:** Patient (pregnant mother) · Doctor
**Location:** `c:\Users\KIIT0001\Codes\iot-26\wombcare-app\` — separate folder, **outside**
the firmware repo `icc-26-WombCare-Silicovegas`.

> **Framing rule inherited from `WOMBCARE_HANDOFF.md` (non-negotiable):** WombCare is a
> **home wellness-awareness / early-warning aid, NOT a diagnostic device.** Every screen,
> every report, and the T&C must say so. The app never uses the words "diagnosis",
> "normal baby", or "safe".

Companion docs: [FEATURES.md](FEATURES.md) · [BLE_CONTRACT.md](BLE_CONTRACT.md)

---

## 0. Read this first — the two firmware conflicts

Full detail in [BLE_CONTRACT.md](BLE_CONTRACT.md). Summary:

1. **UUIDs disagree** between `uuid.txt`, the flashed `btconf`, and git `main` (which has
   no WombCare service at all). → App accepts **both** UUID pairs; firmware team should
   make `btconf` match `uuid.txt`.
2. **The 6-field spec you sent ≠ what the firmware sends.** Firmware sends **one 8-byte
   NOTIFY** frame: `version, nsp, confidence, fhr_bpm, kick_count, flags, timestamp`.
   There is **no motion-state byte and no battery byte**, and the anomaly score arrives as
   NOTIFY (no ack) not INDICATE. → Motion degrades to `Resting/Active` from `flags` bit 2,
   Battery shows `—`, and we request **payload v2** (adds the 2 bytes) in parallel.
3. **Firmware BLE does not compile/run yet** (handoff §8, items 2–5b). This is exactly why
   Phase 4 builds the simulator behind the *same interface* as real BLE — the app is fully
   demoable with zero hardware, and switching to the real board is a one-line source swap.

---

## 1. Do we need a database? — Yes, but only for one thing

Three data paths. Only path 3 needs the cloud.

```
PATH 1 — Patient live view                       (no cloud, works in airplane mode)
  MG26 ──BLE notify(8B)/60s──▶ Phone parses ──▶ Compose state ──▶ dashboard + charts

PATH 2 — Patient history                         (no cloud)
  parsed reading ──▶ Room (SQLite on device) ──▶ history list, trends, session reports

PATH 3 — Doctor view                             ◀── THIS is why a DB exists
  Room ──sync (only if mother enabled sharing)──▶ Cloud Firestore
                                                       │ snapshot listeners + rules
                                                       ▼
                                              Doctor's phone
```

**Why the cloud is unavoidable:** the doctor is on a *different phone*, often in a
different city. Two phones cannot see the same data without a server in between. The
device itself has no internet — the mother's phone is always the uplink.

**How this stays consistent with "privacy-by-design, no cloud":** the claim is about
*inference*, and it still holds — all ML runs on the MG26, no raw ECG/PVDF waveform ever
leaves the device or the phone. What syncs is **only** the 8 bytes/minute of derived
summary, **only** for sessions the mother has consented to share, **only** to doctors she
explicitly approved, and she can revoke or delete at any time.

**Explicitly NOT in Firestore:** raw ECG/PVDF samples, IMU samples, the TFLite model,
anything the mother didn't consent to share. Sharing defaults to **OFF** — with it off,
the app never writes to Firestore at all.

**Room stays even though Firestore has an offline cache.** Because sharing defaults off,
un-shared sessions must never reach Firestore — so the local store remains the write-path
of record and Firestore is a *downstream, opt-in* copy.

---

## 2. Firestore data model

Firestore rules cannot join, so the access-control relationship is **denormalised** onto
the patient document as `authorizedDoctors`. That is the one real architectural difference
from a SQL backend — with Postgres the join *is* the policy; here we maintain the list.

```
/profiles/{uid}                          { role: 'patient'|'doctor', fullName, phone, createdAt }

/patients/{patientUid}                   { shareCode, shareCodeRotatedAt, dueDate, pregnancyWeeks,
                                           sharingEnabled: false, emergencyContact,
                                           authorizedDoctors: [uid, …],      ← the access list
                                           liveStatus: { lastReadingAt, nsp, fhrBpm,
                                                         kickTotal, confidence, sessionId } }

/patients/{patientUid}/sessions/{sid}    { startedAt, endedAt, deviceId, readingCount,
                                           avgFhr, totalKicks, worstNsp,
                                           rollup: [ …readings… ]  ← written on session end }

/patients/{patientUid}/sessions/{sid}/readings/{minute}
                                         { recordedAt, deviceMinute, fhrBpm|null, kickCount,
                                           nsp, confidence, flags,
                                           motionState|null, batteryPct|null }

/patients/{patientUid}/alerts/{alertId}  { sessionId, nsp, createdAt,
                                           acknowledgedBy|null, acknowledgedAt|null }

/doctors/{doctorUid}                     { registrationNo, clinic, specialty, verified }

/shareCodes/{WMB-XXXXXXXX}               { patientUid }        ← get ALLOWED, list DENIED

/careLinks/{doctorUid}_{patientUid}      { doctorUid, patientUid, doctorName, clinic,
                                           status: 'pending'|'active'|'revoked',
                                           requestedAt, respondedAt }

/consents/{autoId}                       { uid, docType, docVersion, acceptedAt }
```

### Three model decisions worth knowing

**`liveStatus` on the patient doc.** The doctor's patient list needs a live dot per patient.
Subscribing to every patient's `readings` subcollection would be expensive and noisy;
instead the mother's phone stamps one small map on her own patient doc each minute. The
doctor's list listens to N patient docs — one document read per update.

**Readings are a subcollection while streaming, plus a `rollup` on the session doc.**
Realtime needs one doc per minute. But a doctor reopening a 60-minute session would then
cost 60 reads, so on session end we write a compact `rollup` array and history views read
**one document**. Both paths, each optimal for its job.

**`doctorName` is copied onto the careLink.** The approval prompt must say *"Dr. Sharma
requested access"* — but the mother has no read access to a doctor she hasn't approved yet.
Denormalising the name at request time solves it without widening any rule.

### Security rules — the whole model in seven rules

```js
function signedIn()      { return request.auth != null }
function role()          { return get(/databases/$(db)/documents/profiles/$(request.auth.uid)).data.role }
function isDoctor()      { return signedIn() && role() == 'doctor' }
function owns(pid)       { return signedIn() && request.auth.uid == pid }
function approvedFor(pid){ return get(/databases/$(db)/documents/patients/$(pid))
                                    .data.authorizedDoctors.hasAny([request.auth.uid]) }
```

1. **`/profiles/{uid}`** — read/write self only. A doctor may `get` a linked patient's
   profile via `approvedFor(uid)`. No `list`, ever.
2. **`/patients/{pid}` and everything under it** — full access when `owns(pid)`;
   **read-only** when `approvedFor(pid)`. `allow list: if false` on the collection itself,
   so no one can browse patients under any circumstances.
3. **`/shareCodes/{code}`** — `allow get: if isDoctor()`, **`allow list: if false`**. This
   is the crux: Firestore distinguishes fetching a *known* document ID from querying a
   collection, so a doctor who has the code can resolve it while nobody can enumerate
   codes. The document holds `patientUid` and nothing else — no PII. Patient may create
   (claiming) and delete (rotating) their own code.
4. **`/careLinks/{id}`** — a doctor may `create` only with `doctorUid == auth.uid`,
   `status == 'pending'`, and the doc ID matching `{auth.uid}_{patientUid}` (which makes
   duplicate requests impossible). The **patient** may `update`, and only the
   `['status','respondedAt']` keys via `diff().affectedKeys().hasOnly(...)`.
5. **Alert acknowledgement** — an approved doctor may update only
   `['acknowledgedBy','acknowledgedAt']`. Nothing else clinical is ever doctor-writable.
6. **`/consents/{id}`** — create + read own; `update` and `delete` are `false` forever, so
   the consent trail is tamper-proof.
7. **App Check** enforced on Firestore + Auth, so only genuine builds of this app can talk
   to the backend. This replaces the server-side brute-force logging a SQL RPC would have
   given us on share-code lookups.

**Approve is one atomic batch, no Cloud Function needed:**
`careLinks/{id}.status = 'active'` **+** `patients/{uid}.authorizedDoctors arrayUnion(doctorUid)`
— both writes are things the mother is allowed to do to her own data. Revoke is the exact
mirror (`arrayRemove` + `status = 'revoked'`).

**Share-code generation, also client-side:** generate a candidate `WMB-XXXXXXXX` (Crockford
base32 — no `I`/`L`/`O`/`U`, ~1.1 × 10¹² combinations), then a transaction that creates
`/shareCodes/{CODE}` only if absent and writes it to the patient doc. Retry on collision.

### ⚠️ One decision deferred to Phase 7: doctor push needs the Blaze plan

Sending an FCM push requires the Admin SDK, i.e. a server — a **Cloud Function**, which
needs the **Blaze plan (a card on file)**. Free-tier quotas still cover our volume many
times over, so the practical cost is ~₹0, but a card is required. *(Supabase has the
identical constraint via Edge Functions — this is not a Firebase-specific limitation.)*

Fallback if you'd rather not add a card: the doctor gets **in-app realtime alerts** via a
Firestore listener while the app is open, and the mother's alert screen offers a one-tap
call. Everything else in Phase 7 works on the free Spark plan. Decide before Phase 7, not now.

*Cost note:* rules calling `get()` (for `role()` and `approvedFor()`) bill one document read
each. At our volume this is negligible; if it ever matters, `role` moves into a custom auth
claim — which also needs Blaze.

---

## 3. Design system — "clean, pink/blue, honest numbers"

Two role themes off one token set: **patient = soft rose**, **doctor = clinical sky-blue**.
Same components, same spacing, instantly distinguishable — one shared component library.

```
rose      primary #D96A85   container #FFE4EA   surface-tint #FFF7F9
sky       primary #4A90D9   container #DFEDFB   surface-tint #F5FAFF
ink       #1F2430   muted #6B7280   hairline #ECEEF2   bg #FDFBFC
status    normal #2E9E7B   suspect #E0A03A   pathologic #D64545
```

Rules that keep it clean:
- **Status is never colour-only** — always colour + icon + word (`● Normal`). Required for
  colour-blind users and a real clinical-safety point in a pitch.
- **Numbers are the hero.** Large tabular-figure display type (`tnum` so digits don't jitter
  as they update), unit in small muted caps beside it, no gauges, no glossy rings.
- **Invalid ≠ zero.** `fhr_bpm == 0` renders `--`, never `0`.
- One accent per screen. Cards: 20 dp radius, 1 dp hairline border, no drop shadows.
- 8 dp spacing grid; 4 dp only inside a tile. Light + dark from the same tokens.

### Charts (Phase 5 — load the `dataviz` skill before writing the first chart)

| Chart | Form | Notes |
|---|---|---|
| FHR trend | Line, 1 pt/min, y 60–200 | Shaded **110–160 normal band**. **Break the line on gaps** > 2 min — never interpolate across missing minutes. Skip invalid (0) points |
| NSP timeline | Thin segmented strip under the FHR chart | One cell per minute, green/amber/red + pattern. The "read it in 2 seconds" clinical row |
| Kicks | Bars per minute + big cumulative total | Total is the app's running sum; firmware sends per-window only |
| Confidence | Slim horizontal meter | Dimmed + "signal quality low" when `SIGNAL_LOW` is set |
| Session summary | 4 stat tiles | Avg FHR · total kicks · time in each NSP class · worst class |

Library: **Vico** for line/bar (Compose-native); hand-rolled **Canvas** for the NSP strip
and confidence meter — simple enough that full control beats fighting a chart lib.

---

## 4. Screen inventory (~22 screens)

**Shared** — `00` Splash/route-by-role · `01` Onboarding (what WombCare is, what it is
*not*, how it works) · `02` Role select · `03` Patient signup · `04` Doctor signup ·
`05` Login · `06` Forgot password · `07` Terms · `08` Privacy · `09` **Consent gate**
(3 checkboxes, each writing a `consents` doc) · `10` Profile & settings.

**Patient** — `P1` Dashboard · `P2` Device scan & connect · `P3` Live session ·
`P4` Session report · `P5` History + trends · `P6` **Share & care team** ·
`P7` Alerts · `P8` Settings.

**Doctor** — `D1` Patient list · `D2` Add patient (paste code) · `D3` Patient detail (live) ·
`D4` Session history · `D5` Alert feed · `D6` Settings.

Full behaviour per screen is in [FEATURES.md](FEATURES.md).

---

## 5. Phases

Each phase ends in something runnable. Nothing is "integrated at the end".

### Phase 0 — Contract & scaffold
Freeze [BLE_CONTRACT.md](BLE_CONTRACT.md). Android project (`minSdk 26`, `targetSdk 35`),
Gradle version catalog, Hilt, Compose Navigation, Room, DataStore, **Firebase BoM**
(auth + firestore + messaging + appcheck), Vico. Packages
`core/{ble,data,db,ui,util}` + `feature/{auth,patient,doctor}`. `WombCareGatt.kt` constants.
Build + blank themed app runs. **Raise now:** UUID confirmation, payload v2 request.

### Phase 1 — Design system
Tokens, both role themes, light+dark. Reusable `StatusPill`, `StatTile`, `BigNumber`,
`SectionCard`, `PrimaryButton`, `ConnectionChip`, `EmptyState`, `LoadingShimmer`,
`ConsentCheckbox`. A `@Preview` gallery screen so the whole system reviews on one screen.

### Phase 2 — Firebase backend
Firebase project (Android app registered, `google-services.json`), Auth email/password,
Firestore in the right region, **complete `firestore.rules`** per §2, composite indexes,
App Check (Play Integrity + debug provider). Emulator Suite for local dev, plus a seed
script (2 patients, 1 doctor, 3 realistic sessions).
**Verified by rules unit tests, not by eye:** doctor A must be *unable* to read patient B's
readings, `list` on `/shareCodes` must fail, and a doctor writing a reading must fail.

### Phase 3 — Auth & legal
Signup/login/forgot-password on Firebase Auth, `profiles` doc written with role, session
persistence + auto-login, role-based routing. Terms + Privacy as versioned in-app Markdown.
Consent gate writing `consents` docs. Real legal copy including the medical disclaimer.

### Phase 4 — BLE + simulator (the technical core)
One interface, two implementations:
```kotlin
interface WombCareDeviceSource {
    val state: StateFlow<ConnectionState>
    val readings: SharedFlow<ClinicalReading>
    suspend fun connect(deviceId: String? = null); fun disconnect()
}
class BleDeviceSource(...)       : WombCareDeviceSource   // real MG26
class SimulatedDeviceSource(...) : WombCareDeviceSource   // demo mode
```
Real: scan filtered by service UUID (either pair), connect, discover, subscribe CCCD, parse
per [BLE_CONTRACT.md](BLE_CONTRACT.md) §3, permissions for API 31+ *and* ≤30, auto-reconnect
with backoff, **foreground service** so a 60-min session survives screen-off, optional
`0x180F` battery read.
Simulator: emits the **same 8-byte frames through the same parser** (demo mode exercises
production code, not a parallel fake path), compressed clock (1 window/3 s), scripted
`NORMAL → SUSPECT → PATHOLOGIC → recovery` arc that fires a genuine alert.
Also: invalid-FHR handling, reboot detection, 150 s dropout. Parser unit tests including the
all-zero late-subscriber frame and v2 forward-compat.

### Phase 5 — Patient app
`P1`–`P5`, `P7`, `P8`. Session lifecycle (start → readings → Room → end → summary + rollup),
charts (load `dataviz` first), Pathologic notification with confirm-and-persist and
signal-quality gating, session report + export. Room is the write path; the UI never waits
on the network.

### Phase 6 — Doctor app + linking
`P6` share-and-approve, `D1`–`D4`, `D6`. Sync worker: Room → Firestore, batched, retrying,
idempotent (client-generated doc IDs — the minute index makes writes naturally idempotent),
only when `sharingEnabled`. Doctor side uses snapshot listeners: `liveStatus` for the list,
the current session's `readings` for the live view, `rollup` for history. **"LIVE" only when
the last reading is under 150 s old** — otherwise "Last seen 14 min ago", because a frozen
chart must never look live. Revoke must visibly cut access.

### Phase 7 — Alerts & notifications
`alerts` docs on Pathologic, patient local notification, `D5` feed with acknowledge,
notification channels (Alerts high-priority, Monitoring silent ongoing), Android 13+
`POST_NOTIFICATIONS`, quiet hours. **Doctor push = Cloud Function + Blaze** (see §2 warning);
free-plan fallback is in-app realtime alerts. Every alert screen says "seek clinical care",
never a diagnosis.

### Phase 8 — Polish, hardening, demo
Offline-first pass (airplane-mode test), error/empty/loading states everywhere,
accessibility (TalkBack on every number, 44 dp targets, contrast), strings extracted for
Hindi/Bengali, "delete my data", release signing, and a written **demo script** with timings.
README + architecture diagram.

---

## 6. Firmware asks (parallel track — the app does not block on these)

| # | Ask | Owner | Why it matters to the app |
|---|---|---|---|
| 1 | Confirm final UUIDs; make `btconf` match `uuid.txt` and commit to `main` | Nikolaos / Kunal | Three sources currently disagree |
| 2 | **Payload v2** (10 B): add `motion_state`, `battery_pct`, bump version to 2 | Nikolaos | Two of the six values you specified are not transmitted today |
| 3 | Get BLE compiling — `sl_status_t sc`, single `sl_bt_on_event()`, BLE components in `.slcp` (handoff §8 items 3, 5, 5b) | Nikolaos | Nothing real to connect to until then |
| 4 | Set `SIGNAL_LOW` from IMU trust (TODO at `wombcare_ble.c:80`) | Nikolaos | The flag is never set today, so alert gating can't be exercised on hardware |
| 5 | Fix `minute_window_ready` re-firing every 1 s (handoff §8 item 1) | Malay | Would flood the app with ~60× the intended readings |
| 6 | Confirm whether bonding/pairing will be enabled (`sm_configure` is commented out) | Nikolaos | Changes the connect UX (pairing dialog or not) |
