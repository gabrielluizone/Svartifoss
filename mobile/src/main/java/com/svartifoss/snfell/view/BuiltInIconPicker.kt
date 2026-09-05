package com.svartifoss.snfell.view

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.svartifoss.snfell.R
import com.matejdro.wearutils.miscutils.BitmapUtils
import java.text.Normalizer

/**
 * "Change icon" first stop: a searchable grid of the app's own glyphs to pick from, with a
 * "From gallery" button falling back to the classic image-picker flow. A built-in choice is
 * delivered as a stable `android.resource://` [Uri] plus the rendered [Bitmap], so callers
 * feed it through the exact same customIconUri/CustomIconStorage pipeline a gallery image
 * uses (the storage persists the bitmap keyed by the uri, so nothing re-resolves it later).
 *
 * The set below is deliberately not limited to transport/action glyphs - callers already used
 * it for settings-style icons (dark mode, history, bug report) before this file existed, so it
 * stays a general "any icon this app ships" picker rather than narrowing to one category.
 */
object BuiltInIconPicker {

    internal val BUILT_IN_ICONS = intArrayOf(
            // The app's own mark, offered first - "use Svartifoss's icon" is one tap away rather
            // than being buried in a list of transport glyphs it has nothing to do with.
            R.drawable.ic_app_brand,

            com.svartifoss.snfell.common.R.drawable.action_play,
            com.svartifoss.snfell.common.R.drawable.action_pause,
            com.svartifoss.snfell.common.R.drawable.action_play_pause,
            com.svartifoss.snfell.common.R.drawable.action_play_filled,
            com.svartifoss.snfell.common.R.drawable.action_pause_filled,
            com.svartifoss.snfell.common.R.drawable.action_pause_expressive,
            com.svartifoss.snfell.common.R.drawable.action_stop,
            com.svartifoss.snfell.common.R.drawable.action_skip_next,
            com.svartifoss.snfell.common.R.drawable.action_skip_prev,
            com.svartifoss.snfell.common.R.drawable.action_forward_5,
            com.svartifoss.snfell.common.R.drawable.action_forward_10,
            com.svartifoss.snfell.common.R.drawable.action_replay_5,
            com.svartifoss.snfell.common.R.drawable.action_replay_10,
            com.svartifoss.snfell.common.R.drawable.action_skip_30_seconds,
            com.svartifoss.snfell.common.R.drawable.action_reverse_30_seconds,
            com.svartifoss.snfell.common.R.drawable.action_restart,
            com.svartifoss.snfell.common.R.drawable.action_like,
            com.svartifoss.snfell.common.R.drawable.action_liked_songs,
            com.svartifoss.snfell.common.R.drawable.action_shuffle,
            com.svartifoss.snfell.common.R.drawable.action_repeat,
            com.svartifoss.snfell.common.R.drawable.action_repeat_one,
            com.svartifoss.snfell.common.R.drawable.action_volume_up,
            com.svartifoss.snfell.common.R.drawable.action_volume_down,
            com.svartifoss.snfell.common.R.drawable.action_volume_off,
            com.svartifoss.snfell.common.R.drawable.action_open_menu,
            com.svartifoss.snfell.common.R.drawable.action_open_playlist,
            // These two ship on the watch already (see StandardIcons's own comment on why they're
            // registered there), but were never added to this picker - the reason "Lyrics" was
            // reported missing here despite the action existing for a full release.
            com.svartifoss.snfell.common.R.drawable.action_open_quick_panel,
            com.svartifoss.snfell.common.R.drawable.action_lyrics,
            com.svartifoss.snfell.common.R.drawable.action_progress,
            com.svartifoss.snfell.common.R.drawable.action_search,
            com.svartifoss.snfell.common.R.drawable.action_custom,
            // Mobile module drawables
            R.drawable.ic_actions_menu,
            R.drawable.ic_album,
            R.drawable.ic_apps,
            R.drawable.ic_arrow_down,
            R.drawable.ic_arrow_left,
            R.drawable.ic_arrow_up,
            R.drawable.ic_autorenew,
            R.drawable.ic_bell,
            R.drawable.ic_bolt,
            R.drawable.ic_bug_report,
            R.drawable.ic_category,
            R.drawable.ic_check_circle,
            R.drawable.ic_chevron_right,
            R.drawable.ic_colors,
            R.drawable.ic_cross_black,
            R.drawable.ic_dark_mode,
            R.drawable.ic_delete,
            R.drawable.ic_download,
            R.drawable.ic_drag_handle,
            R.drawable.ic_export,
            R.drawable.ic_format_paint,
            R.drawable.ic_gamepad,
            R.drawable.ic_help,
            R.drawable.ic_history,
            R.drawable.ic_import,
            R.drawable.ic_light_mode,
            R.drawable.ic_menu_camera,
            R.drawable.ic_menu_gallery,
            R.drawable.ic_menu_manage,
            R.drawable.ic_menu_send,
            R.drawable.ic_menu_share,
            R.drawable.ic_menu_slideshow,
            R.drawable.ic_mini_next,
            R.drawable.ic_mini_prev,
            R.drawable.ic_more_vert,
            R.drawable.ic_music_note,
            R.drawable.ic_music_off,
            R.drawable.ic_nav_actions,
            R.drawable.ic_nav_playing,
            R.drawable.ic_nav_settings,
            R.drawable.ic_nav_stopped,
            R.drawable.ic_nav_tutorial,
            R.drawable.ic_open_in_new,
            R.drawable.ic_paint_roller,
            R.drawable.ic_palette,
            R.drawable.ic_pause_black,
            R.drawable.ic_phone,
            R.drawable.ic_play_black,
            R.drawable.ic_plus,
            R.drawable.ic_send_to_mobile,
            R.drawable.ic_settings,
            R.drawable.ic_share,
            R.drawable.ic_update_available,
            R.drawable.ic_watch,
            R.drawable.ic_watch_off,
            R.drawable.ic_playlist_play,
            // Already shipping in the app (settings-row icons, mostly) but never reachable from
            // this picker - the rest of what prompted "I think more icons used in the app are
            // missing here". Brand-specific marks (YouTube Music, Buy Me a Coffee) are
            // deliberately left out: they identify one specific external service, and offering
            // them as a generic custom-action icon would misrepresent whatever the user assigns
            // them to.
            R.drawable.ic_always_on,
            R.drawable.ic_archived_options,
            R.drawable.ic_backdrop_style,
            R.drawable.ic_blur,
            R.drawable.ic_brightness_medium,
            R.drawable.ic_clock,
            R.drawable.ic_contrast,
            R.drawable.ic_devices_wearables,
            R.drawable.ic_dim_style,
            R.drawable.ic_email,
            R.drawable.ic_favorite,
            R.drawable.ic_gamepad_circle_down,
            R.drawable.ic_joystick,
            R.drawable.ic_language,
            R.drawable.ic_mini_buttons,
            R.drawable.ic_opacity,
            R.drawable.ic_person,
            R.drawable.ic_player_theme,
            R.drawable.ic_playlist_add,
            R.drawable.ic_progress_ring,
            R.drawable.ic_progress_style,
            R.drawable.ic_quick_actions,
            R.drawable.ic_rounded_corner,
            R.drawable.ic_swipe_directions,
            R.drawable.ic_sync_to_watch,
            R.drawable.ic_text_fields,
            R.drawable.ic_transport_controls,
            R.drawable.ic_tune,
            R.drawable.ic_watch_crossed_out,
            R.drawable.ic_watch_vibration,
            // Extended Material Symbols set (auto-converted from icons/icons_1 & icons_2).
            R.drawable.ic_add_circle_outline,
            R.drawable.ic_add_link,
            R.drawable.ic_alternate_email,
            R.drawable.ic_arrow_back,
            R.drawable.ic_arrow_downward,
            R.drawable.ic_arrow_forward,
            R.drawable.ic_arrow_insert,
            R.drawable.ic_arrow_outward,
            R.drawable.ic_arrow_upward,
            R.drawable.ic_asterisk,
            R.drawable.ic_attach_file,
            R.drawable.ic_autoplay,
            R.drawable.ic_backup,
            R.drawable.ic_border_color,
            R.drawable.ic_build,
            R.drawable.ic_coffee,
            R.drawable.ic_contrast_square,
            R.drawable.ic_deblur,
            R.drawable.ic_deployed_code,
            R.drawable.ic_developer_board,
            R.drawable.ic_edit,
            R.drawable.ic_edit_square,
            R.drawable.ic_fast_forward,
            R.drawable.ic_fast_rewind,
            R.drawable.ic_favorite_border,
            R.drawable.ic_folder_open,
            R.drawable.ic_forward_10,
            R.drawable.ic_forward_30,
            R.drawable.ic_forward_5,
            R.drawable.ic_gamepad_circle_left,
            R.drawable.ic_gamepad_circle_right,
            R.drawable.ic_gamepad_circle_up,
            R.drawable.ic_g_translate,
            R.drawable.ic_headphones,
            R.drawable.ic_hub,
            R.drawable.ic_imagesearch_roller,
            R.drawable.ic_link,
            R.drawable.ic_link_off,
            R.drawable.ic_mark_email_unread,
            R.drawable.ic_match_case,
            R.drawable.ic_mobile_arrow_down,
            R.drawable.ic_moon_stars,
            R.drawable.ic_music_note_2,
            R.drawable.ic_newsmode,
            R.drawable.ic_open_with,
            R.drawable.ic_play_arrow,
            R.drawable.ic_playlist_add_check,
            R.drawable.ic_playlist_remove,
            R.drawable.ic_queue_music,
            R.drawable.ic_repeat_one,
            R.drawable.ic_replay_10,
            R.drawable.ic_replay_30,
            R.drawable.ic_replay_5,
            R.drawable.ic_save,
            R.drawable.ic_shuffle,
            R.drawable.ic_shuffle_on,
            R.drawable.ic_signal_wifi_off,
            R.drawable.ic_skip_next,
            R.drawable.ic_skip_previous,
            R.drawable.ic_stop,
            R.drawable.ic_text_decrease,
            R.drawable.ic_text_increase,
            R.drawable.ic_texture_add,
            R.drawable.ic_thumb_down,
            R.drawable.ic_thumb_up,
            R.drawable.ic_timelapse,
            R.drawable.ic_toggle_off,
            R.drawable.ic_toggle_on,
            R.drawable.ic_tooltip,
            R.drawable.ic_unknown_2,
            R.drawable.ic_upload_file,
            R.drawable.ic_volume_down,
            R.drawable.ic_volume_off,
            R.drawable.ic_volume_up,
            R.drawable.ic_watch_arrow,
            R.drawable.ic_watch_arrow_down,
            R.drawable.ic_watch_screentime,
            R.drawable.ic_workspaces,
            R.drawable.ic_zoom_out_map,
            // Material Symbols set 3 (icons/icons_3). "backup", "language", "match_case" and
            // "save" were already shipped from an earlier set under the same name and are not
            // re-added here.
            R.drawable.ic_account_circle,
            R.drawable.ic_admin_panel_settings,
            R.drawable.ic_apparel,
            R.drawable.ic_bottom_panel_open,
            R.drawable.ic_cloud_download,
            R.drawable.ic_cloud_off,
            R.drawable.ic_dashboard_customize,
            R.drawable.ic_download_for_offline,
            R.drawable.ic_file_copy,
            R.drawable.ic_file_save,
            R.drawable.ic_folder_shared,
            R.drawable.ic_left_panel_open,
            R.drawable.ic_local_mall,
            R.drawable.ic_lowercase,
            R.drawable.ic_more_down,
            R.drawable.ic_play_pause,
            R.drawable.ic_save_as,
            R.drawable.ic_sd_card,
            R.drawable.ic_shop,
            R.drawable.ic_shopping_bag,
            R.drawable.ic_split_scene_left,
            R.drawable.ic_split_scene_right,
            R.drawable.ic_splitscreen_add,
            R.drawable.ic_splitscreen_bottom,
            R.drawable.ic_splitscreen_top,
            R.drawable.ic_store,
            R.drawable.ic_subtitles,
            R.drawable.ic_switches,
            R.drawable.ic_tactic,
            R.drawable.ic_tile_medium,
            R.drawable.ic_tile_small,
            R.drawable.ic_titlecase,
            R.drawable.ic_top_panel_open,
            R.drawable.ic_upload,
            R.drawable.ic_uppercase,
            R.drawable.ic_view_column,
            R.drawable.ic_view_column_2,
            R.drawable.ic_web_stories,
            R.drawable.ic_window,
    )

