package com.svartifoss.snfell.view.watchface.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.MusicLoadingBarsView
import com.svartifoss.snfell.view.watchface.WatchPreviewView
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Explicit authoring boundary for community themes. It receives only a local profile id; the
 * repository reloads the saved profile and creates a fresh public UUID when the user submits.
 * Browsing the gallery never reaches this activity and never creates an Auth request.
 */
class SubmitCommunityThemeActivity : AppCompatActivity() {

    private lateinit var themeRepository: WatchThemeRepository
    private lateinit var submissionRepository: CommunityThemeSubmissionRepository
    private lateinit var preview: WatchPreviewView
    private lateinit var publicNameLayout: TextInputLayout
    private lateinit var publicNameInput: TextInputEditText
    private lateinit var authorLayout: TextInputLayout
    private lateinit var authorInput: TextInputEditText
    private lateinit var originality: TextView
    private lateinit var error: TextView
    private lateinit var progress: MusicLoadingBarsView
    private lateinit var submitButton: MaterialButton

    private lateinit var profileId: String
    private var submissionInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_submit_community_theme)

        val requestedProfileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        if (requestedProfileId.isNullOrBlank()) {
            Toast.makeText(this, R.string.community_theme_submit_invalid, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        profileId = requestedProfileId
        themeRepository = WatchThemeRepository(this)
        submissionRepository = CommunityThemeSubmissionRepository(applicationContext)

        preview = findViewById(R.id.submission_preview)
        publicNameLayout = findViewById(R.id.public_name_layout)
        publicNameInput = findViewById(R.id.public_name_input)
        authorLayout = findViewById(R.id.author_layout)
        authorInput = findViewById(R.id.author_input)
        originality = findViewById(R.id.submission_originality)
        error = findViewById(R.id.submission_error)
        progress = findViewById(R.id.submission_progress)
        progress.setBarsColor(LyraAccent.resolve(this))
        submitButton = findViewById(R.id.button_submit_theme)

        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }
        publicNameInput.doAfterTextChanged {
            publicNameLayout.error = null
            clearError()
        }
        authorInput.doAfterTextChanged {
            authorLayout.error = null
            clearError()
        }
        submitButton.setOnClickListener { submit() }

        loadInitialTheme()
    }

    private fun loadInitialTheme() {
        val local = themeRepository.load().firstOrNull { it.id == profileId }
        val initialName = local?.name ?: run {
            showInvalidAndFinish()
            return
        }
        val ready = themeRepository.prepareCommunityThemeSubmission(profileId, initialName)
                as? CommunityThemeSubmissionDraftResult.Ready ?: run {
            showInvalidAndFinish()
            return
        }
        val publicProfile = themeRepository.parsePublishedProfile(ready.draft.profileJson()) ?: run {
            showInvalidAndFinish()
            return
        }
        preview.setThemeProfile(publicProfile)
        findViewById<TextView>(R.id.submission_theme_name).text = local.name
        findViewById<TextView>(R.id.submission_theme_layout).text =
                WatchThemeRepository.displayNameForFace(this, local.baseFace)
        publicNameInput.setText(ready.draft.name)
        publicNameInput.setSelection(publicNameInput.text?.length ?: 0)
        showPreflight(submissionRepository.preflight(ready.draft))
    }

    private fun showPreflight(preflight: CommunityThemeSubmissionPreflight) {
        when (preflight) {
            is CommunityThemeSubmissionPreflight.Ready -> {
                originality.text = getString(
                        R.string.community_theme_submit_originality,
                        preflight.changedSettings,
                        COMMUNITY_THEME_MINIMUM_CHANGED_SETTINGS)
                submitButton.isEnabled = true
            }
            is CommunityThemeSubmissionPreflight.InsufficientOriginality -> {
                originality.text = getString(
                        R.string.community_theme_submit_originality,
                        preflight.changedSettings,
                        preflight.minimumRequired)
                submitButton.isEnabled = false
            }
            CommunityThemeSubmissionPreflight.InvalidDraft -> {
                originality.text = getString(R.string.community_theme_submit_invalid)
                submitButton.isEnabled = false
            }
        }
    }

    private fun submit() {
        if (submissionInProgress) return
        val publicName = publicNameInput.text?.toString().orEmpty()
        val author = authorInput.text?.toString().orEmpty()
        if (publicName.isBlank()) {
            publicNameLayout.error = getString(R.string.community_theme_submit_name_required)
            publicNameInput.requestFocus()
            return
        }
        if (!isValidPublicText(author)) {
            authorLayout.error = getString(R.string.community_theme_submit_author_required)
            authorInput.requestFocus()
            return
        }
        val draftResult = themeRepository.prepareCommunityThemeSubmission(profileId, publicName)
        val draft = (draftResult as? CommunityThemeSubmissionDraftResult.Ready)?.draft ?: run {
            if (draftResult is CommunityThemeSubmissionDraftResult.InvalidPublicName) {
                publicNameLayout.error = getString(R.string.community_theme_submit_name_required)
                publicNameInput.requestFocus()
            } else {
                showError(getString(R.string.community_theme_submit_invalid))
            }
            return
        }
        when (val preflight = submissionRepository.preflight(draft)) {
            is CommunityThemeSubmissionPreflight.InsufficientOriginality -> {
                showPreflight(preflight)
                showError(getString(
                        R.string.community_theme_submit_originality,
                        preflight.changedSettings,
                        preflight.minimumRequired))
                return
            }
            CommunityThemeSubmissionPreflight.InvalidDraft -> {
                showError(getString(R.string.community_theme_submit_invalid))
                return
            }
            is CommunityThemeSubmissionPreflight.Ready -> submitReady(preflight, author)
        }
    }

    private fun submitReady(
            preflight: CommunityThemeSubmissionPreflight.Ready,
            author: String
    ) {
        val publicProfile = themeRepository.parsePublishedProfile(preflight.draft.profileJson()) ?: run {
            showError(getString(R.string.community_theme_submit_invalid))
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            val moderationPreview = renderModerationPreview(publicProfile)
            if (moderationPreview == null) {
                setLoading(false)
                showError(getString(R.string.community_theme_submit_preview_error))
                return@launch
            }
            when (val signIn = submissionRepository.signInWithGoogle(this@SubmitCommunityThemeActivity)) {
                CommunityThemeGoogleSignInResult.Authenticated -> {
                    when (val queued = submissionRepository.enqueue(
                            preflight,
                            author,
                            moderationPreview)) {
                        CommunityThemeQueueResult.Queued -> showSuccess()
                        CommunityThemeQueueResult.SubmissionLimitReached -> {
                            setLoading(false)
                            showError(getString(R.string.community_theme_submit_limit_reached))
                        }
                        CommunityThemeQueueResult.NotAuthenticated,
                        CommunityThemeQueueResult.InvalidRequest -> {
                            setLoading(false)
                            showError(getString(R.string.community_theme_submit_error))
                        }
                        is CommunityThemeQueueResult.Failed -> {
                            Timber.w(queued.error, "Could not queue community theme")
                            setLoading(false)
                            showError(getString(R.string.community_theme_submit_error))
                        }
                    }
                }
                CommunityThemeGoogleSignInResult.Cancelled -> {
                    setLoading(false)
                    showError(getString(R.string.community_theme_submit_sign_in_cancelled))
                }
                is CommunityThemeGoogleSignInResult.Failed -> {
                    Timber.w(signIn.error, "Google sign-in for community theme failed")
                    setLoading(false)
                    showError(getString(R.string.community_theme_submit_error))
                }
            }
        }
    }

    /**
     * Renders an isolated, fixed-size review image. Its profile mode forces the bundled sample
     * track/artwork and moderation mode fixes the clock, so neither local playback nor device time
     * can enter Firestore.
     */
    private fun renderModerationPreview(profile: WatchThemeProfile): String? = try {
        val offscreenPreview = WatchPreviewView(this).apply {
            setThemeProfile(profile)
            setModerationPreviewMode(true)
        }
        val size = MODERATION_PREVIEW_PIXELS
        val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        offscreenPreview.measure(spec, spec)
        offscreenPreview.layout(0, 0, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            offscreenPreview.draw(Canvas(bitmap))
            val bytes = ByteArrayOutputStream().use { output ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                if (!bitmap.compress(format, MODERATION_PREVIEW_QUALITY, output)) return null
                output.toByteArray()
            }
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    } catch (error: Exception) {
        Timber.w(error, "Could not render community-theme moderation preview")
        null
    }

    private fun setLoading(loading: Boolean) {
        submissionInProgress = loading
        submitButton.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) clearError()
    }

    private fun showError(message: String) {
        error.text = message
        error.visibility = View.VISIBLE
    }

    private fun clearError() {
        if (!submissionInProgress) error.visibility = View.GONE
    }

    private fun showSuccess() {
        setLoading(false)
        AlertDialog.Builder(this)
                .setTitle(R.string.community_theme_submit_success_title)
                .setMessage(R.string.community_theme_submit_success_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    setResult(RESULT_OK)
                    finish()
                }
                .setCancelable(false)
                .show()
    }

    private fun showInvalidAndFinish() {
        Toast.makeText(this, R.string.community_theme_submit_invalid, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun isValidPublicText(value: String): Boolean {
        val normalized = value.trim().replace(WHITESPACE, " ")
        return normalized.isNotBlank() &&
                normalized.length <= MAX_PUBLIC_TEXT_LENGTH &&
                normalized.none(Character::isISOControl)
    }

    companion object {
        const val EXTRA_PROFILE_ID = "community_theme_profile_id"

        private const val MODERATION_PREVIEW_PIXELS = 200
        private const val MODERATION_PREVIEW_QUALITY = 84
        private const val MAX_PUBLIC_TEXT_LENGTH = 48
        private val WHITESPACE = Regex("\\s+")
    }
}
