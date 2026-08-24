import assert from "node:assert/strict";
import { mkdtemp, mkdir, readdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import {
    SETTING_KEYS as ADMIN_SETTING_KEYS,
    SETTING_TYPES as ADMIN_SETTING_TYPES,
} from "../../docs/admin/theme-profile-schema.mjs";
import {
    defaultSettingsForFace,
    isOriginalityApplicableSetting,
    SETTING_KEYS,
    SETTING_TYPES,
} from "./schema.mjs";
import {
    ValidationError,
    canonicalSettingsDigest,
    finalizePublishedThemes,
    publishApprovedThemes,
    validateApprovedDocument,
} from "./publisher.mjs";

const ID = "123e4567-e89b-42d3-a456-426614174000";
const SECOND_ID = "223e4567-e89b-42d3-a456-426614174000";
const PUBLISHED_AT = "2026-08-24T12:00:00Z";

function settingsWithTwelveChanges(baseFace) {
    const settings = defaultSettingsForFace(baseFace);
    const changes = {
        album_art_style: "cover",
        always_show_time: true,
        ambient_album_art_opacity: 56,
        dim_album_art: false,
        screen_buttons_bg_style: "uniform_glass",
        screen_buttons_curve_style: "arc",
        screen_buttons_opacity: 99,
        screen_buttons_shape: "circle",
        wear_accent_floor: "soft",
        wear_album_accent_source: "vibrant",
        wear_album_art_fade: false,
        wear_aod_art_treatment: "clear",
    };
    for (const [key, value] of Object.entries(changes)) {
        if (settings[key].value === value) {
            throw new Error(`Test fixture change ${key} must differ from ${baseFace} default`);
        }
        settings[key].value = value;
    }
    return settings;
}

function allSettings(baseFace = "poster") {
    return settingsWithTwelveChanges(baseFace);
}

function timestamp() {
    return { toMillis: () => 1_787_594_181_000 };
}

function approvedDocument(overrides = {}, id = ID) {
    const profile = {
        schemaVersion: 1,
        id,
        name: "Night signal",
        baseFace: "poster",
        createdAt: 1_787_594_181_000,
        updatedAt: 1_787_594_181_000,
        revision: 1,
        settings: allSettings(),
    };
    const profileJson = JSON.stringify(profile);
    return {
        id,
        data: {
            ownerUid: "firebase-user-id",
            status: "approved",
            submissionSchemaVersion: 1,
            name: profile.name,
            author: "Theme maker",
            baseFace: profile.baseFace,
            profileSchemaVersion: 1,
            revision: 1,
            profileJson,
            settingsDigest: canonicalSettingsDigest(profile.baseFace, profile.settings),
            moderationPreviewWebpBase64: "AAAA",
            clientVersion: "3.3",
            createdAt: timestamp(),
            ...overrides,
        },
        publishedAt: PUBLISHED_AT,
    };
}

function assertValidationCode(action, code) {
    assert.throws(action, (error) => error instanceof ValidationError && error.code === code);
}

function fakeFirestore(input) {
    const documents = Array.isArray(input) ? input : [input];
    const byId = new Map(documents.map((document) => [document.id, document]));
    return {
        collection(collectionName) {
            assert.equal(collectionName, "themeIntake");
            return {
                where(field, operator, value) {
                    assert.equal(field, "status");
                    assert.equal(operator, "==");
                    assert.equal(value, "approved");
                    return {
                        get: async () => ({
                            docs: documents.filter((document) => document.data.status === "approved").map((document) => ({
                                id: document.id,
                                ref: { id: document.id, path: `themeIntake/${document.id}` },
                                data: () => document.data,
                            })),
                        }),
                    };
                },
                doc(id) {
                    return { id };
                },
            };
        },
        async runTransaction(callback) {
            const transaction = {
                get: async (reference) => ({
                    id: reference.id,
                    exists: byId.has(reference.id),
                    data: () => byId.get(reference.id)?.data,
                }),
                update(reference, values) {
                    Object.assign(byId.get(reference.id).data, values);
                },
            };
            return callback(transaction);
        },
    };
}

async function temporaryCatalogue(t) {
    const root = await mkdtemp(join(tmpdir(), "svartifoss-theme-publisher-"));
    t.after(() => rm(root, { recursive: true, force: true }));
    await mkdir(join(root, "docs", "themes"), { recursive: true });
    await writeFile(join(root, "docs", "themes", "index.json"), `${JSON.stringify({
        schemaVersion: 1,
        generatedAt: "2026-08-24T00:00:00Z",
        themes: [],
    }, null, 2)}\n`);
    return root;
}

test("the publisher mirrors the pinned CommunityThemeSubmissionPolicy digest", () => {
    assert.equal(
        canonicalSettingsDigest("poster", {
            number: { type: "int", value: 1 },
            word: { type: "string", value: "one" },
        }),
        "sha256:491a68363c9e3f536b26aa90fee19ce96b2d27429fd376165e28dc2e7b098b13",
    );
});

test("the browser moderation schema stays aligned with the publisher schema", () => {
    assert.deepEqual(ADMIN_SETTING_TYPES, SETTING_TYPES);
    assert.deepEqual(ADMIN_SETTING_KEYS, SETTING_KEYS);
    assert.deepEqual(Object.keys(ADMIN_SETTING_TYPES).sort(), ADMIN_SETTING_KEYS);
});

test("a complete approved intake creates a preview-free public profile", () => {
    const candidate = validateApprovedDocument(approvedDocument());

    assert.equal(SETTING_KEYS.length, 115);
    assert.equal(candidate.id, ID);
    assert.equal(candidate.publicProfile.author, "Theme maker");
    assert.equal(candidate.publicProfile.publishedAt, PUBLISHED_AT);
    assert.equal(candidate.summary.minimumAppVersion, "3.3");
    assert.equal(Object.hasOwn(candidate.publicProfile, "moderationPreviewWebpBase64"), false);
    assert.deepEqual(Object.keys(candidate.publicProfile.settings), SETTING_KEYS);
});

test("unknown or missing settings fail closed", () => {
    const document = approvedDocument();
    const profile = JSON.parse(document.data.profileJson);
    delete profile.settings.wear_font;
    profile.settings.untrusted_setting = { type: "string", value: "no" };
    document.data.profileJson = JSON.stringify(profile);

    assertValidationCode(
        () => validateApprovedDocument(document),
        "unknown-or-missing-setting",
    );
});

test("a decimal JSON number is rejected even when JavaScript would coerce it to an integer", () => {
    const document = approvedDocument();
    document.data.profileJson = document.data.profileJson.replace('"value":56', '"value":56.0');

    assertValidationCode(
        () => validateApprovedDocument(document),
        "non-integer-json-number",
    );
});

test("a mismatched canonical digest cannot be published", () => {
    const document = approvedDocument({
        settingsDigest: "sha256:0000000000000000000000000000000000000000000000000000000000000000",
    });

    assertValidationCode(
        () => validateApprovedDocument(document),
        "settings-digest-mismatch",
    );
});

test("unsupported semantic enum, range and color values cannot qualify for originality", () => {
    const enumDocument = approvedDocument();
    const enumProfile = JSON.parse(enumDocument.data.profileJson);
    enumProfile.settings.wear_color_treatment.value = "not-a-treatment";
    enumDocument.data.profileJson = JSON.stringify(enumProfile);
    enumDocument.data.settingsDigest = canonicalSettingsDigest(enumProfile.baseFace, enumProfile.settings);
    assertValidationCode(
        () => validateApprovedDocument(enumDocument),
        "unsupported-setting-value",
    );

    const rangeDocument = approvedDocument();
    const rangeProfile = JSON.parse(rangeDocument.data.profileJson);
    rangeProfile.settings.album_art_blur_radius.value = 121;
    rangeDocument.data.profileJson = JSON.stringify(rangeProfile);
    rangeDocument.data.settingsDigest = canonicalSettingsDigest(rangeProfile.baseFace, rangeProfile.settings);
    assertValidationCode(
        () => validateApprovedDocument(rangeDocument),
        "unsupported-setting-value",
    );

    const colorDocument = approvedDocument();
    const colorProfile = JSON.parse(colorDocument.data.profileJson);
    colorProfile.settings.wear_normal_color.value = "#abcdef";
    colorDocument.data.profileJson = JSON.stringify(colorProfile);
    colorDocument.data.settingsDigest = canonicalSettingsDigest(colorProfile.baseFace, colorProfile.settings);
    assertValidationCode(
        () => validateApprovedDocument(colorDocument),
        "unsupported-setting-value",
    );

    const archivedDocument = approvedDocument();
    const archivedProfile = JSON.parse(archivedDocument.data.profileJson);
    archivedProfile.settings.wear_font.value = "typewriter";
    archivedDocument.data.profileJson = JSON.stringify(archivedProfile);
    archivedDocument.data.settingsDigest = canonicalSettingsDigest(
        archivedProfile.baseFace,
        archivedProfile.settings,
    );
    assertValidationCode(
        () => validateApprovedDocument(archivedDocument),
        "unsupported-setting-value",
    );
});

test("the trusted publisher enforces the 12-setting originality floor", () => {
    const document = approvedDocument();
    const profile = JSON.parse(document.data.profileJson);
    profile.settings = defaultSettingsForFace(profile.baseFace);
    document.data.profileJson = JSON.stringify(profile);
    document.data.settingsDigest = canonicalSettingsDigest(profile.baseFace, profile.settings);

    assertValidationCode(
        () => validateApprovedDocument(document),
        "insufficient-originality",
    );
});

test("changes that cannot affect the selected face do not meet the originality floor", () => {
    const document = approvedDocument();
    const profile = JSON.parse(document.data.profileJson);
    profile.settings = defaultSettingsForFace(profile.baseFace);
    Object.assign(profile.settings, {
        wear_carousel_card_shape: { type: "string", value: "square" },
        wear_split_panel: { type: "string", value: "solid" },
        wear_classic_icons_visible: { type: "boolean", value: false },
        wear_expressive_seek_mode: { type: "string", value: "edge" },
        wear_metadata_show_core: { type: "boolean", value: false },
        wear_metadata_show_credits: { type: "boolean", value: false },
        wear_metadata_show_identifiers: { type: "boolean", value: true },
        wear_metadata_show_playback: { type: "boolean", value: false },
        wear_metadata_show_release: { type: "boolean", value: false },
        wear_metadata_show_technical: { type: "boolean", value: false },
        wear_artist_desaturated: { type: "boolean", value: true },
        wear_progress_desaturated: { type: "boolean", value: true },
    });
    document.data.profileJson = JSON.stringify(profile);
    document.data.settingsDigest = canonicalSettingsDigest(profile.baseFace, profile.settings);

    assertValidationCode(
        () => validateApprovedDocument(document),
        "insufficient-originality",
    );
});

test("public text must already be normalized and control-free", () => {
    const document = approvedDocument({ name: "Night\n signal" });

    assertValidationCode(
        () => validateApprovedDocument(document),
        "invalid-document-name",
    );
});

test("publication writes static files first, then finalization marks the matching intake", async (t) => {
    const root = await temporaryCatalogue(t);
    const document = approvedDocument();
    const firestore = fakeFirestore(document);
    const manifestPath = join(root, "publication-manifest.json");
    const logger = { log() {}, warn() {} };

    await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger,
        publish: true,
        manifestPath,
    });

    assert.equal(document.data.status, "approved");
    const profile = JSON.parse(await readFile(join(root, "docs", "themes", `${ID}.json`), "utf8"));
    const index = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    assert.equal(Object.hasOwn(profile, "moderationPreviewWebpBase64"), false);
    assert.equal(index.themes[0].id, ID);

    const result = await finalizePublishedThemes({ firestore, root, manifestPath, logger });
    assert.deepEqual(result, { marked: 1, skipped: 0 });
    assert.equal(document.data.status, "published");
    assert.ok(document.data.publishedAt);
});

