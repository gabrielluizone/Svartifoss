package com.svartifoss.snfell.common

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * Android-free data model for the typed appearance settings carried by a community theme.
 *
 * This deliberately mirrors the public profile's three JSON value types without depending on
 * the phone module's JSON or preference classes. The submission boundary maps its parsed values
 * to this model before running [CommunityThemeSubmissionPolicy].
 */
sealed interface CommunityThemeSettingValue {
    val kind: CommunityThemeSettingKind

    data class StringValue(val value: String) : CommunityThemeSettingValue {
        override val kind = CommunityThemeSettingKind.STRING
    }

    data class BooleanValue(val value: Boolean) : CommunityThemeSettingValue {
        override val kind = CommunityThemeSettingKind.BOOLEAN
    }

    data class IntValue(val value: Int) : CommunityThemeSettingValue {
        override val kind = CommunityThemeSettingKind.INT
    }
}

enum class CommunityThemeSettingKind {
    STRING,
    BOOLEAN,
    INT
}

/** A face plus the appearance settings that resolve its look. */
data class CommunityThemeSettings(
        val baseFace: String,
        val values: Map<String, CommunityThemeSettingValue>
)

/**
 * Inputs controlled by the caller, rather than a product policy hidden in this utility.
 *
 * The Firebase queue (and any later server-side gate) supplies [minimumChangedSettings] and the
 * already-published digests. This module intentionally does not choose an originality threshold,
 * store accounts, or implement rate limiting.
 */
data class CommunityThemeSubmissionRules(
        val minimumChangedSettings: Int,
        val publishedSettingsDigests: Set<String> = emptySet()
) {
    init {
        require(minimumChangedSettings >= 0) {
            "minimumChangedSettings must not be negative"
        }
    }
}

/** A type mismatch is invalid input, not an artificial "change" from the face default. */
data class CommunityThemeSettingTypeMismatch(
        val key: String,
        val expected: CommunityThemeSettingKind,
        val actual: CommunityThemeSettingKind
)

/** The explicit automated reasons a valid candidate can be rejected before moderation. */
sealed interface CommunityThemeSubmissionRejection {
    data class InsufficientOriginality(
            val changedSettings: Int,
            val minimumRequired: Int
    ) : CommunityThemeSubmissionRejection

    data class ExactDuplicate(val settingsDigest: String) : CommunityThemeSubmissionRejection
}

/**
 * The policy never silently turns malformed input into a low-originality theme. A caller gets a
 * separate result for schema-invalid maps, then an accepted/rejected result for valid maps.
 */
sealed interface CommunityThemeSubmissionResult {
    data class Accepted(
            val normalizedSettings: CommunityThemeSettings,
            val changedSettings: Int,
            val settingsDigest: String
    ) : CommunityThemeSubmissionResult

    data class Rejected(
            val normalizedSettings: CommunityThemeSettings,
            val changedSettings: Int,
            val settingsDigest: String,
            /** Ordered: duplicate first, then originality, so clients can show a stable message. */
            val reasons: List<CommunityThemeSubmissionRejection>
    ) : CommunityThemeSubmissionResult

    data class InvalidSettings(
            val unknownKeys: List<String>,
            val typeMismatches: List<CommunityThemeSettingTypeMismatch>
    ) : CommunityThemeSubmissionResult
}

/**
 * Deterministic, Android-free preflight gates for a community-theme submission.
 *
 * A public profile may omit settings that equal a face default. [evaluate] first materializes that
 * partial map over [defaults], so an omitted default and an explicitly supplied default have the
 * same diff count and digest. The fingerprint includes [CommunityThemeSettings.baseFace]: the same
 * settings map can render differently on two face layouts and must not be treated as a duplicate.
 */
object CommunityThemeSubmissionPolicy {

    private const val DIGEST_PREFIX = "sha256:"
    private val CANONICAL_HEADER = "svartifoss-community-theme-settings-v1".toByteArray(Charsets.UTF_8)

