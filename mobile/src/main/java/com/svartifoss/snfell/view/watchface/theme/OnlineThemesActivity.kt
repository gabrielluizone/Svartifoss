package com.svartifoss.snfell.view.watchface.theme

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Animatable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.svartifoss.snfell.BuildConfig
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.ArchivedFaces
import com.svartifoss.snfell.common.R as commonR
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.update.AppVersionComparison
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.MusicLoadingBarsView
import com.svartifoss.snfell.view.applyLyraDialogStyling
import com.google.android.material.imageview.ShapeableImageView
import com.svartifoss.snfell.common.CommunityThemeScreenshots
import com.svartifoss.snfell.view.watchface.WatchPreviewView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.NumberFormat
import java.util.Locale

private data class OnlineThemeKey(
        val id: String,
        val revision: Int
)

private fun OnlineThemeSummary.key(): OnlineThemeKey = OnlineThemeKey(id, revision)

/**
 * Height of a card metric's glyph, in dp, and the reason it is set here rather than in the layout.
 *
 * A compound drawable has no size attribute: `setCompoundDrawablesRelativeWithIntrinsicBounds`
 * draws the vector at whatever it declares, and every icon in this set declares 24dp. Beside a
 * 12sp figure that is half again the height of the number it labels, which is what made the
 * likes/downloads line read as two mismatched controls rather than as one metric row.
 */
private const val METRIC_ICON_DP = 15f

/** Sets a card metric's leading glyph at [METRIC_ICON_DP], already tinted to the row's colour. */
private fun TextView.setMetricIcon(@DrawableRes icon: Int, tint: Int) {
    val drawable = AppCompatResources.getDrawable(context, icon)?.mutate()?.apply {
        val size = (METRIC_ICON_DP * resources.displayMetrics.density).toInt()
        setBounds(0, 0, size, size)
        DrawableCompat.setTint(this, tint)
    }
    setCompoundDrawablesRelative(drawable, null, null, null)
}

/** Adaptive grid policy kept pure so narrow phones and large text cannot create crushed cards. */
internal fun communityGallerySpanCount(viewportWidthDp: Int, fontScale: Float): Int {
    val horizontalListPaddingDp = 14
    val minimumColumnWidthDp = if (fontScale >= 1.3f) 190 else 164
    return ((viewportWidthDp - horizontalListPaddingDp) / minimumColumnWidthDp)
            .coerceIn(1, 4)
}

/** Whether this installed build can open a public profile and/or add it to My themes. */
private enum class OnlineThemeCompatibility {
    SUPPORTED,
    REQUIRES_NEWER_APP,
    UNSUPPORTED
}

/**
 * Opt-in Community-theme discovery. The gallery downloads its public static catalogue only after
 * it opens; searching, filtering and ordering subsequently happen on-device. A card always opens
 * a detail screen first, where adding/applying is a second explicit action.
 */
class OnlineThemesActivity : AppCompatActivity() {

    private lateinit var themeRepository: WatchThemeRepository
    private lateinit var catalogRepository: OnlineThemesRepository
    private lateinit var defaultPrefs: SharedPreferences
    private lateinit var adapter: CommunityGalleryAdapter
    private lateinit var state: View
    private lateinit var stateIcon: ImageView
    private lateinit var stateProgress: MusicLoadingBarsView
    private lateinit var stateTitle: TextView
    private lateinit var stateMessage: TextView
    private lateinit var retryButton: MaterialButton
    private lateinit var stateClearButton: MaterialButton
    private lateinit var discoveryPanel: LinearLayout
    private lateinit var searchLayout: TextInputLayout
    private lateinit var searchInput: TextInputEditText
    private lateinit var filterScroll: View
    private lateinit var layoutFilterChip: Chip
    private lateinit var sortChip: Chip
    private lateinit var installedFilterChip: Chip
    private lateinit var likedFilterChip: Chip
    private lateinit var authorFilterChip: Chip
    private lateinit var clearFiltersChip: Chip
    private lateinit var galleryList: RecyclerView
    private lateinit var refreshButton: ImageButton
    private lateinit var submitFab: FloatingActionButton

    /**
     * Profile ids still to be submitted from one picker run, and how many that run started with.
     *
     * The submission screen owns one theme at a time (it asks for a public name and a pseudonym
     * per theme), so a multi-selection is served by opening it once per pick. Both survive
     * [onSaveInstanceState] because the submission flow includes Google sign-in, which can take
     * this Activity down with it.
     */
    private val submissionQueue = ArrayDeque<String>()
    private var submissionRunSize = 0

    private var catalogJob: Job? = null
    private var searchJob: Job? = null
    /** Increments for every request so a cancelled, blocking HTTP request cannot win the race. */
    private var catalogRequestGeneration = 0L
    /** Increments only after a new catalogue is accepted; tags its preview/detail work. */
    private var galleryGeneration = 0L
    private val previewJobs = mutableMapOf<OnlineThemeKey, Job>()
    private val detailJobs = mutableMapOf<OnlineThemeKey, Job>()
    private val parsedProfiles = mutableMapOf<OnlineThemeKey, WatchThemeProfile>()

    /*
     * The author photographs the cards show, and the fetches in flight for them.
     *
     * Kept beside the parsed profiles because the profile a card already downloads is what declares
     * whether a photograph exists -- the index deliberately does not carry it, since the detail
     * screen fetches the profile anyway and so, it turns out, does every visible card.
     */
    private val screenshotSurfaces = mutableMapOf<OnlineThemeKey, List<String>>()
    private val screenshotJobs = mutableMapOf<OnlineThemeKey, Job>()
    private var allThemes: List<OnlineThemeSummary> = emptyList()
    private var availableFaces: List<String> = emptyList()
    private var discoveryRequest = OnlineThemeDiscoveryRequest()
    /**
     * Reactions this session made, per theme, relative to the catalogue's aggregated counts.
     *
     * The published totals only move when the trusted publisher next runs, so without this a card
     * would answer a like left seconds ago on its detail page with the same unchanged number.
     */
    private val likeDeltas: MutableMap<String, Int> = mutableMapOf()

    /**
     * Ids of catalogue themes this phone has already installed, mirrored from the local library.
     *
     * Held here as well as in the adapter because the “New to me” filter has to run inside
     * discovery, before a card exists to ask. It is derived from the same provenance the adapter
     * reads, through one shared helper, so the row's installed marker and the filter can never
     * disagree about what counts as installed.
     */
    private var installedThemeIds: Set<String> = emptySet()

    /** Loaded only after an explicit request for the private “Liked” filter. */
    private var likedThemeIds: Set<String> = emptySet()
    private var likedThemesLoading = false
    /** Firebase is optional for public discovery, so never construct this eagerly. */
    private var likeRepository: CommunityThemeLikeRepository? = null

