package com.svartifoss.snfell.view.mainactivity

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.TitledActivity
import com.svartifoss.snfell.common.R as commonR

/**
 * "Guide" tab — a static, scrollable walkthrough of everything the watch app can do (screen-zone
 * taps, multi-press gestures, physical buttons, rotary, swipes, the Tile and system media controls).
 * Purely informational, so it needs no DI or view model.
 */
class TutorialFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_tutorial, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val controlsPath = getString(
            R.string.settings_search_breadcrumb,
            getString(R.string.controls_header),
            "${getString(R.string.controls_state_playing)} / ${getString(R.string.controls_state_stopped)}"
        )
        val actionsPath = getString(R.string.actions_menu)
        val behaviorPath = getString(
            R.string.settings_search_breadcrumb,
            getString(R.string.action_settings),
            getString(R.string.settings_section_watch)
        )

        bindCard(view, R.id.card_taps, R.drawable.ic_nav_actions,
            R.string.tutorial_taps_title, R.string.tutorial_taps_desc, controlsPath)
        bindCard(view, R.id.card_quick_panel, R.drawable.ic_quick_actions,
            R.string.tutorial_quick_panel_title, R.string.tutorial_quick_panel_desc, actionsPath)
        bindCard(view, R.id.card_gestures, R.drawable.ic_nav_tutorial,
            R.string.tutorial_gestures_title, R.string.tutorial_gestures_desc, controlsPath)
        bindCard(view, R.id.card_buttons, R.drawable.ic_settings,
            R.string.tutorial_buttons_title, R.string.tutorial_buttons_desc, controlsPath)
        bindCard(view, R.id.card_rotary, commonR.drawable.button_turn_cw,
            R.string.tutorial_rotary_title, R.string.tutorial_rotary_desc, behaviorPath)
        bindCard(view, R.id.card_swipe_up, R.drawable.ic_playlist_play,
            R.string.tutorial_swipe_up_title, R.string.tutorial_swipe_up_desc, controlsPath)
        bindCard(view, R.id.card_shortcuts, R.drawable.ic_playlist_add,
            R.string.tutorial_shortcuts_title, R.string.tutorial_shortcuts_desc, actionsPath)
        bindCard(view, R.id.card_yt_music, commonR.drawable.action_liked_songs,
            R.string.tutorial_yt_music_title, R.string.tutorial_yt_music_desc, actionsPath)
        bindCard(view, R.id.card_exit, commonR.drawable.button_back,
            R.string.tutorial_exit_title, R.string.tutorial_exit_desc, behaviorPath)
        bindCard(view, R.id.card_tile, R.drawable.ic_music_note,
            R.string.tutorial_tile_title, R.string.tutorial_tile_desc)
        bindCard(view, R.id.card_complication, R.drawable.ic_music_note,
            R.string.tutorial_complication_title, R.string.tutorial_complication_desc)
        bindCard(view, R.id.card_system, R.drawable.ic_nav_playing,
            R.string.tutorial_system_title, R.string.tutorial_system_desc)
    }

    override fun onStart() {
        super.onStart()
        if (parentFragmentManager.findFragmentById(R.id.fragment_container) !== this) return

        (activity as? TitledActivity)?.updateActivityTitle(getString(R.string.tutorial_header))
    }

    private fun bindCard(
        root: View,
        cardId: Int,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes description: Int,
        path: CharSequence? = null
    ) {
        val card = root.findViewById<View>(cardId)
        val iconView = card.findViewById<ImageView>(R.id.tutorial_card_icon)
        iconView.setImageResource(icon)
        // The layout's own android:tint is the static Lyra default (always green/sage) - this is
        // a standalone Activity outside MainActivity's live accent traversal, so it needs its own
        // one-shot resolve, same as LyraAccent's other standalone-dialog callers.
        iconView.imageTintList = ColorStateList.valueOf(LyraAccent.resolve(requireContext()))
        card.findViewById<TextView>(R.id.tutorial_card_title).setText(title)
        card.findViewById<TextView>(R.id.tutorial_card_desc).setText(description)
        card.findViewById<TextView>(R.id.tutorial_card_path).apply {
            text = path
            setTextColor(LyraAccent.resolve(requireContext()))
            visibility = if (path.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }
}