    /**
     * Icons the picker offers only while **Show archived options** is on.
     *
     * Kept as a second array rather than a flag on the first, because "archived" here does not
     * mean the same thing it does for a watch layout: nothing is wrong with these and nothing was
     * retired. They are simply marks that belong to something other than this app, so offering
     * them in the ordinary grid would put them beside the transport glyphs as though they meant
     * something to a media action. The developer switch is the existing way to say "show me the
     * things that are deliberately out of the way", so it is the one used here too.
     */
    internal val ARCHIVED_ICONS = intArrayOf(
            R.drawable.ic_haibane_renmei,
    )

    fun show(
            activity: Activity,
            accent: Int,
            onIconPicked: (Uri, Bitmap) -> Unit,
            onGalleryRequested: () -> Unit
    ) {
        val iconTint = ContextCompat.getColor(activity, R.color.lyra_on_surface)
        // Read per call rather than cached: the switch lives in Settings, and this dialog is
        // opened long after the process started. Archived icons go last so turning the switch on
        // never reorders the grid somebody already knows.
        val showArchived = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean("dev_show_archived", false)
        val available = if (showArchived) BUILT_IN_ICONS + ARCHIVED_ICONS else BUILT_IN_ICONS
        val entries = available.map { resId -> IconEntry(resId, iconLabel(activity, resId)) }

        lateinit var dialog: AlertDialog

        fun pick(entry: IconEntry) {
            val drawable = AppCompatResources.getDrawable(activity, entry.resId) ?: return
            // The grid applies a runtime tint, but BitmapUtils rasterizes the drawable's raw path
            // colours. Several legacy picker resources are authored black, so they looked correct
            // here and were then saved as an almost invisible black custom icon on the watch.
            // Persist built-ins as monochrome white templates; every watch surface can then apply
            // the correct contrast tint.
            drawable.mutate().setTint(android.graphics.Color.WHITE)
            val bitmap = BitmapUtils.getBitmap(drawable) ?: return
            val uri = Uri.parse("android.resource://${activity.packageName}/drawable/" +
                    activity.resources.getResourceEntryName(entry.resId))
            dialog.dismiss()
            onIconPicked(uri, bitmap)
        }

        val root = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_icon_selection, null, false)
        val searchInput = root.findViewById<EditText>(R.id.icon_search_input)
        val emptyView = root.findViewById<TextView>(R.id.icon_search_empty)
        val recycler = root.findViewById<RecyclerView>(R.id.icon_grid)

