# Firebase Setup — Phase 2

Two parts. **Part A** is console work only you can do (~12 minutes). **Part B** is the code
and rules, which I write once Part A is done. Part C lists exactly what to send back to me.

Related: [PLAN.md §2](PLAN.md) has the data model and all seven security rules.

---

## Part A — Firebase console (you)

### A1 · Create the project

1. Go to <https://console.firebase.google.com> → **Add project**.
2. Name it `WombCare` (the console will derive a project ID like `wombcare-a1b2c` — note it
   down, I need it for `.firebaserc`). If you want a clean ID, click the pencil next to it
   and set `wombcare-icc26`.
3. **Google Analytics: turn it off.** We don't use it, and it adds a consent surface we'd
   then have to disclose in the privacy policy for no benefit.
4. Create project.

### A2 · Register the Android app — twice

This is the one step with a trap. The debug build has `applicationIdSuffix = ".debug"`, so
it installs as a *different* package. `google-services.json` only contains the packages you
register, and a missing one fails at runtime with *"No matching client found for package
name"*.

So register **both**:

1. Project overview → **Add app** → Android icon.
2. Package name: `com.silicovegas.wombcare` → Register app → **Next/Continue** through the
   remaining steps (skip the SDK instructions, we've done that).
3. Add app → Android **again**. Package name: `com.silicovegas.wombcare.debug`.
   Nickname it "WombCare (debug)".

App nickname and SHA-1 can stay empty for now — email/password auth doesn't need SHA-1.
You'll need it before a release build (see A6).

> Alternative if you'd rather register once: delete `applicationIdSuffix = ".debug"` from
> `app/build.gradle.kts`. You lose the ability to keep debug and release installed side by
> side on one phone. I'd keep the suffix.

### A3 · Download `google-services.json`

1. Project settings (gear icon) → **Your apps** → pick the `com.silicovegas.wombcare` app →
   **google-services.json** → download.
2. Put it at exactly:
   ```
   wombcare-app/app/google-services.json
   ```
3. Open it and confirm it contains **both** package names — search for `.debug`. If it
   doesn't, you downloaded before registering the second app; re-download.

> **It's gitignored on purpose**, so every teammate downloads their own. To be clear about
> what it is: the Android "API key" inside it is an *identifier*, not a secret — Firebase
> security comes from security rules plus App Check, never from hiding this file. Don't
> panic if it leaks; don't commit it either.

### A4 · Enable Authentication

1. Build → **Authentication** → Get started.
2. **Sign-in method** → **Email/Password** → Enable → **Save**.
   Leave "Email link (passwordless)" off.

### A5 · Create Firestore

1. Build → **Firestore Database** → Create database.
2. **Start in production mode.** Not test mode — test mode leaves the database world-readable
   for 30 days, and we are writing real rules in Phase 2 anyway, so test mode buys nothing
   and risks shipping with it still on.
3. Location: **`asia-south1` (Mumbai)** — lowest latency for India.
   ⚠️ **This is permanent.** It cannot be changed later without recreating the project.
4. Keep the database ID as `(default)`.

Everything will be denied until I deploy the rules — that's expected and correct.

### A6 · SHA-1 (only before a release build — skip for now)

When you get there, from `wombcare-app/`:

```bash
./gradlew signingReport
```

Copy the **SHA1** under `Variant: debug` (and later your release keystore's) into
Project settings → Your apps → **Add fingerprint**. Needed for App Check Play Integrity.

### A7 · Blaze plan — a Phase 7 decision, not now

Doctor **push** notifications need a Cloud Function, which needs the **Blaze** plan (a card
on file). Free-tier quotas cover our volume many times over, so real cost is ≈ ₹0.
If you'd rather not add a card, the doctor still gets in-app realtime alerts while the app
is open — everything else works on the free Spark plan. Decide before Phase 7.

If you do upgrade: set **Budget alerts** at ₹100 immediately (Usage and billing → Details
and settings → Budget alerts). Not optional in my view — an accidental loop in a Cloud
Function is the one way this project could cost real money.

---

## Part B — Rules, CLI, and code (me, after Part A)

### B1 · One thing you run yourself

The CLI login opens a browser and waits for you, so it can't run from my side:

```bash
npm install -g firebase-tools     # node v22 is already installed on this machine
firebase login
```

Then confirm the project is visible:

```bash
firebase projects:list
```

### B2 · Then I do all of this

| # | What | File / action |
|---|---|---|
| 1 | Point the repo at your project | `.firebaserc`, `firebase.json` |
| 2 | Write all seven security rules from [PLAN.md §2](PLAN.md) | `firestore.rules` |
| 3 | Composite indexes for the doctor's queries | `firestore.indexes.json` |
| 4 | **Rules unit tests** — the real deliverable | `firebase/test/rules.test.js` via `@firebase/rules-unit-testing` against the emulator |
| 5 | Enable the Firebase plugin + dependencies | uncomment in `app/build.gradle.kts` + `libs.versions.toml` (already staged) |
| 6 | App Check: Play Integrity for release, debug provider for dev | `WombCareApp.kt` |
| 7 | Firestore DTOs + repository layer, share-code generator with the claim transaction | `core/data/` |
| 8 | Seed script: 2 patients, 1 doctor, 3 realistic sessions | `firebase/seed.js` |
| 9 | Deploy | `firebase deploy --only firestore:rules,firestore:indexes` |

### B3 · How Phase 2 gets proved

Not by clicking around — by tests that must fail if the rules are wrong. Against the
emulator, so nothing touches your real project:

- doctor A **cannot** read patient B's readings (the core promise of the whole app)
- `list` on `/shareCodes` **fails**, while `get` on a known code succeeds for a doctor
  (this asymmetry is what makes code-pasting work without letting anyone enumerate patients)
- a doctor **cannot** write a reading, a session, or a patient profile
- a doctor **can** update only `acknowledgedBy`/`acknowledgedAt` on an alert, nothing else
- a patient **cannot** modify a `consents` row after creating it
- a revoked doctor immediately loses read access

Emulator commands, for reference:

```bash
firebase emulators:start --only auth,firestore
firebase emulators:exec --only firestore "npm test --prefix firebase"
```

---

## Part C — Send me these four things

1. **Project ID** (e.g. `wombcare-icc26`) — from Project settings.
2. **Confirmation both package names are registered** (`com.silicovegas.wombcare` and
   `...debug`) — or tell me you dropped the suffix instead.
3. **`google-services.json` is at `wombcare-app/app/`** — just say done; I'll verify it
   parses and contains both clients.
4. **Firestore region** you picked, if not `asia-south1`.

Then I start Part B.

---

## Meanwhile

Part A blocks nothing else. **Phase 4 (BLE + simulator)** needs no backend at all, and it's
what puts live-looking data and working charts on screen. If you want, I'll build that while
you do the console work.
