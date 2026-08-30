package com.svartifoss.snfell.view.watchface.theme

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.svartifoss.snfell.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * What should happen to the themes an account has already published, once that account is gone.
 *
 * The choice exists because the two answers are both reasonable and neither can be guessed. A
 * theme was published under a pseudonym that is already public, so leaving it in the gallery costs
 * its author nothing further; withdrawing it is equally legitimate, and only its author can say
 * which they meant.
 *
 * Neither value preserves a submission that is not public yet: "keep my themes" can only mean the
 * ones people can actually see, and a queued or rejected submission is deleted under both.
 */
internal enum class CommunityThemeDeletionChoice(val wireValue: String) {
    /** Published themes stay in the gallery; their link back to this account is removed. */
    KEEP_THEMES("keep"),

    /** Published themes are withdrawn from the gallery on the next publication run. */
    DELETE_THEMES("delete");

    companion object {
        fun fromWire(raw: String?): CommunityThemeDeletionChoice? =
                entries.firstOrNull { it.wireValue == raw }
    }
}

/** What the Community account screen can offer, kept pure so its rules are testable. */
internal enum class CommunityThemeAccountAction { CONNECT, DISCONNECT, DELETE }

internal object CommunityThemeAccountActions {

    fun resolve(
            state: CommunityThemeAccountState,
            deletionRequested: Boolean
    ): Set<CommunityThemeAccountAction> = when {
        // Erasure is one-way by design, so the screen stops offering anything that would imply it
        // can be taken back. Signing out stays available: it is local and changes nothing pending.
        deletionRequested -> setOf(CommunityThemeAccountAction.DISCONNECT)
        state == CommunityThemeAccountState.SIGNED_OUT -> setOf(CommunityThemeAccountAction.CONNECT)
        state == CommunityThemeAccountState.ANONYMOUS_LIKES -> setOf(
                CommunityThemeAccountAction.CONNECT,
                CommunityThemeAccountAction.DISCONNECT,
                CommunityThemeAccountAction.DELETE)
        else -> setOf(CommunityThemeAccountAction.DISCONNECT, CommunityThemeAccountAction.DELETE)
    }

    /**
     * Only an identified account can own a submission, so only it is asked about its themes. An
     * anonymous account holds nothing but private likes, and offering a choice with one possible
     * outcome would suggest it had something published to lose.
     */
    fun offersThemeChoice(state: CommunityThemeAccountState): Boolean =
            state == CommunityThemeAccountState.GOOGLE

    /**
     * The disposition sent when no choice was presented. Keeping is the conservative half of a
     * decision that cannot apply, so an account that unexpectedly does own a public theme has it
     * left alone rather than silently withdrawn.
     */
    fun choiceWithoutPrompt(): CommunityThemeDeletionChoice = CommunityThemeDeletionChoice.KEEP_THEMES
}

internal sealed interface CommunityThemeAccountDeletionState {
    object None : CommunityThemeAccountDeletionState
    data class Requested(val choice: CommunityThemeDeletionChoice?) : CommunityThemeAccountDeletionState
    data class Failed(val error: Throwable) : CommunityThemeAccountDeletionState
}

internal sealed interface CommunityThemeAccountDeletionResult {
    object Requested : CommunityThemeAccountDeletionResult
    data class AlreadyRequested(val choice: CommunityThemeDeletionChoice?) : CommunityThemeAccountDeletionResult
    object NotAuthenticated : CommunityThemeAccountDeletionResult
    data class Failed(val error: Throwable) : CommunityThemeAccountDeletionResult
}

/**
 * Records a person's request to erase their community account, and their choice about the themes
 * they published.
 *
 * The app never carries the erasure out itself, for the same reason it never publishes a theme:
 * an APK cannot be trusted with the public catalogue, and deleting a Firebase identity from the
 * device that holds it would leave that identity's submissions, quota and votes behind. The
 * trusted publisher does the work on its next run -- withdrawing files in a Git commit first, then
 * removing every Firestore record and the identity itself.
 *
 * Firestore accepts this document only as a create, so the request is deliberately one-way. That
 * is what keeps the Git half and the Firestore half of an erasure from ever disagreeing, and the
 * screen says so before asking.
 */
internal class CommunityThemeAccountDeletionRepository(
        private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
        private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /** Returns [CommunityThemeAccountDeletionState.None] whenever no account is held at all. */
    suspend fun requestedDeletion(): CommunityThemeAccountDeletionState {
        val uid = auth.currentUser?.uid ?: return CommunityThemeAccountDeletionState.None
        return try {
            val snapshot = document(uid).get().await()
            if (!snapshot.exists()) {
                CommunityThemeAccountDeletionState.None
            } else {
                CommunityThemeAccountDeletionState.Requested(
                        CommunityThemeDeletionChoice.fromWire(snapshot.getString(FIELD_DISPOSITION)))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommunityThemeAccountDeletionState.Failed(error)
        }
    }

    suspend fun requestDeletion(choice: CommunityThemeDeletionChoice): CommunityThemeAccountDeletionResult {
        val uid = auth.currentUser?.uid ?: return CommunityThemeAccountDeletionResult.NotAuthenticated
        val reference = document(uid)
        return try {
            // Rules accept a create and nothing else, so an existing request must be reported
            // rather than written over -- an update would be denied and read as a plain failure.
            val existing = firestore.runTransaction { transaction ->
                val snapshot = transaction.get(reference)
                if (snapshot.exists()) {
                    return@runTransaction snapshot.getString(FIELD_DISPOSITION) ?: ""
                }
                transaction.set(reference, hashMapOf(
                        "ownerUid" to uid,
                        "requestSchemaVersion" to REQUEST_SCHEMA_VERSION,
                        "status" to "pending",
                        FIELD_DISPOSITION to choice.wireValue,
                        "clientVersion" to BuildConfig.VERSION_NAME,
                        "createdAt" to FieldValue.serverTimestamp()))
                null
            }.await()
            if (existing == null) {
                CommunityThemeAccountDeletionResult.Requested
            } else {
                CommunityThemeAccountDeletionResult.AlreadyRequested(
                        CommunityThemeDeletionChoice.fromWire(existing))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommunityThemeAccountDeletionResult.Failed(error)
        }
    }

    private fun document(uid: String) =
            firestore.collection(DELETION_COLLECTION).document(uid)

    private companion object {
        const val DELETION_COLLECTION = "communityThemeAccountDeletion"
        const val FIELD_DISPOSITION = "themeDisposition"
        const val REQUEST_SCHEMA_VERSION = 1
    }
}