test("the publisher rejects an exact digest already present in the public catalogue", async (t) => {
    const root = await temporaryCatalogue(t);
    const first = approvedDocument();
    const firstFirestore = fakeFirestore(first);
    const logger = { log() {}, warn() {} };
    const firstManifest = join(root, "first-manifest.json");
    await publishApprovedThemes({
        firestore: firstFirestore,
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: firstManifest,
    });
    await finalizePublishedThemes({ firestore: firstFirestore, root, manifestPath: firstManifest, logger });

    const duplicate = approvedDocument({}, SECOND_ID);
    await publishApprovedThemes({
        firestore: fakeFirestore(duplicate),
        root,
        now: new Date("2026-08-25T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "duplicate-manifest.json"),
    });

    assert.equal(duplicate.data.status, "rejected");
    assert.equal(duplicate.data.publicationFailure, "exact-duplicate");
});

test("the publisher deterministically rejects duplicate approved documents in one batch", async (t) => {
    const root = await temporaryCatalogue(t);
    const first = approvedDocument();
    const duplicate = approvedDocument({}, SECOND_ID);
    const logger = { log() {}, warn() {} };

    await publishApprovedThemes({
        firestore: fakeFirestore([duplicate, first]),
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "batch-manifest.json"),
    });

    assert.equal(first.data.status, "approved");
    assert.equal(duplicate.data.status, "rejected");
    assert.equal(duplicate.data.publicationFailure, "exact-duplicate");
});

