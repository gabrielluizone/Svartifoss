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
const deleteDoc = (_db, reference) => reference.delete();
const writeBatch = (db) => db.batch();

const PROJECT_ID = "demo-svartifoss";
const AUTHOR = "author-uid";
const OTHER_AUTHOR = "other-author-uid";
const MODERATOR = "moderator-uid";
const ANONYMOUS = "anonymous-uid";
const SECOND_ANONYMOUS = "second-anonymous-uid";
const SELF_MODERATOR = "self-moderator-uid";
const FIRST_ID = "5e5a77c0-0420-4d11-8e3a-7ae4fae8de34";
const SECOND_ID = "8c989d90-3648-489b-8da1-cc06e2f29179";
const THIRD_ID = "be925dc0-8db5-424c-9d28-276e47c13dd4";
const FOURTH_ID = "21c3df0d-e6d4-4f62-8ec4-f1ca1a57ed1e";
const FIFTH_ID = "54b23da9-98de-48ce-a158-4a52d2d5bb6e";

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
    quotaSchemaVersion: 2,
    submissionCount,
    lastSubmissionAt: serverTimestamp(),
    lastSubmissionId: id,
    recentSubmissionCount: 1,
    recentSubmissionFirstAt: serverTimestamp(),
  };
}

function legacyQuota(ownerUid, id, submissionCount = 1, lastSubmissionAt = serverTimestamp()) {
  return {
    ownerUid,
    submissionCount,
    lastSubmissionAt,
    lastSubmissionId: id,
  };
}

function threeSubmissionQuota(ownerUid, id, {
  submissionCount = 3,
  firstAt,
  secondAt,
  lastAt,
}) {
  return {
    ownerUid,
    quotaSchemaVersion: 2,
    submissionCount,
    lastSubmissionAt: lastAt,
    lastSubmissionId: id,
    recentSubmissionCount: 3,
    recentSubmissionFirstAt: firstAt,
    recentSubmissionSecondAt: secondAt,
  };
}

function nextQuota(ownerUid, id, previous) {
  if (previous === null || previous.quotaSchemaVersion !== 2) {
    return quota(ownerUid, id, (previous?.submissionCount ?? 0) + 1);
  }

  const next = {
    ownerUid,
    quotaSchemaVersion: 2,
    submissionCount: previous.submissionCount + 1,
    lastSubmissionAt: serverTimestamp(),
    lastSubmissionId: id,
  };
  if (previous.recentSubmissionCount === 1) {
    return {
      ...next,
      recentSubmissionCount: 2,
      recentSubmissionFirstAt: previous.recentSubmissionFirstAt,
    };
  }
  if (previous.recentSubmissionCount === 2) {
    return {
      ...next,
      recentSubmissionCount: 3,
      recentSubmissionFirstAt: previous.recentSubmissionFirstAt,
      recentSubmissionSecondAt: previous.lastSubmissionAt,
    };
  }
  return {
    ...next,
    recentSubmissionCount: 3,
    recentSubmissionFirstAt: previous.recentSubmissionSecondAt,
    recentSubmissionSecondAt: previous.lastSubmissionAt,
  };
}

function voter(db, themeId, uid) {
  return doc(db, "communityThemeLikes", themeId).collection("voters").doc(uid);
}

function like() {
  return {
    schemaVersion: 1,
    createdAt: serverTimestamp(),
  };
}

/**
 * Somebody who signed in with a real provider, which is what submitting a theme requires.
 *
 * The default mock token already carries `firebase.sign_in_provider: "custom"`, so it satisfies
 * identifiedUser() without being spelled out. Overriding the claim here is deliberately avoided:
 * see anonymousDb for what the emulator does with a hand-built `firebase` claim.
 */
function authenticatedDb(uid) {
  return testEnvironment.authenticatedContext(uid).firestore();
}

/** The silent account the app provisions for a heart tap: no provider, no linked identity. */
function anonymousDb(uid) {
  return testEnvironment
    .authenticatedContext(uid, {
      firebase: { sign_in_provider: "anonymous", identities: {} },
    })
    .firestore();
}

/**
 * An anonymous account that has since linked Google, which is what happens to somebody who likes
 * a theme before submitting one. sign_in_provider keeps naming the original anonymous sign-in.
 */
