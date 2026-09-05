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
    COMMUNITY_THEME_CONSTRAINTS,
    defaultSettingsForFace,
    isOriginalityApplicableSetting,
    MINIMUM_APP_VERSION,
    SETTING_KEYS,
    SETTING_TYPES,
} from "./schema.mjs";
import { FieldValue } from "firebase-admin/firestore";
import {
    ValidationError,
    canonicalSettingsDigest,
    decodeThemeScreenshot,
    describeThemeScreenshot,
    finalizePublishedThemes,
    isLikeRefreshDue,
    publishApprovedThemes,
    validateApprovedDocument,
    validateManifest,
} from "./publisher.mjs";

const DELETE_SENTINEL = FieldValue.delete();
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
            submissionSchemaVersion: 2,
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

function fakeFirestore(input, likesByTheme = {}, options = {}) {
    const documents = Array.isArray(input) ? input : [input];
    const byId = new Map(documents.map((document) => [document.id, document]));
    const likes = new Map(Object.entries(likesByTheme));
    const publishedThemeMarkers = new Map();
    const deletionRequests = new Map(Object.entries(options.deletionRequests ?? {}));
    const voters = new Map(
        Object.entries(options.voters ?? {}).map(([themeId, uids]) => [themeId, new Set(uids)]),
    );
    const installers = new Map(
        Object.entries(options.installers ?? {}).map(([themeId, uids]) => [themeId, new Set(uids)]),
    );
    const reporters = new Map(
        Object.entries(options.reporters ?? {}).map(([themeId, uids]) => [themeId, new Set(uids)]),
    );
    const installs = new Map(Object.entries(options.installs ?? {}));
    const quotas = new Set(options.quotas ?? []);
    const reviews = new Map(Object.entries(options.reviews ?? {}));
    // Author screenshots, keyed "<themeId>/<surface>" so one flat map models the subcollection.
    const shots = new Map(Object.entries(options.shots ?? {}));
    const defaultAccounts = {};
    const defaultAuthorNames = {};
    for (const document of documents) {
        const { ownerUid, author, submissionSchemaVersion } = document.data;
        if (submissionSchemaVersion < 2 || author === "Anonymous") continue;
        const authorKey = `v1:${author.toLowerCase()}`;
        defaultAccounts[ownerUid] = {
            ownerUid,
            accountSchemaVersion: 1,
            authorName: author,
            authorKey,
            createdAt: timestamp(),
        };
        defaultAuthorNames[authorKey] = {
            ownerUid,
            nameSchemaVersion: 1,
            authorName: author,
            authorKey,
            createdAt: timestamp(),
        };
    }
    const accounts = new Map(Object.entries(options.accounts ?? defaultAccounts));
    const authorNames = new Map(Object.entries(options.authorNames ?? defaultAuthorNames));
    const deletedAccounts = new Map(Object.entries(options.deletedAccounts ?? {}));
    const events = options.events ?? [];
    const removed = {
        intake: [], markers: [], votes: [], quotas: [], requests: [], reviews: [], accounts: [], authorNames: [],
        shots: [], installs: [], reports: [],
    };

    const snapshotFor = (target) => {
        const segments = target.path.split("/");
        let value;
        if (segments[0] === "themeIntake") value = byId.get(target.id)?.data;
        else if (segments[0] === "themeIntakeReview") value = reviews.get(target.id);
        else if (segments[0] === "communityThemePublished") value = publishedThemeMarkers.get(target.id);
        else if (segments[0] === "communityThemeAccounts") value = accounts.get(target.id);
        else if (segments[0] === "communityThemeAuthorNames") value = authorNames.get(target.id);
        else if (segments[0] === "communityThemeDeletedAccounts") value = deletedAccounts.get(target.id);
        else if (segments[0] === "communityThemeAccountDeletion") value = deletionRequests.get(target.id);
        return { id: target.id, exists: value !== undefined, data: () => value };
    };
    const reference = (path, id) => {
        const target = { id, path: `${path}/${id}` };
        target.get = async () => snapshotFor(target);
        return target;
    };
    const intakeDocuments = (predicate, limit = Number.MAX_SAFE_INTEGER) => {
        const query = {
            limit: (count) => intakeDocuments(predicate, count),
            get: async () => ({
                docs: documents
                    .filter((document) => byId.has(document.id) && predicate(document))
                    .slice(0, limit)
                    .map((document) => ({
                        id: document.id,
                        ref: reference("themeIntake", document.id),
                        data: () => document.data,
                    })),
            }),
        };
        return query;
    };

    function applyWrite(target, operation, value) {
        const segments = target.path.split("/");
        events.push(`${operation}:${target.path}`);
        if (segments[0] === "themeIntake") {
            if (operation === "update") {
                const data = byId.get(target.id).data;
                for (const [key, entry] of Object.entries(value)) {
                    // firebase-admin's delete sentinel removes the field rather than storing it.
                    if (entry === DELETE_SENTINEL) delete data[key];
                    else data[key] = entry;
                }
            } else {
                byId.delete(target.id);
                removed.intake.push(target.id);
            }
            return;
        }
        if (segments[0] === "themeIntakeReview") {
            if (operation === "set") reviews.set(target.id, { ...value });
            else {
                reviews.delete(target.id);
                removed.reviews.push(target.id);
            }
            return;
        }
        if (segments[0] === "communityThemePublished") {
            if (operation === "set") publishedThemeMarkers.set(target.id, { ...value });
            else {
                publishedThemeMarkers.delete(target.id);
                removed.markers.push(target.id);
            }
            return;
        }
        if (segments[0] === "themeIntakeShots") {
            const key = `${segments[1]}/${target.id}`;
            shots.delete(key);
            removed.shots.push(key);
            return;
        }
        if (segments[0] === "communityThemeLikes") {
            voters.get(segments[1])?.delete(target.id);
            removed.votes.push(`${segments[1]}/${target.id}`);
            return;
        }
        if (segments[0] === "communityThemeInstalls") {
            installers.get(segments[1])?.delete(target.id);
            removed.installs.push(`${segments[1]}/${target.id}`);
            return;
        }
        if (segments[0] === "communityThemeReports") {
            reporters.get(segments[1])?.delete(target.id);
            removed.reports.push(`${segments[1]}/${target.id}`);
            return;
        }
        if (segments[0] === "communityThemeSubmissionQuota") {
            quotas.delete(target.id);
            removed.quotas.push(target.id);
            return;
        }
        if (segments[0] === "communityThemeAccountDeletion") {
            deletionRequests.delete(target.id);
            removed.requests.push(target.id);
            return;
        }
        if (segments[0] === "communityThemeAccounts") {
            accounts.delete(target.id);
            removed.accounts.push(target.id);
            return;
        }
        if (segments[0] === "communityThemeAuthorNames") {
            authorNames.delete(target.id);
            removed.authorNames.push(target.id);
            return;
        }
        if (segments[0] === "communityThemeDeletedAccounts" && operation === "set") {
            deletedAccounts.set(target.id, { ...value });
            return;
        }
        throw new Error(`Unexpected write to ${target.path}`);
    }

    const firestore = {
        collection(collectionName) {
            if (collectionName === "themeIntake") {
                return {
                    where(field, operator, value) {
                        if (field === "reviewedBy") {
                            // The self-terminating legacy migration: documents that still carry a
                            // reviewer identity on the submission itself.
                            assert.equal(operator, "!=");
                            assert.equal(value, null);
                            return intakeDocuments((document) =>
                                typeof document.data.reviewedBy === "string");
                        }
                        assert.equal(operator, "==");
                        if (field === "status") {
                            assert.ok(["approved", "withdrawn"].includes(value));
                            return intakeDocuments((document) => document.data.status === value);
                        }
                        assert.equal(field, "ownerUid");
                        return intakeDocuments((document) => document.data.ownerUid === value);
                    },
                    doc(id) {
                        return reference("themeIntake", id);
                    },
                };
            }
            if (collectionName === "communityThemeAccountDeletion") {
                return {
                    where(field, operator, value) {
                        assert.equal(field, "status");
                        assert.equal(operator, "==");
                        assert.equal(value, "pending");
                        return {
                            get: async () => ({
                                docs: [...deletionRequests.entries()]
                                    .filter(([, data]) => data.status === "pending")
                                    .map(([uid, data]) => ({ id: uid, data: () => data })),
                            }),
                        };
                    },
                    doc(uid) {
                        return reference("communityThemeAccountDeletion", uid);
                    },
                };
            }
            if (collectionName === "themeIntakeReview") {
                return {
                    doc(id) {
                        return reference("themeIntakeReview", id);
                    },
                };
            }
            if (collectionName === "themeIntakeShots") {
                return {
                    doc(themeId) {
                        return {
                            collection(surfaces) {
                                assert.equal(surfaces, "surfaces");
                                return {
                                    get: async () => {
                                        const docs = [...shots.entries()]
                                            .filter(([key]) => key.startsWith(`${themeId}/`))
                                            .sort(([left], [right]) => left < right ? -1 : 1)
                                            .map(([key, data]) => ({
                                                id: key.slice(themeId.length + 1),
                                                data: () => data,
                                                ref: {
                                                    id: key.slice(themeId.length + 1),
                                                    path: `themeIntakeShots/${themeId}/surfaces/${key.slice(themeId.length + 1)}`,
                                                },
                                            }));
                                        return { empty: docs.length === 0, docs };
                                    },
                                };
                            },
                        };
                    },
                };
            }
            if (collectionName === "communityThemeSubmissionQuota") {
                return {
                    doc(uid) {
                        return reference("communityThemeSubmissionQuota", uid);
                    },
                };
            }
            if (collectionName === "communityThemeAccounts") {
                return {
                    doc(uid) {
                        return reference("communityThemeAccounts", uid);
                    },
                };
            }
            if (collectionName === "communityThemeAuthorNames") {
                return {
                    doc(authorKey) {
                        return reference("communityThemeAuthorNames", authorKey);
                    },
                };
            }
            if (collectionName === "communityThemeDeletedAccounts") {
                return {
                    doc(uid) {
                        return reference("communityThemeDeletedAccounts", uid);
                    },
                };
            }
            // The three per-theme ledgers are the same shape: one document per account under a
            // fixed subcollection, counted by aggregate and listed for withdrawal cleanup.
            const ledgers = {
                communityThemeLikes: { sub: "voters", members: voters, counts: likes },
                communityThemeInstalls: { sub: "installers", members: installers, counts: installs },
                communityThemeReports: { sub: "themeReporters", members: reporters, counts: new Map() },
            };
            if (Object.hasOwn(ledgers, collectionName)) {
                const { sub, members, counts } = ledgers[collectionName];
                return {
                    doc(themeId) {
                        return {
                            collection(subcollectionName) {
                                assert.equal(subcollectionName, sub);
                                const pathFor = (uid) => `${collectionName}/${themeId}/${sub}/${uid}`;
                                return {
                                    count() {
                                        return {
                                            get: async () => ({
                                                data: () => ({
                                                    count: counts.get(themeId) ?? members.get(themeId)?.size ?? 0,
                                                }),
                                            }),
                                        };
                                    },
                                    async listDocuments() {
                                        return [...(members.get(themeId) ?? [])].sort().map((uid) => ({
                                            id: uid,
                                            path: pathFor(uid),
                                        }));
                                    },
                                    doc(uid) {
                                        return { id: uid, path: pathFor(uid) };
                                    },
                                };
                            },
                        };
                    },
                };
            }
            assert.equal(collectionName, "communityThemePublished");
            return {
                doc(id) {
                    return reference("communityThemePublished", id);
                },
            };
        },
        async getAll(...references) {
            return references.map(snapshotFor);
        },
        batch() {
            const writes = [];
            return {
                set(target, value) {
                    writes.push(() => applyWrite(target, "set", value));
                },
                update(target, value) {
                    writes.push(() => applyWrite(target, "update", value));
                },
                delete(target) {
                    writes.push(() => applyWrite(target, "delete"));
                },
                async commit() {
                    writes.forEach((write) => write());
                },
            };
        },
        async runTransaction(callback) {
            const transaction = {
                get: async (target) => snapshotFor(target),
                update(target, values) {
                    Object.assign(byId.get(target.id).data, values);
                },
            };
            return callback(transaction);
        },
    };
    return Object.assign(firestore, {
        publishedThemeMarkers, deletionRequests, voters, installers, reporters, quotas, reviews,
        removed, byId, accounts, authorNames, deletedAccounts, events,
    });
}

