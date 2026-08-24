import { SETTING_KEYS, SETTING_TYPES } from "./theme-profile-schema.mjs";

const FIREBASE_VERSION = "12.17.1";
const INTAKE_COLLECTION = "themeIntake";
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
  count: document.querySelector("#count"),
  cards: document.querySelector("#cards")
};

let firebase;
let signedInUser = null;

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
    serverTimestamp: firestoreApi.serverTimestamp
  };

  elements.signIn.addEventListener("click", signIn);
  elements.signOut.addEventListener("click", signOut);
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
    showStatus("Loading pending submissions…");
    loadPending();
  } else {
    elements.cards.replaceChildren();
    elements.count.textContent = "";
    showStatus("Sign in with the Firebase account listed as a community-theme moderator.");
  }
}

async function loadPending() {
  if (!signedInUser) return;
  try {
    const submissions = firebase.collection(firebase.db, INTAKE_COLLECTION);
    const snapshot = await firebase.getDocs(firebase.query(
      submissions,
      firebase.where("status", "==", "pending")
    ));
    if (signedInUser !== firebase.auth.currentUser) return;
    const rows = snapshot.docs.map((document) => ({ id: document.id, data: document.data() }))
      .sort((left, right) => createdMillis(left.data) - createdMillis(right.data));
    renderRows(rows);
    showStatus(rows.length ? "Review each theme visually before choosing a decision." : "There are no pending submissions.");
  } catch (error) {
    console.error("Could not load moderation queue", error);
    elements.queue.hidden = true;
    showStatus("This account cannot read the review queue. Its Firebase Auth UID must have a document in communityThemeModerators.", true);
  }
}

function renderRows(rows) {
  elements.cards.replaceChildren();
  elements.count.textContent = `${rows.length} pending`;
  if (!rows.length) {
    const empty = document.createElement("p");
    empty.className = "panel empty";
    empty.textContent = "Nothing is waiting for review.";
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
  const approve = decisionButton("Approve", "approve", id, "approved", actions, true);
  const reject = decisionButton("Reject", "reject", id, "rejected", actions, ownSubmission);
  actions.append(approve, reject);
  body.append(title, byline, metadata, previewNotice, payloadStatus, actions);
  const submittedJson = profileJsonDetails(data.profileJson);
  if (submittedJson) body.append(submittedJson);
  if (ownSubmission) {
    body.append(textElement(
      "p",
      "meta",
      "A different moderator must review your own submission."
    ));
  }
  card.append(preview, body);
  inspectSubmissionPayload(id, data)
    .then((inspection) => {
      payloadStatus.textContent = `Payload check passed · ${inspection.settingCount} typed settings · ${inspection.digest}`;
      if (!ownSubmission) approve.disabled = false;
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

function decisionButton(label, kind, id, status, actions, disabled) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = `button ${kind}`;
  button.textContent = label;
  button.disabled = disabled;
  if (disabled) button.title = "A different moderator must review this submission.";
  button.addEventListener("click", async () => {
    if (!signedInUser) return;
    const buttons = [...actions.querySelectorAll("button")];
    const previousDisabledStates = buttons.map((item) => item.disabled);
    buttons.forEach((item) => { item.disabled = true; });
    try {
      await firebase.updateDoc(firebase.doc(firebase.db, INTAKE_COLLECTION, id), {
        status,
        reviewedBy: signedInUser.uid,
        reviewedAt: firebase.serverTimestamp()
      });
      await loadPending();
    } catch (error) {
      console.error(`Could not ${status} ${id}`, error);
      showStatus(`Could not ${status} this submission. The rules only allow a configured moderator to decide a pending theme once.`, true);
      buttons.forEach((item, index) => { item.disabled = previousDisabledStates[index]; });
    }
  });
  return button;
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