function linkedAnonymousDb(uid) {
  return testEnvironment
    .authenticatedContext(uid, {
      email: `${uid}@example.com`,
      email_verified: true,
      firebase: {
        sign_in_provider: "anonymous",
        identities: { "google.com": [`${uid}@example.com`] },
      },
    })
    .firestore();
}

async function submit(db, ownerUid, id) {
  const quotaDocument = doc(db, "communityThemeSubmissionQuota", ownerUid);
  const previousSnapshot = await getDoc(db, quotaDocument);
  const previousQuota = previousSnapshot.exists ? previousSnapshot.data() : null;
  const batch = writeBatch(db);
  batch.set(doc(db, "themeIntake", id), intake(ownerUid, id));
  batch.set(quotaDocument, nextQuota(ownerUid, id, previousQuota));
  return batch.commit();
}

/**
 * One moderator action: the intake transition and the review record that authors it, in the one
 * atomic batch the rules verify with getAfter. Passing the pair through a single helper is what
 * keeps every test honest about the contract the admin page has to follow.
 */
function moderate(db, uid, id, { from, to, fields = {} }) {
  const batch = writeBatch(db);
  batch.update(doc(db, "themeIntake", id), { status: to, reviewedAt: serverTimestamp(), ...fields });
  batch.set(doc(db, "themeIntakeReview", id), {
    reviewSchemaVersion: 1,
    reviewedBy: uid,
    reviewedAt: serverTimestamp(),
    decision: to,
    previousStatus: from,
  });
  return batch.commit();
}

async function seedStatus(ownerUid, id, status) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(db, doc(db, "themeIntake", id), { ...intake(ownerUid, id), status });
  });
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

async function seedPublished(id) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(db, doc(db, "themeIntake", id), {
      ...intake(AUTHOR, id),
      status: "published",
      createdAt: Timestamp.fromMillis(1_787_594_181_000),
    });
  });
}

async function seedPublishedMarker(id) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(db, doc(db, "communityThemePublished", id), {
      schemaVersion: 1,
      revision: 1,
      publishedAt: "2026-08-24T12:00:00Z",
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
  const existingQuota = (await getDoc(authorDb,
    doc(authorDb, "communityThemeSubmissionQuota", AUTHOR))).data();
  const next = nextQuota(AUTHOR, SECOND_ID, existingQuota);

  await assertFails(setDoc(authorDb, doc(authorDb, "communityThemeSubmissionQuota", AUTHOR),
    next));

  const batch = writeBatch(authorDb);
  batch.set(doc(authorDb, "themeIntake", SECOND_ID), intake(AUTHOR, SECOND_ID));
  batch.set(doc(authorDb, "themeIntake", THIRD_ID), intake(AUTHOR, THIRD_ID));
  batch.set(doc(authorDb, "communityThemeSubmissionQuota", AUTHOR), next);
  await assertFails(batch.commit());
});

test("the quota accepts three submissions in a rolling 24-hour period and rejects a fourth", async () => {
  const authorDb = authenticatedDb(AUTHOR);
  await assertSucceeds(submit(authorDb, AUTHOR, FIRST_ID));
  await assertSucceeds(submit(authorDb, AUTHOR, SECOND_ID));
  await assertSucceeds(submit(authorDb, AUTHOR, THIRD_ID));
  await assertFails(submit(authorDb, AUTHOR, FOURTH_ID));
});

test("the rolling history shifts once its third-newest submission is older than 24 hours", async () => {
  const authorDb = authenticatedDb(AUTHOR);
  const day = 24 * 60 * 60 * 1_000;
  const now = Date.now();
  const latest = Timestamp.fromMillis(now - 1_000);
  const middle = Timestamp.fromMillis(now - 2_000);
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(db, doc(db, "communityThemeSubmissionQuota", AUTHOR), threeSubmissionQuota(AUTHOR,
      THIRD_ID, {
        firstAt: Timestamp.fromMillis(now - day + 60_000),
        secondAt: middle,
        lastAt: latest,
      }));
  });
  await assertFails(submit(authorDb, AUTHOR, FOURTH_ID));

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(db, doc(db, "communityThemeSubmissionQuota", AUTHOR), threeSubmissionQuota(AUTHOR,
      THIRD_ID, {
        firstAt: Timestamp.fromMillis(Date.now() - day - 60_000),
        secondAt: middle,
        lastAt: latest,
      }));
  });
  await assertSucceeds(submit(authorDb, AUTHOR, FOURTH_ID));
  const shifted = (await getDoc(authorDb,
    doc(authorDb, "communityThemeSubmissionQuota", AUTHOR))).data();
  assert.equal(shifted.recentSubmissionCount, 3);
  assert.equal(shifted.recentSubmissionFirstAt.isEqual(middle), true);
  assert.equal(shifted.recentSubmissionSecondAt.isEqual(latest), true);
});