test("partial Phase-1 profiles are materialized before duplicate comparison", async (t) => {
    const root = await temporaryCatalogue(t);
    const fullSettings = settingsWithTwelveChanges("poster");
    const partialSettings = Object.fromEntries(
        SETTING_KEYS
            .filter((key) => fullSettings[key].value !== defaultSettingsForFace("poster")[key].value)
            .map((key) => [key, fullSettings[key]]),
    );
    const legacy = {
        schemaVersion: 1,
        id: SECOND_ID,
        name: "Legacy poster",
        author: "Svartifoss",
        baseFace: "poster",
        createdAt: 1_787_594_181_000,
        updatedAt: 1_787_594_181_000,
        revision: 1,
        minimumAppVersion: "3.3",
        publishedAt: "2026-08-24T00:00:00Z",
        settings: partialSettings,
    };
    await writeFile(join(root, "docs", "themes", `${SECOND_ID}.json`), `${JSON.stringify(legacy, null, 2)}\n`);
    await writeFile(join(root, "docs", "themes", "index.json"), `${JSON.stringify({
        schemaVersion: 1,
        generatedAt: "2026-08-24T00:00:00Z",
        themes: [{
            id: legacy.id,
            name: legacy.name,
            author: legacy.author,
            baseFace: legacy.baseFace,
            revision: legacy.revision,
            schemaVersion: legacy.schemaVersion,
            minimumAppVersion: legacy.minimumAppVersion,
            publishedAt: legacy.publishedAt,
        }],
    }, null, 2)}\n`);

    const duplicate = approvedDocument();
    const profile = JSON.parse(duplicate.data.profileJson);
    profile.settings = fullSettings;
    duplicate.data.profileJson = JSON.stringify(profile);
    duplicate.data.settingsDigest = canonicalSettingsDigest(profile.baseFace, profile.settings);

    await publishApprovedThemes({
        firestore: fakeFirestore(duplicate),
        root,
        now: new Date("2026-08-25T12:00:00Z"),
        logger: { log() {}, warn() {} },
        publish: true,
        manifestPath: join(root, "legacy-duplicate-manifest.json"),
    });

    assert.equal(duplicate.data.status, "rejected");
    assert.equal(duplicate.data.publicationFailure, "exact-duplicate");
});

