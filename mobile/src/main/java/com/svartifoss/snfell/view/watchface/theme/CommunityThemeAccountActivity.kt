package com.svartifoss.snfell.view.watchface.theme

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.MusicLoadingBarsView
import com.svartifoss.snfell.view.applyLyraDialogStyling
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Device-local Community themes account controls.
 *
 * Firebase itself restores a Google connection between launches. This screen gives that state a
 * visible home, lets a person disconnect the local session, and is where an account is deleted
 * for good.
 *
 * Deletion is *requested* here and carried out by the trusted publisher, because the two halves of
 * it live in different places: an APK can delete its own Firebase identity but cannot touch the
 * public catalogue in Git, and doing only the first would leave the themes behind while destroying
 * the account that could ask for them back. The one thing the app must collect is the decision
 * only the author can make -- whether those published themes stay.
 */
class CommunityThemeAccountActivity : AppCompatActivity() {

    private lateinit var accountRepository: CommunityThemeAccountRepository
    private lateinit var deletionRepository: CommunityThemeAccountDeletionRepository
    private lateinit var statusIcon: ImageView
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var publicAuthor: TextView
    private lateinit var error: TextView
    private lateinit var progress: MusicLoadingBarsView
    private lateinit var signInButton: MaterialButton
    private lateinit var signOutButton: MaterialButton
    private lateinit var deleteButton: MaterialButton
    private lateinit var submissionsButton: MaterialButton
    private lateinit var deletionPending: TextView
    private var requestInProgress = false
    private var deletionState: CommunityThemeAccountDeletionState = CommunityThemeAccountDeletionState.None
    private var authorIdentity: CommunityThemeAuthorIdentity? = null
    private var authorIdentityLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community_theme_account)

        accountRepository = CommunityThemeAccountRepository()
        deletionRepository = CommunityThemeAccountDeletionRepository()
        statusIcon = findViewById(R.id.community_account_status_icon)
        status = findViewById(R.id.community_account_status)
        detail = findViewById(R.id.community_account_detail)
        publicAuthor = findViewById(R.id.community_account_public_author)
        error = findViewById(R.id.community_account_error)
        progress = findViewById(R.id.community_account_progress)
        signInButton = findViewById(R.id.button_community_account_sign_in)
        signOutButton = findViewById(R.id.button_community_account_sign_out)
        deleteButton = findViewById(R.id.button_community_account_delete)
        submissionsButton = findViewById(R.id.button_community_account_submissions)
        deletionPending = findViewById(R.id.community_account_deletion_pending)

        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }
        signInButton.setOnClickListener { connectGoogle() }
        signOutButton.setOnClickListener { confirmSignOut() }
        deleteButton.setOnClickListener { chooseDeletion() }
        submissionsButton.setOnClickListener {
            startActivity(Intent(this, CommunityThemeSubmissionsActivity::class.java))
        }

        applyRuntimeAccent()
        renderAccount()
    }

    override fun onResume() {
        super.onResume()
        applyRuntimeAccent()
        if (!requestInProgress) refreshAccount()
    }

    /**
     * Reloads Firebase before drawing, so a completed erasure shows as a signed-out account rather
     * than as the cached user it deleted, and re-reads whether a request is still outstanding.
     */
    private fun refreshAccount() {
        renderAccount()
        lifecycleScope.launch {
            accountRepository.refresh()
            val identityResult = if (accountRepository.isGoogleConnected()) {
                accountRepository.publicAuthorIdentity()
            } else {
                CommunityThemeAuthorIdentityLoadResult.NotAuthenticated
            }
            when (identityResult) {
                is CommunityThemeAuthorIdentityLoadResult.Claimed -> {
                    authorIdentity = identityResult.identity
                    authorIdentityLoaded = true
                }
                CommunityThemeAuthorIdentityLoadResult.Unclaimed -> {
                    authorIdentity = null
                    authorIdentityLoaded = true
                }
                CommunityThemeAuthorIdentityLoadResult.NotAuthenticated -> {
                    authorIdentity = null
                    authorIdentityLoaded = false
                }
                is CommunityThemeAuthorIdentityLoadResult.Failed -> {
                    authorIdentity = null
                    authorIdentityLoaded = false
                    Timber.d(identityResult.error, "Could not load fixed community author identity")
                }
            }
            deletionState = deletionRepository.requestedDeletion()
            if (!requestInProgress) renderAccount()
        }
    }

    private fun renderAccount() {
        val state = accountRepository.state()
        when (state) {
            CommunityThemeAccountState.GOOGLE -> {
                status.setText(R.string.community_theme_account_status_google)
                detail.text = accountRepository.email()?.let { email ->
                    getString(R.string.community_theme_account_google_email, email)
                } ?: getString(R.string.community_theme_account_google_detail)
                publicAuthor.visibility = visibilityFor(authorIdentityLoaded)
                if (authorIdentityLoaded) {
                    publicAuthor.text = authorIdentity?.let { identity ->
                        getString(
                                R.string.community_theme_account_public_author_claimed,
                                identity.authorName)
                    } ?: getString(R.string.community_theme_account_public_author_unclaimed)
                }
            }
            CommunityThemeAccountState.ANONYMOUS_LIKES -> {
                status.setText(R.string.community_theme_account_status_anonymous)
                detail.setText(R.string.community_theme_account_anonymous_detail)
                publicAuthor.visibility = View.GONE
            }
            CommunityThemeAccountState.SIGNED_OUT -> {
                status.setText(R.string.community_theme_account_status_signed_out)
                detail.setText(R.string.community_theme_account_signed_out_detail)
                publicAuthor.visibility = View.GONE
            }
        }

        val requested = deletionState as? CommunityThemeAccountDeletionState.Requested
        val actions = CommunityThemeAccountActions.resolve(state, deletionRequested = requested != null)
        signInButton.visibility = visibilityFor(CommunityThemeAccountAction.CONNECT in actions)
        signOutButton.visibility = visibilityFor(CommunityThemeAccountAction.DISCONNECT in actions)
        deleteButton.visibility = visibilityFor(CommunityThemeAccountAction.DELETE in actions)
        // Only an identified account can own a submission, so only it has a queue to look at.
        submissionsButton.visibility = visibilityFor(
                CommunityThemeAccountActions.offersThemeChoice(state))
        deletionPending.visibility = visibilityFor(requested != null)
        if (requested != null) {
            deletionPending.setText(when (requested.choice) {
                CommunityThemeDeletionChoice.KEEP_THEMES ->
                    R.string.community_theme_account_delete_pending_keep
                CommunityThemeDeletionChoice.DELETE_THEMES ->
                    R.string.community_theme_account_delete_pending_remove
                // A request whose stored choice cannot be read is still a request; saying so
                // beats implying the account is fine because one field did not parse.
                null -> R.string.community_theme_account_delete_pending_unknown
            })
        }
        updateEnabledState()
    }

    private fun visibilityFor(visible: Boolean): Int = if (visible) View.VISIBLE else View.GONE

    private fun connectGoogle() {
        if (requestInProgress) return
        setLoading(true)
        lifecycleScope.launch {
            when (val result = accountRepository.signInWithGoogle(this@CommunityThemeAccountActivity)) {
                CommunityThemeGoogleSignInResult.Authenticated -> {
                    setLoading(false)
                    refreshAccount()
                    Toast.makeText(
                            this@CommunityThemeAccountActivity,
                            R.string.community_theme_account_connected,
                            Toast.LENGTH_SHORT).show()
                }
                CommunityThemeGoogleSignInResult.Cancelled -> {
                    setLoading(false)
                    showError(R.string.community_theme_account_sign_in_cancelled)
                }
                is CommunityThemeGoogleSignInResult.Failed -> {
                    Timber.w(result.error, "Google sign-in from Community themes account settings failed")
                    setLoading(false)
                    showError(R.string.community_theme_account_sign_in_error)
                }
            }
        }
    }

    private fun confirmSignOut() {
        if (requestInProgress || accountRepository.state() == CommunityThemeAccountState.SIGNED_OUT) {
            return
        }
        val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.community_theme_account_sign_out_title)
                .setMessage(R.string.community_theme_account_sign_out_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.community_theme_account_sign_out_confirm) { _, _ ->
                    accountRepository.signOutFromThisDevice()
                    authorIdentity = null
                    authorIdentityLoaded = false
                    error.visibility = View.GONE
                    renderAccount()
                    Toast.makeText(
                            this,
                            R.string.community_theme_account_disconnected,
                            Toast.LENGTH_SHORT).show()
                }
                .create()
        showLyraDialog(dialog)
    }

    /** Collects the one decision only the author can make, then asks for it a second time. */
    private fun chooseDeletion() {
        if (requestInProgress) return
        val state = accountRepository.state()
        if (state == CommunityThemeAccountState.SIGNED_OUT) {
            showError(R.string.community_theme_account_delete_signed_out)
            return
        }
        if (!CommunityThemeAccountActions.offersThemeChoice(state)) {
            confirmDeletion(CommunityThemeAccountActions.choiceWithoutPrompt())
            return
        }

        val view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_community_account_delete, null, false)
        val choices = view.findViewById<RadioGroup>(R.id.community_account_delete_choice)
        val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.community_theme_account_delete_title)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.community_theme_account_delete_confirm) { _, _ ->
                    confirmDeletion(
                            if (choices.checkedRadioButtonId == R.id.community_account_delete_remove) {
                                CommunityThemeDeletionChoice.DELETE_THEMES
                            } else {
                                CommunityThemeDeletionChoice.KEEP_THEMES
                            })
                }
                .create()
        showLyraDialog(dialog)
    }

    private fun confirmDeletion(choice: CommunityThemeDeletionChoice) {
        val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.community_theme_account_delete_confirm_title)
                .setMessage(when (choice) {
                    CommunityThemeDeletionChoice.KEEP_THEMES ->
                        R.string.community_theme_account_delete_confirm_keep
                    CommunityThemeDeletionChoice.DELETE_THEMES ->
                        R.string.community_theme_account_delete_confirm_remove
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.community_theme_account_delete_confirm) { _, _ ->
                    requestDeletion(choice)
                }
                .create()
        showLyraDialog(dialog)
    }

    private fun requestDeletion(choice: CommunityThemeDeletionChoice) {
        if (requestInProgress) return
        setLoading(true)
        lifecycleScope.launch {
            when (val result = deletionRepository.requestDeletion(choice)) {
                CommunityThemeAccountDeletionResult.Requested -> {
                    deletionState = CommunityThemeAccountDeletionState.Requested(choice)
                    setLoading(false)
                    renderAccount()
                    Toast.makeText(
                            this@CommunityThemeAccountActivity,
                            R.string.community_theme_account_delete_requested,
                            Toast.LENGTH_LONG).show()
                }
                is CommunityThemeAccountDeletionResult.AlreadyRequested -> {
                    // A request is create-only, so a second one is not an error to report: the
                    // account is already on its way out, with the choice made the first time.
                    deletionState = CommunityThemeAccountDeletionState.Requested(result.choice)
                    setLoading(false)
                    renderAccount()
                }
                CommunityThemeAccountDeletionResult.NotAuthenticated -> {
                    setLoading(false)
                    showError(R.string.community_theme_account_delete_signed_out)
                }
                is CommunityThemeAccountDeletionResult.Failed -> {
                    Timber.w(result.error, "Could not request a community account deletion")
                    setLoading(false)
                    showError(R.string.community_theme_account_delete_error)
                }
            }
        }
    }

    private fun showLyraDialog(dialog: AlertDialog) {
        dialog.setOnShowListener {
            val accent = LyraAccent.contrastSafe(
                    LyraAccent.resolve(this),
                    getColor(R.color.lyra_surface),
                    minimumContrast = 4.5)
            dialog.applyLyraDialogStyling(accent = accent)
        }
        dialog.show()
    }

    private fun setLoading(loading: Boolean) {
        requestInProgress = loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) error.visibility = View.GONE
        updateEnabledState()
    }

    private fun updateEnabledState() {
        listOf(signInButton, signOutButton, deleteButton, submissionsButton).forEach { button ->
            button.isEnabled = !requestInProgress
            button.alpha = if (button.isEnabled) 1f else DISABLED_CONTROL_ALPHA
        }
    }

    private fun showError(stringRes: Int) {
        error.setText(stringRes)
        error.visibility = View.VISIBLE
    }

    private fun applyRuntimeAccent() {
        if (!::signInButton.isInitialized) return
        val accent = LyraAccent.resolve(this)
        val accentOnBackground = LyraAccent.contrastSafe(
                accent,
                getColor(R.color.lyra_background),
                minimumContrast = 3.0)
        val accentOnSurface = LyraAccent.contrastSafe(
                accent,
                getColor(R.color.lyra_surface),
                minimumContrast = 4.5)

        signInButton.backgroundTintList = ColorStateList.valueOf(accent)
        signInButton.setTextColor(LyraAccent.foregroundFor(accent))
        signOutButton.setTextColor(accentOnSurface)
        statusIcon.imageTintList = ColorStateList.valueOf(accentOnBackground)
        progress.setBarsColor(accentOnBackground)
    }

    private companion object {
        const val DISABLED_CONTROL_ALPHA = 0.54f
    }
}