/** Records the identities a finalization asks Firebase Auth to delete. */
function fakeAuth(missingUids = [], events = []) {
    const deletedUids = [];
    const missing = new Set(missingUids);
    return {
        deletedUids,
        async deleteUser(uid) {
            events.push(`auth:delete:${uid}`);
            if (missing.has(uid)) {
                const error = new Error("no user record");
                error.code = "auth/user-not-found";
                throw error;
            }
            deletedUids.push(uid);
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

test("individual title and artist fonts retain their Flex contracts", () => {
    const settings = defaultSettingsForFace("poster");
    const targets = [
        { prefix: "wear_title_font", visible: "wear_show_track_title" },
        { prefix: "wear_artist_font", visible: "wear_show_track_artist" },
    ];
    const axisBounds = {
        flex_width: [25, 151],
        flex_optical_size: [6, 144],
        flex_grade: [0, 100],
        flex_roundness: [0, 100],
    };

    for (const { prefix, visible } of targets) {
        assert.equal(SETTING_TYPES[prefix], "string");
        assert.equal(settings[prefix].value, "follow");
        assert.equal(isOriginalityApplicableSetting(prefix, settings, "poster"), true);

        for (const [axis, [min, max]] of Object.entries(axisBounds)) {
            const key = `${prefix}_${axis}`;
            assert.equal(SETTING_TYPES[key], "int");
            assert.deepEqual(COMMUNITY_THEME_CONSTRAINTS.settings[key], { type: "int", min, max });
            assert.equal(isOriginalityApplicableSetting(key, settings, "poster"), false);
        }

        settings[prefix].value = "google_sans_flex";
        for (const axis of Object.keys(axisBounds)) {
            assert.equal(isOriginalityApplicableSetting(`${prefix}_${axis}`, settings, "poster"), true);
        }

        settings[visible].value = false;
        assert.equal(isOriginalityApplicableSetting(prefix, settings, "poster"), false);
        for (const axis of Object.keys(axisBounds)) {
            assert.equal(isOriginalityApplicableSetting(`${prefix}_${axis}`, settings, "poster"), false);
        }
    }
});

/*
 * The published vocabulary and the app version able to read it are one decision, not two.
 *
 * Every published profile is materialized complete -- all of SETTING_KEYS, whatever its author
 * actually changed -- and the phone refuses one it does not fully recognise: first on the key
 * count exceeding its own registry, and again on the first key it has never heard of. So the run
 * after this list grows publishes themes no older build can install, and minimumAppVersion is the
 * only thing that makes the gallery say "requires a newer app" instead of offering an Add button
 * that fails with nothing on screen naming a cause.
 *
 * Nothing else ties the two together: the constant is hand-written in schema.mjs while the
 * vocabulary grows in Kotlin, a module and a language away. That gap has already been crossed
 * once, in the direction that costs nothing only because no release had gone out yet. If this
 * fails because you added a setting, raise MINIMUM_APP_VERSION to the version that ships it --
 * updating the count alone is the change this test exists to refuse.
 */
test("the published vocabulary is pinned to the app version that can read it", () => {
    assert.deepEqual(
        { settings: SETTING_KEYS.length, minimumAppVersion: MINIMUM_APP_VERSION },
        { settings: 156, minimumAppVersion: "3.3" },
    );
});

test("a complete approved intake creates a preview-free public profile", () => {
    const candidate = validateApprovedDocument(approvedDocument());

    assert.equal(SETTING_KEYS.length, 156);
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
    assert.equal(index.themes[0].likes, 0);
    assert.equal(firestore.publishedThemeMarkers.size, 0);

    const result = await finalizePublishedThemes({ firestore, root, manifestPath, logger });
    assert.deepEqual(result, { marked: 1, skipped: 0, erased: 0, withdrawn: 0, migratedReviewers: 0 });
    assert.equal(document.data.status, "published");
    assert.ok(document.data.publishedAt);
    assert.deepEqual(firestore.publishedThemeMarkers.get(ID), {
        schemaVersion: 1,
        revision: 1,
        publishedAt: "2026-08-24T12:00:00.000Z",
    });
});

test("a schema-v2 named submission requires its immutable account profile", async (t) => {
    const root = await temporaryCatalogue(t);
    const document = approvedDocument();
    const firestore = fakeFirestore(document, {}, { accounts: {} });

    const plan = await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger: { log() {}, warn() {} },
        publish: true,
        manifestPath: join(root, "missing-author-account-manifest.json"),
    });

    assert.equal(plan.plans.length, 0);
    assert.equal(document.data.status, "rejected");
    assert.equal(document.data.publicationFailure, "invalid-author-identity");
});

test("a schema-v2 name reservation owned by another account is rejected", async (t) => {
    const root = await temporaryCatalogue(t);
    const document = approvedDocument();
    const authorKey = "v1:theme maker";
    const firestore = fakeFirestore(document, {}, {
        authorNames: {
            [authorKey]: {
                ownerUid: "different-user",
                nameSchemaVersion: 1,
                authorName: "Theme maker",
                authorKey,
                createdAt: timestamp(),
            },
        },
    });

    await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger: { log() {}, warn() {} },
        publish: true,
        manifestPath: join(root, "foreign-author-claim-manifest.json"),
    });

    assert.equal(document.data.status, "rejected");
    assert.equal(document.data.publicationFailure, "invalid-author-identity");
});

