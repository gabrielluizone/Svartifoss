package com.svartifoss.snfell.view.watchface.theme

import android.content.Context
import android.content.SharedPreferences
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.ThemeAppearance

/** One row of the quick theme window: a heading, or a theme that can be picked. */
sealed class QuickThemeRow {
    data class Header(val title: String) : QuickThemeRow()

    /**
     * @param key what [QuickThemeSwitch.apply] takes: a bare face key for a built-in theme, or
     *   `custom:<id>` for one of the user's saved ones. The same two forms the watch's own picker
     *   sends the phone (`MESSAGE_SET_SCREEN_FACE`), so a theme has one name wherever it is chosen.
     */
    data class Choice(
            val key: String,
            val title: String,
            val subtitle: String?,
            val active: Boolean
    ) : QuickThemeRow()
}

/** A theme as the window lists it, before it is placed among the rows. */
data class QuickThemeEntry(val key: String, val title: String, val subtitle: String? = null)

/**
 * Lays out the quick theme window: the user's own themes first, then the built-in ones.
 *
 * The same order the Watch themes screen and the watch's own picker use - someone with saved
 * themes is almost always switching between *those*, and behind the whole built-in list they were
 * a scroll away every time. Headings only appear when there is something to tell apart: with no
 * saved themes the window is one plain list of the built-in themes, because a lone heading above
 * the only section names nothing.
 *
 * Pure, so the order and the active marker are pinned by a JVM test instead of by looking at the
 * window.
 */
internal fun buildQuickThemeRows(
        customThemes: List<QuickThemeEntry>,
        builtInThemes: List<QuickThemeEntry>,
        activeKey: String,
        customHeader: String,
        builtInHeader: String
): List<QuickThemeRow> = buildList {
    fun choice(entry: QuickThemeEntry) = QuickThemeRow.Choice(
            key = entry.key,
            title = entry.title,
            subtitle = entry.subtitle,
            active = entry.key == activeKey)

    if (customThemes.isNotEmpty()) {
        add(QuickThemeRow.Header(customHeader))
        customThemes.forEach { add(choice(it)) }
        add(QuickThemeRow.Header(builtInHeader))
    }
    builtInThemes.forEach { add(choice(it)) }
}

/** The key the active appearance is listed under - see [QuickThemeRow.Choice.key]. */
internal fun activeQuickThemeKey(appearance: AppearanceContext): String = when (appearance) {
    is AppearanceContext.Custom -> QuickThemeSwitch.CUSTOM_PREFIX + appearance.themeId
    is AppearanceContext.BuiltIn -> appearance.baseFace
}

/**
 * Lists and applies watch themes without the Watch themes screen, for the window the persistent
 * notification opens. Applies through the same [WatchThemeRepository] calls that screen makes, so
 * a theme chosen here is exactly the theme chosen there; the preference change is then pushed to
 * the watch by `WatchPreferenceSyncCoordinator` like any other.
 */
object QuickThemeSwitch {
    const val CUSTOM_PREFIX = "custom:"

    /** The developer switch that un-hides retired faces, the key the Watch themes screen reads. */
    private const val SHOW_ARCHIVED_PREF = "dev_show_archived"

    fun rows(context: Context, prefs: SharedPreferences): List<QuickThemeRow> {
        val appearance = ThemeAppearance.resolve(prefs)
        val showArchived = prefs.getBoolean(SHOW_ARCHIVED_PREF, false)

        val custom = WatchThemeRepository(context).load().map { profile ->
            QuickThemeEntry(
                    key = CUSTOM_PREFIX + profile.id,
                    title = profile.name,
                    subtitle = context.getString(
                            R.string.watch_theme_active_layout,
                            WatchThemeRepository.displayNameForFace(context, profile.baseFace)))
        }
        val builtIn = ThemeAppearance.ALLOWED_BASE_FACES
                // Retired faces stay out, as on the Watch themes screen - except the one in use,
                // which a list that hid it would leave looking like nothing is applied.
                .filter { face ->
                    showArchived || face !in ArchivedFaces.KEYS || appearance.baseFace == face
                }
                .map { face ->
                    QuickThemeEntry(face, WatchThemeRepository.displayNameForFace(context, face))
                }

        return buildQuickThemeRows(
                customThemes = custom,
                builtInThemes = builtIn,
                activeKey = activeQuickThemeKey(appearance),
                customHeader = context.getString(R.string.watch_theme_custom_section),
                builtInHeader = context.getString(R.string.watch_theme_built_in_section))
    }

    /**
     * Applies the theme named by [key].
     *
     * @return the name to show the user, or null when nothing was applied (an unknown theme, or the
     *   write failed).
     */
    fun apply(context: Context, prefs: SharedPreferences, key: String): String? {
        val repository = WatchThemeRepository(context)
        // Both branches below overwrite or invalidate the shared `custom_active` scope, and an edit
        // made in the Watch editor lives only there until something writes it back into the profile
        // it belongs to. Switching away without capturing first would discard it silently - the
        // same reason the Watch themes screen captures before it applies, and the watch's picker
        // does too (MusicService.applyScreenFaceFromWatch).
        repository.captureActive(prefs)

        if (key.startsWith(CUSTOM_PREFIX)) {
            val profile = repository.profiles.firstOrNull { it.id == key.removePrefix(CUSTOM_PREFIX) }
                    ?: return null
            return profile.name.takeIf { repository.applyProfile(prefs, profile) }
        }

        if (key !in ThemeAppearance.ALLOWED_BASE_FACES) return null
        return WatchThemeRepository.displayNameForFace(context, key)
                .takeIf { repository.applyBuiltIn(prefs, key) }
    }
}
