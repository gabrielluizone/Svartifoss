package com.svartifoss.snfell.view.mainactivity

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.media.session.MediaSession
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.QueueArtworkResolver
import com.svartifoss.snfell.view.MusicLoadingBarsView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The mini player's queue sheet.
 *
 * A [RecyclerView] rather than the hand-inflated column this replaced, and the covers are why: the
 * old list built one View per entry into a `LinearLayout` and kept every one of them alive, which
 * was merely wasteful for two lines of text and untenable once each row also holds a bitmap. A
 * long queue would have carried a couple of hundred decoded covers with nothing to release them.
 *
 * Covers come from [QueueArtworkResolver] - the same cheapest-first chain, the same disk cache and
 * the same "fetch queue covers online" switch the watch queue already goes through. That is
 * deliberate: a cover the wrist can show is one this list can show, and a second resolution chain
 * here would be free to drift from it.
 */
internal class QueueAdapter(
        private val scope: LifecycleCoroutineScope,
        private val onSelect: (MediaSession.QueueItem) -> Unit
) : ListAdapter<QueueAdapter.Row, QueueAdapter.ViewHolder>(DIFF) {

    /** One entry plus everything the row needs that the entry alone does not carry. */
    data class Row(
            val item: MediaSession.QueueItem,
            val position: Int,
            val isPlaying: Boolean,
            /** Before the playing track. Drawn dimmer, so the list reads as a place in a running
             *  session rather than as a flat list of equal candidates. */
            val isPast: Boolean)

    var accentColor: Int = 0
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    /**
     * Covers already resolved for this sheet.
     *
     * [QueueArtworkResolver] caches the *downloaded bytes*, not the decoded bitmap, so without
     * this every scroll back over a row would decode it again. Bounded, because the whole point of
     * moving to a RecyclerView was to stop holding an unbounded number of bitmaps.
     */
    private val covers = object : LruCache<Long, Bitmap>(COVER_CACHE_ENTRIES) {}

    /** Entries whose resolution already finished and found nothing, so it is not retried on
     *  every rebind - a queue of local tracks with no art would otherwise re-run the whole chain,
     *  MediaStore lookup included, each time the row scrolled back into view. */
    private val resolvedEmpty = mutableSetOf<Long>()

    private var allowRemoteArtwork = true

    fun setAllowRemoteArtwork(allow: Boolean) {
        if (allowRemoteArtwork == allow) return
        allowRemoteArtwork = allow
        // A queue resolved with the switch off holds "no cover" answers that are only true while
        // it stays off. Drop them so turning it on fills the rows in rather than leaving the
        // sheet permanently blank until it is reopened.
        if (allow) resolvedEmpty.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                    LayoutInflater.from(parent.context)
                            .inflate(R.layout.item_queue_song, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelPendingCover()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cover: ImageView = view.findViewById(R.id.queue_item_cover)
        private val title: TextView = view.findViewById(R.id.queue_item_title)
        private val artist: TextView = view.findViewById(R.id.queue_item_artist)
        private val positionLabel: TextView = view.findViewById(R.id.queue_item_position)
        private val bars: MusicLoadingBarsView = view.findViewById(R.id.queue_item_playing_bars)
        private var coverJob: Job? = null

        fun cancelPendingCover() {
            coverJob?.cancel()
            coverJob = null
        }

        fun bind(row: Row) {
            val context = itemView.context
            val description = row.item.description
            title.text = description.title ?: context.getString(R.string.queue_item_unknown_title)
            artist.text = description.subtitle ?: ""
            artist.isVisible = !description.subtitle.isNullOrBlank()

            positionLabel.text = row.position.toString()
            positionLabel.isVisible = !row.isPlaying
            bars.isVisible = row.isPlaying

            val onSurface = ContextCompat.getColor(context, R.color.lyra_on_surface)
            if (row.isPlaying) {
                title.setTextColor(accentColor)
                bars.setBarsColor(accentColor)
                // A wash rather than a fill: the row still has to read as one of the list, and the
                // bars plus the accent title already say which one it is.
                itemView.setBackgroundColor(ColorUtils.setAlphaComponent(accentColor, 0x14))
            } else {
                title.setTextColor(onSurface)
                // Not 0: that clears the background outright, taking the layout's
                // ?selectableItemBackground with it, so any row recycled out of the playing state
                // silently lost its touch feedback for the rest of the sheet's life.
                itemView.setBackgroundResource(selectableItemBackground(context))
            }
            // Played entries stay reachable - tapping one goes back to it - but recede.
            itemView.alpha = if (row.isPast) PAST_ROW_ALPHA else 1f

            itemView.setOnClickListener { onSelect(row.item) }
            itemView.contentDescription = buildString {
                append(title.text)
                if (artist.text.isNotBlank()) append(", ").append(artist.text)
                if (row.isPlaying) {
                    append(", ").append(context.getString(R.string.queue_item_now_playing))
                }
            }

            bindCover(row)
        }

        private fun bindCover(row: Row) {
            cancelPendingCover()
            val id = row.item.queueId
            val cached = covers.get(id)
            if (cached != null) {
                showCover(cached)
                return
            }
            showPlaceholder()
            if (id in resolvedEmpty) return

            coverJob = scope.launch {
                val bitmap = QueueArtworkResolver.resolve(
                        itemView.context.applicationContext,
                        row.item.description,
                        allowRemoteArtwork,
                        targetPx = COVER_TARGET_PX)
                if (bitmap == null) {
                    resolvedEmpty += id
                    return@launch
                }
                covers.put(id, bitmap)
                // The holder is recycled while this runs, so the row it is bound to now may be a
                // different track entirely. Without this check a slow cover lands on whichever
                // entry happens to occupy the recycled row - the classic way an image list ends up
                // showing the wrong art for a second.
                if (bindingAdapterPosition != RecyclerView.NO_POSITION &&
                        getItem(bindingAdapterPosition).item.queueId == id) {
                    showCover(bitmap)
                }
            }
        }

        private fun showCover(bitmap: Bitmap) {
            cover.scaleType = ImageView.ScaleType.CENTER_CROP
            cover.setImageBitmap(bitmap)
            cover.imageTintList = null
        }

        private fun showPlaceholder() {
            cover.scaleType = ImageView.ScaleType.CENTER
            cover.setImageResource(R.drawable.ic_music_note)
            cover.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, R.color.lyra_text_secondary))
        }
    }

    /** The theme's own row-press background, resolved once and reused for every idle row. */
    private fun selectableItemBackground(context: android.content.Context): Int {
        cachedSelectableBackground?.let { return it }
        val typed = android.util.TypedValue()
        context.theme.resolveAttribute(
                android.R.attr.selectableItemBackground, typed, true)
        return typed.resourceId.also { cachedSelectableBackground = it }
    }

    private var cachedSelectableBackground: Int? = null

    private companion object {
        /** 132px covers a 44dp thumbnail on a 3x screen without decoding a full-size cover. */
        const val COVER_TARGET_PX = 132
        const val COVER_CACHE_ENTRIES = 64
        const val PAST_ROW_ALPHA = 0.45f

        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(old: Row, new: Row) =
                    old.item.queueId == new.item.queueId

            override fun areContentsTheSame(old: Row, new: Row) =
                    old.position == new.position &&
                            old.isPlaying == new.isPlaying &&
                            old.isPast == new.isPast &&
                            old.item.description.title == new.item.description.title &&
                            old.item.description.subtitle == new.item.description.subtitle
        }
    }
}