test("schema-v2 Anonymous and legacy schema-v1 submissions need no name reservation", async (t) => {
    const anonymousRoot = await temporaryCatalogue(t);
    const anonymous = approvedDocument({ author: "Anonymous" });
    const anonymousPlan = await publishApprovedThemes({
        firestore: fakeFirestore(anonymous, {}, { accounts: {}, authorNames: {} }),
        root: anonymousRoot,
        now: new Date("2026-08-24T12:00:00Z"),
        logger: { log() {}, warn() {} },
    });
    assert.equal(anonymousPlan.plans.length, 1);

    const legacyRoot = await temporaryCatalogue(t);
    const legacy = approvedDocument({ submissionSchemaVersion: 1 });
    const legacyPlan = await publishApprovedThemes({
        firestore: fakeFirestore(legacy, {}, { accounts: {}, authorNames: {} }),
        root: legacyRoot,
        now: new Date("2026-08-24T12:00:00Z"),
        logger: { log() {}, warn() {} },
    });
    assert.equal(legacyPlan.plans.length, 1);
});

test("finalization rechecks the author reservation after the static commit", async (t) => {
    const root = await temporaryCatalogue(t);
    const document = approvedDocument();
    const firestore = fakeFirestore(document);
    const manifestPath = join(root, "identity-race-manifest.json");
    const logger = { log() {}, warn() {} };
    await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger,
        publish: true,
        manifestPath,
    });
    firestore.authorNames.get("v1:theme maker").ownerUid = "different-user";

    const result = await finalizePublishedThemes({ firestore, root, manifestPath, logger });

    assert.equal(result.marked, 0);
    assert.equal(result.skipped, 1);
    assert.equal(document.data.status, "approved");
});

test("finalization registers a static catalogue theme with no intake document", async (t) => {
    const root = await temporaryCatalogue(t);
    const candidate = validateApprovedDocument(approvedDocument());
    await writeFile(
        join(root, "docs", "themes", `${ID}.json`),
        `${JSON.stringify(candidate.publicProfile, null, 2)}\n`,
    );
    await writeFile(join(root, "docs", "themes", "index.json"), `${JSON.stringify({
        schemaVersion: 1,
        generatedAt: PUBLISHED_AT,
        themes: [candidate.summary],
    }, null, 2)}\n`);
    const manifestPath = join(root, "empty-manifest.json");
    await writeFile(manifestPath, `${JSON.stringify({ schemaVersion: 2, candidates: [], erasures: [], withdrawals: [] })}\n`);
    const firestore = fakeFirestore([]);

    assert.deepEqual(
        await finalizePublishedThemes({ firestore, root, manifestPath, logger: { log() {}, warn() {} } }),
        { marked: 0, skipped: 0, erased: 0, withdrawn: 0, migratedReviewers: 0 },
    );
    assert.deepEqual(firestore.publishedThemeMarkers.get(ID), {
        schemaVersion: 1,
        revision: 1,
        publishedAt: PUBLISHED_AT,
    });
});

test("finalization never registers an index entry whose static profile is missing", async (t) => {
    const root = await temporaryCatalogue(t);
    const candidate = validateApprovedDocument(approvedDocument());
    await writeFile(join(root, "docs", "themes", "index.json"), `${JSON.stringify({
        schemaVersion: 1,
        generatedAt: PUBLISHED_AT,
        themes: [candidate.summary],
    }, null, 2)}\n`);
    const manifestPath = join(root, "empty-manifest.json");
    await writeFile(manifestPath, `${JSON.stringify({ schemaVersion: 2, candidates: [], erasures: [], withdrawals: [] })}\n`);
    const firestore = fakeFirestore([]);

    await assert.rejects(
        () => finalizePublishedThemes({ firestore, root, manifestPath, logger: { log() {}, warn() {} } }),
        (error) => error instanceof ValidationError && error.code === "catalogue-profile-is-missing",
    );
    assert.equal(firestore.publishedThemeMarkers.size, 0);
});

