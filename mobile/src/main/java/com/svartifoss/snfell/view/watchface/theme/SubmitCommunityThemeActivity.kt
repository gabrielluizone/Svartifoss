package com.svartifoss.snfell.view.watchface.theme

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommunityThemeScreenshots
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.MusicLoadingBarsView
import com.svartifoss.snfell.view.applyLyraDialogStyling
import com.svartifoss.snfell.view.watchface.WatchPreviewView
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Explicit authoring boundary for community themes. It receives only a local profile id; the
 * repository reloads the saved profile and creates a fresh public UUID when the user submits.
 * Browsing the gallery never reaches this activity and never creates an Auth request.
 */
class SubmitCommunityThemeActivity : AppCompatActivity() {

    private lateinit var defaultPrefs: SharedPreferences
    private lateinit var themeRepository: WatchThemeRepository
    private lateinit var submissionRepository: CommunityThemeSubmissionRepository
    private lateinit var accountRepository: CommunityThemeAccountRepository
    private lateinit var preview: WatchPreviewView
    private lateinit var publicNameLayout: TextInputLayout
    private lateinit var publicNameInput: TextInputEditText
    private lateinit var authorLayout: TextInputLayout
    private lateinit var authorInput: TextInputEditText
    private lateinit var anonymousAuthorSwitch: SwitchMaterial
    private lateinit var originality: TextView
    private lateinit var error: TextView
    private lateinit var progress: MusicLoadingBarsView
    private lateinit var submitButton: MaterialButton
    private lateinit var screenshotImage: ShapeableImageView
    private lateinit var screenshotEmpty: TextView
    private lateinit var screenshotStatus: TextView
    private lateinit var chooseScreenshotButton: MaterialButton
    private lateinit var removeScreenshotButton: MaterialButton

    /** The normalized, already-encoded picture, or null when the author attached none. */
    private var attachedScreenshot: String? = null

    /**
     * Kept so a recreated Activity can rebuild the attachment.
     *
     * The encoded picture itself is deliberately not saved: at up to 128 KB it is the wrong thing
     * to put through a saved-state binder transaction. Re-normalizing from the URI is cheap, works
     * for the case this actually happens in (a rotation, same process, read grant still held), and
     * after process death the grant is gone and the slot honestly returns to empty.
     */
    private var pickedScreenshotUri: Uri? = null

