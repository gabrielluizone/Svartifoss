package com.svartifoss.snfell.watch.tile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.preference.PreferenceManager
import com.matejdro.wearutils.preferences.definition.Preferences
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.proto.ShortcutPlayMode
import com.svartifoss.snfell.watch.communication.ShortcutPlayRequest
import com.svartifoss.snfell.watch.view.shortcut.ShortcutDetailActivity
import com.svartifoss.snfell.watch.view.shortcut.ShortcutDetailUi
import com.svartifoss.snfell.watch.view.shortcut.ShortcutScreenPolicy

/**
 * Invisible bridge activity launched by a [ShortcutsTileService] chip. A Tile click can only launch
 * an Activity (not send Data Layer messages), so this is where a chip's tap becomes something.
 *
 * Usually that is the shortcut's own screen - cover, name, Play beside Shuffle - the same screen
 * a pick from the shortcut list opens, since the Tile lists the same shortcuts; it then starts the
 * pick itself, there being no player behind it to hand the choice to. With that screen switched
 * off, or a phone that predates play modes, the chip starts the shortcut at once, as it always did,
 * through [ShortcutPlayRequest]: register the URI with the phone opener first, then ask the phone
 * to play it.
 *
 * It shows no UI and finishes straight away; anything left to do outlives it on a process scope.
 */
class ShortcutLaunchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entryId = intent?.getStringExtra(EXTRA_ENTRY_ID)?.takeIf { it.isNotBlank() }
        if (entryId == null) {
            finish()
            return
        }

        val phoneKnowsPlayModes = intent.hasExtra(EXTRA_SHUFFLEABLE)
        val opensScreen = ShortcutScreenPolicy.opensForListEntry(
                enabled = Preferences.getBoolean(
                        PreferenceManager.getDefaultSharedPreferences(this),
                        MiscPreferences.WEAR_SHORTCUT_DETAILS),
                listId = CustomLists.PLAYLIST_SHORTCUTS,
                entryId = entryId,
                phoneKnowsPlayModes = phoneKnowsPlayModes,
                shuffleable = intent.getBooleanExtra(EXTRA_SHUFFLEABLE, false))

        if (opensScreen) {
            startActivity(ShortcutDetailActivity.intentFor(
                    this,
                    ShortcutDetailUi(
                            entryId = entryId,
                            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                            subtitle = intent.getStringExtra(EXTRA_SUBTITLE)
                                    ?.takeIf { it.isNotBlank() },
                            shuffleable = intent.getBooleanExtra(EXTRA_SHUFFLEABLE, false)),
                    execute = true))
        } else {
            ShortcutPlayRequest.start(this, entryId, ShortcutPlayMode.AS_SAVED)
        }
        finish()
    }

    companion object {
        const val EXTRA_ENTRY_ID = "com.svartifoss.snfell.watch.tile.EXTRA_ENTRY_ID"
        const val EXTRA_TITLE = "com.svartifoss.snfell.watch.tile.EXTRA_TITLE"
        const val EXTRA_SUBTITLE = "com.svartifoss.snfell.watch.tile.EXTRA_SUBTITLE"
        /** Present only when the phone said whether the shortcut can be shuffled - which is
         *  also how the bridge tells a phone that understands play modes from one that does not. */
        const val EXTRA_SHUFFLEABLE = "com.svartifoss.snfell.watch.tile.EXTRA_SHUFFLEABLE"
    }
}
