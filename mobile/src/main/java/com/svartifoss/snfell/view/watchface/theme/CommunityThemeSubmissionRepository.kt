package com.svartifoss.snfell.view.watchface.theme

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.svartifoss.snfell.BuildConfig
import com.svartifoss.snfell.common.CommunityThemeSettingValue
import com.svartifoss.snfell.common.CommunityThemeSettings
import com.svartifoss.snfell.common.CommunityThemeSubmissionPolicy
import com.svartifoss.snfell.common.CommunityThemeSubmissionResult
import com.svartifoss.snfell.common.CommunityThemeSubmissionRules
import com.svartifoss.snfell.common.ArchivedFaces
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * The initial local gate is intentionally conservative. It is user feedback rather than a
 * security boundary: the trusted publisher must apply the same (or stricter) policy before a
 * pending document becomes a public Git commit.
 */
const val COMMUNITY_THEME_MINIMUM_CHANGED_SETTINGS = 12
const val COMMUNITY_THEME_SUBMISSION_WINDOW_MILLIS = 24L * 60L * 60L * 1_000L

sealed interface CommunityThemeSubmissionPreflight {
    data class Ready(
            val draft: CommunityThemeSubmissionDraft,
            val changedSettings: Int,
            val settingsDigest: String
    ) : CommunityThemeSubmissionPreflight

    data class InsufficientOriginality(
            val changedSettings: Int,
            val minimumRequired: Int
    ) : CommunityThemeSubmissionPreflight

    object InvalidDraft : CommunityThemeSubmissionPreflight
}

sealed interface CommunityThemeGoogleSignInResult {
    object Authenticated : CommunityThemeGoogleSignInResult
    object Cancelled : CommunityThemeGoogleSignInResult
    data class Failed(val error: Throwable) : CommunityThemeGoogleSignInResult
}

sealed interface CommunityThemeQueueResult {
    object Queued : CommunityThemeQueueResult
    /** The current server-authored v2 quota already has three submissions in its rolling window. */
    object SubmissionLimitReached : CommunityThemeQueueResult
    object NotAuthenticated : CommunityThemeQueueResult
    object InvalidRequest : CommunityThemeQueueResult
    data class Failed(val error: Throwable) : CommunityThemeQueueResult
}

/**
 * A UI-only early explanation based on the server-authored timestamp history. Firestore Rules
 * remain the authority for the actual write, including concurrent submissions from another device.
 * Legacy and malformed quota documents deliberately return false so their normal rule path stays
 * fail-closed instead of being mislabeled as a rate limit.
 */
internal fun isRollingSubmissionLimitReached(
        quotaSchemaVersion: Long?,
        recentSubmissionCount: Int?,
        recentSubmissionFirstAtMillis: Long?,
        nowMillis: Long
): Boolean = quotaSchemaVersion == 2L &&
        recentSubmissionCount == 3 &&
        recentSubmissionFirstAtMillis != null &&
        nowMillis < recentSubmissionFirstAtMillis + COMMUNITY_THEME_SUBMISSION_WINDOW_MILLIS

/** Android/Firebase-free adapter from a strict local draft into the shared policy model. */
internal object CommunityThemeSubmissionPreflightEvaluator {

    fun evaluate(
            draft: CommunityThemeSubmissionDraft,
            constraints: CommunityThemeConstraints?
    ): CommunityThemeSubmissionPreflight {
        constraints ?: return CommunityThemeSubmissionPreflight.InvalidDraft
        if (draft.baseFace !in ThemeAppearance.ALLOWED_BASE_FACES ||
                draft.baseFace in ArchivedFaces.KEYS) {
            return CommunityThemeSubmissionPreflight.InvalidDraft
        }
        val defaults = FaceScopedPreferences.SCOPED_DEFINITIONS.associate { definition ->
            definition.key to defaultValue(draft.baseFace, definition.key, definition.defaultValue)
        }
        // A raw data class can be constructed outside WatchThemeRepository. Keep the complete
        // snapshot invariant at this boundary instead of treating missing settings as defaults.
        if (draft.settings.keys != defaults.keys) {
            return CommunityThemeSubmissionPreflight.InvalidDraft
        }
        if (draft.settings.any { (key, value) -> !constraints.accepts(key, value) }) {
            return CommunityThemeSubmissionPreflight.InvalidDraft
        }
        val candidate = CommunityThemeSettings(
                baseFace = draft.baseFace,
                values = draft.settings.mapValues { (_, value) -> value.toPolicyValue() })
        return when (val result = CommunityThemeSubmissionPolicy.evaluate(
                candidate,
                defaults,
                // The shared policy remains the canonical full-map validator and digest writer.
                // Applicability is intentionally a higher-level UI/publisher rule: filtering the
                // map before passing it here would change the public SHA-256 fingerprint.
                CommunityThemeSubmissionRules(minimumChangedSettings = 0))) {
            is CommunityThemeSubmissionResult.Accepted -> {
                val changedSettings = result.normalizedSettings.values.count { (key, value) ->
                    value != defaults.getValue(key) && constraints.isOriginalityApplicable(
                            key,
                            draft.settings,
                            draft.baseFace)
                }
                if (changedSettings >= COMMUNITY_THEME_MINIMUM_CHANGED_SETTINGS) {
                    CommunityThemeSubmissionPreflight.Ready(
                            draft = draft,
                            changedSettings = changedSettings,
                            settingsDigest = result.settingsDigest)
                } else {
                    CommunityThemeSubmissionPreflight.InsufficientOriginality(
                            changedSettings = changedSettings,
                            minimumRequired = COMMUNITY_THEME_MINIMUM_CHANGED_SETTINGS)
                }
            }
            // With a zero floor and no client-side duplicate index this is defensive only. Do not
            // turn an unexpected policy state into a publishable Firebase request.
            is CommunityThemeSubmissionResult.Rejected -> CommunityThemeSubmissionPreflight.InvalidDraft
            is CommunityThemeSubmissionResult.InvalidSettings ->
                CommunityThemeSubmissionPreflight.InvalidDraft
        }
    }

