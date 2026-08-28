import { SETTING_KEYS, SETTING_TYPES } from "./theme-profile-schema.mjs";

const FIREBASE_VERSION = "12.17.1";
const INTAKE_COLLECTION = "themeIntake";
const REVIEW_COLLECTION = "themeIntakeReview";
const STATUSES = ["pending", "approved", "published", "rejected", "withdrawn"];
const STATUS_TITLES = {
  pending: "Pending review",
  approved: "Approved, awaiting publication",
  published: "Published in the gallery",
  rejected: "Rejected",
  withdrawn: "Withdrawn, awaiting removal",
  all: "Every submission"
};
const BASE64 = /^[A-Za-z0-9+/]+={0,2}$/;
const DIGEST = /^sha256:[0-9a-f]{64}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const CONTROL_OR_SURROGATE = /[\p{Cc}\p{Cs}]/u;
const MAX_PROFILE_JSON_BYTES = 24 * 1024;
const MAX_SETTING_TEXT_LENGTH = 128;
const MAX_PUBLIC_TEXT_LENGTH = 48;
const CANONICAL_HEADER = "svartifoss-community-theme-settings-v1";
const textEncoder = new TextEncoder();
const ALLOWED_BASE_FACES = new Set([
  "classic", "expressive", "poster", "studio", "material", "immersive",
  "carousel", "chat", "split", "note", "verse", "metadata"
]);

const elements = {
  user: document.querySelector("#user"),
  signIn: document.querySelector("#sign-in"),
  signOut: document.querySelector("#sign-out"),
  setup: document.querySelector("#setup"),
  status: document.querySelector("#status"),
  queue: document.querySelector("#queue"),
  queueTitle: document.querySelector("#queue-title"),
  count: document.querySelector("#count"),
  cards: document.querySelector("#cards"),
  filters: [...document.querySelectorAll(".filter")]
};

let firebase;
let signedInUser = null;
let selectedStatus = "pending";

boot().catch((error) => {
  console.error("Could not initialize community-theme review", error);
  showSetup("<strong>Could not load Firebase.</strong> Check <code>firebase-config.js</code> and the browser console.");
});

async function boot() {
  const { firebaseConfig } = await import("./firebase-config.js");
  if (!hasConfig(firebaseConfig)) {
    showSetup("<strong>Setup required.</strong> Replace the null value in <code>firebase-config.js</code> with the Firebase Web app configuration. The exact Console steps are in <code>docs/admin/README.md</code>.");
    return;
  }

  const [appApi, authApi, firestoreApi] = await Promise.all([
    import(`https://www.gstatic.com/firebasejs/${FIREBASE_VERSION}/firebase-app.js`),
    import(`https://www.gstatic.com/firebasejs/${FIREBASE_VERSION}/firebase-auth.js`),
    import(`https://www.gstatic.com/firebasejs/${FIREBASE_VERSION}/firebase-firestore.js`)
  ]);
  const app = appApi.initializeApp(firebaseConfig);
  firebase = {
    auth: authApi.getAuth(app),
    db: firestoreApi.getFirestore(app),
    GoogleAuthProvider: authApi.GoogleAuthProvider,
    signInWithPopup: authApi.signInWithPopup,
    signOut: authApi.signOut,
    onAuthStateChanged: authApi.onAuthStateChanged,
    collection: firestoreApi.collection,
    query: firestoreApi.query,
    where: firestoreApi.where,
    getDocs: firestoreApi.getDocs,
    doc: firestoreApi.doc,
    updateDoc: firestoreApi.updateDoc,
    writeBatch: firestoreApi.writeBatch,
    serverTimestamp: firestoreApi.serverTimestamp
  };

  elements.signIn.addEventListener("click", signIn);
  elements.signOut.addEventListener("click", signOut);
  elements.filters.forEach((filter) => filter.addEventListener("click", () => {
    selectedStatus = filter.dataset.status;
    elements.filters.forEach((item) => {
      const selected = item === filter;
      item.classList.toggle("is-selected", selected);
      item.setAttribute("aria-selected", String(selected));
    });
    loadSubmissions();
  }));
  firebase.onAuthStateChanged(firebase.auth, onAuthChanged);
}

function hasConfig(config) {
  return config && ["apiKey", "authDomain", "projectId", "appId"].every((key) =>
    typeof config[key] === "string" && config[key].trim() && !config[key].startsWith("REPLACE_"));
}

