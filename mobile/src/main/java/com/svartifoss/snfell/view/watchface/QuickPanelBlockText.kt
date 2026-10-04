package com.svartifoss.snfell.view.watchface

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.QuickPanelBlockType

/** What each quick-panel block is called, how it is described and which glyph stands for it, on
 *  every phone screen that talks about them - the editor and the entry that opens it. */
internal object QuickPanelBlockText {

    @StringRes
    fun title(type: QuickPanelBlockType): Int = when (type) {
        QuickPanelBlockType.HEADER -> R.string.quick_block_header
        QuickPanelBlockType.BUTTONS -> R.string.quick_block_buttons
        QuickPanelBlockType.UP_NEXT -> R.string.quick_block_upnext
        QuickPanelBlockType.VOLUME -> R.string.quick_block_volume
        QuickPanelBlockType.SEEK -> R.string.quick_block_seek
        QuickPanelBlockType.TOOLS -> R.string.quick_block_tools
        QuickPanelBlockType.FAVORITES -> R.string.quick_block_favorites
        QuickPanelBlockType.ACTIONS -> R.string.quick_block_actions
        QuickPanelBlockType.MENU_LINK -> R.string.quick_block_menu
    }

    @StringRes
    fun description(type: QuickPanelBlockType): Int = when (type) {
        QuickPanelBlockType.HEADER -> R.string.quick_block_header_description
        QuickPanelBlockType.BUTTONS -> R.string.quick_block_buttons_description
        QuickPanelBlockType.UP_NEXT -> R.string.quick_block_upnext_description
        QuickPanelBlockType.VOLUME -> R.string.quick_block_volume_description
        QuickPanelBlockType.SEEK -> R.string.quick_block_seek_description
        QuickPanelBlockType.TOOLS -> R.string.quick_block_tools_description
        QuickPanelBlockType.FAVORITES -> R.string.quick_block_favorites_description
        QuickPanelBlockType.ACTIONS -> R.string.quick_block_actions_description
        QuickPanelBlockType.MENU_LINK -> R.string.quick_block_menu_description
    }

    @DrawableRes
    fun icon(type: QuickPanelBlockType): Int = when (type) {
        QuickPanelBlockType.HEADER -> R.drawable.ic_text_fields
        QuickPanelBlockType.BUTTONS -> R.drawable.ic_joystick
        QuickPanelBlockType.UP_NEXT -> R.drawable.ic_queue_music
        QuickPanelBlockType.VOLUME -> R.drawable.ic_volume_up
        QuickPanelBlockType.SEEK -> R.drawable.ic_forward_10
        QuickPanelBlockType.TOOLS -> R.drawable.ic_tune
        QuickPanelBlockType.FAVORITES -> R.drawable.ic_bolt
        QuickPanelBlockType.ACTIONS -> R.drawable.ic_actions_menu
        QuickPanelBlockType.MENU_LINK -> R.drawable.ic_arrow_outward
    }
}
