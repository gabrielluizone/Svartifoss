package com.svartifoss.snfell.view.watchface.theme

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.svartifoss.snfell.BuildConfig
import com.svartifoss.snfell.common.CommunityThemeScreenshots
import com.svartifoss.snfell.common.CommunityThemeSettingValue
import com.svartifoss.snfell.common.CommunityThemeSettings
import com.svartifoss.snfell.common.CommunityThemeSubmissionPolicy
import com.svartifoss.snfell.common.CommunityThemeSubmissionResult
import com.svartifoss.snfell.common.CommunityThemeSubmissionRejection
import com.svartifoss.snfell.common.CommunityThemeSubmissionRules
import com.svartifoss.snfell.common.ArchivedFaces
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * The initial local gate is intentionally conservative. It is user feedback rather than a
 * security boundary: the trusted publisher must apply the same (or stricter) policy before a
 * pending document becomes a public Git commit.
 */
const val COMMUNITY_THEME_MINIMUM_CHANGED_SETTINGS = 12
const val COMMUNITY_THEME_SUBMISSION_WINDOW_MILLIS = 24L * 60L * 60L * 1_000L

/**
 * How many submissions the rolling window allows. Firestore Rules remain the authority; this is
 * the same number named once so the gallery's picker cannot offer more themes than the quota
 * would accept and leave the last pick failing at the write.
 */
const val COMMUNITY_THEME_SUBMISSIONS_PER_WINDOW = 10

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

    /**
     * These exact settings are already published on this base face.
     *
     * Refused here so a person learns it before being asked to sign in. The trusted publisher
     * applies the same rule and stays the boundary: this check reads the catalogue the phone has
     * already downloaded, so it is silent about a duplicate the device has never seen.
     */
    object ExactDuplicate : CommunityThemeSubmissionPreflight

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
    /** These exact settings are already published on this base face. */
    object ExactDuplicate : CommunityThemeQueueResult
    /** Another account already owns the case-insensitive canonical form of this name. */
    object AuthorNameUnavailable : CommunityThemeQueueResult
    /** This account already owns a different immutable public author identity. */
    data class AuthorNameLocked(val authorName: String) : CommunityThemeQueueResult
    object InvalidRequest : CommunityThemeQueueResult
    data class Failed(val error: Throwable) : CommunityThemeQueueResult
}

/**
 * A UI-only early explanation based on the server-authored timestamp history. Firestore Rules
 * remain the authority for the actual write, including concurrent submissions from another device.
 * Legacy and malformed quota documents deliberately return false so their normal rule path stays
 * fail-closed instead of being mislabeled as a rate limit.
 */
internal fun isSubmissionWindowFull(
        quotaSchemaVersion: Long?,
        windowSubmissionCount: Int?,
        windowStartedAtMillis: Long?,
        nowMillis: Long
): Boolean = quotaSchemaVersion == 3L &&
        windowSubmissionCount != null &&
        windowSubmissionCount >= COMMUNITY_THEME_SUBMISSIONS_PER_WINDOW &&
        windowStartedAtMillis != null &&
        nowMillis < windowStartedAtMillis + COMMUNITY_THEME_SUBMISSION_WINDOW_MILLIS

/** Android/Firebase-free adapter from a strict local draft into the shared policy model. */
internal object CommunityThemeSubmissionPreflightEvaluator {