test("a legacy quota migrates on its next submission without waiting and cannot reset afterward", async () => {
  const authorDb = authenticatedDb(AUTHOR);
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(db, doc(db, "communityThemeSubmissionQuota", AUTHOR), legacyQuota(AUTHOR,
      FIRST_ID, 9, Timestamp.fromMillis(Date.now())));
  });

  await assertSucceeds(submit(authorDb, AUTHOR, SECOND_ID));
  const migrated = (await getDoc(authorDb,
    doc(authorDb, "communityThemeSubmissionQuota", AUTHOR))).data();
  assert.equal(migrated.quotaSchemaVersion, 2);
  assert.equal(migrated.submissionCount, 10);
  assert.equal(migrated.recentSubmissionCount, 1);
  assert.equal(migrated.recentSubmissionFirstAt.isEqual(migrated.lastSubmissionAt), true);

  await assertSucceeds(submit(authorDb, AUTHOR, THIRD_ID));
  await assertSucceeds(submit(authorDb, AUTHOR, FOURTH_ID));

  const resetAttempt = quota(AUTHOR, FIFTH_ID, 13);
  const batch = writeBatch(authorDb);
  batch.set(doc(authorDb, "themeIntake", FIFTH_ID), intake(AUTHOR, FIFTH_ID));
  batch.set(doc(authorDb, "communityThemeSubmissionQuota", AUTHOR), resetAttempt);
  await assertFails(batch.commit());
  await assertFails(submit(authorDb, AUTHOR, FIFTH_ID));
});

