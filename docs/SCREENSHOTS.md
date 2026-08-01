# WombCare — App Screenshots

Every page of the app, captured on a Pixel 9 emulator (Android 15). Auth/legal pages are
real navigation; the signed-in pages were rendered via a temporary debug harness because the
emulator has no working internet (so live Firebase data isn't present — those pages show
their empty/waiting states, which are real app states).

## Shared — onboarding, role, auth, legal

| | | |
|---|---|---|
| **Onboarding**<br>[01](screenshots/01-onboarding.png) | **Role select**<br>[02](screenshots/02-role-select.png) | **Login**<br>[11](screenshots/11-login.png) |
| **Sign up**<br>[03](screenshots/03-signup.png) | **Consent gate** (all 3 checked → button enabled)<br>[04](screenshots/04-consent-gate.png) | **Forgot password**<br>[12](screenshots/12-forgot-password.png) |
| **Terms of Service**<br>[13](screenshots/13-terms.png) | **Privacy Policy**<br>[14](screenshots/14-privacy.png) | **Signup offline error** (friendly, not a stack trace)<br>[05](screenshots/05-signup-offline-error.png) |

## Patient (rose theme)

| | | |
|---|---|---|
| **Dashboard — idle** (FHR shows `--`, not 0)<br>[06](screenshots/06-dashboard-idle.png) | **Live — Pathologic arc** (line climbing, NSP strip green→red, alert prompt)<br>[07](screenshots/07-pathologic-arc.png) | **Full charts** (HR peak+recovery, NSP strip, kick bars, battery "not sent")<br>[08](screenshots/08-charts-full.png) |
| **Share & care team** (sharing off by default)<br>[16](screenshots/16-patient-share.png) | **Settings** (demo toggle, delete my data)<br>[15](screenshots/15-patient-settings.png) | |

## Doctor (sky-blue theme)

| | | |
|---|---|---|
| **Patient list** (live dots)<br>[09](screenshots/09-doctor-patient-list.png) | **Add patient** (paste code)<br>[10](screenshots/10-doctor-add-patient.png) | **Patient detail** (live view; waiting state shown)<br>[18](screenshots/18-doctor-patient-detail.png) |
| **Alerts feed** (acknowledge)<br>[17](screenshots/17-doctor-alerts.png) | | |

## Notes

- **Rose = patient, sky-blue = doctor** — the two role themes off one token set, so the apps
  are never confused in a demo.
- **Honest empty/invalid states** are visible on purpose: `--` for a missing measurement,
  "Waiting for the first reading", "No readings shared yet", battery "Not sent by this
  device". These are designed states, not bugs.
- The **live doctor detail** and **patient dashboard mid-arc** show real data in the working
  app (see 07/08); the doctor detail here (18) shows the pre-data waiting state because the
  screenshot harness had no signed-in session.
- Not yet screenshottable on this machine: the **two-phone live sync** (patient on one phone,
  doctor watching live on another) — the emulator has no internet. That needs a real device.
