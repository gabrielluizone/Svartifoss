package com.svartifoss.snfell.common

import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The public-theme publisher is deliberately independent of Android, so it cannot read the
 * preference registry at runtime. This fixture is the explicit hand-off between the two sides:
 * [MiscPreferences] and [FaceScopedPreferences] must agree with it here, while the Node test next
 * to the publisher checks its own materialized defaults against the very same file.
 */
class CommunityThemeDefaultsParityTest {

    @Test
    fun `shared fixture matches every exportable scoped preference default`() {
        val expectedFaces = ThemeAppearance.ALLOWED_BASE_FACES - ArchivedFaces.KEYS
        assertEquals(expectedFaces, fixture.faces.toSet())

        val fromExportRegistry = MiscPreferences.EXPORTABLE
                .filter { it.key in FaceScopedPreferences.SCOPED_KEYS }
                .associate { definition ->
                    definition.key to fixtureSetting(definition.defaultValue)
                }
        val scopedDefinitions = FaceScopedPreferences.SCOPED_DEFINITIONS.associate { definition ->
            definition.key to fixtureSetting(definition.defaultValue)
        }

        assertEquals(
                "Every scoped setting must remain exportable before it can be public-theme data",
                scopedDefinitions,
                fromExportRegistry)
        assertEquals(
                "Update the shared publisher fixture when an Android public-theme default changes",
                fixture.baseDefaults,
                scopedDefinitions)
    }

    @Test
    fun `shared fixture matches every public face override and effective baseline`() {
        val actualOverrides = linkedMapOf<String, Map<String, FixtureSetting>>()

        fixture.faces.forEach { face ->
            val overrides = FaceScopedPreferences.SCOPED_DEFINITIONS.mapNotNull { definition ->
                FaceScopedPreferences.perFaceDefault(face, definition.key)?.let { rawValue ->
                    definition.key to fixtureSetting(rawValue, definition.defaultValue)
                }
            }.toMap()
            if (overrides.isNotEmpty()) actualOverrides[face] = overrides

            val actualBaseline = FaceScopedPreferences.SCOPED_DEFINITIONS.associate { definition ->
                val rawValue = FaceScopedPreferences.perFaceDefault(face, definition.key)
                definition.key to if (rawValue == null) {
                    fixtureSetting(definition.defaultValue)
                } else {
                    fixtureSetting(rawValue, definition.defaultValue)
                }
            }
            assertEquals(
                    "The $face public-theme baseline changed; update the shared fixture and publisher",
                    materializedFixtureDefaults(face),
                    actualBaseline)
        }

        assertEquals(
                "FaceScopedPreferences.perFaceDefault changed; keep the publisher baseline aligned",
                fixture.faceOverrides,
                actualOverrides)
    }

    @Test
    fun `every fixture baseline has zero originality changes`() {
        fixture.faces.forEach { face ->
            val defaults = materializedFixtureDefaults(face).mapValues { (_, setting) ->
                setting.toCommunityThemeValue()
            }
            val result = CommunityThemeSubmissionPolicy.evaluate(
                    candidate = CommunityThemeSettings(face, emptyMap()),
                    defaults = defaults,
                    rules = CommunityThemeSubmissionRules(minimumChangedSettings = 1))

            val rejected = result as? CommunityThemeSubmissionResult.Rejected
            assertTrue("$face defaults must be rejected as a zero-change submission", rejected != null)
            assertEquals(0, rejected!!.changedSettings)
            assertEquals(
                    listOf(CommunityThemeSubmissionRejection.InsufficientOriginality(0, 1)),
                    rejected.reasons)
        }
    }

    private fun materializedFixtureDefaults(face: String): Map<String, FixtureSetting> =
            fixture.baseDefaults + fixture.faceOverrides[face].orEmpty()