test("an existing intake ID cannot be paired with a quota update", async () => {
  const authorDb = authenticatedDb(AUTHOR);
  await assertSucceeds(submit(authorDb, AUTHOR, FIRST_ID));
  const existingQuota = (await getDoc(authorDb,
    doc(authorDb, "communityThemeSubmissionQuota", AUTHOR))).data();
  const batch = writeBatch(authorDb);
  batch.set(doc(authorDb, "themeIntake", FIRST_ID), intake(AUTHOR, FIRST_ID));
  batch.set(doc(authorDb, "communityThemeSubmissionQuota", AUTHOR),
    nextQuota(AUTHOR, FIRST_ID, existingQuota));
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

test("a moderator decides a third-party submission without touching what was submitted", async () => {
  const authorDb = authenticatedDb(AUTHOR);
  const moderatorDb = authenticatedDb(MODERATOR);
  await assertSucceeds(submit(authorDb, AUTHOR, FIRST_ID));
  await seedModerator(MODERATOR);

  await assertFails(moderate(moderatorDb, MODERATOR, FIRST_ID, {
    from: "pending",
    to: "approved",
    fields: { profileJson: "{\"changed\":true}" },
  }));
  // Publishing is the trusted publisher's word, never a client's.
  await assertFails(moderate(moderatorDb, MODERATOR, FIRST_ID, { from: "pending", to: "published" }));
  // A decision without the paired review record leaves no author for the verdict.
  await assertFails(updateDoc(moderatorDb, doc(moderatorDb, "themeIntake", FIRST_ID), {
    status: "approved",
    reviewedAt: serverTimestamp(),
  }));
  await assertSucceeds(moderate(moderatorDb, MODERATOR, FIRST_ID, { from: "pending", to: "approved" }));
});

test("the reviewer's identity is readable by moderators and never by the author", async () => {
  // This is what buys the author the right to read their own rejected and approved submissions:
  // rules grant access per document, so the verdict and its author cannot share one.
  const authorDb = authenticatedDb(AUTHOR);
  const moderatorDb = authenticatedDb(MODERATOR);
  await assertSucceeds(submit(authorDb, AUTHOR, FIRST_ID));
  await seedModerator(MODERATOR);
  await assertSucceeds(moderate(moderatorDb, MODERATOR, FIRST_ID, { from: "pending", to: "rejected" }));

  await assertSucceeds(getDoc(moderatorDb, doc(moderatorDb, "themeIntakeReview", FIRST_ID)));
  await assertFails(getDoc(authorDb, doc(authorDb, "themeIntakeReview", FIRST_ID)));
});

test("an author can list their own submissions in every status", async () => {
  const authorDb = authenticatedDb(AUTHOR);
  const otherDb = authenticatedDb(OTHER_AUTHOR);
  await seedStatus(AUTHOR, FIRST_ID, "pending");
  await seedStatus(AUTHOR, SECOND_ID, "rejected");
  await seedStatus(AUTHOR, THIRD_ID, "published");
  await seedStatus(OTHER_AUTHOR, FOURTH_ID, "pending");

  const own = await getDocs(authorDb,
    collection(authorDb, "themeIntake").where("ownerUid", "==", AUTHOR));
  assert.deepEqual(own.docs.map((entry) => entry.id).sort(), [FIRST_ID, SECOND_ID, THIRD_ID].sort());
  await assertSucceeds(getDoc(authorDb, doc(authorDb, "themeIntake", SECOND_ID)));

  // Somebody else's queue stays closed, listed or fetched by id.
  await assertFails(getDoc(otherDb, doc(otherDb, "themeIntake", FIRST_ID)));
  await assertFails(getDocs(otherDb,
    collection(otherDb, "themeIntake").where("ownerUid", "==", AUTHOR)));
  await assertFails(getDocs(authorDb, collection(authorDb, "themeIntake")));
});

test("a moderator can reopen a decision and withdraw a published theme", async () => {
  const moderatorDb = authenticatedDb(MODERATOR);
  await seedStatus(AUTHOR, FIRST_ID, "approved");
  await seedStatus(AUTHOR, SECOND_ID, "published");
  await seedModerator(MODERATOR);

  // Approved by mistake: back to the queue rather than on its way to the gallery.
  await assertSucceeds(moderate(moderatorDb, MODERATOR, FIRST_ID, { from: "approved", to: "pending" }));
  // Already public: only the publisher can remove the files, so this marks the intent.
  await assertSucceeds(moderate(moderatorDb, MODERATOR, SECOND_ID, { from: "published", to: "withdrawn" }));
  // A review record has to describe the transition it actually accompanied.
  await seedStatus(AUTHOR, THIRD_ID, "approved");
  await assertFails(moderate(moderatorDb, MODERATOR, THIRD_ID, { from: "pending", to: "pending" }));
});

test("a moderator corrects public text only before the theme is public", async () => {
  const moderatorDb = authenticatedDb(MODERATOR);
  await seedStatus(AUTHOR, FIRST_ID, "pending");
  await seedStatus(AUTHOR, SECOND_ID, "published");
  await seedModerator(MODERATOR);

  await assertSucceeds(moderate(moderatorDb, MODERATOR, FIRST_ID, {
    from: "pending",
    to: "pending",
    fields: { name: "A corrected name", author: "Corrected author" },
  }));
  // The published file is committed to Git under the old text; rewriting the record alone would
  // leave the two disagreeing with nothing to notice it.
  await assertFails(moderate(moderatorDb, MODERATOR, SECOND_ID, {
    from: "published",
    to: "published",
    fields: { name: "Too late" },
  }));
  await assertFails(moderate(moderatorDb, MODERATOR, FIRST_ID, {
    from: "pending",
    to: "pending",
    fields: { name: "" },
  }));
});

test("a moderator cannot decide or reopen their own intake, but can take it down", async () => {
  const selfDb = authenticatedDb(SELF_MODERATOR);
  await seedPending(SELF_MODERATOR, THIRD_ID);
  await seedModerator(SELF_MODERATOR);

  // The ban exists so nobody publishes themselves into the gallery.
  await assertFails(moderate(selfDb, SELF_MODERATOR, THIRD_ID, { from: "pending", to: "approved" }));
  // Taking a listing down is not that, and barring it would leave a bad entry with nobody able
  // to act on it.
  await assertSucceeds(moderate(selfDb, SELF_MODERATOR, THIRD_ID, { from: "pending", to: "withdrawn" }));
});

test("likes are one private immutable vote and can only target a published theme", async () => {
  const voterDb = authenticatedDb(OTHER_AUTHOR);
  const otherDb = authenticatedDb(MODERATOR);
  await seedPending(AUTHOR, FIRST_ID);

  await assertFails(setDoc(voterDb, voter(voterDb, FIRST_ID, OTHER_AUTHOR), like()));
  await seedPublished(FIRST_ID);

  await assertFails(setDoc(voterDb, voter(voterDb, FIRST_ID, OTHER_AUTHOR), {
    ...like(),
    untrustedCount: 999,
  }));
  await assertSucceeds(setDoc(voterDb, voter(voterDb, FIRST_ID, OTHER_AUTHOR), like()));
  await assertSucceeds(getDoc(voterDb, voter(voterDb, FIRST_ID, OTHER_AUTHOR)));
  await assertFails(getDoc(otherDb, voter(otherDb, FIRST_ID, OTHER_AUTHOR)));
  await assertFails(getDocs(voterDb,
    doc(voterDb, "communityThemeLikes", FIRST_ID).collection("voters")));
  await assertFails(updateDoc(voterDb, voter(voterDb, FIRST_ID, OTHER_AUTHOR), {
    createdAt: serverTimestamp(),
  }));

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(db, voter(db, FIRST_ID, MODERATOR), {
      schemaVersion: 1,
      createdAt: Timestamp.fromMillis(1_787_594_181_000),
    });
  });
  await assertFails(deleteDoc(voterDb, voter(voterDb, FIRST_ID, MODERATOR)));
  await assertSucceeds(deleteDoc(voterDb, voter(voterDb, FIRST_ID, OTHER_AUTHOR)));
});

