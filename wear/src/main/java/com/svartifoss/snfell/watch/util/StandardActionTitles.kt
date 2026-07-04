package com.svartifoss.snfell.watch.util

import android.content.Context
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.actions.StandardActions

/**
 * Localized display names for the standard phone-side actions, resolved on the watch. The button
 * config protobuf only carries each action's key (its phone-side class name), never a display
 * title, so this is the only way the watch can name a quadrant's action - used to give the
 * quadrant icons accessibility labels.
 */
object StandardActionTitles {
    private val titleMap = mapOf(
            StandardActions.ACTION_PLAY to R.string.action_name_play,
            StandardActions.ACTION_PAUSE to R.string.action_name_pause,
            StandardActions.ACTION_SKIP_TO_PREV to R.string.action_name_skip_prev,
            StandardActions.ACTION_SKIP_TO_NEXT to R.string.action_name_skip_next,
            StandardActions.ACTION_VOLUME_UP to R.string.action_name_volume_up,
            StandardActions.ACTION_VOLUME_DOWN to R.string.action_name_volume_down,
            StandardActions.ACTION_OPEN_MENU to R.string.action_name_open_menu,
            StandardActions.ACTION_SKIP_30_SECONDS to R.string.action_name_skip_30,
            StandardActions.ACTION_REVERSE_30_SECONDS to R.string.action_name_reverse_30,
            StandardActions.ACTION_PLAY_PAUSE to R.string.action_name_play_pause,
            StandardActions.ACTION_STOP to R.string.action_name_stop,
            StandardActions.ACTION_RESTART to R.string.action_name_restart,
            StandardActions.ACTION_MUTE to R.string.action_name_mute,
            StandardActions.ACTION_SEARCH to R.string.action_name_search
    )

    /**
     * Localized name for a standard [actionKey], a humanized class-name fallback for custom
     * actions (Tasker tasks, app launches, ...), or null when there is no action at all.
     */
    fun get(context: Context, actionKey: String?): String? {
        if (actionKey.isNullOrEmpty()) {
            return null
        }

        titleMap[actionKey]?.let { return context.getString(it) }
        return humanizeKey(actionKey)
    }

    /** "com.example.OpenSettingsAction" -> "Open Settings". */
    private fun humanizeKey(key: String): String =
            key.substringAfterLast('.')
                    .removeSuffix("Action")
                    .replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
                    .ifBlank { key }
}