        val adapter = IconGridAdapter(iconTint, ::pick)
        recycler.adapter = adapter
        val layoutManager = GridLayoutManager(activity, MIN_SPAN_COUNT)
        recycler.layoutManager = layoutManager
        // The dialog's real width is only known once it has been measured, so the span count is
        // computed from it here rather than guessed from the screen width - a phone in split
        // screen or a narrow multi-window dialog gets fewer, correctly-sized columns instead of
        // columns so cramped the icons touch.
        recycler.addOnLayoutChangeListener { view, left, _, right, _, oldLeft, _, oldRight, _ ->
            val width = right - left
            if (width <= 0 || width == oldRight - oldLeft) return@addOnLayoutChangeListener
            val columnWidth =
                    (COLUMN_WIDTH_DP * view.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            layoutManager.spanCount = (width / columnWidth).coerceAtLeast(MIN_SPAN_COUNT)
        }

        fun applyFilter(query: String) {
            val filtered = if (query.isBlank()) {
                entries
            } else {
                val needle = normalizeForSearch(query)
                entries.filter { normalizeForSearch(it.label).contains(needle) }
            }
            adapter.submit(filtered)
            emptyView.isVisible = filtered.isEmpty()
            recycler.isVisible = filtered.isNotEmpty()
        }
        applyFilter("")
        searchInput.doAfterTextChanged { applyFilter(it?.toString().orEmpty()) }

        dialog = AlertDialog.Builder(activity)
                .setTitle(R.string.icon_selection_title)
                .setView(root)
                .setPositiveButton(R.string.icon_selection_gallery) { _, _ -> onGalleryRequested() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
                ContextCompat.getColor(activity, R.color.lyra_text_secondary))
    }

