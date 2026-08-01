# WombCare App — Feature Specification

Every feature below is traced to the data that actually feeds it. The device gives us
**8 bytes once per 60 seconds** (`nsp`, `confidence`, `fhr_bpm`, `kick_count`, `flags`,
`timestamp` — see [BLE_CONTRACT.md](BLE_CONTRACT.md)). Nothing here invents data we don't
receive.

**Tiers:** `T1` = must ship / demo-critical · `T2` = should ship · `T3` = if time permits.

---

## A. Patient app (the mother)

### A1 · Account & onboarding — `T1`
- 3-card onboarding: what WombCare is, **what it is not**, how it works.
- Role choice (Mother / Doctor) → separate signup flows.
- Email + password signup, login, forgot-password, persistent auto-login.
- Profile: name, phone, due date → derived **pregnancy week counter**, emergency contact.
- **Consent gate:** three separate checkboxes — Terms, Privacy, and *"I understand this is
  not a medical device and does not replace clinical care."* Continue stays disabled until
  all three; each writes an audit row with the document version.
- Terms of Service + Privacy Policy as versioned in-app documents (also required for Play
  Store). Re-consent prompt if a version changes.

### A2 · Device pairing & connection — `T1`
- Scan filtered by the WombCare service UUID (accepts both UUID pairs).
- Remembers the device → one-tap reconnect next time, auto-reconnect with backoff.
- Live connection state: `Scanning · Connecting · Connected · Monitoring · Signal lost`.
- Permission flows for Android 12+ (`BLUETOOTH_SCAN`/`CONNECT`) *and* ≤11 (location).
- **Guided help:** "Press the button on your WombCare device to begin" — the board boots
  asleep and BTN0 starts monitoring, so the app must say so or the user sees a dead screen.
- Battery-optimisation prompt before a long session.

### A3 · Live monitoring session — `T1`
- Start/stop a session; elapsed timer; **first reading arrives at ~60 s**, so an explicit
  "collecting first minute…" state rather than a screen of zeros.
- Runs as a **foreground service** — screen can turn off, the 60-minute session survives.
- Charts update once per minute (see A5).
- Session auto-splits on device reboot (detected when `timestamp` goes backwards).
- Signal-loss banner after 150 s of silence, distinct from "disconnected".

### A4 · Dashboard — `T1`
Six tiles, big tabular numbers, one status hero:

| Tile | Source | Notes |
|---|---|---|
| **Status** (Normal / Suspect / Pathologic) | `nsp` | Hero card. Colour **+ icon + word**, never colour alone |
| **Fetal heart rate** | `fhr_bpm` | `--` when 0 (invalid), normal range 110–160 shown as context |
| **Kicks** | `kick_count` | Session running total (app sums; firmware sends per-window) |
| **Confidence** | `confidence` | Dimmed + "signal quality low" when `SIGNAL_LOW` flag set |
| **Motion** | `flags` bit 2 | `Resting` / `Active` today · full `Resting/Sitting/Walking` after payload v2 |
| **Battery** | — | `—` today · needs payload v2 or Battery Service `0x180F` |

Plus a persistent, non-dismissible footnote: *wellness awareness, not a diagnosis.*