test("a trusted partial Phase-1 profile can use its segregated legacy-read vocabulary", async (t) => {
    const root = await temporaryCatalogue(t);
    const legacy = {
        schemaVersion: 1,
        id: SECOND_ID,
        name: "Legacy poster",
        author: "Svartifoss",
        baseFace: "poster",
        createdAt: 1_787_594_181_000,
        updatedAt: 1_787_594_181_000,
        revision: 1,
        minimumAppVersion: "3.3",
        publishedAt: "2026-08-24T00:00:00Z",
        settings: {
            wear_screen_theme: { type: "string", value: "cinema" },
        },
    };
    await writeFile(join(root, "docs", "themes", `${SECOND_ID}.json`), `${JSON.stringify(legacy, null, 2)}\n`);
    await writeFile(join(root, "docs", "themes", "index.json"), `${JSON.stringify({
        schemaVersion: 1,
        generatedAt: "2026-08-24T00:00:00Z",
        themes: [{
            id: legacy.id,
            name: legacy.name,
            author: legacy.author,
            baseFace: legacy.baseFace,
            revision: legacy.revision,
            schemaVersion: legacy.schemaVersion,
            minimumAppVersion: legacy.minimumAppVersion,
            publishedAt: legacy.publishedAt,
        }],
    }, null, 2)}\n`);

    const result = await publishApprovedThemes({
        firestore: fakeFirestore([]),
        root,
        now: new Date("2026-08-25T12:00:00Z"),
        logger: { log() {}, warn() {} },
    });
    assert.equal(result.plans.length, 0);
});

