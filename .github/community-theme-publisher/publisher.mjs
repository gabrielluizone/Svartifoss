import { createHash, randomUUID, timingSafeEqual } from "node:crypto";
import { link, lstat, mkdir, open, readFile, rename, unlink } from "node:fs/promises";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { cert, getApps, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import {
    ALLOWED_BASE_FACES,
    defaultSettingsForFace,
    MAX_PROFILE_JSON_BYTES,
    MAX_PUBLIC_TEXT_LENGTH,
    MAX_SETTING_TEXT_LENGTH,
    maxSettingTextLength,
    MINIMUM_APP_VERSION,
    PROFILE_REVISION,
    PROFILE_SCHEMA_VERSION,
    isOriginalityApplicableSetting,
    isSemanticallyValidSetting,
    SETTING_KEYS,
    SETTING_TYPES,
    SUBMISSION_SCHEMA_VERSION,
} from "./schema.mjs";

const REPOSITORY_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const INTAKE_COLLECTION = "themeIntake";
const LIKES_COLLECTION = "communityThemeLikes";
const LIKE_VOTERS_COLLECTION = "voters";
const PUBLISHED_THEMES_COLLECTION = "communityThemePublished";
const SUBMISSION_QUOTA_COLLECTION = "communityThemeSubmissionQuota";
const ACCOUNT_DELETION_COLLECTION = "communityThemeAccountDeletion";
const ACCOUNT_COLLECTION = "communityThemeAccounts";
const AUTHOR_NAMES_COLLECTION = "communityThemeAuthorNames";
const DELETED_ACCOUNTS_COLLECTION = "communityThemeDeletedAccounts";
const REVIEW_COLLECTION = "themeIntakeReview";
const SHOTS_COLLECTION = "themeIntakeShots";
const SHOT_SURFACES_SUBCOLLECTION = "surfaces";
/*
 * The published surface vocabulary, mirroring the literal list in firestore.rules. One value for
 * now: the Player is where a person spends nearly all of their time, and every other surface costs
 * a moderator another image to judge. The file layout keeps a surface segment anyway, so adding one
 * later is a new entry here rather than a rewrite of every published theme.
 */
const SHOT_SURFACES = ["player"];
const SHOTS_DIRECTORY = "shots";
const MAX_SHOT_BASE64_LENGTH = 128 * 1024;
const MAX_SHOT_BYTES = 96 * 1024;
const MIN_SHOT_PIXELS = 128;
const MAX_SHOT_PIXELS = 512;
const MAX_WITHDRAWALS_PER_RUN = 200;
const MAX_REVIEWER_MIGRATIONS_PER_RUN = 400;
const CATALOG_FILE = "index.json";
const MANIFEST_SCHEMA_VERSION = 2;
const ACCOUNT_DELETION_SCHEMA_VERSION = 1;
const ACCOUNT_SCHEMA_VERSION = 1;
const AUTHOR_NAME_SCHEMA_VERSION = 1;
const DELETED_ACCOUNT_SCHEMA_VERSION = 1;
const DELETED_ACCOUNT_TOMBSTONE_MS = 24 * 60 * 60 * 1000;
const MAX_ACCOUNT_DELETIONS_PER_RUN = 50;
const MAX_ERASED_THEMES_PER_ACCOUNT = 200;
const ERASURE_WRITE_BATCH_SIZE = 200;
/*
 * Firebase Auth generates a 28-character base62 UID and never a value containing a hyphen, so this
 * sentinel cannot collide with a real account. It replaces the owner of a theme whose author asked
 * to keep it public while deleting their account: the row stays, the link to a person does not.
 */
const ERASED_OWNER_UID = "account-erased";
const MAX_CATALOG_BYTES = 512 * 1024;
const MAX_MANIFEST_ENTRIES = 1_000;
const LIKE_COUNT_CONCURRENCY = 16;
/*
 * How stale a published like count is allowed to get before the catalogue is rewritten for its own
 * sake. Counts are re-read every run and ride along free whenever a theme is published or withdrawn;
 * this interval governs only the case where *nothing else* changed. Without it the daily cron
 * commits whenever any count moves, which turns a popularity number into a daily commit in the
 * application's own history. A person who just tapped Like still sees their own vote immediately --
 * the gallery applies it locally on top of the published figure.
 */
const LIKE_REFRESH_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000;
const PUBLISHED_MARKER_BATCH_SIZE = 400;
// OnlineThemesRepository permits a 128 KiB enriched Pages profile. The 24 KiB submission cap is
// intentionally narrower and applies only to the raw profileJson coming from Firestore.
const MAX_PUBLISHED_PROFILE_BYTES = 128 * 1024;
const MIN_INT = -2_147_483_648;
const MAX_INT = 2_147_483_647;
const MINIMUM_CHANGED_SETTINGS = 12;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const DIGEST = /^sha256:[0-9a-f]{64}$/;
const BASE64 = /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/;
const ISO_TIMESTAMP = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?Z$/;
const CONTROL_OR_SURROGATE = /[\p{Cc}\p{Cs}]/u;
const SUBMISSION_PROFILE_KEYS = [
    "schemaVersion",
    "id",
    "name",
    "baseFace",
    "createdAt",
    "updatedAt",
    "revision",
    "settings",
];
const PUBLISHED_PROFILE_KEYS = [
    "schemaVersion",
    "id",
    "name",
    "author",
    "baseFace",
    "createdAt",
    "updatedAt",
    "revision",
    "minimumAppVersion",
    "publishedAt",
    "screenshots",
    "settings",
];
/*
 * Absent means the author attached nothing, which is also every profile published before author
 * screenshots existed. An empty array is deliberately *not* a second way to say the same thing:
 * two spellings of "no image" would stop a republish comparing byte-for-byte against what is
 * already committed.
 */
const OPTIONAL_PUBLISHED_PROFILE_KEYS = ["screenshots"];
/*
 * Entries gained `likes` and then `settingsDigest` after the first catalogues were published, so
 * both are optional on read and always written. A catalogue missing either is upgraded on the next
 * run rather than waiting for the like-refresh interval: an absent field is not a stale value.
 */
const OPTIONAL_INDEX_ENTRY_KEYS = ["likes", "settingsDigest"];
const INDEX_ENTRY_KEYS = [
    "id",
    "name",
    "author",
    "baseFace",
    "revision",
    "schemaVersion",
    "minimumAppVersion",
    "publishedAt",
    "likes",
    "settingsDigest",
];
const INDEX_ROOT_KEYS = ["schemaVersion", "generatedAt", "themes"];

/** A deliberately data-free failure so logs never echo submitted names or JSON. */
export class ValidationError extends Error {
    constructor(code) {
        super(code);
        this.name = "ValidationError";
        this.code = code;
    }
}

function fail(code) {
    throw new ValidationError(code);
}

function hasOwn(record, key) {
    return Object.prototype.hasOwnProperty.call(record, key);
}

function isJsonRecord(value) {
    return typeof value === "object" &&
        value !== null &&
        !Array.isArray(value) &&
        Object.getPrototypeOf(value) === Object.prototype;
}

function isRecord(value) {
    return typeof value === "object" && value !== null && !Array.isArray(value);
}

function assertJsonRecord(value, code) {
    if (!isJsonRecord(value)) fail(code);
    return value;
}

function assertExactKeys(record, expected, code) {
    const actual = Object.keys(record).sort();
    const wanted = [...expected].sort();
    if (actual.length !== wanted.length || actual.some((key, index) => key !== wanted[index])) {
        fail(code);
    }
}

function isWellFormed(value) {
    return typeof value === "string" &&
        (typeof value.isWellFormed !== "function" || value.isWellFormed());
}

function normalizePublicText(value) {
    return value.trim().replace(/\s+/gu, " ");
}

function assertPublicText(value, code) {
    if (!isWellFormed(value) || CONTROL_OR_SURROGATE.test(value)) fail(code);
    const normalized = normalizePublicText(value);
    if (value !== normalized || normalized.length < 1 || normalized.length > MAX_PUBLIC_TEXT_LENGTH) {
        fail(code);
    }
    return normalized;
}

/**
 * The reservation document id is derived from the normalized display name. JavaScript's
 * `toLowerCase` is locale-independent, matching the Android client and Firestore rules. The
 * version prefix leaves room for a future canonicalization scheme without silently colliding with
 * names already reserved under this one.
 */
function canonicalAuthorKey(authorName) {
    return `v1:${authorName.toLowerCase()}`;
}

function validateAuthorAccount({ uid, data }) {
    const account = data;
    if (!isRecord(account)) fail("missing-author-account");
    assertExactKeys(
        account,
        ["ownerUid", "accountSchemaVersion", "authorName", "authorKey", "createdAt"],
        "invalid-author-account",
    );
    if (account.ownerUid !== uid) fail("author-account-owner-mismatch");
    assertExactInteger(account.accountSchemaVersion, ACCOUNT_SCHEMA_VERSION, "invalid-author-account-schema");
    const authorName = assertPublicText(account.authorName, "invalid-author-account-name");
    const authorKey = assertOpaqueText(account.authorKey, MAX_PUBLIC_TEXT_LENGTH + 3, "invalid-author-account-key");
    if (authorKey.includes("/") || authorKey !== canonicalAuthorKey(authorName)) {
        fail("invalid-author-account-key");
    }
    assertFirestoreTimestamp(account.createdAt, "invalid-author-account-created-at");
    return { authorName, authorKey };
}

function validateAuthorNameClaim({ uid, authorName, authorKey, data }) {
    const claim = data;
    if (!isRecord(claim)) fail("missing-author-name-claim");
    assertExactKeys(
        claim,
        ["ownerUid", "nameSchemaVersion", "authorName", "authorKey", "createdAt"],
        "invalid-author-name-claim",
    );
    if (claim.ownerUid !== uid) fail("author-name-owner-mismatch");
    assertExactInteger(claim.nameSchemaVersion, AUTHOR_NAME_SCHEMA_VERSION, "invalid-author-name-schema");
    if (claim.authorName !== authorName || claim.authorKey !== authorKey) {
        fail("author-name-claim-mismatch");
    }
    assertFirestoreTimestamp(claim.createdAt, "invalid-author-name-created-at");
}

/**
 * A named submission is publishable only while both immutable halves of its account identity
 * agree. Anonymous submissions deliberately carry no public-name reservation.
 */
async function validateAuthorIdentity({ firestore, transaction, uid, author }) {
    if (author === "Anonymous") return null;
    const accountReference = firestore.collection(ACCOUNT_COLLECTION).doc(uid);
    let accountSnapshot;
    try {
        accountSnapshot = transaction === undefined
            ? await accountReference.get()
            : await transaction.get(accountReference);
    } catch (_error) {
        throw new Error("Could not read the community author account");
    }
    if (!accountSnapshot.exists) fail("missing-author-account");
    const identity = validateAuthorAccount({ uid, data: accountSnapshot.data() });
    if (identity.authorName !== author) fail("submission-author-mismatch");

    const claimReference = firestore.collection(AUTHOR_NAMES_COLLECTION).doc(identity.authorKey);
    let claimSnapshot;
    try {
        claimSnapshot = transaction === undefined
            ? await claimReference.get()
            : await transaction.get(claimReference);
    } catch (_error) {
        throw new Error("Could not read the community author-name reservation");
    }
    if (!claimSnapshot.exists) fail("missing-author-name-claim");
    validateAuthorNameClaim({ uid, ...identity, data: claimSnapshot.data() });
    return identity;
}

function assertOpaqueText(value, maximumLength, code) {
    if (!isWellFormed(value) || CONTROL_OR_SURROGATE.test(value) ||
            value.trim().length < 1 || value.length > maximumLength) {
        fail(code);
    }
    return value;
}

function assertCanonicalUuid(value, code) {
    if (typeof value !== "string" || !UUID.test(value)) fail(code);
    return value;
}

function assertExactInteger(value, expected, code) {
    if (!Number.isSafeInteger(value) || value !== expected) fail(code);
    return value;
}

function assertPositiveInteger(value, code) {
    if (!Number.isSafeInteger(value) || value < 1) fail(code);
    return value;
}

function assertTimestampMillis(value, code) {
    if (!Number.isSafeInteger(value) || value < 0) fail(code);
    return value;
}

function assertPublicationTimestamp(value, code) {
    if (!isWellFormed(value) || !ISO_TIMESTAMP.test(value) || Number.isNaN(Date.parse(value))) {
        fail(code);
    }
    return value;
}

function assertAllowedBaseFace(value, code) {
    if (typeof value !== "string" || !ALLOWED_BASE_FACES.includes(value)) fail(code);
    return value;
}

function assertFirestoreTimestamp(value, code) {
    if (!isRecord(value) || typeof value.toMillis !== "function") fail(code);
    const millis = value.toMillis();
    if (!Number.isSafeInteger(millis) || millis < 0) fail(code);
    return value;
}

/**
 * JSON.parse turns `1.0` and `1e0` into the same JavaScript number as `1`. Android's strict
 * parser does not: it accepts an Integer only. Scan non-string number tokens so the publisher
 * never blesses a value whose Android type cannot be asserted from the original JSON body.
 */
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

function assertSettingString(value, key) {
    // Per key rather than one shared cap: the background stack is a sequence of enumerated values
    // and declares its own ceiling on its layer rule, while every other setting is a single token
    // well inside the shared 128. The semantic check below is what actually bounds either.
    if (!isWellFormed(value) || CONTROL_OR_SURROGATE.test(value) ||
            value.length > maxSettingTextLength(key)) {
        fail("invalid-setting-string");
    }
    return value;
}

function validateSettingsInternal(value, { requireComplete, allowLegacyReadOnly = false }) {
    const settings = assertJsonRecord(value, "invalid-settings");
    const actualKeys = Object.keys(settings).sort();
    if ((requireComplete && (actualKeys.length !== SETTING_KEYS.length ||
            actualKeys.some((key, index) => key !== SETTING_KEYS[index]))) ||
            (!requireComplete && actualKeys.some((key) => !hasOwn(SETTING_TYPES, key)))) {
        fail("unknown-or-missing-setting");
    }

    const normalized = {};
    const keys = requireComplete ? SETTING_KEYS : actualKeys;
    for (const key of keys) {
        const expectedType = SETTING_TYPES[key];
        const setting = assertJsonRecord(settings[key], "invalid-setting");
        assertExactKeys(setting, ["type", "value"], "invalid-setting-shape");
        if (setting.type !== expectedType) fail("setting-type-mismatch");

        let settingValue;
        switch (expectedType) {
            case "string":
                settingValue = assertSettingString(setting.value, key);
                break;
            case "boolean":
                if (typeof setting.value !== "boolean") fail("setting-type-mismatch");
                settingValue = setting.value;
                break;
            case "int":
                if (!Number.isSafeInteger(setting.value) ||
                        setting.value < MIN_INT || setting.value > MAX_INT) {
                    fail("setting-type-mismatch");
                }
                settingValue = setting.value;
                break;
            default:
                fail("unsupported-setting-type");
        }
        const normalizedSetting = { type: expectedType, value: settingValue };
        if (!isSemanticallyValidSetting(key, normalizedSetting, { allowLegacyReadOnly })) {
            fail("unsupported-setting-value");
        }
        normalized[key] = normalizedSetting;
    }
    return normalized;
}

/** Validates and canonicalizes the complete typed map sent by the Android draft factory. */
export function validateSettings(value) {
    return validateSettingsInternal(value, { requireComplete: true });
}

/** Legacy hand-authored Pages profiles may omit settings that resolve to a shipped face default. */
function validatePartialSettings(value) {
    return validateSettingsInternal(value, { requireComplete: false, allowLegacyReadOnly: true });
}

function validateSubmissionProfile(rawProfile, expectedId, expectedName, expectedBaseFace) {
    const profile = assertJsonRecord(rawProfile, "invalid-profile-root");
    assertExactKeys(profile, SUBMISSION_PROFILE_KEYS, "unexpected-profile-field");
    assertExactInteger(profile.schemaVersion, PROFILE_SCHEMA_VERSION, "unsupported-profile-schema");
    const id = assertCanonicalUuid(profile.id, "invalid-profile-id");
    if (id !== expectedId) fail("profile-id-does-not-match-document");
    const name = assertPublicText(profile.name, "invalid-profile-name");
    if (name !== expectedName) fail("profile-name-does-not-match-document");
    const baseFace = assertAllowedBaseFace(profile.baseFace, "invalid-profile-base-face");
    if (baseFace !== expectedBaseFace) fail("profile-base-face-does-not-match-document");
    const createdAt = assertTimestampMillis(profile.createdAt, "invalid-profile-created-at");
    const updatedAt = assertTimestampMillis(profile.updatedAt, "invalid-profile-updated-at");
    if (updatedAt < createdAt) fail("invalid-profile-timestamps");
    assertExactInteger(profile.revision, PROFILE_REVISION, "unsupported-profile-revision");

    return {
        schemaVersion: PROFILE_SCHEMA_VERSION,
        id,
        name,
        baseFace,
        createdAt,
        updatedAt,
        revision: PROFILE_REVISION,
        settings: validateSettings(profile.settings),
    };
}

function validatePublishedProfileInternal(rawProfile, { requireCompleteSettings }) {
    const profile = assertJsonRecord(rawProfile, "invalid-published-profile");
    const profileKeys = Object.keys(profile);
    if (profileKeys.some((key) => !PUBLISHED_PROFILE_KEYS.includes(key))) {
        fail("unexpected-published-profile-field");
    }
    for (const required of PUBLISHED_PROFILE_KEYS.filter(
            (key) => !OPTIONAL_PUBLISHED_PROFILE_KEYS.includes(key))) {
        if (!hasOwn(profile, required)) fail("missing-published-profile-field");
    }
    assertExactInteger(profile.schemaVersion, PROFILE_SCHEMA_VERSION, "unsupported-published-profile-schema");
    const id = assertCanonicalUuid(profile.id, "invalid-published-profile-id");
    const name = assertPublicText(profile.name, "invalid-published-profile-name");
    const author = assertPublicText(profile.author, "invalid-published-profile-author");
    const baseFace = assertAllowedBaseFace(profile.baseFace, "invalid-published-profile-base-face");
    const createdAt = assertTimestampMillis(profile.createdAt, "invalid-published-profile-created-at");
    const updatedAt = assertTimestampMillis(profile.updatedAt, "invalid-published-profile-updated-at");
    if (updatedAt < createdAt) fail("invalid-published-profile-timestamps");
    assertExactInteger(profile.revision, PROFILE_REVISION, "unsupported-published-profile-revision");
    if (profile.minimumAppVersion !== MINIMUM_APP_VERSION) fail("unexpected-minimum-app-version");
    const publishedAt = assertPublicationTimestamp(profile.publishedAt, "invalid-published-at");
    return {
        schemaVersion: PROFILE_SCHEMA_VERSION,
        id,
        name,
        author,
        baseFace,
        createdAt,
        updatedAt,
        revision: PROFILE_REVISION,
        minimumAppVersion: MINIMUM_APP_VERSION,
        publishedAt,
        // Positioned here rather than appended, because publicProfilesMatch compares serialized
        // JSON: the key order this builds has to be the one buildPublishedProfile writes.
        ...(hasOwn(profile, "screenshots")
            ? { screenshots: validateScreenshotSurfaces(profile.screenshots) }
            : {}),
        settings: requireCompleteSettings ? validateSettings(profile.settings) : validatePartialSettings(profile.settings),
    };
}

/** The caller-supplied form: an empty list is legal here and simply omits the field. */
function validateScreenshotSurfaceList(value) {
    if (!Array.isArray(value)) fail("invalid-screenshot-surfaces");
    return value.length === 0 ? [] : validateScreenshotSurfaces(value);
}

/** Surfaces are republished in registry order so a retry can compare against the committed file. */
function validateScreenshotSurfaces(value) {
    if (!Array.isArray(value) || value.length === 0 || value.length > SHOT_SURFACES.length) {
        fail("invalid-published-profile-screenshots");
    }
    const surfaces = [];
    for (const surface of value) {
        if (typeof surface !== "string" || !SHOT_SURFACES.includes(surface) ||
                surfaces.includes(surface)) {
            fail("invalid-published-profile-screenshots");
        }
        surfaces.push(surface);
    }
    return SHOT_SURFACES.filter((surface) => surfaces.includes(surface));
}

function validatePublishedProfile(rawProfile) {
    return validatePublishedProfileInternal(rawProfile, { requireCompleteSettings: true });
}

function validateLegacyPublishedProfile(rawProfile) {
    return validatePublishedProfileInternal(rawProfile, { requireCompleteSettings: false });
}

function writeInt(value) {
    const output = Buffer.allocUnsafe(4);
    output.writeInt32BE(value, 0);
    return output;
}

function canonicalString(value) {
    const bytes = Buffer.from(value, "utf8");
    return [writeInt(bytes.length), bytes];
}

/** Mirrors CommunityThemeSubmissionPolicy.canonicalBytes byte-for-byte. */
export function canonicalSettingsDigest(baseFace, settings) {
    const parts = [Buffer.from("svartifoss-community-theme-settings-v1", "utf8"), Buffer.from([0])];
    parts.push(...canonicalString(baseFace));
    const keys = Object.keys(settings).sort();
    parts.push(writeInt(keys.length));
    for (const key of keys) {
        const setting = settings[key];
        parts.push(...canonicalString(key));
        switch (setting.type) {
            case "string":
                parts.push(Buffer.from([1]), ...canonicalString(setting.value));
                break;
            case "boolean":
                parts.push(Buffer.from([2, setting.value ? 1 : 0]));
                break;
            case "int":
                parts.push(Buffer.from([3]), writeInt(setting.value));
                break;
            default:
                fail("unsupported-setting-type");
        }
    }
    return `sha256:${createHash("sha256").update(Buffer.concat(parts)).digest("hex")}`;
}

function digestMatches(actual, expected) {
    if (typeof actual !== "string" || typeof expected !== "string" ||
            actual.length !== expected.length) {
        return false;
    }
    return timingSafeEqual(Buffer.from(actual, "utf8"), Buffer.from(expected, "utf8"));
}

function countChangedSettings(settings, baseFace) {
    const defaults = defaultSettingsForFace(baseFace);
    return SETTING_KEYS.reduce((count, key) => {
        const value = settings[key];
        const fallback = defaults[key];
        const changed = value.type !== fallback.type || value.value !== fallback.value;
        return count + (changed && isOriginalityApplicableSetting(key, settings, baseFace) ? 1 : 0);
    }, 0);
}

function validatePreview(value) {
    if (typeof value !== "string" || value.length < 4 || value.length > 64 * 1024 ||
            !BASE64.test(value)) {
        fail("invalid-moderation-preview");
    }
}

/**
 * Walks the top-level chunks of a RIFF body, refusing anything whose declared sizes do not tile the
 * buffer exactly. Chunks are padded to an even length; a final odd chunk missing its pad byte is
 * the one deviation real encoders produce, so it is tolerated and nothing else is.
 */
function readRiffChunks(buffer, start, end, code) {
    const chunks = [];
    let offset = start;
    while (offset + 8 <= end) {
        const size = buffer.readUInt32LE(offset + 4);
        const payloadStart = offset + 8;
        if (size > end - payloadStart) fail(code);
        chunks.push({
            fourCC: buffer.toString("latin1", offset, offset + 4),
            start: payloadStart,
            end: payloadStart + size,
        });
        offset = payloadStart + size + (size % 2);
        if (offset === end + 1) offset = end;
    }
    if (offset !== end) fail(code);
    return chunks;
}

/** Width and height out of a VP8 key frame header (RFC 6386 section 9.1). */
function vp8KeyFrameDimensions(buffer, start, end, code) {
    if (end - start < 10) fail(code);
    const tag = buffer[start] | (buffer[start + 1] << 8) | (buffer[start + 2] << 16);
    if ((tag & 1) !== 0) fail(code);
    if (buffer[start + 3] !== 0x9d || buffer[start + 4] !== 0x01 || buffer[start + 5] !== 0x2a) {
        fail(code);
    }
    return {
        width: buffer.readUInt16LE(start + 6) & 0x3fff,
        height: buffer.readUInt16LE(start + 8) & 0x3fff,
    };
}

/**
 * Turns one submitted base64 string into bytes that are provably a plain, still, metadata-free
 * WebP -- the only thing this pipeline will ever commit to a public page.
 *
 * Parsing the container rather than sniffing a magic number is the entire point. `.webp` is a RIFF
 * container, and its extended form can carry an animation, an ICC profile, EXIF or XMP beside the
 * picture; EXIF in particular is how an image that passed through a phone gallery carries a
 * location. The app re-encodes through a Bitmap, which strips all of it. This is the boundary that
 * does not have to believe the app.
 *
 * Alpha is the one extension a screenshot may legitimately carry, so VP8X is accepted when its
 * flags claim nothing else *and* no chunk beyond ALPH/VP8 is actually present -- a flag byte is a
 * claim, and a claim is not what this function trusts. Lossless (VP8L) is refused because the app
 * never produces it: a screenshot arriving as one did not come from a released build.
 */
export function decodeThemeScreenshot(value) {
    if (typeof value !== "string" || value.length < 4 ||
            value.length > MAX_SHOT_BASE64_LENGTH || !BASE64.test(value)) {
        fail("invalid-screenshot-encoding");
    }
    const bytes = Buffer.from(value, "base64");
    /*
     * Each refusal names its own condition. They shared one code at first, which was fine until a
     * real submission was dropped in production: the log said "invalid-screenshot-image" and that
     * covered eight unrelated checks, so it identified a category rather than a cause and there was
     * nothing to act on. A boundary that refuses input has to say which rule the input broke.
     */
    if (bytes.length < 20) fail("screenshot-truncated");
    if (bytes.length > MAX_SHOT_BYTES) fail("screenshot-too-large");
    if (bytes.toString("latin1", 0, 4) !== "RIFF" || bytes.toString("latin1", 8, 12) !== "WEBP") {
        fail("screenshot-not-a-webp");
    }
    if (bytes.readUInt32LE(4) + 8 !== bytes.length) fail("screenshot-riff-length-mismatch");
    const chunks = readRiffChunks(bytes, 12, bytes.length, "screenshot-riff-chunks-unreadable");
    let frame;
    if (chunks.length === 1 && chunks[0].fourCC === "VP8 ") {
        frame = chunks[0];
    } else if (chunks.length > 1 && chunks[0].fourCC === "VP8X") {
        const header = chunks[0];
        if (header.end - header.start !== 10) fail("screenshot-bad-vp8x-header");
        // ICC (0x20) | EXIF (0x08) | XMP (0x04) | animation (0x02). Alpha (0x10) is allowed.
        if ((bytes[header.start] & 0x2e) !== 0) fail("screenshot-carries-metadata");
        for (const chunk of chunks.slice(1)) {
            if (chunk.fourCC !== "ALPH" && chunk.fourCC !== "VP8 ") {
                fail("screenshot-unexpected-chunk");
            }
        }
        frame = chunks.find((chunk) => chunk.fourCC === "VP8 ");
        if (frame === undefined) fail("screenshot-has-no-lossy-frame");
    } else {
        fail("screenshot-unsupported-container");
    }
    const { width, height } = vp8KeyFrameDimensions(
        bytes, frame.start, frame.end, "screenshot-bad-vp8-frame");
    // Square because the app centre-crops before encoding, and because the gallery draws the result
    // under a round mask: a non-square image did not come from that path.
    if (width !== height) fail("screenshot-not-square");
    if (width < MIN_SHOT_PIXELS || width > MAX_SHOT_PIXELS) fail("screenshot-wrong-size");
    return { bytes, width, height };
}

/**
 * A tolerant description of whatever arrived, for the log line beside a refusal.
 *
 * Deliberately separate from the validator and deliberately unable to fail: its whole job is to
 * describe input the validator has already refused, so throwing would remove the one piece of
 * evidence there is. Reports only shape -- byte length and chunk names -- never image content.
 */
export function describeThemeScreenshot(value) {
    if (typeof value !== "string") return `type=${typeof value}`;
    let bytes;
    try {
        bytes = Buffer.from(value, "base64");
    } catch (_error) {
        return `base64=${value.length} chars, undecodable`;
    }
    const parts = [`base64=${value.length}`, `bytes=${bytes.length}`];
    if (bytes.length >= 12) {
        parts.push(`magic=${JSON.stringify(bytes.toString("latin1", 0, 4))}` +
            `/${JSON.stringify(bytes.toString("latin1", 8, 12))}`);
        parts.push(`declared=${bytes.readUInt32LE(4) + 8}`);
    }
    const fourCCs = [];
    let offset = 12;
    while (offset + 8 <= bytes.length && fourCCs.length < 6) {
        const size = bytes.readUInt32LE(offset + 4);
        fourCCs.push(`${bytes.toString("latin1", offset, offset + 4)}:${size}`);
        const next = offset + 8 + size + (size % 2);
        if (next <= offset) break;
        offset = next;
    }
    if (fourCCs.length > 0) parts.push(`chunks=[${fourCCs.join(", ")}]`);
    return parts.join(" ");
}

function buildPublishedProfile(profile, author, publishedAt, screenshots = []) {
    return {
        schemaVersion: PROFILE_SCHEMA_VERSION,
        id: profile.id,
        name: profile.name,
        author,
        baseFace: profile.baseFace,
        createdAt: profile.createdAt,
        updatedAt: profile.updatedAt,
        revision: PROFILE_REVISION,
        minimumAppVersion: MINIMUM_APP_VERSION,
        publishedAt,
        ...(screenshots.length > 0 ? { screenshots: [...screenshots] } : {}),
        settings: profile.settings,
    };
}

function buildIndexEntry(profile, author, publishedAt, likes = 0, settingsDigest = undefined) {
    if (!Number.isSafeInteger(likes) || likes < 0) fail("invalid-index-likes");
    const entry = {
        id: profile.id,
        name: profile.name,
        author,
        baseFace: profile.baseFace,
        revision: PROFILE_REVISION,
        schemaVersion: PROFILE_SCHEMA_VERSION,
        minimumAppVersion: MINIMUM_APP_VERSION,
        publishedAt,
        likes,
    };
    if (settingsDigest !== undefined) {
        if (typeof settingsDigest !== "string" || !DIGEST.test(settingsDigest)) {
            fail("invalid-index-settings-digest");
        }
        entry.settingsDigest = settingsDigest;
    }
    return entry;
}

/**
 * Re-validates every app-controlled value before it can cross into a public Git commit. Preview
 * bytes are checked only as part of the queue envelope and deliberately never appear in the
 * returned profile or index entry.
 */
export function validateApprovedDocument({ id, data, publishedAt, screenshots = [] }) {
    const documentId = assertCanonicalUuid(id, "invalid-document-id");
    if (!isRecord(data)) fail("invalid-intake-document");
    if (data.status !== "approved") fail("document-is-not-approved");
    assertOpaqueText(data.ownerUid, 128, "invalid-owner-uid");
    const submissionSchemaVersion = data.submissionSchemaVersion;
    if (!Number.isSafeInteger(submissionSchemaVersion) ||
            (submissionSchemaVersion !== 1 && submissionSchemaVersion !== SUBMISSION_SCHEMA_VERSION)) {
        fail("unsupported-submission-schema");
    }
    const name = assertPublicText(data.name, "invalid-document-name");
    const author = assertPublicText(data.author, "invalid-document-author");
    const baseFace = assertAllowedBaseFace(data.baseFace, "invalid-document-base-face");
    assertExactInteger(data.profileSchemaVersion, PROFILE_SCHEMA_VERSION, "unsupported-document-profile-schema");
    assertExactInteger(data.revision, PROFILE_REVISION, "unsupported-document-revision");
    assertOpaqueText(data.clientVersion, 64, "invalid-client-version");
    assertFirestoreTimestamp(data.createdAt, "invalid-document-created-at");
    validatePreview(data.moderationPreviewWebpBase64);
    if (typeof data.profileJson !== "string" ||
            Buffer.byteLength(data.profileJson, "utf8") < 2 ||
            Buffer.byteLength(data.profileJson, "utf8") > MAX_PROFILE_JSON_BYTES ||
            !isWellFormed(data.profileJson)) {
        fail("invalid-profile-json");
    }
    if (hasNonIntegerNumberLiteral(data.profileJson)) fail("non-integer-json-number");

    let rawProfile;
    try {
        rawProfile = JSON.parse(data.profileJson);
    } catch (_error) {
        fail("invalid-profile-json");
    }
    const profile = validateSubmissionProfile(rawProfile, documentId, name, baseFace);
    const expectedDigest = canonicalSettingsDigest(profile.baseFace, profile.settings);
    if (typeof data.settingsDigest !== "string" || !DIGEST.test(data.settingsDigest) ||
            !digestMatches(data.settingsDigest, expectedDigest)) {
        fail("settings-digest-mismatch");
    }
    const changedSettings = countChangedSettings(profile.settings, profile.baseFace);
    if (changedSettings < MINIMUM_CHANGED_SETTINGS) fail("insufficient-originality");
    const canonicalPublishedAt = assertPublicationTimestamp(publishedAt, "invalid-publication-timestamp");
    const publicProfile = buildPublishedProfile(
        profile, author, canonicalPublishedAt, validateScreenshotSurfaceList(screenshots));
    if (Buffer.byteLength(jsonText(publicProfile), "utf8") > MAX_PUBLISHED_PROFILE_BYTES) {
        fail("published-profile-too-large");
    }
    return {
        id: documentId,
        submissionSchemaVersion,
        settingsDigest: expectedDigest,
        changedSettings,
        publicProfile,
        summary: buildIndexEntry(profile, author, canonicalPublishedAt, 0, expectedDigest),
    };
}

function validateIndexEntry(rawEntry) {
    const entry = assertJsonRecord(rawEntry, "invalid-index-entry");
    const keys = Object.keys(entry);
    if (keys.some((key) => !INDEX_ENTRY_KEYS.includes(key))) fail("unexpected-index-entry-field");
    for (const required of INDEX_ENTRY_KEYS.filter((key) => !OPTIONAL_INDEX_ENTRY_KEYS.includes(key))) {
        if (!hasOwn(entry, required)) fail("missing-index-entry-field");
    }
    const normalized = {
        id: assertCanonicalUuid(entry.id, "invalid-index-id"),
        name: assertPublicText(entry.name, "invalid-index-name"),
        author: assertPublicText(entry.author, "invalid-index-author"),
        baseFace: assertAllowedBaseFace(entry.baseFace, "invalid-index-base-face"),
        revision: assertPositiveInteger(entry.revision, "invalid-index-revision"),
        schemaVersion: assertExactInteger(entry.schemaVersion, PROFILE_SCHEMA_VERSION, "invalid-index-schema"),
        minimumAppVersion: assertOpaqueText(entry.minimumAppVersion, 32, "invalid-index-minimum-app-version"),
        publishedAt: assertPublicationTimestamp(entry.publishedAt, "invalid-index-published-at"),
    };
    if (hasOwn(entry, "likes")) {
        if (!Number.isSafeInteger(entry.likes) || entry.likes < 0) fail("invalid-index-likes");
        normalized.likes = entry.likes;
    }
    if (hasOwn(entry, "settingsDigest")) {
        if (typeof entry.settingsDigest !== "string" || !DIGEST.test(entry.settingsDigest)) {
            fail("invalid-index-settings-digest");
        }
        normalized.settingsDigest = entry.settingsDigest;
    }
    return normalized;
}

function sortIndexEntries(entries) {
    return [...entries].sort((left, right) => {
        if (left.publishedAt !== right.publishedAt) return left.publishedAt > right.publishedAt ? -1 : 1;
        if (left.id !== right.id) return left.id < right.id ? -1 : 1;
        return 0;
    });
}

function summariesMatch(existing, expected) {
    return existing.id === expected.id &&
        existing.name === expected.name &&
        existing.author === expected.author &&
        existing.baseFace === expected.baseFace &&
        existing.revision === expected.revision &&
        existing.schemaVersion === expected.schemaVersion &&
        existing.minimumAppVersion === expected.minimumAppVersion &&
        existing.publishedAt === expected.publishedAt;
}

/** Rest-destructuring, so the surviving keys keep the insertion order the comparison relies on. */
function withoutScreenshots(profile) {
    if (!hasOwn(profile, "screenshots")) return profile;
    const { screenshots: _screenshots, ...rest } = profile;
    return rest;
}

/**
 * Whether a committed file still corresponds to the approved intake it was published from.
 *
 * Screenshots are deliberately outside that question. They are not part of the intake: they come
 * from a separate immutable collection plus the moderator's verdict on them, and finalization
 * deletes those documents once the bytes are safely in Git. Comparing them here would turn a
 * perfectly good published theme into one that can never finalize and republishes on every run.
 */
function publicProfilesMatch(existing, expected) {
    try {
        const normalized = validatePublishedProfile(existing);
        return JSON.stringify(withoutScreenshots(normalized)) ===
            JSON.stringify(withoutScreenshots(expected));
    } catch (_error) {
        return false;
    }
}

async function assertRegularFile(path, code) {
    let stat;
    try {
        stat = await lstat(path);
    } catch (error) {
        if (error?.code === "ENOENT") return null;
        throw error;
    }
    if (!stat.isFile() || stat.isSymbolicLink()) fail(code);
    return stat;
}

function parseJsonText(raw, code) {
    try {
        return JSON.parse(raw);
    } catch (_error) {
        fail(code);
    }
}

async function readCatalog(root) {
    const themesDirectory = resolve(root, "docs", "themes");
    const indexPath = resolve(themesDirectory, CATALOG_FILE);
    if (dirname(indexPath) !== themesDirectory) fail("unsafe-index-path");
    const stat = await assertRegularFile(indexPath, "invalid-index-file");
    if (stat === null || stat.size > MAX_CATALOG_BYTES) fail("invalid-index-file");
    const raw = await readFile(indexPath, "utf8");
    const catalog = assertJsonRecord(parseJsonText(raw, "invalid-index-json"), "invalid-index-root");
    assertExactKeys(catalog, INDEX_ROOT_KEYS, "unexpected-index-root-field");
    assertExactInteger(catalog.schemaVersion, PROFILE_SCHEMA_VERSION, "unsupported-index-schema");
    assertPublicationTimestamp(catalog.generatedAt, "invalid-index-generated-at");
    if (!Array.isArray(catalog.themes) || catalog.themes.length > MAX_MANIFEST_ENTRIES * 10) {
        fail("invalid-index-themes");
    }
    const entries = catalog.themes.map(validateIndexEntry);
    const ids = new Set();
    for (const entry of entries) {
        if (ids.has(entry.id)) fail("duplicate-index-id");
        ids.add(entry.id);
    }
    return {
        themesDirectory,
        indexPath,
        raw,
        rawHash: createHash("sha256").update(raw, "utf8").digest("hex"),
        generatedAt: catalog.generatedAt,
        entries,
    };
}

function profilePath(themesDirectory, id) {
    const target = resolve(themesDirectory, `${id}.json`);
    if (dirname(target) !== themesDirectory) fail("unsafe-profile-path");
    return target;
}

/*
 * `<uuid>-<surface>.webp` under docs/themes/shots. Both components are already constrained -- a
 * canonical UUID and a value from SHOT_SURFACES -- so the containment checks mirror profilePath's
 * belt-and-braces rather than closing a reachable traversal.
 */
function shotPath(themesDirectory, id, surface) {
    const directory = resolve(themesDirectory, SHOTS_DIRECTORY);
    if (dirname(directory) !== themesDirectory) fail("unsafe-screenshot-path");
    const target = resolve(directory, `${id}-${surface}.webp`);
    if (dirname(target) !== directory) fail("unsafe-screenshot-path");
    return target;
}

async function readPublishedProfile(themesDirectory, id) {
    const target = profilePath(themesDirectory, id);
    const stat = await assertRegularFile(target, "invalid-existing-profile-file");
    if (stat === null) return null;
    if (stat.size > MAX_PUBLISHED_PROFILE_BYTES) fail("invalid-existing-profile-file");
    const raw = await readFile(target, "utf8");
    return {
        path: target,
        raw,
        profile: parseJsonText(raw, "invalid-existing-profile-json"),
    };
}

/** Materializes legacy partial Pages profiles over the same shipped face defaults as Android. */
async function collectPublishedDigestState(catalog) {
    const digests = new Map();
    const digestById = new Map();
    for (const entry of catalog.entries) {
        const stored = await readPublishedProfile(catalog.themesDirectory, entry.id);
        if (stored === null) fail("catalogue-profile-is-missing");
        let legacyProfile;
        try {
            legacyProfile = validateLegacyPublishedProfile(stored.profile);
        } catch (_error) {
            fail("invalid-catalogue-profile");
        }
        const profileSummary = buildIndexEntry(
            legacyProfile,
            legacyProfile.author,
            legacyProfile.publishedAt,
        );
        if (!summariesMatch(entry, profileSummary)) fail("catalogue-profile-index-mismatch");

        const materializedSettings = {
            ...defaultSettingsForFace(legacyProfile.baseFace),
            ...legacyProfile.settings,
        };
        const digest = canonicalSettingsDigest(legacyProfile.baseFace, materializedSettings);
        const ids = digests.get(digest) ?? new Set();
        ids.add(legacyProfile.id);
        digests.set(digest, ids);
        digestById.set(legacyProfile.id, digest);
    }
    return { digests, digestById };
}

/**
 * Counts the one-vote-per-UID documents protected by Firestore rules. The public catalogue never
 * trusts a counter supplied by an Android client: it receives only this aggregate computed by the
 * service account while publishing. Firestore's aggregate query returns the count without
 * downloading each voter document, so a popular theme cannot make this workflow's memory usage
 * grow with its likes.
 */
async function countAuthoritativeLikes(firestore, themeId) {
    try {
        const voters = firestore.collection(LIKES_COLLECTION).doc(themeId).collection(LIKE_VOTERS_COLLECTION);
        const aggregate = await voters.count().get();
        const count = aggregate.data()?.count;
        if (!Number.isSafeInteger(count) || count < 0) fail("invalid-authoritative-like-count");
        return count;
    } catch (error) {
        if (error instanceof ValidationError) throw error;
        throw new Error("Could not count authoritative community-theme likes");
    }
}

/**
 * Whether a catalogue whose only pending change is its like counts has waited long enough.
 *
 * The catalogue's own `generatedAt` is the clock, so there is no extra state to keep in step: it
 * advances exactly when the file is rewritten, which is the moment the counts were last correct.
 * An unreadable or future-dated timestamp refreshes immediately rather than waiting out a window
 * it cannot measure.
 */
export function isLikeRefreshDue(generatedAt, now, intervalMs = LIKE_REFRESH_INTERVAL_MS) {
    const previous = Date.parse(generatedAt);
    const current = Date.parse(now);
    if (!Number.isFinite(previous) || !Number.isFinite(current)) return true;
    if (current < previous) return true;
    return current - previous >= intervalMs;
}

/** Limits concurrent aggregate queries instead of bursting once per catalogue entry at Firestore. */
async function collectAuthoritativeLikeCounts(firestore, themeIds) {
    const ids = [...new Set(themeIds)].sort();
    const counts = new Map();
    let next = 0;
    const worker = async () => {
        while (next < ids.length) {
            const index = next;
            next += 1;
            const id = ids[index];
            counts.set(id, await countAuthoritativeLikes(firestore, id));
        }
    };
    await Promise.all(Array.from({ length: Math.min(LIKE_COUNT_CONCURRENCY, ids.length) }, worker));
    return counts;
}

/**
 * Re-validates one account-erasure request. Firestore rules already constrain its shape, so a
 * document that fails here was written with administrative credentials; it is skipped rather than
 * guessed at, because erasure is the one operation in this pipeline that cannot be undone.
 */
export function validateAccountDeletionRequest({ id, data }) {
    const uid = assertOpaqueText(id, 128, "invalid-account-deletion-id");
    if (!isRecord(data)) fail("invalid-account-deletion-request");
    if (data.status !== "pending") fail("account-deletion-is-not-pending");
    assertExactInteger(
        data.requestSchemaVersion,
        ACCOUNT_DELETION_SCHEMA_VERSION,
        "unsupported-account-deletion-schema",
    );
    assertOpaqueText(data.ownerUid, 128, "invalid-account-deletion-owner");
    if (data.ownerUid !== uid) fail("account-deletion-owner-mismatch");
    if (data.themeDisposition !== "keep" && data.themeDisposition !== "delete") {
        fail("invalid-theme-disposition");
    }
    assertOpaqueText(data.clientVersion, 64, "invalid-client-version");
    assertFirestoreTimestamp(data.createdAt, "invalid-account-deletion-created-at");
    return { uid, themeDisposition: data.themeDisposition };
}

/**
 * Turns pending erasure requests into a plan the publish phase can act on with files alone.
 *
 * Which themes are *public* is decided by the static catalogue, never by an intake status: the
 * files under docs/themes are the thing being withdrawn, and an intake document that disagrees
 * with them is exactly the state this must survive. A submission that never became public is
 * removed under either disposition -- "keep my themes" can only mean the ones people can see.
 */
async function buildAccountErasurePlan({ firestore, catalog, logger }) {
    let snapshot;
    try {
        snapshot = await firestore
            .collection(ACCOUNT_DELETION_COLLECTION)
            .where("status", "==", "pending")
            .get();
    } catch (_error) {
        throw new Error("Could not read pending community-account erasure requests");
    }
    const documents = [...snapshot.docs].sort((left, right) =>
        String(left.id) < String(right.id) ? -1 : String(left.id) > String(right.id) ? 1 : 0);
    const catalogIds = new Set(catalog.entries.map((entry) => entry.id));
    const requests = [];
    let skipped = 0;

    for (const document of documents.slice(0, MAX_ACCOUNT_DELETIONS_PER_RUN)) {
        let request;
        try {
            request = validateAccountDeletionRequest({ id: document.id, data: document.data() });
        } catch (error) {
            skipped += 1;
            log(
                logger,
                "warn",
                `Skipped an erasure request: ${error instanceof ValidationError ? error.code : "invalid-account-deletion-request"}`,
            );
            continue;
        }

        let ownedSnapshot;
        try {
            ownedSnapshot = await firestore
                .collection(INTAKE_COLLECTION)
                .where("ownerUid", "==", request.uid)
                .get();
        } catch (_error) {
            throw new Error("Could not read the submissions owned by an erasing account");
        }
        const intakeIds = [...ownedSnapshot.docs]
            .map((owned) => String(owned.id))
            .filter((id) => UUID.test(id))
            .sort();
        if (intakeIds.length > MAX_ERASED_THEMES_PER_ACCOUNT) {
            // Far past what the three-per-day quota can produce, so this is a corrupt or hostile
            // state rather than a busy author. It is skipped and logged loudly instead of thrown:
            // one such account must not stop everybody else's themes from being published.
            skipped += 1;
            log(logger, "error", "Skipped an erasure request: account-erasure-too-large");
            continue;
        }
        const publicIds = intakeIds.filter((id) => catalogIds.has(id));
        requests.push({
            uid: request.uid,
            themeDisposition: request.themeDisposition,
            intakeIds,
            // Withdrawn from Pages by the publish phase; retained under the published pseudonym
            // with its owner scrubbed by finalization.
            removedThemeIds: request.themeDisposition === "delete" ? publicIds : [],
            keptThemeIds: request.themeDisposition === "keep" ? publicIds : [],
        });
    }

    const deferred = documents.length - Math.min(documents.length, MAX_ACCOUNT_DELETIONS_PER_RUN);
    if (documents.length > 0) {
        log(
            logger,
            "log",
            `Pending account erasures: ${documents.length}; planned: ${requests.length}; ` +
                `deferred: ${deferred}; skipped: ${skipped}.`,
        );
    }
    return { requests, deferred, skipped };
}

/**
 * The submissions a moderator has marked for removal.
 *
 * A withdrawal is the only way a theme leaves the public catalogue by decision rather than by its
 * author deleting their account, and it is deliberately a status rather than a client delete: the
 * files live in Git, so the removal has to happen in the commit phase and the Firestore cleanup
 * after it. Which ids are public is read from the catalogue, never from the intake status, for the
 * same reason an erasure does it that way -- the files are the thing being withdrawn.
 */
/**
 * Resolves the author screenshot for one approved theme.
 *
 * Every failure drops the image and keeps the theme. A moderator approved a theme; an unreadable,
 * mismatched or unexpected picture is not a reason to withhold it, and the fallback -- a gallery
 * card that renders the profile locally -- is exactly what a theme with no screenshot shows. The
 * bytes cannot have changed since review, because the rules make a screenshot immutable, so what
 * gets committed is what the moderator actually saw.
 */
async function collectApprovedScreenshots({ firestore, themeId, ownerUid, logger }) {
    if (typeof ownerUid !== "string" || ownerUid.length === 0) return [];
    const review = await firestore.collection(REVIEW_COLLECTION).doc(themeId).get();
    // Absent means accepted, so every record written before screenshots existed keeps its meaning.
    if (review.exists && review.data()?.shotsAccepted === false) {
        log(logger, "log", `Dropped the screenshot of ${themeId}: the moderator did not accept it.`);
        return [];
    }
    const snapshot = await firestore
        .collection(SHOTS_COLLECTION)
        .doc(themeId)
        .collection(SHOT_SURFACES_SUBCOLLECTION)
        .get();
    const resolved = [];
    for (const surface of SHOT_SURFACES) {
        const stored = snapshot.docs.find((document) => document.id === surface);
        if (stored === undefined) continue;
        const data = stored.data();
        // Defence in depth behind the rules: a screenshot is filed against an intake the caller
        // owns, so an owner that disagrees means one of the two documents is not what it claims.
        if (!isRecord(data) || data.ownerUid !== ownerUid || data.surface !== surface) {
            log(logger, "warn", `Dropped the ${surface} screenshot of ${themeId}: owner-mismatch.`);
            continue;
        }
        try {
            resolved.push({ surface, image: decodeThemeScreenshot(data.webpBase64) });
        } catch (error) {
            const code = error instanceof ValidationError ? error.code : "invalid-screenshot";
            log(
                logger,
                "warn",
                `Dropped the ${surface} screenshot of ${themeId}: ${code}. ` +
                    describeThemeScreenshot(data.webpBase64));
        }
    }
    return resolved;
}

/**
 * Removes the stored screenshots of one theme. Called once the bytes are committed to Git, and
 * again wherever a theme or an account is being erased -- a 96 KB document per submission is worth
 * keeping only until it is either public or gone.
 */
async function deleteThemeScreenshots(firestore, themeId) {
    const snapshot = await firestore
        .collection(SHOTS_COLLECTION)
        .doc(themeId)
        .collection(SHOT_SURFACES_SUBCOLLECTION)
        .get();
    if (snapshot.empty) return;
    await commitInBatches(
        firestore,
        snapshot.docs.map((document) => (batch) => batch.delete(document.ref)),
        "Could not remove the screenshots of a community theme",
    );
}

async function collectWithdrawnThemes({ firestore, catalog, logger }) {
    let snapshot;
    try {
        snapshot = await firestore
            .collection(INTAKE_COLLECTION)
            .where("status", "==", "withdrawn")
            .get();
    } catch (_error) {
        throw new Error("Could not read withdrawn community themes");
    }
    const catalogIds = new Set(catalog.entries.map((entry) => entry.id));
    const ids = [...snapshot.docs]
        .map((document) => String(document.id))
        .filter((id) => UUID.test(id))
        .sort()
        .slice(0, MAX_WITHDRAWALS_PER_RUN);
    if (ids.length > 0) {
        log(logger, "log", `Withdrawn community themes: ${ids.length}.`);
    }
    return { ids, publicIds: ids.filter((id) => catalogIds.has(id)) };
}

/**
 * Carries out the withdrawals whose files the commit phase already removed.
 *
 * Idempotent throughout, like an erasure: a run that fails part way is resumed by the next one.
 */
async function applyWithdrawals({ firestore, withdrawals, logger }) {
    for (const id of withdrawals.ids) {
        // Every vote on a theme that is no longer public, not merely the ones this run counted.
        const voters = await collectVoterReferences(firestore, id);
        await commitInBatches(
            firestore,
            voters.map((reference) => (batch) => batch.delete(reference)),
            "Could not erase the votes of a withdrawn community theme",
        );
        await commitInBatches(
            firestore,
            [
                (batch) => batch.delete(firestore.collection(PUBLISHED_THEMES_COLLECTION).doc(id)),
                (batch) => batch.delete(firestore.collection(REVIEW_COLLECTION).doc(id)),
                (batch) => batch.delete(firestore.collection(INTAKE_COLLECTION).doc(id)),
            ],
            "Could not remove a withdrawn community theme",
        );
        await deleteThemeScreenshots(firestore, id);
    }
    if (withdrawals.ids.length > 0) {
        log(logger, "log", `Removed ${withdrawals.ids.length} withdrawn community theme(s).`);
    }
    return withdrawals.ids.length;
}

/**
 * Moves a reviewer identity off the submission it decided.
 *
 * `reviewedBy` used to live on the intake document, which is why an author could only ever read
 * their own *pending* submission: Firestore grants access per document, so the verdict and the
 * name of whoever reached it could not be separated by a rule. They are separate documents now,
 * and this carries the already-decided ones across. It is self-terminating -- once no document
 * carries the field the query returns nothing and costs one empty read per run.
 */
async function migrateLegacyReviewers({ firestore, logger }) {
    let snapshot;
    try {
        snapshot = await firestore
            .collection(INTAKE_COLLECTION)
            .where("reviewedBy", "!=", null)
            .limit(MAX_REVIEWER_MIGRATIONS_PER_RUN)
            .get();
    } catch (_error) {
        throw new Error("Could not read community themes with a legacy reviewer field");
    }
    const operations = [];
    for (const document of snapshot.docs) {
        const id = String(document.id);
        if (!UUID.test(id)) continue;
        const data = document.data();
        const reviewedBy = typeof data?.reviewedBy === "string" ? data.reviewedBy : null;
        if (reviewedBy === null) continue;
        const status = typeof data?.status === "string" ? data.status : "pending";
        operations.push((batch) => batch.set(firestore.collection(REVIEW_COLLECTION).doc(id), {
            reviewSchemaVersion: 1,
            reviewedBy,
            reviewedAt: data?.reviewedAt ?? FieldValue.serverTimestamp(),
            decision: status === "published" ? "approved" : status,
            previousStatus: "pending",
        }));
        operations.push((batch) => batch.update(firestore.collection(INTAKE_COLLECTION).doc(id), {
            reviewedBy: FieldValue.delete(),
        }));
    }
    if (operations.length === 0) return 0;
    await commitInBatches(firestore, operations, "Could not move a legacy reviewer identity");
    const migrated = operations.length / 2;
    log(logger, "log", `Moved ${migrated} legacy reviewer identity/identities out of the intake queue.`);
    return migrated;
}

/** Removes one Firestore document key set at a time so a large account stays within batch limits. */
async function commitInBatches(firestore, operations, failureMessage) {
    for (let start = 0; start < operations.length; start += ERASURE_WRITE_BATCH_SIZE) {
        const chunk = operations.slice(start, start + ERASURE_WRITE_BATCH_SIZE);
        const batch = firestore.batch();
        for (const operation of chunk) operation(batch);
        try {
            await batch.commit();
        } catch (_error) {
            throw new Error(failureMessage);
        }
    }
}

/** Every vote on a theme that is itself being withdrawn; the document ids are voter UIDs. */
async function collectVoterReferences(firestore, themeId) {
    try {
        return await firestore
            .collection(LIKES_COLLECTION)
            .doc(themeId)
            .collection(LIKE_VOTERS_COLLECTION)
            .listDocuments();
    } catch (_error) {
        throw new Error("Could not read the votes of a withdrawn community theme");
    }
}

/**
 * Resolves the reservation that belongs to an account before its Auth identity is deleted. A
 * missing account is valid for anonymous/legacy users that never reserved a public name. If an
 * account exists, however, its claim must still agree so deletion can never release somebody
 * else's name through a corrupt profile.
 */
async function authorIdentityForErasure(firestore, uid) {
    const accountReference = firestore.collection(ACCOUNT_COLLECTION).doc(uid);
    let accountSnapshot;
    try {
        accountSnapshot = await accountReference.get();
    } catch (_error) {
        throw new Error("Could not read the community author account before erasure");
    }
    if (!accountSnapshot.exists) return null;
    const identity = validateAuthorAccount({ uid, data: accountSnapshot.data() });
    const claimReference = firestore.collection(AUTHOR_NAMES_COLLECTION).doc(identity.authorKey);
    let claimSnapshot;
    try {
        claimSnapshot = await claimReference.get();
    } catch (_error) {
        throw new Error("Could not read the community author-name reservation before erasure");
    }
    if (!claimSnapshot.exists) fail("missing-author-name-claim");
    validateAuthorNameClaim({ uid, ...identity, data: claimSnapshot.data() });
    return identity;
}

/**
 * Carries out one validated erasure, after the Git commit that withdrew any public files.
 *
 * The order is deliberate: everything this account owns or wrote goes first, the Firebase identity
 * second, and the request document last. A failure at any point leaves the request pending, and
 * every step is idempotent, so the next scheduled run resumes rather than restarting.
 */
async function applyAccountErasure({ firestore, auth, request, catalogIds, logger }) {
    if (auth === undefined || auth === null || typeof auth.deleteUser !== "function") {
        throw new Error("Account erasure requires a Firebase Auth client");
    }
    // Resolve the exact reservation now, but retain it until Firebase Authentication confirms that
    // the account is gone. This keeps a still-live identity from racing to reclaim its own name.
    const authorIdentity = await authorIdentityForErasure(firestore, request.uid);
    const intakeCollection = firestore.collection(INTAKE_COLLECTION);
    const keptThemeIds = new Set(request.keptThemeIds);
    const intakeOperations = request.intakeIds.map((id) => {
        const reference = intakeCollection.doc(id);
        if (request.themeDisposition === "keep" && keptThemeIds.has(id)) {
            // The public row and its pseudonym stay exactly as published; only the private link
            // back to a person is removed. Nothing re-reads a published intake, so a sentinel
            // owner cannot be mistaken for a submitter.
            return (batch) => batch.update(reference, {
                ownerUid: ERASED_OWNER_UID,
                ownerErasedAt: FieldValue.serverTimestamp(),
            });
        }
        return (batch) => batch.delete(reference);
    });
    const withdrawnMarkers = request.removedThemeIds.map((id) => {
        const reference = firestore.collection(PUBLISHED_THEMES_COLLECTION).doc(id);
        return (batch) => batch.delete(reference);
    });
    await commitInBatches(
        firestore,
        [...intakeOperations, ...withdrawnMarkers],
        "Could not erase the submissions of a deleted community account",
    );

    // Including the themes being kept public: those bytes are already committed to Git, so the
    // stored copy is only a private artefact still pointing at the person being erased.
    for (const themeId of request.intakeIds) {
        await deleteThemeScreenshots(firestore, themeId);
    }

    // Other people's votes on a theme that no longer exists publicly.
    for (const themeId of request.removedThemeIds) {
        const voters = await collectVoterReferences(firestore, themeId);
        await commitInBatches(
            firestore,
            voters.map((reference) => (batch) => batch.delete(reference)),
            "Could not erase the votes of a withdrawn community theme",
        );
    }

    // This account's own votes. The voters subcollection cannot be queried by voter UID, so the
    // bounded public catalogue is walked instead -- the same shape the app's Liked filter uses.
    const remainingThemeIds = [...catalogIds].filter((id) => !request.removedThemeIds.includes(id)).sort();
    await commitInBatches(
        firestore,
        remainingThemeIds.map((themeId) => (batch) => batch.delete(
            firestore.collection(LIKES_COLLECTION).doc(themeId).collection(LIKE_VOTERS_COLLECTION).doc(request.uid),
        )),
        "Could not erase the votes of a deleted community account",
    );

    await commitInBatches(
        firestore,
        [(batch) => batch.delete(firestore.collection(SUBMISSION_QUOTA_COLLECTION).doc(request.uid))],
        "Could not erase the submission quota of a deleted community account",
    );

    try {
        await auth.deleteUser(request.uid);
    } catch (error) {
        // An identity already gone is the success state of a resumed run, not a failure.
        if (error?.code !== "auth/user-not-found") {
            throw new Error("Could not delete the Firebase identity of an erased community account");
        }
    }

    const completion = firestore.batch();
    completion.delete(firestore.collection(ACCOUNT_COLLECTION).doc(request.uid));
    if (authorIdentity !== null) {
        completion.delete(firestore.collection(AUTHOR_NAMES_COLLECTION).doc(authorIdentity.authorKey));
    }
    completion.delete(firestore.collection(ACCOUNT_DELETION_COLLECTION).doc(request.uid));
    completion.set(firestore.collection(DELETED_ACCOUNTS_COLLECTION).doc(request.uid), {
        schemaVersion: DELETED_ACCOUNT_SCHEMA_VERSION,
        deletedAt: FieldValue.serverTimestamp(),
        expiresAt: Timestamp.fromMillis(Date.now() + DELETED_ACCOUNT_TOMBSTONE_MS),
    });
    try {
        await completion.commit();
    } catch (_error) {
        throw new Error("Could not finalize a completed community-account erasure");
    }
    log(
        logger,
        "log",
        `Erased a community account: ${request.intakeIds.length} submission(s), ` +
            `${request.removedThemeIds.length} withdrawn, ${request.keptThemeIds.length} kept public.`,
    );
}

function jsonText(value) {
    return `${JSON.stringify(value, null, 2)}\n`;
}

async function writeTemporaryFile(target, body) {
    await mkdir(dirname(target), { recursive: true });
    const temporary = join(dirname(target), `.${basename(target)}.${process.pid}.${randomUUID()}.tmp`);
    let handle;
    try {
        handle = await open(temporary, "wx", 0o600);
        await handle.writeFile(body, "utf8");
        await handle.sync();
        await handle.close();
        handle = undefined;
        return temporary;
    } catch (error) {
        if (handle !== undefined) await handle.close().catch(() => undefined);
        await unlink(temporary).catch(() => undefined);
        throw error;
    }
}

/** Creates a new profile without ever replacing a file another publisher has created. */
async function atomicCreateJson(target, value) {
    const temporary = await writeTemporaryFile(target, jsonText(value));
    try {
        await link(temporary, target);
    } finally {
        await unlink(temporary).catch(() => undefined);
    }
}

/** The binary sibling of atomicCreateJson; writeFile ignores the encoding for a Buffer. */
async function atomicCreateBytes(target, bytes) {
    const temporary = await writeTemporaryFile(target, bytes);
    try {
        await link(temporary, target);
    } finally {
        await unlink(temporary).catch(() => undefined);
    }
}

/** Atomic replacement is safe only after the caller has checked the catalogue did not change. */
async function atomicReplaceJson(target, value) {
    const temporary = await writeTemporaryFile(target, jsonText(value));
    try {
        await rename(temporary, target);
    } catch (error) {
        await unlink(temporary).catch(() => undefined);
        throw error;
    }
}

async function assertCatalogUnchanged(catalog) {
    const stat = await assertRegularFile(catalog.indexPath, "invalid-index-file");
    if (stat === null) fail("catalogue-changed-during-publication");
    const current = await readFile(catalog.indexPath, "utf8");
    const currentHash = createHash("sha256").update(current, "utf8").digest("hex");
    if (currentHash !== catalog.rawHash) fail("catalogue-changed-during-publication");
}

function documentIdForLog(rawId) {
    return typeof rawId === "string" && UUID.test(rawId) ? rawId : "an invalid intake document";
}

function log(logger, level, message) {
    const method = typeof logger?.[level] === "function" ? logger[level] : logger?.log;
    method?.call(logger, message);
}

function intakeFingerprint(data) {
    const fields = [
        "ownerUid",
        "status",
        "submissionSchemaVersion",
        "name",
        "author",
        "baseFace",
        "profileSchemaVersion",
        "revision",
        "profileJson",
        "settingsDigest",
        "moderationPreviewWebpBase64",
        "clientVersion",
    ];
    const hash = createHash("sha256");
    for (const field of fields) {
        const value = isRecord(data) ? data[field] : undefined;
        const type = typeof value;
        const serialized = type === "string" || type === "number" || type === "boolean"
            ? String(value)
            : `<${type}>`;
        for (const part of [field, type, serialized]) {
            const bytes = Buffer.from(part, "utf8");
            hash.update(writeInt(bytes.length));
            hash.update(bytes);
        }
    }
    return hash.digest("hex");
}

function rejectionFor(document, failure) {
    try {
        const data = document.data();
        return {
            id: typeof document.id === "string" ? document.id : undefined,
            reference: document.ref,
            failure,
            fingerprint: intakeFingerprint(data),
        };
    } catch (_error) {
        // If even a snapshot cannot be read, this is an operational failure, not grounds for a
        // terminal rejection that might hide a recoverable document.
        return null;
    }
}

function queueRejection(rejections, document, failure) {
    const rejection = rejectionFor(document, failure);
    if (rejection !== null) rejections.push(rejection);
}

async function rejectDeterministicIntakes(firestore, rejections, logger) {
    const unique = new Map();
    for (const rejection of rejections) {
        const key = rejection.reference?.path ?? rejection.id;
        if (typeof key === "string" && !unique.has(key)) unique.set(key, rejection);
    }
    let rejected = 0;
    for (const rejection of unique.values()) {
        const reference = rejection.reference ?? (typeof rejection.id === "string"
            ? firestore.collection(INTAKE_COLLECTION).doc(rejection.id)
            : undefined);
        if (reference === undefined) continue;
        try {
            const didReject = await firestore.runTransaction(async (transaction) => {
                const snapshot = await transaction.get(reference);
                if (!snapshot.exists || snapshot.data()?.status !== "approved") return false;
                if (intakeFingerprint(snapshot.data()) !== rejection.fingerprint) return false;
                transaction.update(reference, {
                    status: "rejected",
                    publicationFailure: rejection.failure,
                    rejectedBy: "community-theme-publisher",
                    rejectedAt: FieldValue.serverTimestamp(),
                });
                return true;
            });
            if (didReject) {
                rejected += 1;
            } else {
                log(logger, "warn", `Did not reject ${documentIdForLog(rejection.id)}: intake changed or is no longer approved.`);
            }
        } catch (_error) {
            throw new Error(`Could not reject invalid intake ${documentIdForLog(rejection.id)}`);
        }
    }
    if (rejections.length > 0) {
        log(logger, "log", `Rejected ${rejected} invalid or duplicate intake document(s).`);
    }
}

function fixedNow(now) {
    if (!(now instanceof Date) || Number.isNaN(now.getTime())) fail("invalid-clock");
    return now.toISOString();
}

/**
 * Reads only approved documents and calculates a conflict-free, idempotent publication plan.
 * A malformed approval never blocks unrelated valid documents, but it also can never enter Git.
 */
async function buildPublicationPlan({ firestore, root, now, logger, refreshLikes = false }) {
    const catalog = await readCatalog(root);
    // Withdrawals resolve before anything else. A theme leaving the catalogue must not be counted,
    // re-indexed, or held against a new submission as an already-published duplicate.
    const erasurePlan = await buildAccountErasurePlan({ firestore, catalog, logger });
    const withdrawals = await collectWithdrawnThemes({ firestore, catalog, logger });
    const erasingOwnerUids = new Set(erasurePlan.requests.map((request) => request.uid));
    const removedThemeIds = new Set([
        ...erasurePlan.requests.flatMap((request) => request.removedThemeIds),
        ...withdrawals.publicIds,
    ]);
    const withdrawnIds = new Set(withdrawals.ids);
    const publishedDigestState = await collectPublishedDigestState(catalog);
    for (const [digest, ids] of publishedDigestState.digests) {
        for (const id of removedThemeIds) ids.delete(id);
        if (ids.size === 0) publishedDigestState.digests.delete(digest);
    }
    const remainingEntries = catalog.entries.filter((entry) => !removedThemeIds.has(entry.id));
    const publishedAt = fixedNow(now);
    const snapshot = await firestore.collection(INTAKE_COLLECTION).where("status", "==", "approved").get();
    const documents = [...snapshot.docs].sort((left, right) => {
        const a = String(left.id);
        const b = String(right.id);
        return a < b ? -1 : a > b ? 1 : 0;
    });
    const catalogEntriesById = new Map(remainingEntries.map((entry) => [entry.id, entry]));
    const plans = [];
    const plannedDigests = new Map();
    const rejections = [];
    let skipped = 0;

    for (const document of documents) {
        const idForLog = documentIdForLog(document.id);
        // An account being erased has its whole intake queue removed at finalization. Publishing
        // one of its themes first would put a file into Git that the same run then withdraws.
        if (withdrawnIds.has(String(document.id))) {
            skipped += 1;
            log(logger, "warn", `Skipped ${idForLog}: withdrawn-by-moderator`);
            continue;
        }
        const ownerUid = document.data()?.ownerUid;
        if (typeof ownerUid === "string" && erasingOwnerUids.has(ownerUid)) {
            skipped += 1;
            log(logger, "warn", `Skipped ${idForLog}: owner-account-erasure-pending`);
            continue;
        }
        const screenshots = await collectApprovedScreenshots({
            firestore,
            themeId: String(document.id),
            ownerUid,
            logger,
        });
        let candidate;
        try {
            candidate = validateApprovedDocument({
                id: document.id,
                data: document.data(),
                publishedAt,
                screenshots: screenshots.map((shot) => shot.surface),
            });
        } catch (error) {
            skipped += 1;
            log(logger, "warn", `Skipped ${idForLog}: ${error instanceof ValidationError ? error.code : "invalid-intake-document"}`);
            queueRejection(rejections, document, "invalid-submission");
            continue;
        }
        if (candidate.submissionSchemaVersion >= 2) {
            try {
                await validateAuthorIdentity({
                    firestore,
                    uid: ownerUid,
                    author: candidate.publicProfile.author,
                });
            } catch (error) {
                if (!(error instanceof ValidationError)) throw error;
                skipped += 1;
                log(logger, "warn", `Skipped ${idForLog}: ${error.code}`);
                queueRejection(rejections, document, "invalid-author-identity");
                continue;
            }
        }

        let existingProfile;
        try {
            existingProfile = await readPublishedProfile(catalog.themesDirectory, candidate.id);
        } catch (error) {
            skipped += 1;
            log(logger, "warn", `Skipped ${candidate.id}: ${error instanceof ValidationError ? error.code : "existing-profile-unreadable"}`);
            continue;
        }

        let expectedProfile = candidate.publicProfile;
        let expectedSummary = candidate.summary;
        let needsProfileWrite = false;
        if (existingProfile === null) {
            needsProfileWrite = true;
        } else {
            let normalizedExisting;
            try {
                normalizedExisting = validatePublishedProfile(existingProfile.profile);
            } catch (_error) {
                skipped += 1;
                log(logger, "warn", `Skipped ${candidate.id}: existing-profile-conflict`);
                queueRejection(rejections, document, "profile-id-conflict");
                continue;
            }
            // A retry after a successful Git push may have a different current clock. Keep the
            // already-public timestamp and accept only an otherwise byte-for-byte equivalent theme.
            expectedProfile = {
                ...candidate.publicProfile,
                publishedAt: normalizedExisting.publishedAt,
            };
            expectedSummary = {
                ...candidate.summary,
                publishedAt: normalizedExisting.publishedAt,
            };
            if (!publicProfilesMatch(existingProfile.profile, expectedProfile)) {
                skipped += 1;
                log(logger, "warn", `Skipped ${candidate.id}: existing-profile-conflict`);
                queueRejection(rejections, document, "profile-id-conflict");
                continue;
            }
        }

        const existingEntry = catalogEntriesById.get(candidate.id);
        if (existingEntry !== undefined && !summariesMatch(existingEntry, expectedSummary)) {
            skipped += 1;
            log(logger, "warn", `Skipped ${candidate.id}: existing-index-conflict`);
            queueRejection(rejections, document, "profile-id-conflict");
            continue;
        }

        const existingDigestIds = publishedDigestState.digests.get(candidate.settingsDigest);
        if (existingDigestIds !== undefined &&
                (existingDigestIds.size !== 1 || !existingDigestIds.has(candidate.id))) {
            skipped += 1;
            log(logger, "warn", `Skipped ${candidate.id}: duplicate-published-settings`);
            queueRejection(rejections, document, "exact-duplicate");
            continue;
        }
        const plannedDuplicateId = plannedDigests.get(candidate.settingsDigest);
        if (plannedDuplicateId !== undefined && plannedDuplicateId !== candidate.id) {
            skipped += 1;
            log(logger, "warn", `Skipped ${candidate.id}: duplicate-approved-settings`);
            queueRejection(rejections, document, "exact-duplicate");
            continue;
        }
        plannedDigests.set(candidate.settingsDigest, candidate.id);
        plans.push({
            id: candidate.id,
            settingsDigest: candidate.settingsDigest,
            publicProfile: expectedProfile,
            summary: expectedSummary,
            needsProfileWrite,
            screenshots,
        });
    }

    // A single manifest/finalization transaction is intentionally bounded.  Keep the initial
    // page in Firestore document-id order and leave valid overflow documents approved for the
    // next scheduled run.  Build the catalogue solely from that page: no deferred candidate may
    // reach Git before it is represented in the manifest that later finalizes it.
    const eligible = plans.length;
    const selectedPlans = plans.slice(0, MAX_MANIFEST_ENTRIES);
    const likeCounts = await collectAuthoritativeLikeCounts(
        firestore,
        [...remainingEntries.map((entry) => entry.id), ...selectedPlans.map((plan) => plan.id)],
    );
    const refreshedEntries = remainingEntries.map((entry) => ({
        ...entry,
        likes: likeCounts.get(entry.id),
        // Recomputed from the published file rather than carried over, so a catalogue written
        // before this field existed gains a digest that is provably the one the profile hashes to.
        settingsDigest: publishedDigestState.digestById.get(entry.id) ?? entry.settingsDigest,
    }));
    const likesMoved = remainingEntries.some((entry, index) =>
        !hasOwn(entry, "likes") || entry.likes !== refreshedEntries[index].likes);
    // An entry carrying no count at all is not a stale number but a missing one -- the app reads
    // the absent field as zero -- so it is never made to wait out the refresh interval. A missing
    // settingsDigest is the same kind of gap: without it a phone cannot refuse a duplicate before
    // submitting one, which is the whole reason the field is published.
    const likesMissing = remainingEntries.some((entry) => !hasOwn(entry, "likes"));
    const digestsMissing = remainingEntries.some((entry, index) =>
        entry.settingsDigest !== refreshedEntries[index].settingsDigest);

    let publicationChanged = removedThemeIds.size > 0;
    const selectedEntriesById = new Map(remainingEntries.map((entry) => [entry.id, entry]));
    const addedEntries = [];
    for (const candidate of selectedPlans) {
        // A document cannot be liked until finalization changes it to `published`, so a newly
        // selected intake normally starts at zero. Still use the same authoritative source for a
        // retry where the static file already exists, rather than carrying a client-supplied total.
        candidate.summary = {
            ...candidate.summary,
            likes: likeCounts.get(candidate.id),
        };
        if (selectedEntriesById.has(candidate.id)) continue;
        addedEntries.push(candidate.summary);
        selectedEntriesById.set(candidate.id, candidate.summary);
        publicationChanged = true;
    }

    // Fresh counts are written whenever the file is being rewritten anyway, and otherwise only
    // once the interval has elapsed. Deferring costs a number some accuracy for a while; writing
    // every time costs a commit a day in a repository that is mainly an Android application.
    const writeRefreshed = (likesMoved || digestsMissing) && (
        likesMissing ||
        digestsMissing ||
        publicationChanged ||
        refreshLikes ||
        isLikeRefreshDue(catalog.generatedAt, publishedAt));
    const selectedEntries = [...(writeRefreshed ? refreshedEntries : remainingEntries), ...addedEntries];
    if (likesMoved && !writeRefreshed) {
        log(logger, "log", "Like counts moved but are not due for publication; deferring the catalogue rewrite.");
    }

    const catalogChanged = publicationChanged || writeRefreshed;

    const nextCatalog = {
        schemaVersion: PROFILE_SCHEMA_VERSION,
        generatedAt: catalogChanged ? publishedAt : catalog.generatedAt,
        themes: sortIndexEntries(selectedEntries),
    };
    if (catalogChanged && Buffer.byteLength(jsonText(nextCatalog), "utf8") > MAX_CATALOG_BYTES) {
        fail("catalogue-too-large");
    }
    return {
        catalog,
        plans: selectedPlans,
        eligible,
        deferred: eligible - selectedPlans.length,
        rejections,
        skipped,
        erasures: erasurePlan.requests,
        erasuresDeferred: erasurePlan.deferred,
        erasuresSkipped: erasurePlan.skipped,
        withdrawals: withdrawals.ids,
        removedThemeIds: [...removedThemeIds].sort(),
        nextCatalog,
        // A retry that only needs Firestore finalization must not create a meaningless catalogue
        // commit merely because a clock tick would change generatedAt.
        catalogChanged,
    };
}

function manifestFor(plans, erasures, withdrawals) {
    return {
        schemaVersion: MANIFEST_SCHEMA_VERSION,
        withdrawals: [...withdrawals].sort(),
        candidates: plans.map((plan) => ({
            id: plan.id,
            settingsDigest: plan.settingsDigest,
            publishedAt: plan.summary.publishedAt,
        })).sort((left, right) => left.id < right.id ? -1 : left.id > right.id ? 1 : 0),
        erasures: erasures.map((request) => ({
            uid: request.uid,
            themeDisposition: request.themeDisposition,
            intakeIds: [...request.intakeIds].sort(),
            removedThemeIds: [...request.removedThemeIds].sort(),
            keptThemeIds: [...request.keptThemeIds].sort(),
        })).sort((left, right) => left.uid < right.uid ? -1 : left.uid > right.uid ? 1 : 0),
    };
}

function validateManifestIdList(value, code) {
    if (!Array.isArray(value) || value.length > MAX_ERASED_THEMES_PER_ACCOUNT) fail(code);
    const ids = value.map((id) => assertCanonicalUuid(id, code));
    if (new Set(ids).size !== ids.length) fail(code);
    return ids;
}

function validateManifestErasures(rawErasures) {
    if (!Array.isArray(rawErasures) || rawErasures.length > MAX_ACCOUNT_DELETIONS_PER_RUN) {
        fail("invalid-publication-manifest-erasures");
    }
    const seen = new Set();
    return rawErasures.map((raw) => {
        const erasure = assertJsonRecord(raw, "invalid-publication-manifest-erasure");
        assertExactKeys(
            erasure,
            ["uid", "themeDisposition", "intakeIds", "removedThemeIds", "keptThemeIds"],
            "invalid-publication-manifest-erasure",
        );
        const uid = assertOpaqueText(erasure.uid, 128, "invalid-publication-manifest-erasure-uid");
        if (seen.has(uid)) fail("duplicate-publication-manifest-erasure");
        seen.add(uid);
        if (erasure.themeDisposition !== "keep" && erasure.themeDisposition !== "delete") {
            fail("invalid-publication-manifest-disposition");
        }
        const intakeIds = validateManifestIdList(erasure.intakeIds, "invalid-publication-manifest-erasure-ids");
        const removedThemeIds = validateManifestIdList(erasure.removedThemeIds, "invalid-publication-manifest-erasure-ids");
        const keptThemeIds = validateManifestIdList(erasure.keptThemeIds, "invalid-publication-manifest-erasure-ids");
        const owned = new Set(intakeIds);
        // A public id that this account never submitted would withdraw somebody else's theme.
        for (const id of [...removedThemeIds, ...keptThemeIds]) {
            if (!owned.has(id)) fail("unowned-publication-manifest-erasure-id");
        }
        if (erasure.themeDisposition === "keep" && removedThemeIds.length > 0) {
            fail("invalid-publication-manifest-disposition");
        }
        if (erasure.themeDisposition === "delete" && keptThemeIds.length > 0) {
            fail("invalid-publication-manifest-disposition");
        }
        return { uid, themeDisposition: erasure.themeDisposition, intakeIds, removedThemeIds, keptThemeIds };
    }).sort((left, right) => left.uid < right.uid ? -1 : left.uid > right.uid ? 1 : 0);
}

export function validateManifest(rawManifest) {
    const manifest = assertJsonRecord(rawManifest, "invalid-publication-manifest");
    assertExactKeys(
        manifest,
        ["schemaVersion", "candidates", "erasures", "withdrawals"],
        "invalid-publication-manifest",
    );
    assertExactInteger(manifest.schemaVersion, MANIFEST_SCHEMA_VERSION, "unsupported-publication-manifest");
    if (!Array.isArray(manifest.candidates) || manifest.candidates.length > MAX_MANIFEST_ENTRIES) {
        fail("invalid-publication-manifest");
    }
    const seen = new Set();
    const candidates = manifest.candidates.map((raw) => {
        const candidate = assertJsonRecord(raw, "invalid-publication-manifest-entry");
        assertExactKeys(candidate, ["id", "settingsDigest", "publishedAt"], "invalid-publication-manifest-entry");
        const id = assertCanonicalUuid(candidate.id, "invalid-publication-manifest-id");
        if (!DIGEST.test(candidate.settingsDigest)) fail("invalid-publication-manifest-digest");
        const publishedAt = assertPublicationTimestamp(candidate.publishedAt, "invalid-publication-manifest-date");
        if (seen.has(id)) fail("duplicate-publication-manifest-id");
        seen.add(id);
        return { id, settingsDigest: candidate.settingsDigest, publishedAt };
    });
    if (!Array.isArray(manifest.withdrawals) || manifest.withdrawals.length > MAX_WITHDRAWALS_PER_RUN) {
        fail("invalid-publication-manifest-withdrawals");
    }
    const withdrawals = manifest.withdrawals.map((id) =>
        assertCanonicalUuid(id, "invalid-publication-manifest-withdrawal-id"));
    if (new Set(withdrawals).size !== withdrawals.length) fail("duplicate-publication-manifest-withdrawal");
    return {
        candidates: candidates.sort((left, right) => left.id < right.id ? -1 : left.id > right.id ? 1 : 0),
        erasures: validateManifestErasures(manifest.erasures),
        withdrawals: withdrawals.sort(),
    };
}

async function readManifest(path) {
    const stat = await assertRegularFile(path, "invalid-publication-manifest-file");
    if (stat === null || stat.size > MAX_CATALOG_BYTES) fail("invalid-publication-manifest-file");
    return validateManifest(parseJsonText(await readFile(path, "utf8"), "invalid-publication-manifest"));
}

function publishedThemeMarker(entry) {
    return {
        schemaVersion: 1,
        revision: entry.revision,
        publishedAt: entry.publishedAt,
    };
}

function publishedThemeMarkerMatches(snapshot, expected) {
    if (!snapshot?.exists || typeof snapshot.data !== "function") return false;
    const actual = snapshot.data();
    return isJsonRecord(actual) &&
        Object.keys(actual).length === 3 &&
        hasOwn(actual, "schemaVersion") &&
        hasOwn(actual, "revision") &&
        hasOwn(actual, "publishedAt") &&
        actual.schemaVersion === expected.schemaVersion &&
        actual.revision === expected.revision &&
        actual.publishedAt === expected.publishedAt;
}

/**
 * Reconciles a private existence marker for every static catalogue entry. This intentionally runs
 * only from finalization, after the workflow has committed and pushed the Pages files. As a
 * result, rules can use the marker to let legacy hand-authored themes receive likes without ever
 * declaring a locally prepared, unpushed profile public.
 */
async function synchronizePublishedThemeMarkers(firestore, entries, logger) {
    let synchronized = 0;
    for (let start = 0; start < entries.length; start += PUBLISHED_MARKER_BATCH_SIZE) {
        const chunk = entries.slice(start, start + PUBLISHED_MARKER_BATCH_SIZE);
        const references = chunk.map((entry) => firestore.collection(PUBLISHED_THEMES_COLLECTION).doc(entry.id));
        let snapshots;
        try {
            snapshots = await firestore.getAll(...references);
        } catch (_error) {
            throw new Error("Could not read published community-theme markers");
        }
        if (!Array.isArray(snapshots)) throw new Error("Could not read published community-theme markers");
        const snapshotsById = new Map(snapshots.map((snapshot) => [snapshot?.id, snapshot]));
        let batch;
        for (let index = 0; index < chunk.length; index += 1) {
            const entry = chunk[index];
            const expected = publishedThemeMarker(entry);
            if (publishedThemeMarkerMatches(snapshotsById.get(entry.id), expected)) continue;
            batch ??= firestore.batch();
            batch.set(references[index], expected);
            synchronized += 1;
        }
        if (batch !== undefined) {
            try {
                await batch.commit();
            } catch (_error) {
                throw new Error("Could not write published community-theme markers");
            }
        }
    }
    if (synchronized > 0) {
        log(logger, "log", `Synchronized ${synchronized} published community-theme marker(s).`);
    }
    return synchronized;
}

/**
 * Creates static files, but never changes Firestore. The workflow commits and pushes those files
 * first, then calls finalizePublishedThemes. That ordering prevents a failed Git push from making
 * Firestore claim a theme is public when it is not.
 */
export async function publishApprovedThemes({
    firestore,
    root = REPOSITORY_ROOT,
    now = new Date(),
    logger = console,
    publish = false,
    manifestPath,
    refreshLikes = false,
}) {
    const plan = await buildPublicationPlan({ firestore, root, now, logger, refreshLikes });
    log(
        logger,
        "log",
        `Eligible approved intake documents: ${plan.eligible}; scheduled: ${plan.plans.length}; ` +
            `deferred: ${plan.deferred}; skipped: ${plan.skipped}; terminal rejections: ${plan.rejections.length}; ` +
            `account erasures: ${plan.erasures.length}; moderator withdrawals: ${plan.withdrawals.length}; ` +
            `catalogue files removed: ${plan.removedThemeIds.length}.`,
    );
    if (!publish) {
        for (const candidate of plan.plans) log(logger, "log", `Would publish ${candidate.id}.`);
        for (const id of plan.removedThemeIds) log(logger, "log", `Would withdraw ${id}.`);
        log(logger, "log", "Dry run complete; no files or Firestore documents were changed.");
        return plan;
    }
    if (typeof manifestPath !== "string" || manifestPath.length === 0) {
        fail("publish-requires-manifest-path");
    }
    // A validation/duplicate conflict is terminal for the immutable client queue. Do this before
    // touching static files; infrastructure failures still throw and leave the document approved.
    await rejectDeterministicIntakes(firestore, plan.rejections, logger);

    // Withdrawals happen before new files so a single commit never shows a theme both ways.
    for (const id of plan.removedThemeIds) {
        await unlink(profilePath(plan.catalog.themesDirectory, id)).catch((error) => {
            if (error?.code !== "ENOENT") throw error;
        });
        // Every registered surface, not only the ones this run resolved: a surface is only ever
        // added to SHOT_SURFACES, so the current registry covers whatever an older run wrote.
        for (const surface of SHOT_SURFACES) {
            await unlink(shotPath(plan.catalog.themesDirectory, id, surface)).catch((error) => {
                if (error?.code !== "ENOENT") throw error;
            });
        }
    }
    for (const candidate of plan.plans) {
        if (!candidate.needsProfileWrite) continue;
        const target = profilePath(plan.catalog.themesDirectory, candidate.id);
        // Hard-link creation fails if another process created this id after planning. It never
        // overwrites an existing publication, even in that narrow race.
        await atomicCreateJson(target, candidate.publicProfile);
    }
    // Deliberately not gated on needsProfileWrite: a run interrupted between the two writes leaves
    // a profile whose screenshot is missing, and this is what repairs it. EEXIST is the ordinary
    // outcome of a retry and never an overwrite -- the committed bytes stay the reviewed ones.
    for (const candidate of plan.plans) {
        for (const shot of candidate.screenshots) {
            const target = shotPath(plan.catalog.themesDirectory, candidate.id, shot.surface);
            await atomicCreateBytes(target, shot.image.bytes).catch((error) => {
                if (error?.code !== "EEXIST") throw error;
            });
        }
    }
    if (plan.catalogChanged) {
        await assertCatalogUnchanged(plan.catalog);
        await atomicReplaceJson(plan.catalog.indexPath, plan.nextCatalog);
    }
    await atomicCreateJson(
        resolve(manifestPath),
        manifestFor(plan.plans, plan.erasures, plan.withdrawals),
    );
    log(
        logger,
        "log",
        `Prepared ${plan.plans.length} publication(s) and ${plan.erasures.length} account erasure(s); ` +
            "finalize only after Git push succeeds.",
    );
    return plan;
}

/**
 * Marks exactly the successfully committed static publications. It re-reads both sides and uses
 * a transaction so a moderator changing an intake item between planning and finalization cannot
 * be overwritten into "published".
 */
export async function finalizePublishedThemes({
    firestore,
    auth,
    root = REPOSITORY_ROOT,
    manifestPath,
    logger = console,
}) {
    if (typeof manifestPath !== "string" || manifestPath.length === 0) fail("finalize-requires-manifest-path");
    const { candidates: manifest, erasures, withdrawals } = await readManifest(resolve(manifestPath));
    const catalog = await readCatalog(root);
    // The marker is an authorization fact, not merely an index hint. Verify every public profile
    // still exists and agrees with its index row before making any catalogue ID likeable.
    await collectPublishedDigestState(catalog);
    const entriesById = new Map(catalog.entries.map((entry) => [entry.id, entry]));
    await synchronizePublishedThemeMarkers(firestore, catalog.entries, logger);
    let marked = 0;
    let skipped = 0;

    for (const item of manifest) {
        const profileFile = await readPublishedProfile(catalog.themesDirectory, item.id).catch((error) => {
            log(logger, "warn", `Did not finalize ${item.id}: ${error instanceof ValidationError ? error.code : "profile-unreadable"}`);
            return undefined;
        });
        if (profileFile === undefined || profileFile === null) {
            skipped += 1;
            continue;
        }
        let staticProfile;
        try {
            staticProfile = validatePublishedProfile(profileFile.profile);
        } catch (_error) {
            skipped += 1;
            log(logger, "warn", `Did not finalize ${item.id}: invalid-static-profile`);
            continue;
        }
        const staticSummary = entriesById.get(item.id);
        if (staticSummary === undefined) {
            skipped += 1;
            log(logger, "warn", `Did not finalize ${item.id}: missing-static-index-entry`);
            continue;
        }

        const reference = firestore.collection(INTAKE_COLLECTION).doc(item.id);
        try {
            const didMark = await firestore.runTransaction(async (transaction) => {
                const snapshot = await transaction.get(reference);
                if (!snapshot.exists) return false;
                let current;
                try {
                    current = validateApprovedDocument({
                        id: snapshot.id,
                        data: snapshot.data(),
                        publishedAt: item.publishedAt,
                        // The committed file is the authority on its own screenshots; the intake
                        // never carried them. publicProfilesMatch ignores the field either way.
                        screenshots: staticProfile.screenshots ?? [],
                    });
                    if (current.submissionSchemaVersion >= 2) {
                        await validateAuthorIdentity({
                            firestore,
                            transaction,
                            uid: snapshot.data().ownerUid,
                            author: current.publicProfile.author,
                        });
                    }
                } catch (error) {
                    if (error instanceof ValidationError) return false;
                    throw error;
                }
                if (!digestMatches(current.settingsDigest, item.settingsDigest) ||
                        !publicProfilesMatch(staticProfile, current.publicProfile) ||
                        !summariesMatch(staticSummary, current.summary)) {
                    return false;
                }
                transaction.update(reference, {
                    status: "published",
                    publishedAt: FieldValue.serverTimestamp(),
                });
                return true;
            });
            if (didMark) {
                marked += 1;
                // The bytes are in Git now, so the stored copy is waste rather than a safeguard.
                // Failing to clear it must not fail a publication that has already succeeded.
                await deleteThemeScreenshots(firestore, item.id).catch(() => {
                    log(logger, "warn", `Left the stored screenshot of ${item.id} in place.`);
                });
            } else {
                skipped += 1;
                log(logger, "warn", `Did not finalize ${item.id}: intake changed or is no longer approved.`);
            }
        } catch (_error) {
            // Throw after the loop's current operation: the Git commit already exists, so a failed
            // status update must fail the workflow and be retried rather than silently forgotten.
            throw new Error(`Could not finalize publication for ${item.id}`);
        }
    }
    log(logger, "log", `Marked ${marked} Firestore intake document(s) as published; skipped ${skipped}.`);

    const withdrawn = await applyWithdrawals({
        firestore,
        withdrawals: { ids: withdrawals },
        logger,
    });
    const migratedReviewers = await migrateLegacyReviewers({ firestore, logger });

    // Erasure runs last: the withdrawn files are committed and every publication above is already
    // recorded, so a failure here can be retried by the next scheduled run without republishing.
    const catalogIds = new Set(catalog.entries.map((entry) => entry.id));
    let erased = 0;
    const failedErasures = [];
    for (const request of erasures) {
        try {
            await applyAccountErasure({ firestore, auth, request, catalogIds, logger });
            erased += 1;
        } catch (error) {
            // Never fail the whole run on one account: the rest of the queue still deserves to be
            // carried out. The throw after the loop makes the incomplete erasure visible instead.
            failedErasures.push(error instanceof Error ? error.message : "account-erasure-failed");
            log(logger, "error", "Could not complete a community-account erasure; it stays pending.");
        }
    }
    if (erasures.length > 0) {
        log(logger, "log", `Completed ${erased} of ${erasures.length} community-account erasure(s).`);
    }
    if (failedErasures.length > 0) {
        throw new Error(`Could not complete ${failedErasures.length} community-account erasure(s)`);
    }
    return { marked, skipped, erased, withdrawn, migratedReviewers };
}

function initializeFirebaseFromEnvironment() {
    const serialized = process.env.FIREBASE_SERVICE_ACCOUNT;
    if (typeof serialized !== "string" || serialized.length === 0) {
        throw new Error("FIREBASE_SERVICE_ACCOUNT is required");
    }
    let serviceAccount;
    try {
        serviceAccount = JSON.parse(serialized);
    } catch (_error) {
        throw new Error("FIREBASE_SERVICE_ACCOUNT is not valid JSON");
    }
    if (!isJsonRecord(serviceAccount) ||
            typeof serviceAccount.project_id !== "string" ||
            typeof serviceAccount.client_email !== "string" ||
            typeof serviceAccount.private_key !== "string") {
        throw new Error("FIREBASE_SERVICE_ACCOUNT is not a service-account JSON object");
    }
    const app = getApps()[0] ?? initializeApp({ credential: cert(serviceAccount) });
    return { firestore: getFirestore(app), auth: getAuth(app) };
}

function usage() {
    return [
        "Usage:",
        "  node publisher.mjs                         # dry run (default)",
        "  node publisher.mjs --publish --manifest <path> [--refresh-likes]",
        "  node publisher.mjs --finalize <manifest-path>",
    ].join("\n");
}

function parseArguments(argumentsList) {
    let publish = false;
    let manifestPath;
    let finalizePath;
    let refreshLikes = false;
    for (let index = 0; index < argumentsList.length; index += 1) {
        const argument = argumentsList[index];
        if (argument === "--publish") {
            publish = true;
        } else if (argument === "--refresh-likes") {
            // The manual way past the weekly interval, so a maintainer never has to wait it out.
            refreshLikes = true;
        } else if (argument === "--manifest") {
            manifestPath = argumentsList[++index];
        } else if (argument === "--finalize") {
            finalizePath = argumentsList[++index];
        } else if (argument === "--help" || argument === "-h") {
            return { help: true };
        } else {
            throw new Error("Unknown publisher argument");
        }
    }
    if (finalizePath !== undefined && (publish || manifestPath !== undefined || refreshLikes)) {
        throw new Error("--finalize cannot be combined with publication options");
    }
    if (publish && (typeof manifestPath !== "string" || manifestPath.length === 0)) {
        throw new Error("--publish requires --manifest <path>");
    }
    if (!publish && manifestPath !== undefined) throw new Error("--manifest requires --publish");
    if (finalizePath !== undefined && (typeof finalizePath !== "string" || finalizePath.length === 0)) {
        throw new Error("--finalize requires a manifest path");
    }
    return { publish, manifestPath, finalizePath, refreshLikes };
}

async function main() {
    const options = parseArguments(process.argv.slice(2));
    if (options.help) {
        process.stdout.write(`${usage()}\n`);
        return;
    }
    const { firestore, auth } = initializeFirebaseFromEnvironment();
    if (options.finalizePath !== undefined) {
        await finalizePublishedThemes({ firestore, auth, manifestPath: options.finalizePath });
    } else {
        await publishApprovedThemes({
            firestore,
            publish: options.publish,
            manifestPath: options.manifestPath,
            refreshLikes: options.refreshLikes,
        });
    }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
    main().catch((error) => {
        // Never print a Firebase service-account parse failure object: it can contain the secret.
        process.stderr.write(`Community theme publisher failed: ${error instanceof ValidationError ? error.code : error.message}\n`);
        process.exitCode = 1;
    });
}