### A5 · Charts & trends — `T1`
- **FHR trend** — line, 1 point/min, shaded 110–160 normal band, **line breaks across gaps**
  (never interpolate a reading the device didn't make), invalid points skipped.
- **NSP timeline** — one thin green/amber/red cell per minute under the FHR chart. The
  2-second read of the whole session.
- **Kick chart** — bars per minute + cumulative total.
- **Confidence meter** — slim bar, honest about low signal.
- **Session summary** — avg FHR · total kicks · minutes in each status · worst status.
- **Cross-session trends** `T2` — FHR baseline and kick counts week over week.

### A6 · History & reports — `T1`
- Session list: date, duration, avg FHR, kicks, worst status, alert marker.
- Session detail = the full replay of charts above.
- **Export / share a report** `T2` — PDF or formatted text with the trend image, summary
  stats, and the disclaimer, shareable via WhatsApp/email for a mother whose doctor isn't
  on the app.

### A7 · Alerts — `T1`
- **Pathologic → alert.** Local high-priority notification + full-screen in-app card with
  *"contact your doctor"* guidance and a one-tap call to her doctor or emergency contact.
- **Confirm-and-persist:** requires **2 consecutive** Pathologic windows before alerting, so
  one noisy minute can't panic her.
- **Signal-quality gating:** if `SIGNAL_LOW` is set or `confidence` is very low, the same
  reading becomes *"please sit still and re-check"* instead of an alert — a false alarm to a
  pregnant woman is a real harm, not a UX blemish.
- **Suspect → amber in-app banner only**, no push. Three consecutive → a gentle "consider
  checking in with your doctor".
- Alert history with what was shown and when.
- Quiet hours `T3` (alerts always override).

### A8 · Share & care team — `T1`  ← *the doctor-linking feature*
- **Her unique ID**, big and copyable: `WMB-XXXXXXXX` (Crockford base32 — no I/L/O/U, so
  nothing is misread over WhatsApp).
- Share via **Copy · WhatsApp · Email · QR code** (`T2` for QR).
- **Approval flow:** doctor pastes the code → she gets *"Dr. Sharma requested access to
  your readings"* → **Approve / Deny**. Consent is a visible, on-screen act.
- Linked-doctor list with **Revoke** — access is cut immediately and visibly.
- **Rotate code** — invalidates the old code without breaking existing approved links.
- Master **sharing toggle, default OFF**: with it off, nothing ever leaves the phone.

### A9 · Privacy & settings — `T1`
- Sharing on/off, notification preferences, **demo mode toggle**, units, theme.
- **Delete my data** — wipes cloud copies and local history, GDPR-shaped.
- Plain-language "what we store and what we never store" screen: derived summaries only,
  never raw ECG/PVDF waveform, never a cloud model.

---

## B. Doctor app

### B1 · Doctor account — `T1`
Signup with name, medical registration number, clinic, specialty. Separate role, separate
sky-blue theme so the two apps are never confused in a demo. `verified` flag reserved for a
future manual review step.

### B2 · Link a patient — `T1`
Paste the code (or scan the QR) → request goes out as **pending** → appears once the mother
approves. Clear pending/denied states. A doctor can never discover a patient any other way —
no search, no browse, by design.

### B3 · Patient list — `T1`
One row per linked patient: name, pregnancy week, last reading time, **live dot**, worst
status in the last 24 h, unacknowledged-alert badge. Sorted so anyone needing attention is
at the top.

### B4 · Live patient view — `T1`
The same charts as the mother sees, at clinical density — FHR trend + NSP timeline + kicks +
confidence, updating in near-realtime while her session runs. **"LIVE" only when the last
reading is under 150 s old**; otherwise "Last seen 14 min ago". A frozen chart must never
look live.

### B5 · Session review — `T1`
Full history for that patient, session by session, with summary stats and the alert log.
Compare sessions side by side `T3`.

### B6 · Alert feed — `T1`
All alerts across all linked patients, newest first, with **Acknowledge** — recorded with
who acknowledged and when. This is the doctor's inbox.

### B7 · Read-only by construction — `T1`
The doctor can write exactly two things: a care-link request, and an alert acknowledgement
(+ private notes at `T3`). They can never create, edit, or delete clinical data. Enforced in
the backend rules, not just the UI.

---

## C. System features (cross-cutting)

| # | Feature | Tier | Why |
|---|---|---|---|
| C1 | **Demo / simulator mode** | `T1` | Emits the same 8-byte frames through the same parser as real BLE, on a compressed clock, with a scripted `Normal → Suspect → Pathologic → recovery` arc that fires a genuine alert. The firmware BLE doesn't compile yet — this is what makes the app demoable today, and it exercises production code, not a parallel fake path |
| C2 | **Offline-first** | `T1` | Room is the write-path of record. Live view and history work in airplane mode; cloud sync is a background catch-up, never a blocker |
| C3 | **Foreground monitoring service** | `T1` | A 60-minute session must survive screen-off and Doze |
| C4 | **Notification channels** | `T1` | Alerts = high priority; Monitoring = silent ongoing. Android 13+ permission prompt |
| C5 | **Security model** | `T1` | Backend rules enforce "doctor sees only approved patients"; code lookup is get-only so codes can't be enumerated; App Check blocks non-app clients |
| C6 | **Accessibility** | `T2` | TalkBack labels on every number, 44 dp targets, contrast-checked, status never colour-only |
| C7 | **Localisation-ready** | `T3` | All strings extracted; Hindi / Bengali are realistic additions for the target user |
| C8 | **Light + dark theme** | `T2` | One token set, both modes |

---

## D. Deliberately NOT in scope — and why

Saying this out loud protects the timeline and answers the obvious judge question.

| Not doing | Reason |
|---|---|
| **Raw ECG / CTG waveform display** | BLE sends one summary per minute, not a waveform. Showing a "trace" would mean fabricating it |
| **Any diagnosis, risk score, or prediction in the app** | The MG26 produces the classification. The app displays and contextualises — it never adds a second opinion |
| **Uterine contraction (UC) monitoring** | No sensor for it. The model's UC-derived features come from the training dataset, not from this hardware |
| **Cloud ML / server-side inference** | Inference stays on-device. This is the privacy claim; breaking it would break the pitch |
| **Doctor↔patient chat, appointments, prescriptions** | A different product. The one-tap call in A7 covers the actual urgent need |
| **Twins / multiple pregnancy** | Firmware reports a single FHR |
| **iOS** | Native Android was the chosen stack |
| **Manual kick counter** | Tempting and cheap, but it puts a mother-entered number next to a sensor-derived one on the same screen. Revisit post-demo |

---

## E. What the demo actually looks like (~3 minutes)

1. Mother signs up → consent gate → dashboard. *(A1)*
2. Demo mode on → "device" connects → first reading lands, charts start filling. *(C1, A3)*
3. Status reads **Normal**, FHR 142, kicks climbing, confidence 87%. *(A4, A5)*
4. She opens Share, copies `WMB-…`, "sends it on WhatsApp". *(A8)*
5. Second phone: doctor pastes the code → mother taps **Approve** → patient appears. *(B2)*
6. Doctor opens the live view — the same session, updating, marked LIVE. *(B4)*
7. Scripted arc turns **Suspect → Pathologic** → mother's phone alerts with "contact your
   doctor"; the doctor's alert feed lights up simultaneously. *(A7, B6)*
8. Mother taps **Revoke** → the doctor's access disappears. *(A8)*

That last step is the one most teams won't show, and it's the one that proves consent is
real rather than decorative.