    /**
     * Resolves [candidate] over the complete defaults for its base face, validates the key/type
     * boundary, counts actual differences, and checks the caller-supplied duplicate index.
     */
    fun evaluate(
            candidate: CommunityThemeSettings,
            defaults: Map<String, CommunityThemeSettingValue>,
            rules: CommunityThemeSubmissionRules
    ): CommunityThemeSubmissionResult {
        val invalid = validate(candidate.values, defaults)
        if (invalid != null) return invalid

        val normalized = CommunityThemeSettings(
                baseFace = candidate.baseFace,
                values = defaults + candidate.values)
        val changed = defaults.count { (key, defaultValue) ->
            normalized.values.getValue(key) != defaultValue
        }
        val digest = settingsDigest(normalized)
        val reasons = buildList {
            if (digest in rules.publishedSettingsDigests) {
                add(CommunityThemeSubmissionRejection.ExactDuplicate(digest))
            }
            if (changed < rules.minimumChangedSettings) {
                add(CommunityThemeSubmissionRejection.InsufficientOriginality(
                        changedSettings = changed,
                        minimumRequired = rules.minimumChangedSettings))
            }
        }

        return if (reasons.isEmpty()) {
            CommunityThemeSubmissionResult.Accepted(normalized, changed, digest)
        } else {
            CommunityThemeSubmissionResult.Rejected(normalized, changed, digest, reasons)
        }
    }

    /**
     * Canonical binary form used for [settingsDigest]. It is deliberately length-prefixed and
     * type-tagged: map order, separators inside text, and values such as `"1"` versus `1` cannot
     * change or collide with the result. Keys use Kotlin's locale-independent natural order.
     */
    fun canonicalBytes(settings: CommunityThemeSettings): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { sink ->
            sink.write(CANONICAL_HEADER)
            sink.writeByte(0)
            sink.writeCanonicalString(settings.baseFace)
            sink.writeInt(settings.values.size)
            settings.values.toSortedMap().forEach { (key, value) ->
                sink.writeCanonicalString(key)
                when (value) {
                    is CommunityThemeSettingValue.StringValue -> {
                        sink.writeByte(1)
                        sink.writeCanonicalString(value.value)
                    }
                    is CommunityThemeSettingValue.BooleanValue -> {
                        sink.writeByte(2)
                        sink.writeBoolean(value.value)
                    }
                    is CommunityThemeSettingValue.IntValue -> {
                        sink.writeByte(3)
                        sink.writeInt(value.value)
                    }
                }
            }
        }
        return output.toByteArray()
    }

    /** Stable lower-case SHA-256 fingerprint of [canonicalBytes]. */
    fun settingsDigest(settings: CommunityThemeSettings): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(canonicalBytes(settings))
        return DIGEST_PREFIX + hash.toHex()
    }

    private fun validate(
            values: Map<String, CommunityThemeSettingValue>,
            defaults: Map<String, CommunityThemeSettingValue>
    ): CommunityThemeSubmissionResult.InvalidSettings? {
        val unknownKeys = (values.keys - defaults.keys).sorted()
        val typeMismatches = values.entries.mapNotNull { (key, value) ->
            val defaultValue = defaults[key] ?: return@mapNotNull null
            if (value.kind == defaultValue.kind) null else CommunityThemeSettingTypeMismatch(
                    key = key,
                    expected = defaultValue.kind,
                    actual = value.kind)
        }.sortedBy { it.key }
        return if (unknownKeys.isEmpty() && typeMismatches.isEmpty()) null else {
            CommunityThemeSubmissionResult.InvalidSettings(unknownKeys, typeMismatches)
        }
    }

    private fun DataOutputStream.writeCanonicalString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        val digits = "0123456789abcdef"
        for (byte in this@toHex) {
            val unsigned = byte.toInt() and 0xff
            append(digits[unsigned ushr 4])
            append(digits[unsigned and 0x0f])
        }
    }
}
