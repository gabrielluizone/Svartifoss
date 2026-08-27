package com.svartifoss.snfell.view.watchface.theme

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.svartifoss.snfell.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * The one explicit Google sign-in boundary, used by community theme *submission* only.
 *
 * Merely constructing this class never starts an interactive flow. Callers invoke [signIn] only
 * after a person taps an action that needs a real identity, preserving anonymous browsing — and
 * anonymous liking, which [CommunityThemeLikeRepository] handles with no account at all.
 */
internal class CommunityThemeGoogleAuthentication(
        private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    suspend fun signIn(activity: Activity): CommunityThemeGoogleSignInResult {
        val option = try {
            GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .setServerClientId(activity.getString(R.string.default_web_client_id))
                    .build()
        } catch (error: Exception) {
            return CommunityThemeGoogleSignInResult.Failed(error)
        }
        val response = try {
            CredentialManager.create(activity).getCredential(
                    activity,
                    GetCredentialRequest.Builder().addCredentialOption(option).build())
        } catch (_: GetCredentialCancellationException) {
            return CommunityThemeGoogleSignInResult.Cancelled
        } catch (error: CancellationException) {
            throw error
        } catch (error: GetCredentialException) {
            return CommunityThemeGoogleSignInResult.Failed(error)
        } catch (error: Exception) {
            return CommunityThemeGoogleSignInResult.Failed(error)
        }
        val credential = response.credential
        if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return CommunityThemeGoogleSignInResult.Failed(
                    IllegalStateException("Credential Manager returned an unsupported credential"))
        }
        val token = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (error: Exception) {
            return CommunityThemeGoogleSignInResult.Failed(error)
        }
        val authCredential = GoogleAuthProvider.getCredential(token, null)
        val anonymousAccount = auth.currentUser?.takeIf { it.isAnonymous }
        return try {
            if (anonymousAccount != null) {
                // Reactions are stored under whichever UID is current, and liking creates an
                // anonymous one. Signing in normally would swap that UID out and strand every
                // like this person had already left, so upgrade the existing account in place.
                // A collision means the chosen Google account already exists in its own right;
                // adopting it is then the only option, and losing the anonymous reactions is
                // preferable to refusing a submission the person explicitly asked for.
                try {
                    anonymousAccount.linkWithCredential(authCredential).await()
                } catch (_: FirebaseAuthUserCollisionException) {
                    auth.signInWithCredential(authCredential).await()
                }
            } else {
                auth.signInWithCredential(authCredential).await()
            }
            CommunityThemeGoogleSignInResult.Authenticated
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommunityThemeGoogleSignInResult.Failed(error)
        }
    }
}
