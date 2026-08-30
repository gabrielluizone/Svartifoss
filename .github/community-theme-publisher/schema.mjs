import { readFileSync } from "node:fs";

/**
 * Public theme schema mirrored from FaceScopedPreferences.SCOPED_DEFINITIONS.
 *
 * The publisher cannot execute Android/Kotlin code, so this table deliberately fails closed when
 * the app grows a new appearance preference. Update it in the same change as the Android schema.
 */
export const PROFILE_SCHEMA_VERSION = 1;
export const SUBMISSION_SCHEMA_VERSION = 2;
export const PROFILE_REVISION = 1;
export const MINIMUM_APP_VERSION = "3.3";

export const MAX_PUBLIC_TEXT_LENGTH = 48;
export const MAX_SETTING_TEXT_LENGTH = 128;
export const MAX_PROFILE_JSON_BYTES = 24 * 1024;

/** These are ThemeAppearance.ALLOWED_BASE_FACES minus ArchivedFaces.KEYS. */
export const ALLOWED_BASE_FACES = Object.freeze([
    "classic",
    "expressive",
    "poster",
    "studio",
    "material",
    "immersive",
    "carousel",
    "chat",
    "split",
    "note",
    "verse",
    "metadata",
    "ribbon",
    "frame",
]);

const STRING_SETTINGS = [
    "album_art_style",
    "screen_buttons_bg_style",
    "screen_buttons_curve_style",
    "screen_buttons_shape",
    "wear_accent_floor",
    "wear_album_accent_source",
    "wear_aod_art_treatment",
    "wear_aod_color_mode",
    "wear_aod_custom_color",
    "wear_aod_style",
    "wear_artist_color_mode",
    "wear_artist_custom_color",
    "wear_artist_font",
    "wear_artist_text_case",
    "wear_carousel_card_shape",
    "wear_clock_color_mode",
    "wear_clock_custom_color",
    "wear_clock_font",
    "wear_color_modifier",
    "wear_color_treatment",
    "wear_expressive_seek_mode",
    "wear_font",
    "wear_gestures_mode",
    "wear_list_row_size",
    "wear_lyrics_font",
    "wear_mini_buttons_mode",
    "wear_normal_color",
    "wear_note_cover_shape",
    "wear_overlay_backdrop_style",
    "wear_player_shading_intensity",
    "wear_player_shading_style",
    "wear_progress_color_mode",
    "wear_progress_custom_color",
    "wear_progress_layout",
    "wear_progress_style",
    "wear_queue_style",
    "wear_quick_panel_color_mode",
    "wear_quick_panel_custom_color",
    "wear_quick_panel_layout",
    "wear_quick_panel_style",
    "wear_screen_theme",
    "wear_seek_layout",
    "wear_seek_style",
    "wear_shading_color_mode",
    "wear_shading_custom_color",
    "wear_split_panel",
    "wear_title_color_mode",
    "wear_title_custom_color",
    "wear_title_font",
    "wear_title_text_case",
    "wear_title_text_mode",
    "wear_track_time_font",
    "wear_track_time_mode",
    "wear_up_next_pill_style",
    "wear_volume_color_mode",
    "wear_volume_custom_color",
    "wear_volume_layout",
    "wear_volume_style",
];

const BOOLEAN_SETTINGS = [
    "always_show_time",
    "dim_album_art",
    "wear_album_art_fade",
    "wear_aod_show_art",
    "wear_aod_show_clock",
    "wear_aod_show_pills",
    "wear_aod_show_progress",
    "wear_aod_show_track_info",
    "wear_aod_show_transport",
    "wear_artist_adaptive_contrast",
    "wear_artist_desaturated",
    "wear_artist_font_italic",
    "wear_classic_icons_visible",
    "wear_clock_adaptive_contrast",
    "wear_clock_font_italic",
    "wear_dynamic_accent",
    "wear_edge_progress_visible",
    "wear_edge_seek_enabled",
    "wear_font_all_screens",
    "wear_internal_progress_visible",
    "wear_keep_screen_on",
    "wear_metadata_show_core",
    "wear_metadata_show_credits",
    "wear_metadata_show_identifiers",
    "wear_metadata_show_playback",
    "wear_metadata_show_release",
    "wear_metadata_show_technical",
    "wear_normal_color_multi",
    "wear_progress_desaturated",
    "wear_progress_gradient",
    "wear_quadrant_tap_flash",
    "wear_quick_panel_shortcut_cover",
    "wear_show_source_icon",
    "wear_show_track_artist",
    "wear_show_track_title",
    "wear_show_up_next_pill",
    "wear_title_adaptive_contrast",
    "wear_title_font_italic",
    "wear_track_time_font_italic",
];