    private fun fixtureSetting(defaultValue: Any): FixtureSetting = when (defaultValue) {
        is String -> FixtureSetting("string", defaultValue)
        is Boolean -> FixtureSetting("boolean", defaultValue)
        is Int -> FixtureSetting("int", defaultValue)
        else -> error("Unsupported public-theme preference type: ${defaultValue::class.java.name}")
    }

    private fun fixtureSetting(rawValue: String, definitionDefault: Any): FixtureSetting =
            when (definitionDefault) {
                is String -> FixtureSetting("string", rawValue)
                is Boolean -> FixtureSetting(
                        "boolean",
                        checkNotNull(rawValue.toBooleanStrictOrNull()) {
                            "Expected strict boolean face override, got $rawValue"
                        })
                is Int -> FixtureSetting(
                        "int",
                        rawValue.toIntOrNull()
                                ?: error("Expected integer face override, got $rawValue"))
                else -> error("Unsupported public-theme preference type: ${definitionDefault::class.java.name}")
            }

    private data class Fixture(
            val faces: List<String>,
            val baseDefaults: Map<String, FixtureSetting>,
            val faceOverrides: Map<String, Map<String, FixtureSetting>>
    )

    private data class FixtureSetting(val type: String, val value: Any) {
        fun toCommunityThemeValue(): CommunityThemeSettingValue = when (type) {
            "string" -> CommunityThemeSettingValue.StringValue(value as String)
            "boolean" -> CommunityThemeSettingValue.BooleanValue(value as Boolean)
            "int" -> CommunityThemeSettingValue.IntValue(value as Int)
            else -> error("Unsupported fixture type: $type")
        }
    }

    private val fixture: Fixture by lazy {
        val properties = Properties()
        val stream = checkNotNull(
                CommunityThemeDefaultsParityTest::class.java.getResourceAsStream(
                        "/community-theme-defaults.properties")) {
            "Missing shared community-theme defaults fixture"
        }
        stream.use(properties::load)

        check(properties.getProperty("fixture.version") == "1") {
            "Unsupported community-theme defaults fixture version"
        }
        val faces = properties.getProperty("faces")
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                ?: error("Community-theme defaults fixture has no faces")
        val baseDefaults = properties.stringPropertyNames()
                .filter { it.startsWith(BASE_PREFIX) }
                .associate { key ->
                    key.removePrefix(BASE_PREFIX) to parseSetting(properties.getProperty(key))
                }
        val faceOverrides = linkedMapOf<String, MutableMap<String, FixtureSetting>>()
        properties.stringPropertyNames()
                .filter { it.startsWith(OVERRIDE_PREFIX) }
                .forEach { key ->
                    val suffix = key.removePrefix(OVERRIDE_PREFIX)
                    val face = suffix.substringBefore('.')
                    val settingKey = suffix.substringAfter('.', missingDelimiterValue = "")
                    check(face in faces && settingKey.isNotEmpty()) {
                        "Invalid fixture override key: $key"
                    }
                    faceOverrides.getOrPut(face, ::linkedMapOf)[settingKey] =
                            parseSetting(properties.getProperty(key))
                }
        Fixture(faces, baseDefaults, faceOverrides)
    }

    private fun parseSetting(encoded: String): FixtureSetting {
        val typeEnd = encoded.indexOf(':')
        check(typeEnd > 0) { "Invalid community-theme fixture setting: $encoded" }
        val type = encoded.substring(0, typeEnd)
        val rawValue = encoded.substring(typeEnd + 1)
        return when (type) {
            "string" -> FixtureSetting(type, rawValue)
            "boolean" -> FixtureSetting(
                    type,
                    checkNotNull(rawValue.toBooleanStrictOrNull()) {
                        "Invalid boolean fixture value: $rawValue"
                    })
            "int" -> FixtureSetting(
                    type,
                    rawValue.toIntOrNull() ?: error("Invalid integer fixture value: $rawValue"))
            else -> error("Unknown community-theme fixture type: $type")
        }
    }

    private companion object {
        const val BASE_PREFIX = "base."
        const val OVERRIDE_PREFIX = "override."
    }
}