    /** MainActivity can keep extracting album accents underneath this standalone Activity. */
    private val accentPreferenceListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (LyraAccent.affectsResolvedColor(key) && ::adapter.isInitialized) {
                    runOnUiThread { applyRuntimeAccent() }
                }
            }

    private val detailLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // The detail screen is the only place that can install a gallery profile. Re-read the
            // local library when it returns so its card immediately reflects an installed/update
            // state without relying on a mutable object passed through an Intent.
            val localProfiles = themeRepository.load()
            installedThemeIds = installedIdsOf(localProfiles)
            adapter.updateInstalled(localProfiles)
            val data = result.data
            val themeId = data?.getStringExtra(CommunityThemeDetailActivity.EXTRA_ID)
            val changedLike = data?.let { intent ->
                if (intent.hasExtra(CommunityThemeDetailActivity.EXTRA_LIKED)) {
                    intent.getBooleanExtra(CommunityThemeDetailActivity.EXTRA_LIKED, false)
                } else {
                    null
                }
            }
            if (themeId != null && changedLike != null) {
                likedThemeIds = likedThemeIds.toMutableSet().apply {
                    if (changedLike) {
                        add(themeId)
                    } else {
                        remove(themeId)
                    }
                }
                adapter.updateKnownLikes(likedThemeIds)
                if (discoveryRequest.likedOnly) applyDiscovery()
            }
            val likeDelta = data?.getIntExtra(
                    CommunityThemeDetailActivity.EXTRA_LIKE_DELTA, 0) ?: 0
            if (themeId != null && likeDelta != 0) {
                likeDeltas[themeId] = (likeDeltas[themeId] ?: 0) + likeDelta
                adapter.updateLikeDeltas(likeDeltas)
            }
            // Tapping an author on the detail screen closes it and lands here, because the list of
            // that author's work is this screen -- opening a second gallery on top of the first
            // would leave two of them on the back stack showing overlapping catalogues.
            data?.getStringExtra(CommunityThemeDetailActivity.EXTRA_AUTHOR_FILTER)
                    ?.takeIf(String::isNotBlank)
                    ?.let(::filterByAuthor)
        }
    }

    private val submitLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            startNextQueuedSubmission()
        } else {
            // Backing out of one form ends the whole run. The remaining picks were chosen in the
            // same gesture, so pushing the next form at someone who just declined this one reads
            // as the app refusing to let go rather than as the queue it is.
            submissionQueue.clear()
            submissionRunSize = 0
        }
        updateSubmitControl()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community_theme_gallery)
        applySystemBarInsets(findViewById(R.id.community_gallery_root))

        themeRepository = WatchThemeRepository(this)
        catalogRepository = OnlineThemesRepository(this)
        defaultPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        savedInstanceState?.getBundle(STATE_LIKE_DELTAS)?.let { stored ->
            stored.keySet().forEach { id -> likeDeltas[id] = stored.getInt(id) }
        }
        likedThemeIds = savedInstanceState?.getStringArrayList(STATE_LIKED_IDS)
                ?.toSet()
                .orEmpty()
        savedInstanceState?.getStringArrayList(STATE_SUBMISSION_QUEUE)?.let(submissionQueue::addAll)
        submissionRunSize = savedInstanceState?.getInt(STATE_SUBMISSION_RUN_SIZE) ?: 0
        discoveryRequest = OnlineThemeDiscoveryRequest(
                query = savedInstanceState?.getString(STATE_QUERY).orEmpty(),
                baseFace = savedInstanceState?.getString(STATE_BASE_FACE)
                        ?.let(OnlineThemeBaseFaceFilter::BaseFace)
                        ?: OnlineThemeBaseFaceFilter.All,
                sort = savedInstanceState?.getString(STATE_SORT)
                        ?.let { saved -> OnlineThemeSort.values().firstOrNull { it.name == saved } }
                        ?: OnlineThemeSort.NEWEST,
                likedOnly = savedInstanceState?.getBoolean(STATE_LIKED_ONLY) ?: false,
                author = savedInstanceState?.getString(STATE_AUTHOR),
                hideInstalled = savedInstanceState?.getBoolean(STATE_HIDE_INSTALLED) ?: true)

        state = findViewById(R.id.community_gallery_state)
        stateIcon = findViewById(R.id.community_gallery_state_icon)
        stateProgress = findViewById(R.id.community_gallery_progress)
        stateTitle = findViewById(R.id.community_gallery_state_title)
        stateMessage = findViewById(R.id.community_gallery_state_message)
        retryButton = findViewById(R.id.community_gallery_retry)
        stateClearButton = findViewById(R.id.community_gallery_state_clear)
        discoveryPanel = findViewById(R.id.community_gallery_discovery)
        searchLayout = findViewById(R.id.community_gallery_search_box)
        searchInput = findViewById(R.id.community_gallery_search)
        filterScroll = findViewById(R.id.community_gallery_filter_scroll)
        layoutFilterChip = findViewById(R.id.community_gallery_layout_filter)
        sortChip = findViewById(R.id.community_gallery_sort)
        installedFilterChip = findViewById(R.id.community_gallery_installed_filter)
        likedFilterChip = findViewById(R.id.community_gallery_liked_filter)
        authorFilterChip = findViewById(R.id.community_gallery_author_filter)
        clearFiltersChip = findViewById(R.id.community_gallery_clear_filters)
        galleryList = findViewById(R.id.community_gallery_list)
        refreshButton = findViewById(R.id.community_gallery_refresh)
        submitFab = findViewById(R.id.community_gallery_submit)
        findViewById<ImageButton>(R.id.community_gallery_submissions).setOnClickListener {
            startActivity(Intent(this, CommunityThemeSubmissionsActivity::class.java))
        }
        ViewCompat.setAccessibilityHeading(
                findViewById(R.id.community_gallery_title),
                true)
        configureGalleryLayout()

        adapter = CommunityGalleryAdapter(
                onPreviewRequired = ::loadPreview,
                onScreenshotRequired = ::loadCardScreenshot,
                showScreenshots = ::showAuthorScreenshots,
                onDetails = ::openDetails,
                onAuthor = ::filterByAuthor)
        adapter.stateRestorationPolicy =
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        galleryList.apply {
            val gridLayoutManager = GridLayoutManager(
                    this@OnlineThemesActivity,
                    communityGallerySpanCount(
                            resources.configuration.screenWidthDp,
                            resources.configuration.fontScale))
            layoutManager = gridLayoutManager
            adapter = this@OnlineThemesActivity.adapter
            itemAnimator = null
            doOnLayout { list ->
                val viewportWidthDp = (list.width / resources.displayMetrics.density).toInt()
                val resolvedSpans = communityGallerySpanCount(
                        viewportWidthDp,
                        resources.configuration.fontScale)
                if (gridLayoutManager.spanCount != resolvedSpans) {
                    gridLayoutManager.spanCount = resolvedSpans
                }
            }
        }

        findViewById<ImageButton>(R.id.community_gallery_back).setOnClickListener { finish() }
        submitFab.setOnClickListener { showSubmissionPicker() }
        refreshButton.setOnClickListener { loadCatalog(true) }
        retryButton.setOnClickListener { loadCatalog(true) }
        stateClearButton.setOnClickListener { clearDiscoveryControls() }
        clearFiltersChip.setOnClickListener { clearDiscoveryControls() }
        layoutFilterChip.setOnClickListener { showLayoutFilterDialog() }
        sortChip.setOnClickListener { showSortDialog() }
        installedFilterChip.setOnClickListener { toggleInstalledFilter() }
        likedFilterChip.setOnClickListener { toggleLikedFilter() }
        authorFilterChip.setOnClickListener { filterByAuthor(null) }
        searchInput.apply {
            setText(discoveryRequest.query)
            setSelection(text?.length ?: 0)
            doAfterTextChanged { editable ->
                val query = editable?.toString().orEmpty()
                searchJob?.cancel()
                if (query != discoveryRequest.query) {
                    discoveryRequest = discoveryRequest.copy(query = query)
                    if (allThemes.isNotEmpty()) {
                        searchJob = lifecycleScope.launch {
                            delay(SEARCH_DEBOUNCE_MS)
                            if (query == discoveryRequest.query) {
                                applyDiscovery(resetScroll = true)
                            }
                        }
                    }
                }
            }
        }
        adapter.updateKnownLikes(likedThemeIds)
        adapter.updateLikeDeltas(likeDeltas)
        updateDiscoveryControls()
        setDiscoveryAvailable(false)
        loadCatalog(forceRefresh = false)
    }

    override fun onStart() {
        super.onStart()
        defaultPrefs.registerOnSharedPreferenceChangeListener(accentPreferenceListener)
        if (!refreshButton.isEnabled) {
            (refreshButton.drawable as? Animatable)?.start()
        }
        if (likedThemesLoading) {
            (likedFilterChip.chipIcon as? Animatable)?.start()
        }
    }

    override fun onResume() {
        super.onResume()
        applyRuntimeAccent()
        // My themes is reachable from the same tab, so the local library can gain or lose an
        // eligible theme while this screen sits in the background.
        updateSubmitControl()
    }

    override fun onStop() {
        (refreshButton.drawable as? Animatable)?.stop()
        (likedFilterChip.chipIcon as? Animatable)?.stop()
        defaultPrefs.unregisterOnSharedPreferenceChangeListener(accentPreferenceListener)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_QUERY, discoveryRequest.query)
        (discoveryRequest.baseFace as? OnlineThemeBaseFaceFilter.BaseFace)?.let {
            outState.putString(STATE_BASE_FACE, it.value)
        }
        outState.putString(STATE_SORT, discoveryRequest.sort.name)
        outState.putBoolean(STATE_LIKED_ONLY, discoveryRequest.likedOnly)
        discoveryRequest.author?.let { outState.putString(STATE_AUTHOR, it) }
        outState.putBoolean(STATE_HIDE_INSTALLED, discoveryRequest.hideInstalled)
        outState.putStringArrayList(STATE_LIKED_IDS, ArrayList(likedThemeIds))
        outState.putBundle(STATE_LIKE_DELTAS, Bundle().apply {
            likeDeltas.forEach { (id, delta) -> putInt(id, delta) }
        })
        outState.putStringArrayList(STATE_SUBMISSION_QUEUE, ArrayList(submissionQueue))
        outState.putInt(STATE_SUBMISSION_RUN_SIZE, submissionRunSize)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        catalogJob?.cancel()
        searchJob?.cancel()
        previewJobs.values.forEach { it.cancel() }
        detailJobs.values.forEach { it.cancel() }
        super.onDestroy()
    }

    private fun loadCatalog(forceRefresh: Boolean) {
        val requestGeneration = ++catalogRequestGeneration
        catalogJob?.cancel()
        val hasVisibleCatalog = allThemes.isNotEmpty()
        refreshButton.isEnabled = false
        refreshButton.visibility = if (hasVisibleCatalog) View.VISIBLE else View.INVISIBLE
        if (hasVisibleCatalog) {
            refreshButton.alpha = 0.5f
            (refreshButton.drawable as? Animatable)?.stop()
            refreshButton.setImageResource(commonR.drawable.ic_equalizer_bars_animated)
            (refreshButton.drawable as? Animatable)?.start()
        }
        if (allThemes.isEmpty()) {
            setDiscoveryAvailable(false)
            showLoadingState()
        }
        catalogJob = lifecycleScope.launch {
            try {
                val themes = catalogRepository.loadCatalog(forceRefresh)
                if (requestGeneration != catalogRequestGeneration) return@launch
                // A catalogue refresh can advance a theme without changing its UUID. Drop all
                // in-memory card work before binding the new revision, so no thumbnail/detail can
                // use a profile parsed for the preceding revision.
                discardLoadedProfiles()
                allThemes = themes
                val localProfiles = themeRepository.load()
                installedThemeIds = installedIdsOf(localProfiles)
                adapter.replaceCatalog(themes, localProfiles)
                updateAvailableFaces()
                updateDiscoveryControls()
                setDiscoveryAvailable(themes.isNotEmpty())
                if (themes.isEmpty()) {
                    // `replaceCatalog` deliberately retains the current visible subset while a
                    // non-empty refresh is being discovered. An accepted empty catalogue is the
                    // one exception: remove old cards before showing the empty-state message.
                    adapter.submit(emptyList())
                    showCatalogEmptyState()
                } else {
                    applyDiscovery()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (allThemes.isEmpty()) {
                    setDiscoveryAvailable(false)
                    showLoadErrorState()
                } else {
                    Toast.makeText(
                            this@OnlineThemesActivity,
                            R.string.online_theme_load_error,
                            Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (requestGeneration == catalogRequestGeneration) {
                    (refreshButton.drawable as? Animatable)?.stop()
                    refreshButton.setImageResource(R.drawable.ic_autorenew)
                    refreshButton.visibility = View.VISIBLE
                    refreshButton.isEnabled = true
                    refreshButton.alpha = 1f
                    applyRuntimeAccent()
                }
            }
        }
    }

    private fun updateAvailableFaces() {
        availableFaces = allThemes.map { it.baseFace }
                .distinct()
                .sortedBy(::displayNameForCatalogFace)
        val selectedFace = (discoveryRequest.baseFace as? OnlineThemeBaseFaceFilter.BaseFace)?.value
        if (selectedFace != null && selectedFace !in availableFaces) {
            discoveryRequest = discoveryRequest.copy(baseFace = OnlineThemeBaseFaceFilter.All)
        }
    }

    private fun showLayoutFilterDialog() {
        if (availableFaces.isEmpty()) return
        val filters: List<OnlineThemeBaseFaceFilter> =
                listOf(OnlineThemeBaseFaceFilter.All) +
                        availableFaces.map(OnlineThemeBaseFaceFilter::BaseFace)
        val labels = arrayOf(getString(R.string.online_theme_filter_all)) +
                availableFaces.map(::displayNameForCatalogFace).toTypedArray()
        val selected = filters.indexOf(discoveryRequest.baseFace).coerceAtLeast(0)
        showDiscoveryChoiceDialog(
                title = getString(R.string.online_theme_filter_layout_dialog),
                labels = labels,
                selectedIndex = selected) { position ->
            val selectedFilter = filters[position]
            if (selectedFilter != discoveryRequest.baseFace) {
                discoveryRequest = discoveryRequest.copy(baseFace = selectedFilter)
                updateDiscoveryControls()
                applyDiscovery(resetScroll = true)
            }
        }
    }

    private fun showSortDialog() {
        val sorts = OnlineThemeSort.values()
        val labels = arrayOf(
                getString(R.string.online_theme_sort_newest),
                getString(R.string.online_theme_sort_most_liked),
                getString(R.string.online_theme_sort_most_downloaded))
        showDiscoveryChoiceDialog(
                title = getString(R.string.online_theme_sort),
                labels = labels,
                selectedIndex = sorts.indexOf(discoveryRequest.sort)) { position ->
            val sort = sorts[position]
            if (sort != discoveryRequest.sort) {
                discoveryRequest = discoveryRequest.copy(sort = sort)
                updateDiscoveryControls()
                applyDiscovery(resetScroll = true)
            }
        }
    }

    /** Uses the same stock single-choice dialog as Watch appearance's Accent floor preference. */
    private fun showDiscoveryChoiceDialog(
            title: String,
            labels: Array<String>,
            selectedIndex: Int,
            onSelected: (Int) -> Unit
    ) {
        val dialog = AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, selectedIndex) { shownDialog, position ->
                    onSelected(position)
                    shownDialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        dialog.setOnShowListener {
            dialog.applyLyraDialogStyling(accent = LyraAccent.resolve(this))
        }
        dialog.show()
    }

    /**
     * The saved themes this phone may offer for review.
     *
     * A gallery install keeps its [PublishedThemeSource], which is exactly what stops it being
     * re-submitted as an original - the rule My themes already applies to its own overflow menu.
     * Duplicating such a theme clears that provenance, so a deliberate fork stays submittable.
     */
    private fun submittableThemes(): List<WatchThemeProfile> =
            themeRepository.load().filter { it.publishedTheme == null }

    /**
     * Offered only when there is something to offer. A picker that can only report having nothing
     * in it is a button whose entire answer is that it should not have been shown.
     */
    private fun updateSubmitControl() {
        if (!::submitFab.isInitialized) return
        submitFab.isVisible = submittableThemes().isNotEmpty()
    }

    private fun showSubmissionPicker() {
        // Capture pending edits first. The Watch editor writes into the custom_active scope, and
        // the submission boundary re-reads each profile from the local library by id.
        themeRepository.captureActive(defaultPrefs)
        val themes = submittableThemes()
        if (themes.isEmpty()) {
            updateSubmitControl()
            showSubmissionMessage(
                    R.string.community_theme_submit_picker_empty_title,
                    R.string.community_theme_submit_picker_empty_message)
            return
        }

        val labels = themes.map { it.name }.toTypedArray()
        val selection = sortedSetOf<Int>()
        // Deliberately no setMessage. AlertController swaps the choice list into the content
        // panel only on the branch where there is no message, so a dialog given both shows the
        // message and no list at all. The cap goes in the title and in the refusal toast.
        val dialog = AlertDialog.Builder(this)
                .setTitle(getString(
                        R.string.community_theme_submit_picker_title,
                        COMMUNITY_THEME_SUBMISSIONS_PER_WINDOW))
                .setMultiChoiceItems(labels, null) { shown, index, isChecked ->
                    val chooser = shown as AlertDialog
                    if (isChecked && selection.size >= COMMUNITY_THEME_SUBMISSIONS_PER_WINDOW) {
                        // Refused here rather than accepted and rejected at the write, which would
                        // only happen after the last form had already been filled in.
                        chooser.listView.setItemChecked(index, false)
                        Toast.makeText(
                                this,
                                getString(
                                        R.string.community_theme_submit_picker_limit,
                                        COMMUNITY_THEME_SUBMISSIONS_PER_WINDOW),
                                Toast.LENGTH_SHORT).show()
                    } else if (isChecked) {
                        selection += index
                    } else {
                        selection -= index
                    }
                    chooser.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled =
                            selection.isNotEmpty()
                }
                .setPositiveButton(R.string.community_theme_submit_picker_confirm) { _, _ ->
                    startSubmissionRun(selection.map { themes[it].id })
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        dialog.setOnShowListener {
            dialog.applyLyraDialogStyling(accent = LyraAccent.resolve(this))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
        }
        dialog.show()
    }

    private fun showSubmissionMessage(titleRes: Int, messageRes: Int) {
        val dialog = AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(android.R.string.ok, null)
                .create()
        dialog.setOnShowListener {
            dialog.applyLyraDialogStyling(accent = LyraAccent.resolve(this))
        }
        dialog.show()
    }

    private fun startSubmissionRun(profileIds: List<String>) {
        submissionQueue.clear()
        submissionQueue.addAll(profileIds)
        submissionRunSize = profileIds.size
        startNextQueuedSubmission()
    }

    /**
     * Opens the submission screen for the next pick, or ends the run when the queue is empty.
     *
     * The position counter is the only thing telling the user that a second form is coming, so it
     * is shown for every theme of a multi-theme run - including the first.
     */
    private fun startNextQueuedSubmission() {
        val nextId = submissionQueue.removeFirstOrNull()
        if (nextId == null) {
            submissionRunSize = 0
            return
        }
        if (submissionRunSize > 1) {
            val name = themeRepository.load()
                    .firstOrNull { it.id == nextId }
                    ?.name
                    .orEmpty()
            Toast.makeText(
                    this,
                    getString(
                            R.string.community_theme_submit_picker_next,
                            submissionRunSize - submissionQueue.size,
                            submissionRunSize,
                            name),
                    Toast.LENGTH_SHORT).show()
        }
        submitLauncher.launch(Intent(this, SubmitCommunityThemeActivity::class.java)
                .putExtra(SubmitCommunityThemeActivity.EXTRA_PROFILE_ID, nextId))
    }

    private fun styleFilterChip(chip: Chip, selected: Boolean) {
        val accent = LyraAccent.resolve(this)
        val surface = getColor(R.color.lyra_surface)
        val selectedContainer = ColorUtils.blendARGB(surface, accent, 0.16f)
        val selectedContent = LyraAccent.contrastSafe(
                accent,
                selectedContainer,
                minimumContrast = 4.5)
        val neutralContent = getColor(R.color.lyra_on_surface)
        val outline = getColor(R.color.lyra_divider)
        val content = if (selected) selectedContent else neutralContent
        chip.chipBackgroundColor = ColorStateList.valueOf(
                if (selected) selectedContainer else surface)
        chip.chipStrokeColor = ColorStateList.valueOf(
                if (selected) selectedContent else outline)
        chip.setTextColor(content)
        chip.chipIconTint = ColorStateList.valueOf(content)
        chip.alpha = if (chip.isEnabled) 1f else 0.45f
    }

    /** Reset is an action, not a selected filter; keep every visual state neutral. */
    private fun styleClearFiltersChip() {
        clearFiltersChip.isCheckable = false
        clearFiltersChip.isChecked = false
        clearFiltersChip.chipBackgroundColor = ColorStateList.valueOf(
                getColor(R.color.lyra_surface))
        clearFiltersChip.chipStrokeColor = ColorStateList.valueOf(
                getColor(R.color.lyra_divider))
        clearFiltersChip.setTextColor(getColor(R.color.lyra_on_surface))
        clearFiltersChip.rippleColor = ColorStateList.valueOf(getColor(R.color.lyra_ripple))
        clearFiltersChip.alpha = if (clearFiltersChip.isEnabled) 1f else 0.45f
    }

    private fun updateDiscoveryControls() {
        updateLayoutFilterControl()
        updateSortControl()
        updateInstalledFilterControl()
        updateLikedFilterControl()
        updateAuthorFilterControl()
        updateClearControl()
        applyRuntimeAccent(refreshCards = false)
    }

    private fun updateLayoutFilterControl() {
        val selectedFace = (discoveryRequest.baseFace as? OnlineThemeBaseFaceFilter.BaseFace)?.value
        val selectedName = selectedFace?.let(::displayNameForCatalogFace)
        layoutFilterChip.text = selectedName ?: getString(R.string.online_theme_filter_layout)
        layoutFilterChip.contentDescription = getString(
                R.string.online_theme_filter_layout_selected,
                selectedName ?: getString(R.string.online_theme_filter_all))
    }

    private fun applyRuntimeAccent(refreshCards: Boolean = true) {
        if (!::searchInput.isInitialized) return
        val accent = LyraAccent.resolve(this)
        val accentIconOnBackground = LyraAccent.contrastSafe(
                accent,
                getColor(R.color.lyra_background),
                minimumContrast = 3.0)
        val accentTextOnBackground = LyraAccent.contrastSafe(
                accent,
                getColor(R.color.lyra_background),
                minimumContrast = 4.5)
        val onAccent = LyraAccent.foregroundFor(accent)

        // Discovery is content, not album art. A neutral cursor and subtle neutral selection keep
        // typed text readable even when the current music-derived accent is almost white.
        val searchContent = getColor(R.color.lyra_on_surface)
        LyraAccent.applyToEditText(searchInput, searchContent)
        // TextInputLayout installs its own filled-box drawable as this EditText's background, so
        // the focus-dependent background tint LyraAccent applies for bare EditTexts repaints the
        // entire search box the moment it takes focus — a near-black or near-white slab instead of
        // the quiet surface it shows at rest. The box keeps one neutral colour in every state;
        // only the cursor, handles and selection carry a tint.
        searchInput.backgroundTintList = null
        searchInput.highlightColor = ColorUtils.setAlphaComponent(searchContent, 0x24)
        searchInput.setTextColor(searchContent)
        searchLayout.boxBackgroundColor = getColor(R.color.lyra_surface)
        val neutralIcon = getColor(R.color.lyra_text_secondary)
        searchInput.setHintTextColor(neutralIcon)
        searchLayout.setStartIconTintList(ColorStateList.valueOf(neutralIcon))
        searchLayout.setEndIconTintList(ColorStateList.valueOf(neutralIcon))
        refreshButton.imageTintList = ColorStateList.valueOf(getColor(R.color.lyra_on_surface))
        stateIcon.imageTintList = ColorStateList.valueOf(accentIconOnBackground)
        stateProgress.setBarsColor(accentIconOnBackground)

        styleFilterChip(
                layoutFilterChip,
                discoveryRequest.baseFace !is OnlineThemeBaseFaceFilter.All)
        styleFilterChip(sortChip, selected = false)
        styleFilterChip(installedFilterChip, discoveryRequest.hideInstalled)
        styleFilterChip(likedFilterChip, discoveryRequest.likedOnly)
        styleFilterChip(authorFilterChip, selected = true)
        styleClearFiltersChip()

        retryButton.backgroundTintList = ColorStateList.valueOf(accent)
        retryButton.strokeWidth = resources.displayMetrics.density.coerceAtLeast(1f).toInt()
        retryButton.strokeColor = ColorStateList.valueOf(accentIconOnBackground)
        retryButton.iconTint = ColorStateList.valueOf(onAccent)
        retryButton.setTextColor(onAccent)
        stateClearButton.setTextColor(accentTextOnBackground)
        submitFab.backgroundTintList = ColorStateList.valueOf(accent)
        submitFab.imageTintList = ColorStateList.valueOf(onAccent)
        if (refreshCards) adapter.refreshAccent()
    }

    private fun updateSortControl() {
        val (shortLabel, fullLabel) = when (discoveryRequest.sort) {
            OnlineThemeSort.NEWEST -> R.string.online_theme_sort_newest_short to
                    R.string.online_theme_sort_newest
            OnlineThemeSort.MOST_LIKED -> R.string.online_theme_sort_most_liked_short to
                    R.string.online_theme_sort_most_liked
            OnlineThemeSort.MOST_DOWNLOADED ->
                    R.string.online_theme_sort_most_downloaded_short to
                            R.string.online_theme_sort_most_downloaded
        }
        sortChip.setText(shortLabel)
        sortChip.contentDescription = getString(
                R.string.online_theme_sort_selected,
                getString(fullLabel))
    }

    private fun updateLikedFilterControl() {
        likedFilterChip.isEnabled = allThemes.isNotEmpty() && !likedThemesLoading
        likedFilterChip.setText(R.string.online_theme_filter_liked)
        styleFilterChip(likedFilterChip, discoveryRequest.likedOnly)
        (likedFilterChip.chipIcon as? Animatable)?.stop()
        likedFilterChip.setChipIconResource(when {
            likedThemesLoading -> commonR.drawable.ic_equalizer_bars_animated
            discoveryRequest.likedOnly -> R.drawable.ic_favorite
            else -> R.drawable.ic_favorite_border
        })
        if (likedThemesLoading) {
            (likedFilterChip.chipIcon as? Animatable)?.let { animation ->
                if (!animation.isRunning) animation.start()
            }
        }
        likedFilterChip.contentDescription = getString(when {
            likedThemesLoading -> R.string.online_theme_filter_liked_loading
            discoveryRequest.likedOnly -> R.string.online_theme_filter_liked_selected
            else -> R.string.online_theme_filter_liked
        })
    }

    /**
     * Narrows the gallery to one author, or clears that filter when given null.
     *
     * The search box is cleared alongside it. Arriving here is always a tap on a name, and a query
     * still sitting in the field would silently intersect with it -- the user would see "themes by
     * Aurora" on the chip and a list that had also been narrowed by whatever they last typed.
     */
    private fun filterByAuthor(author: String?) {
        val requested = author?.takeIf(String::isNotBlank)
        if (requested == discoveryRequest.author && searchInput.text.isNullOrEmpty()) return
        discoveryRequest = discoveryRequest.copy(author = requested, query = "")
        if (searchInput.text?.isNotEmpty() == true) {
            // The watcher reads the already-cleared request, so this does not discover twice.
            searchInput.setText("")
        }
        updateDiscoveryControls()
        if (allThemes.isNotEmpty()) applyDiscovery(resetScroll = true)
    }

    private fun updateAuthorFilterControl() {
        val author = discoveryRequest.author?.takeIf(String::isNotBlank)
        authorFilterChip.visibility = if (author == null) View.GONE else View.VISIBLE
        if (author == null) return
        // The author's own name is the label; a fixed word would leave the one filter you reach by
        // tapping a name unable to say which name it was.
        authorFilterChip.text = author
        authorFilterChip.contentDescription = getString(
                R.string.online_theme_filter_author_selected,
                author)
    }

    private fun updateClearControl() {
        val visible = hasNonDefaultDiscoveryControls()
        clearFiltersChip.visibility = if (visible) View.VISIBLE else View.GONE
        clearFiltersChip.isEnabled = !likedThemesLoading
    }

    private fun accentTextOnSurface(accent: Int = LyraAccent.resolve(this)): Int =
            LyraAccent.contrastSafe(
                    accent,
                    getColor(R.color.lyra_surface),
                    minimumContrast = 4.5)

    private fun displayNameForCatalogFace(face: String): String =
            if (face in ThemeAppearance.ALLOWED_BASE_FACES) {
                WatchThemeRepository.displayNameForFace(this, face)
            } else {
                face
            }

    /** The catalogue's aggregated total for one theme, moved by what this session changed. */
    private fun displayedLikeCount(summary: OnlineThemeSummary): Int =
            (summary.likes + (likeDeltas[summary.id] ?: 0)).coerceAtLeast(0)

    private fun formatCount(value: Int): String =
            NumberFormat.getIntegerInstance(displayLocale()).format(value.coerceAtLeast(0))

    @Suppress("DEPRECATION")
    private fun displayLocale(): Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        resources.configuration.locales[0] ?: Locale.getDefault()
    } else {
        resources.configuration.locale ?: Locale.getDefault()
    }

    /** Keeps the gallery useful on a short landscape phone without duplicating the whole layout. */
    private fun configureGalleryLayout() {
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        discoveryPanel.orientation = LinearLayout.HORIZONTAL
        (searchLayout.layoutParams as LinearLayout.LayoutParams).apply {
            width = 0
            height = LinearLayout.LayoutParams.WRAP_CONTENT
            weight = 1f
            marginStart = dp(12)
            marginEnd = dp(4)
            topMargin = dp(4)
            bottomMargin = dp(4)
            searchLayout.layoutParams = this
        }
        (filterScroll.layoutParams as LinearLayout.LayoutParams).apply {
            width = 0
            height = LinearLayout.LayoutParams.WRAP_CONTENT
            weight = 1.15f
            marginStart = 0
            marginEnd = 0
            topMargin = 0
            bottomMargin = 0
            filterScroll.layoutParams = this
        }
        filterScroll.setPaddingRelative(
                dp(4),
                filterScroll.paddingTop,
                dp(12),
                filterScroll.paddingBottom)
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

    private fun shapeDrawable(
            color: Int,
            cornerRadiusDp: Float,
            strokeColor: Int? = null
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cornerRadiusDp * resources.displayMetrics.density
        setColor(color)
        strokeColor?.let {
            setStroke(resources.displayMetrics.density.coerceAtLeast(1f).toInt(), it)
        }
    }

    private fun setDiscoveryAvailable(available: Boolean) {
        discoveryPanel.visibility = if (available) View.VISIBLE else View.GONE
        searchLayout.isEnabled = available
        layoutFilterChip.isEnabled = available && availableFaces.isNotEmpty()
        sortChip.isEnabled = available
        installedFilterChip.isEnabled = available
        authorFilterChip.isEnabled = available
        likedFilterChip.isEnabled = available && !likedThemesLoading
        applyRuntimeAccent(refreshCards = false)
    }

    private fun hasActiveFilters(): Boolean =
            discoveryRequest.query.isNotBlank() ||
                    discoveryRequest.baseFace !is OnlineThemeBaseFaceFilter.All ||
                    !discoveryRequest.author.isNullOrBlank() ||
                    discoveryRequest.likedOnly

    private fun hasNonDefaultDiscoveryControls(): Boolean =
            hasActiveFilters() ||
                    discoveryRequest.sort != OnlineThemeSort.NEWEST ||
                    // Off is the non-default state here: the filter ships on.
                    !discoveryRequest.hideInstalled

    private fun clearDiscoveryControls() {
        if (likedThemesLoading || !hasNonDefaultDiscoveryControls()) return
        discoveryRequest = OnlineThemeDiscoveryRequest()
        if (searchInput.text?.isNotEmpty() == true) {
            // The watcher sees the already-cleared request and therefore does not double-discover.
            searchInput.setText("")
        }
        updateDiscoveryControls()
        if (allThemes.isNotEmpty()) applyDiscovery(resetScroll = true)
    }

    /**
     * Purely local, unlike every other filter chip: it reads the installed library, never Firebase
     * or the network, so it can be toggled freely and can ship enabled.
     */
    private fun toggleInstalledFilter() {
        discoveryRequest = discoveryRequest.copy(hideInstalled = !discoveryRequest.hideInstalled)
        updateDiscoveryControls()
        if (allThemes.isNotEmpty()) applyDiscovery(resetScroll = true)
    }

    private fun updateInstalledFilterControl() {
        installedFilterChip.setText(R.string.online_theme_filter_not_installed)
        styleFilterChip(installedFilterChip, discoveryRequest.hideInstalled)
        installedFilterChip.setChipIconResource(if (discoveryRequest.hideInstalled) {
            R.drawable.ic_download_for_offline
        } else {
            R.drawable.ic_cloud_download
        })
        installedFilterChip.contentDescription = getString(if (discoveryRequest.hideInstalled) {
            R.string.online_theme_filter_not_installed_selected
        } else {
            R.string.online_theme_filter_not_installed_off
        })
    }

    /** One derivation of "installed", shared with the adapter's per-card marker. */
    private fun installedIdsOf(localProfiles: List<WatchThemeProfile>): Set<String> =
            localProfiles.mapNotNullTo(mutableSetOf()) { it.publishedTheme?.id }

    /** The only gallery control that may read private Firebase documents or offer sign-in. */
    private fun toggleLikedFilter() {
        if (likedThemesLoading) return
        if (discoveryRequest.likedOnly) {
            discoveryRequest = discoveryRequest.copy(likedOnly = false)
            updateDiscoveryControls()
            applyDiscovery(resetScroll = true)
            return
        }
        if (allThemes.isEmpty()) return
        val repository = likeRepositoryOrNull() ?: run {
            showLikedThemesError()
            return
        }
        val catalogueGeneration = galleryGeneration
        val themeIds = allThemes.map(OnlineThemeSummary::id)
        lifecycleScope.launch {
            likedThemesLoading = true
            updateDiscoveryControls()
            try {
                when (val state = repository.loadLikedThemeIds(themeIds)) {
                    is CommunityThemeLikedThemesState.Loaded -> {
                        if (catalogueGeneration != galleryGeneration) return@launch
                        likedThemeIds = state.themeIds
                        adapter.updateKnownLikes(likedThemeIds)
                        discoveryRequest = discoveryRequest.copy(likedOnly = true)
                        updateDiscoveryControls()
                        applyDiscovery(resetScroll = true)
                    }
                    is CommunityThemeLikedThemesState.Failed -> showLikedThemesError()
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                likedThemesLoading = false
                updateDiscoveryControls()
            }
        }
    }

    private fun showLikedThemesError() {
        Toast.makeText(
                this,
                R.string.online_theme_filter_liked_error,
                Toast.LENGTH_LONG).show()
    }

    private fun likeRepositoryOrNull(): CommunityThemeLikeRepository? {
        likeRepository?.let { return it }
        return try {
            CommunityThemeLikeRepository().also { likeRepository = it }
        } catch (_: Exception) {
            null
        }
    }

    /** Applies pure local discovery so changing controls never adds Firebase/network traffic. */
    private fun applyDiscovery(resetScroll: Boolean = false) {
        val visibleThemes = OnlineThemeDiscovery.discover(
                themes = allThemes,
                request = discoveryRequest,
                likedThemeIds = likedThemeIds,
                installedThemeIds = installedThemeIds)
        adapter.submit(visibleThemes)
        if (resetScroll && visibleThemes.isNotEmpty()) galleryList.scrollToPosition(0)
        updateClearControl()
        if (visibleThemes.isEmpty()) {
            showSearchEmptyState()
        } else {
            state.visibility = View.GONE
        }
    }

    /** Loads only a card's full profile, because the index is deliberately compact. */
    private fun loadPreview(summary: OnlineThemeSummary) {
        if (compatibility(summary) == OnlineThemeCompatibility.UNSUPPORTED) return
        val key = summary.key()
        if (key in parsedProfiles || key in previewJobs) return
        val generation = galleryGeneration
        adapter.setPreviewLoading(summary, true)
        previewJobs[key] = lifecycleScope.launch {
            try {
                val profile = fetchProfile(summary, generation)
                if (generation == galleryGeneration) {
                    if (profile != null) {
                        adapter.setPreview(summary, profile)
                    } else {
                        adapter.setPreviewFailed(summary)
                    }
                }
            } finally {
                if (generation == galleryGeneration) {
                    previewJobs.remove(key)
                    adapter.setPreviewLoading(summary, false)
                }
            }
        }
    }

    private suspend fun fetchProfile(
            summary: OnlineThemeSummary,
            generation: Long = galleryGeneration
    ): WatchThemeProfile? {
        val key = summary.key()
        parsedProfiles[key]?.let { return it }
        return try {
            val onlineTheme = catalogRepository.loadTheme(summary)
            if (generation == galleryGeneration) {
                screenshotSurfaces[key] = onlineTheme.screenshots
            }
            (themeRepository.parsePublishedProfile(onlineTheme.profileJson)
                    // The compatibility reader itself is pinned to one Phase-1 public ID. It is
                    // intentionally only attempted for a Pages profile after the catalogue has
                    // already verified the id/revision/metadata pairing.
                    ?: themeRepository.parseTrustedLegacyPhaseOneProfile(onlineTheme.profileJson))?.also {
                if (generation == galleryGeneration) parsedProfiles[key] = it
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Loads the author's photograph for one card, once.
     *
     * The same lazy-on-bind shape the profile above already uses, and for the same reason: the list
     * only ever pays for the rows somebody actually scrolled to. Reuses the repository's ETag disk
     * cache, so a card that has been seen once costs nothing again.
     */
    private fun loadCardScreenshot(summary: OnlineThemeSummary) {
        if (!showAuthorScreenshots()) return
        val key = summary.key()
        if (key in screenshotJobs || adapter.hasScreenshot(key)) return
        if (CommunityThemeScreenshots.SURFACE_PLAYER !in screenshotSurfaces[key].orEmpty()) return
        val generation = galleryGeneration
        screenshotJobs[key] = lifecycleScope.launch {
            val bitmap = try {
                catalogRepository.loadScreenshot(
                        summary.id,
                        CommunityThemeScreenshots.SURFACE_PLAYER)
                        ?.let { bytes ->
                            withContext(Dispatchers.Default) {
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                        }
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                Timber.d(error, "Could not load the card screenshot of %s", summary.id)
                null
            }
            screenshotJobs -= key
            // A photograph that never arrives leaves the rendered miniature in place, which is what
            // every theme without one already shows.
            if (bitmap != null && generation == galleryGeneration) {
                adapter.setScreenshot(summary, bitmap)
            }
        }
    }

    private fun showAuthorScreenshots(): Boolean = defaultPrefs.getBoolean(
            CommunityThemeDetailActivity.PREF_SHOW_SCREENSHOTS, true)

    /** Opens a validated detail screen; it deliberately does not add or apply a theme itself. */
    private fun openDetails(summary: OnlineThemeSummary) {
        if (compatibility(summary) == OnlineThemeCompatibility.UNSUPPORTED) return
        val key = summary.key()
        if (key in detailJobs) return
        val generation = galleryGeneration
        detailJobs[key] = lifecycleScope.launch {
            adapter.setOpening(summary, true)
            try {
                val onlineTheme = catalogRepository.loadTheme(summary)
                if (generation != galleryGeneration) return@launch
                val profile = themeRepository.parsePublishedProfile(onlineTheme.profileJson)
                        ?: themeRepository.parseTrustedLegacyPhaseOneProfile(onlineTheme.profileJson)
                if (profile == null) {
                    Toast.makeText(
                            this@OnlineThemesActivity,
                            R.string.online_theme_preview_error,
                            Toast.LENGTH_LONG).show()
                    return@launch
                }
                parsedProfiles[key] = profile
                detailLauncher.launch(CommunityThemeDetailActivity.newIntent(
                        context = this@OnlineThemesActivity,
                        id = summary.id,
                        name = summary.name,
                        author = summary.author,
                        baseFace = summary.baseFace,
                        revision = summary.revision,
                        minimumAppVersion = summary.minimumAppVersion,
                        publishedAt = summary.publishedAt,
                        likes = displayedLikeCount(summary),
                        installs = summary.installs,
                        canInstall = compatibility(summary) == OnlineThemeCompatibility.SUPPORTED,
                        profileJson = onlineTheme.profileJson.toString()))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(
                        this@OnlineThemesActivity,
                        R.string.online_theme_preview_error,
                        Toast.LENGTH_LONG).show()
            } finally {
                if (generation == galleryGeneration) {
                    detailJobs.remove(key)
                    adapter.setOpening(summary, false)
                }
            }
        }
    }

    /** Clears values tied to a previous catalogue response, including unfinished old revisions. */
    private fun discardLoadedProfiles() {
        galleryGeneration++
        previewJobs.values.forEach { it.cancel() }
        previewJobs.clear()
        detailJobs.values.forEach { it.cancel() }
        detailJobs.clear()
        parsedProfiles.clear()
    }

    private fun showLoadingState() {
        state.visibility = View.VISIBLE
        stateIcon.visibility = View.GONE
        stateProgress.visibility = View.VISIBLE
        stateTitle.setText(R.string.online_theme_loading)
        stateMessage.visibility = View.GONE
        retryButton.visibility = View.GONE
        stateClearButton.visibility = View.GONE
    }

    private fun showCatalogEmptyState() {
        showMessageState(
                icon = R.drawable.ic_palette,
                title = R.string.online_theme_empty,
                message = R.string.online_theme_empty_hint)
    }

    private fun showSearchEmptyState() {
        /*
         * "Nothing matches these filters" is misleading when the only filter doing the hiding is
         * the one that ships on and the real answer is that you already have them all. Detected by
         * asking discovery the same question with that filter off, rather than by counting ids, so
         * the search terms and layout chip still apply.
         */
        val onlyHiddenByInstalled = discoveryRequest.hideInstalled &&
                // The all-installed message says "every community theme", which stops being true
                // the moment the list is one author's. Under that filter the generic message is
                // the accurate one, and it offers the same Clear.
                discoveryRequest.author == null &&
                allThemes.isNotEmpty() &&
                OnlineThemeDiscovery.discover(
                        themes = allThemes,
                        request = discoveryRequest.copy(hideInstalled = false),
                        likedThemeIds = likedThemeIds,
                        installedThemeIds = installedThemeIds).isNotEmpty()
        showMessageState(
                icon = if (onlyHiddenByInstalled) {
                    R.drawable.ic_download_for_offline
                } else {
                    R.drawable.ic_tune
                },
                title = if (onlyHiddenByInstalled) {
                    R.string.online_theme_search_empty_all_installed
                } else {
                    R.string.online_theme_search_empty
                },
                message = if (onlyHiddenByInstalled) {
                    R.string.online_theme_search_empty_all_installed_hint
                } else {
                    R.string.online_theme_search_empty_hint
                },
                clear = hasNonDefaultDiscoveryControls())
        // The central recovery action is clearer than showing two simultaneous Clear buttons.
        clearFiltersChip.visibility = View.GONE
    }

    private fun showLoadErrorState() {
        showMessageState(
                icon = R.drawable.ic_signal_wifi_off,
                title = R.string.online_theme_load_error_title,
                message = R.string.online_theme_load_error,
                retry = true)
    }

    private fun showMessageState(
            icon: Int,
            title: Int,
            message: Int,
            retry: Boolean = false,
            clear: Boolean = false
    ) {
        state.visibility = View.VISIBLE
        stateIcon.setImageResource(icon)
        stateIcon.visibility = View.VISIBLE
        stateProgress.visibility = View.GONE
        stateTitle.setText(title)
        stateMessage.setText(message)
        stateMessage.visibility = View.VISIBLE
        retryButton.visibility = if (retry) View.VISIBLE else View.GONE
        stateClearButton.visibility = if (clear) View.VISIBLE else View.GONE
        applyRuntimeAccent(refreshCards = false)
    }

    /**
     * An index can outlive an installed app by years. A profile using an unknown schema or retired
     * face cannot be parsed safely, so it stays visible but is not tappable. A known profile that
     * merely needs a newer app can still open its detail page and explain why installation is
     * disabled there.
     */
    private fun compatibility(summary: OnlineThemeSummary): OnlineThemeCompatibility = when {
        summary.schemaVersion != WatchThemeRepository.LIBRARY_SCHEMA ||
                summary.baseFace !in ThemeAppearance.ALLOWED_BASE_FACES ||
                summary.baseFace in ArchivedFaces.KEYS -> OnlineThemeCompatibility.UNSUPPORTED
        AppVersionComparison.isNewer(summary.minimumAppVersion, BuildConfig.VERSION_NAME) ->
            OnlineThemeCompatibility.REQUIRES_NEWER_APP
        else -> OnlineThemeCompatibility.SUPPORTED
    }

    private inner class CommunityGalleryAdapter(
            private val onPreviewRequired: (OnlineThemeSummary) -> Unit,
            private val onScreenshotRequired: (OnlineThemeSummary) -> Unit,
            private val showScreenshots: () -> Boolean,
            private val onDetails: (OnlineThemeSummary) -> Unit,
            private val onAuthor: (String) -> Unit
    ) : RecyclerView.Adapter<CommunityGalleryAdapter.ThemeHolder>() {

        private var themes: List<OnlineThemeSummary> = emptyList()
        private var catalogue: List<OnlineThemeSummary> = emptyList()
        private val previews = mutableMapOf<OnlineThemeKey, WatchThemeProfile>()
        private val screenshots = mutableMapOf<OnlineThemeKey, Bitmap>()
        private val loadingPreviews = mutableSetOf<OnlineThemeKey>()
        private val failedPreviews = mutableSetOf<OnlineThemeKey>()
        private val opening = mutableSetOf<OnlineThemeKey>()
        private val installed = mutableSetOf<String>()
        private val updates = mutableSetOf<String>()
        private var knownLikedIds: Set<String> = emptySet()
        private var knownLikeDeltas: Map<String, Int> = emptyMap()

        /** Replaces downloaded source data; discovery changes below intentionally retain previews. */
        fun replaceCatalog(
                newCatalogue: List<OnlineThemeSummary>,
                localProfiles: List<WatchThemeProfile>
        ) {
            catalogue = newCatalogue
            previews.clear()
            screenshots.clear()
            loadingPreviews.clear()
            failedPreviews.clear()
            opening.clear()
            updateInstalledState(localProfiles)
        }

        /** Replaces only the displayed subset after a local search/filter/sort change. */
        fun submit(newThemes: List<OnlineThemeSummary>) {
            val previous = themes
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = previous.size

                override fun getNewListSize(): Int = newThemes.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                        previous[oldItemPosition].key() == newThemes[newItemPosition].key()

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                        previous[oldItemPosition] == newThemes[newItemPosition]
            })
            themes = newThemes
            diff.dispatchUpdatesTo(this)
        }

        fun updateInstalled(localProfiles: List<WatchThemeProfile>) {
            updateInstalledState(localProfiles)
            if (themes.isNotEmpty()) notifyItemRangeChanged(0, themes.size)
        }

        fun updateLikeDeltas(deltas: Map<String, Int>) {
            if (knownLikeDeltas == deltas) return
            val changedIds = (knownLikeDeltas.keys + deltas.keys).filter {
                knownLikeDeltas[it] != deltas[it]
            }.toSet()
            knownLikeDeltas = deltas.toMap()
            themes.forEachIndexed { index, summary ->
                if (summary.id in changedIds) notifyItemChanged(index)
            }
        }

        fun updateKnownLikes(themeIds: Set<String>) {
            if (knownLikedIds == themeIds) return
            val changedIds = (knownLikedIds - themeIds) + (themeIds - knownLikedIds)
            knownLikedIds = themeIds.toSet()
            themes.forEachIndexed { index, summary ->
                if (summary.id in changedIds) notifyItemChanged(index)
            }
        }

        /** Cards resolve their accent at bind time; redraw them when this Activity resumes. */
        fun refreshAccent() {
            if (themes.isNotEmpty()) notifyItemRangeChanged(0, themes.size)
        }

        private fun updateInstalledState(localProfiles: List<WatchThemeProfile>) {
            installed.clear()
            updates.clear()
            localProfiles.mapNotNull { it.publishedTheme }.forEach { source ->
                installed += source.id
                catalogue.firstOrNull { it.id == source.id }?.let { summary ->
                    if (summary.revision > source.revision) updates += summary.id
                }
            }
        }

        fun setPreviewLoading(summary: OnlineThemeSummary, isLoading: Boolean) {
            val key = summary.key()
            if (isLoading) loadingPreviews += key else loadingPreviews -= key
            notifyThemeChanged(key)
        }

        fun hasScreenshot(key: OnlineThemeKey): Boolean = key in screenshots

        fun setScreenshot(summary: OnlineThemeSummary, bitmap: Bitmap) {
            val key = summary.key()
            screenshots[key] = bitmap
            notifyThemeChanged(key)
        }

        fun setPreview(summary: OnlineThemeSummary, profile: WatchThemeProfile) {
            val key = summary.key()
            previews[key] = profile
            failedPreviews -= key
            notifyThemeChanged(key)
        }

        fun setPreviewFailed(summary: OnlineThemeSummary) {
            val key = summary.key()
            failedPreviews += key
            notifyThemeChanged(key)
        }

        fun setOpening(summary: OnlineThemeSummary, isOpening: Boolean) {
            val key = summary.key()
            if (isOpening) opening += key else opening -= key
            notifyThemeChanged(key)
        }

        private fun notifyThemeChanged(key: OnlineThemeKey) {
            themes.indexOfFirst { it.id == key.id && it.revision == key.revision }
                    .takeIf { it >= 0 }
                    ?.let(::notifyItemChanged)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeHolder = ThemeHolder(
                LayoutInflater.from(parent.context).inflate(
                        R.layout.item_community_theme_card, parent, false))

        override fun getItemCount(): Int = themes.size

        override fun onBindViewHolder(holder: ThemeHolder, position: Int) {
            holder.bind(themes[position])
        }

        private inner class ThemeHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val card: MaterialCardView = view.findViewById(R.id.community_theme_card)
            private val artwork: View = view.findViewById(R.id.community_theme_artwork)
            private val preview: WatchPreviewView = view.findViewById(R.id.community_theme_preview)
            private val screenshot: ShapeableImageView =
                    view.findViewById(R.id.community_theme_screenshot)
            private val previewPlaceholder: ImageView =
                    view.findViewById(R.id.community_theme_preview_placeholder)
            private val previewLoading: MusicLoadingBarsView =
                    view.findViewById(R.id.community_theme_preview_loading)
            private val name: TextView = view.findViewById(R.id.community_theme_name)
            private val byline: TextView = view.findViewById(R.id.community_theme_byline)
            private val likes: TextView = view.findViewById(R.id.community_theme_likes)
            private val downloads: TextView = view.findViewById(R.id.community_theme_downloads)
            private val status: TextView = view.findViewById(R.id.community_theme_status)
            private val installedMarker: ImageView =
                    view.findViewById(R.id.community_theme_installed_marker)
            private var boundKey: OnlineThemeKey? = null

            fun bind(summary: OnlineThemeSummary) {
                val accent = LyraAccent.resolve(this@OnlineThemesActivity)
                val surface = getColor(R.color.lyra_surface)
                val secondary = getColor(R.color.lyra_text_secondary)
                val accentOnSurface = accentTextOnSurface(accent)
                val accentContainer = ColorUtils.blendARGB(surface, accent, 0.16f)
                val previewContainer = ColorUtils.blendARGB(surface, accent, 0.08f)
                val accentOnContainer = LyraAccent.contrastSafe(
                        accent,
                        accentContainer,
                        minimumContrast = 4.5)
                name.text = summary.name
                val faceName = displayNameForCatalogFace(summary.baseFace)
                byline.text = getString(
                        R.string.online_theme_byline,
                        summary.author)
                /*
                 * The byline is a control, not a caption: it narrows the gallery to this author's
                 * other themes. It is a child of a clickable card, so it has to take the touch
                 * itself -- and it announces the destination rather than the label it displays,
                 * since "By Aurora" says nothing about what a tap would do.
                 */
                byline.setTextColor(if (summary.author.isBlank()) secondary else accentOnSurface)
                byline.isClickable = summary.author.isNotBlank()
                byline.isFocusable = summary.author.isNotBlank()
                byline.setOnClickListener {
                    if (summary.author.isNotBlank()) onAuthor(summary.author)
                }
                byline.contentDescription = if (summary.author.isBlank()) {
                    byline.text
                } else {
                    getString(R.string.online_theme_filter_author, summary.author)
                }

                val likedByUser = summary.id in knownLikedIds
                // The count is shown even at zero. Hiding it made the reaction look like a control
                // with no readout at all on a young catalogue, where every theme is at zero until
                // the trusted publisher's next run aggregates the first votes.
                val likeCount = (summary.likes + (knownLikeDeltas[summary.id] ?: 0))
                        .coerceAtLeast(0)
                val likeDescription = resources.getQuantityString(
                        R.plurals.online_theme_likes_count,
                        likeCount,
                        formatCount(likeCount))
                likes.text = formatCount(likeCount)
                val likeColor = if (likedByUser) accentOnSurface else secondary
                likes.background = null
                likes.setMetricIcon(
                        if (likedByUser) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                        likeColor)
                likes.setTextColor(likeColor)
                likes.visibility = View.VISIBLE
                likes.contentDescription = if (likedByUser) {
                    getString(R.string.online_theme_liked_by_you, likeDescription)
                } else {
                    likeDescription
                }

                // No session delta beside this one, unlike the heart. Installing closes the
                // gallery's detail screen and the count is republished by the publisher, so there
                // is no moment where a stale figure sits in front of the person who moved it.
                val downloadCount = summary.installs.coerceAtLeast(0)
                downloads.text = formatCount(downloadCount)
                downloads.setTextColor(secondary)
                downloads.setMetricIcon(R.drawable.ic_download, secondary)
                downloads.contentDescription = resources.getQuantityString(
                        R.plurals.online_theme_downloads_count,
                        downloadCount,
                        formatCount(downloadCount))

                val baseDescription = getString(
                        R.string.online_theme_row_description,
                        summary.name,
                        summary.author,
                        faceName)

                val compatibility = compatibility(summary)
                val canOpenDetails = compatibility != OnlineThemeCompatibility.UNSUPPORTED
                val key = summary.key()
                boundKey = key
                val profile = previews[key]
                if (canOpenDetails && profile != null) {
                    preview.setThemeProfile(profile)
                    preview.visibility = View.VISIBLE
                } else {
                    preview.clearThemeProfile()
                    preview.visibility = View.INVISIBLE
                }
                /*
                 * The photograph covers the miniature rather than replacing it, so the render stays
                 * underneath: turning the setting off reveals it again with no reload, and a theme
                 * whose picture never arrives simply keeps showing what every theme without one
                 * shows. Requested only once the profile is in hand, because the profile is what
                 * declares whether a photograph exists at all.
                 */
                val authorShot = screenshots[key].takeIf { showScreenshots() }
                screenshot.setImageBitmap(authorShot)
                screenshot.visibility = if (authorShot != null) View.VISIBLE else View.GONE
                if (canOpenDetails && profile != null && authorShot == null && showScreenshots()) {
                    itemView.post {
                        if (boundKey == key) onScreenshotRequired(summary)
                    }
                }

                val loading = canOpenDetails && key in loadingPreviews
                val isOpening = key in opening
                val failed = key in failedPreviews
                artwork.background = shapeDrawable(
                        color = previewContainer,
                        cornerRadiusDp = 16f)
                previewPlaceholder.setImageResource(when {
                    compatibility == OnlineThemeCompatibility.UNSUPPORTED -> R.drawable.ic_watch_off
                    failed -> R.drawable.ic_signal_wifi_off
                    else -> R.drawable.ic_watch
                })
                previewPlaceholder.imageTintList = ColorStateList.valueOf(
                        LyraAccent.contrastSafe(
                                accent,
                                previewContainer,
                                minimumContrast = 3.0))
                previewPlaceholder.visibility = if (profile == null) View.VISIBLE else View.GONE
                previewPlaceholder.alpha = if (isOpening) 0.3f else 0.82f
                preview.alpha = if (isOpening) 0.45f else 1f
                previewLoading.visibility = if (loading || isOpening) View.VISIBLE else View.GONE
                previewLoading.setBarsColor(LyraAccent.contrastSafe(
                        accent,
                        previewContainer,
                        minimumContrast = 3.0))
                if (canOpenDetails && profile == null && !loading && key !in failedPreviews) {
                    // Binding runs while RecyclerView may be computing its layout. Loading the
                    // full profile marks this row changed, so schedule it after that pass instead
                    // of notifying the adapter from inside onBind.
                    itemView.post {
                        if (boundKey == key) onPreviewRequired(summary)
                    }
                }

                val showInstalledMarker = !isOpening &&
                        compatibility == OnlineThemeCompatibility.SUPPORTED &&
                        summary.id !in updates &&
                        summary.id in installed
                installedMarker.visibility = if (showInstalledMarker) View.VISIBLE else View.GONE
                installedMarker.imageTintList = ColorStateList.valueOf(accentOnContainer)
                installedMarker.background = shapeDrawable(
                        color = accentContainer,
                        cornerRadiusDp = 10f)

                val statusUsesAccent = when {
                    isOpening -> {
                        status.setText(R.string.online_theme_opening_details)
                        status.visibility = View.VISIBLE
                        true
                    }
                    compatibility == OnlineThemeCompatibility.UNSUPPORTED -> {
                        status.setText(R.string.online_theme_unsupported)
                        status.visibility = View.VISIBLE
                        false
                    }
                    compatibility == OnlineThemeCompatibility.REQUIRES_NEWER_APP -> {
                        status.text = getString(
                                R.string.online_theme_requires_app_version,
                                summary.minimumAppVersion)
                        status.visibility = View.VISIBLE
                        true
                    }
                    summary.id in updates -> {
                        status.setText(R.string.online_theme_update_available)
                        status.visibility = View.VISIBLE
                        true
                    }
                    else -> {
                        status.text = ""
                        status.visibility = View.GONE
                        false
                    }
                }
                if (status.isVisible) {
                    val statusContainer = if (statusUsesAccent) accentContainer else surface
                    status.setTextColor(if (statusUsesAccent) accentOnContainer else secondary)
                    status.background = shapeDrawable(
                            color = statusContainer,
                            cornerRadiusDp = 10f)
                } else {
                    status.background = null
                }
                val statusDescription = when {
                    status.isVisible -> status.text
                    installedMarker.isVisible -> installedMarker.contentDescription
                    else -> null
                }
                card.contentDescription = buildString {
                    append(baseDescription)
                    append(' ')
                    if (likedByUser) {
                        append(getString(R.string.online_theme_liked_by_you, likeDescription))
                    } else {
                        append(likeDescription)
                    }
                    append(' ')
                    append(downloads.contentDescription)
                    if (!statusDescription.isNullOrBlank()) {
                        append(' ')
                        append(statusDescription)
                    }
                }
                card.strokeColor = getColor(R.color.lyra_divider)
                card.isEnabled = canOpenDetails && !isOpening
                card.isClickable = canOpenDetails && !isOpening
                card.isFocusable = canOpenDetails && !isOpening
                card.setOnClickListener {
                    if (canOpenDetails && !isOpening) onDetails(summary)
                }
            }
        }
    }

    private companion object {
        const val STATE_QUERY = "online_themes.query"
        const val STATE_BASE_FACE = "online_themes.base_face"
        const val STATE_SORT = "online_themes.sort"
        const val STATE_LIKED_ONLY = "online_themes.liked_only"
        const val STATE_AUTHOR = "online_themes.author"
        const val STATE_HIDE_INSTALLED = "online_themes.hide_installed"
        const val STATE_LIKED_IDS = "online_themes.liked_ids"
        const val STATE_LIKE_DELTAS = "online_themes.like_deltas"
        const val STATE_SUBMISSION_QUEUE = "online_themes.submission_queue"
        const val STATE_SUBMISSION_RUN_SIZE = "online_themes.submission_run_size"
        const val SEARCH_DEBOUNCE_MS = 120L
    }
}