test("the publisher derives catalogue likes from private voter documents", async (t) => {
    const root = await temporaryCatalogue(t);
    const document = approvedDocument();
    const logger = { log() {}, warn() {} };
    const firstManifest = join(root, "first-manifest.json");
    const firstFirestore = fakeFirestore(document);

    await publishApprovedThemes({
        firestore: firstFirestore,
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: firstManifest,
    });
    await finalizePublishedThemes({ firestore: firstFirestore, root, manifestPath: firstManifest, logger });

    const staleIndex = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    staleIndex.themes[0].likes = 999;
    await writeFile(join(root, "docs", "themes", "index.json"), `${JSON.stringify(staleIndex, null, 2)}\n`);
    const profileBeforeRefresh = await readFile(join(root, "docs", "themes", `${ID}.json`), "utf8");
    const staleFile = await readFile(join(root, "docs", "themes", "index.json"), "utf8");

    // Hours later the number is wrong and known to be wrong, and is still left alone: a run that
    // fires again the same day should not commit a second time to move a popularity count.
    const deferred = await publishApprovedThemes({
        firestore: fakeFirestore(document, { [ID]: 7 }),
        root,
        now: new Date("2026-08-24T20:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "deferred-likes-manifest.json"),
    });
    assert.equal(deferred.catalogChanged, false);
    assert.equal(await readFile(join(root, "docs", "themes", "index.json"), "utf8"), staleFile);

    // The next daily run publishes it. Holding a moved count for longer than the gap between runs
    // is what made the public figures look like they had stopped counting altogether.
    const refresh = await publishApprovedThemes({
        firestore: fakeFirestore(document, { [ID]: 7 }),
        root,
        now: new Date("2026-08-25T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "likes-manifest.json"),
    });

    assert.equal(refresh.plans.length, 0);
    assert.equal(refresh.catalogChanged, true);
    const refreshedIndex = await readFile(join(root, "docs", "themes", "index.json"), "utf8");
    assert.equal(JSON.parse(refreshedIndex).themes[0].likes, 7);
    assert.equal(await readFile(join(root, "docs", "themes", `${ID}.json`), "utf8"), profileBeforeRefresh);
    assert.equal(document.data.status, "published");

    const steady = await publishApprovedThemes({
        firestore: fakeFirestore(document, { [ID]: 7 }),
        root,
        now: new Date("2026-09-09T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "steady-likes-manifest.json"),
    });
    assert.equal(steady.catalogChanged, false);
    assert.equal(await readFile(join(root, "docs", "themes", "index.json"), "utf8"), refreshedIndex);
});

test("a catalogue entry with no like count at all never waits for the refresh interval", async (t) => {
    // This is the state a catalogue published before like aggregation existed is in. The app reads
    // the absent field as zero, so deferring it would leave every theme showing 0 for a week.
    const root = await temporaryCatalogue(t);
    const document = approvedDocument();
    const logger = { log() {}, warn() {} };
    const seedManifest = join(root, "seed-manifest.json");
    await publishApprovedThemes({
        firestore: fakeFirestore(document),
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: seedManifest,
    });

    const index = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    delete index.themes[0].likes;
    await writeFile(join(root, "docs", "themes", "index.json"), `${JSON.stringify(index, null, 2)}\n`);

    const result = await publishApprovedThemes({
        firestore: fakeFirestore(document, { [ID]: 4 }),
        root,
        now: new Date("2026-08-24T18:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "missing-likes-manifest.json"),
    });

    assert.equal(result.catalogChanged, true);
    const written = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    assert.equal(written.themes[0].likes, 4);
});

test("the publisher derives catalogue installs from private installer documents", async (t) => {
    const root = await temporaryCatalogue(t);
    const document = approvedDocument();
    const logger = { log() {}, warn() {} };
    const seedManifest = join(root, "seed-manifest.json");
    const seedFirestore = fakeFirestore(document);
    await publishApprovedThemes({
        firestore: seedFirestore,
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: seedManifest,
    });
    await finalizePublishedThemes({ firestore: seedFirestore, root, manifestPath: seedManifest, logger });

    const seeded = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    assert.equal(seeded.themes[0].installs, 0);
    const seededFile = await readFile(join(root, "docs", "themes", "index.json"), "utf8");

    // Installs ride the like counts' deferral rather than carrying a clock of their own: two
    // intervals would mean the two numbers on one card were last correct at different moments.
    const deferred = await publishApprovedThemes({
        firestore: fakeFirestore(document, {}, { installs: { [ID]: 12 } }),
        root,
        now: new Date("2026-08-24T20:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "deferred-installs-manifest.json"),
    });
    assert.equal(deferred.catalogChanged, false);
    assert.equal(await readFile(join(root, "docs", "themes", "index.json"), "utf8"), seededFile);

    const refresh = await publishApprovedThemes({
        firestore: fakeFirestore(document, {}, { installs: { [ID]: 12 } }),
        root,
        now: new Date("2026-08-25T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "installs-manifest.json"),
    });
    assert.equal(refresh.catalogChanged, true);
    const refreshed = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    assert.equal(refreshed.themes[0].installs, 12);
    assert.equal(refreshed.themes[0].likes, 0);
});

test("a catalogue entry with no install count at all never waits for the refresh interval", async (t) => {
    // Exactly the state every already-published catalogue is in the first time this ships. The app
    // reads the absent field as zero, so deferring would show 0 downloads everywhere until the
    // window elapsed.
    const root = await temporaryCatalogue(t);
    const document = approvedDocument();
    const logger = { log() {}, warn() {} };
    await publishApprovedThemes({
        firestore: fakeFirestore(document),
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "seed-manifest.json"),
    });

    const index = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    delete index.themes[0].installs;
    await writeFile(join(root, "docs", "themes", "index.json"), `${JSON.stringify(index, null, 2)}\n`);

    const result = await publishApprovedThemes({
        firestore: fakeFirestore(document, {}, { installs: { [ID]: 5 } }),
        root,
        now: new Date("2026-08-24T18:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "missing-installs-manifest.json"),
    });

    assert.equal(result.catalogChanged, true);
    const written = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    assert.equal(written.themes[0].installs, 5);
});

test("a publication carries fresh like counts even inside the refresh interval", async (t) => {
    const root = await temporaryCatalogue(t);
    const first = approvedDocument();
    const logger = { log() {}, warn() {} };
    await publishApprovedThemes({
        firestore: fakeFirestore(first, { [ID]: 3 }),
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "first-manifest.json"),
    });

    // The file is being rewritten for the second theme anyway, so the counts ride along free.
    const second = approvedDocument({}, SECOND_ID);
    const secondProfile = JSON.parse(second.data.profileJson);
    secondProfile.settings.wear_color_modifier.value = "warm";
    second.data.profileJson = JSON.stringify(secondProfile);
    second.data.settingsDigest = canonicalSettingsDigest(secondProfile.baseFace, secondProfile.settings);
    const result = await publishApprovedThemes({
        firestore: fakeFirestore([first, second], { [ID]: 11, [SECOND_ID]: 0 }),
        root,
        now: new Date("2026-08-24T20:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "second-manifest.json"),
    });

    assert.equal(result.catalogChanged, true);
    const written = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    assert.equal(written.themes.find((entry) => entry.id === ID).likes, 11);
});

test("the like refresh interval is measured from the catalogue's own timestamp", () => {
    const week = 7 * 24 * 60 * 60 * 1000;
    // The default window is shorter than the cron period on purpose: a count that moves has to be
    // published by the next daily run, not held back for another whole day by a run that fires a
    // few minutes earlier than the one that wrote the catalogue.
    assert.equal(isLikeRefreshDue("2026-08-24T12:00:00Z", "2026-08-24T20:00:00Z"), false);
    assert.equal(isLikeRefreshDue("2026-08-24T12:00:00Z", "2026-08-25T00:00:00Z"), true);
    assert.equal(isLikeRefreshDue("2026-08-24T12:00:00Z", "2026-08-25T11:50:00Z"), true);
    assert.equal(isLikeRefreshDue("2026-08-24T12:00:00Z", "2026-08-25T12:00:00Z", week), false);
    // A clock that ran backwards, or a timestamp that cannot be read, refreshes rather than
    // stalling until real time catches up with a window it cannot measure.
    assert.equal(isLikeRefreshDue("2026-08-24T12:00:00Z", "2026-08-20T12:00:00Z"), true);
    assert.equal(isLikeRefreshDue("not a date", "2026-08-25T12:00:00Z"), true);
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

function deletionRequest(uid, themeDisposition, overrides = {}) {
    return {
        ownerUid: uid,
        requestSchemaVersion: 1,
        status: "pending",
        themeDisposition,
        clientVersion: "3.3",
        createdAt: timestamp(),
        ...overrides,
    };
}

/** Publishes one theme and marks it public, which is the starting point every erasure needs. */
async function publishOneTheme(
    t,
    { uid = "firebase-user-id", voters = {}, installers = {}, reporters = {} } = {},
) {
    const root = await temporaryCatalogue(t);
    const document = approvedDocument({ ownerUid: uid });
    const firestore = fakeFirestore(document, {}, { voters, installers, reporters, quotas: [uid] });
    const manifestPath = join(root, "seed-manifest.json");
    const logger = { log() {}, warn() {} };
    await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger,
        publish: true,
        manifestPath,
    });
    await finalizePublishedThemes({ firestore, auth: fakeAuth(), root, manifestPath, logger });
    return { root, document, firestore, logger };
}

test("deleting an account keeps its published themes when the author chose to keep them", async (t) => {
    const uid = "keeping-user";
    const { root, document, firestore, logger } = await publishOneTheme(t, {
        uid,
        voters: { [ID]: [uid, "someone-else"] },
        // An erased account's own download and report records go with its votes; other people's
        // stay, because they are somebody else's data about a theme that is still public.
        installers: { [ID]: [uid, "someone-else"] },
        reporters: { [ID]: [uid, "someone-else"] },
    });
    firestore.deletionRequests.set(uid, deletionRequest(uid, "keep"));
    const manifestPath = join(root, "keep-manifest.json");
    const auth = fakeAuth([], firestore.events);

    const plan = await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-25T12:00:00Z"),
        logger,
        publish: true,
        manifestPath,
    });
    assert.deepEqual(plan.removedThemeIds, []);

    // The public theme and the vote another person left on it both survive untouched.
    const index = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    assert.deepEqual(index.themes.map((entry) => entry.id), [ID]);
    assert.equal(index.themes[0].author, "Theme maker");

    const result = await finalizePublishedThemes({ firestore, auth, root, manifestPath, logger });
    assert.equal(result.erased, 1);
    assert.equal(document.data.status, "published");
    assert.equal(document.data.ownerUid, "account-erased");
    assert.ok(document.data.ownerErasedAt);
    assert.deepEqual(auth.deletedUids, [uid]);
    assert.deepEqual([...firestore.voters.get(ID)], ["someone-else"]);
    assert.deepEqual([...firestore.installers.get(ID)], ["someone-else"]);
    assert.deepEqual([...firestore.reporters.get(ID)], ["someone-else"]);
    assert.equal(firestore.quotas.size, 0);
    assert.equal(firestore.deletionRequests.size, 0);
    assert.equal(firestore.accounts.size, 0);
    assert.equal(firestore.authorNames.size, 0);
    assert.equal(firestore.deletedAccounts.get(uid).schemaVersion, 1);
    assert.equal(typeof firestore.deletedAccounts.get(uid).expiresAt.toMillis, "function");
    assert.ok(firestore.publishedThemeMarkers.has(ID));
    const authDelete = firestore.events.lastIndexOf(`auth:delete:${uid}`);
    assert.ok(authDelete >= 0);
    assert.ok(firestore.events.lastIndexOf(`delete:communityThemeAccounts/${uid}`) > authDelete);
    assert.ok(firestore.events.lastIndexOf("delete:communityThemeAuthorNames/v1:theme maker") > authDelete);
    assert.ok(firestore.events.lastIndexOf(`delete:communityThemeAccountDeletion/${uid}`) > authDelete);
    assert.ok(firestore.events.lastIndexOf(`set:communityThemeDeletedAccounts/${uid}`) > authDelete);
});

test("deleting an account withdraws its published themes when the author chose to delete them", async (t) => {
    const uid = "withdrawing-user";
    const { root, firestore, logger } = await publishOneTheme(t, {
        uid,
        voters: { [ID]: [uid, "someone-else"] },
    });
    firestore.deletionRequests.set(uid, deletionRequest(uid, "delete"));
    const manifestPath = join(root, "delete-manifest.json");
    const auth = fakeAuth();

    const plan = await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-25T12:00:00Z"),
        logger,
        publish: true,
        manifestPath,
    });
    assert.deepEqual(plan.removedThemeIds, [ID]);

    // Withdrawal is a Git change: the profile file and its index row are gone before Firestore is
    // touched at all, which is the same ordering a publication uses.
    await assert.rejects(readFile(join(root, "docs", "themes", `${ID}.json`), "utf8"));
    const index = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    assert.deepEqual(index.themes, []);

    const result = await finalizePublishedThemes({ firestore, auth, root, manifestPath, logger });
    assert.equal(result.erased, 1);
    assert.equal(firestore.byId.has(ID), false);
    assert.deepEqual(firestore.removed.markers, [ID]);
    // Every vote on a withdrawn theme goes, not only the erasing account's own.
    assert.deepEqual([...firestore.voters.get(ID)], []);
    assert.deepEqual(auth.deletedUids, [uid]);
    assert.equal(firestore.deletionRequests.size, 0);
    assert.equal(firestore.accounts.size, 0);
    assert.equal(firestore.authorNames.size, 0);
    assert.ok(firestore.deletedAccounts.has(uid));
});

test("an erasing account cannot have a theme published in the same run", async (t) => {
    const root = await temporaryCatalogue(t);
    const uid = "leaving-user";
    const document = approvedDocument({ ownerUid: uid });
    const firestore = fakeFirestore(document, {}, {
        deletionRequests: { [uid]: deletionRequest(uid, "keep") },
    });
    const manifestPath = join(root, "race-manifest.json");
    const logger = { log() {}, warn() {} };

    const plan = await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger,
        publish: true,
        manifestPath,
    });

    assert.deepEqual(plan.plans, []);
    assert.equal(plan.skipped, 1);
    const index = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    assert.deepEqual(index.themes, []);

    // Nothing was public, so "keep my themes" has nothing to keep and the queue is cleared.
    await finalizePublishedThemes({ firestore, auth: fakeAuth(), root, manifestPath, logger });
    assert.equal(firestore.byId.has(ID), false);
});