test("a server-only catalogue marker enables likes for a static seed theme", async () => {
  const voterDb = authenticatedDb(OTHER_AUTHOR);
  const marker = doc(voterDb, "communityThemePublished", SECOND_ID);

  await assertFails(setDoc(voterDb, voter(voterDb, SECOND_ID, OTHER_AUTHOR), like()));
  await assertFails(getDoc(voterDb, marker));
  await assertFails(setDoc(voterDb, marker, {
    schemaVersion: 1,
    revision: 1,
    publishedAt: "2026-08-24T12:00:00Z",
  }));

  await seedPublishedMarker(SECOND_ID);
  await assertSucceeds(setDoc(voterDb, voter(voterDb, SECOND_ID, OTHER_AUTHOR), like()));
});

test("one account can like several separately published themes", async () => {
  const voterDb = authenticatedDb(OTHER_AUTHOR);
  await seedPublished(FIRST_ID);
  await seedPublished(SECOND_ID);
  await seedPublished(THIRD_ID);

  await assertSucceeds(setDoc(voterDb, voter(voterDb, FIRST_ID, OTHER_AUTHOR), like()));
  await assertSucceeds(setDoc(voterDb, voter(voterDb, SECOND_ID, OTHER_AUTHOR), like()));
  await assertSucceeds(setDoc(voterDb, voter(voterDb, THIRD_ID, OTHER_AUTHOR), like()));
});

test("an anonymous account can like, read and unlike its own vote", async () => {
  const anonDb = anonymousDb(ANONYMOUS);
  await seedPublished(FIRST_ID);

  await assertSucceeds(setDoc(anonDb, voter(anonDb, FIRST_ID, ANONYMOUS), like()));
  await assertSucceeds(getDoc(anonDb, voter(anonDb, FIRST_ID, ANONYMOUS)));
  await assertSucceeds(deleteDoc(anonDb, voter(anonDb, FIRST_ID, ANONYMOUS)));
});

test("an anonymous account still cannot vote for or read someone else", async () => {
  const anonDb = anonymousDb(ANONYMOUS);
  await seedPublished(FIRST_ID);

  await assertFails(setDoc(anonDb, voter(anonDb, FIRST_ID, SECOND_ANONYMOUS), like()));
  await assertFails(getDoc(anonDb, voter(anonDb, FIRST_ID, OTHER_AUTHOR)));
  await assertFails(getDocs(anonDb,
    doc(anonDb, "communityThemeLikes", FIRST_ID).collection("voters")));
});

test("an anonymous account cannot submit a theme or open a submission quota", async () => {
  // Reactions accept a disposable account; the per-account submission quota only means anything
  // when an account costs the submitter an identity, so every intake rule refuses this provider.
  const anonDb = anonymousDb(ANONYMOUS);

  await assertFails(setDoc(anonDb, doc(anonDb, "themeIntake", FIRST_ID), intake(ANONYMOUS, FIRST_ID)));
  await assertFails(setDoc(anonDb, doc(anonDb, "communityThemeSubmissionQuota", ANONYMOUS),
    quota(ANONYMOUS, FIRST_ID)));

  await seedPending(ANONYMOUS, SECOND_ID);
  await assertFails(getDoc(anonDb, doc(anonDb, "themeIntake", SECOND_ID)));
});

