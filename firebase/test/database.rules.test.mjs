import { readFile } from "node:fs/promises";
import { after, before, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment
} from "@firebase/rules-unit-testing";
import { initializeApp, deleteApp } from "firebase/app";
import {
  connectAuthEmulator,
  getAuth,
  signInWithEmailAndPassword,
  signOut
} from "firebase/auth";
import {
  connectDatabaseEmulator,
  get,
  getDatabase,
  ref,
  set,
  update
} from "firebase/database";

const projectId = "demo-smart-garden";
const namespace = `${projectId}-default-rtdb`;
const zonePath = "smart_garden/zones/zone_a";
const seed = JSON.parse(
  await readFile(new URL("../seed.json", import.meta.url), "utf8")
);
const rules = await readFile(
  new URL("../database.rules.json", import.meta.url),
  "utf8"
);

let testEnv;
const clientApps = [];
let adminDb;
let deviceDb;
let anonymousDb;
let outsiderDb;

async function signedInDatabase(name, email, password) {
  const app = initializeApp(
    { projectId, apiKey: "demo-key", databaseURL: `http://127.0.0.1:9000/?ns=${projectId}-default-rtdb` },
    name
  );
  clientApps.push(app);
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  await signInWithEmailAndPassword(auth, email, password);
  const db = getDatabase(app);
  connectDatabaseEmulator(db, "127.0.0.1", 9000);
  return db;
}

function anonymousDatabase(name) {
  const app = initializeApp(
    { projectId, apiKey: "demo-key", databaseURL: `http://127.0.0.1:9000/?ns=${projectId}-default-rtdb` },
    name
  );
  clientApps.push(app);
  const db = getDatabase(app);
  connectDatabaseEmulator(db, "127.0.0.1", 9000);
  return db;
}

async function resetDatabase() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database()), seed);
  });
}

async function markDeviceFresh() {
  const nowSeconds = Math.floor(Date.now() / 1000);
  await assertSucceeds(
    set(ref(deviceDb, `${zonePath}/device`), {
      online: true,
      last_seen: nowSeconds,
      acknowledged_request_id: ""
    })
  );
}

async function createPendingRequest(
  requestId = "123e4567-e89b-42d3-a456-426614174000"
) {
  await markDeviceFresh();
  await assertSucceeds(update(ref(adminDb, `${zonePath}/control`), { mode: "manual" }));
  await assertSucceeds(
    update(ref(adminDb, `${zonePath}/control`), {
      manual_water: true,
      request_id: requestId,
      request_expires_at: Math.floor(Date.now() / 1000) + 10
    })
  );
  return requestId;
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: namespace,
    database: { host: "127.0.0.1", port: 9000, rules }
  });
  adminDb = await signedInDatabase(
    "admin-client",
    "admin@garden.test",
    "GardenDemo123!"
  );
  deviceDb = await signedInDatabase(
    "device-client",
    "esp32@garden.test",
    "DeviceDemo123!"
  );
  anonymousDb = anonymousDatabase("anonymous-client");
  outsiderDb = testEnv.authenticatedContext("outsider").database();
});

beforeEach(resetDatabase);

after(async () => {
  for (const app of clientApps) {
    if (getAuth(app).currentUser) await signOut(getAuth(app));
    await deleteApp(app);
  }
  await testEnv.cleanup();
});

test("seeded admin and device can read zone_a", async () => {
  const [adminSnapshot, deviceSnapshot] = await Promise.all([
    assertSucceeds(get(ref(adminDb, zonePath))),
    assertSucceeds(get(ref(deviceDb, zonePath)))
  ]);
  assert.equal(adminSnapshot.child("control/moisture_start").val(), 30);
  assert.equal(deviceSnapshot.child("control/moisture_stop").val(), 45);
});

test("anonymous and unassigned authenticated users cannot read zone_a", async () => {
  await assertFails(get(ref(anonymousDb, zonePath)));
  await assertFails(get(ref(outsiderDb, zonePath)));
});

test("clients cannot read or change role assignments", async () => {
  await assertFails(get(ref(adminDb, "smart_garden/access")));
  await assertFails(set(ref(adminDb, "smart_garden/access/admins/outsider"), true));
  await assertFails(set(ref(deviceDb, "smart_garden/access/admins/demo-esp32"), true));
});