    /** Accent/case-insensitive comparison so "assao"/"ação" and "Colors"/"colors" match the same
     *  row - a label typed while searching is rarely spelled with the exact original casing or
     *  diacritics. */
    private fun normalizeForSearch(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "").lowercase()
    }

    private data class IconEntry(val resId: Int, val label: String)

    private const val MIN_SPAN_COUNT = 3

    /** Target column width in dp: 40dp icon + a little breathing room either side, matching the
     *  cell's own padding in item_icon_picker.xml. */
    private const val COLUMN_WIDTH_DP = 76

    private class IconGridAdapter(
            private val tint: Int,
            private val onPicked: (IconEntry) -> Unit
    ) : RecyclerView.Adapter<IconGridAdapter.ViewHolder>() {

        private var items: List<IconEntry> = emptyList()

        fun submit(newItems: List<IconEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_icon_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = items[position]
            holder.image.setImageResource(entry.resId)
            holder.image.setColorFilter(tint)
            holder.label.text = entry.label
            holder.itemView.contentDescription = entry.label
            holder.itemView.setOnClickListener { onPicked(entry) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.icon_picker_image)
            val label: TextView = view.findViewById(R.id.icon_picker_label)
        }
    }

    /** Human labels for the transport family; remaining utility icons get a cleaned-up name
     * instead of TalkBack spelling technical resource ids such as `ic_nav_playing`. */
    private fun iconLabel(activity: Activity, resId: Int): String {
        val labelRes = when (resId) {
            R.drawable.ic_app_brand -> R.string.app_name
            com.svartifoss.snfell.common.R.drawable.action_play,
            com.svartifoss.snfell.common.R.drawable.action_play_filled,
            R.drawable.ic_play_black,
            R.drawable.ic_nav_playing -> R.string.action_play
            com.svartifoss.snfell.common.R.drawable.action_pause,
            com.svartifoss.snfell.common.R.drawable.action_pause_filled,
            com.svartifoss.snfell.common.R.drawable.action_pause_expressive,
            R.drawable.ic_pause_black,
            R.drawable.ic_nav_stopped -> R.string.action_pause
            com.svartifoss.snfell.common.R.drawable.action_play_pause -> R.string.action_play_pause
            com.svartifoss.snfell.common.R.drawable.action_skip_prev,
            R.drawable.ic_mini_prev -> R.string.action_skip_prev
            com.svartifoss.snfell.common.R.drawable.action_skip_next,
            R.drawable.ic_mini_next -> R.string.action_skip_next
            com.svartifoss.snfell.common.R.drawable.action_stop -> R.string.action_stop
            com.svartifoss.snfell.common.R.drawable.action_restart -> R.string.action_restart
            com.svartifoss.snfell.common.R.drawable.action_volume_up -> R.string.action_volume_up
            com.svartifoss.snfell.common.R.drawable.action_volume_down -> R.string.volume_down
            com.svartifoss.snfell.common.R.drawable.action_volume_off -> R.string.action_mute
            com.svartifoss.snfell.common.R.drawable.action_like,
            com.svartifoss.snfell.common.R.drawable.action_liked_songs -> R.string.action_like
            com.svartifoss.snfell.common.R.drawable.action_shuffle -> R.string.action_shuffle
            com.svartifoss.snfell.common.R.drawable.action_repeat -> R.string.action_repeat
            com.svartifoss.snfell.common.R.drawable.action_repeat_one -> R.string.action_repeat_one
            com.svartifoss.snfell.common.R.drawable.action_search -> R.string.action_search
            com.svartifoss.snfell.common.R.drawable.action_open_quick_panel ->
                R.string.open_quick_actions_panel
            com.svartifoss.snfell.common.R.drawable.action_lyrics -> R.string.action_open_lyrics
            R.drawable.ic_settings,
            R.drawable.ic_menu_manage -> R.string.action_settings
            else -> 0
        }
        if (labelRes != 0) return activity.getString(labelRes)

        return activity.resources.getResourceEntryName(resId)
                .removePrefix("action_")
                .removePrefix("ic_")
                .replace('_', ' ')
                .split(' ')
                .joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }
    }
}