test("linking Google onto the anonymous like account restores submission access", async () => {
  // The upgraded account keeps reporting sign_in_provider "anonymous" until it signs in afresh,
  // so testing that claim alone would lock out exactly the people who liked a theme first.
  const linkedDb = linkedAnonymousDb(ANONYMOUS);
  await seedPending(ANONYMOUS, FIRST_ID);

  await assertSucceeds(getDoc(linkedDb, doc(linkedDb, "themeIntake", FIRST_ID)));
});

function deletionRequest(uid, themeDisposition = "keep", overrides = {}) {
  return {
    ownerUid: uid,
    requestSchemaVersion: 1,
    status: "pending",
    themeDisposition,
    clientVersion: "3.3",
    createdAt: serverTimestamp(),
    ...overrides,
  };
}

test("an account can request its own erasure with either choice about its themes", async () => {
  const authorDb = authenticatedDb(AUTHOR);
  const otherDb = authenticatedDb(OTHER_AUTHOR);

  await assertSucceeds(setDoc(authorDb,
    doc(authorDb, "communityThemeAccountDeletion", AUTHOR), deletionRequest(AUTHOR, "keep")));
  await assertSucceeds(getDoc(authorDb, doc(authorDb, "communityThemeAccountDeletion", AUTHOR)));
  await assertSucceeds(setDoc(otherDb,
    doc(otherDb, "communityThemeAccountDeletion", OTHER_AUTHOR),
    deletionRequest(OTHER_AUTHOR, "delete")));
});

test("an erasure request is one-way: it cannot be edited, withdrawn, or read by anyone else", async () => {
  // The publisher removes a public theme file in a Git commit and only then writes Firestore. A
  // request that could change in between would let those two halves disagree about what was asked.
  const authorDb = authenticatedDb(AUTHOR);
  const otherDb = authenticatedDb(OTHER_AUTHOR);
  await assertSucceeds(setDoc(authorDb,
    doc(authorDb, "communityThemeAccountDeletion", AUTHOR), deletionRequest(AUTHOR, "keep")));

  await assertFails(updateDoc(authorDb,
    doc(authorDb, "communityThemeAccountDeletion", AUTHOR), { themeDisposition: "delete" }));
  await assertFails(deleteDoc(authorDb, doc(authorDb, "communityThemeAccountDeletion", AUTHOR)));
  await assertFails(getDoc(otherDb, doc(otherDb, "communityThemeAccountDeletion", AUTHOR)));
  await assertFails(getDocs(authorDb, collection(authorDb, "communityThemeAccountDeletion")));
});

test("an erasure request cannot name another account or an unknown choice", async () => {
  const authorDb = authenticatedDb(AUTHOR);

  await assertFails(setDoc(authorDb,
    doc(authorDb, "communityThemeAccountDeletion", OTHER_AUTHOR), deletionRequest(OTHER_AUTHOR)));
  await assertFails(setDoc(authorDb,
    doc(authorDb, "communityThemeAccountDeletion", AUTHOR),
    deletionRequest(AUTHOR, "keep", { ownerUid: OTHER_AUTHOR })));
  await assertFails(setDoc(authorDb,
    doc(authorDb, "communityThemeAccountDeletion", AUTHOR), deletionRequest(AUTHOR, "everything")));
  await assertFails(setDoc(authorDb,
    doc(authorDb, "communityThemeAccountDeletion", AUTHOR),
    deletionRequest(AUTHOR, "keep", { status: "approved" })));
  await assertFails(setDoc(authorDb,
    doc(authorDb, "communityThemeAccountDeletion", AUTHOR),
    deletionRequest(AUTHOR, "keep", { removeEverything: true })));
});

test("an anonymous account may also ask for its own erasure", async () => {
  // Its only trace is a set of private like documents. The less identified somebody is, the less
  // standing there is to keep their data, so this is the one intake path anonymity does not gate.
  const anonDb = anonymousDb(ANONYMOUS);

  await assertSucceeds(setDoc(anonDb,
    doc(anonDb, "communityThemeAccountDeletion", ANONYMOUS), deletionRequest(ANONYMOUS, "delete")));
  await assertSucceeds(getDoc(anonDb, doc(anonDb, "communityThemeAccountDeletion", ANONYMOUS)));
});
