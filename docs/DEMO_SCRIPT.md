# WombCare — Demo Script

A ~4-minute, two-phone live demo for judges. It proves the whole product: on-device
wellness monitoring, a live doctor link the mother controls, a real alert, and — the ending
most teams skip — that consent is revocable.

> **Framing line to open with (say it once, out loud):** "WombCare is a home
> wellness-awareness aid, not a medical device. It helps a mother notice changes early and
> seek care sooner. All the analysis runs on the wearable itself."

## Setup (before you present)

- **Two phones** (or one phone + one emulator), both on the same network with **working
  internet** (not venue Wi-Fi if it blocks device traffic — tether to a hotspot).
- Phone A: install, **Demo mode ON** (Settings). This is the mother.
- Phone B: install. This is the doctor.
- Have one patient account and one doctor account ready, OR create them live (adds ~40s).
- Firebase project reachable; rules deployed (`firebase deploy --only database`).

## Runsheet

| # | Screen / action | What to say | Proves |
|---|---|---|---|
| 1 | **Phone A** — sign up as mother → consent gate | "She accepts terms, privacy, and — required — that this isn't a medical device." | Consent is explicit, recorded |
| 2 | Dashboard → **Start monitoring** | "The wearable sends one reading a minute. Here it's the simulator so we can show a full arc in seconds." | Live on-device data, honest `--` before first reading |
| 3 | Watch FHR line + status | "Heart rate, kick count, wellness status — Normal for now, in the healthy band." | Clean, honest charts; status = colour + icon + word |
| 4 | **Share** → copy code | "She owns a code. Sharing is off by default — nothing has left her phone until now." | Privacy-by-default |
| 5 | **Phone B** — doctor → Add patient → paste code → Send | "The doctor requests access. He can't see anything yet." | No access without approval |
| 6 | **Phone A** — approve the request | "She approves. Only now can he see her readings." | Consent gates access, visibly |
| 7 | **Phone B** — open the patient | "Same live session, on his phone, marked LIVE." | Cross-device realtime works |
| 8 | Watch the arc turn **Suspect → Pathologic** | "The status escalates. One bad minute doesn't alarm her — it takes two in a row." | Confirm-and-persist (no false alarms) |
| 9 | **Phone A** — alert fires: "contact your doctor" | "She gets a clear prompt to seek care — never a diagnosis." | Alerting + safe framing |
| 10 | **Phone B** — Alerts feed → Acknowledge | "It lands in his inbox; he acknowledges it." | Two-sided alert loop |
| 11 | **Phone A** — Share → **Revoke** | "She revokes. Watch his screen." | **Consent is revocable** |
| 12 | **Phone B** — data is gone | "His access is cut immediately." | The rules enforce it, not just the UI |

**End on step 12.** It's the strongest 15 seconds — most teams demo sharing but never show
that the mother can take it back, on demand, and that the backend actually enforces it.

## If something breaks

- **No live data on Phone B:** check both phones have internet; the doctor sees "last seen"
  not "LIVE" if readings are >150s old. Restart the mother's session.
- **Signup fails with "No connection":** the device/emulator has no working internet — this
  is the #1 gotcha (see the emulator NAT issue in dev notes). Use a real phone on a hotspot.
- **Alert doesn't fire:** the arc needs to reach two consecutive Pathologic windows; in demo
  mode that's ~15–18s after Start. Let it run.

## What NOT to claim

- Don't say "diagnoses", "detects distress", "safe", or "normal baby". Say "wellness
  indicator", "pattern associated with", "seek care sooner".
- Don't imply raw ECG leaves the device. It never does — only per-minute summaries, and only
  when shared.

## One-line pitch (if you only get a sentence)

"WombCare lets an expectant mother monitor her baby's wellbeing at home and share it live
with her doctor — on her terms, revocable any time — with all the analysis running privately
on the device itself."