test("admin can atomically update valid mode, duration, and strict thresholds", async () => {
  await assertSucceeds(
    update(ref(adminDb, `${zonePath}/control`), {
      mode: "manual",
      manual_duration_ms: 15000,
      moisture_start: 20,
      moisture_stop: 60
    })
  );
});

test("admin threshold writes reject equal, reversed, out-of-range, and malformed values", async () => {
  await assertFails(
    update(ref(adminDb, `${zonePath}/control`), { moisture_start: 45 })
  );
  await assertFails(
    update(ref(adminDb, `${zonePath}/control`), {
      moisture_start: 70,
      moisture_stop: 60
    })
  );
  await assertFails(
    update(ref(adminDb, `${zonePath}/control`), {
      moisture_start: -1,
      moisture_stop: 101
    })
  );
  await assertFails(
    update(ref(adminDb, `${zonePath}/control`), { moisture_start: "30" })
  );
});

test("admin settings reject invalid modes, duration bounds, unknown fields, and deletion", async () => {
  await assertFails(update(ref(adminDb, `${zonePath}/control`), { mode: "automatic" }));
  await assertFails(update(ref(adminDb, `${zonePath}/control`), { manual_duration_ms: 999 }));
  await assertFails(update(ref(adminDb, `${zonePath}/control`), { manual_duration_ms: 15001 }));
  await assertFails(update(ref(adminDb, `${zonePath}/control`), { unexpected: true }));
  await assertFails(set(ref(adminDb, `${zonePath}/control`), null));
});

test("admin cannot write device-owned telemetry", async () => {
  await assertFails(set(ref(adminDb, `${zonePath}/pump/status`), true));
  await assertFails(set(ref(adminDb, `${zonePath}/device/online`), true));
  await assertFails(set(ref(adminDb, `${zonePath}/sensors/soil_moisture`), 10));
});

test("admin can submit a fresh UUID manual request only in safe manual conditions", async () => {
  await markDeviceFresh();
  await assertSucceeds(update(ref(adminDb, `${zonePath}/control`), { mode: "manual" }));
  await assertSucceeds(
    update(ref(adminDb, `${zonePath}/control`), {
      manual_water: true,
      request_id: "123e4567-e89b-42d3-a456-426614174000",
      request_expires_at: Math.floor(Date.now() / 1000) + 10
    })
  );
});

test("manual request rejects malformed, expired, overlong, replayed, and outstanding commands", async () => {
  await markDeviceFresh();
  await assertSucceeds(update(ref(adminDb, `${zonePath}/control`), { mode: "manual" }));
  const nowSeconds = Math.floor(Date.now() / 1000);
  await assertFails(
    update(ref(adminDb, `${zonePath}/control`), {
      manual_water: true,
      request_id: "not-a-uuid",
      request_expires_at: nowSeconds + 10
    })
  );
  await assertFails(
    update(ref(adminDb, `${zonePath}/control`), {
      manual_water: true,
      request_id: "123e4567-e89b-42d3-a456-426614174001",
      request_expires_at: nowSeconds
    })
  );
  await assertFails(
    update(ref(adminDb, `${zonePath}/control`), {
      manual_water: true,
      request_id: "123e4567-e89b-42d3-a456-426614174002",
      request_expires_at: nowSeconds + 11
    })
  );
  const requestId = await createPendingRequest();
  await assertFails(
    update(ref(adminDb, `${zonePath}/control`), {
      manual_water: true,
      request_id: "123e4567-e89b-42d3-a456-426614174003",
      request_expires_at: Math.floor(Date.now() / 1000) + 10
    })
  );
  await assertFails(update(ref(adminDb, `${zonePath}/control`), { manual_water: false }));
  assert.equal(requestId, "123e4567-e89b-42d3-a456-426614174000");
});

test("admin can replace an expired unacknowledged request with a new UUID and deadline", async () => {
  await markDeviceFresh();
  const nowSeconds = Math.floor(Date.now() / 1000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await update(ref(context.database(), `${zonePath}/control`), {
      mode: "manual",
      manual_water: true,
      request_id: "123e4567-e89b-42d3-a456-426614174010",
      request_expires_at: nowSeconds - 1
    });
  });
  await assertSucceeds(
    update(ref(adminDb, `${zonePath}/control`), {
      manual_water: true,
      request_id: "123e4567-e89b-42d3-a456-426614174011",
      request_expires_at: Math.floor(Date.now() / 1000) + 10
    })
  );
});