test("a resumed erasure tolerates an identity Firebase has already deleted", async (t) => {
    const uid = "half-erased-user";
    const { root, firestore, logger } = await publishOneTheme(t, { uid });
    firestore.deletionRequests.set(uid, deletionRequest(uid, "keep"));
    const manifestPath = join(root, "resume-manifest.json");

    await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-25T12:00:00Z"),
        logger,
        publish: true,
        manifestPath,
    });
    const auth = fakeAuth([uid], firestore.events);
    const result = await finalizePublishedThemes({ firestore, auth, root, manifestPath, logger });

    assert.equal(result.erased, 1);
    assert.deepEqual(auth.deletedUids, []);
    assert.equal(firestore.deletionRequests.size, 0);
    assert.equal(firestore.accounts.size, 0);
    assert.equal(firestore.authorNames.size, 0);
    assert.ok(firestore.deletedAccounts.has(uid));
});

test("an erasure that cannot delete the identity stays pending and fails the run", async (t) => {
    const uid = "stuck-user";
    const { root, firestore, logger } = await publishOneTheme(t, { uid });
    firestore.deletionRequests.set(uid, deletionRequest(uid, "keep"));
    const manifestPath = join(root, "stuck-manifest.json");

    await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-25T12:00:00Z"),
        logger,
        publish: true,
        manifestPath,
    });
    const auth = {
        async deleteUser() {
            throw new Error("identity toolkit unavailable");
        },
    };

    await assert.rejects(
        finalizePublishedThemes({
            firestore,
            auth,
            root,
            manifestPath,
            logger: { log() {}, warn() {}, error() {} },
        }),
        /Could not complete 1 community-account erasure/,
    );
    assert.equal(firestore.deletionRequests.size, 1);
    assert.equal(firestore.accounts.size, 1);
    assert.equal(firestore.authorNames.size, 1);
    assert.equal(firestore.deletedAccounts.size, 0);
});

