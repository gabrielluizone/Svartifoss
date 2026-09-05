package com.svartifoss.snfell.view.watchface.theme

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Why somebody is flagging a published theme.
 *
 * A closed vocabulary rather than free text, because the moderator queue has to be triageable
 * without reading prose first — and because the value crosses into Firestore, where
 * `firestore.rules` carries its own literal copy of this list. That copy cannot execute Kotlin, so
 * the two are pinned together by `CommunityThemeReportVocabularyTest`: a reason added here and not
 * there is refused at the write and reported to the user as an ordinary failure, which is exactly
 * the shape of the base-face bug CLAUDE.md records.
 *
 * [OTHER] exists so the picker is never a reason to abandon a report. It is the one option whose
 * free-text detail is genuinely load-bearing, which is why the dialog asks for it there.
 */
enum class CommunityThemeReportReason(val wireValue: String) {
    /** Offensive or otherwise unacceptable content in the name, author name or artwork. */
    INAPPROPRIATE("inappropriate"),

    /** Passing the listing off as somebody else's, or as an official one. */
    IMPERSONATION("impersonation"),

    /** The listing does not describe what the theme actually looks like. */
    MISLEADING("misleading"),

    /** Renders unusably — unreadable text, invisible controls, a blank screen. */
    ILLEGIBLE("illegible"),

    /** A duplicate, an empty submission, or a listing made to occupy space in the gallery. */
    SPAM("spam"),

    OTHER("other");

    companion object {
        fun fromWireValue(value: String?): CommunityThemeReportReason? =
                entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * How long a reporter's own words may be.
 *
 * Small on purpose. This text is read by a moderator triaging a queue, is never shown to another
 * user and is never republished anywhere, so extra room buys nothing except a larger surface for
 * somebody to store arbitrary content behind a report button. `firestore.rules` enforces the same
 * bound, and this constant exists so the field stops accepting characters before a write is
 * refused for a reason the user cannot see.
 */
const val COMMUNITY_THEME_REPORT_DETAILS_MAX_LENGTH = 300

sealed interface CommunityThemeReportState {
    /** Whether this device has already reported this theme. */
    data class Loaded(val reported: Boolean) : CommunityThemeReportState
    data class Failed(val error: Throwable) : CommunityThemeReportState
}

sealed interface CommunityThemeReportResult {
    object Sent : CommunityThemeReportResult

    /** A report from this account already exists; the rules accept only a create. */
    object AlreadyReported : CommunityThemeReportResult
    object InvalidTheme : CommunityThemeReportResult

    /** The catalogue entry has not reached Firebase's private publication registry yet. */
    object NotReady : CommunityThemeReportResult
    data class Failed(val error: Throwable) : CommunityThemeReportResult
}

/**
 * Reporting a published community theme to the moderators.
 *
 * This is the one write in the whole feature made *about somebody else*, and the design follows
 * from that in three ways.
 *
 * **It costs no identity.** Like a heart tap, it provisions an anonymous Firebase UID silently.
 * Demanding a Google account before somebody can flag offensive content asks the wrong person to
 * pay for it, and the cost — a disposable account can file more than one report across app-data
 * resets — is borne by a queue that a person reads, not by an automatic threshold.
 *
 * **It is never public.** No count is aggregated into the catalogue and no client can read another
 * account's report. A visible report tally would be a brigading tool, and the reported author in
 * particular must never learn who reported them: `firestore.rules` grants the listing only to a
 * moderator, which is also what backs the moderator page's collection-group query.
 *
 * **It cannot be withdrawn.** One document per account per theme, create-only. Allowing a delete
 * would let a coordinated group probe which reports a moderator had already acted on.
 */
class CommunityThemeReportRepository(
        private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
        private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /** Whether this device has already reported [themeId], so the screen can say so up front. */
    suspend fun load(themeId: String): CommunityThemeReportState {
        if (!isCanonicalThemeId(themeId)) {
            return CommunityThemeReportState.Failed(IllegalArgumentException("Invalid theme id"))
        }
        return try {
            val uid = requireAccountUid()
            CommunityThemeReportState.Loaded(reportDocument(themeId, uid).get().await().exists())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommunityThemeReportState.Failed(error)
        }
    }

    /**
     * Files this account's report.
     *
     * [details] is optional and trimmed; an empty one is omitted from the document rather than
     * written as an empty string, because the rules accept the key only when it carries text.
     */
    suspend fun report(
            themeId: String,
            reason: CommunityThemeReportReason,
            details: String?
    ): CommunityThemeReportResult {
        if (!isCanonicalThemeId(themeId)) return CommunityThemeReportResult.InvalidTheme
        val trimmed = details?.trim()?.take(COMMUNITY_THEME_REPORT_DETAILS_MAX_LENGTH)
        return try {
            val reference = reportDocument(themeId, requireAccountUid())
            var existed = false
            firestore.runTransaction { transaction ->
                if (transaction.get(reference).exists()) {
                    existed = true
                } else {
                    val document = hashMapOf<String, Any>(
                            "schemaVersion" to REPORT_SCHEMA_VERSION,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "reason" to reason.wireValue)
                    if (!trimmed.isNullOrEmpty()) document["details"] = trimmed
                    transaction.set(reference, document)
                }
            }.await()
            if (existed) CommunityThemeReportResult.AlreadyReported else CommunityThemeReportResult.Sent
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error is FirebaseFirestoreException &&
                    error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                CommunityThemeReportResult.NotReady
            } else {
                CommunityThemeReportResult.Failed(error)
            }
        }
    }

    private suspend fun requireAccountUid(): String =
            auth.currentUser?.uid
                    ?: auth.signInAnonymously().await().user?.uid
                    ?: throw IllegalStateException("Firebase returned no anonymous report account")

    private fun reportDocument(themeId: String, uid: String) = firestore
            .collection(REPORTS_COLLECTION)
            .document(themeId)
            .collection(REPORTERS_COLLECTION)
            .document(uid)

    /** Mirrors the UUIDv4-only Firestore document-id contract. */
    private fun isCanonicalThemeId(raw: String): Boolean = UUID_V4.matches(raw)

    private companion object {
        const val REPORTS_COLLECTION = "communityThemeReports"

        /**
         * Not `reporters`. `firestore.rules` authorises the moderator queue with a
         * recursive-wildcard rule, which applies to every same-named subcollection anywhere in the
         * database, so the name has to be one nothing else will plausibly claim.
         */
        const val REPORTERS_COLLECTION = "themeReporters"
        const val REPORT_SCHEMA_VERSION = 1
        val UUID_V4 = Regex(
                "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
