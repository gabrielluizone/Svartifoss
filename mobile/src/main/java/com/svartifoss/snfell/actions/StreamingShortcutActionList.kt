package com.svartifoss.snfell.actions

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.PlaylistShortcutStorage

/**
 * The streaming category of Pick action, backed by the same library as Streaming shortcuts. Saved
 * tracks, albums, mixes and playlists are exposed directly as assignable actions; no second list or
 * preference is created for the Actions tab. The built-in account-library shortcuts live here
 * too, so the picker has one streaming section instead of a growing row per service.
 *
 * Making a *new* shortcut is not an action in this list: it is the "Add a link" row the picker draws
 * at the top of the section (see [com.svartifoss.snfell.view.buttonconfig.ActionPickerCatalogue]),
 * which opens the link sheet in place and returns the new shortcut as the choice. It used to be a
 * "Choose or add" entry that opened the whole Streaming shortcuts screen as a second window.
 */
class StreamingShortcutActionList : PickerActionGroup {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun pickerChildren(): List<PhoneAction> {
        val shortcuts = PlaylistShortcutStorage.load(context)
        val actions = ArrayList<PhoneAction>(shortcuts.size + BUILT_IN_SHORTCUT_COUNT + 1)

        // These are fixed account-library links rather than user-saved links, but they are
        // still streaming shortcuts. Keeping them inside this category prevents every newly
        // supported service (and YouTube Music's shuffled variant) from adding another root row.
        actions.addAll(listOf(
            PlayLikedSongsAction(context),
            PlayLikedSongsShuffledAction(context),
            PlaySpotifyLikedSongsAction(context),
            PlayDeezerFlowAction(context),
            PlaySoundCloudLikesAction(context)
        ))

        actions.addAll(shortcuts.map { shortcut ->
            PlayPlaylistShortcutAction(context, shortcut.name, shortcut.link)
        })

        if (shortcuts.isNotEmpty()) {
            // This assigns the action that opens the complete saved library on the watch, useful
            // when one Quick Action should provide access to more than a single link.
            actions.add(OpenPlaylistShortcutsAction(context).apply {
                customTitle = context.getString(R.string.action_show_streaming_library)
            })
        }

        return actions
    }

    override fun retrieveTitle(): String =
            context.getString(R.string.action_my_playlists)

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context,
                com.svartifoss.snfell.common.R.drawable.action_open_playlist
        )!!

    private companion object {
        const val BUILT_IN_SHORTCUT_COUNT = 5
    }
}
