package com.svartifoss.snfell.view.watchface.theme

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * The account currently available to the Community themes feature on this device.
 *
 * Firebase restores a signed-in user between process launches. This type deliberately models
 * that persisted state rather than any Google profile data: the app needs to know whether it can
 * submit. The public author identity is stored separately in Firestore, but is fixed once claimed
 * rather than being chosen again for every submission.
 */
internal enum class CommunityThemeAccountState {
    /** A Google identity is linked and may own theme submissions. */
    GOOGLE,

    /** A local anonymous Firebase account exists only for likes and the Liked filter. */
    ANONYMOUS_LIKES,

    /** No Firebase user is currently held on this device. */
    SIGNED_OUT
}

/** Kept pure so both the auth boundary and Settings screen make the same classification. */
internal object CommunityThemeAccountStateResolver {
    private const val GOOGLE_PROVIDER = "google.com"

    fun resolve(isAnonymous: Boolean?, providerIds: Collection<String>): CommunityThemeAccountState =
            when {
                isAnonymous == null -> CommunityThemeAccountState.SIGNED_OUT
                // An account linked from an anonymous reaction can keep Firebase's sign-in event
                // marked anonymous, but its provider list contains Google. That is the state
                // Firestore's identifiedUser() rule accepts as well.
                GOOGLE_PROVIDER in providerIds -> CommunityThemeAccountState.GOOGLE
                // Google is the only non-anonymous provider this app creates. Treat an existing
                // non-anonymous session as connected for compatibility with older Firebase state
                // and so an already authenticated user is never prompted again unnecessarily.
                !isAnonymous -> CommunityThemeAccountState.GOOGLE
                else -> CommunityThemeAccountState.ANONYMOUS_LIKES
            }

    fun resolve(user: FirebaseUser?): CommunityThemeAccountState = resolve(
            isAnonymous = user?.isAnonymous,
            providerIds = user?.providerData?.map { it.providerId }.orEmpty())
}

/**
 * Small device-local facade for Settings and submission UI.
 *
 * It deliberately does not offer FirebaseUser.delete(). Deleting the identity from the device that
 * holds it would leave that identity's intake records, submission quota, private reactions and any
 * already-public Git content behind -- an account deletion that deletes the least important half.
 * [CommunityThemeAccountDeletionRepository] records the request instead, and the trusted publisher
 * carries out both halves in the order that keeps them consistent.
 */
internal class CommunityThemeAccountRepository(
        private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
        private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun state(): CommunityThemeAccountState = CommunityThemeAccountStateResolver.resolve(auth.currentUser)

    fun isGoogleConnected(): Boolean = state() == CommunityThemeAccountState.GOOGLE

    /** Shown only on this device to help a person recognize their connected Google account. */
    fun email(): String? = auth.currentUser?.email?.trim()?.takeIf { it.isNotEmpty() }

    fun userUid(): String? = auth.currentUser?.uid

    /**
     * Reads the immutable public author identity owned by the current account.
     *
     * Absence is a normal first-submission state. A malformed document is reported as a failure
     * rather than treated as unclaimed, because doing the latter would invite the UI to offer a
     * second name for an account whose reservation needs administrative repair.
     */
    suspend fun publicAuthorIdentity(): CommunityThemeAuthorIdentityLoadResult {
        val user = auth.currentUser
                ?: return CommunityThemeAuthorIdentityLoadResult.NotAuthenticated
        if (!isGoogleConnected()) return CommunityThemeAuthorIdentityLoadResult.NotAuthenticated
        return try {
            val snapshot = firestore.collection(AUTHOR_ACCOUNT_COLLECTION)
                    .document(user.uid)
                    .get()
                    .await()
            if (!snapshot.exists()) {
                CommunityThemeAuthorIdentityLoadResult.Unclaimed
            } else {
                val identity = CommunityThemeAuthorNames.parse(user.uid, snapshot.data)
                        ?: return CommunityThemeAuthorIdentityLoadResult.Failed(
                                IllegalStateException("Malformed Community theme author identity"))
                CommunityThemeAuthorIdentityLoadResult.Claimed(identity)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommunityThemeAuthorIdentityLoadResult.Failed(error)
        }
    }

    /** An explicit Settings or submission action may open Credential Manager when required. */
    suspend fun signInWithGoogle(activity: Activity): CommunityThemeGoogleSignInResult =
            CommunityThemeGoogleAuthentication(auth).signIn(activity)

    /** Removes Firebase's local session; it does not delete the Firebase/Google account or content. */
    fun signOutFromThisDevice() {
        auth.signOut()
    }

    /**
     * Re-reads the account from Firebase, which is how a completed erasure becomes visible here.
     *
     * A locally cached FirebaseUser survives the deletion of the identity behind it, so a screen
     * that only read `currentUser` would keep showing a connected account indefinitely. Reloading
     * fails with USER_NOT_FOUND once the publisher has deleted it, and the local session is then
     * dropped. Any other failure is ordinary connectivity and deliberately changes nothing.
     */
    suspend fun refresh() {
        val user = auth.currentUser ?: return
        try {
            user.reload().await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: FirebaseAuthInvalidUserException) {
            Timber.i(error, "Community account no longer exists; dropping the local session")
            auth.signOut()
        } catch (error: Exception) {
            Timber.d(error, "Could not refresh the community account state")
        }
    }

    private companion object {
        const val AUTHOR_ACCOUNT_COLLECTION = "communityThemeAccounts"
    }
}
