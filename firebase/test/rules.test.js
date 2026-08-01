/**
 * WombCare Realtime Database — security rules test suite.
 *
 * This is the real Phase 2 deliverable. The app's central promise — "a doctor sees a
 * patient's readings only after that patient approved them, and nobody can browse patients"
 * — is enforced entirely by database.rules.json. These tests exist to make that promise
 * fail loudly in CI if a rule ever regresses.
 *
 * Runs against the emulator, so it never touches the real project:
 *   firebase emulators:exec --only auth,database "npm test --prefix firebase"
 */

const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');
const fs = require('fs');
const path = require('path');
const assert = require('assert');

const PATIENT_A = 'patientA';
const PATIENT_B = 'patientB';
const DOCTOR_1 = 'doctor1';
const DOCTOR_2 = 'doctor2';

let testEnv;

// Authenticated database handle for a given uid.
function db(uid) {
  return testEnv.authenticatedContext(uid).database();
}
function unauthedDb() {
  return testEnv.unauthenticatedContext().database();
}

// Overwrite the whole tree with rules DISABLED, so setup can't be blocked by the rules
// we're about to test. Takes a fully NESTED object — RTDB's update() rejects a flat map
// with overlapping ancestor paths, so the full-world seed must be a real tree.
async function seedTree(tree) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref().set(tree);
  });
}

// Incremental change (also rules-disabled), e.g. a patient revoking a doctor. Non-
// overlapping deep paths only — that's what update() is for.
async function patch(updates) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref().update(updates);
  });
}

// A world where doctor1 is APPROVED for patientA, doctor2 is linked to nobody.
async function seedApprovedWorld() {
  await seedTree({
    profiles: {
      patientA: { role: 'patient', fullName: 'A', createdAt: 1 },
      patientB: { role: 'patient', fullName: 'B', createdAt: 1 },
      doctor1: { role: 'doctor', fullName: 'Dr One', createdAt: 1 },
      doctor2: { role: 'doctor', fullName: 'Dr Two', createdAt: 1 },
    },
    patients: {
      patientA: {
        shareCode: 'WMB-AAAA1111',
        sharingEnabled: true,
        authorizedDoctors: { doctor1: true },
        liveStatus: { nsp: 0, fhrBpm: 142, lastReadingAt: 1000 },
        sessions: {
          s1: {
            startedAt: 100, avgFhr: 140, worstNsp: 0,
            readings: { 0: { recordedAt: 100, fhrBpm: 142, nsp: 0 } },
          },
        },
        alerts: { al1: { nsp: 2, createdAt: 500 } },
      },
      patientB: { shareCode: 'WMB-BBBB2222', sharingEnabled: true, authorizedDoctors: {} },
    },
    shareCodes: {
      'WMB-AAAA1111': { patientUid: 'patientA' },
      'WMB-BBBB2222': { patientUid: 'patientB' },
    },
  });
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'wombcare-icc26',
    database: {
      rules: fs.readFileSync(path.resolve(__dirname, '../../database.rules.json'), 'utf8'),
      host: '127.0.0.1',
      port: 9000,
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearDatabase();
});

describe('profiles', () => {
  it('a user can read and write their own profile', async () => {
    await assertSucceeds(
      db(PATIENT_A).ref('profiles/patientA').set({ role: 'patient', fullName: 'A', createdAt: 1 }),
    );
    await assertSucceeds(db(PATIENT_A).ref('profiles/patientA').get());
  });

  it('a user cannot write someone else\'s profile', async () => {
    await assertFails(
      db(DOCTOR_1).ref('profiles/patientA').set({ role: 'patient', fullName: 'hacked', createdAt: 1 }),
    );
  });

  it('an unknown role value is rejected', async () => {
    await assertFails(
      db(PATIENT_A).ref('profiles/patientA').set({ role: 'admin', fullName: 'A', createdAt: 1 }),
    );
  });

  it('an unlinked doctor cannot read a patient profile', async () => {
    await seedApprovedWorld();
    await assertFails(db(DOCTOR_2).ref('profiles/patientA').get());
  });

  it('an approved doctor CAN read the linked patient profile', async () => {
    await seedApprovedWorld();
    await assertSucceeds(db(DOCTOR_1).ref('profiles/patientA').get());
  });
});