test("manual request rejects auto mode, running pump, and stale device", async () => {
  const request = {
    manual_water: true,
    request_id: "123e4567-e89b-42d3-a456-426614174004",
    request_expires_at: Math.floor(Date.now() / 1000) + 10
  };
  await assertFails(update(ref(adminDb, `${zonePath}/control`), request));
  await assertSucceeds(update(ref(adminDb, `${zonePath}/control`), { mode: "manual" }));
  await assertFails(update(ref(adminDb, `${zonePath}/control`), request));
  await markDeviceFresh();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await update(ref(context.database(), `${zonePath}/pump`), {
      status: true,
      last_started_at: Math.floor(Date.now() / 1000)
    });
  });
  await assertFails(update(ref(adminDb, `${zonePath}/control`), request));
});

test("device can publish a coherent validated sensor snapshot", async () => {
  await assertSucceeds(
    set(ref(deviceDb, `${zonePath}/sensors`), {
      soil_moisture: 0,
      soil_raw: 4095,
      temperature: -40,
      humidity: 100,
      light: 200000,
      updated_at: Math.floor(Date.now() / 1000)
    })
  );
});

test("sensor writes reject malformed values, unknown fields, stale timestamps, and partial leaf writes", async () => {
  const nowSeconds = Math.floor(Date.now() / 1000);
  await assertFails(
    set(ref(deviceDb, `${zonePath}/sensors`), {
      soil_moisture: 101,
      updated_at: nowSeconds
    })
  );
  await assertFails(
    set(ref(deviceDb, `${zonePath}/sensors`), {
      temperature: "hot",
      updated_at: nowSeconds
    })
  );
  await assertFails(
    set(ref(deviceDb, `${zonePath}/sensors`), {
      updated_at: nowSeconds - 31,
      extra: 1
    })
  );
  await assertFails(set(ref(deviceDb, `${zonePath}/sensors/soil_moisture`), 30));
});

test("device can report pump transitions and heartbeat but malformed telemetry is rejected", async () => {
  const nowSeconds = Math.floor(Date.now() / 1000);
  await assertSucceeds(
    set(ref(deviceDb, `${zonePath}/pump`), {
      status: true,
      last_started_at: nowSeconds,
      last_stopped_at: 0
    })
  );
  await assertSucceeds(
    set(ref(deviceDb, `${zonePath}/pump`), {
      status: false,
      last_started_at: nowSeconds,
      last_stopped_at: nowSeconds
    })
  );
  await assertSucceeds(
    set(ref(deviceDb, `${zonePath}/device`), {
      online: true,
      last_seen: nowSeconds,
      acknowledged_request_id: ""
    })
  );
  await assertFails(update(ref(deviceDb, `${zonePath}/pump`), { status: "on" }));
  await assertFails(update(ref(deviceDb, `${zonePath}/device`), { last_seen: -1 }));
});

test("device acknowledgement requires one atomic multi-location clear with the matching request ID", async () => {
  const requestId = await createPendingRequest();
  await assertFails(set(ref(deviceDb, `${zonePath}/control/manual_water`), false));
  await assertFails(set(ref(deviceDb, `${zonePath}/device/acknowledged_request_id`), requestId));
  await assertFails(
    update(ref(deviceDb, zonePath), {
      "control/manual_water": false,
      "device/acknowledged_request_id": "123e4567-e89b-42d3-a456-426614174999"
    })
  );
  await assertSucceeds(
    update(ref(deviceDb, zonePath), {
      "control/manual_water": false,
      "device/acknowledged_request_id": requestId
    })
  );
  const snapshot = await get(ref(deviceDb, zonePath));
  assert.equal(snapshot.child("control/manual_water").val(), false);
  assert.equal(snapshot.child("device/acknowledged_request_id").val(), requestId);
});

test("device cannot alter admin control fields or create manual commands", async () => {
  await assertFails(update(ref(deviceDb, `${zonePath}/control`), { mode: "manual" }));
  await assertFails(set(ref(deviceDb, `${zonePath}/control/manual_water`), true));
  await assertFails(set(ref(deviceDb, `${zonePath}/control/request_id`), "123e4567-e89b-42d3-a456-426614174005"));
});
