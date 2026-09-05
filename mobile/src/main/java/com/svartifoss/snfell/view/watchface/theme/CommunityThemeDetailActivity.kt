package com.svartifoss.snfell.view.watchface.theme

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommunityThemeScreenshots
import com.svartifoss.snfell.common.R as commonR
import com.svartifoss.snfell.music.ActiveMediaSessionProvider
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.applyLyraDialogStyling
import com.svartifoss.snfell.view.watchface.WatchPreviewFullScreen
import com.svartifoss.snfell.view.watchface.WatchPreviewView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Detail screen for one immutable community-theme publication.
 *
 * The gallery deliberately owns downloading and catalogue validation. This activity accepts the
 * resulting public metadata and profile JSON as extras so it can be used by the gallery now and
 * by a future deep link without coupling it to a particular list implementation. It may render
 * the current in-memory album cover on this phone only; no cover, media metadata, or screenshot
 * is sent to Firebase, GitHub, the profile, or the connected watch.
 */
class CommunityThemeDetailActivity : AppCompatActivity() {

    private enum class PreviewSurface(
            val buttonId: Int,
            val previewSurface: WatchPreviewView.PreviewSurface,
            val labelRes: Int
    ) {
        PLAYER(
                R.id.community_theme_detail_surface_player,
                WatchPreviewView.PreviewSurface.PLAYER,
                R.string.community_theme_detail_surface_player),
        AOD(
                R.id.community_theme_detail_surface_aod,
                WatchPreviewView.PreviewSurface.AOD,
                R.string.community_theme_detail_surface_aod),
        VOLUME(
                R.id.community_theme_detail_surface_volume,
                WatchPreviewView.PreviewSurface.VOLUME,
                R.string.community_theme_detail_surface_volume),
        PROGRESS(
                R.id.community_theme_detail_surface_progress,
                WatchPreviewView.PreviewSurface.SEEK,
                R.string.community_theme_detail_surface_progress),
        QUICK_PANEL(
                R.id.community_theme_detail_surface_quick_panel,
                WatchPreviewView.PreviewSurface.QUICK_PANEL,
                R.string.community_theme_detail_surface_quick_panel),
        QUEUE(
                R.id.community_theme_detail_surface_queue,
                WatchPreviewView.PreviewSurface.QUEUE,
                R.string.community_theme_detail_surface_queue)
    }

    private lateinit var defaultPrefs: SharedPreferences
    private lateinit var themeRepository: WatchThemeRepository
    private lateinit var previewCard: MaterialCardView
    private lateinit var preview: WatchPreviewView

    /**
     * The enlarged copy, while the full-screen dialog is open.
     *
     * The detail preview is a synthetic render of a theme nobody has installed, which is the only
     * way to see it before adding it -- and at card size that is a hard thing to judge. Held so
     * the surface toggle and the local-artwork callback keep reaching it.
     */
    private var fullScreenPreview: WatchPreviewView? = null

    /** The card preview plus, while it is open, its full-screen copy. */
    private fun previews(action: (WatchPreviewView) -> Unit) {
        if (::preview.isInitialized) action(preview)
        fullScreenPreview?.let(action)
    }
    private lateinit var previewLabel: TextView
    private lateinit var detailName: TextView
    private lateinit var detailAuthor: TextView
    private lateinit var layoutValue: TextView
    private lateinit var minimumVersionValue: TextView
    private lateinit var publishedValue: TextView
    private lateinit var settingsValue: TextView
    private lateinit var downloadsValue: TextView
    private lateinit var error: TextView
    private lateinit var themeActionButton: MaterialButton
    private lateinit var likeButton: MaterialButton
    private lateinit var reportButton: MaterialButton
    private lateinit var surfaceToggleGroup: MaterialButtonToggleGroup
    private lateinit var screenshotImage: ShapeableImageView
    private lateinit var screenshotToggle: ImageButton

    /** Decoded once per visit; kept so switching back to it costs no second fetch. */
    private var authorScreenshot: Bitmap? = null
    private var authorScreenshotSurfaces: List<String> = emptyList()
    private var screenshotLoading = false
    private val onlineThemes by lazy(LazyThreadSafetyMode.NONE) {
        OnlineThemesRepository(applicationContext)
    }

    /**
     * Whether the author's photograph is preferred over the local render, remembered across visits.
     *
     * It gates the **download**, not only the display: turning it off is therefore also the answer
     * for someone who does not want the gallery fetching images at all. Phone-local and deliberately
     * outside `MiscPreferences.EXPORTABLE`, so it never reaches the watch or a saved theme.
     */
    private var showAuthorScreenshot: Boolean
        get() = defaultPrefs.getBoolean(PREF_SHOW_SCREENSHOTS, true)
        set(value) {
            defaultPrefs.edit().putBoolean(PREF_SHOW_SCREENSHOTS, value).apply()
        }
    /** Optional: absence of a default Firebase app must not prevent static detail browsing. */
    private var likeRepository: CommunityThemeLikeRepository? = null
    private var installRepository: CommunityThemeInstallRepository? = null
    private var reportRepository: CommunityThemeReportRepository? = null

    private var selectedSurface = PreviewSurface.PLAYER
    private var parsedProfile: WatchThemeProfile? = null
    private var installedProfile: WatchThemeProfile? = null
    private var publicName = ""
    private var publishedLikes = 0

