import { readFile } from "node:fs/promises";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getDatabase } from "firebase-admin/database";

const projectId = "demo-smart-garden";
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const databaseHost = process.env.FIREBASE_DATABASE_EMULATOR_HOST;

if (!authHost || !databaseHost) {
  throw new Error(
    "Refusing to seed: FIREBASE_AUTH_EMULATOR_HOST and FIREBASE_DATABASE_EMULATOR_HOST must both be set."
  );
}

const app = initializeApp({
  projectId,
  databaseURL: `http://${databaseHost}/?ns=${projectId}-default-rtdb`
});

const users = [
  {
    uid: "demo-admin",
    email: "admin@garden.test",
    password: "GardenDemo123!",
    displayName: "Admin Kebun"
  },
  {
    uid: "demo-esp32",
    email: "esp32@garden.test",
    password: "DeviceDemo123!",
    displayName: "ESP32 Zona A"
  }
];

try {
  const auth = getAuth(app);
  for (const user of users) {
    try {
      await auth.createUser(user);
    } catch (error) {
      if (error.code !== "auth/uid-already-exists" && error.code !== "auth/email-already-exists") {
        throw error;
      }
      await auth.updateUser(user.uid, user);
    }
  }

  const seed = JSON.parse(
    await readFile(new URL("../seed.json", import.meta.url), "utf8")
  );
  await getDatabase(app).ref().set(seed);
  console.log("Seeded local Auth and Realtime Database emulators.");
} finally {
  await deleteApp(app);
}
