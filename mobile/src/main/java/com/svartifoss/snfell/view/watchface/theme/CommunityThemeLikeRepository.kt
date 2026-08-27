package com.svartifoss.snfell.view.watchface.theme

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/** The current device's private reaction to one published community theme. */
sealed interface CommunityThemeLikeState {
    data class Loaded(val liked: Boolean) : CommunityThemeLikeState
    data class Failed(val error: Throwable) : CommunityThemeLikeState
}

/** The current device's reactions among the already-public catalogue IDs. */
sealed interface CommunityThemeLikedThemesState {
    data class Loaded(val themeIds: Set<String>) : CommunityThemeLikedThemesState
    data class Failed(val error: Throwable) : CommunityThemeLikedThemesState
}

sealed interface CommunityThemeLikeMutation {
    data class Updated(val liked: Boolean) : CommunityThemeLikeMutation
    object InvalidTheme : CommunityThemeLikeMutation
    /** The public catalogue entry has not reached Firebase's private publication registry yet. */
    object NotReady : CommunityThemeLikeMutation
    data class Failed(val error: Throwable) : CommunityThemeLikeMutation
}

/**
 * Private, one-vote-per-device reactions for public community themes.
 *
 * Liking is deliberately **not** an identified action. Browsing, liking, unliking and the private
 * "Liked" filter all work with no Google account and no visible sign-in: a missing account is
 * created silently with Firebase anonymous authentication, which never shows UI. Submitting a
 * theme remains the one action that requires a real Google identity, and Firestore rules enforce
 * that separation — an anonymous caller can write a reaction and nothing else.
 *
 * The consequence is accepted rather than worked around: the anonymous account lives only in this
 * app's local Firebase state, so clearing app data yields a new UID and the same person can like
 * the same theme again. The vote ledger is a rough popularity signal, not an audited ballot, and
 * the alternative — demanding an account before a heart tap — costs far more than the duplicates.
 * [CommunityThemeGoogleAuthentication] upgrades an anonymous account in place when someone later
 * signs in to submit, so reactions made before that survive it.
 *
 * The client never reads a list of voters or writes a public counter. Each reaction document lives
 * at `communityThemeLikes/<theme id>/voters/<Firebase UID>`; the trusted publisher aggregates them
 * into the static Pages catalogue, so browsing still costs no Firestore read per card.
 */
class CommunityThemeLikeRepository(
        private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
        private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun load(themeId: String): CommunityThemeLikeState {
        if (!isCanonicalThemeId(themeId)) {
            return CommunityThemeLikeState.Failed(IllegalArgumentException("Invalid theme id"))
        }
        return try {
            val uid = requireAccountUid()
            CommunityThemeLikeState.Loaded(likeDocument(themeId, uid).get().await().exists())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommunityThemeLikeState.Failed(error)
        }
    }

    /**
     * Loads only this device's reaction documents for IDs that are already public in Pages.
     *
     * Firestore intentionally denies listing the voters subcollection: relaxing that would reveal
     * other people's reactions. The gallery has the bounded public catalogue locally, so it can
     * ask for the caller's exact document under each known theme instead. Reads run in bounded
     * concurrent groups, which keeps one explicit "Liked" filter tap from opening an unbounded
     * number of requests at once. Nothing is read for normal browsing, search, layout
     * filtering, or sorting.
     */
    suspend fun loadLikedThemeIds(themeIds: Collection<String>): CommunityThemeLikedThemesState {
        val canonicalIds = themeIds.asSequence()
                .filter(::isCanonicalThemeId)
                .distinct()
                .toList()
        if (canonicalIds.isEmpty()) return CommunityThemeLikedThemesState.Loaded(emptySet())

        return try {
            val uid = requireAccountUid()
            val liked = mutableSetOf<String>()
            canonicalIds.chunked(LIKE_LOOKUP_BATCH_SIZE).forEach { ids ->
                val matchingIds = coroutineScope {
                    ids.map { id ->
                        async { id.takeIf { likeDocument(id, uid).get().await().exists() } }
                    }.awaitAll()
                }
                matchingIds.filterNotNull().forEach { liked += it }
            }
            CommunityThemeLikedThemesState.Loaded(liked)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommunityThemeLikedThemesState.Failed(error)
        }
    }

    /** Adds or removes only this device's own immutable vote document. */
    suspend fun setLiked(themeId: String, liked: Boolean): CommunityThemeLikeMutation {
        if (!isCanonicalThemeId(themeId)) return CommunityThemeLikeMutation.InvalidTheme
        return try {
            val reference = likeDocument(themeId, requireAccountUid())
            if (liked) {
                // A transaction makes repeated taps and concurrent devices idempotent. The rules
                // still accept only a true create, never an update to an existing vote document.
                firestore.runTransaction { transaction ->
                    if (!transaction.get(reference).exists()) {
                        transaction.set(reference, hashMapOf(
                                "schemaVersion" to LIKE_SCHEMA_VERSION,
                                "createdAt" to FieldValue.serverTimestamp()))
                    }
                }.await()
            } else {
                // A delete against a concurrently removed document may have no existing resource
                // for the strict rule to validate. Read first and omit that write when absent so
                // an unlike is idempotent across two devices as well.
                firestore.runTransaction { transaction ->
                    if (transaction.get(reference).exists()) transaction.delete(reference)
                }.await()
            }
            CommunityThemeLikeMutation.Updated(liked)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error is FirebaseFirestoreException &&
                    error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                CommunityThemeLikeMutation.NotReady
            } else {
                CommunityThemeLikeMutation.Failed(error)
            }
        }
    }

    /**
     * Returns the UID that owns this device's reactions, creating an anonymous one when needed.
     *
     * This never presents UI, so it is safe on the path of a plain heart tap. It throws rather
     * than returning null so that every caller's existing failure branch reports it — the one
     * realistic cause is Anonymous authentication being disabled in the Firebase project, which
     * has to surface as the ordinary "could not save your like" message instead of a silent no-op.
     */
    private suspend fun requireAccountUid(): String =
            auth.currentUser?.uid
                    ?: auth.signInAnonymously().await().user?.uid
                    ?: throw IllegalStateException("Firebase returned no anonymous reaction account")

    private fun likeDocument(themeId: String, uid: String) = firestore
            .collection(LIKES_COLLECTION)
            .document(themeId)
            .collection(VOTERS_COLLECTION)
            .document(uid)

    /** Mirrors the UUIDv4-only Firestore document-id contract. */
    private fun isCanonicalThemeId(raw: String): Boolean = UUID_V4.matches(raw)

    private companion object {
        const val LIKES_COLLECTION = "communityThemeLikes"
        const val VOTERS_COLLECTION = "voters"
        const val LIKE_SCHEMA_VERSION = 1
        const val LIKE_LOOKUP_BATCH_SIZE = 20
        val UUID_V4 = Regex(
                "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