    /**
     * The catalogue's aggregated install count.
     *
     * Unlike the like count this is never moved by anything the current visit does. An install
     * finishes the screen — the activity closes on success — so there is no moment where a stale
     * figure would sit in front of the person who just changed it, and inventing a +1 on a number
     * whose ledger may already contain this account from an earlier install would be a guess.
     */
    private var publishedInstalls = 0
    /**
     * What this visit changed, relative to the catalogue's aggregated count.
     *
     * The published total is only recomputed when the trusted publisher next runs, so a heart tap
     * would otherwise leave the number it sits next to completely unmoved. Tracking the delta of
     * this session rather than adding one for [currentUserLiked] is what keeps that honest: a like
     * left in an earlier session may already be inside [publishedLikes], and there is no way to
     * ask which — so only a transition the user just performed is allowed to move the figure.
     */
    private var sessionLikeDelta = 0
    private var currentUserLiked = false
    private var likeBusy = false
    private var canInstall = true
    private var likeResultChanged = false
    private var likeStateKnown = false
    private var alreadyReported = false
    /**
     * The author the byline currently names, or null while it names nobody the gallery can filter
     * by. Held rather than re-read from the launching Intent: the name can also come out of the
     * fetched profile, and a byline that looks tappable and then does nothing is worse than one
     * that never offered.
     */
    private var linkedAuthor: String? = null
    private var reportBusy = false
    private val accentPreferenceListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (LyraAccent.affectsResolvedColor(key) && ::themeActionButton.isInitialized) {
                    runOnUiThread(::applyCommunityAccent)
                }
            }

    /** Kept Activity-local so the detail preview neither depends on MainActivity nor persists media. */
    private val activeMediaSessionProvider by lazy(LazyThreadSafetyMode.NONE) {
        ActiveMediaSessionProvider(applicationContext)
    }
    private var localArtworkController: MediaController? = null
    private var localArtworkCallbackRegistered = false
    private val localArtworkCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            pushLocalArtwork(metadata)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community_theme_detail)
        applySystemBarInsets(findViewById(R.id.community_theme_detail_root))

        defaultPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        themeRepository = WatchThemeRepository(this)
        previewCard = findViewById(R.id.community_theme_detail_preview_card)
        preview = findViewById(R.id.community_theme_detail_preview)
        previewLabel = findViewById(R.id.community_theme_detail_preview_label)
        detailName = findViewById(R.id.community_theme_detail_name)
        detailAuthor = findViewById(R.id.community_theme_detail_author)
        layoutValue = findViewById(R.id.community_theme_detail_layout_value)
        minimumVersionValue = findViewById(R.id.community_theme_detail_minimum_version_value)
        publishedValue = findViewById(R.id.community_theme_detail_published_value)
        settingsValue = findViewById(R.id.community_theme_detail_settings_value)
        downloadsValue = findViewById(R.id.community_theme_detail_downloads_value)
        error = findViewById(R.id.community_theme_detail_error)
        themeActionButton = findViewById(R.id.button_add_apply_community_theme)
        likeButton = findViewById(R.id.button_like_community_theme)
        reportButton = findViewById(R.id.button_report_community_theme)
        surfaceToggleGroup = findViewById(R.id.community_theme_detail_surfaces)
        ViewCompat.setAccessibilityHeading(
                findViewById(R.id.community_theme_detail_toolbar_title),
                true)

        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }
        // The surface toggle below the card already answers "show me another screen"; this
        // answers "show me this one properly", which the card is too small to do.
        preview.isClickable = true
        preview.isFocusable = true
        preview.setOnClickListener { openFullScreenPreview() }
        screenshotImage = findViewById(R.id.community_theme_detail_screenshot)
        screenshotToggle = findViewById(R.id.community_theme_detail_screenshot_toggle)
        screenshotImage.setOnClickListener { openFullScreenScreenshot() }
        screenshotToggle.setOnClickListener {
            showAuthorScreenshot = !showAuthorScreenshot
            if (showAuthorScreenshot) loadAuthorScreenshot()
            applyPreviewSource()
        }
        ViewCompat.replaceAccessibilityAction(
                preview,
                AccessibilityActionCompat.ACTION_CLICK,
                getString(R.string.watch_preview_expand),
                null)
        themeActionButton.setOnClickListener { handleThemeAction() }
        detailAuthor.setOnClickListener { openAuthorInGallery() }
        likeButton.setOnClickListener { toggleLike() }
        reportButton.setOnClickListener { openReportDialog() }
        surfaceToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                PreviewSurface.values().firstOrNull { it.buttonId == checkedId }
                        ?.let(::showSurface)
            }
            // ToggleGroup reports the new checked item before it finishes unchecking the old one.
            // Styling from selectedSurface keeps that transient state from leaving two buttons
            // painted as selected after the callbacks complete.
            applyCommunityAccent()
        }

        selectedSurface = savedInstanceState?.getString(STATE_SURFACE)
                ?.let { name -> PreviewSurface.values().firstOrNull { it.name == name } }
                ?: PreviewSurface.PLAYER
        currentUserLiked = savedInstanceState?.getBoolean(STATE_LIKED) ?: false
        sessionLikeDelta = savedInstanceState?.getInt(STATE_LIKE_DELTA) ?: 0
        likeResultChanged = savedInstanceState?.getBoolean(STATE_LIKE_CHANGED) ?: false
        likeStateKnown = savedInstanceState?.getBoolean(STATE_LIKE_KNOWN) ?: false
        alreadyReported = savedInstanceState?.getBoolean(STATE_REPORTED) ?: false
        loadProfile()
        if (likeStateKnown || likeResultChanged) setResult(RESULT_OK, galleryResultIntent())
        observeLocalArtwork()
    }

    override fun onResume() {
        super.onResume()
        applyCommunityAccent()
    }

    override fun onStart() {
        super.onStart()
        defaultPrefs.registerOnSharedPreferenceChangeListener(accentPreferenceListener)
        registerLocalArtworkCallback()
        if (likeBusy) (likeButton.icon as? Animatable)?.start()
    }

    override fun onStop() {
        (likeButton.icon as? Animatable)?.stop()
        unregisterLocalArtworkCallback()
        defaultPrefs.unregisterOnSharedPreferenceChangeListener(accentPreferenceListener)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SURFACE, selectedSurface.name)
        outState.putBoolean(STATE_LIKED, currentUserLiked)
        outState.putInt(STATE_LIKE_DELTA, sessionLikeDelta)
        outState.putBoolean(STATE_LIKE_CHANGED, likeResultChanged)
        outState.putBoolean(STATE_LIKE_KNOWN, likeStateKnown)
        outState.putBoolean(STATE_REPORTED, alreadyReported)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        unbindLocalArtwork()
        previews { it.setNowPlaying(null, null, null) }
        // Clearing the artwork above must reach it, so it is released only afterwards.
        fullScreenPreview = null
        super.onDestroy()
    }

    private fun loadProfile() {
        val profileJson = intent.getStringExtra(EXTRA_PROFILE_JSON)
        if (profileJson.isNullOrBlank()) {
            showProfileError(R.string.community_theme_detail_load_error)
            return
        }

        val rawProfile = try {
            JSONObject(profileJson)
        } catch (_: Exception) {
            showProfileError(R.string.community_theme_detail_load_error)
            return
        }
        val profile = themeRepository.parsePublishedProfile(rawProfile)
                ?: themeRepository.parseTrustedLegacyPhaseOneProfile(rawProfile)
        if (profile == null) {
            showProfileError(R.string.community_theme_detail_load_error)
            return
        }
        if (!matchesPublicExtras(profile)) {
            showProfileError(R.string.community_theme_detail_profile_mismatch)
            return
        }

        parsedProfile = profile
        authorScreenshotSurfaces = screenshotSurfaces(rawProfile)
        loadAuthorScreenshot()
        previewCard.visibility = View.VISIBLE
        publicName = intent.textExtra(EXTRA_NAME) ?: profile.name
        publishedLikes = intent.getIntExtra(EXTRA_LIKES, 0).coerceAtLeast(0)
        publishedInstalls = intent.getIntExtra(EXTRA_INSTALLS, 0).coerceAtLeast(0)
        canInstall = intent.getBooleanExtra(EXTRA_CAN_INSTALL, true)
        refreshInstalledProfile()
        val author = intent.textExtra(EXTRA_AUTHOR) ?: rawProfile.textOrNull("author")
                ?: getString(R.string.community_theme_detail_not_available)
        val minimumVersion = intent.textExtra(EXTRA_MINIMUM_APP_VERSION)
                ?: rawProfile.textOrNull("minimumAppVersion")
                ?: rawProfile.textOrNull("minAppVersion")
                ?: getString(R.string.community_theme_detail_not_available)
        val publishedAt = intent.textExtra(EXTRA_PUBLISHED_AT)
                ?: rawProfile.textOrNull("publishedAt")
                ?: getString(R.string.community_theme_detail_not_available)

        detailName.text = publicName
        renderAuthorLink(author)
        layoutValue.text = WatchThemeRepository.displayNameForFace(this, profile.baseFace)
        minimumVersionValue.text = minimumVersion
        publishedValue.text = formatPublishedAt(publishedAt)
        val settingsCount = formatCount(profile.settings.size)
        settingsValue.text = settingsCount
        settingsValue.contentDescription = resources.getQuantityString(
                R.plurals.community_theme_detail_settings_count,
                profile.settings.size,
                settingsCount)
        renderDownloads()
        // Only the in-memory cover may be used locally. Text, timing, queue, upload and
        // moderation rendering remain on the fixed sample media inside WatchPreviewView.
        preview.setThemeProfile(profile, useLocalArtwork = true)
        if (canInstall || installedProfile != null) {
            error.visibility = View.GONE
        } else {
            error.text = getString(
                    R.string.community_theme_detail_requires_app_version,
                    minimumVersion)
            error.visibility = View.VISIBLE
        }
        renderLikeAction()
        renderReportAction()
        applyCommunityAccent()

        // WatchPreviewView's existing public preference routing selects each real watch surface.
        // Keep this mapping here rather than adding another preview-only API until the gallery
        // needs additional surfaces.
        surfaceToggleGroup.check(selectedSurface.buttonId)
        showSurface(selectedSurface)
        loadCurrentLike()
        loadCurrentReport()
    }

    /**
     * The published download count.
     *
     * A theme published before install aggregation existed carries no figure, and the catalogue
     * reader reports that as zero — so a fresh listing and an unaggregated one look identical
     * here. Both are shown as the number they are rather than hedged: the publisher backfills a
     * missing count on its next run instead of waiting out the refresh interval, so the ambiguous
     * state lasts hours rather than being a permanent property of the entry.
     */
    private fun renderDownloads() {
        val count = formatCount(publishedInstalls)
        downloadsValue.text = count
        downloadsValue.contentDescription = resources.getQuantityString(
                R.plurals.community_theme_detail_downloads_count,
                publishedInstalls,
                count)
    }

    /**
     * Opens the enlarged preview on the surface the card is currently showing.
     *
     * It re-applies the parsed profile rather than sharing state with the card's view: a theme
     * profile is the entire input this screen renders from, and passing it again is what keeps the
     * enlarged copy isolated from the viewer's own appearance in exactly the same way.
     */
    private fun openFullScreenPreview() {
        val profile = parsedProfile ?: return
        fullScreenPreview = WatchPreviewFullScreen.show(
                this,
                onDismiss = { fullScreenPreview = null }
        ) { large ->
            large.setThemeProfile(profile, useLocalArtwork = true)
            large.showPreviewSurface(selectedSurface.previewSurface)
        } ?: return
        pushLocalArtwork(localArtworkController?.metadata)
    }

    private fun showSurface(surface: PreviewSurface) {
        selectedSurface = surface
        previews { it.showPreviewSurface(surface.previewSurface) }
        val surfaceName = getString(surface.labelRes)
        preview.contentDescription = getString(
                R.string.community_theme_detail_preview_description,
                publicName.ifBlank { getString(R.string.community_theme_detail_title) },
                surfaceName)
        applyPreviewSource()
    }

    /**
     * Which surfaces the publisher committed a photograph for.
     *
     * An unknown name is dropped rather than refused: this build may be older than the catalogue,
     * and a profile naming a surface a later version added must still install here. The degraded
     * result is the locally-rendered preview every theme without a photograph already shows.
     */
    private fun screenshotSurfaces(profile: JSONObject): List<String> {
        val declared = profile.optJSONArray("screenshots") ?: return emptyList()
        val surfaces = LinkedHashSet<String>()
        for (index in 0 until declared.length()) {
            val surface = declared.optString(index)
            if (surface in CommunityThemeScreenshots.SURFACES) surfaces.add(surface)
        }
        return surfaces.toList()
    }

    private fun hasAuthorScreenshot(): Boolean =
            CommunityThemeScreenshots.SURFACE_PLAYER in authorScreenshotSurfaces

    private fun loadAuthorScreenshot() {
        val themeId = intent.textExtra(EXTRA_ID) ?: return
        if (!hasAuthorScreenshot() || !showAuthorScreenshot) return
        if (authorScreenshot != null || screenshotLoading) return
        screenshotLoading = true
        lifecycleScope.launch {
            val bytes = onlineThemes.loadScreenshot(
                    themeId,
                    CommunityThemeScreenshots.SURFACE_PLAYER)
            screenshotLoading = false
            authorScreenshot = bytes?.let(::decodeScreenshot)
            authorScreenshot?.let(screenshotImage::setImageBitmap)
            applyPreviewSource()
        }
    }

    private fun decodeScreenshot(bytes: ByteArray): Bitmap? = try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (error: Exception) {
        Timber.d(error, "Could not decode a community theme screenshot")
        null
    }

    /**
     * Chooses between the author's photograph and the local render, and says which is on screen.
     *
     * The photograph only ever covers the Player: it is the one surface an author can capture, and
     * every other one keeps rendering from the profile exactly as before. Saying so matters more
     * here than it looks -- a screenshot is unverifiable, so a visitor has to be able to tell which
     * of the two they are judging, and to reach the honest one in a single tap.
     */
    private fun applyPreviewSource() {
        val surfaceName = getString(selectedSurface.labelRes)
        val onPlayer = selectedSurface == PreviewSurface.PLAYER
        val offered = onPlayer && hasAuthorScreenshot()
        val showing = offered && showAuthorScreenshot && authorScreenshot != null
        screenshotImage.visibility = if (showing) View.VISIBLE else View.GONE
        screenshotToggle.visibility = if (offered) View.VISIBLE else View.GONE
        // The icon names where a tap goes, not where you are: the label below already says which of
        // the two is on screen, and repeating that here would leave the control saying nothing
        // about what it does.
        screenshotToggle.setImageResource(if (showing) R.drawable.ic_watch else R.drawable.ic_menu_gallery)
        screenshotToggle.contentDescription = getString(if (showing) {
            R.string.community_theme_detail_show_rendered
        } else {
            R.string.community_theme_detail_show_author
        })
        TooltipCompat.setTooltipText(screenshotToggle, screenshotToggle.contentDescription)
        previewLabel.text = if (offered) {
            getString(
                    R.string.community_theme_detail_preview_source,
                    getString(R.string.community_theme_detail_preview_label, surfaceName),
                    getString(if (showing) {
                        R.string.community_theme_detail_source_author
                    } else {
                        R.string.community_theme_detail_source_rendered
                    }))
        } else {
            getString(R.string.community_theme_detail_preview_label, surfaceName)
        }
    }

    private fun openFullScreenScreenshot() {
        val bitmap = authorScreenshot ?: return
        WatchPreviewFullScreen.showImage(
                this,
                bitmap,
                getString(R.string.community_theme_detail_screenshot_description))
    }

    /**
     * Mirrors the Watch tab's session selection but intentionally forwards only the cover bitmap.
     * This observer is lifecycle-bound and uses no permission prompt: missing notification access,
     * a stopped player, or a player without artwork simply leaves the built-in sample in place.
     */
    private fun observeLocalArtwork() {
        activeMediaSessionProvider.observe(this) { resource ->
            bindLocalArtwork(resource?.data)
        }
    }

    private fun bindLocalArtwork(controller: MediaController?) {
        if (controller === localArtworkController) {
            registerLocalArtworkCallback()
            pushLocalArtwork(controller?.metadata)
            return
        }
        unregisterLocalArtworkCallback()
        localArtworkController = controller
        registerLocalArtworkCallback()
        pushLocalArtwork(controller?.metadata)
    }

    private fun unbindLocalArtwork() {
        unregisterLocalArtworkCallback()
        localArtworkController = null
    }

    private fun registerLocalArtworkCallback() {
        val controller = localArtworkController ?: return
        if (localArtworkCallbackRegistered) return
        controller.registerCallback(localArtworkCallback)
        localArtworkCallbackRegistered = true
    }

    private fun unregisterLocalArtworkCallback() {
        if (!localArtworkCallbackRegistered) return
        localArtworkController?.unregisterCallback(localArtworkCallback)
        localArtworkCallbackRegistered = false
    }

    private fun pushLocalArtwork(metadata: MediaMetadata?) {
        val artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        previews { it.setNowPlaying(artwork, null, null) }
    }

    /** Reads only the current person's private vote; public counts stay in the static catalogue. */
    private fun loadCurrentLike() {
        val themeId = intent.textExtra(EXTRA_ID) ?: return
        val repository = likeRepositoryOrNull() ?: return
        lifecycleScope.launch {
            likeBusy = true
            renderLikeAction()
            try {
                var resolved = false
                when (val state = repository.load(themeId)) {
                    is CommunityThemeLikeState.Loaded -> {
                        currentUserLiked = state.liked
                        resolved = true
                    }
                    is CommunityThemeLikeState.Failed -> {
                        // Browsing must remain useful if Firestore is temporarily unavailable. The
                        // next explicit heart tap retries and only then shows a failure.
                    }
                }
                if (resolved) publishKnownLikeState()
            } finally {
                likeBusy = false
                renderLikeAction()
            }
        }
    }

    /**
     * Liking needs no account: [CommunityThemeLikeRepository] provisions an anonymous one silently.
     * The current state is read first so two devices cannot flip each other's vote back and forth.
     */
    private fun toggleLike() {
        val themeId = intent.textExtra(EXTRA_ID) ?: return
        if (parsedProfile == null || likeBusy) return
        val repository = likeRepositoryOrNull() ?: run {
            showLikeError()
            return
        }
        lifecycleScope.launch {
            likeBusy = true
            renderLikeAction()
            try {
                when (val state = repository.load(themeId)) {
                    is CommunityThemeLikeState.Loaded -> {
                        currentUserLiked = state.liked
                        publishKnownLikeState()
                        saveLike(repository, themeId, liked = !state.liked)
                    }
                    is CommunityThemeLikeState.Failed -> showLikeError()
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                likeBusy = false
                renderLikeAction()
            }
        }
    }

    private suspend fun saveLike(
            repository: CommunityThemeLikeRepository,
            themeId: String,
            liked: Boolean
    ) {
        when (val result = repository.setLiked(themeId, liked)) {
            is CommunityThemeLikeMutation.Updated -> {
                if (result.liked != currentUserLiked) {
                    sessionLikeDelta += if (result.liked) 1 else -1
                }
                currentUserLiked = result.liked
                likeResultChanged = true
                likeStateKnown = true
                setResult(RESULT_OK, galleryResultIntent())
                Toast.makeText(
                        this,
                        getString(if (result.liked) {
                            R.string.community_theme_detail_like_saved
                        } else {
                            R.string.community_theme_detail_unlike_saved
                        }),
                        Toast.LENGTH_SHORT).show()
            }
            CommunityThemeLikeMutation.InvalidTheme -> showLikeError()
            CommunityThemeLikeMutation.NotReady -> showLikeNotReady()
            is CommunityThemeLikeMutation.Failed -> showLikeError()
        }
    }

    private fun renderLikeAction() {
        likeButton.isEnabled = parsedProfile != null && !likeBusy
        val displayedLikes = displayedLikeCount()
        // Preserve the count's width while the network work uses the icon slot.
        likeButton.text = formatCount(displayedLikes)
        // The bars repeat forever. Stop the old drawable before MaterialButton discards it so a
        // finished/cancelled like cannot leave an orphan animator ticking in the background.
        (likeButton.icon as? Animatable)?.stop()
        likeButton.setIconResource(when {
            likeBusy -> commonR.drawable.ic_equalizer_bars_animated
            currentUserLiked -> R.drawable.ic_favorite
            else -> R.drawable.ic_favorite_border
        })
        if (likeBusy) {
            (likeButton.icon as? Animatable)?.let { animation ->
                if (!animation.isRunning) animation.start()
            }
        }
        val accent = LyraAccent.resolve(this)
        val surface = getColor(R.color.lyra_surface)
        val iconAndText = if (currentUserLiked) {
            LyraAccent.contrastSafe(accent, surface, minimumContrast = 4.5)
        } else {
            getColor(R.color.lyra_on_surface)
        }
        likeButton.iconTint = ColorStateList.valueOf(iconAndText)
        likeButton.setTextColor(iconAndText)
        likeButton.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        likeButton.strokeWidth = 0
        likeButton.rippleColor = ColorStateList.valueOf(getColor(R.color.lyra_ripple))
        likeButton.alpha = if (parsedProfile != null || likeBusy) 1f else 0.5f
        val likeCountDescription = resources.getQuantityString(
                R.plurals.online_theme_likes_count,
                displayedLikes,
                formatCount(displayedLikes))
        likeButton.contentDescription = if (likeBusy) {
            getString(R.string.community_theme_detail_like_loading)
        } else {
            getString(if (currentUserLiked) {
                R.string.community_theme_detail_unlike_description
            } else {
                R.string.community_theme_detail_like_description
            }, publicName, likeCountDescription)
        }
    }

    /** The catalogue's aggregated total, moved by whatever this visit actually changed. */
    private fun displayedLikeCount(): Int = (publishedLikes + sessionLikeDelta).coerceAtLeast(0)

    /** Applies the runtime Lyra accent to every control inflated with the static default theme. */
    private fun applyCommunityAccent() {
        if (!::themeActionButton.isInitialized) return
        val accent = LyraAccent.resolve(this)
        val onAccent = LyraAccent.foregroundFor(accent)
        val surface = getColor(R.color.lyra_surface)
        val background = getColor(R.color.lyra_background)
        val accentText = LyraAccent.contrastSafe(
                accent,
                surface,
                minimumContrast = 4.5)
        val accentOutlineOnSurface = LyraAccent.contrastSafe(
                accent,
                surface,
                minimumContrast = 3.0)
        val accentOutlineOnBackground = LyraAccent.contrastSafe(
                accent,
                background,
                minimumContrast = 3.0)
        val neutralOutline = neutralOutlineOnSurface()
        val removing = installedProfile != null
        val destructiveContent = LyraAccent.contrastSafe(
                error.currentTextColor,
                surface,
                minimumContrast = 4.5)
        val destructiveOutline = LyraAccent.contrastSafe(
                error.currentTextColor,
                background,
                minimumContrast = 3.0)
        themeActionButton.backgroundTintList = ColorStateList.valueOf(
                if (removing) surface else accent)
        themeActionButton.strokeWidth = resources.displayMetrics.density.coerceAtLeast(1f).toInt()
        themeActionButton.strokeColor = ColorStateList.valueOf(
                if (removing) destructiveOutline else accentOutlineOnBackground)
        themeActionButton.setTextColor(if (removing) destructiveContent else onAccent)
        themeActionButton.iconTint = ColorStateList.valueOf(
                if (removing) destructiveContent else onAccent)
        themeActionButton.alpha = if (themeActionButton.isEnabled) 1f else 0.5f

        PreviewSurface.values().forEach { surface ->
            val button = findViewById<MaterialButton>(surface.buttonId)
            val selected = surface == selectedSurface
            button.backgroundTintList = ColorStateList.valueOf(if (selected) {
                accent
            } else {
                getColor(R.color.lyra_surface)
            })
            button.strokeColor = ColorStateList.valueOf(if (selected) {
                accentOutlineOnSurface
            } else {
                neutralOutline
            })
            button.setTextColor(if (selected) onAccent else accentText)
        }
        // A tappable name is the accent colour, the same as the gallery card's byline, so the two
        // screens agree about which text in front of you is a link.
        detailAuthor.setTextColor(
                if (linkedAuthor != null) accentText else getColor(R.color.lyra_text_secondary))
        // The report row is a stock TextButton, so its label came from the theme's colorPrimary -
        // the static Lyra sage, resolved once at inflation and unable to follow the runtime
        // accent. It was the one control on this card still reading green under a custom or
        // album-derived accent. Ripple is set alongside it for the same reason the like button
        // sets one: the default ripple is also drawn from that static primary.
        if (::reportButton.isInitialized) {
            reportButton.setTextColor(accentText)
            reportButton.rippleColor = ColorStateList.valueOf(getColor(R.color.lyra_ripple))
        }
        renderLikeAction()
    }

    private fun formatPublishedAt(raw: String): String = try {
        val locale = displayLocale()
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault())
                .format(Instant.parse(raw))
    } catch (_: Exception) {
        raw
    }

    private fun formatCount(value: Int): String =
            NumberFormat.getIntegerInstance(displayLocale()).format(value.coerceAtLeast(0))

    private fun neutralOutlineOnSurface(): Int {
        val surface = getColor(R.color.lyra_surface)
        val outlineSeed = ColorUtils.blendARGB(
                surface,
                getColor(R.color.lyra_text_secondary),
                0.55f)
        return LyraAccent.contrastSafe(outlineSeed, surface, minimumContrast = 3.0)
    }

    private fun applySystemBarInsets(root: View) {
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                            WindowInsetsCompat.Type.displayCutout())
            view.setPadding(
                    initialLeft + bars.left,
                    initialTop + bars.top,
                    initialRight + bars.right,
                    initialBottom + bars.bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    @Suppress("DEPRECATION")
    private fun displayLocale(): Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        resources.configuration.locales[0] ?: Locale.getDefault()
    } else {
        resources.configuration.locale ?: Locale.getDefault()
    }

    /**
     * Draws the byline and decides whether it is a control at all.
     *
     * A theme whose author could not be resolved shows the fallback text and stops being tappable:
     * filtering the gallery by "Not available" would return an empty list and read as a bug rather
     * than as a missing name.
     */
    private fun renderAuthorLink(author: String) {
        val linkable = author.isNotBlank() &&
                author != getString(R.string.community_theme_detail_not_available)
        detailAuthor.text = if (linkable) {
            getString(R.string.community_theme_detail_byline, author)
        } else {
            author
        }
        // The layout's ?selectableItemBackground is inert on a view that takes no touches, so an
        // unlinkable byline needs nothing removed -- only the click disabled.
        detailAuthor.isClickable = linkable
        detailAuthor.isFocusable = linkable
        detailAuthor.contentDescription = if (linkable) {
            getString(R.string.online_theme_filter_author, author)
        } else {
            detailAuthor.text
        }
        linkedAuthor = author.takeIf { linkable }
        applyCommunityAccent()
    }

    /**
     * Hands the author back to the gallery and closes, rather than opening a second gallery.
     *
     * This screen is always reached from the gallery, and the gallery *is* the list of an author's
     * work. Starting another one would put two catalogues on the back stack, so that pressing Back
     * from an author's themes would land on an unfiltered copy of the same screen.
     */
    private fun openAuthorInGallery() {
        val author = linkedAuthor ?: return
        setResult(RESULT_OK, galleryResultIntent().apply {
            putExtra(EXTRA_AUTHOR_FILTER, author)
        })
        finish()
    }

    private fun galleryResultIntent(): Intent = Intent().apply {
        this@CommunityThemeDetailActivity.intent.textExtra(EXTRA_ID)?.let {
            putExtra(EXTRA_ID, it)
        }
        if (likeStateKnown) putExtra(EXTRA_LIKED, currentUserLiked)
        if (sessionLikeDelta != 0) putExtra(EXTRA_LIKE_DELTA, sessionLikeDelta)
    }

    private fun publishKnownLikeState() {
        likeStateKnown = true
        setResult(RESULT_OK, galleryResultIntent())
    }

    private fun showLikeError() {
        Toast.makeText(
                this,
                R.string.community_theme_detail_like_error,
                Toast.LENGTH_LONG).show()
    }

    private fun showLikeNotReady() {
        Toast.makeText(
                this,
                R.string.community_theme_detail_like_not_ready,
                Toast.LENGTH_LONG).show()
    }

    /**
     * Static Pages previews must remain available when Firebase was deliberately not configured
     * in a development build. A signed-in detail view may try to read its own private reaction,
     * but configuration failure is intentionally treated as an unavailable optional Like feature.
     */
    private fun likeRepositoryOrNull(): CommunityThemeLikeRepository? {
        likeRepository?.let { return it }
        return try {
            CommunityThemeLikeRepository().also { likeRepository = it }
        } catch (_: Exception) {
            null
        }
    }

    /** [likeRepositoryOrNull] for the download ledger; an unconfigured build simply never counts. */
    private fun installRepositoryOrNull(): CommunityThemeInstallRepository? {
        installRepository?.let { return it }
        return try {
            CommunityThemeInstallRepository().also { installRepository = it }
        } catch (_: Exception) {
            null
        }
    }

    private fun reportRepositoryOrNull(): CommunityThemeReportRepository? {
        reportRepository?.let { return it }
        return try {
            CommunityThemeReportRepository().also { reportRepository = it }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads back whether this device has already reported the theme.
     *
     * Failure is silent for the same reason the like lookup's is: the answer only changes a label,
     * and a toast about a background read nobody asked for would be noise. A device that cannot
     * tell falls back to offering the report, which the write itself then reports as already made.
     */
    private fun loadCurrentReport() {
        if (alreadyReported) {
            renderReportAction()
            return
        }
        val themeId = intent.textExtra(EXTRA_ID) ?: return
        val repository = reportRepositoryOrNull() ?: return
        lifecycleScope.launch {
            when (val state = repository.load(themeId)) {
                is CommunityThemeReportState.Loaded -> {
                    alreadyReported = state.reported
                    renderReportAction()
                }
                is CommunityThemeReportState.Failed -> Unit
            }
        }
    }

    private fun renderReportAction() {
        if (!::reportButton.isInitialized) return
        reportButton.isEnabled = parsedProfile != null && !alreadyReported && !reportBusy
        reportButton.setText(if (alreadyReported) {
            R.string.community_theme_report_already
        } else {
            R.string.community_theme_report_action
        })
        reportButton.alpha = if (reportButton.isEnabled) 1f else 0.5f
        reportButton.contentDescription = if (alreadyReported) {
            getString(R.string.community_theme_report_already)
        } else {
            getString(R.string.community_theme_report_description, publicName)
        }
    }

    /**
     * Collects a reason and, optionally, the reporter's own words.
     *
     * Reporting needs no account: [CommunityThemeReportRepository] provisions an anonymous one
     * silently, exactly as a heart tap does. Asking somebody to sign in with Google before they
     * can flag offensive content puts the cost on the wrong person.
     */
    private fun openReportDialog() {
        val themeId = intent.textExtra(EXTRA_ID) ?: return
        if (parsedProfile == null || alreadyReported || reportBusy) return
        val view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_community_theme_report, null, false)
        val reasons = view.findViewById<RadioGroup>(R.id.community_theme_report_reason)
        val details = view.findViewById<EditText>(R.id.community_theme_report_details)
        details.filters = arrayOf(InputFilter.LengthFilter(COMMUNITY_THEME_REPORT_DETAILS_MAX_LENGTH))
        val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.community_theme_report_title)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.community_theme_report_submit) { _, _ ->
                    sendReport(
                            themeId,
                            reasonFor(reasons.checkedRadioButtonId),
                            details.text?.toString())
                }
                .create()
        dialog.setOnShowListener {
            dialog.applyLyraDialogStyling(accent = LyraAccent.contrastSafe(
                    LyraAccent.resolve(this),
                    getColor(R.color.lyra_surface),
                    minimumContrast = 4.5))
        }
        dialog.show()
    }

    /**
     * The radio ids are presentation; the wire values are the contract firestore.rules mirrors.
     *
     * An unrecognised id resolves to [CommunityThemeReportReason.OTHER] rather than refusing to
     * send: the one way to reach that branch is a layout edit, and losing the report because a
     * button was renamed is worse than filing it under the least specific reason.
     */
    private fun reasonFor(checkedId: Int): CommunityThemeReportReason = when (checkedId) {
        R.id.community_theme_report_inappropriate -> CommunityThemeReportReason.INAPPROPRIATE
        R.id.community_theme_report_impersonation -> CommunityThemeReportReason.IMPERSONATION
        R.id.community_theme_report_misleading -> CommunityThemeReportReason.MISLEADING
        R.id.community_theme_report_illegible -> CommunityThemeReportReason.ILLEGIBLE
        R.id.community_theme_report_spam -> CommunityThemeReportReason.SPAM
        else -> CommunityThemeReportReason.OTHER
    }

    private fun sendReport(
            themeId: String,
            reason: CommunityThemeReportReason,
            details: String?
    ) {
        val repository = reportRepositoryOrNull() ?: run {
            Toast.makeText(this, R.string.community_theme_report_error, Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            reportBusy = true
            renderReportAction()
            try {
                val message = when (repository.report(themeId, reason, details)) {
                    CommunityThemeReportResult.Sent -> {
                        alreadyReported = true
                        R.string.community_theme_report_sent
                    }
                    CommunityThemeReportResult.AlreadyReported -> {
                        alreadyReported = true
                        R.string.community_theme_report_already_sent
                    }
                    CommunityThemeReportResult.NotReady -> R.string.community_theme_report_not_ready
                    CommunityThemeReportResult.InvalidTheme,
                    is CommunityThemeReportResult.Failed -> R.string.community_theme_report_error
                }
                Toast.makeText(this@CommunityThemeDetailActivity, message, Toast.LENGTH_LONG).show()
            } catch (error: CancellationException) {
                throw error
            } finally {
                reportBusy = false
                renderReportAction()
            }
        }
    }

    private fun handleThemeAction() {
        installedProfile?.let {
            confirmRemoveTheme(it)
            return
        }
        addAndApply()
    }

    private fun refreshInstalledProfile() {
        val publishedId = parsedProfile?.publishedTheme?.id
        installedProfile = publishedId?.let { id ->
            installedCommunityTheme(themeRepository.load(), id)
        }
        renderThemeAction()
    }

    private fun renderThemeAction() {
        if (!::themeActionButton.isInitialized) return
        val removing = installedProfile != null
        themeActionButton.setText(if (removing) {
            R.string.community_theme_detail_remove
        } else {
            R.string.community_theme_detail_add_apply
        })
        themeActionButton.setIconResource(
                if (removing) R.drawable.ic_delete else R.drawable.ic_download)
        themeActionButton.isEnabled = parsedProfile != null && (removing || canInstall)
    }

    private fun confirmRemoveTheme(profile: WatchThemeProfile) {
        val dialog = AlertDialog.Builder(this)
                .setTitle(getString(R.string.community_theme_detail_remove_title, profile.name))
                .setMessage(R.string.community_theme_detail_remove_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.community_theme_detail_remove) { _, _ ->
                    removeTheme(profile)
                }
                .create()
        dialog.setOnShowListener {
            dialog.applyLyraDialogStyling(
                    accent = LyraAccent.resolve(this),
                    positiveColor = error.currentTextColor)
        }
        dialog.show()
    }

    private fun removeTheme(profile: WatchThemeProfile) {
        val publishedId = profile.publishedTheme?.id ?: return
        val current = installedCommunityTheme(themeRepository.load(), publishedId) ?: run {
            refreshInstalledProfile()
            applyCommunityAccent()
            return
        }
        themeActionButton.isEnabled = false
        themeActionButton.alpha = 0.5f
        val isActive = themeRepository.activeProfile(defaultPrefs)?.id == current.id
        if (isActive && !themeRepository.applyBuiltIn(defaultPrefs, current.baseFace)) {
            Toast.makeText(
                    this,
                    R.string.community_theme_detail_remove_error,
                    Toast.LENGTH_LONG).show()
            renderThemeAction()
            applyCommunityAccent()
            return
        }
        val removed = themeRepository.delete(current) &&
                installedCommunityTheme(themeRepository.load(), publishedId) == null
        if (!removed) {
            // Returning to the built-in face is only a prerequisite for deleting an active
            // profile. If persistence rejects that deletion, restore the user's prior active
            // theme so a failed destructive action has no surprising visible side effect.
            if (isActive) themeRepository.applyProfile(defaultPrefs, current)
            Toast.makeText(
                    this,
                    R.string.community_theme_detail_remove_error,
                    Toast.LENGTH_LONG).show()
            refreshInstalledProfile()
            applyCommunityAccent()
            return
        }

        installedProfile = null
        setResult(RESULT_OK, galleryResultIntent())
        Toast.makeText(
                this,
                R.string.community_theme_detail_removed,
                Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun addAndApply() {
        val profile = parsedProfile ?: return
        themeActionButton.isEnabled = false
        themeActionButton.alpha = 0.5f
        try {
            when (val result = themeRepository.installAndApplyPublishedProfile(defaultPrefs, profile)) {
                is PublishedThemeInstallResult.Applied -> {
                    setResult(RESULT_OK, galleryResultIntent().apply {
                        result.profile.publishedTheme?.id?.let { putExtra(EXTRA_ID, it) }
                    })
                    // The one moment the download figure can honestly be moved: a static catalogue
                    // never sees a download, so a successful install on a phone is the only event
                    // there is to count. Recorded for an already-installed theme too -- the ledger
                    // is one document per account, so re-applying is idempotent, and this is what
                    // backfills somebody who installed before the ledger existed.
                    result.profile.publishedTheme?.id?.let { id ->
                        installRepositoryOrNull()?.recordInstallDetached(id)
                    }
                    val message = if (result.alreadyInstalled) {
                        getString(R.string.community_theme_detail_apply_existing)
                    } else {
                        getString(R.string.online_theme_apply_success, result.profile.name)
                    }
                    Toast.makeText(
                            this,
                            message,
                            Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
                PublishedThemeInstallResult.LibraryFull -> Toast.makeText(
                        this,
                        R.string.watch_theme_limit_reached,
                        Toast.LENGTH_LONG).show()
                PublishedThemeInstallResult.WatchSyncTooLarge -> Toast.makeText(
                        this,
                        R.string.online_theme_sync_limit,
                        Toast.LENGTH_LONG).show()
                PublishedThemeInstallResult.InvalidProfile,
                PublishedThemeInstallResult.ApplyFailed -> Toast.makeText(
                        this,
                        R.string.watch_theme_apply_error,
                        Toast.LENGTH_LONG).show()
            }
        } finally {
            if (!isFinishing) {
                refreshInstalledProfile()
                applyCommunityAccent()
            }
        }
    }

    private fun showProfileError(message: Int) {
        parsedProfile = null
        installedProfile = null
        // The card is hidden below, so nothing would draw the photograph -- but leaving the state
        // behind would let a later successful load show the previous theme's picture.
        authorScreenshotSurfaces = emptyList()
        authorScreenshot = null
        renderThemeAction()
        likeButton.isEnabled = false
        reportButton.isEnabled = false
        error.setText(message)
        error.visibility = View.VISIBLE
        val unavailable = getString(R.string.community_theme_detail_not_available)
        detailName.text = intent.textExtra(EXTRA_NAME) ?: unavailable
        renderAuthorLink(intent.textExtra(EXTRA_AUTHOR) ?: unavailable)
        layoutValue.text = intent.textExtra(EXTRA_BASE_FACE) ?: unavailable
        minimumVersionValue.text = intent.textExtra(EXTRA_MINIMUM_APP_VERSION) ?: unavailable
        publishedValue.text = formatPublishedAt(intent.textExtra(EXTRA_PUBLISHED_AT) ?: unavailable)
        publishedLikes = intent.getIntExtra(EXTRA_LIKES, 0).coerceAtLeast(0)
        publishedInstalls = intent.getIntExtra(EXTRA_INSTALLS, 0).coerceAtLeast(0)
        settingsValue.text = unavailable
        downloadsValue.text = unavailable
        previewCard.visibility = View.GONE
        preview.clearThemeProfile()
        renderLikeAction()
    }

    private fun matchesPublicExtras(profile: WatchThemeProfile): Boolean {
        val source = profile.publishedTheme ?: return false
        val expectedId = intent.textExtra(EXTRA_ID)
        val expectedName = intent.textExtra(EXTRA_NAME)
        val expectedBaseFace = intent.textExtra(EXTRA_BASE_FACE)
        val expectedRevision = intent.getIntExtra(EXTRA_REVISION, 0).takeIf {
            intent.hasExtra(EXTRA_REVISION) && it > 0
        }
        return (expectedId == null || expectedId == source.id) &&
                (expectedName == null || expectedName == profile.name) &&
                (expectedBaseFace == null || expectedBaseFace == profile.baseFace) &&
                (expectedRevision == null || expectedRevision == profile.revision)
    }

    private fun Intent.textExtra(key: String): String? = getStringExtra(key)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun JSONObject.textOrNull(key: String): String? = optString(key)
            .trim()
            .takeIf { it.isNotEmpty() }

    companion object {
        /**
         * Phone-local browsing preference, deliberately outside `MiscPreferences.EXPORTABLE`: which
         * of two pictures this person prefers to look at is not a property of any theme and has no
         * business reaching the watch or a backup.
         */
        const val PREF_SHOW_SCREENSHOTS = "community_theme_show_screenshots"

        const val EXTRA_ID = "com.svartifoss.snfell.extra.COMMUNITY_THEME_ID"
        const val EXTRA_NAME = "com.svartifoss.snfell.extra.COMMUNITY_THEME_NAME"
        const val EXTRA_AUTHOR = "com.svartifoss.snfell.extra.COMMUNITY_THEME_AUTHOR"
        const val EXTRA_BASE_FACE = "com.svartifoss.snfell.extra.COMMUNITY_THEME_BASE_FACE"
        const val EXTRA_REVISION = "com.svartifoss.snfell.extra.COMMUNITY_THEME_REVISION"
        const val EXTRA_MINIMUM_APP_VERSION =
                "com.svartifoss.snfell.extra.COMMUNITY_THEME_MINIMUM_APP_VERSION"
        const val EXTRA_PUBLISHED_AT = "com.svartifoss.snfell.extra.COMMUNITY_THEME_PUBLISHED_AT"
        const val EXTRA_LIKES = "com.svartifoss.snfell.extra.COMMUNITY_THEME_LIKES"
        const val EXTRA_INSTALLS = "com.svartifoss.snfell.extra.COMMUNITY_THEME_INSTALLS"
        /** Returned to the gallery after the viewer explicitly changed their private reaction. */
        const val EXTRA_LIKED = "com.svartifoss.snfell.extra.COMMUNITY_THEME_LIKED"
        const val EXTRA_LIKE_DELTA = "com.svartifoss.snfell.extra.COMMUNITY_THEME_LIKE_DELTA"
        const val EXTRA_AUTHOR_FILTER =
                "com.svartifoss.snfell.extra.COMMUNITY_THEME_AUTHOR_FILTER"
        const val EXTRA_CAN_INSTALL = "com.svartifoss.snfell.extra.COMMUNITY_THEME_CAN_INSTALL"
        const val EXTRA_PROFILE_JSON = "com.svartifoss.snfell.extra.COMMUNITY_THEME_PROFILE_JSON"

        private const val STATE_SURFACE = "community_theme_detail.surface"
        private const val STATE_LIKED = "community_theme_detail.liked"
        private const val STATE_LIKE_DELTA = "community_theme_detail.like_delta"
        private const val STATE_LIKE_CHANGED = "community_theme_detail.like_changed"
        private const val STATE_LIKE_KNOWN = "community_theme_detail.like_known"
        private const val STATE_REPORTED = "community_theme_detail.reported"

        /** Creates an intent whose payload is fully self-contained for gallery and deep-link callers. */
        fun newIntent(
                context: Context,
                id: String,
                name: String,
                author: String,
                baseFace: String,
                revision: Int,
                minimumAppVersion: String,
                publishedAt: String,
                likes: Int,
                installs: Int,
                canInstall: Boolean,
                profileJson: String
        ): Intent = Intent(context, CommunityThemeDetailActivity::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_AUTHOR, author)
            putExtra(EXTRA_BASE_FACE, baseFace)
            putExtra(EXTRA_REVISION, revision)
            putExtra(EXTRA_MINIMUM_APP_VERSION, minimumAppVersion)
            putExtra(EXTRA_PUBLISHED_AT, publishedAt)
            putExtra(EXTRA_LIKES, likes.coerceAtLeast(0))
            putExtra(EXTRA_INSTALLS, installs.coerceAtLeast(0))
            putExtra(EXTRA_CAN_INSTALL, canInstall)
            putExtra(EXTRA_PROFILE_JSON, profileJson)
        }
    }
}

/** A duplicated/user-owned fork has no publication provenance and is intentionally not installed. */
internal fun installedCommunityTheme(
        profiles: List<WatchThemeProfile>,
        publishedId: String
): WatchThemeProfile? = profiles.firstOrNull { it.publishedTheme?.id == publishedId }