    private val screenshotPicker = registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) applyPickedScreenshot(uri, reportErrors = true)
    }

    private lateinit var profileId: String
    private var submissionInProgress = false
    private var authorIdentity: CommunityThemeAuthorIdentity? = null
    private var authorIdentityRequestUid: String? = null
    /** MainActivity can keep extracting a new album accent underneath this standalone Activity. */
    private val accentPreferenceListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (LyraAccent.affectsResolvedColor(key) && ::submitButton.isInitialized) {
                    runOnUiThread(::applyRuntimeAccent)
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_submit_community_theme)
        defaultPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        val requestedProfileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        if (requestedProfileId.isNullOrBlank()) {
            Toast.makeText(this, R.string.community_theme_submit_invalid, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        profileId = requestedProfileId
        themeRepository = WatchThemeRepository(this)
        submissionRepository = CommunityThemeSubmissionRepository(applicationContext)
        accountRepository = CommunityThemeAccountRepository()

        preview = findViewById(R.id.submission_preview)
        publicNameLayout = findViewById(R.id.public_name_layout)
        publicNameInput = findViewById(R.id.public_name_input)
        authorLayout = findViewById(R.id.author_layout)
        authorInput = findViewById(R.id.author_input)
        anonymousAuthorSwitch = findViewById(R.id.anonymous_author_switch)
        originality = findViewById(R.id.submission_originality)
        error = findViewById(R.id.submission_error)
        progress = findViewById(R.id.submission_progress)
        submitButton = findViewById(R.id.button_submit_theme)
        screenshotImage = findViewById(R.id.submission_screenshot)
        screenshotEmpty = findViewById(R.id.submission_screenshot_empty)
        screenshotStatus = findViewById(R.id.submission_screenshot_status)
        chooseScreenshotButton = findViewById(R.id.button_choose_screenshot)
        removeScreenshotButton = findViewById(R.id.button_remove_screenshot)
        applyRuntimeAccent()
        updateSubmitButtonLabel()

        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }
        publicNameInput.doAfterTextChanged {
            publicNameLayout.error = null
            clearError()
        }
        authorInput.doAfterTextChanged {
            authorLayout.error = null
            clearError()
        }
        anonymousAuthorSwitch.setOnCheckedChangeListener { _, publishAnonymously ->
            updateAuthorPresentation(publishAnonymously)
            clearError()
        }
        updateAuthorPresentation(anonymousAuthorSwitch.isChecked)
        submitButton.setOnClickListener { submit() }
        chooseScreenshotButton.setOnClickListener {
            screenshotPicker.launch(PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        removeScreenshotButton.setOnClickListener { clearScreenshot() }
        savedInstanceState?.getString(STATE_SCREENSHOT_URI)?.let { saved ->
            // Silent on failure: the author did nothing wrong, and an error about a picture they
            // picked before a rotation explains nothing they can act on.
            applyPickedScreenshot(Uri.parse(saved), reportErrors = false)
        }

        loadInitialTheme()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pickedScreenshotUri?.let { outState.putString(STATE_SCREENSHOT_URI, it.toString()) }
    }

    override fun onStart() {
        super.onStart()
        defaultPrefs.registerOnSharedPreferenceChangeListener(accentPreferenceListener)
    }

    override fun onResume() {
        super.onResume()
        applyRuntimeAccent()
        updateSubmitButtonLabel()
        loadFixedAuthorIdentity()
    }

    override fun onStop() {
        defaultPrefs.unregisterOnSharedPreferenceChangeListener(accentPreferenceListener)
        super.onStop()
    }

    /** Applies the live Lyra accent without sacrificing contrast for very light album colours. */
    private fun applyRuntimeAccent() {
        if (!::submitButton.isInitialized) return
        val accent = LyraAccent.resolve(this)
        val background = getColor(R.color.lyra_background)
        val accentOnBackground = LyraAccent.contrastSafe(
                accent,
                background,
                minimumContrast = 3.0)
        val accentTextOnBackground = LyraAccent.contrastSafe(
                accent,
                background,
                minimumContrast = 4.5)
        val onAccent = LyraAccent.foregroundFor(accent)

        submitButton.backgroundTintList = ColorStateList.valueOf(accent)
        submitButton.setTextColor(onAccent)
        submitButton.iconTint = ColorStateList.valueOf(onAccent)
        renderSubmitButtonEnabledState()
        progress.setBarsColor(accentOnBackground)

        listOf(
                publicNameLayout to publicNameInput,
                authorLayout to authorInput
        ).forEach { (layout, input) ->
            // Cursor/handles and Material's focused outline otherwise retain the static sage that
            // colorControlActivated resolved at inflation time.
            LyraAccent.applyToEditText(input, accentOnBackground)
            layout.boxStrokeColor = accentOnBackground
            layout.defaultHintTextColor = ColorStateList.valueOf(accentTextOnBackground)
            layout.setCounterTextColor(ColorStateList.valueOf(accentTextOnBackground))
        }
        val switchStates = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf())
        anonymousAuthorSwitch.thumbTintList = ColorStateList(
                switchStates,
                intArrayOf(
                        getColor(R.color.lyra_divider),
                        accentOnBackground,
                        getColor(R.color.lyra_stone)))
        anonymousAuthorSwitch.trackTintList = ColorStateList(
                switchStates,
                intArrayOf(
                        ColorUtils.setAlphaComponent(getColor(R.color.lyra_divider), 0x60),
                        ColorUtils.setAlphaComponent(accentOnBackground, 0x80),
                        getColor(R.color.lyra_divider)))
        anonymousAuthorSwitch.jumpDrawablesToCurrentState()

        // The two screenshot actions are stock TextButtons, so their labels came from the theme's
        // colorPrimary - the static Lyra sage, resolved once at inflation and unable to follow a
        // custom or album-derived accent. They arrived with the author screenshot, after this
        // function was written, and were the last controls on the screen still reading green. It
        // is the same omission the detail card's report row made, for the same reason: a filled
        // button announces its colour and gets tinted, while a text button looks like a label.
        //
        // Measured against the card rather than the page: these two sit on lyra_surface, and a
        // very light album accent needs more lifting there than the controls on the background do.
        val accentTextOnSurface = LyraAccent.contrastSafe(
                accent,
                getColor(R.color.lyra_surface),
                minimumContrast = 4.5)
        // Choosing a picture disables its own button while the image is normalized, so the colour
        // carries a disabled entry instead of being one flat value the state never reaches.
        val screenshotActionColor = ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(
                        ColorUtils.setAlphaComponent(
                                accentTextOnSurface,
                                (0xFF * DISABLED_CONTROL_ALPHA).toInt()),
                        accentTextOnSurface))
        // Set alongside the label for the reason the detail card records: the default ripple is
        // drawn from that same static primary.
        val ripple = ColorStateList.valueOf(getColor(R.color.lyra_ripple))
        listOf(chooseScreenshotButton, removeScreenshotButton).forEach { button ->
            button.setTextColor(screenshotActionColor)
            button.rippleColor = ripple
        }
    }

    private fun renderSubmitButtonEnabledState() {
        submitButton.alpha = if (submitButton.isEnabled) 1f else DISABLED_CONTROL_ALPHA
    }

    /** Firebase restores Google Auth automatically; the copy must not ask to sign in again. */
    private fun updateSubmitButtonLabel() {
        if (!::submitButton.isInitialized || !::accountRepository.isInitialized) return
        submitButton.setText(if (accountRepository.isGoogleConnected()) {
            R.string.community_theme_submit_button_connected
        } else {
            R.string.community_theme_submit_button
        })
    }

    /**
     * Anonymous is a per-theme visibility choice, not a way to create disposable identities.
     * Every submitting account reserves one name; once claimed, the field is read-only here.
     */
    private fun updateAuthorPresentation(publishAnonymously: Boolean) {
        authorLayout.visibility = View.VISIBLE
        publicNameInput.imeOptions = EditorInfo.IME_ACTION_NEXT
        val identity = authorIdentity
        if (identity != null) {
            if (authorInput.text?.toString() != identity.authorName) {
                authorInput.setText(identity.authorName)
                authorInput.setSelection(identity.authorName.length)
            }
            authorInput.isEnabled = false
            authorLayout.helperText = getString(
                    if (publishAnonymously) {
                        R.string.community_theme_submit_author_fixed_anonymous_helper
                    } else {
                        R.string.community_theme_submit_author_fixed_helper
                    })
        } else {
            authorInput.isEnabled = !submissionInProgress
            authorLayout.helperText = getString(
                    if (publishAnonymously) {
                        R.string.community_theme_submit_author_first_anonymous_helper
                    } else {
                        R.string.community_theme_submit_author_helper
                    })
        }
    }

    private fun applyAuthorIdentity(identity: CommunityThemeAuthorIdentity) {
        authorIdentity = identity
        updateAuthorPresentation(anonymousAuthorSwitch.isChecked)
    }

    /** Loads the server-owned identity early so returning authors never see an editable field. */
    private fun loadFixedAuthorIdentity() {
        if (!accountRepository.isGoogleConnected()) return
        val uid = accountRepository.userUid() ?: return
        if (authorIdentityRequestUid == uid) return
        authorIdentityRequestUid = uid
        lifecycleScope.launch {
            when (val result = accountRepository.publicAuthorIdentity()) {
                is CommunityThemeAuthorIdentityLoadResult.Claimed ->
                    applyAuthorIdentity(result.identity)
                CommunityThemeAuthorIdentityLoadResult.Unclaimed -> {
                    authorIdentity = null
                    updateAuthorPresentation(anonymousAuthorSwitch.isChecked)
                }
                CommunityThemeAuthorIdentityLoadResult.NotAuthenticated -> Unit
                is CommunityThemeAuthorIdentityLoadResult.Failed ->
                    Timber.d(result.error, "Could not preload fixed community author identity")
            }
        }
    }

    private fun loadInitialTheme() {
        val local = themeRepository.load().firstOrNull { it.id == profileId }
        val initialName = local?.name ?: run {
            showInvalidAndFinish()
            return
        }
        val prepared = themeRepository.prepareCommunityThemeSubmission(profileId, initialName)
        val ready = prepared as? CommunityThemeSubmissionDraftResult.Ready ?: run {
            showInvalidAndFinish(unsupportedSettingMessage(prepared))
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
        // The duplicate check reads a cached catalogue and can touch the network, so the cheap
        // gates above draw first and this only ever tightens the result afterwards.
        lifecycleScope.launch {
            showPreflight(submissionRepository.preflightAgainstPublished(ready.draft))
        }
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
            CommunityThemeSubmissionPreflight.ExactDuplicate -> {
                originality.text = getString(R.string.community_theme_submit_duplicate)
                submitButton.isEnabled = false
            }
            CommunityThemeSubmissionPreflight.InvalidDraft -> {
                originality.text = getString(R.string.community_theme_submit_invalid)
                submitButton.isEnabled = false
            }
        }
        renderSubmitButtonEnabledState()
    }

    private fun submit() {
        if (submissionInProgress) return
        val publicName = publicNameInput.text?.toString().orEmpty()
        val publishAnonymously = anonymousAuthorSwitch.isChecked
        val author = authorInput.text?.toString().orEmpty()
        if (publicName.isBlank()) {
            publicNameLayout.error = getString(R.string.community_theme_submit_name_required)
            publicNameInput.requestFocus()
            return
        }
        if (CommunityThemeAuthorNames.normalize(author) == null) {
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
                showError(unsupportedSettingMessage(draftResult)
                        ?: getString(R.string.community_theme_submit_invalid))
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
            CommunityThemeSubmissionPreflight.ExactDuplicate -> {
                showPreflight(preflight)
                showError(getString(R.string.community_theme_submit_duplicate))
                return
            }
            CommunityThemeSubmissionPreflight.InvalidDraft -> {
                showError(getString(R.string.community_theme_submit_invalid))
                return
            }
            is CommunityThemeSubmissionPreflight.Ready ->
                submitReady(preflight, author, publishAnonymously)
        }
    }

    private fun submitReady(
            preflight: CommunityThemeSubmissionPreflight.Ready,
            author: String,
            publishAnonymously: Boolean
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
                            publishAnonymously,
                            moderationPreview)) {
                        CommunityThemeQueueResult.Queued -> {
                            // After the intake, never inside its transaction, and never a reason to
                            // report failure: the theme is already submitted, and a theme with no
                            // picture is the ordinary state of this feature.
                            val screenshot = attachedScreenshot
                            val attached = screenshot == null ||
                                    submissionRepository.attachScreenshot(
                                            preflight.draft.id,
                                            CommunityThemeScreenshots.SURFACE_PLAYER,
                                            screenshot)
                            showSuccess(screenshotFailed = !attached)
                        }
                        CommunityThemeQueueResult.SubmissionLimitReached -> {
                            setLoading(false)
                            showError(getString(R.string.community_theme_submit_limit_reached))
                        }
                        CommunityThemeQueueResult.ExactDuplicate -> {
                            setLoading(false)
                            submitButton.isEnabled = false
                            originality.text = getString(R.string.community_theme_submit_duplicate)
                            showError(getString(R.string.community_theme_submit_duplicate))
                        }
                        CommunityThemeQueueResult.AuthorNameUnavailable -> {
                            setLoading(false)
                            authorLayout.error = getString(
                                    R.string.community_theme_submit_author_unavailable)
                            authorInput.requestFocus()
                            showError(getString(R.string.community_theme_submit_author_unavailable))
                        }
                        is CommunityThemeQueueResult.AuthorNameLocked -> {
                            setLoading(false)
                            val uid = accountRepository.userUid().orEmpty()
                            applyAuthorIdentity(CommunityThemeAuthorIdentity(
                                    ownerUid = uid,
                                    authorName = queued.authorName,
                                    authorKey = CommunityThemeAuthorNames.keyForCanonicalName(
                                            queued.authorName)))
                            showError(getString(
                                    R.string.community_theme_submit_author_locked,
                                    queued.authorName))
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

    private fun applyPickedScreenshot(uri: Uri, reportErrors: Boolean) {
        chooseScreenshotButton.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { normalizeScreenshot(uri) }
            chooseScreenshotButton.isEnabled = true
            if (result is ScreenshotResult.Ready) {
                attachedScreenshot = result.base64
                pickedScreenshotUri = uri
                screenshotImage.setImageBitmap(result.preview)
                renderScreenshotSlot(R.string.community_theme_submit_screenshot_attached)
                clearError()
                return@launch
            }
            pickedScreenshotUri = null
            if (!reportErrors) return@launch
            showError(when (result) {
                ScreenshotResult.TooSmall -> getString(
                        R.string.community_theme_submit_screenshot_too_small,
                        CommunityThemeScreenshots.MIN_PIXELS)
                ScreenshotResult.TooLarge ->
                        getString(R.string.community_theme_submit_screenshot_too_large)
                else -> getString(R.string.community_theme_submit_screenshot_unreadable)
            })
        }
    }

    private fun clearScreenshot() {
        attachedScreenshot = null
        pickedScreenshotUri = null
        screenshotImage.setImageDrawable(null)
        renderScreenshotSlot(R.string.community_theme_submit_screenshot_placeholder)
        clearError()
    }

    private fun renderScreenshotSlot(statusRes: Int) {
        val attached = attachedScreenshot != null
        screenshotImage.visibility = if (attached) View.VISIBLE else View.GONE
        screenshotEmpty.visibility = if (attached) View.GONE else View.VISIBLE
        removeScreenshotButton.visibility = if (attached) View.VISIBLE else View.GONE
        chooseScreenshotButton.setText(if (attached) {
            R.string.community_theme_submit_screenshot_replace
        } else {
            R.string.community_theme_submit_screenshot_add
        })
        screenshotStatus.setText(statusRes)
    }

    /**
     * Turns a picked gallery image into the exact bytes the submission will carry.
     *
     * Three things happen here that are not obvious from the call site. The source is *sampled*
     * while decoding, because a phone gallery holds pictures far larger than a watch screen and
     * decoding one at full size to immediately shrink it is how an attach slot runs out of memory.
     * It is composited onto an **opaque** ground, which makes libwebp drop the alpha channel and
     * emit a simple `VP8 ` chunk -- the plain form the publisher accepts without having to reason
     * about an extended container. And re-encoding at all is what strips EXIF: an image that
     * passed through a phone gallery can carry a location, and this is the only point at which
     * that is removed rather than merely disallowed.
     */
    private fun normalizeScreenshot(uri: Uri): ScreenshotResult {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (!decode(uri, bounds)) return ScreenshotResult.Unreadable
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ScreenshotResult.Unreadable
        val shorterSide = minOf(bounds.outWidth, bounds.outHeight)
        if (CommunityThemeScreenshots.targetSize(shorterSide) == 0) return ScreenshotResult.TooSmall

        val options = BitmapFactory.Options().apply {
            inSampleSize = CommunityThemeScreenshots.sampleSize(shorterSide)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val source = decodeBitmap(uri, options) ?: return ScreenshotResult.Unreadable
        val size = CommunityThemeScreenshots.targetSize(minOf(source.width, source.height))
        if (size == 0) {
            source.recycle()
            return ScreenshotResult.TooSmall
        }
        val square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cropSize = minOf(source.width, source.height)
        val left = CommunityThemeScreenshots.cropLeft(source.width, source.height)
        val top = CommunityThemeScreenshots.cropTop(source.width, source.height)
        Canvas(square).apply {
            drawColor(Color.BLACK)
            drawBitmap(
                    source,
                    Rect(left, top, left + cropSize, top + cropSize),
                    Rect(0, 0, size, size),
                    Paint(Paint.FILTER_BITMAP_FLAG))
        }
        source.recycle()

        val encoded = encodeWithinBudget(square)
        if (encoded == null) {
            square.recycle()
            return ScreenshotResult.TooLarge
        }
        return ScreenshotResult.Ready(encoded, square)
    }

    private fun decode(uri: Uri, options: BitmapFactory.Options): Boolean = try {
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        true
    } catch (error: Exception) {
        Timber.w(error, "Could not read a picked community-theme screenshot")
        false
    }

    private fun decodeBitmap(uri: Uri, options: BitmapFactory.Options): Bitmap? = try {
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (error: Exception) {
        Timber.w(error, "Could not decode a picked community-theme screenshot")
        null
    }

    /**
     * Walks the quality ladder until the result fits the transport budget.
     *
     * An ordinary screenshot clears the first rung; the rest exist so an unusually noisy picture
     * fails here, where the message can name the picture, rather than at the Firestore write, where
     * it would surface as a bare permission error after the author has already signed in.
     */
    private fun encodeWithinBudget(bitmap: Bitmap): String? {
        for (quality in CommunityThemeScreenshots.QUALITY_LADDER) {
            val bytes = compressWebp(bitmap, quality) ?: continue
            if (bytes.size > CommunityThemeScreenshots.MAX_BYTES) continue
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (CommunityThemeScreenshots.isSubmittableEncoding(encoded)) return encoded
        }
        return null
    }

    private fun compressWebp(bitmap: Bitmap, quality: Int): ByteArray? = try {
        ByteArrayOutputStream().use { output ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            if (bitmap.compress(format, quality, output)) output.toByteArray() else null
        }
    } catch (error: Exception) {
        Timber.w(error, "Could not encode a community-theme image")
        null
    }

    private sealed interface ScreenshotResult {
        /** [preview] is the normalized square the slot displays: what is approved is what is sent. */
        data class Ready(val base64: String, val preview: Bitmap) : ScreenshotResult
        object TooSmall : ScreenshotResult
        object TooLarge : ScreenshotResult
        object Unreadable : ScreenshotResult
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
            val bytes = compressWebp(bitmap, MODERATION_PREVIEW_QUALITY) ?: return null
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
        renderSubmitButtonEnabledState()
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        updateAuthorPresentation(anonymousAuthorSwitch.isChecked)
        if (loading) clearError()
    }

    private fun showError(message: String) {
        error.text = message
        error.visibility = View.VISIBLE
    }

    private fun clearError() {
        if (!submissionInProgress) error.visibility = View.GONE
    }

    private fun showSuccess(screenshotFailed: Boolean = false) {
        setLoading(false)
        val message = if (screenshotFailed) {
            getString(R.string.community_theme_submit_success_message) + "\n\n" +
                    getString(R.string.community_theme_submit_screenshot_failed)
        } else {
            getString(R.string.community_theme_submit_success_message)
        }
        val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.community_theme_submit_success_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    setResult(RESULT_OK)
                    finish()
                }
                .setCancelable(false)
                .create()
        dialog.setOnShowListener {
            val dialogAccent = LyraAccent.contrastSafe(
                    LyraAccent.resolve(this),
                    getColor(R.color.lyra_surface),
                    minimumContrast = 4.5)
            dialog.applyLyraDialogStyling(accent = dialogAccent)
        }
        dialog.show()
    }

    private fun showInvalidAndFinish(message: String? = null) {
        Toast.makeText(
                this,
                message ?: getString(R.string.community_theme_submit_invalid),
                Toast.LENGTH_LONG).show()
        finish()
    }

    /**
     * Turns the one refusal a person can act on into a sentence naming the control to change.
     *
     * Every other way [CommunityThemeSubmissionDraftResult] fails describes a broken or oversized
     * profile, where there is nothing to point at; this one is a choice they made in the Watch
     * appearance tab. Falls back to naming just the setting when the stored value has no label in
     * its picker, since the setting alone is already the whole of what was missing before.
     */
    private fun unsupportedSettingMessage(
            result: CommunityThemeSubmissionDraftResult
    ): String? {
        if (result !is CommunityThemeSubmissionDraftResult.UnsupportedSetting) return null
        val setting = CommunityThemeSettingNames.settingTitle(this, result.key) ?: return null
        val value = CommunityThemeSettingNames.valueLabel(this, result.key, result.value)
        return if (value != null) {
            getString(R.string.community_theme_submit_unsupported_setting_value, setting, value)
        } else {
            getString(R.string.community_theme_submit_unsupported_setting, setting)
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "community_theme_profile_id"

        private const val STATE_SCREENSHOT_URI = "community_theme_screenshot_uri"
        private const val MODERATION_PREVIEW_PIXELS = 200
        private const val MODERATION_PREVIEW_QUALITY = 84
        private const val DISABLED_CONTROL_ALPHA = 0.5f
    }
}