const INT_SETTINGS = [
    "album_art_blur_radius",
    "album_art_dim_strength",
    "ambient_album_art_opacity",
    "overlay_blur_radius",
    "screen_buttons_bottom_offset",
    "screen_buttons_opacity",
    "wear_aod_intensity",
    "wear_artist_font_opacity",
    "wear_artist_font_scale",
    "wear_artist_font_tracking",
    "wear_artist_font_weight",
    "wear_artist_font_flex_grade",
    "wear_artist_font_flex_optical_size",
    "wear_artist_font_flex_roundness",
    "wear_artist_font_flex_width",
    "wear_clock_font_scale",
    "wear_clock_font_tracking",
    "wear_clock_font_weight",
    "wear_clock_font_flex_grade",
    "wear_clock_font_flex_optical_size",
    "wear_clock_font_flex_roundness",
    "wear_clock_font_flex_width",
    "wear_clock_opacity",
    "wear_color_hue_shift",
    "wear_font_flex_grade",
    "wear_font_flex_optical_size",
    "wear_font_flex_roundness",
    "wear_font_flex_width",
    "wear_lyrics_font_flex_grade",
    "wear_lyrics_font_flex_optical_size",
    "wear_lyrics_font_flex_roundness",
    "wear_lyrics_font_flex_width",
    "wear_source_icon_opacity",
    "wear_source_icon_scale",
    "wear_title_font_opacity",
    "wear_title_font_scale",
    "wear_title_font_tracking",
    "wear_title_font_weight",
    "wear_title_font_flex_grade",
    "wear_title_font_flex_optical_size",
    "wear_title_font_flex_roundness",
    "wear_title_font_flex_width",
    "wear_track_time_font_opacity",
    "wear_track_time_font_scale",
    "wear_track_time_font_tracking",
    "wear_track_time_font_weight",
    "wear_track_time_font_flex_grade",
    "wear_track_time_font_flex_optical_size",
    "wear_track_time_font_flex_roundness",
    "wear_track_time_font_flex_width",
];

export const SETTING_TYPES = Object.freeze(Object.fromEntries([
    ...STRING_SETTINGS.map((key) => [key, "string"]),
    ...BOOLEAN_SETTINGS.map((key) => [key, "boolean"]),
    ...INT_SETTINGS.map((key) => [key, "int"]),
]));

/** Sorted once so both JSON output and canonical digests are independent of input map order. */
export const SETTING_KEYS = Object.freeze(Object.keys(SETTING_TYPES).sort());