async function signIn() {
  setAuthLoading(true);
  try {
    await firebase.signInWithPopup(firebase.auth, new firebase.GoogleAuthProvider());
  } catch (error) {
    console.error("Moderator Google sign-in failed", error);
    showStatus("Google sign-in did not finish. Check that this GitHub Pages domain is authorized in Firebase Authentication.", true);
  } finally {
    setAuthLoading(false);
  }
}

async function signOut() {
  try {
    await firebase.signOut(firebase.auth);
  } catch (error) {
    console.error("Moderator sign-out failed", error);
    showStatus("Could not sign out. Try reloading the page.", true);
  }
}

function onAuthChanged(user) {
  signedInUser = user;
  elements.signIn.hidden = Boolean(user);
  elements.signOut.hidden = !user;
  elements.user.textContent = user ? `Signed in · UID: ${user.uid}` : "Not signed in";
  elements.queue.hidden = !user;
  if (user) {
    showStatus("Loading submissions…");
    loadSubmissions();
  } else {
    elements.cards.replaceChildren();
    elements.count.textContent = "";
    showStatus("Sign in with the Firebase account listed as a community-theme moderator.");
  }
}

/**
 * Loads one status at a time, or everything.
 *
 * "All" is several single-status queries rather than one unfiltered read: a moderator may read the
 * whole collection, but keeping the query shaped like the ones the rules describe means a rule
 * tightened later fails on one tab instead of blanking the page.
 */
async function loadSubmissions() {
  if (!signedInUser) return;
  try {
    const submissions = firebase.collection(firebase.db, INTAKE_COLLECTION);
    const wanted = selectedStatus === "all" ? STATUSES : [selectedStatus];
    const snapshots = await Promise.all(wanted.map((status) => firebase.getDocs(
      firebase.query(submissions, firebase.where("status", "==", status))
    )));
    if (signedInUser !== firebase.auth.currentUser) return;
    const rows = snapshots
      .flatMap((snapshot) => snapshot.docs.map((document) => ({ id: document.id, data: document.data() })))
      .sort((left, right) => createdMillis(left.data) - createdMillis(right.data));
    renderRows(rows);
    showStatus(rows.length
      ? "Review each theme visually before changing anything."
      : "Nothing here right now.");
  } catch (error) {
    console.error("Could not load moderation queue", error);
    elements.queue.hidden = true;
    showStatus("This account cannot read the review queue. Its Firebase Auth UID must have a document in communityThemeModerators.", true);
  }
}

function renderRows(rows) {
  elements.cards.replaceChildren();
  elements.queueTitle.textContent = STATUS_TITLES[selectedStatus] ?? "Submissions";
  elements.count.textContent = `${rows.length} ${selectedStatus === "all" ? "total" : selectedStatus}`;
  if (!rows.length) {
    const empty = document.createElement("p");
    empty.className = "panel empty";
    empty.textContent = "Nothing here right now.";
    elements.cards.append(empty);
    return;
  }
  rows.forEach((row) => elements.cards.append(buildCard(row)));
}

function buildCard({ id, data }) {
  const card = document.createElement("article");
  card.className = "card";
  const preview = previewElement(data.moderationPreviewWebpBase64);
  const body = document.createElement("div");
  body.className = "card-body";
  const title = textElement("h3", "card-title", stringOr(data.name, "Untitled theme"));
  const byline = textElement("p", "byline", `By ${stringOr(data.author, "Unknown author")} · ${stringOr(data.baseFace, "unknown")} layout`);
  const metadata = textElement("p", "meta", `ID: ${id}\nClient: ${stringOr(data.clientVersion, "unknown")}`);
  metadata.style.whiteSpace = "pre-line";
  const previewNotice = textElement(
    "p",
    "meta",
    "Preview supplied by the client. It is a visual aid, not proof of the submitted JSON."
  );
  const payloadStatus = textElement("p", "meta", "Checking submitted JSON and settings fingerprint…");
  const actions = document.createElement("div");
  actions.className = "actions";
  const ownSubmission = data.ownerUid === signedInUser?.uid;
  const status = stringOr(data.status, "pending");
  const statusTag = textElement("span", `status-tag ${status}`, status);
  let approve = null;
  if (status === "pending") {
    // Approve stays disabled until the payload check below has actually passed.
    approve = transitionButton("Approve", "approve", id, status, "approved", actions, true);
    actions.append(approve, transitionButton("Reject", "reject", id, status, "rejected", actions, ownSubmission));
  }
  if (status === "approved" || status === "rejected") {
    actions.append(transitionButton("Reopen for review", "secondary", id, status, "pending", actions, ownSubmission));
  }
  if (status !== "withdrawn") {
    // The one action available in every state, including on a moderator's own theme: a listing
    // nobody can take down is worse than one its author could also have removed.
    actions.append(transitionButton(
      status === "published" ? "Withdraw from gallery" : "Delete submission",
      "withdraw",
      id,
      status,
      "withdrawn",
      actions,
      false
    ));
  }
  body.append(statusTag, title, byline, metadata, previewNotice, payloadStatus, actions);
  const editor = metadataEditor(id, data, status);
  if (editor) body.append(editor);
  const submittedJson = profileJsonDetails(data.profileJson);
  if (submittedJson) body.append(submittedJson);
  if (ownSubmission && (status === "pending" || status === "approved" || status === "rejected")) {
    body.append(textElement(
      "p",
      "meta",
      "A different moderator must decide or reopen your own submission. You can still take it down."
    ));
  }
  card.append(preview, body);
  inspectSubmissionPayload(id, data)
    .then((inspection) => {
      payloadStatus.textContent = `Payload check passed · ${inspection.settingCount} typed settings · ${inspection.digest}`;
      // Only a pending card has an Approve button waiting on this check.
      if (approve !== null && !ownSubmission) approve.disabled = false;
    })
    .catch((error) => {
      console.warn(`Submission payload ${id} did not pass the browser check`, error);
      payloadStatus.textContent = `Payload check failed: ${error.message}. It cannot be approved from this page, but it may be rejected.`;
      payloadStatus.classList.add("error");
    });
  return card;
}

