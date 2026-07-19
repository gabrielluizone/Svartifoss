package com.svartifoss.snfell.common.actions

import android.view.KeyEvent
import com.svartifoss.snfell.common.R
import com.svartifoss.snfell.common.buttonconfig.SpecialButtonCodes

object StandardIcons {
    private val iconMap = mapOf(
            StandardActions.ACTION_PLAY to com.svartifoss.snfell.common.R.drawable.action_play,
            StandardActions.ACTION_PAUSE to com.svartifoss.snfell.common.R.drawable.action_pause,
            StandardActions.ACTION_SKIP_TO_PREV to com.svartifoss.snfell.common.R.drawable.action_skip_prev,
            StandardActions.ACTION_SKIP_TO_NEXT to com.svartifoss.snfell.common.R.drawable.action_skip_next,
            StandardActions.ACTION_VOLUME_UP to com.svartifoss.snfell.common.R.drawable.action_volume_up,
            StandardActions.ACTION_VOLUME_DOWN to com.svartifoss.snfell.common.R.drawable.action_volume_down,
            StandardActions.ACTION_OPEN_MENU to com.svartifoss.snfell.common.R.drawable.action_open_menu,
            StandardActions.ACTION_SKIP_30_SECONDS to com.svartifoss.snfell.common.R.drawable.action_skip_30_seconds,
            StandardActions.ACTION_REVERSE_30_SECONDS to com.svartifoss.snfell.common.R.drawable.action_reverse_30_seconds,
            StandardActions.ACTION_PLAY_PAUSE to com.svartifoss.snfell.common.R.drawable.action_play_pause,
            StandardActions.ACTION_STOP to com.svartifoss.snfell.common.R.drawable.action_stop,
            StandardActions.ACTION_RESTART to com.svartifoss.snfell.common.R.drawable.action_restart,
            StandardActions.ACTION_MUTE to com.svartifoss.snfell.common.R.drawable.action_volume_off,
            StandardActions.ACTION_SEARCH to com.svartifoss.snfell.common.R.drawable.action_search,
            StandardActions.ACTION_LIKE to com.svartifoss.snfell.common.R.drawable.action_like,
            StandardActions.ACTION_SHUFFLE to com.svartifoss.snfell.common.R.drawable.action_shuffle,
            StandardActions.ACTION_REPEAT to com.svartifoss.snfell.common.R.drawable.action_repeat,
            StandardActions.ACTION_REPEAT_ONE to com.svartifoss.snfell.common.R.drawable.action_repeat_one,
            StandardActions.ACTION_OPEN_PLAYLIST_MENU to
                    com.svartifoss.snfell.common.R.drawable.action_open_playlist,

            getButtonKey(KeyEvent.KEYCODE_BACK) to R.drawable.button_back,
            getButtonKey(SpecialButtonCodes.TURN_ROTARY_CW) to R.drawable.button_turn_cw,
            getButtonKey(SpecialButtonCodes.TURN_ROTARY_CCW) to R.drawable.button_turn_ccw
    )

    fun hasIcon(key: String): Boolean = iconMap.containsKey(key)
    fun getIcon(key: String): Int = iconMap[key] ?: 0

    fun hasIcon(buttonId: Int): Boolean = iconMap.containsKey(getButtonKey(buttonId))
    fun getIcon(buttonId: Int): Int = iconMap[getButtonKey(buttonId)] ?: 0

    private fun getButtonKey(id: Int): String {
        return "$BUTTON_PREFIX$id"
    }
}

private const val BUTTON_PREFIX = "BUTTON_"
