package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.music.PlaylistShortcutStorage
import com.svartifoss.snfell.music.ShortcutAppMark
import com.svartifoss.snfell.music.ShortcutCovers
import com.svartifoss.snfell.music.StreamingCollection
import com.svartifoss.snfell.music.StreamingShortcutDescription
import com.svartifoss.snfell.music.StreamingShortcutLinks
import com.svartifoss.snfell.music.StreamingShortcutRoutes
import javax.inject.Inject

/**
 * Opens one specific saved playlist shortcut (see
 * [com.svartifoss.snfell.music.PlaylistShortcutStorage]) directly - unlike
 * [OpenPlaylistShortcutsAction], which shows the whole list on the watch for the user to pick
 * from. Because this is a regular parameterized [PhoneAction] (the chosen playlist's name and
 * link are baked into the action bundle, Tasker-task style), it can be assigned to anything:
 * a quadrant, a swipe, a stem button, an on-screen mini button, or the actions menu.
 *
 * Built by [StreamingShortcutActionList] from the saved library, or by the picker's "Add a link"
 * sheet from a link just pasted.
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
     * launcher icon otherwise (see [ShortcutAppMark]). For a custom scheme/provider, use Android's
     * default handler when one exists. The monochrome playlist glyph remains the safe fallback for
     * an app that is not installed or a link without a default handler.
     */
    private val destinationAppIcon: ShortcutAppMark? by lazy {
        ShortcutAppMark.forLink(context, link)
    }

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

    override val streamingShortcut: StreamingShortcutDescription
        get() = PlaylistShortcutStorage.describeForWatch(context, playlistName, link)

    /** The fetched cover, else the collection's or the service's drawn one - see [ShortcutCovers]. */
    override val listCover: Drawable?
        get() = ShortcutCovers.forLink(context, link)
                ?.let { android.graphics.drawable.BitmapDrawable(context.resources, it) }

    override val streamingCollection: StreamingCollection?
        get() = StreamingCollection.forLink(link)

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
            val targetPackage = StreamingShortcutRoutes.targetPackage(
                    context, prefs, service, openMode, link)
            val primaryLink = StreamingShortcutRoutes.linkForTarget(link, service, targetPackage)

            return if (targetPackage != null) {
                "$targetPackage|$primaryLink"
            } else {
                primaryLink
            }
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
