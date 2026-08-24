import assert from "node:assert/strict";
import { after, before, beforeEach, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import firebase from "firebase/compat/app";
import "firebase/compat/firestore";

// `@firebase/rules-unit-testing` currently returns the compat Firestore client. Keep the helpers
// below in that API family; mixing it with the modular `firebase/firestore` references produces a
// client-side type error before the emulator gets a chance to evaluate a rule.
const Timestamp = firebase.firestore.Timestamp;
const serverTimestamp = () => firebase.firestore.FieldValue.serverTimestamp();
const doc = (db, collectionName, id) => db.collection(collectionName).doc(id);
const collection = (db, collectionName) => db.collection(collectionName);
const getDoc = (_db, reference) => reference.get();
const getDocs = (_db, reference) => reference.get();
const setDoc = (_db, reference, value) => reference.set(value);
const updateDoc = (_db, reference, value) => reference.update(value);
const writeBatch = (db) => db.batch();

const PROJECT_ID = "demo-svartifoss";
const AUTHOR = "author-uid";
const OTHER_AUTHOR = "other-author-uid";
const MODERATOR = "moderator-uid";
const SELF_MODERATOR = "self-moderator-uid";
const FIRST_ID = "5e5a77c0-0420-4d11-8e3a-7ae4fae8de34";
const SECOND_ID = "8c989d90-3648-489b-8da1-cc06e2f29179";
const THIRD_ID = "be925dc0-8db5-424c-9d28-276e47c13dd4";

let testEnvironment;

function intake(ownerUid, id) {
  return {
    ownerUid,
    status: "pending",
    submissionSchemaVersion: 1,
    name: "A community theme",
    author: "Theme maker",
    baseFace: "poster",
    profileSchemaVersion: 1,
    revision: 1,
    profileJson: "{}",
    settingsDigest: "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    moderationPreviewWebpBase64: "AAAA",
    clientVersion: "3.3",
    createdAt: serverTimestamp(),
  };
}

function quota(ownerUid, id, submissionCount = 1) {
  return {
    ownerUid,
    submissionCount,
    lastSubmissionAt: serverTimestamp(),
    lastSubmissionId: id,
  };
}

function authenticatedDb(uid) {
  return testEnvironment.authenticatedContext(uid).firestore();
}

async function submit(db, ownerUid, id, submissionCount = 1) {
  const batch = writeBatch(db);
  batch.set(doc(db, "themeIntake", id), intake(ownerUid, id));
  batch.set(doc(db, "communityThemeSubmissionQuota", ownerUid),
    quota(ownerUid, id, submissionCount));
  return batch.commit();
}

async function seedModerator(uid) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(db, doc(db, "communityThemeModerators", uid), {
      createdAt: Timestamp.fromMillis(1_787_594_181_000),
    });
  });
}

async function seedPending(ownerUid, id) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(db, doc(db, "themeIntake", id), {
      ...intake(ownerUid, id),
      createdAt: Timestamp.fromMillis(1_787_594_181_000),
    });
  });
}

before(async () => {
  testEnvironment = await initializeTestEnvironment({ projectId: PROJECT_ID });
});

beforeEach(async () => {
  await testEnvironment.clearFirestore();
});

after(async () => {
  await testEnvironment.cleanup();
});

test("an intake and quota must be created together in one batch", async () => {
  const authorDb = authenticatedDb(AUTHOR);
  await assertFails(setDoc(authorDb, doc(authorDb, "themeIntake", FIRST_ID), intake(AUTHOR, FIRST_ID)));
  await assertFails(setDoc(authorDb, doc(authorDb, "communityThemeSubmissionQuota", AUTHOR),
    quota(AUTHOR, FIRST_ID)));
  await assertSucceeds(submit(authorDb, AUTHOR, FIRST_ID));
});

test("a quota cannot be advanced without one matching new intake", async () => {
  const authorDb = authenticatedDb(AUTHOR);
  await assertSucceeds(submit(authorDb, AUTHOR, FIRST_ID));

  await assertFails(setDoc(authorDb, doc(authorDb, "communityThemeSubmissionQuota", AUTHOR),
    quota(AUTHOR, SECOND_ID, 2)));

  const batch = writeBatch(authorDb);
  batch.set(doc(authorDb, "themeIntake", SECOND_ID), intake(AUTHOR, SECOND_ID));
  batch.set(doc(authorDb, "themeIntake", THIRD_ID), intake(AUTHOR, THIRD_ID));
  batch.set(doc(authorDb, "communityThemeSubmissionQuota", AUTHOR), quota(AUTHOR, SECOND_ID, 2));
  await assertFails(batch.commit());
});

