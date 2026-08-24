import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
    ALLOWED_BASE_FACES,
    defaultSettingsForFace,
    SETTING_KEYS,
    SETTING_TYPES,
} from "./schema.mjs";

const FIXTURE_URL = new URL(
    "../../common/src/test/resources/community-theme-defaults.properties",
    import.meta.url,
);

const fixture = parseFixture(await readFile(FIXTURE_URL, "utf8"));

test("publisher defaults use the shared Android face-default fixture", () => {
    assert.deepEqual(ALLOWED_BASE_FACES, fixture.faces);
    assert.deepEqual(SETTING_KEYS, Object.keys(fixture.baseDefaults).sort());
    assert.deepEqual(
        SETTING_TYPES,
        Object.fromEntries(Object.entries(fixture.baseDefaults).map(([key, setting]) => [key, setting.type])),
    );

    for (const face of fixture.faces) {
        const expected = {
            ...fixture.baseDefaults,
            ...(fixture.faceOverrides[face] ?? {}),
        };
        const actual = defaultSettingsForFace(face);

        assert.deepEqual(actual, expected, `${face} must materialize the Android baseline exactly`);
        assert.equal(
            Object.keys(actual).filter((key) => !sameSetting(actual[key], expected[key])).length,
            0,
            `${face} defaults must have zero originality changes`,
        );
    }
});

function parseFixture(text) {
    const properties = new Map();
    for (const rawLine of text.split(/\r?\n/)) {
        const line = rawLine.trim();
        if (!line || line.startsWith("#")) continue;
        const separator = line.indexOf("=");
        assert.ok(separator > 0, `Invalid shared defaults fixture line: ${rawLine}`);
        properties.set(line.slice(0, separator), line.slice(separator + 1));
    }

    assert.equal(properties.get("fixture.version"), "1");
    const faces = required(properties, "faces").split(",").map((face) => face.trim()).filter(Boolean);
    const baseDefaults = {};
    const faceOverrides = {};

    for (const [key, encoded] of properties) {
        if (key.startsWith("base.")) {
            baseDefaults[key.slice("base.".length)] = parseSetting(encoded);
            continue;
        }
        if (key.startsWith("override.")) {
            const suffix = key.slice("override.".length);
            const separator = suffix.indexOf(".");
            assert.ok(separator > 0, `Invalid shared defaults override key: ${key}`);
            const face = suffix.slice(0, separator);
            const settingKey = suffix.slice(separator + 1);
            assert.ok(faces.includes(face), `Fixture override targets an unknown face: ${face}`);
            faceOverrides[face] ??= {};
            faceOverrides[face][settingKey] = parseSetting(encoded);
        }
    }

    for (const [face, overrides] of Object.entries(faceOverrides)) {
        for (const key of Object.keys(overrides)) {
            assert.ok(Object.hasOwn(baseDefaults, key), `${face} overrides unknown setting ${key}`);
        }
    }
    return { faces, baseDefaults, faceOverrides };
}

function parseSetting(encoded) {
    const separator = encoded.indexOf(":");
    assert.ok(separator > 0, `Invalid shared defaults setting: ${encoded}`);
    const type = encoded.slice(0, separator);
    const rawValue = encoded.slice(separator + 1);
    switch (type) {
        case "string":
            return { type, value: rawValue };
        case "boolean":
            assert.ok(rawValue === "true" || rawValue === "false", `Invalid boolean: ${rawValue}`);
            return { type, value: rawValue === "true" };
        case "int":
            assert.match(rawValue, /^-?\d+$/, `Invalid integer: ${rawValue}`);
            return { type, value: Number(rawValue) };
        default:
            assert.fail(`Unknown shared defaults setting type: ${type}`);
    }
}

function required(properties, key) {
    const value = properties.get(key);
    assert.notEqual(value, undefined, `Missing shared defaults fixture key: ${key}`);
    return value;
}

function sameSetting(left, right) {
    return left?.type === right?.type && left?.value === right?.value;
}