    private fun defaultValue(
            baseFace: String,
            key: String,
            definitionDefault: Any
    ): CommunityThemeSettingValue {
        val faceValue = FaceScopedPreferences.perFaceDefault(baseFace, key)
        return when (definitionDefault) {
            is String -> CommunityThemeSettingValue.StringValue(faceValue ?: definitionDefault)
            is Boolean -> CommunityThemeSettingValue.BooleanValue(
                    faceValue?.toBooleanStrictOrNull() ?: definitionDefault)
            is Int -> CommunityThemeSettingValue.IntValue(
                    faceValue?.toIntOrNull() ?: definitionDefault)
            else -> error("Unsupported community-theme setting type for $key")
        }
    }

    private fun WatchThemeValue.toPolicyValue(): CommunityThemeSettingValue = when (this) {
        is WatchThemeValue.Text -> CommunityThemeSettingValue.StringValue(value)
        is WatchThemeValue.Flag -> CommunityThemeSettingValue.BooleanValue(value)
        is WatchThemeValue.Number -> CommunityThemeSettingValue.IntValue(value)
    }
}

/**
 * Auth and Firestore boundary for an explicit community-theme submission.
 *
 * Browsing never instantiates a network request through this class. The activity first runs
 * [preflight], then asks the user to select a Google account, and finally calls [enqueue]. The
 * immutable Firestore contract is duplicated here and in `firestore.rules`; rules remain the
 * authority when a modified client tries to send a different envelope.
 */