test("the quota rejects a second submission before 24 hours and accepts one after it", async () => {
  const authorDb = authenticatedDb(AUTHOR);
  const now = Date.now();
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(db, doc(db, "communityThemeSubmissionQuota", AUTHOR), {
      ownerUid: AUTHOR,
      submissionCount: 1,
      lastSubmissionAt: Timestamp.fromMillis(now - (24 * 60 * 60 * 1000 - 1_000)),
      lastSubmissionId: FIRST_ID,
    });
  });
  await assertFails(submit(authorDb, AUTHOR, SECOND_ID, 2));

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(db, doc(db, "communityThemeSubmissionQuota", AUTHOR), {
      ownerUid: AUTHOR,
      submissionCount: 1,
      lastSubmissionAt: Timestamp.fromMillis(Date.now() - (24 * 60 * 60 * 1000 + 5_000)),
      lastSubmissionId: FIRST_ID,
    });
  });
  await assertSucceeds(submit(authorDb, AUTHOR, SECOND_ID, 2));
});

test("an existing intake ID cannot be paired with a quota update", async () => {
  const authorDb = authenticatedDb(AUTHOR);
  await assertSucceeds(submit(authorDb, AUTHOR, FIRST_ID));
  const batch = writeBatch(authorDb);
  batch.set(doc(authorDb, "themeIntake", FIRST_ID), intake(AUTHOR, FIRST_ID));
  batch.set(doc(authorDb, "communityThemeSubmissionQuota", AUTHOR), quota(AUTHOR, FIRST_ID, 2));
  await assertFails(batch.commit());
});

test("an author can read only their pending intake and own quota", async () => {
  const authorDb = authenticatedDb(AUTHOR);
  const otherDb = authenticatedDb(OTHER_AUTHOR);
  await assertSucceeds(submit(authorDb, AUTHOR, FIRST_ID));
  await assertSucceeds(getDoc(authorDb, doc(authorDb, "themeIntake", FIRST_ID)));
  await assertSucceeds(getDoc(authorDb, doc(authorDb, "communityThemeSubmissionQuota", AUTHOR)));
  await assertFails(getDocs(authorDb, collection(authorDb, "communityThemeSubmissionQuota")));
  await assertFails(getDoc(otherDb, doc(otherDb, "themeIntake", FIRST_ID)));
  await assertFails(getDoc(otherDb, doc(otherDb, "communityThemeSubmissionQuota", AUTHOR)));
});

test("a moderator can make one third-party decision without changing the submitted data", async () => {
  const authorDb = authenticatedDb(AUTHOR);
  const moderatorDb = authenticatedDb(MODERATOR);
  await assertSucceeds(submit(authorDb, AUTHOR, FIRST_ID));
  await seedModerator(MODERATOR);

  await assertFails(updateDoc(moderatorDb, doc(moderatorDb, "themeIntake", FIRST_ID), {
    status: "approved",
    reviewedBy: MODERATOR,
    reviewedAt: serverTimestamp(),
    profileJson: "{\"changed\":true}",
  }));
  await assertFails(updateDoc(moderatorDb, doc(moderatorDb, "themeIntake", FIRST_ID), {
    status: "published",
    reviewedBy: MODERATOR,
    reviewedAt: serverTimestamp(),
  }));
  await assertSucceeds(updateDoc(moderatorDb, doc(moderatorDb, "themeIntake", FIRST_ID), {
    status: "approved",
    reviewedBy: MODERATOR,
    reviewedAt: serverTimestamp(),
  }));
  await assertFails(getDoc(authorDb, doc(authorDb, "themeIntake", FIRST_ID)));
  await assertSucceeds(getDoc(moderatorDb, doc(moderatorDb, "themeIntake", FIRST_ID)));
  await assertFails(updateDoc(moderatorDb, doc(moderatorDb, "themeIntake", FIRST_ID), {
    status: "rejected",
    reviewedBy: MODERATOR,
    reviewedAt: serverTimestamp(),
  }));
});

test("a moderator cannot decide their own intake", async () => {
  const selfDb = authenticatedDb(SELF_MODERATOR);
  await seedPending(SELF_MODERATOR, THIRD_ID);
  await seedModerator(SELF_MODERATOR);
  await assertFails(updateDoc(selfDb, doc(selfDb, "themeIntake", THIRD_ID), {
    status: "approved",
    reviewedBy: SELF_MODERATOR,
    reviewedAt: serverTimestamp(),
  }));
});
