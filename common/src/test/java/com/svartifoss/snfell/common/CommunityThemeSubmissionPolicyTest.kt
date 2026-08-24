package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityThemeSubmissionPolicyTest {

    @Test
    fun `partial and explicitly materialized defaults have the same canonical digest`() {
        val defaults = defaults()
        val partial = CommunityThemeSettings(
                baseFace = "poster",
                values = linkedMapOf("size" to CommunityThemeSettingValue.IntValue(12)))
        val explicit = CommunityThemeSettings(
                baseFace = "poster",
                values = linkedMapOf(
                        "visible" to CommunityThemeSettingValue.BooleanValue(true),
                        "style" to CommunityThemeSettingValue.StringValue("plain"),
                        "size" to CommunityThemeSettingValue.IntValue(12)))

        val partialResult = CommunityThemeSubmissionPolicy.evaluate(
                partial, defaults, CommunityThemeSubmissionRules(minimumChangedSettings = 1))
                as CommunityThemeSubmissionResult.Accepted
        val explicitResult = CommunityThemeSubmissionPolicy.evaluate(
                explicit, defaults, CommunityThemeSubmissionRules(minimumChangedSettings = 1))
                as CommunityThemeSubmissionResult.Accepted

        assertEquals(1, partialResult.changedSettings)
        assertEquals(partialResult.normalizedSettings, explicitResult.normalizedSettings)
        assertEquals(partialResult.settingsDigest, explicitResult.settingsDigest)
    }

    @Test
    fun `digest is stable across map order and distinguishes typed values and base faces`() {
        val first = CommunityThemeSettings(
                baseFace = "poster",
                values = linkedMapOf(
                        "number" to CommunityThemeSettingValue.IntValue(1),
                        "word" to CommunityThemeSettingValue.StringValue("one")))
        val reordered = CommunityThemeSettings(
                baseFace = "poster",
                values = linkedMapOf(
                        "word" to CommunityThemeSettingValue.StringValue("one"),
                        "number" to CommunityThemeSettingValue.IntValue(1)))
        val stringNumber = CommunityThemeSettings(
                baseFace = "poster",
                values = mapOf(
                        "number" to CommunityThemeSettingValue.StringValue("1"),
                        "word" to CommunityThemeSettingValue.StringValue("one")))

        val digest = CommunityThemeSubmissionPolicy.settingsDigest(first)
        assertEquals(digest, CommunityThemeSubmissionPolicy.settingsDigest(reordered))
        assertNotEquals(digest, CommunityThemeSubmissionPolicy.settingsDigest(stringNumber))
        assertNotEquals(digest, CommunityThemeSubmissionPolicy.settingsDigest(first.copy(baseFace = "note")))
        // Pins the format/version as well as its ordering behavior.
        assertEquals(
                "sha256:491a68363c9e3f536b26aa90fee19ce96b2d27429fd376165e28dc2e7b098b13",
                digest)
    }

    @Test
    fun `low originality and exact duplicates return explicit rejection reasons`() {
        val defaults = defaults()
        val rules = CommunityThemeSubmissionRules(minimumChangedSettings = 2)
        val tooClose = CommunityThemeSubmissionPolicy.evaluate(
                CommunityThemeSettings("poster", mapOf("style" to CommunityThemeSettingValue.StringValue("plain"))),
                defaults,
                rules) as CommunityThemeSubmissionResult.Rejected

        assertEquals(
                listOf(CommunityThemeSubmissionRejection.InsufficientOriginality(0, 2)),
                tooClose.reasons)

        val original = CommunityThemeSubmissionPolicy.evaluate(
                CommunityThemeSettings("poster", mapOf(
                        "style" to CommunityThemeSettingValue.StringValue("ink"),
                        "size" to CommunityThemeSettingValue.IntValue(12))),
                defaults,
                rules) as CommunityThemeSubmissionResult.Accepted
        val duplicate = CommunityThemeSubmissionPolicy.evaluate(
                original.normalizedSettings,
                defaults,
                rules.copy(publishedSettingsDigests = setOf(original.settingsDigest)))
                as CommunityThemeSubmissionResult.Rejected

        assertEquals(
                listOf(CommunityThemeSubmissionRejection.ExactDuplicate(original.settingsDigest)),
                duplicate.reasons)
        assertEquals(2, duplicate.changedSettings)
    }

    @Test
    fun `unknown keys and type mismatches are invalid rather than counted as changes`() {
        val invalid = CommunityThemeSubmissionPolicy.evaluate(
                CommunityThemeSettings("poster", mapOf(
                        "unknown" to CommunityThemeSettingValue.IntValue(1),
                        "size" to CommunityThemeSettingValue.StringValue("12"))),
                defaults(),
                CommunityThemeSubmissionRules(minimumChangedSettings = 1))
                as CommunityThemeSubmissionResult.InvalidSettings

        assertEquals(listOf("unknown"), invalid.unknownKeys)
        assertEquals(
                listOf(CommunityThemeSettingTypeMismatch(
                        key = "size",
                        expected = CommunityThemeSettingKind.INT,
                        actual = CommunityThemeSettingKind.STRING)),
                invalid.typeMismatches)
    }

    @Test
    fun `rules keep the threshold caller supplied`() {
        assertFalse(CommunityThemeSubmissionRules(0).publishedSettingsDigests.isNotEmpty())
        assertTrue(runCatching { CommunityThemeSubmissionRules(-1) }.isFailure)
    }

    private fun defaults(): Map<String, CommunityThemeSettingValue> = linkedMapOf(
            "style" to CommunityThemeSettingValue.StringValue("plain"),
            "size" to CommunityThemeSettingValue.IntValue(10),
            "visible" to CommunityThemeSettingValue.BooleanValue(true))
}