describe('readings — the core promise', () => {
  it('the patient can read her own readings', async () => {
    await seedApprovedWorld();
    await assertSucceeds(db(PATIENT_A).ref('patients/patientA/sessions/s1/readings').get());
  });

  it('an APPROVED doctor can read the patient readings', async () => {
    await seedApprovedWorld();
    await assertSucceeds(db(DOCTOR_1).ref('patients/patientA/sessions/s1/readings').get());
  });

  it('doctor A cannot read patient B (no link) — THE test', async () => {
    await seedApprovedWorld();
    await assertFails(db(DOCTOR_1).ref('patients/patientB/sessions').get());
    await assertFails(db(DOCTOR_2).ref('patients/patientA/sessions/s1/readings').get());
  });

  it('an unauthenticated client can read nothing', async () => {
    await seedApprovedWorld();
    await assertFails(unauthedDb().ref('patients/patientA/sessions/s1/readings').get());
  });

  it('a doctor cannot write clinical data', async () => {
    await seedApprovedWorld();
    await assertFails(
      db(DOCTOR_1).ref('patients/patientA/sessions/s1/readings/1').set({ recordedAt: 200, fhrBpm: 99, nsp: 0 }),
    );
    await assertFails(db(DOCTOR_1).ref('patients/patientA/liveStatus/nsp').set(2));
  });

  it('revoking a doctor immediately cuts read access', async () => {
    await seedApprovedWorld();
    await assertSucceeds(db(DOCTOR_1).ref('patients/patientA/sessions/s1/readings').get());
    // Patient revokes.
    await patch({ 'patients/patientA/authorizedDoctors/doctor1': null });
    await assertFails(db(DOCTOR_1).ref('patients/patientA/sessions/s1/readings').get());
  });
});

describe('share codes — resolvable but not enumerable', () => {
  it('a doctor can resolve a code they already know (get)', async () => {
    await seedApprovedWorld();
    await assertSucceeds(db(DOCTOR_2).ref('shareCodes/WMB-BBBB2222').get());
  });

  it('nobody can list the shareCodes collection', async () => {
    await seedApprovedWorld();
    // Reading the parent would enumerate every code -> every patient. Must fail for all.
    await assertFails(db(DOCTOR_1).ref('shareCodes').get());
    await assertFails(db(PATIENT_A).ref('shareCodes').get());
    await assertFails(unauthedDb().ref('shareCodes').get());
  });

  it('a patient cannot read a code (only doctors resolve)', async () => {
    await seedApprovedWorld();
    await assertFails(db(PATIENT_B).ref('shareCodes/WMB-AAAA1111').get());
  });

  it('a patient can claim a code that maps to themselves', async () => {
    await patch({ 'profiles/patientA': { role: 'patient', fullName: 'A', createdAt: 1 } });
    await assertSucceeds(
      db(PATIENT_A).ref('shareCodes/WMB-NEWCODE1').set({ patientUid: 'patientA' }),
    );
  });

  it('a patient cannot claim a code pointing at someone else', async () => {
    await patch({ 'profiles/patientA': { role: 'patient', fullName: 'A', createdAt: 1 } });
    await assertFails(
      db(PATIENT_A).ref('shareCodes/WMB-STOLEN01').set({ patientUid: 'patientB' }),
    );
  });
});

describe('care links + alert acknowledgement', () => {
  it('a doctor can create a pending link request', async () => {
    await seedApprovedWorld();
    await assertSucceeds(
      db(DOCTOR_2).ref('careLinks/doctor2/patientB').set({
        status: 'pending', doctorName: 'Dr Two', requestedAt: 10,
      }),
    );
  });

  it('a doctor cannot self-approve a link', async () => {
    await seedApprovedWorld();
    await assertFails(
      db(DOCTOR_2).ref('careLinks/doctor2/patientB').set({
        status: 'active', doctorName: 'Dr Two', requestedAt: 10,
      }),
    );
  });

  it('an approved doctor may acknowledge an alert, and only the ack fields', async () => {
    await seedApprovedWorld();
    await assertSucceeds(
      db(DOCTOR_1).ref('patients/patientA/alerts/al1/acknowledgedBy').set('doctor1'),
    );
    // ...but cannot rewrite the clinical severity of the alert.
    await assertFails(db(DOCTOR_1).ref('patients/patientA/alerts/al1/nsp').set(0));
  });

  it('a doctor cannot acknowledge as a different doctor', async () => {
    await seedApprovedWorld();
    await assertFails(
      db(DOCTOR_1).ref('patients/patientA/alerts/al1/acknowledgedBy').set('doctor2'),
    );
  });
});

describe('consents — write-once audit trail', () => {
  it('a user can create their own consent record', async () => {
    await assertSucceeds(
      db(PATIENT_A).ref('consents/patientA/c1').set({
        docType: 'terms', docVersion: '2026-07-01', acceptedAt: 100,
      }),
    );
  });

  it('a consent record cannot be modified once written', async () => {
    await patch({
      'consents/patientA/c1': { docType: 'terms', docVersion: '2026-07-01', acceptedAt: 100 },
    });
    await assertFails(
      db(PATIENT_A).ref('consents/patientA/c1/docVersion').set('tampered'),
    );
    await assertFails(db(PATIENT_A).ref('consents/patientA/c1').remove());
  });

  it('an unknown consent docType is rejected', async () => {
    await assertFails(
      db(PATIENT_A).ref('consents/patientA/c2').set({
        docType: 'whatever', docVersion: 'x', acceptedAt: 1,
      }),
    );
  });
});
