package com.svartifoss.snfell.view.watchface.home

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.RecyclerView
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.watchface.theme.WatchThemeProfile
import com.svartifoss.snfell.view.watchface.theme.WatchThemeRepository
import kotlin.math.roundToInt

/**
 * The theme strip's tiles: each theme as its picture and its name, the applied one ringed and
 * checked, and the community gallery last.
 *
 * Pictures come from [thumbnailer], which draws them one at a time; a tile whose picture is not
 * drawn yet shows an empty watch of the same size, so nothing moves when it arrives. The adapter
 * outlives the RecyclerView it is shown in - Home's row can be rebuilt - so the strip keeps its
 * pictures and its order through a rebind.
 */
internal class WatchLooksAdapter(
        private val thumbnailer: WatchLookThumbnailer,
        /** The look to draw for [WatchLook.key]; null for the community tile. */
        private val profileFor: (WatchLook) -> WatchThemeProfile?,
        private val onPick: (WatchLookTile) -> Unit
) : RecyclerView.Adapter<WatchLooksAdapter.TileHolder>(),
        WatchLookThumbnailer.Listener {

    private var tiles: List<WatchLookTile> = emptyList()
    private var accent = 0
    private var surface = 0
    private var background = 0

    init {
        setHasStableIds(true)
        thumbnailer.listener = this
    }

    val appliedIndex: Int get() = tiles.indexOfFirst { it.applied }

    fun submit(newTiles: List<WatchLookTile>) {
        if (newTiles == tiles) return
        tiles = newTiles
        notifyDataSetChanged()
    }

    /** Recolours the ring, the check and the applied name; the accent follows the album art. */
    fun setColors(accent: Int, surface: Int, background: Int) {
        if (accent == this.accent && surface == this.surface && background == this.background) return
        this.accent = accent
        this.surface = surface
        this.background = background
        notifyItemRangeChanged(0, itemCount, PAYLOAD_COLORS)
    }

    /** Every picture is stale, for instance because the song changed: draw them all again. */
    fun redrawAll() {
        notifyItemRangeChanged(0, itemCount, PAYLOAD_THUMBNAIL)
    }

    /** [key]'s picture is stale. */
    fun redraw(key: String) {
        val index = tiles.indexOfFirst { it.look.key == key }
        if (index >= 0) notifyItemChanged(index, PAYLOAD_THUMBNAIL)
    }

    override fun onThumbnail(key: String, thumbnail: Bitmap) {
        val index = tiles.indexOfFirst { it.look.key == key }
        if (index >= 0) notifyItemChanged(index, PAYLOAD_THUMBNAIL)
    }

    override fun getItemCount(): Int = tiles.size

    override fun getItemId(position: Int): Long = tiles[position].look.key.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileHolder =
            TileHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_watch_look, parent, false))

    override fun onBindViewHolder(holder: TileHolder, position: Int) {
        holder.bind(tiles[position])
    }

    override fun onBindViewHolder(holder: TileHolder, position: Int, payloads: MutableList<Any>) {
        when {
            payloads.isEmpty() -> holder.bind(tiles[position])
            PAYLOAD_THUMBNAIL in payloads -> holder.bindThumbnail(tiles[position])
            else -> holder.bindColors(tiles[position])
        }
    }

    inner class TileHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumbnail: ImageView = view.findViewById(R.id.watch_look_thumbnail)
        private val icon: ImageView = view.findViewById(R.id.watch_look_icon)
        private val ring: View = view.findViewById(R.id.watch_look_ring)
        private val check: ImageView = view.findViewById(R.id.watch_look_check)
        private val label: TextView = view.findViewById(R.id.watch_look_label)
        private val context = view.context

        fun bind(tile: WatchLookTile) {
            label.text = titleOf(tile.look)
            itemView.contentDescription = describe(tile)
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onPick(tiles[position])
            }
            ViewCompat.setAccessibilityDelegate(itemView, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = Button::class.java.name
                    info.isSelected = tile.applied
                }
            })
            bindThumbnail(tile)
            bindColors(tile)
        }

        fun bindThumbnail(tile: WatchLookTile) {
            val look = tile.look
            if (look is WatchLook.Community) {
                thumbnail.setImageDrawable(emptyWatch())
                icon.visibility = View.VISIBLE
                return
            }
            icon.visibility = View.GONE
            val picture = thumbnailer.picture(look.key)
            if (picture != null) {
                thumbnail.setImageBitmap(picture)
            } else {
                thumbnail.setImageDrawable(emptyWatch())
            }
            if (!thumbnailer.isFresh(look.key)) {
                thumbnailer.request(look.key) { profileFor(look) }
            }
        }

        fun bindColors(tile: WatchLookTile) {
            ring.visibility = if (tile.applied) View.VISIBLE else View.GONE
            check.visibility = if (tile.applied) View.VISIBLE else View.GONE
            if (tile.applied) {
                ring.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dp(2f), accent)
                }
                check.imageTintList = ColorStateList.valueOf(accent)
                check.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(background)
                }
            }
            label.setTextColor(if (tile.applied) accent else
                ContextCompat.getColor(context, R.color.lyra_on_surface))
            icon.imageTintList = ColorStateList.valueOf(accent)
        }

        /**
         * A plain disc the size of a drawn watch: the stand-in until the picture arrives. Inset by
         * the margin the preview leaves around the watch for its shadow, so nothing changes size
         * when the picture replaces it.
         */
        private fun emptyWatch() = InsetDrawable(
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(surface)
                },
                dp(3f))

        private fun titleOf(look: WatchLook): String = when (look) {
            is WatchLook.BuiltIn -> WatchThemeRepository.displayNameForFace(context, look.face)
            is WatchLook.Saved -> look.profile.name
            // One short word, because the name is one line: the full name below needed two and was
            // cut off. [describe] still gives a screen reader the full name.
            WatchLook.Community -> context.getString(R.string.online_themes_title_short)
        }

        /** The sentence the themes library reads for the same theme, so the two agree. */
        private fun describe(tile: WatchLookTile): String {
            val look = tile.look
            val subtitle = when (look) {
                is WatchLook.BuiltIn -> context.getString(
                        R.string.watch_theme_builtin_subtitle,
                        WatchThemeRepository.displayNameForFace(context, look.face))
                is WatchLook.Saved -> context.getString(
                        R.string.watch_theme_custom_subtitle,
                        WatchThemeRepository.displayNameForFace(context, look.profile.baseFace))
                WatchLook.Community -> return context.getString(R.string.online_themes_title)
            }
            return context.getString(
                    if (tile.applied) R.string.watch_theme_row_description_applied
                    else R.string.watch_theme_row_description,
                    titleOf(look),
                    subtitle)
        }

        private fun dp(value: Float): Int =
                (value * context.resources.displayMetrics.density).roundToInt()
    }

    private companion object {
        const val PAYLOAD_THUMBNAIL = "thumbnail"
        const val PAYLOAD_COLORS = "colors"
    }
}
