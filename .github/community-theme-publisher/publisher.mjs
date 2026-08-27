import { createHash, randomUUID, timingSafeEqual } from "node:crypto";
import { link, lstat, mkdir, open, readFile, rename, unlink } from "node:fs/promises";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { cert, getApps, initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import {
    ALLOWED_BASE_FACES,
    defaultSettingsForFace,
    MAX_PROFILE_JSON_BYTES,
    MAX_PUBLIC_TEXT_LENGTH,
    MAX_SETTING_TEXT_LENGTH,
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
const CATALOG_FILE = "index.json";
const MANIFEST_SCHEMA_VERSION = 1;
const MAX_CATALOG_BYTES = 512 * 1024;
const MAX_MANIFEST_ENTRIES = 1_000;
const LIKE_COUNT_CONCURRENCY = 16;
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
    "settings",
];
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

function assertSettingString(value) {
    if (!isWellFormed(value) || CONTROL_OR_SURROGATE.test(value) || value.length > MAX_SETTING_TEXT_LENGTH) {
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
                settingValue = assertSettingString(setting.value);
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
    assertExactKeys(profile, PUBLISHED_PROFILE_KEYS, "unexpected-published-profile-field");
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
        settings: requireCompleteSettings ? validateSettings(profile.settings) : validatePartialSettings(profile.settings),
    };
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

function buildPublishedProfile(profile, author, publishedAt) {
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
        settings: profile.settings,
    };
}

function buildIndexEntry(profile, author, publishedAt, likes = 0) {
    if (!Number.isSafeInteger(likes) || likes < 0) fail("invalid-index-likes");
    return {
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
}

/**
 * Re-validates every app-controlled value before it can cross into a public Git commit. Preview
 * bytes are checked only as part of the queue envelope and deliberately never appear in the
 * returned profile or index entry.
 */
export function validateApprovedDocument({ id, data, publishedAt }) {
    const documentId = assertCanonicalUuid(id, "invalid-document-id");
    if (!isRecord(data)) fail("invalid-intake-document");
    if (data.status !== "approved") fail("document-is-not-approved");
    assertOpaqueText(data.ownerUid, 128, "invalid-owner-uid");
    assertExactInteger(
        data.submissionSchemaVersion,
        SUBMISSION_SCHEMA_VERSION,
        "unsupported-submission-schema",
    );
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
    const publicProfile = buildPublishedProfile(profile, author, canonicalPublishedAt);
    if (Buffer.byteLength(jsonText(publicProfile), "utf8") > MAX_PUBLISHED_PROFILE_BYTES) {
        fail("published-profile-too-large");
    }
    return {
        id: documentId,
        settingsDigest: expectedDigest,
        changedSettings,
        publicProfile,
        summary: buildIndexEntry(profile, author, canonicalPublishedAt),
    };
}

function validateIndexEntry(rawEntry) {
    const entry = assertJsonRecord(rawEntry, "invalid-index-entry");
    const keys = Object.keys(entry);
    if (keys.some((key) => !INDEX_ENTRY_KEYS.includes(key))) fail("unexpected-index-entry-field");
    for (const required of INDEX_ENTRY_KEYS.filter((key) => key !== "likes")) {
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

function publicProfilesMatch(existing, expected) {
    try {
        const normalized = validatePublishedProfile(existing);
        return JSON.stringify(normalized) === JSON.stringify(expected);
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
    }
    return { digests };
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
async function buildPublicationPlan({ firestore, root, now, logger }) {
    const catalog = await readCatalog(root);
    const publishedDigestState = await collectPublishedDigestState(catalog);
    const publishedAt = fixedNow(now);
    const snapshot = await firestore.collection(INTAKE_COLLECTION).where("status", "==", "approved").get();
    const documents = [...snapshot.docs].sort((left, right) => {
        const a = String(left.id);
        const b = String(right.id);
        return a < b ? -1 : a > b ? 1 : 0;
    });
    const catalogEntriesById = new Map(catalog.entries.map((entry) => [entry.id, entry]));
    const plans = [];
    const plannedDigests = new Map();
    const rejections = [];
    let skipped = 0;

    for (const document of documents) {
        const idForLog = documentIdForLog(document.id);
        let candidate;
        try {
            candidate = validateApprovedDocument({
                id: document.id,
                data: document.data(),
                publishedAt,
            });
        } catch (error) {
            skipped += 1;
            log(logger, "warn", `Skipped ${idForLog}: ${error instanceof ValidationError ? error.code : "invalid-intake-document"}`);
            queueRejection(rejections, document, "invalid-submission");
            continue;
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
        [...catalog.entries.map((entry) => entry.id), ...selectedPlans.map((plan) => plan.id)],
    );
    const selectedEntries = catalog.entries.map((entry) => ({
        ...entry,
        likes: likeCounts.get(entry.id),
    }));
    const selectedEntriesById = new Map(selectedEntries.map((entry) => [entry.id, entry]));
    const likesChanged = catalog.entries.some((entry, index) =>
        !hasOwn(entry, "likes") || entry.likes !== selectedEntries[index].likes);
    let selectedIndexChanged = false;
    for (const candidate of selectedPlans) {
        // A document cannot be liked until finalization changes it to `published`, so a newly
        // selected intake normally starts at zero. Still use the same authoritative source for a
        // retry where the static file already exists, rather than carrying a client-supplied total.
        candidate.summary = {
            ...candidate.summary,
            likes: likeCounts.get(candidate.id),
        };
        if (selectedEntriesById.has(candidate.id)) continue;
        selectedEntries.push(candidate.summary);
        selectedEntriesById.set(candidate.id, candidate.summary);
        selectedIndexChanged = true;
    }

    const catalogChanged = selectedIndexChanged || likesChanged;

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
        nextCatalog,
        // A retry that only needs Firestore finalization must not create a meaningless catalogue
        // commit merely because a clock tick would change generatedAt.
        catalogChanged,
    };
}

function manifestFor(plans) {
    return {
        schemaVersion: MANIFEST_SCHEMA_VERSION,
        candidates: plans.map((plan) => ({
            id: plan.id,
            settingsDigest: plan.settingsDigest,
            publishedAt: plan.summary.publishedAt,
        })).sort((left, right) => left.id < right.id ? -1 : left.id > right.id ? 1 : 0),
    };
}

function validateManifest(rawManifest) {
    const manifest = assertJsonRecord(rawManifest, "invalid-publication-manifest");
    assertExactKeys(manifest, ["schemaVersion", "candidates"], "invalid-publication-manifest");
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
    return candidates.sort((left, right) => left.id < right.id ? -1 : left.id > right.id ? 1 : 0);
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
}) {
    const plan = await buildPublicationPlan({ firestore, root, now, logger });
    log(
        logger,
        "log",
        `Eligible approved intake documents: ${plan.eligible}; scheduled: ${plan.plans.length}; ` +
            `deferred: ${plan.deferred}; skipped: ${plan.skipped}; terminal rejections: ${plan.rejections.length}.`,
    );
    if (!publish) {
        for (const candidate of plan.plans) log(logger, "log", `Would publish ${candidate.id}.`);
        log(logger, "log", "Dry run complete; no files or Firestore documents were changed.");
        return plan;
    }
    if (typeof manifestPath !== "string" || manifestPath.length === 0) {
        fail("publish-requires-manifest-path");
    }
    // A validation/duplicate conflict is terminal for the immutable client queue. Do this before
    // touching static files; infrastructure failures still throw and leave the document approved.
    await rejectDeterministicIntakes(firestore, plan.rejections, logger);

    for (const candidate of plan.plans) {
        if (!candidate.needsProfileWrite) continue;
        const target = profilePath(plan.catalog.themesDirectory, candidate.id);
        // Hard-link creation fails if another process created this id after planning. It never
        // overwrites an existing publication, even in that narrow race.
        await atomicCreateJson(target, candidate.publicProfile);
    }
    if (plan.catalogChanged) {
        await assertCatalogUnchanged(plan.catalog);
        await atomicReplaceJson(plan.catalog.indexPath, plan.nextCatalog);
    }
    await atomicCreateJson(resolve(manifestPath), manifestFor(plan.plans));
    log(logger, "log", `Prepared ${plan.plans.length} publication(s); finalize only after Git push succeeds.`);
    return plan;
}

/**
 * Marks exactly the successfully committed static publications. It re-reads both sides and uses
 * a transaction so a moderator changing an intake item between planning and finalization cannot
 * be overwritten into "published".
 */
export async function finalizePublishedThemes({
    firestore,
    root = REPOSITORY_ROOT,
    manifestPath,
    logger = console,
}) {
    if (typeof manifestPath !== "string" || manifestPath.length === 0) fail("finalize-requires-manifest-path");
    const manifest = await readManifest(resolve(manifestPath));
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
                    });
                } catch (_error) {
                    return false;
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
    return { marked, skipped };
}

function initializeFirestoreFromEnvironment() {
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
    return getFirestore(app);
}

function usage() {
    return [
        "Usage:",
        "  node publisher.mjs                         # dry run (default)",
        "  node publisher.mjs --publish --manifest <path>",
        "  node publisher.mjs --finalize <manifest-path>",
    ].join("\n");
}

function parseArguments(argumentsList) {
    let publish = false;
    let manifestPath;
    let finalizePath;
    for (let index = 0; index < argumentsList.length; index += 1) {
        const argument = argumentsList[index];
        if (argument === "--publish") {
            publish = true;
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
    if (finalizePath !== undefined && (publish || manifestPath !== undefined)) {
        throw new Error("--finalize cannot be combined with publication options");
    }
    if (publish && (typeof manifestPath !== "string" || manifestPath.length === 0)) {
        throw new Error("--publish requires --manifest <path>");
    }
    if (!publish && manifestPath !== undefined) throw new Error("--manifest requires --publish");
    if (finalizePath !== undefined && (typeof finalizePath !== "string" || finalizePath.length === 0)) {
        throw new Error("--finalize requires a manifest path");
    }
    return { publish, manifestPath, finalizePath };
}

async function main() {
    const options = parseArguments(process.argv.slice(2));
    if (options.help) {
        process.stdout.write(`${usage()}\n`);
        return;
    }
    const firestore = initializeFirestoreFromEnvironment();
    if (options.finalizePath !== undefined) {
        await finalizePublishedThemes({ firestore, manifestPath: options.finalizePath });
    } else {
        await publishApprovedThemes({
            firestore,
            publish: options.publish,
            manifestPath: options.manifestPath,
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