test("a malformed erasure request is skipped rather than guessed at", async (t) => {
    const uid = "tampered-user";
    const { root, firestore, logger } = await publishOneTheme(t, { uid });
    firestore.deletionRequests.set(uid, deletionRequest(uid, "everything"));
    const manifestPath = join(root, "malformed-manifest.json");

    const plan = await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-25T12:00:00Z"),
        logger,
        publish: true,
        manifestPath,
    });

    assert.deepEqual(plan.erasures, []);
    assert.equal(plan.erasuresSkipped, 1);
    const result = await finalizePublishedThemes({ firestore, auth: fakeAuth(), root, manifestPath, logger });
    assert.equal(result.erased, 0);
    assert.equal(firestore.deletionRequests.size, 1);
});

test("a manifest cannot withdraw a theme the erasing account never submitted", () => {
    assertValidationCode(
        () => validateManifest({
            schemaVersion: 2,
            candidates: [],
            withdrawals: [],
            erasures: [{
                uid: "attacker",
                themeDisposition: "delete",
                intakeIds: [],
                removedThemeIds: [SECOND_ID],
                keptThemeIds: [],
            }],
        }),
        "unowned-publication-manifest-erasure-id",
    );
});

test("a published entry carries the digest a phone needs to refuse a duplicate", async (t) => {
    const root = await temporaryCatalogue(t);
    const document = approvedDocument();
    const logger = { log() {}, warn() {} };
    await publishApprovedThemes({
        firestore: fakeFirestore(document),
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "digest-manifest.json"),
    });

    const index = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    assert.equal(index.themes[0].settingsDigest, document.data.settingsDigest);
});

test("a catalogue written before the digest field is upgraded without waiting a week", async (t) => {
    // The same carve-out a missing like count gets, and for a sharper reason: without the digest
    // the phone cannot check a duplicate at all, so deferring would disable the check for a week.
    const root = await temporaryCatalogue(t);
    const document = approvedDocument();
    const logger = { log() {}, warn() {} };
    await publishApprovedThemes({
        firestore: fakeFirestore(document),
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "seed-manifest.json"),
    });

    const index = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    delete index.themes[0].settingsDigest;
    await writeFile(join(root, "docs", "themes", "index.json"), `${JSON.stringify(index, null, 2)}\n`);

    const result = await publishApprovedThemes({
        firestore: fakeFirestore(document),
        root,
        now: new Date("2026-08-24T18:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "upgrade-manifest.json"),
    });

    assert.equal(result.catalogChanged, true);
    const upgraded = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    // Recomputed from the published profile, not copied from anything a client ever sent.
    assert.equal(upgraded.themes[0].settingsDigest, document.data.settingsDigest);
});

test("a moderator withdrawal removes the public files and then every Firestore trace", async (t) => {
    const uid = "withdrawn-theme-owner";
    const { root, firestore, logger, document } = await publishOneTheme(t, {
        uid,
        voters: { [ID]: ["someone", "someone-else"] },
        installers: { [ID]: ["someone", "a-third-person"] },
        reporters: { [ID]: ["the-person-who-flagged-it"] },
    });
    // What a moderator's batch leaves behind for the publisher to act on.
    document.data.status = "withdrawn";
    firestore.reviews.set(ID, { reviewSchemaVersion: 1, reviewedBy: "moderator", decision: "withdrawn" });
    const manifestPath = join(root, "withdrawal-manifest.json");

    const plan = await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-25T12:00:00Z"),
        logger,
        publish: true,
        manifestPath,
    });

    assert.deepEqual(plan.withdrawals, [ID]);
    await assert.rejects(readFile(join(root, "docs", "themes", `${ID}.json`), "utf8"));
    assert.deepEqual(
        JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8")).themes,
        [],
    );

    const result = await finalizePublishedThemes({ firestore, auth: fakeAuth(), root, manifestPath, logger });
    assert.equal(result.withdrawn, 1);
    assert.equal(firestore.byId.has(ID), false);
    assert.deepEqual(firestore.removed.markers, [ID]);
    assert.deepEqual(firestore.removed.reviews, [ID]);
    assert.deepEqual([...firestore.voters.get(ID)], []);
    assert.deepEqual([...firestore.installers.get(ID)], []);
    // The reports go with the listing. Once it is gone the only thing they still record is who
    // complained about what, which is not a record worth keeping about a person.
    assert.deepEqual([...firestore.reporters.get(ID)], []);
});

test("a withdrawn submission is never published by the run that withdraws it", async (t) => {
    const root = await temporaryCatalogue(t);
    const document = approvedDocument();
    // Approved earlier, withdrawn before the publisher next ran.
    const withdrawn = { ...document, data: { ...document.data, status: "withdrawn" } };
    const firestore = fakeFirestore(withdrawn);
    const logger = { log() {}, warn() {} };

    const plan = await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "race-withdrawal-manifest.json"),
    });

    assert.deepEqual(plan.plans, []);
    assert.deepEqual(plan.withdrawals, [ID]);
    assert.deepEqual(
        JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8")).themes,
        [],
    );
});

test("a legacy reviewer identity is moved off the submission it decided", async (t) => {
    // Until it moves, an author reading their own decided submission would read the reviewer's UID
    // along with it, which is the whole reason that read used to be limited to pending themes.
    const root = await temporaryCatalogue(t);
    const document = approvedDocument({ status: "published", reviewedBy: "legacy-moderator-uid" });
    const firestore = fakeFirestore(document);
    const manifestPath = join(root, "legacy-manifest.json");
    await writeFile(manifestPath, `${JSON.stringify({
        schemaVersion: 2, candidates: [], erasures: [], withdrawals: [],
    })}\n`);

    const result = await finalizePublishedThemes({
        firestore,
        auth: fakeAuth(),
        root,
        manifestPath,
        logger: { log() {}, warn() {} },
    });

    assert.equal(result.migratedReviewers, 1);
    assert.equal(Object.hasOwn(document.data, "reviewedBy"), false);
    assert.equal(firestore.reviews.get(ID).reviewedBy, "legacy-moderator-uid");
    assert.equal(firestore.reviews.get(ID).decision, "approved");

    // Self-terminating: nothing carries the field any more, so a second run moves nothing.
    const again = await finalizePublishedThemes({
        firestore,
        auth: fakeAuth(),
        root,
        manifestPath,
        logger: { log() {}, warn() {} },
    });
    assert.equal(again.migratedReviewers, 0);
});

/*
 * Author screenshots.
 *
 * The container tests build their own RIFF bytes rather than embedding one blob per case: what is
 * under test is the parser's reading of a container, and hand-built chunks state each case exactly
 * -- an EXIF chunk present while the flag claiming it is clear cannot be expressed any other way.
 * REAL_WEBP is the one genuine encoder output, there to prove the parser accepts what libwebp
 * actually produces and not only what this file thinks it produces.
 */
function riffChunk(fourCC, payload) {
    const header = Buffer.alloc(8);
    header.write(fourCC, 0, "latin1");
    header.writeUInt32LE(payload.length, 4);
    const padding = Buffer.alloc(payload.length % 2);
    return Buffer.concat([header, payload, padding]);
}

function riff(chunks) {
    const body = Buffer.concat([Buffer.from("WEBP", "latin1"), ...chunks]);
    const header = Buffer.alloc(8);
    header.write("RIFF", 0, "latin1");
    header.writeUInt32LE(body.length, 4);
    return Buffer.concat([header, body]);
}

/** A minimal VP8 key-frame header: the ten bytes the dimension read actually looks at. */
function vp8Frame(width, height) {
    const payload = Buffer.alloc(16);
    payload[0] = 0x50;
    payload[3] = 0x9d;
    payload[4] = 0x01;
    payload[5] = 0x2a;
    payload.writeUInt16LE(width, 6);
    payload.writeUInt16LE(height, 8);
    return payload;
}

