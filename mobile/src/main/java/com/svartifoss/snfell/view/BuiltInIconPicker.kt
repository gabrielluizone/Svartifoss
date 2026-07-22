package com.svartifoss.snfell.view

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.svartifoss.snfell.R
import com.matejdro.wearutils.miscutils.BitmapUtils

/**
 * "Change icon" first stop: a grid of the app's own action glyphs to pick from, with a
 * "From gallery" button falling back to the classic image-picker flow. A built-in choice is
 * delivered as a stable `android.resource://` [Uri] plus the rendered [Bitmap], so callers
 * feed it through the exact same customIconUri/CustomIconStorage pipeline a gallery image
 * uses (the storage persists the bitmap keyed by the uri, so nothing re-resolves it later).
 */
object BuiltInIconPicker {

    private val BUILT_IN_ICONS = intArrayOf(
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
    )

    fun show(
            activity: Activity,
            accent: Int,
            onIconPicked: (Uri, Bitmap) -> Unit,
            onGalleryRequested: () -> Unit
    ) {
        val density = activity.resources.displayMetrics.density
        // Each cell is 56dp icon + 12dp padding on each side = 80dp total column width
        val cellSize = (56 * density).toInt()
        val cellPadding = (12 * density).toInt()
        val columnWidth = cellSize + cellPadding * 2
        val iconTint = ContextCompat.getColor(activity, R.color.lyra_on_surface)

        lateinit var dialog: AlertDialog

        val adapter = object : BaseAdapter() {
            override fun getCount() = BUILT_IN_ICONS.size
            override fun getItem(position: Int) = BUILT_IN_ICONS[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val resId = BUILT_IN_ICONS[position]
                val iv = (convertView as? ImageView) ?: ImageView(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(cellSize, cellSize)
                    setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
                    background = with(android.util.TypedValue()) {
                        activity.theme.resolveAttribute(
                                android.R.attr.selectableItemBackgroundBorderless, this, true)
                        AppCompatResources.getDrawable(activity, resourceId)
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                iv.setImageResource(resId)
                iv.setColorFilter(iconTint)
                iv.contentDescription = iconLabel(activity, resId)
                iv.setOnClickListener {
                    val drawable = AppCompatResources.getDrawable(activity, resId)
                            ?: return@setOnClickListener
                    // The grid applies a runtime tint, but BitmapUtils rasterizes the drawable's
                    // raw path colours. Several legacy picker resources are authored black, so
                    // they looked correct here and were then saved as an almost invisible black
                    // custom icon on the watch. Persist built-ins as monochrome white templates;
                    // every watch surface can then apply the correct contrast tint.
                    drawable.mutate().setTint(android.graphics.Color.WHITE)
                    val bitmap = BitmapUtils.getBitmap(drawable) ?: return@setOnClickListener
                    val uri = Uri.parse("android.resource://${activity.packageName}/drawable/" +
                            activity.resources.getResourceEntryName(resId))
                    dialog.dismiss()
                    onIconPicked(uri, bitmap)
                }
                return iv
            }
        }

        // GridView with AUTO_FIT automatically fills the dialog width with as many columns as
        // fit without any leftover empty space — no hard-coded column count needed.
        val gridView = GridView(activity).apply {
            numColumns = GridView.AUTO_FIT
            setColumnWidth(columnWidth)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            val pad = (8 * density).toInt()
            setPadding(pad, pad, pad, pad)
            setAdapter(adapter)
        }

        dialog = AlertDialog.Builder(activity)
                .setTitle(R.string.icon_selection_title)
                .setView(gridView)
                .setPositiveButton(R.string.icon_selection_gallery) { _, _ -> onGalleryRequested() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
                ContextCompat.getColor(activity, R.color.lyra_text_secondary))
    }

    /** Human labels for the transport family; remaining utility icons get a cleaned-up name
     * instead of TalkBack spelling technical resource ids such as `ic_nav_playing`. */
    private fun iconLabel(activity: Activity, resId: Int): String {
        val labelRes = when (resId) {
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
            R.drawable.ic_settings,
            R.drawable.ic_menu_manage -> R.string.action_settings
            else -> 0
        }
        if (labelRes != 0) return activity.getString(labelRes)

        return activity.resources.getResourceEntryName(resId)
                .removePrefix("action_")
                .removePrefix("ic_")
                .replace('_', ' ')
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
