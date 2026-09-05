package com.svartifoss.snfell.view.watchface.theme

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Where one submission stands.
 *
 * These are the statuses `firestore.rules` accepts, and the mapping is deliberately total: an
 * unrecognized value from a newer build becomes [UNKNOWN] rather than being dropped, because a
 * submission the app cannot label is still a submission the author sent and should see.
 */
internal enum class CommunityThemeSubmissionStatus(val wireValue: String) {
    PENDING("pending"),
    APPROVED("approved"),
    PUBLISHED("published"),
    REJECTED("rejected"),
    WITHDRAWN("withdrawn"),
    UNKNOWN("");

    companion object {
        fun fromWire(raw: String?): CommunityThemeSubmissionStatus =
                entries.firstOrNull { it.wireValue.isNotEmpty() && it.wireValue == raw } ?: UNKNOWN
    }
}

internal data class CommunityThemeSubmissionRecord(
        val id: String,
        val name: String,
        val author: String,
        val baseFace: String,
        val status: CommunityThemeSubmissionStatus,
        val createdAtMillis: Long?,
        /**
         * Likes this theme has collected, read from the public catalogue rather than from
         * Firestore. Null whenever there is no published entry to count -- a submission still in
         * review has no public existence, so zero would be a claim rather than an absence.
         */
        val likes: Int? = null,
        /** [likes] for the download figure, with the same null-means-not-public meaning. */
        val installs: Int? = null
)

internal sealed interface CommunityThemeWithdrawalResult {
    /** Recorded. The theme leaves the gallery on the publisher's next run. */
    object Requested : CommunityThemeWithdrawalResult
    object SignedOut : CommunityThemeWithdrawalResult
    data class Failed(val error: Throwable) : CommunityThemeWithdrawalResult
}

internal sealed interface CommunityThemeSubmissionsState {
    data class Loaded(val records: List<CommunityThemeSubmissionRecord>) : CommunityThemeSubmissionsState
    /** No account has ever been connected on this device, so there is nothing to look up. */
    object SignedOut : CommunityThemeSubmissionsState
    data class Failed(val error: Throwable) : CommunityThemeSubmissionsState
}

/** Newest first, and an entry with no readable timestamp sorts last rather than being dropped. */
internal object CommunityThemeSubmissionOrder {
    fun sorted(records: List<CommunityThemeSubmissionRecord>): List<CommunityThemeSubmissionRecord> =
            records.sortedWith(
                    compareByDescending<CommunityThemeSubmissionRecord> { it.createdAtMillis ?: Long.MIN_VALUE }
                            .thenBy { it.id })

    /**
     * Attaches the public like counts to the submissions they belong to.
     *
     * A count is only ever taken from the catalogue, and only for a record the catalogue actually
     * lists. A submission still in review has no public entry, and showing it a zero would state
     * that nobody liked it rather than that there was nothing to like yet.
     */
    /**
     * Attaches the public figures to each record.
     *
     * Both maps come from the one already-downloaded catalogue, so a record the catalogue does not
     * list gets null rather than zero in both — a submission still in review has no public
     * existence, and reporting it as zero downloads would be a claim rather than an absence.
     */
    fun withPublicCounts(
            records: List<CommunityThemeSubmissionRecord>,
            likesById: Map<String, Int>,
            installsById: Map<String, Int>
    ): List<CommunityThemeSubmissionRecord> = records.map { record ->
        record.copy(likes = likesById[record.id], installs = installsById[record.id])
    }
}

/**
 * Reads back the submissions this account has sent.
 *
 * This is only possible because the reviewer's identity was moved out of the intake document and
 * into the moderator-only `themeIntakeReview` collection: Firestore grants read access per
 * document and never per field, so while a verdict and its author shared one record, an author
 * could be shown nothing beyond their own still-pending submissions. Do not put a reviewer field
 * back on the intake document -- it would silently make every one of these reads leak it.
 */
internal class CommunityThemeSubmissionsRepository(
        private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
        private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun mySubmissions(): CommunityThemeSubmissionsState {
        val uid = auth.currentUser?.uid ?: return CommunityThemeSubmissionsState.SignedOut
        return try {
            val snapshot = firestore.collection(INTAKE_COLLECTION)
                    .whereEqualTo(FIELD_OWNER_UID, uid)
                    .get()
                    .await()
            val records = snapshot.documents.mapNotNull { document ->
                val name = document.getString(FIELD_NAME)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                CommunityThemeSubmissionRecord(
                        id = document.id,
                        name = name,
                        author = document.getString(FIELD_AUTHOR)?.trim().orEmpty(),
                        baseFace = document.getString(FIELD_BASE_FACE)?.trim().orEmpty(),
                        status = CommunityThemeSubmissionStatus.fromWire(document.getString(FIELD_STATUS)),
                        createdAtMillis = document.getTimestamp(FIELD_CREATED_AT)?.toDate()?.time)
            }
            CommunityThemeSubmissionsState.Loaded(CommunityThemeSubmissionOrder.sorted(records))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommunityThemeSubmissionsState.Failed(error)
        }
    }

    /**
     * Takes one of this account's own themes out of the gallery.
     *
     * The write is only a status: the catalogue lives in Git, so the trusted publisher is what
     * removes the file, the catalogue row and the likes on its next run, then deletes the record.
     * It is the same path a moderator withdrawal takes, and the rules accept it from the author
     * without a review record because no review happened.
     */
    suspend fun withdraw(themeId: String): CommunityThemeWithdrawalResult {
        auth.currentUser ?: return CommunityThemeWithdrawalResult.SignedOut
        return try {
            firestore.collection(INTAKE_COLLECTION)
                    .document(themeId)
                    .update(FIELD_STATUS, STATUS_WITHDRAWN)
                    .await()
            CommunityThemeWithdrawalResult.Requested
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommunityThemeWithdrawalResult.Failed(error)
        }
    }

    private companion object {
        const val STATUS_WITHDRAWN = "withdrawn"
        const val INTAKE_COLLECTION = "themeIntake"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_NAME = "name"
        const val FIELD_AUTHOR = "author"
        const val FIELD_BASE_FACE = "baseFace"
        const val FIELD_STATUS = "status"
        const val FIELD_CREATED_AT = "createdAt"
    }
}