/** A minimal VP8L header: the signature byte then two packed 14-bit fields. */
function vp8lFrame(width, height) {
    const payload = Buffer.alloc(8);
    payload[0] = 0x2f;
    payload.writeUInt32LE(((width - 1) & 0x3fff) | (((height - 1) & 0x3fff) << 14), 1);
    return payload;
}

function vp8xHeader(flags, width, height) {
    const payload = Buffer.alloc(10);
    payload[0] = flags;
    payload.writeUIntLE(width - 1, 4, 3);
    payload.writeUIntLE(height - 1, 7, 3);
    return payload;
}

function screenshot(width = 256, height = width) {
    return riff([riffChunk("VP8 ", vp8Frame(width, height))]).toString("base64");
}

/** 128x128, quality 70, straight out of libwebp. */
const REAL_WEBP =
    "UklGRkIDAABXRUJQVlA4IDYDAABQFQCdASqAAIAAPp1KnkqlpKKhqpzoALATiUAaPr0v4z+V66t1z8jv4BzL2ung" +
    "bnkrP5wH5J9FXpAeYDoE+bl/Zusz9ADy4PY+8qPNIJqisBlgcUnetHN/7DxKagfRzI8f////6jU4OCLUKp7Oa/v6" +
    "jRJ/BTb+MliToqhqt8x3d+y1oeDtfpcBPdNJqeqkkBLPSr4dZ2OnF/WBvj7zrpD9x9UaVirdXLGCiOJC+8738QyI" +
    "AAD+8iI/ZEVryBggv5mXx5xkgOxXzp0T3RSIJq8N+5vegbaLv11uDlze1HCXYCHa0o4uRXTOR1suBTR0cUyqTKSf" +
    "tzA2aLfiDc//+T+hyDWwB/mweewKbTUaMrIAL6qjv63+Hacug+71U5vIo7U4r0fB3KcEkcARMTfUHenyin+OyY++" +
    "rfyAlEEP6GRiCzxZQJ7hYerqjjYF61l7rP88FO1WjneAgfPTA2hf1q03N4Xib7mJgxLU1IgOOedU69yXAeGmYiEq" +
    "yPclh9QuH0AC6TrD1hhONM0EA/Y2R/JY9lsWHVe+HTjOTbSccACohkddfwoPwCDqShGnqrMJAGHur89j0T3gHm7m" +
    "wCSFRGHABX3a0/J/Q5BxqLKAUFn+v3ql/Hij+yMNxdBLrAWJbMev76MeMcmZ06H7l0eCU4kHYq0pvWAD85sHY1cR" +
    "Dhtnrc3HHSpMRBGEHVkVa7LNqdQgv1KGl9RXuHCvTfBQLHgoWg+WPFdx4DfJn/CyigsgGuaKWMFYkzWa8Q6G2ykE" +
    "k3OVcGRMt7yZQ3qeK7Dz3KK78F0VD2xMLyK0s52Gr0mVNx8Se1OmP8gUE9AS75EgFKLNjLot2wfAMF7/QOMAhSl7" +
    "BlHxK/jK+A4wt/3tPCrcrxU6OSly7hvyEoF6HFxH/2vM4jEFfZ4g732cop4dFBjrXemQsTAHey8Yd5Lo9FI4B657" +
    "2lge1ng5NduYGMAST5T/R2TdENgEPldEik+aSEe6T/55dZIJFkbolb0k4J0XfC0DLuXUNjnOAP8oWdUQ2OlsLwre" +
    "6l03vzl/KMiF6o14U0OT4E/8xWiU3kqYpv4zyxfoeszUGzM4/wXxpL+u4iAAAAAAAAA=";

function shotDocument(ownerUid = "firebase-user-id", overrides = {}) {
    return {
        ownerUid,
        surface: "player",
        webpBase64: screenshot(),
        createdAt: timestamp(),
        ...overrides,
    };
}

test("a screenshot decodes as any still WebP an encoder plausibly writes", () => {
    assert.deepEqual(
        { ...decodeThemeScreenshot(screenshot(450)), bytes: undefined },
        { bytes: undefined, width: 450, height: 450 });
    // Genuine libwebp output, not only bytes this test file built.
    assert.equal(decodeThemeScreenshot(REAL_WEBP).width, 128);
    // Extended container with alpha, which is what an encoder writes for a bitmap it thinks has any.
    assert.equal(decodeThemeScreenshot(riff([
        riffChunk("VP8X", vp8xHeader(0x10, 256, 256)),
        riffChunk("ALPH", Buffer.alloc(4)),
        riffChunk("VP8 ", vp8Frame(256, 256)),
    ]).toString("base64")).width, 256);
    // Lossless. Refusing it was a guess about what the app emits, not a safety property: a still
    // VP8L picture is exactly as safe to publish as a lossy one.
    assert.equal(decodeThemeScreenshot(riff([
        riffChunk("VP8L", vp8lFrame(256, 256)),
    ]).toString("base64")).width, 256);
    // A colour profile, which every Android screenshot carries. Refusing it as "metadata" is what
    // dropped the first two real submissions: it holds no personal data and it is what makes a
    // Display P3 picture render in the colours it was captured in.
    assert.equal(decodeThemeScreenshot(riff([
        riffChunk("VP8X", vp8xHeader(0x20, 329, 329)),
        riffChunk("ICCP", Buffer.alloc(536)),
        riffChunk("VP8 ", vp8Frame(329, 329)),
    ]).toString("base64")).width, 329);
    // Trailing bytes no longer make an ordinary picture unreadable.
    const padded = Buffer.concat([
        riff([riffChunk("VP8 ", vp8Frame(256, 256))]),
        Buffer.alloc(4),
    ]);
    assert.equal(decodeThemeScreenshot(padded.toString("base64")).width, 256);
});

test("a screenshot carrying anything but a picture is refused", () => {
    // By chunk, in every container form. A VP8X header only *declares* what it carries, and the
    // declaration is not what this trusts -- EXIF is how a file that passed through a phone gallery
    // carries a location.
    for (const fourCC of ["EXIF", "XMP ", "ANIM", "ANMF"]) {
        assertValidationCode(() => decodeThemeScreenshot(riff([
            riffChunk("VP8X", vp8xHeader(0x00, 256, 256)),
            riffChunk("VP8 ", vp8Frame(256, 256)),
            riffChunk(fourCC, Buffer.from("abcdefgh", "latin1")),
        ]).toString("base64")), "screenshot-carries-metadata");
    }
    // And by flag, for a header that declares metadata it did not attach.
    for (const flags of [0x08, 0x04, 0x02]) {
        assertValidationCode(() => decodeThemeScreenshot(riff([
            riffChunk("VP8X", vp8xHeader(flags, 256, 256)),
            riffChunk("VP8 ", vp8Frame(256, 256)),
        ]).toString("base64")), "screenshot-carries-metadata");
    }
    // Several frames is an animation whichever chunks announced it.
    assertValidationCode(() => decodeThemeScreenshot(riff([
        riffChunk("VP8 ", vp8Frame(256, 256)),
        riffChunk("VP8 ", vp8Frame(256, 256)),
    ]).toString("base64")), "screenshot-carries-metadata");
});

test("a screenshot has to be a square of a plausible size", () => {
    assertValidationCode(() => decodeThemeScreenshot(screenshot(256, 160)), "screenshot-not-square");
    assertValidationCode(() => decodeThemeScreenshot(screenshot(64)), "screenshot-wrong-size");
    assertValidationCode(() => decodeThemeScreenshot(screenshot(1024)), "screenshot-wrong-size");
});

