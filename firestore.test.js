const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_vpn_user_1";
const BOB_UID = "bob_vpn_user_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserPrefs(uid, now) {
  return {
    userId: uid,
    displayName: "Alice",
    email: "alice@example.com",
    workerBaseUrl: "https://vpn-api.example.workers.dev",
    autoSelectEnabled: true,
    preferredCountryCode: "AUTO",
    createdAt: now,
    updatedAt: now,
  };
}

function validServerPayload(uid, now) {
  return {
    userId: uid,
    name: "Singapore Edge 01",
    countryCode: "SG",
    countryName: "Singapore",
    city: "Singapore",
    endpoint: "203.0.113.10:51820",
    serverPublicKey: "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=",
    clientAddress: "10.8.0.2/32",
    clientPrivateKey: "MTIzNDU2Nzg5MGFiY2RlZmdoaWprbG1ub3BxcnN0dXY=",
    dns: "1.1.1.1, 1.0.0.1",
    allowedIps: "0.0.0.0/0, ::/0",
    mtu: 1280,
    persistentKeepalive: 25,
    latencyMs: 34,
    loadPercent: 18,
    source: "qr",
    favorite: false,
    createdAt: now,
    updatedAt: now,
  };
}

function validLogPayload(uid, now) {
  return {
    userId: uid,
    serverName: "Singapore Edge 01",
    countryCode: "SG",
    verifiedIp: "203.0.113.10",
    verifiedCountry: "Singapore",
    eventType: "connected",
    createdAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated requests are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validUserPrefs(ALICE_UID, now)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("vpn_servers").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("connection_logs").get());
});

test("Owner can create, update, and read user preferences; cross-user and ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPrefs(ALICE_UID, created))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredCountryCode: "SG",
      autoSelectEnabled: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable createdAt cannot change
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Ghost field isAdmin rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validUserPrefs(ALICE_UID, created),
      isAdmin: true,
    })
  );

  // Cross-user read/write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hack" }));
});

test("Owner can manage vpn_servers subcollection; invalid fields, @DocumentId id key, and cross-user access fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // List on empty user-scoped subcollection succeeds
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("vpn_servers").get());

  // Create valid server profile
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("vpn_servers").doc("sg_01")
      .set(validServerPayload(ALICE_UID, created))
  );

  // Partial update (favorite + latencyMs + updatedAt) succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("vpn_servers").doc("sg_01").update({
      favorite: true,
      latencyMs: 28,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Rejects storing @DocumentId `id` field inside document body
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("vpn_servers").doc("sg_02")
      .set({ ...validServerPayload(ALICE_UID, created), id: "sg_02" })
  );

  // Rejects invalid source enum
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("vpn_servers").doc("sg_03")
      .set({ ...validServerPayload(ALICE_UID, created), source: "proton" })
  );

  // Rejects future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("vpn_servers").doc("sg_04")
      .set(validServerPayload(ALICE_UID, new Date(Date.now() + 60000)))
  );

  // Rejects cross-user read & write
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vpn_servers").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vpn_servers").doc("sg_01").delete());
});

test("Owner can create and read append-only connection_logs; updates are denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("connection_logs").doc("log_1")
      .set(validLogPayload(ALICE_UID, now))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("connection_logs").get()
  );

  // Append-only: updates must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("connection_logs").doc("log_1").update({
      verifiedIp: "198.51.100.1",
    })
  );

  // Cross-user read denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("connection_logs").get()
  );
});