function previewElement(base64) {
  if (typeof base64 !== "string" || base64.length > 65536 || !BASE64.test(base64)) {
    const missing = document.createElement("div");
    missing.className = "preview missing";
    missing.textContent = "Invalid moderation preview";
    return missing;
  }
  const image = document.createElement("img");
  image.className = "preview";
  image.alt = "Submission moderation preview";
  image.src = `data:image/webp;base64,${base64}`;
  return image;
}

/**
 * Writes one moderator action as the two-document batch the rules require.
 *
 * The verdict and the identity behind it are separate documents on purpose -- that separation is
 * what lets an author read their own decided submissions without ever seeing who decided them --
 * and `getAfter` in the rules means neither write is accepted without the other.
 */
async function applyModeratorAction(id, previousStatus, nextStatus, fields = {}) {
  const batch = firebase.writeBatch(firebase.db);
  batch.update(firebase.doc(firebase.db, INTAKE_COLLECTION, id), {
    status: nextStatus,
    reviewedAt: firebase.serverTimestamp(),
    ...fields
  });
  batch.set(firebase.doc(firebase.db, REVIEW_COLLECTION, id), {
    reviewSchemaVersion: 1,
    reviewedBy: signedInUser.uid,
    reviewedAt: firebase.serverTimestamp(),
    decision: nextStatus,
    previousStatus
  });
  await batch.commit();
}

function transitionButton(label, kind, id, previousStatus, nextStatus, actions, disabled) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = `button ${kind}`;
  button.textContent = label;
  button.disabled = disabled;
  if (disabled) button.title = "A different moderator must review this submission.";
  button.addEventListener("click", async () => {
    if (!signedInUser) return;
    if (nextStatus === "withdrawn" && !confirm(withdrawalWarning(previousStatus))) return;
    const buttons = [...actions.querySelectorAll("button")];
    const previousDisabledStates = buttons.map((item) => item.disabled);
    buttons.forEach((item) => { item.disabled = true; });
    try {
      await applyModeratorAction(id, previousStatus, nextStatus);
      await loadSubmissions();
    } catch (error) {
      console.error(`Could not move ${id} to ${nextStatus}`, error);
      showStatus(`Could not move this submission to ${nextStatus}. A moderator cannot decide or reopen their own submission, and a published theme cannot be approved again.`, true);
      buttons.forEach((item, index) => { item.disabled = previousDisabledStates[index]; });
    }
  });
  return button;
}

function withdrawalWarning(previousStatus) {
  return previousStatus === "published"
    ? "Withdraw this theme from the public gallery?\n\nThe next publisher run removes its file, its catalogue entry and its likes, then deletes the submission record. People who already installed it keep their own copy. This cannot be undone from here."
    : "Delete this submission?\n\nThe next publisher run removes the record entirely. Reject it instead if the author should see a verdict.";
}

/**
 * Corrects the public name and author credit, which is the one part of a submission a moderator
 * may rewrite. Deliberately absent once a theme is public: its file is already committed to Git
 * under the old text, and changing the record alone would leave the two disagreeing silently.
 */