test("a malformed screenshot envelope never reaches the container parser", () => {
    assertValidationCode(() => decodeThemeScreenshot("not base64!"), "invalid-screenshot-encoding");
    assertValidationCode(() => decodeThemeScreenshot(""), "invalid-screenshot-encoding");
    assertValidationCode(() => decodeThemeScreenshot("A".repeat(131_073)), "invalid-screenshot-encoding");
    assertValidationCode(() => decodeThemeScreenshot(42), "invalid-screenshot-encoding");
    assertValidationCode(
        () => decodeThemeScreenshot(Buffer.from("GIF89a").toString("base64")),
        "screenshot-truncated");
    assertValidationCode(
        () => decodeThemeScreenshot(Buffer.alloc(64).toString("base64")),
        "screenshot-not-a-webp");
    // A chunk header that declares more payload than arrived, so nothing parses.
    assertValidationCode(
        () => decodeThemeScreenshot(
            riff([riffChunk("VP8 ", vp8Frame(256, 256))]).subarray(0, 24).toString("base64")),
        "screenshot-riff-chunks-unreadable");
    // A chunk table that parses but holds no picture.
    assertValidationCode(
        () => decodeThemeScreenshot(riff([riffChunk("ALPH", Buffer.alloc(16))]).toString("base64")),
        "screenshot-has-no-frame");
    // A frame whose header is not one.
    assertValidationCode(
        () => decodeThemeScreenshot(riff([riffChunk("VP8 ", Buffer.alloc(16))]).toString("base64")),
        "screenshot-bad-frame-header");
});

test("a refused screenshot is described well enough to act on", () => {
    // The reason this exists: a real submission was dropped in production and the log named a
    // category, not a cause. The description has to survive input the validator already refused.
    const described = describeThemeScreenshot(riff([
        riffChunk("VP8X", vp8xHeader(0x08, 256, 256)),
        riffChunk("VP8 ", vp8Frame(256, 256)),
    ]).toString("base64"));
    assert.match(described, /magic="RIFF"\/"WEBP"/);
    assert.match(described, /chunks=\[VP8X:10, VP8 :16]/);
    // And never throw on anything, which is the whole point of keeping it out of the validator.
    for (const value of [undefined, 42, "", "!!!!", Buffer.from("GIF89a").toString("base64")]) {
        assert.equal(typeof describeThemeScreenshot(value), "string");
    }
});

test("an approved screenshot is committed beside the profile and named in it", async (t) => {
    const root = await temporaryCatalogue(t);
    const document = approvedDocument();
    const firestore = fakeFirestore(document, {}, {
        shots: { [`${ID}/player`]: shotDocument() },
    });
    await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger: { log() {}, warn() {} },
        publish: true,
        manifestPath: join(root, "manifest.json"),
    });

    const profile = JSON.parse(await readFile(join(root, "docs", "themes", `${ID}.json`), "utf8"));
    assert.deepEqual(profile.screenshots, ["player"]);
    const committed = await readFile(join(root, "docs", "themes", "shots", `${ID}-player.webp`));
    assert.deepEqual(committed, Buffer.from(screenshot(), "base64"));
    // The index deliberately stays untouched: it is one fetch for the whole gallery, and the cards
    // that read it render locally.
    const index = JSON.parse(await readFile(join(root, "docs", "themes", "index.json"), "utf8"));
    assert.equal(Object.hasOwn(index.themes[0], "screenshots"), false);
});

test("a theme with no screenshot publishes exactly as it did before", async (t) => {
    const root = await temporaryCatalogue(t);
    const firestore = fakeFirestore(approvedDocument());
    await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-24T12:00:00Z"),
        logger: { log() {}, warn() {} },
        publish: true,
        manifestPath: join(root, "manifest.json"),
    });

    const profile = JSON.parse(await readFile(join(root, "docs", "themes", `${ID}.json`), "utf8"));
    // Absent, never an empty array: two spellings of "no image" would stop a republish comparing
    // byte-for-byte against what is already committed.
    assert.equal(Object.hasOwn(profile, "screenshots"), false);
    const themeFiles = await readdir(join(root, "docs", "themes"));
    assert.equal(themeFiles.includes("shots"), false);
});

test("a screenshot the theme publishes without is dropped, never fatal", async (t) => {
    for (const [label, options] of [
        // The moderator approved the theme and refused its image.
        ["rejected by the moderator", {
            shots: { [`${ID}/player`]: shotDocument() },
            reviews: { [ID]: { reviewSchemaVersion: 1, decision: "approved", shotsAccepted: false } },
        }],
        // Bytes the container parser refuses.
        ["undecodable bytes", {
            shots: { [`${ID}/player`]: shotDocument("firebase-user-id", { webpBase64: "AAAA" }) },
        }],
        // A screenshot filed against an intake it does not belong to.
        ["owner mismatch", { shots: { [`${ID}/player`]: shotDocument("someone-else") } }],
        // A surface the registry does not publish.
        ["unknown surface", { shots: { [`${ID}/volume`]: shotDocument() } }],
    ]) {
        const root = await temporaryCatalogue(t);
        const firestore = fakeFirestore(approvedDocument(), {}, options);
        const result = await publishApprovedThemes({
            firestore,
            root,
            now: new Date("2026-08-24T12:00:00Z"),
            logger: { log() {}, warn() {} },
            publish: true,
            manifestPath: join(root, "manifest.json"),
        });

        assert.equal(result.plans.length, 1, label);
        const profile = JSON.parse(await readFile(join(root, "docs", "themes", `${ID}.json`), "utf8"));
        assert.equal(Object.hasOwn(profile, "screenshots"), false, label);
    }
});

test("finalization clears the stored screenshot once its bytes are committed", async (t) => {
    const root = await temporaryCatalogue(t);
    const document = approvedDocument();
    const firestore = fakeFirestore(document, {}, {
        quotas: ["firebase-user-id"],
        shots: { [`${ID}/player`]: shotDocument() },
    });
    const manifestPath = join(root, "manifest.json");
    const logger = { log() {}, warn() {} };
    await publishApprovedThemes({
        firestore, root, now: new Date("2026-08-24T12:00:00Z"), logger, publish: true, manifestPath,
    });
    await finalizePublishedThemes({ firestore, auth: fakeAuth(), root, manifestPath, logger });

    // Published, and the private copy is gone now that a public one exists.
    assert.equal(document.data.status, "published");
    assert.deepEqual(firestore.removed.shots, [`${ID}/player`]);
    await readFile(join(root, "docs", "themes", "shots", `${ID}-player.webp`));
});

test("withdrawing a theme takes its screenshot down with it", async (t) => {
    const root = await temporaryCatalogue(t);
    const document = approvedDocument();
    const firestore = fakeFirestore(document, {}, {
        quotas: ["firebase-user-id"],
        shots: { [`${ID}/player`]: shotDocument() },
    });
    const logger = { log() {}, warn() {} };
    const manifestPath = join(root, "manifest.json");
    await publishApprovedThemes({
        firestore, root, now: new Date("2026-08-24T12:00:00Z"), logger, publish: true, manifestPath,
    });
    await finalizePublishedThemes({ firestore, auth: fakeAuth(), root, manifestPath, logger });
    await readFile(join(root, "docs", "themes", "shots", `${ID}-player.webp`));

    document.data.status = "withdrawn";
    await publishApprovedThemes({
        firestore,
        root,
        now: new Date("2026-08-25T12:00:00Z"),
        logger,
        publish: true,
        manifestPath: join(root, "withdrawal-manifest.json"),
    });

    await assert.rejects(readFile(join(root, "docs", "themes", "shots", `${ID}-player.webp`)));
    await assert.rejects(readFile(join(root, "docs", "themes", `${ID}.json`)));
});
