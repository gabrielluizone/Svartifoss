package com.svartifoss.snfell.watch.tile

import android.content.Context
import android.net.Uri
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.ButtonDefaults
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.MainActivity
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.tasks.await

/**
 * Glanceable quick-control Tile: shows the current track + artist, play/pause and skip prev/next
 * buttons plus a slimmer -10s/+10s seek row, without opening the app. Tapping the text opens
 * Svartifoss on the watch.
 *
 * The Tile is a pure proxy like [com.svartifoss.snfell.watch.communication.WatchMediaSession]:
 * it reads the latest music-state [androidx.wear.protolayout] DataItem the phone already publishes,
 * and forwards transport controls back to the phone over the Data Layer. Button taps use a
 * [ActionBuilders.LoadAction] so the framework re-requests the Tile; we read the clicked id from
 * [RequestBuilders.TileRequest.getCurrentState] and dispatch the matching control before rebuilding.
 */
class MediaTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        // Dispatch any pending click first, then render the freshest state we can read.
        val clickedId = requestParams.currentState.lastClickableId
        dispatchClick(clickedId)
        val state = readMusicState()

        // A play/pause toggle was only just sent to the phone, which won't have published the
        // resulting state back yet - the DataItem read above still holds the pre-click state.
        // Render the icon optimistically flipped instead of showing the stale one until the
        // next refresh, which would make the button feel like it did nothing.
        val flipPlaying = clickedId == ID_PLAY_PAUSE

        val layout = buildLayout(this@MediaTileService, state, flipPlaying)

        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(REFRESH_INTERVAL_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = scope.future {
        ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .addIdToImageMapping(ICON_PREV, resourceById(com.svartifoss.snfell.common.R.drawable.action_skip_prev))
            .addIdToImageMapping(ICON_NEXT, resourceById(com.svartifoss.snfell.common.R.drawable.action_skip_next))
            .addIdToImageMapping(ICON_PLAY, resourceById(com.svartifoss.snfell.common.R.drawable.action_play))
            .addIdToImageMapping(ICON_PAUSE, resourceById(com.svartifoss.snfell.common.R.drawable.action_pause))
            .addIdToImageMapping(ICON_SEEK_BACK, resourceById(com.svartifoss.snfell.common.R.drawable.action_replay_10))
            .addIdToImageMapping(ICON_SEEK_FORWARD, resourceById(com.svartifoss.snfell.common.R.drawable.action_forward_10))
            .build()
    }

    private suspend fun dispatchClick(clickedId: String?) {
        val messageClient = Wearable.getMessageClient(this)
        val nodeClient = Wearable.getNodeClient(this)

        // -10s/+10s carry a signed delta; the phone resolves it against the session's LIVE
        // position (this Tile's snapshot can be up to 30s stale).
        val seekDeltaMs = when (clickedId) {
            ID_SEEK_BACK -> -SEEK_STEP_MS
            ID_SEEK_FORWARD -> SEEK_STEP_MS
            else -> null
        }
        if (seekDeltaMs != null) {
            val payload = java.nio.ByteBuffer.allocate(java.lang.Long.BYTES).putLong(seekDeltaMs).array()
            messageClient.sendMessageToNearestClient(nodeClient, CommPaths.MESSAGE_SEEK_RELATIVE, payload)
            return
        }

        val path = when (clickedId) {
            ID_PLAY_PAUSE -> CommPaths.MESSAGE_TOGGLE_PLAY_PAUSE
            ID_SKIP_NEXT -> CommPaths.MESSAGE_SKIP_NEXT
            ID_SKIP_PREV -> CommPaths.MESSAGE_SKIP_PREVIOUS
            else -> return
        }
        messageClient.sendMessageToNearestClient(nodeClient, path)
    }

    private suspend fun readMusicState(): MusicState? {
        return try {
            val buffer = Wearable.getDataClient(this).getDataItems(
                Uri.parse("wear://*${CommPaths.DATA_MUSIC_STATE}"),
                DataClient.FILTER_LITERAL
            ).await()
            try {
                buffer.firstOrNull()?.let { MusicState.parseFrom(it.data) }
            } finally {
                buffer.release()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildLayout(
        context: Context,
        state: MusicState?,
        flipPlaying: Boolean
    ): LayoutElementBuilders.LayoutElement {
        val hasMusic = state != null && !state.error
        val title = if (hasMusic && state!!.title.isNotBlank()) state.title else context.getString(
            com.svartifoss.snfell.R.string.tile_nothing_playing
        )
        val artist = if (hasMusic) state!!.artist else ""
        val playing = hasMusic && (state!!.playing xor flipPlaying)

        val openAppClickable = Clickable.Builder()
            .setId(ID_OPEN_APP)
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(context.packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .build()

        val textColumn = Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                androidx.wear.protolayout.ModifiersBuilders.Modifiers.Builder()
                    .setClickable(openAppClickable)
                    .build()
            )
            .addContent(
                Text.Builder(context, title)
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(argb(WatchTheme.ON_SURFACE))
                    .setMaxLines(1)
                    .build()
            )
            .apply {
                if (artist.isNotBlank()) {
                    addContent(
                        Text.Builder(context, artist)
                            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                            .setColor(argb(WatchTheme.TEXT_SECONDARY))
                            .setMaxLines(1)
                            .build()
                    )
                }
            }
            .build()

        val controlsRow = Row.Builder()
            .setWidth(wrap())
            .addContent(controlButton(context, ID_SKIP_PREV, ICON_PREV, accent = false))
            .addContent(Spacer.Builder().setWidth(dp(8f)).build())
            .addContent(
                controlButton(
                    context,
                    ID_PLAY_PAUSE,
                    if (playing) ICON_PAUSE else ICON_PLAY,
                    accent = true
                )
            )
            .addContent(Spacer.Builder().setWidth(dp(8f)).build())
            .addContent(controlButton(context, ID_SKIP_NEXT, ICON_NEXT, accent = false))
            .build()

        // Second, slimmer row: -10s/+10s relative seek (long-pressing skip can't be used for
        // scrubbing - the system claims Tile/widget long-presses for its own editor).
        val seekRow = Row.Builder()
            .setWidth(wrap())
            .addContent(smallControlButton(context, ID_SEEK_BACK, ICON_SEEK_BACK))
            .addContent(Spacer.Builder().setWidth(dp(40f)).build())
            .addContent(smallControlButton(context, ID_SEEK_FORWARD, ICON_SEEK_FORWARD))
            .build()

        return androidx.wear.protolayout.LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                androidx.wear.protolayout.ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        androidx.wear.protolayout.ModifiersBuilders.Background.Builder()
                            .setColor(argb(WatchTheme.BACKGROUND_BLACK))
                            .build()
                    )
                    .build()
            )
            .addContent(
                Column.Builder()
                    .setWidth(expand())
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(textColumn)
                    .addContent(Spacer.Builder().setHeight(dp(10f)).build())
                    .addContent(controlsRow)
                    .addContent(Spacer.Builder().setHeight(dp(6f)).build())
                    .addContent(seekRow)
                    .build()
            )
            .build()
    }

    private fun controlButton(
        context: Context,
        clickId: String,
        iconId: String,
        accent: Boolean
    ): Button {
        val clickable = Clickable.Builder()
            .setId(clickId)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        val colors = if (accent) {
            ButtonColors(WatchTheme.ACCENT_DEFAULT, WatchTheme.BACKGROUND_BLACK)
        } else {
            ButtonColors(WatchTheme.SURFACE_DARK, WatchTheme.ON_SURFACE)
        }

        return Button.Builder(context, clickable)
            .setIconContent(iconId)
            .setButtonColors(colors)
            .setSize(if (accent) ButtonDefaults.LARGE_SIZE else ButtonDefaults.DEFAULT_SIZE)
            .build()
    }

    /** The seek row's compact variant - visually secondary to the main transport row. */
    private fun smallControlButton(context: Context, clickId: String, iconId: String): Button {
        val clickable = Clickable.Builder()
            .setId(clickId)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        return Button.Builder(context, clickable)
            .setIconContent(iconId)
            .setButtonColors(ButtonColors(WatchTheme.SURFACE_DARK, WatchTheme.TEXT_SECONDARY))
            .setSize(dp(38f))
            .build()
    }

    private fun resourceById(resId: Int): ResourceBuilders.ImageResource {
        return ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId.Builder()
                    .setResourceId(resId)
                    .build()
            )
            .build()
    }

    companion object {
        // Bump whenever the image-id mappings change, or the renderer keeps its cached set and
        // new icons never load.
        private const val RESOURCES_VERSION = "2"
        private const val REFRESH_INTERVAL_MS = 30_000L

        private const val ID_OPEN_APP = "open_app"
        private const val ID_PLAY_PAUSE = "tile_play_pause"
        private const val ID_SKIP_NEXT = "tile_skip_next"
        private const val ID_SKIP_PREV = "tile_skip_prev"
        private const val ID_SEEK_BACK = "tile_seek_back"
        private const val ID_SEEK_FORWARD = "tile_seek_forward"

        private const val SEEK_STEP_MS = 10_000L

        private const val ICON_PREV = "ic_prev"
        private const val ICON_NEXT = "ic_next"
        private const val ICON_PLAY = "ic_play"
        private const val ICON_PAUSE = "ic_pause"
        private const val ICON_SEEK_BACK = "ic_seek_back"
        private const val ICON_SEEK_FORWARD = "ic_seek_forward"

    }
}