function metadataEditor(id, data, status) {
  if (status !== "pending" && status !== "approved") return null;
  const details = document.createElement("details");
  details.className = "submitted-json";
  const summary = document.createElement("summary");
  summary.textContent = "Correct public name or author";
  const name = document.createElement("input");
  name.className = "edit-field";
  name.maxLength = MAX_PUBLIC_TEXT_LENGTH;
  name.value = stringOr(data.name, "");
  name.setAttribute("aria-label", "Public theme name");
  const author = document.createElement("input");
  author.className = "edit-field";
  author.maxLength = MAX_PUBLIC_TEXT_LENGTH;
  author.value = stringOr(data.author, "");
  author.setAttribute("aria-label", "Public author credit");
  const save = document.createElement("button");
  save.type = "button";
  save.className = "button secondary";
  save.textContent = "Save text";
  save.addEventListener("click", async () => {
    const nextName = name.value.trim().replace(/\s+/gu, " ");
    const nextAuthor = author.value.trim().replace(/\s+/gu, " ");
    if (!nextName || !nextAuthor) {
      showStatus("A public name and an author credit are both required.", true);
      return;
    }
    save.disabled = true;
    try {
      // The status does not move; the review record still names who made the correction.
      await applyModeratorAction(id, status, status, { name: nextName, author: nextAuthor });
      await loadSubmissions();
    } catch (error) {
      console.error(`Could not correct the public text of ${id}`, error);
      showStatus("Could not save that text. A published theme can no longer be corrected here.", true);
      save.disabled = false;
    }
  });
  details.append(summary, name, author, save);
  return details;
}

function profileJsonDetails(rawJson) {
  if (typeof rawJson !== "string") return null;
  const details = document.createElement("details");
  details.className = "submitted-json";
  const summary = document.createElement("summary");
  summary.textContent = "Inspect submitted profile JSON";
  const contents = document.createElement("pre");
  contents.textContent = rawJson;
  details.append(summary, contents);
  return details;
}

/**
 * This is deliberately a browser-side review aid, not the publishing trust boundary. It catches
 * a broken/mismatched envelope before a moderator can approve it and recomputes the same canonical
 * SHA-256 input used by CommunityThemeSubmissionPolicy. The GitHub publisher revalidates the full
 * Android schema and does not trust this result or the supplied preview.
 */
async function inspectSubmissionPayload(documentId, data) {
  if (typeof data.profileJson !== "string" ||
      data.profileJson.length < 2 ||
      textEncoder.encode(data.profileJson).length > MAX_PROFILE_JSON_BYTES ||
      !isWellFormed(data.profileJson) || hasNonIntegerNumberLiteral(data.profileJson) ||
      typeof data.settingsDigest !== "string" || !DIGEST.test(data.settingsDigest)) {
    throw new Error("missing or malformed profile envelope");
  }
  let profile;
  try {
    profile = JSON.parse(data.profileJson);
  } catch (_error) {
    throw new Error("profile JSON is invalid");
  }
  if (!isPlainRecord(profile) ||
      !hasExactKeys(profile, [
        "schemaVersion", "id", "name", "baseFace", "createdAt", "updatedAt", "revision", "settings"
      ]) ||
      profile.schemaVersion !== 1 ||
      !UUID.test(profile.id) ||
      profile.id !== documentId ||
      profile.name !== data.name ||
      profile.baseFace !== data.baseFace ||
      !ALLOWED_BASE_FACES.has(profile.baseFace) ||
      profile.revision !== 1 ||
      !Number.isSafeInteger(profile.createdAt) || profile.createdAt < 0 ||
      !Number.isSafeInteger(profile.updatedAt) || profile.updatedAt < 0 ||
      profile.updatedAt < profile.createdAt ||
      !isPlainRecord(profile.settings)) {
    throw new Error("profile metadata does not match its queue record");
  }
  if (!isNormalizedPublicText(data.name) || !isNormalizedPublicText(data.author)) {
    throw new Error("public name or pseudonym is invalid");
  }
  const keys = Object.keys(profile.settings).sort();
  if (keys.length !== SETTING_KEYS.length ||
      !keys.every((key, index) => key === SETTING_KEYS[index])) {
    throw new Error("profile does not contain the complete current settings schema");
  }
  for (const key of keys) assertTypedSetting(key, profile.settings[key]);
  const digest = await canonicalSettingsDigest(profile.baseFace, profile.settings);
  if (digest !== data.settingsDigest) throw new Error("settings fingerprint does not match");
  return { digest, settingCount: keys.length };
}

function isPlainRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value) &&
    (Object.getPrototypeOf(value) === Object.prototype || Object.getPrototypeOf(value) === null);
}

function hasExactKeys(record, expected) {
  const actual = Object.keys(record).sort();
  return actual.length === expected.length && actual.every((key, index) => key === [...expected].sort()[index]);
}

function assertTypedSetting(key, setting) {
  const expectedType = SETTING_TYPES[key];
  if (typeof key !== "string" || !key || !isPlainRecord(setting) ||
      !expectedType || !hasExactKeys(setting, ["type", "value"]) ||
      setting.type !== expectedType) {
    throw new Error("a setting has an invalid shape");
  }
  if (setting.type === "string" && typeof setting.value === "string" &&
      isWellFormed(setting.value) && !CONTROL_OR_SURROGATE.test(setting.value) &&
      setting.value.length <= MAX_SETTING_TEXT_LENGTH) return;
  if (setting.type === "boolean" && typeof setting.value === "boolean") return;
  if (setting.type === "int" && Number.isInteger(setting.value) &&
      setting.value >= -2147483648 && setting.value <= 2147483647) return;
  throw new Error(`setting ${key} has an invalid typed value`);
}

function isNormalizedPublicText(value) {
  if (!isWellFormed(value) || CONTROL_OR_SURROGATE.test(value) ||
      !value || value.length > MAX_PUBLIC_TEXT_LENGTH) return false;
  return value === value.trim().replace(/\s+/gu, " ");
}

function isWellFormed(value) {
  return typeof value === "string" &&
    (typeof value.isWellFormed !== "function" || value.isWellFormed());
}

function hasNonIntegerNumberLiteral(source) {
  let insideString = false;
  let escaped = false;
  for (let index = 0; index < source.length; index += 1) {
    const character = source[index];
    if (insideString) {
      if (escaped) {
        escaped = false;
      } else if (character === "\\") {
        escaped = true;
      } else if (character === "\"") {
        insideString = false;
      }
      continue;
    }
    if (character === "\"") {
      insideString = true;
      continue;
    }
    if (character === "-" || (character >= "0" && character <= "9")) {
      let end = index + 1;
      while (end < source.length && /[0-9eE+.-]/.test(source[end])) end += 1;
      if (/[.eE]/.test(source.slice(index, end))) return true;
      index = end - 1;
    }
  }
  return false;
}

async function canonicalSettingsDigest(baseFace, settings) {
  const parts = [textEncoder.encode(CANONICAL_HEADER), new Uint8Array([0]), canonicalString(baseFace)];
  parts.push(writeInt(Object.keys(settings).length));
  Object.keys(settings).sort().forEach((key) => {
    const setting = settings[key];
    parts.push(canonicalString(key));
    if (setting.type === "string") {
      parts.push(new Uint8Array([1]), canonicalString(setting.value));
    } else if (setting.type === "boolean") {
      parts.push(new Uint8Array([2, setting.value ? 1 : 0]));
    } else {
      parts.push(new Uint8Array([3]), writeInt(setting.value));
    }
  });
  const bytes = concatBytes(parts);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return `sha256:${Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("")}`;
}

function canonicalString(value) {
  const bytes = textEncoder.encode(value);
  return concatBytes([writeInt(bytes.length), bytes]);
}

function writeInt(value) {
  const bytes = new Uint8Array(4);
  new DataView(bytes.buffer).setInt32(0, value, false);
  return bytes;
}

function concatBytes(parts) {
  const result = new Uint8Array(parts.reduce((total, part) => total + part.length, 0));
  let offset = 0;
  parts.forEach((part) => {
    result.set(part, offset);
    offset += part.length;
  });
  return result;
}

function createdMillis(data) {
  const timestamp = data?.createdAt;
  return timestamp && typeof timestamp.toMillis === "function" ? timestamp.toMillis() : Number.MAX_SAFE_INTEGER;
}

function stringOr(value, fallback) {
  return typeof value === "string" && value ? value : fallback;
}

function textElement(tag, className, value) {
  const element = document.createElement(tag);
  element.className = className;
  element.textContent = value;
  return element;
}

function setAuthLoading(loading) {
  elements.signIn.disabled = loading;
  elements.signIn.textContent = loading ? "Opening Google…" : "Sign in with Google";
}

function showSetup(html) {
  elements.setup.innerHTML = html;
  elements.setup.hidden = false;
  elements.status.hidden = true;
}

function showStatus(message, error = false) {
  elements.status.hidden = false;
  elements.status.classList.toggle("error", error);
  elements.status.textContent = message;
}