class CommunityThemeSubmissionRepository(
        context: Context,
        private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val appContext = context.applicationContext
    private val googleAuthentication = CommunityThemeGoogleAuthentication(auth)
    private val constraints: CommunityThemeConstraints? by lazy {
        CommunityThemeConstraints.load(appContext)
    }

    fun preflight(draft: CommunityThemeSubmissionDraft): CommunityThemeSubmissionPreflight =
            CommunityThemeSubmissionPreflightEvaluator.evaluate(draft, constraints)

    /** Presents Credential Manager only after an explicit submission action. */
    suspend fun signInWithGoogle(activity: android.app.Activity): CommunityThemeGoogleSignInResult =
            googleAuthentication.signIn(activity)

    /**
     * Sends one strict, immutable queue document. It never trusts an email/display name from the
     * Google account; Firestore receives only the Firebase UID and the author pseudonym explicitly
     * entered by the person submitting the theme.
     */
    suspend fun enqueue(
            preflight: CommunityThemeSubmissionPreflight.Ready,
            rawAuthor: String,
            moderationPreviewWebpBase64: String
    ): CommunityThemeQueueResult {
        val author = normalizePublicText(rawAuthor) ?: return CommunityThemeQueueResult.InvalidRequest
        val draft = preflight.draft
        val verifiedPreflight = this.preflight(draft) as? CommunityThemeSubmissionPreflight.Ready
                ?: return CommunityThemeQueueResult.InvalidRequest
        if (verifiedPreflight.changedSettings != preflight.changedSettings ||
                verifiedPreflight.settingsDigest != preflight.settingsDigest) {
            return CommunityThemeQueueResult.InvalidRequest
        }
        if (!isCanonicalUuid(draft.id) ||
                draft.name.length !in 1..MAX_PUBLIC_TEXT_LENGTH ||
                draft.serializedProfile.length !in 2..MAX_PROFILE_JSON_LENGTH ||
                moderationPreviewWebpBase64.length !in 4..MAX_PREVIEW_BASE64_LENGTH ||
                !BASE64.matches(moderationPreviewWebpBase64)) {
            return CommunityThemeQueueResult.InvalidRequest
        }
        val user = auth.currentUser ?: return CommunityThemeQueueResult.NotAuthenticated
        val document = hashMapOf<String, Any>(
                "ownerUid" to user.uid,
                "status" to "pending",
                "submissionSchemaVersion" to SUBMISSION_SCHEMA_VERSION,
                "name" to draft.name,
                "author" to author,
                "baseFace" to draft.baseFace,
                "profileSchemaVersion" to WatchThemeRepository.LIBRARY_SCHEMA,
                "revision" to 1,
                "profileJson" to draft.serializedProfile,
                "settingsDigest" to preflight.settingsDigest,
                "moderationPreviewWebpBase64" to moderationPreviewWebpBase64,
                "clientVersion" to BuildConfig.VERSION_NAME,
                "createdAt" to FieldValue.serverTimestamp())
        return try {
            val intakeDocument = firestore.collection(INTAKE_COLLECTION).document(draft.id)
            val quotaDocument = firestore.collection(SUBMISSION_QUOTA_COLLECTION).document(user.uid)
            firestore.runTransaction { transaction ->
                // The quota document is readable only by its owner. Firestore rules require this
                // write and the new intake document to appear in the same atomic transaction.
                // A modified client cannot skip it or reset the rolling history between
                // submissions. Version 2 permits three related submissions in any 24-hour window
                // instead of unexpectedly treating a whole account as one-and-done. The scalar
                // history intentionally avoids a Firestore server-timestamp sentinel in an array.
                val previousQuota = transaction.get(quotaDocument)
                val previousCount = previousQuota.getLong("submissionCount") ?: 0L
                val recentCount = previousQuota.getLong("recentSubmissionCount")
                        ?.takeIf { it in 1L..3L }
                        ?.toInt()
                val recentFirstAt = previousQuota.getTimestamp("recentSubmissionFirstAt")
                val recentSecondAt = previousQuota.getTimestamp("recentSubmissionSecondAt")
                val previousLastAt = previousQuota.getTimestamp("lastSubmissionAt")
                val isRollingQuota = previousQuota.getLong("quotaSchemaVersion") ==
                        SUBMISSION_QUOTA_SCHEMA_VERSION.toLong() &&
                        recentCount != null && recentCount in 1..3 &&
                        recentFirstAt != null &&
                        previousLastAt != null &&
                        (recentCount != 3 || recentSecondAt != null)
                if (isRollingQuota && isRollingSubmissionLimitReached(
                                quotaSchemaVersion = previousQuota.getLong("quotaSchemaVersion"),
                                recentSubmissionCount = recentCount,
                                recentSubmissionFirstAtMillis = recentFirstAt?.toDate()?.time,
                                nowMillis = System.currentTimeMillis())) {
                    return@runTransaction CommunityThemeQueueResult.SubmissionLimitReached
                }
                val quota = hashMapOf<String, Any>(
                        "ownerUid" to user.uid,
                        "quotaSchemaVersion" to SUBMISSION_QUOTA_SCHEMA_VERSION,
                        "submissionCount" to previousCount + 1L,
                        "lastSubmissionAt" to FieldValue.serverTimestamp(),
                        "lastSubmissionId" to draft.id)
                when (recentCount.takeIf { isRollingQuota }) {
                    1 -> {
                        quota["recentSubmissionCount"] = 2
                        quota["recentSubmissionFirstAt"] = recentFirstAt!!
                    }
                    2 -> {
                        quota["recentSubmissionCount"] = 3
                        quota["recentSubmissionFirstAt"] = recentFirstAt!!
                        quota["recentSubmissionSecondAt"] = previousLastAt!!
                    }
                    3 -> {
                        quota["recentSubmissionCount"] = 3
                        quota["recentSubmissionFirstAt"] = recentSecondAt!!
                        quota["recentSubmissionSecondAt"] = previousLastAt!!
                    }
                    else -> {
                        // A v1/legacy document has no trustworthy rolling history. The rules
                        // permit exactly this one migration write without a 24-hour wait.
                        quota["recentSubmissionCount"] = 1
                        quota["recentSubmissionFirstAt"] = FieldValue.serverTimestamp()
                    }
                }
                transaction.set(quotaDocument, quota)
                // `set` is allowed only for a create by the rules. A UUID collision therefore
                // fails safely instead of overwriting an intake document.
                transaction.set(intakeDocument, document)
                CommunityThemeQueueResult.Queued
            }.await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommunityThemeQueueResult.Failed(error)
        }
    }

    private fun normalizePublicText(raw: String): String? {
        val normalized = raw.trim().replace(WHITESPACE, " ")
        return normalized.takeIf {
            it.isNotBlank() &&
                    it.length <= MAX_PUBLIC_TEXT_LENGTH &&
                    it.none(Character::isISOControl)
        }
    }

    /** Mirrors the UUIDv4-only Firestore document-id contract. */
    private fun isCanonicalUuid(raw: String): Boolean = UUID_V4.matches(raw)

    private companion object {
        const val INTAKE_COLLECTION = "themeIntake"
        const val SUBMISSION_QUOTA_COLLECTION = "communityThemeSubmissionQuota"
        const val SUBMISSION_SCHEMA_VERSION = 1
        const val SUBMISSION_QUOTA_SCHEMA_VERSION = 2
        const val MAX_PUBLIC_TEXT_LENGTH = 48
        const val MAX_PROFILE_JSON_LENGTH = 24 * 1024
        const val MAX_PREVIEW_BASE64_LENGTH = 64 * 1024
        val WHITESPACE = Regex("\\s+")
        val BASE64 = Regex("^[A-Za-z0-9+/]+={0,2}$")
        val UUID_V4 = Regex(
                "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