const CONSTRAINTS_URL = new URL(
    "../../common/src/main/assets/community-theme-constraints.json",
    import.meta.url,
);
const HEX_RGB_OR_EMPTY_PATTERN = "^$|^#[0-9A-F]{6}$";
const HEX_RGB_OR_EMPTY = /^(?:|#[0-9A-F]{6})$/;

function isPlainRecord(value) {
    return typeof value === "object" && value !== null &&
        !Array.isArray(value) && Object.getPrototypeOf(value) === Object.prototype;
}

function constraintError(message) {
    throw new Error(`Invalid community-theme constraints asset: ${message}`);
}

function assertExactConstraintKeys(record, expected, context) {
    const actual = Object.keys(record).sort();
    const wanted = [...expected].sort();
    if (actual.length !== wanted.length || actual.some((key, index) => key !== wanted[index])) {
        constraintError(`${context} has unexpected fields`);
    }
}

function normalizeValueSet(value, context) {
    if (!Array.isArray(value) || value.length === 0 ||
            value.some((item) => typeof item !== "string" || item.length === 0 ||
                    item.length > MAX_SETTING_TEXT_LENGTH) ||
            new Set(value).size !== value.length) {
        constraintError(`${context} must be a nonempty, unique string array`);
    }
    return Object.freeze([...value]);
}

function normalizeFaceSet(value, context, { allowEmpty = false } = {}) {
    if (!Array.isArray(value) || (!allowEmpty && value.length === 0) ||
            value.some((face) => typeof face !== "string" || !ALLOWED_BASE_FACES.includes(face)) ||
            new Set(value).size !== value.length) {
        constraintError(`${context} must be a unique allowed-base-face array`);
    }
    return Object.freeze([...value]);
}

function constraintPrimitiveMatches(definition, value, valueSets) {
    switch (definition.type) {
        case "boolean":
            return typeof value === "boolean";
        case "int":
            return Number.isSafeInteger(value) && value >= definition.min && value <= definition.max;
        case "string":
            if (typeof value !== "string") return false;
            if (definition.valueSet !== undefined) return valueSets[definition.valueSet].includes(value);
            return definition.colorRule === "empty-or-uppercase-hex-rgb" && HEX_RGB_OR_EMPTY.test(value);
        default:
            return false;
    }
}

/**
 * Loads the one data-only contract shared with Android public-profile parsing. This deliberately
 * validates its own shape at startup: an app setting added without a semantic constraint makes the
 * trusted publisher fail instead of accidentally treating arbitrary values as meaningful changes.
 */
function loadConstraintContract() {
    let raw;
    try {
        raw = JSON.parse(readFileSync(CONSTRAINTS_URL, "utf8"));
    } catch (error) {
        constraintError(`cannot be loaded (${error instanceof Error ? error.message : "unknown error"})`);
    }
    if (!isPlainRecord(raw)) constraintError("root must be an object");
    assertExactConstraintKeys(
        raw,
        [
            "schemaVersion",
            "colorRules",
            "valueSets",
            "originalityApplicableFaces",
            "originalityRequires",
            "legacyReadOnlyValues",
            "settings",
        ],
        "root",
    );
    if (raw.schemaVersion !== 1) constraintError("has an unsupported schemaVersion");
    if (!isPlainRecord(raw.valueSets) || !isPlainRecord(raw.colorRules) ||
            !isPlainRecord(raw.originalityApplicableFaces) ||
            !isPlainRecord(raw.originalityRequires) ||
            !isPlainRecord(raw.legacyReadOnlyValues) || !isPlainRecord(raw.settings)) {
        constraintError("constraint sections must be objects");
    }

    const colorRules = {};
    for (const [name, rule] of Object.entries(raw.colorRules)) {
        if (!isPlainRecord(rule)) constraintError(`color rule ${name} must be an object`);
        assertExactConstraintKeys(rule, ["pattern"], `color rule ${name}`);
        if (name !== "empty-or-uppercase-hex-rgb" || rule.pattern !== HEX_RGB_OR_EMPTY_PATTERN) {
            constraintError(`color rule ${name} is unsupported`);
        }
        colorRules[name] = Object.freeze({ pattern: rule.pattern });
    }

    const valueSets = {};
    for (const [name, values] of Object.entries(raw.valueSets)) {
        if (!/^[a-z][A-Za-z0-9]*$/.test(name)) constraintError(`invalid value-set name ${name}`);
        valueSets[name] = normalizeValueSet(values, `value set ${name}`);
    }

    const actualSettingKeys = Object.keys(raw.settings).sort();
    if (actualSettingKeys.length !== SETTING_KEYS.length ||
            actualSettingKeys.some((key, index) => key !== SETTING_KEYS[index])) {
        constraintError("settings must cover the exact exported setting key set");
    }

    const settings = {};
    for (const key of SETTING_KEYS) {
        const definition = raw.settings[key];
        if (!isPlainRecord(definition) || definition.type !== SETTING_TYPES[key]) {
            constraintError(`setting ${key} has the wrong type or shape`);
        }
        switch (definition.type) {
            case "boolean":
                assertExactConstraintKeys(definition, ["type"], `setting ${key}`);
                settings[key] = Object.freeze({ type: "boolean" });
                break;
            case "int":
                assertExactConstraintKeys(definition, ["type", "min", "max"], `setting ${key}`);
                if (!Number.isSafeInteger(definition.min) || !Number.isSafeInteger(definition.max) ||
                        definition.min > definition.max) {
                    constraintError(`setting ${key} has invalid integer bounds`);
                }
                settings[key] = Object.freeze({
                    type: "int",
                    min: definition.min,
                    max: definition.max,
                });
                break;
            case "string":
                if (typeof definition.valueSet === "string") {
                    assertExactConstraintKeys(definition, ["type", "valueSet"], `setting ${key}`);
                    const values = valueSets[definition.valueSet];
                    if (values === undefined) constraintError(`setting ${key} references an unknown value set`);
                    settings[key] = Object.freeze({
                        type: "string",
                        valueSet: definition.valueSet,
                    });
                } else if (typeof definition.colorRule === "string") {
                    assertExactConstraintKeys(definition, ["type", "colorRule"], `setting ${key}`);
                    if (colorRules[definition.colorRule] === undefined) {
                        constraintError(`setting ${key} references an unknown color rule`);
                    }
                    settings[key] = Object.freeze({
                        type: "string",
                        colorRule: definition.colorRule,
                    });
                } else {
                    constraintError(`setting ${key} needs a valueSet or colorRule`);
                }
                break;
            default:
                constraintError(`setting ${key} has an unsupported type`);
        }
    }

    assertExactConstraintKeys(
        raw.originalityApplicableFaces,
        ["default", "overrides"],
        "originalityApplicableFaces",
    );
    if (!isPlainRecord(raw.originalityApplicableFaces.overrides)) {
        constraintError("originalityApplicableFaces.overrides must be an object");
    }
    const originalityApplicableFaces = {
        default: normalizeFaceSet(
            raw.originalityApplicableFaces.default,
            "originalityApplicableFaces.default",
        ),
        overrides: {},
    };
    for (const [key, faces] of Object.entries(raw.originalityApplicableFaces.overrides)) {
        if (settings[key] === undefined) constraintError(`originality applicability references unknown ${key}`);
        originalityApplicableFaces.overrides[key] = normalizeFaceSet(
            faces,
            `originality applicability for ${key}`,
            { allowEmpty: true },
        );
    }

    const originalityRequires = {};
    for (const [key, rawConditions] of Object.entries(raw.originalityRequires)) {
        if (settings[key] === undefined || !Array.isArray(rawConditions) || rawConditions.length === 0) {
            constraintError(`originality requirements for ${key} must be a nonempty array`);
        }
        const conditions = rawConditions.map((condition, index) => {
            if (!isPlainRecord(condition)) constraintError(`originality condition ${key}[${index}] must be an object`);
            assertExactConstraintKeys(condition, ["setting", "equalsAny"], `originality condition ${key}[${index}]`);
            if (typeof condition.setting !== "string" || settings[condition.setting] === undefined) {
                constraintError(`originality condition ${key}[${index}] references an unknown setting`);
            }
            const candidates = condition.equalsAny;
            if (!Array.isArray(candidates) || candidates.length === 0 ||
                    candidates.some((value) => !constraintPrimitiveMatches(
                        settings[condition.setting], value, valueSets,
                    )) ||
                    new Set(candidates.map((value) => JSON.stringify(value))).size !== candidates.length) {
                constraintError(`originality condition ${key}[${index}] has invalid equalsAny values`);
            }
            return Object.freeze({ setting: condition.setting, equalsAny: Object.freeze([...candidates]) });
        });
        originalityRequires[key] = Object.freeze(conditions);
    }

    const legacyReadOnlyValues = {};
    for (const [key, values] of Object.entries(raw.legacyReadOnlyValues)) {
        const definition = settings[key];
        if (definition === undefined || definition.type !== "string" || definition.valueSet === undefined) {
            constraintError(`legacy values for ${key} do not target an enum setting`);
        }
        const normalized = normalizeValueSet(values, `legacy values for ${key}`);
        const canonical = valueSets[definition.valueSet];
        if (normalized.some((value) => canonical.includes(value))) {
            constraintError(`legacy values for ${key} overlap the canonical vocabulary`);
        }
        legacyReadOnlyValues[key] = normalized;
    }

    return Object.freeze({
        schemaVersion: 1,
        colorRules: Object.freeze(colorRules),
        valueSets: Object.freeze(valueSets),
        originalityApplicableFaces: Object.freeze({
            default: originalityApplicableFaces.default,
            overrides: Object.freeze(originalityApplicableFaces.overrides),
        }),
        originalityRequires: Object.freeze(originalityRequires),
        legacyReadOnlyValues: Object.freeze(legacyReadOnlyValues),
        settings: Object.freeze(settings),
    });
}

/** The exact current public vocabulary, plus explicitly segregated trusted legacy-read values. */
export const COMMUNITY_THEME_CONSTRAINTS = loadConstraintContract();

/**
 * Checks a typed setting after its JSON shape and primitive type have already been checked.
 * `allowLegacyReadOnly` is intentionally reserved for existing trusted partial Pages profiles;
 * intake documents and newly generated public profiles always use the canonical branch.
 */
export function isSemanticallyValidSetting(key, setting, { allowLegacyReadOnly = false } = {}) {
    const definition = COMMUNITY_THEME_CONSTRAINTS.settings[key];
    if (definition === undefined || !isPlainRecord(setting) || setting.type !== definition.type) return false;
    switch (definition.type) {
        case "boolean":
            return typeof setting.value === "boolean";
        case "int":
            return Number.isSafeInteger(setting.value) &&
                setting.value >= definition.min && setting.value <= definition.max;
        case "string":
            if (typeof setting.value !== "string") return false;
            if (definition.valueSet !== undefined) {
                const canonical = COMMUNITY_THEME_CONSTRAINTS.valueSets[definition.valueSet];
                return canonical.includes(setting.value) ||
                    (allowLegacyReadOnly &&
                        (COMMUNITY_THEME_CONSTRAINTS.legacyReadOnlyValues[key] ?? []).includes(setting.value));
            }
            return definition.colorRule === "empty-or-uppercase-hex-rgb" &&
                HEX_RGB_OR_EMPTY.test(setting.value);
        default:
            return false;
    }
}

/** True only when a changed setting can visibly contribute to this complete face snapshot. */
export function isOriginalityApplicableSetting(key, settings, baseFace) {
    if (!ALLOWED_BASE_FACES.includes(baseFace) || !isPlainRecord(settings) ||
            COMMUNITY_THEME_CONSTRAINTS.settings[key] === undefined) {
        return false;
    }
    const configuredFaces = COMMUNITY_THEME_CONSTRAINTS.originalityApplicableFaces.overrides[key] ??
        COMMUNITY_THEME_CONSTRAINTS.originalityApplicableFaces.default;
    if (!configuredFaces.includes(baseFace)) return false;
    const requirements = COMMUNITY_THEME_CONSTRAINTS.originalityRequires[key] ?? [];
    return requirements.every(({ setting, equalsAny }) => {
        const dependent = settings[setting];
        return isPlainRecord(dependent) && equalsAny.includes(dependent.value);
    });
}

/**
 * Definition defaults from MiscPreferences.EXPORTABLE. These are needed to materialize the small
 * hand-authored Phase-1 profiles before comparing their digest with a complete submission.
 */
const DEFAULT_VALUES = Object.freeze({
    album_art_blur_radius: 35,
    album_art_dim_strength: 80,
    album_art_style: "cover",
    always_show_time: false,
    ambient_album_art_opacity: 55,
    dim_album_art: true,
    overlay_blur_radius: 35,
    screen_buttons_bg_style: "glass",
    screen_buttons_bottom_offset: 42,
    screen_buttons_curve_style: "flat",
    screen_buttons_opacity: 100,
    screen_buttons_shape: "pill",
    wear_accent_floor: "off",
    wear_album_accent_source: "balanced",
    wear_album_art_fade: true,
    wear_aod_art_treatment: "blur",
    wear_aod_color_mode: "white",
    wear_aod_custom_color: "",
    wear_aod_intensity: 100,
    wear_aod_show_art: true,
    wear_aod_show_clock: true,
    wear_aod_show_pills: true,
    wear_aod_show_progress: true,
    wear_aod_show_track_info: true,
    wear_aod_show_transport: true,
    wear_aod_style: "follow",
    wear_artist_adaptive_contrast: false,
    wear_artist_color_mode: "follow",
    wear_artist_custom_color: "",
    wear_artist_desaturated: false,
    wear_artist_font: "follow",
    wear_artist_font_flex_grade: 0,
    wear_artist_font_flex_optical_size: 18,
    wear_artist_font_flex_roundness: 0,
    wear_artist_font_flex_width: 100,
    wear_artist_font_italic: false,
    wear_artist_font_opacity: 100,
    wear_artist_font_scale: 100,
    wear_artist_font_tracking: 0,
    wear_artist_font_weight: 400,
    wear_artist_text_case: "normal",
    wear_carousel_card_shape: "rounded",
    wear_classic_icons_visible: true,
    wear_clock_adaptive_contrast: false,
    wear_clock_color_mode: "white",
    wear_clock_custom_color: "",
    wear_clock_font: "follow",
    wear_clock_font_italic: false,
    wear_clock_font_scale: 100,
    wear_clock_font_tracking: 0,
    wear_clock_font_weight: 400,
    wear_clock_font_flex_grade: 0,
    wear_clock_font_flex_optical_size: 18,
    wear_clock_font_flex_roundness: 0,
    wear_clock_font_flex_width: 100,
    wear_clock_opacity: 60,
    wear_color_hue_shift: 0,
    wear_color_modifier: "none",
    wear_color_treatment: "expressive",
    wear_dynamic_accent: true,
    wear_edge_progress_visible: true,
    wear_edge_seek_enabled: true,
    wear_expressive_seek_mode: "central",
    wear_font: "google_sans",
    wear_font_all_screens: false,
    wear_font_flex_grade: 0,
    wear_font_flex_optical_size: 18,
    wear_font_flex_roundness: 0,
    wear_font_flex_width: 100,
    wear_gestures_mode: "always",
    wear_internal_progress_visible: true,
    wear_keep_screen_on: false,
    wear_list_row_size: "normal",
    wear_lyrics_font: "follow",
    wear_lyrics_font_flex_grade: 0,
    wear_lyrics_font_flex_optical_size: 18,
    wear_lyrics_font_flex_roundness: 0,
    wear_lyrics_font_flex_width: 100,
    wear_metadata_show_core: true,
    wear_metadata_show_credits: true,
    wear_metadata_show_identifiers: false,
    wear_metadata_show_playback: true,
    wear_metadata_show_release: true,
    wear_metadata_show_technical: true,
    wear_mini_buttons_mode: "always",
    wear_normal_color: "",
    wear_normal_color_multi: true,
    wear_note_cover_shape: "circle",
    wear_overlay_backdrop_style: "follow",
    wear_player_shading_intensity: "balanced",
    wear_player_shading_style: "follow",
    wear_progress_color_mode: "follow",
    wear_progress_custom_color: "",
    wear_progress_desaturated: false,
    wear_progress_gradient: true,
    wear_progress_layout: "edge",
    wear_progress_style: "solid",
    wear_quadrant_tap_flash: false,
    wear_queue_style: "glass",
    wear_quick_panel_color_mode: "follow",
    wear_quick_panel_custom_color: "",
    wear_quick_panel_layout: "stacked",
    wear_quick_panel_shortcut_cover: false,
    wear_quick_panel_style: "glass",
    wear_screen_theme: "default",
    wear_seek_layout: "edge",
    wear_seek_style: "plain",
    wear_shading_color_mode: "black",
    wear_shading_custom_color: "",
    wear_show_source_icon: true,
    wear_show_track_artist: true,
    wear_show_track_title: true,
    wear_show_up_next_pill: false,
    wear_source_icon_opacity: 100,
    wear_source_icon_scale: 100,
    wear_split_panel: "blur",
    wear_title_adaptive_contrast: false,
    wear_title_color_mode: "face",
    wear_title_custom_color: "",
    wear_title_font: "follow",
    wear_title_font_flex_grade: 0,
    wear_title_font_flex_optical_size: 18,
    wear_title_font_flex_roundness: 0,
    wear_title_font_flex_width: 100,
    wear_title_font_italic: false,
    wear_title_font_opacity: 100,
    wear_title_font_scale: 100,
    wear_title_font_tracking: 0,
    wear_title_font_weight: 400,
    wear_title_text_case: "normal",
    wear_title_text_mode: "smart",
    wear_track_time_font: "follow",
    wear_track_time_font_flex_grade: 0,
    wear_track_time_font_flex_optical_size: 18,
    wear_track_time_font_flex_roundness: 0,
    wear_track_time_font_flex_width: 100,
    wear_track_time_font_italic: false,
    wear_track_time_font_opacity: 100,
    wear_track_time_font_scale: 100,
    wear_track_time_font_tracking: 0,
    wear_track_time_font_weight: 400,
    wear_track_time_mode: "always",
    wear_up_next_pill_style: "follow",
    wear_volume_color_mode: "follow",
    wear_volume_custom_color: "",
    wear_volume_layout: "edge",
    wear_volume_style: "glass",
});

// Mirrors FaceScopedPreferences: the faces whose overlay surfaces default to the album-accent
// styles. Ribbon and Frame reach the same defaults through their own per-face maps on the Android
// side, so they are listed here with them rather than in this set.
const ALBUM_ACCENT_FACES = new Set(["expressive", "poster", "studio", "material"]);
const ALBUM_ART_DEFAULTS = Object.freeze({
    classic: "cover",
    expressive: "expressive",
    poster: "poster",
    studio: "studio",
    material: "material",
    immersive: "cover",
    carousel: "expressive",
    chat: "expressive",
    split: "expressive",
    note: "hidden",
    verse: "hidden",
    metadata: "hidden",
    ribbon: "hidden",
    frame: "hidden",
});

/** FaceScopedPreferences.SELF_COMPOSED_FACES: no shared mini-button row, no edge progress arc. */
const SELF_COMPOSED_FACES = new Set(["split", "verse", "ribbon", "frame"]);

/** FaceScopedPreferences.ALBUM_ACCENT_SURFACE_DEFAULTS, applied to the faces that ask for it. */
const ALBUM_ACCENT_SURFACE_FACES = new Set([
    ...ALBUM_ACCENT_FACES, "ribbon", "frame",
]);

if (SETTING_KEYS.length !== Object.keys(DEFAULT_VALUES).length ||
        SETTING_KEYS.some((key) => !Object.hasOwn(DEFAULT_VALUES, key))) {
    throw new Error("Community theme defaults do not match the setting schema");
}

/** Mirrors FaceScopedPreferences.publishedBaseDefault for the public gallery boundary. */
export function defaultSettingsForFace(baseFace) {
    if (!ALLOWED_BASE_FACES.includes(baseFace)) throw new Error("Unknown community theme base face");
    const settings = {};
    for (const key of SETTING_KEYS) {
        settings[key] = { type: SETTING_TYPES[key], value: DEFAULT_VALUES[key] };
    }

    settings.album_art_style.value = ALBUM_ART_DEFAULTS[baseFace];
    if (ALBUM_ACCENT_SURFACE_FACES.has(baseFace)) {
        settings.wear_quick_panel_style.value = "tonal";
        settings.wear_queue_style.value = "tonal";
        settings.wear_volume_style.value = "tonal";
        settings.wear_seek_style.value = "expressive";
    }
    if (SELF_COMPOSED_FACES.has(baseFace)) {
        settings.wear_mini_buttons_mode.value = "never";
        settings.wear_edge_progress_visible.value = false;
    }
    if (baseFace === "verse") settings.wear_accent_floor.value = "standard";
    if (baseFace === "split") settings.wear_show_source_icon.value = true;
    if (baseFace === "ribbon") settings.always_show_time.value = true;
    if (baseFace === "note" || baseFace === "chat") settings.wear_edge_progress_visible.value = false;
    return settings;
}
