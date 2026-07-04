package com.svartifoss.snfell.view

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ScrollView
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
            com.svartifoss.snfell.common.R.drawable.action_stop,
            com.svartifoss.snfell.common.R.drawable.action_skip_next,
            com.svartifoss.snfell.common.R.drawable.action_skip_prev,
            com.svartifoss.snfell.common.R.drawable.action_forward_10,
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
            R.drawable.ic_music_note,
            R.drawable.ic_playlist_play,
            R.drawable.ic_history,
            R.drawable.ic_yt_music
    )

    fun show(
            activity: Activity,
            accent: Int,
            onIconPicked: (Uri, Bitmap) -> Unit,
            onGalleryRequested: () -> Unit
    ) {
        val density = activity.resources.displayMetrics.density
        val cellSize = (56 * density).toInt()
        val cellPadding = (12 * density).toInt()
        val iconTint = ContextCompat.getColor(activity, R.color.lyra_on_surface)

        val grid = GridLayout(activity).apply {
            columnCount = 5
            val pad = (12 * density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }

        lateinit var dialog: AlertDialog

        for (resId in BUILT_IN_ICONS) {
            val cell = ImageView(activity).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cellSize
                    height = cellSize
                }
                setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
                background = with(android.util.TypedValue()) {
                    activity.theme.resolveAttribute(
                            android.R.attr.selectableItemBackgroundBorderless, this, true)
                    AppCompatResources.getDrawable(activity, resourceId)
                }
                setImageResource(resId)
                setColorFilter(iconTint)
                contentDescription = activity.resources.getResourceEntryName(resId)
            }

            cell.setOnClickListener {
                val drawable = AppCompatResources.getDrawable(activity, resId)
                        ?: return@setOnClickListener
                val bitmap = BitmapUtils.getBitmap(drawable) ?: return@setOnClickListener

                // Entry-name uri (not raw res id): stable across builds, and unique per icon
                // so CustomIconStorage files don't collide.
                val uri = Uri.parse("android.resource://${activity.packageName}/drawable/" +
                        activity.resources.getResourceEntryName(resId))

                dialog.dismiss()
                onIconPicked(uri, bitmap)
            }

            grid.addView(cell)
        }

        dialog = AlertDialog.Builder(activity)
                .setTitle(R.string.icon_selection_title)
                .setView(ScrollView(activity).apply { addView(grid) })
                .setPositiveButton(R.string.icon_selection_gallery) { _, _ -> onGalleryRequested() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
                ContextCompat.getColor(activity, R.color.lyra_text_secondary))
    }
}
