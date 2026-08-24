package com.svartifoss.snfell.view.watchface.theme

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.svartifoss.snfell.R
import com.svartifoss.snfell.BuildConfig
import com.svartifoss.snfell.common.ArchivedFaces
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.update.UpdateChecker
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.watchface.WatchPreviewView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class OnlineThemeKey(
        val id: String,
        val revision: Int
)

private fun OnlineThemeSummary.key(): OnlineThemeKey = OnlineThemeKey(id, revision)

/**
 * Opt-in, read-only Phase 1 community gallery. The only network request happens after a user
 * opens this screen; downloaded profiles stay public cache data until the explicit Add and apply
 * action installs one into the normal phone-local theme library.
 */
class OnlineThemesActivity : AppCompatActivity() {

    private lateinit var defaultPrefs: SharedPreferences
    private lateinit var themeRepository: WatchThemeRepository
    private lateinit var catalogRepository: OnlineThemesRepository
    private lateinit var adapter: OnlineThemeAdapter
    private lateinit var state: View
    private lateinit var stateProgress: ProgressBar
    private lateinit var stateMessage: TextView
    private lateinit var retryButton: MaterialButton

    private var catalogJob: Job? = null
    /** Increments for every request so a cancelled, blocking HTTP request cannot win the race. */
    private var catalogRequestGeneration = 0L
    /** Increments only after a new catalogue is accepted; tags its preview/apply work. */
    private var galleryGeneration = 0L
    private val previewJobs = mutableMapOf<OnlineThemeKey, Job>()
    private val applyJobs = mutableMapOf<OnlineThemeKey, Job>()
    private val parsedProfiles = mutableMapOf<OnlineThemeKey, WatchThemeProfile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_themes)

        defaultPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        themeRepository = WatchThemeRepository(this)
        catalogRepository = OnlineThemesRepository(this)

        state = findViewById(R.id.community_theme_state)
        stateProgress = findViewById(R.id.community_theme_progress)
        stateMessage = findViewById(R.id.community_theme_state_message)
        retryButton = findViewById(R.id.button_retry_community_themes)

        adapter = OnlineThemeAdapter(
                onPreviewRequired = ::loadPreview,
                onApply = ::installAndApply)
        findViewById<RecyclerView>(R.id.community_theme_list).apply {
            layoutManager = LinearLayoutManager(this@OnlineThemesActivity)
            adapter = this@OnlineThemesActivity.adapter
            itemAnimator = null
        }

        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.button_refresh).setOnClickListener { loadCatalog(true) }
        retryButton.setOnClickListener { loadCatalog(true) }
        loadCatalog(forceRefresh = false)
    }

    override fun onDestroy() {
        catalogJob?.cancel()
        previewJobs.values.forEach { it.cancel() }
        applyJobs.values.forEach { it.cancel() }
        super.onDestroy()
    }

    private fun loadCatalog(forceRefresh: Boolean) {
        val requestGeneration = ++catalogRequestGeneration
        catalogJob?.cancel()
        if (adapter.itemCount == 0) showLoadingState()
        catalogJob = lifecycleScope.launch {
            try {
                val themes = catalogRepository.loadCatalog(forceRefresh)
                if (requestGeneration != catalogRequestGeneration) return@launch
                // A catalogue refresh can advance a theme without changing its UUID. Drop all
                // in-memory card work before binding the new revision, so neither preview nor
                // install can use a profile parsed for the preceding one.
                discardLoadedProfiles()
                adapter.submit(themes, themeRepository.load())
                when {
                    themes.isEmpty() -> showMessageState(R.string.online_theme_empty, retry = false)
                    else -> state.visibility = View.GONE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (adapter.itemCount == 0) {
                    showMessageState(R.string.online_theme_load_error, retry = true)
                } else {
                    Toast.makeText(
                            this@OnlineThemesActivity,
                            R.string.online_theme_load_error,
                            Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Loads only a card's full profile, because the index is deliberately compact. */
    private fun loadPreview(summary: OnlineThemeSummary) {
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

    private fun installAndApply(summary: OnlineThemeSummary) {
        if (!supports(summary)) return
        val key = summary.key()
        if (key in applyJobs) return
        val generation = galleryGeneration
        applyJobs[key] = lifecycleScope.launch {
            adapter.setApplying(summary, true)
            try {
                val candidate = fetchProfile(summary, generation)
                if (generation != galleryGeneration) return@launch
                if (candidate == null) {
                    Toast.makeText(
                            this@OnlineThemesActivity,
                            R.string.online_theme_preview_error,
                            Toast.LENGTH_LONG).show()
                    return@launch
                }

                when (val result = themeRepository.installAndApplyPublishedProfile(defaultPrefs, candidate)) {
                    is PublishedThemeInstallResult.Applied -> {
                        adapter.markInstalled(summary.id, result.updateAvailable)
                        setResult(RESULT_OK)
                        Toast.makeText(
                                this@OnlineThemesActivity,
                                getString(R.string.online_theme_apply_success, result.profile.name),
                                Toast.LENGTH_SHORT).show()
                    }
                    PublishedThemeInstallResult.LibraryFull -> {
                        Toast.makeText(
                                this@OnlineThemesActivity,
                                R.string.watch_theme_limit_reached,
                                Toast.LENGTH_LONG).show()
                    }
                    PublishedThemeInstallResult.WatchSyncTooLarge -> {
                        Toast.makeText(
                                this@OnlineThemesActivity,
                                R.string.online_theme_sync_limit,
                                Toast.LENGTH_LONG).show()
                    }
                    PublishedThemeInstallResult.InvalidProfile,
                    PublishedThemeInstallResult.ApplyFailed -> {
                        Toast.makeText(
                                this@OnlineThemesActivity,
                                R.string.watch_theme_apply_error,
                                Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                if (generation == galleryGeneration) {
                    applyJobs.remove(key)
                    adapter.setApplying(summary, false)
                }
            }
        }
    }

    /** Clears values tied to a previous catalogue response, including unfinished old revisions. */
    private fun discardLoadedProfiles() {
        galleryGeneration++
        previewJobs.values.forEach { it.cancel() }
        previewJobs.clear()
        applyJobs.values.forEach { it.cancel() }
        applyJobs.clear()
        parsedProfiles.clear()
    }

    private fun showLoadingState() {
        state.visibility = View.VISIBLE
        stateProgress.visibility = View.VISIBLE
        stateMessage.setText(R.string.online_theme_loading)
        retryButton.visibility = View.GONE
    }

    private fun showMessageState(message: Int, retry: Boolean) {
        state.visibility = View.VISIBLE
        stateProgress.visibility = View.GONE
        stateMessage.setText(message)
        retryButton.visibility = if (retry) View.VISIBLE else View.GONE
    }

    /**
     * An index can outlive an installed app by years. Keep incompatible profiles visible with an
     * honest requirement instead of letting the private profile parser make them disappear after
     * a tap. Archived base faces are never gallery-installable, even on builds that can still
     * render them for users who already own one.
     */
    private fun supports(summary: OnlineThemeSummary): Boolean =
            summary.schemaVersion == WatchThemeRepository.LIBRARY_SCHEMA &&
                    summary.baseFace in ThemeAppearance.ALLOWED_BASE_FACES &&
                    summary.baseFace !in ArchivedFaces.KEYS &&
                    !UpdateChecker.isNewer(summary.minimumAppVersion, BuildConfig.VERSION_NAME)

    private inner class OnlineThemeAdapter(
            private val onPreviewRequired: (OnlineThemeSummary) -> Unit,
            private val onApply: (OnlineThemeSummary) -> Unit
    ) : RecyclerView.Adapter<OnlineThemeAdapter.ThemeHolder>() {

        private var themes: List<OnlineThemeSummary> = emptyList()
        private val previews = mutableMapOf<OnlineThemeKey, WatchThemeProfile>()
        private val loadingPreviews = mutableSetOf<OnlineThemeKey>()
        private val failedPreviews = mutableSetOf<OnlineThemeKey>()
        private val applying = mutableSetOf<OnlineThemeKey>()
        private val installed = mutableSetOf<String>()
        private val updates = mutableSetOf<String>()

        fun submit(
                newThemes: List<OnlineThemeSummary>,
                localProfiles: List<WatchThemeProfile>
        ) {
            themes = newThemes
            previews.clear()
            loadingPreviews.clear()
            failedPreviews.clear()
            applying.clear()
            installed.clear()
            updates.clear()
            localProfiles.mapNotNull { it.publishedTheme }.forEach { source ->
                installed += source.id
                newThemes.firstOrNull { it.id == source.id }?.let { summary ->
                    if (summary.revision > source.revision) updates += summary.id
                }
            }
            notifyDataSetChanged()
        }

        fun setPreviewLoading(summary: OnlineThemeSummary, isLoading: Boolean) {
            val key = summary.key()
            if (isLoading) loadingPreviews += key else loadingPreviews -= key
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

        fun setApplying(summary: OnlineThemeSummary, isApplying: Boolean) {
            val key = summary.key()
            if (isApplying) applying += key else applying -= key
            notifyThemeChanged(key)
        }

        fun markInstalled(id: String, updateAvailable: Boolean) {
            installed += id
            if (updateAvailable) updates += id else updates -= id
            notifyThemeChanged(id)
        }

        private fun notifyThemeChanged(key: OnlineThemeKey) {
            themes.indexOfFirst { it.id == key.id && it.revision == key.revision }
                    .takeIf { it >= 0 }
                    ?.let(::notifyItemChanged)
        }

        private fun notifyThemeChanged(id: String) {
            themes.indexOfFirst { it.id == id }
                    .takeIf { it >= 0 }
                    ?.let(::notifyItemChanged)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeHolder = ThemeHolder(
                LayoutInflater.from(parent.context).inflate(
                        R.layout.item_online_watch_theme, parent, false))

        override fun getItemCount(): Int = themes.size

        override fun onBindViewHolder(holder: ThemeHolder, position: Int) {
            holder.bind(themes[position])
        }

        private inner class ThemeHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val card: MaterialCardView = view.findViewById(R.id.online_theme_card)
            private val preview: WatchPreviewView = view.findViewById(R.id.online_theme_preview)
            private val previewLoading: ProgressBar =
                    view.findViewById(R.id.online_theme_preview_loading)
            private val name: TextView = view.findViewById(R.id.online_theme_name)
            private val byline: TextView = view.findViewById(R.id.online_theme_byline)
            private val status: TextView = view.findViewById(R.id.online_theme_status)
            private val apply: MaterialButton = view.findViewById(R.id.button_apply_online_theme)

            fun bind(summary: OnlineThemeSummary) {
                name.text = summary.name
                val faceName = WatchThemeRepository.displayNameForFace(
                        this@OnlineThemesActivity, summary.baseFace)
                byline.text = getString(R.string.online_theme_byline, summary.author, faceName)
                card.contentDescription = getString(
                        R.string.online_theme_row_description, summary.name, summary.author, faceName)

                val supported = supports(summary)
                val key = summary.key()
                val profile = previews[key]
                if (supported && profile != null) {
                    preview.setThemeProfile(profile)
                    preview.visibility = View.VISIBLE
                } else {
                    preview.clearThemeProfile()
                    preview.visibility = View.INVISIBLE
                }
                val loading = supported && key in loadingPreviews
                previewLoading.visibility = if (loading) View.VISIBLE else View.GONE
                if (supported && profile == null && !loading && key !in failedPreviews) {
                    // Binding runs while RecyclerView may be computing its layout. Loading the
                    // full profile marks this row changed, so schedule it for immediately after
                    // that pass instead of notifying the adapter from inside onBind.
                    itemView.post { onPreviewRequired(summary) }
                }

                when {
                    !supported -> {
                        status.text = getString(
                                R.string.online_theme_requires_app_version,
                                summary.minimumAppVersion)
                        status.visibility = View.VISIBLE
                    }
                    summary.id in updates -> {
                        status.setText(R.string.online_theme_update_available)
                        status.visibility = View.VISIBLE
                    }
                    summary.id in installed -> {
                        status.setText(R.string.online_theme_installed)
                        status.visibility = View.VISIBLE
                    }
                    else -> status.visibility = View.GONE
                }
                status.setTextColor(LyraAccent.resolve(this@OnlineThemesActivity))

                val isApplying = key in applying
                apply.isEnabled = supported && !isApplying
                apply.text = getString(R.string.online_theme_apply)
                apply.iconTint = ColorStateList.valueOf(
                        LyraAccent.resolve(this@OnlineThemesActivity))
                apply.setOnClickListener { onApply(summary) }
                card.setOnClickListener { if (supported) onApply(summary) }
            }
        }
    }
}