    fun evaluate(
            draft: CommunityThemeSubmissionDraft,
            constraints: CommunityThemeConstraints?,
            publishedSettingsDigests: Set<String> = emptySet()
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
                CommunityThemeSubmissionRules(
                        minimumChangedSettings = 0,
                        publishedSettingsDigests = publishedSettingsDigests))) {
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
            // With a zero originality floor, the only reason the shared policy can reject here is
            // an exact duplicate. Anything else is an unexpected state and must not become a
            // publishable Firebase request.
            is CommunityThemeSubmissionResult.Rejected ->
                if (result.reasons.any { it is CommunityThemeSubmissionRejection.ExactDuplicate }) {
                    CommunityThemeSubmissionPreflight.ExactDuplicate
                } else {
                    CommunityThemeSubmissionPreflight.InvalidDraft
                }
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
    private val onlineThemes: OnlineThemesRepository by lazy { OnlineThemesRepository(appContext) }

    /** The cheap, offline gates: schema, base face and originality. No duplicate check. */
    fun preflight(draft: CommunityThemeSubmissionDraft): CommunityThemeSubmissionPreflight =
            CommunityThemeSubmissionPreflightEvaluator.evaluate(draft, constraints)

    /**
     * [preflight] plus the exact-duplicate check against the published catalogue.
     *
     * Deliberately **fails open**: a catalogue this device cannot reach yields nothing to compare
     * against, and a submission is not blocked because the network was down. The publisher
     * re-applies the rule from the authoritative side and rejects a duplicate that slips past.
     */
    suspend fun preflightAgainstPublished(
            draft: CommunityThemeSubmissionDraft
    ): CommunityThemeSubmissionPreflight = CommunityThemeSubmissionPreflightEvaluator.evaluate(
            draft,
            constraints,
            onlineThemes.publishedSettingsDigests())

    /** Presents Credential Manager only after an explicit submission action. */
    suspend fun signInWithGoogle(activity: android.app.Activity): CommunityThemeGoogleSignInResult =
            googleAuthentication.signIn(activity)

    /**
     * Sends one strict, immutable queue document. It never trusts an email/display name from the
     * Google account. The first submission atomically reserves one globally unique author name for
     * that UID; later submissions can use only that identity (or hide its credit as Anonymous).
     */
    suspend fun enqueue(
            preflight: CommunityThemeSubmissionPreflight.Ready,
            rawAuthor: String,
            publishAnonymously: Boolean,
            moderationPreviewWebpBase64: String
    ): CommunityThemeQueueResult {
        val draft = preflight.draft
        val verifiedPreflight = this.preflightAgainstPublished(draft)
        if (verifiedPreflight is CommunityThemeSubmissionPreflight.ExactDuplicate) {
            return CommunityThemeQueueResult.ExactDuplicate
        }
        if (verifiedPreflight !is CommunityThemeSubmissionPreflight.Ready) {
            return CommunityThemeQueueResult.InvalidRequest
        }
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
        val requestedIdentity = CommunityThemeAuthorNames.identity(user.uid, rawAuthor)
                ?: return CommunityThemeQueueResult.InvalidRequest
        return try {
            val intakeDocument = firestore.collection(INTAKE_COLLECTION).document(draft.id)
            val quotaDocument = firestore.collection(SUBMISSION_QUOTA_COLLECTION).document(user.uid)
            val accountDocument = firestore.collection(AUTHOR_ACCOUNT_COLLECTION).document(user.uid)
            firestore.runTransaction { transaction ->
                /*
                 * Account and reservation are a reciprocal pair. Reading both before any write
                 * makes the first claim race-safe across devices and accounts: Firestore retries
                 * the loser against the winner's committed reservation.
                 */
                val accountSnapshot = transaction.get(accountDocument)
                val existingIdentity = if (accountSnapshot.exists()) {
                    CommunityThemeAuthorNames.parse(user.uid, accountSnapshot.data)
                            ?: return@runTransaction CommunityThemeQueueResult.InvalidRequest
                } else {
                    null
                }
                if (existingIdentity != null &&
                        existingIdentity.authorKey != requestedIdentity.authorKey) {
                    return@runTransaction CommunityThemeQueueResult.AuthorNameLocked(
                            existingIdentity.authorName)
                }
                val identity = existingIdentity ?: requestedIdentity
                val authorNameDocument = firestore.collection(AUTHOR_NAME_COLLECTION)
                        .document(identity.authorKey)
                val authorNameSnapshot = transaction.get(authorNameDocument)
                if (existingIdentity == null && authorNameSnapshot.exists()) {
                    return@runTransaction CommunityThemeQueueResult.AuthorNameUnavailable
                }
                if (existingIdentity != null &&
                        (!authorNameSnapshot.exists() ||
                                !CommunityThemeAuthorNames.claimMatches(
                                        identity,
                                        authorNameSnapshot.data))) {
                    return@runTransaction CommunityThemeQueueResult.InvalidRequest
                }

                // The quota document is readable only by its owner. Firestore rules require this
                // write and the new intake document to appear in the same atomic transaction.
                // A modified client cannot skip it or reset the rolling history between
                // submissions. Version 2 permits three related submissions in any 24-hour window
                // instead of unexpectedly treating a whole account as one-and-done. The scalar
                // history intentionally avoids a Firestore server-timestamp sentinel in an array.
                val previousQuota = transaction.get(quotaDocument)
                val previousCount = previousQuota.getLong("submissionCount") ?: 0L
                val schemaVersion = previousQuota.getLong("quotaSchemaVersion")
                val now = System.currentTimeMillis()

                val windowCount = previousQuota.getLong("windowSubmissionCount")
                        ?.takeIf { it in 1L..COMMUNITY_THEME_SUBMISSIONS_PER_WINDOW.toLong() }
                        ?.toInt()
                val windowStartedAt = previousQuota.getTimestamp("windowStartedAt")
                val isWindowQuota = schemaVersion == SUBMISSION_QUOTA_SCHEMA_VERSION.toLong() &&
                        windowCount != null && windowStartedAt != null

                // A v2 record still carries a real window. Migrating must not hand back a fresh
                // allowance, so the new window opens at the oldest submission v2 remembers and
                // inherits its count -- the rules assert exactly the same thing.
                val rollingCount = previousQuota.getLong("recentSubmissionCount")
                        ?.takeIf { it in 1L..3L }
                        ?.toInt()
                val rollingFirstAt = previousQuota.getTimestamp("recentSubmissionFirstAt")
                val isRollingQuota = schemaVersion == LEGACY_ROLLING_QUOTA_SCHEMA_VERSION.toLong() &&
                        rollingCount != null && rollingFirstAt != null

                if (isWindowQuota && isSubmissionWindowFull(
                                quotaSchemaVersion = schemaVersion,
                                windowSubmissionCount = windowCount,
                                windowStartedAtMillis = windowStartedAt?.toDate()?.time,
                                nowMillis = now)) {
                    return@runTransaction CommunityThemeQueueResult.SubmissionLimitReached
                }
                val quota = hashMapOf<String, Any>(
                        "ownerUid" to user.uid,
                        "quotaSchemaVersion" to SUBMISSION_QUOTA_SCHEMA_VERSION,
                        "submissionCount" to previousCount + 1L,
                        "lastSubmissionAt" to FieldValue.serverTimestamp(),
                        "lastSubmissionId" to draft.id)
                val carriedWindowStart = when {
                    isWindowQuota && now < windowStartedAt!!.toDate().time +
                            COMMUNITY_THEME_SUBMISSION_WINDOW_MILLIS ->
                        windowStartedAt to windowCount!! + 1
                    isRollingQuota && now < rollingFirstAt!!.toDate().time +
                            COMMUNITY_THEME_SUBMISSION_WINDOW_MILLIS ->
                        rollingFirstAt to rollingCount!! + 1
                    // Either the window has expired, or this is a v1/legacy document with no
                    // trustworthy history at all. Both open a window at `now`, which is the one
                    // migration write the rules permit without a wait.
                    else -> null
                }
                if (carriedWindowStart == null) {
                    quota["windowStartedAt"] = FieldValue.serverTimestamp()
                    quota["windowSubmissionCount"] = 1
                } else {
                    quota["windowStartedAt"] = carriedWindowStart.first
                    quota["windowSubmissionCount"] = carriedWindowStart.second
                }
                if (existingIdentity == null) {
                    transaction.set(accountDocument, hashMapOf<String, Any>(
                            "ownerUid" to user.uid,
                            "accountSchemaVersion" to CommunityThemeAuthorNames.ACCOUNT_SCHEMA_VERSION,
                            "authorName" to identity.authorName,
                            "authorKey" to identity.authorKey,
                            "createdAt" to FieldValue.serverTimestamp()))
                    transaction.set(authorNameDocument, hashMapOf<String, Any>(
                            "ownerUid" to user.uid,
                            "nameSchemaVersion" to CommunityThemeAuthorNames.NAME_SCHEMA_VERSION,
                            "authorName" to identity.authorName,
                            "authorKey" to identity.authorKey,
                            "createdAt" to FieldValue.serverTimestamp()))
                }
                transaction.set(quotaDocument, quota)
                // `set` is allowed only for a create by the rules. A UUID collision therefore
                // fails safely instead of overwriting an intake document.
                transaction.set(intakeDocument, hashMapOf<String, Any>(
                        "ownerUid" to user.uid,
                        "status" to "pending",
                        "submissionSchemaVersion" to SUBMISSION_SCHEMA_VERSION,
                        "name" to draft.name,
                        "author" to if (publishAnonymously) {
                            CommunityThemeAuthorNames.ANONYMOUS_CREDIT
                        } else {
                            identity.authorName
                        },
                        "baseFace" to draft.baseFace,
                        "profileSchemaVersion" to WatchThemeRepository.LIBRARY_SCHEMA,
                        "revision" to 1,
                        "profileJson" to draft.serializedProfile,
                        "settingsDigest" to preflight.settingsDigest,
                        "moderationPreviewWebpBase64" to moderationPreviewWebpBase64,
                        "clientVersion" to BuildConfig.VERSION_NAME,
                        "createdAt" to FieldValue.serverTimestamp()))
                CommunityThemeQueueResult.Queued
            }.await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommunityThemeQueueResult.Failed(error)
        }
    }

    /**
     * Attaches one author screenshot to a submission that has already been queued.
     *
     * Deliberately its own write, *after* the intake transaction, and both halves matter.
     *
     * Its own, because that transaction writes four documents whose rules share one 1000-expression
     * budget -- a limit reached only on an account's second submission and reported as an
     * indistinguishable PERMISSION_DENIED. A picture has nothing to do with quota or name
     * reservation, so it must not spend that budget.
     *
     * After, because the rules bind a screenshot to an intake that already exists, belongs to the
     * caller, and is still pending. Written first it could only be checked against an invented
     * UUID, which would let any signed-in account store unlimited 128 KB documents outside the
     * three-per-day limit.
     *
     * Returns false rather than throwing: the theme is already submitted at this point, and a
     * missing picture is exactly the state of an author who attached none.
     */
    suspend fun attachScreenshot(
            themeId: String,
            surface: String,
            webpBase64: String
    ): Boolean {
        if (!isCanonicalUuid(themeId) ||
                surface !in CommunityThemeScreenshots.SURFACES ||
                !CommunityThemeScreenshots.isSubmittableEncoding(webpBase64)) {
            return false
        }
        val user = auth.currentUser ?: return false
        return try {
            firestore.collection(SHOTS_COLLECTION)
                    .document(themeId)
                    .collection(SHOT_SURFACES_SUBCOLLECTION)
                    .document(surface)
                    .set(hashMapOf<String, Any>(
                            "ownerUid" to user.uid,
                            "surface" to surface,
                            "webpBase64" to webpBase64,
                            "createdAt" to FieldValue.serverTimestamp()))
                    .await()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Could not attach the screenshot of a community theme")
            false
        }
    }

    /** Mirrors the UUIDv4-only Firestore document-id contract. */
    private fun isCanonicalUuid(raw: String): Boolean = UUID_V4.matches(raw)

    private companion object {
        const val INTAKE_COLLECTION = "themeIntake"
        const val SUBMISSION_QUOTA_COLLECTION = "communityThemeSubmissionQuota"
        const val AUTHOR_ACCOUNT_COLLECTION = "communityThemeAccounts"
        const val AUTHOR_NAME_COLLECTION = "communityThemeAuthorNames"
        const val SHOTS_COLLECTION = "themeIntakeShots"
        const val SHOT_SURFACES_SUBCOLLECTION = "surfaces"
        const val SUBMISSION_SCHEMA_VERSION = 2
        const val SUBMISSION_QUOTA_SCHEMA_VERSION = 3
        const val LEGACY_ROLLING_QUOTA_SCHEMA_VERSION = 2
        const val MAX_PUBLIC_TEXT_LENGTH = 48
        const val MAX_PROFILE_JSON_LENGTH = 24 * 1024
        const val MAX_PREVIEW_BASE64_LENGTH = 64 * 1024
        val BASE64 = Regex("^[A-Za-z0-9+/]+={0,2}$")
        val UUID_V4 = Regex(
                "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
