package com.svartifoss.snfell.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.music.StreamingShortcutLinks
import com.svartifoss.snfell.notifications.AppGlyphStore
import javax.inject.Inject

/**
 * Opens one specific saved playlist shortcut (see
 * [com.svartifoss.snfell.music.PlaylistShortcutStorage]) directly - unlike
 * [OpenPlaylistShortcutsAction], which shows the whole list on the watch for the user to pick
 * from. Because this is a regular parameterized [PhoneAction] (the chosen playlist's name and
 * link are baked into the action bundle, Tasker-task style), it can be assigned to anything:
 * a quadrant, a swipe, a stem button, an on-screen mini button, or the actions menu.
 *
 * Created by [PlaylistShortcutPickerAction]; never appears in the picker list itself.
 */
class PlayPlaylistShortcutAction : SelectableAction {
    companion object {
        const val KEY_PLAYLIST_NAME = "PLAYLIST_NAME"
        const val KEY_PLAYLIST_LINK = "PLAYLIST_LINK"
    }

    val playlistName: String
    val link: String

    constructor(context: Context, playlistName: String, link: String) : super(context) {
        this.playlistName = playlistName
        this.link = link
    }

    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle) {
        this.playlistName = bundle.getString(KEY_PLAYLIST_NAME)!!
        this.link = bundle.getString(KEY_PLAYLIST_LINK)!!
    }

    override fun writeToBundle(bundle: PersistableBundle) {
        super.writeToBundle(bundle)

        bundle.putString(KEY_PLAYLIST_NAME, playlistName)
        bundle.putString(KEY_PLAYLIST_LINK, link)
    }

    override fun retrieveTitle(): String = playlistName

    /**
     * A saved shortcut represents a concrete destination, so its icon should identify that
     * destination in Pick action (and on the watch after it is assigned), rather than making
     * every playlist/track look like the same generic playlist command.
     *
     * Supported services use their notification glyph where this phone has learned one and their
     * launcher icon otherwise (see [AppGlyphStore]). For a custom scheme/provider, use Android's
     * default handler when one exists. The monochrome playlist glyph remains the safe fallback for
     * an app that is not installed or a link without a default handler.
     */
    private val destinationAppIcon: AppMark? by lazy {
        val service = StreamingShortcutLinks.detect(link)
        val knownPackage = service.packageName
        if (knownPackage != null) {
            applicationIcon(knownPackage)
        } else {
            resolveCustomLinkHandlerIcon()
        }
    }

    /**
     * An app's mark and whether it may be tinted, carried together.
     *
     * They were two independent conditions before, which was survivable while the answer was
     * always "a launcher icon, never tint it". It stops being survivable the moment the same slot
     * can hold a flat-white notification template: tint decided separately from image is how a
     * glyph ends up drawn white-on-white.
     */
    private data class AppMark(val drawable: Drawable, val tintable: Boolean)

    /** Online thumbnail fetched for this shortcut (opt-in), if one was cached. Full-colour art,
     *  so it must never be tinted. */
    private val cachedThumbnail: Drawable? by lazy {
        com.svartifoss.snfell.music.ShortcutArtworkStore.get(context, link)?.let { png ->
            android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size)?.let { bitmap ->
                android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
            }
        }
    }

    override val defaultIconTintable: Boolean
        get() = cachedThumbnail == null && (destinationAppIcon?.tintable ?: true)

    /** Only the fetched online thumbnail is genuine cover art - the destination app's launcher
     * icon is still a real, non-tintable image, but stretching it across a whole pill just looks
     * like a smeared logo, not a cover. */
    override val defaultIsCoverArt: Boolean
        get() = cachedThumbnail != null

    override val defaultIcon: Drawable
        get() = cachedThumbnail ?: destinationAppIcon?.drawable ?: AppCompatResources.getDrawable(
                context,
                com.svartifoss.snfell.common.R.drawable.action_open_playlist
        )!!

    override val remoteUri: String
        get() {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val openMode = prefs.getString(StreamingShortcutLinks.OPEN_MODE_KEY, null)
                    ?: if (prefs.getBoolean(
                                    StreamingShortcutLinks.PREFER_INSTALLED_APP_KEY,
                                    true)) {
                        StreamingShortcutLinks.OPEN_MODE_APP
                    } else {
                        StreamingShortcutLinks.OPEN_MODE_DEFAULT
                    }

            val service = StreamingShortcutLinks.detect(link)
            val targetPackage = service.packageName?.takeIf { packageName ->
                openMode == StreamingShortcutLinks.OPEN_MODE_APP && isPackageInstalled(packageName)
            }

            val primaryLink = if (targetPackage != null) {
                StreamingShortcutLinks.forInstalledApp(link)
            } else {
                StreamingShortcutLinks.forBrowser(link)
            }

            return if (targetPackage != null) {
                "$targetPackage|$primaryLink"
            } else {
                primaryLink
            }
        }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * The target app's mark: its notification glyph where this phone has learned one, its launcher
     * icon otherwise - see [AppGlyphStore].
     *
     * Only ever reached when the shortcut has no fetched cover of its own. A real thumbnail stays
     * a real thumbnail; this is the fallback that used to be the one place a full-colour launcher
     * icon appeared beside monochrome rows.
     */
    private fun applicationIcon(packageName: String): AppMark? {
        AppGlyphStore.drawable(context, packageName)?.let { return AppMark(it, tintable = true) }
        return try {
            AppMark(context.packageManager.getApplicationIcon(packageName), tintable = false)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun resolveCustomLinkHandlerIcon(): AppMark? {
        val canonicalLink = StreamingShortcutLinks.canonicalize(link)
        if (!StreamingShortcutLinks.isSafeLink(canonicalLink)) return null

        val resolvedPackage = try {
            context.packageManager.resolveActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(canonicalLink)),
                    PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        } catch (_: RuntimeException) {
            null
        }
        return resolvedPackage?.let(::applicationIcon)
    }

    override fun isEqualToAction(other: PhoneAction): Boolean {
        other as PlayPlaylistShortcutAction
        return super.isEqualToAction(other) &&
                this.playlistName == other.playlistName &&
                this.link == other.link
    }

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<PlayPlaylistShortcutAction> {
        override suspend fun handleAction(action: PlayPlaylistShortcutAction) {
            // The saved name doubles as a playFromSearch query - the fallback that plays an artist
            // page (URI-only playback just navigates) and that Spotify honors from outside.
            service.playDeepLink(action.link, action.playlistName)
        }
    }
}