test("the applicability contract excludes only the corrected face-specific controls", () => {
    const poster = defaultSettingsForFace("poster");
    const classic = defaultSettingsForFace("classic");
    const chat = defaultSettingsForFace("chat");

    assert.equal(isOriginalityApplicableSetting("wear_internal_progress_visible", poster, "poster"), true);
    assert.equal(isOriginalityApplicableSetting("wear_internal_progress_visible", classic, "classic"), false);
    assert.equal(isOriginalityApplicableSetting("wear_quadrant_tap_flash", classic, "classic"), true);
    assert.equal(isOriginalityApplicableSetting("wear_quadrant_tap_flash", poster, "poster"), false);
    assert.equal(isOriginalityApplicableSetting("screen_buttons_curve_style", poster, "poster"), true);
    assert.equal(isOriginalityApplicableSetting("screen_buttons_shape", chat, "chat"), false);
    assert.equal(isOriginalityApplicableSetting("wear_dynamic_accent", poster, "poster"), false);
    assert.equal(isOriginalityApplicableSetting("wear_classic_icons_visible", poster, "poster"), true);
    assert.equal(isOriginalityApplicableSetting("wear_classic_icons_visible", classic, "expressive"), false);
    assert.equal(isOriginalityApplicableSetting("wear_classic_icons_visible", classic, "material"), false);
});

test("an oversized eligible batch is deterministically paged without publishing overflow", async (t) => {
    const root = await temporaryCatalogue(t);
    const documents = Array.from({ length: 1_001 }, (_, index) => {
        const id = `123e4567-e89b-42d3-a456-${(index + 1).toString(16).padStart(12, "0")}`;
        const document = approvedDocument({}, id);
        const profile = JSON.parse(document.data.profileJson);
        profile.settings.wear_title_font_weight.value = (index % 1_000) + 1;
        profile.settings.wear_artist_font_weight.value = Math.floor(index / 1_000) + 1;
        document.data.profileJson = JSON.stringify(profile);
        document.data.settingsDigest = canonicalSettingsDigest(profile.baseFace, profile.settings);
        return document;
    });
    const firestore = fakeFirestore([...documents].reverse());
    const manifestPath = join(root, "paged-manifest.json");
    const result = await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger: { log() {}, warn() {} },
        publish: true,
        manifestPath,
    });

    assert.equal(result.eligible, 1_001);
    assert.equal(result.plans.length, 1_000);
    assert.equal(result.deferred, 1);
    const firstId = documents[0].id;
    const overflowId = documents.at(-1).id;
    const files = await readdir(join(root, "docs", "themes"));
    assert.equal(files.length, 1_001);
    assert.equal(files.includes(`${firstId}.json`), true);
    assert.equal(files.includes(`${overflowId}.json`), false);
    const index = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    assert.equal(index.themes.length, 1_000);
    const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
    assert.equal(manifest.candidates.length, 1_000);
    assert.equal(documents.every((document) => document.data.status === "approved"), true);
});
