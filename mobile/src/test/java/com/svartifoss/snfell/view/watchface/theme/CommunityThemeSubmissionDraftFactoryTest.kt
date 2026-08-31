package com.svartifoss.snfell.view.watchface.theme

import com.svartifoss.snfell.common.FaceScopedPreferences
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityThemeSubmissionDraftFactoryTest {

    @Test
    fun `draft has a new public id and complete strict settings without local provenance`() {
        val localId = "11111111-1111-4111-8111-111111111111"
        val publicId = "22222222-2222-4222-8222-222222222222"
        val source = profile(id = localId)

        val result = CommunityThemeSubmissionDraftFactory.build(
                source = source,
                publicName = "  Midnight   Poster  ",
                publicId = publicId,
                nowMillis = 123L)

        val draft = (result as CommunityThemeSubmissionDraftResult.Ready).draft
        assertEquals(publicId, draft.id)
        assertNotEquals(localId, draft.id)
        assertEquals("Midnight Poster", draft.name)
        assertEquals("poster", draft.baseFace)
        assertEquals(FaceScopedPreferences.SCOPED_DEFINITIONS.size, draft.settings.size)

        val json = draft.profileJson()
        assertEquals(WatchThemeRepository.LIBRARY_SCHEMA, json.getInt("schemaVersion"))
        assertEquals(publicId, json.getString("id"))
        assertEquals("Midnight Poster", json.getString("name"))
        assertEquals("poster", json.getString("baseFace"))
        assertEquals(1, json.getInt("revision"))
        assertFalse(json.has("publishedTheme"))
        assertFalse(draft.serializedProfile.contains(localId))

        val settingsJson = json.getJSONObject("settings")
        FaceScopedPreferences.SCOPED_DEFINITIONS.forEach { definition ->
            val value = settingsJson.getJSONObject(definition.key)
            assertTrue(value.has("value"))
            assertEquals(
                    when (definition.defaultValue) {
                        is String -> "string"
                        is Boolean -> "boolean"
                        is Int -> "int"
                        else -> error("Unexpected definition type")
                    },
                    value.getString("type"))
        }

        // Callers receive a fresh object, so an upload adapter cannot mutate the retained payload.
        json.put("id", "33333333-3333-4333-8333-333333333333")
        assertEquals(publicId, draft.profileJson().getString("id"))
    }

    @Test
    fun `factory rejects gallery provenance invalid source and invalid public name`() {
        val publicId = "22222222-2222-4222-8222-222222222222"
        val source = profile()

        assertSame(
                CommunityThemeSubmissionDraftResult.PublishedThemeCannotBeSubmitted,
                CommunityThemeSubmissionDraftFactory.build(
                        source.copy(publishedTheme = PublishedThemeSource(
                                "33333333-3333-4333-8333-333333333333", 1)),
                        "Fork", publicId, 123L))
        assertSame(
                CommunityThemeSubmissionDraftResult.InvalidPublicName,
                CommunityThemeSubmissionDraftFactory.build(
                        source, "x".repeat(49), publicId, 123L))
        assertSame(
                CommunityThemeSubmissionDraftResult.InvalidPublicName,
                CommunityThemeSubmissionDraftFactory.build(
                        source, "Valid\u0000name", publicId, 123L))
        assertSame(
                CommunityThemeSubmissionDraftResult.InvalidProfile,
                CommunityThemeSubmissionDraftFactory.build(
                        source.copy(baseFace = "vinyl"), "Valid name", publicId, 123L))
        assertSame(
                CommunityThemeSubmissionDraftResult.InvalidProfile,
                CommunityThemeSubmissionDraftFactory.build(
                        source.copy(settings = source.settings - "wear_screen_theme"),
                        "Valid name", publicId, 123L))

        val numericDefinition = FaceScopedPreferences.SCOPED_DEFINITIONS.first {
            it.defaultValue is Int
        }
        assertSame(
                CommunityThemeSubmissionDraftResult.InvalidProfile,
                CommunityThemeSubmissionDraftFactory.build(
                        source.copy(settings = source.settings +
                                (numericDefinition.key to WatchThemeValue.Text("12"))),
                        "Valid name", publicId, 123L))

        val textDefinition = FaceScopedPreferences.SCOPED_DEFINITIONS.first {
            it.defaultValue is String
        }
        assertSame(
                CommunityThemeSubmissionDraftResult.InvalidProfile,
                CommunityThemeSubmissionDraftFactory.build(
                        source.copy(settings = source.settings +
                                (textDefinition.key to WatchThemeValue.Text("x".repeat(129)))),
                        "Valid name", publicId, 123L))
    }

    /**
     * A value outside the public vocabulary names itself, instead of joining every other fault
     * under one unactionable "this theme cannot be submitted".
     *
     * It is the only refusal a person can do anything about - the others describe a broken or
     * oversized profile - and reported anonymously it cost a real user days, because a per-face
     * setting made it look like whole layouts were unsubmittable.
     */
    @Test
    fun `factory names the setting holding a value the public vocabulary refuses`() {
        val publicId = "22222222-2222-4222-8222-222222222222"
        val rejected = WatchThemeValue.Text("not-a-font")
        val result = CommunityThemeSubmissionDraftFactory.build(
                profile().copy(settings = profile().settings + ("wear_font" to rejected)),
                "Valid name",
                publicId,
                123L,
                constraints)
        assertEquals(
                CommunityThemeSubmissionDraftResult.UnsupportedSetting("wear_font", rejected),
                result)
    }

    /** The same profile with every value in vocabulary still builds, so the check is not blanket. */
    @Test
    fun `factory accepts a profile whose values are all in the public vocabulary`() {
        val result = CommunityThemeSubmissionDraftFactory.build(
                profile(),
                "Valid name",
                "22222222-2222-4222-8222-222222222222",
                123L,
                constraints)
        assertTrue(result is CommunityThemeSubmissionDraftResult.Ready)
    }

    private val constraints: CommunityThemeConstraints by lazy {
        val file = listOf(
                File("../common/src/main/assets/community-theme-constraints.json"),
                File("common/src/main/assets/community-theme-constraints.json"))
                .first(File::isFile)
        requireNotNull(CommunityThemeConstraints.fromJson(JSONObject(file.readText())))
    }

    private fun profile(
            id: String = "11111111-1111-4111-8111-111111111111"
    ): WatchThemeProfile = WatchThemeProfile(
            id = id,
            name = "Local-only name",
            baseFace = "poster",
            createdAt = 1L,
            updatedAt = 1L,
            revision = 7,
            settings = FaceScopedPreferences.SCOPED_DEFINITIONS.associate { definition ->
                definition.key to defaultValueFor("poster", definition.key, definition.defaultValue)
            })

    private fun defaultValueFor(face: String, key: String, default: Any): WatchThemeValue {
        val faceDefault = FaceScopedPreferences.perFaceDefault(face, key)
        return when (default) {
            is String -> WatchThemeValue.Text(faceDefault ?: default)
            is Boolean -> WatchThemeValue.Flag(faceDefault?.toBooleanStrictOrNull() ?: default)
            is Int -> WatchThemeValue.Number(faceDefault?.toIntOrNull() ?: default)
            else -> error("Unexpected definition type")
        }
    }
}
