package com.svartifoss.snfell.watch.tile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
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
import com.svartifoss.snfell.common.AlbumAccentSource
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.SwatchInfo
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.selectPrimaryAccent
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.MainActivity
import com.svartifoss.snfell.watch.util.WatchLanguage
import com.matejdro.wearutils.messages.getByteArrayAsset
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import com.matejdro.wearutils.miscutils.BitmapUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.tasks.await
import timber.log.Timber

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
 *
 * The accent used for the play/pause and open-app buttons is extracted from the current cover
 * (same Palette primary the now-playing faces pick) so the Tile tracks the album instead of a fixed
 * green; it falls back to [WatchTheme.ACCENT_DEFAULT] when no cover is available.
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
        val clickWasSent = try {
            dispatchClick(clickedId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A disconnected phone must not make the renderer discard the whole Tile response.
            Timber.w(e, "Could not dispatch media Tile action")
            false
        }
        val snapshot = readSnapshot()
        val state = snapshot.state

        // A play/pause toggle was only just sent to the phone, which won't have published the
        // resulting state back yet - the DataItem read above still holds the pre-click state.
        // Render the icon optimistically flipped instead of showing the stale one until the
        // next refresh, which would make the button feel like it did nothing.
        val flipPlaying = clickWasSent && clickedId == ID_PLAY_PAUSE

        // Localized once here so every label and content description below resolves in the
        // app language rather than the watch's own system locale - a TileService gets no
        // attachBaseContext. The wrapper shares this package's files, so the preference
        // reads further down are unaffected.
        val layout = buildLayout(
                WatchLanguage.localized(this@MediaTileService),
                state,
                flipPlaying,
                snapshot.accent)

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
            .addIdToImageMapping(ICON_PLAY, resourceById(com.svartifoss.snfell.common.R.drawable.action_play_filled))
            .addIdToImageMapping(ICON_PAUSE, resourceById(com.svartifoss.snfell.common.R.drawable.action_pause_filled))
            .addIdToImageMapping(ICON_SEEK_BACK, resourceById(com.svartifoss.snfell.common.R.drawable.action_replay_10))
            .addIdToImageMapping(ICON_SEEK_FORWARD, resourceById(com.svartifoss.snfell.common.R.drawable.action_forward_10))
            .addIdToImageMapping(
                ICON_OPEN_APP,
                resourceById(com.svartifoss.snfell.R.drawable.ic_complication_picker)
            )
            .build()
    }

    /** Returns true only when the command was accepted by the phone connection. */
    private suspend fun dispatchClick(clickedId: String?): Boolean {
        val messageClient = Wearable.getMessageClient(this)
        val nodeClient = Wearable.getNodeClient(this)

        // -10s/+10s carry a signed delta; the phone resolves it against the session's LIVE
        // position (this Tile's snapshot can be up to one minute stale).
        val seekDeltaMs = when (clickedId) {
            ID_SEEK_BACK -> -SEEK_STEP_MS
            ID_SEEK_FORWARD -> SEEK_STEP_MS
            else -> null
        }
        if (seekDeltaMs != null) {
            val payload = java.nio.ByteBuffer.allocate(java.lang.Long.BYTES).putLong(seekDeltaMs).array()
            return messageClient.sendMessageToNearestClient(
                nodeClient,
                CommPaths.MESSAGE_SEEK_RELATIVE,
                payload
            ) != null
        }

        val path = when (clickedId) {
            ID_PLAY_PAUSE -> CommPaths.MESSAGE_TOGGLE_PLAY_PAUSE
            ID_SKIP_NEXT -> CommPaths.MESSAGE_SKIP_NEXT
            ID_SKIP_PREV -> CommPaths.MESSAGE_SKIP_PREVIOUS
            else -> return false
        }
        return messageClient.sendMessageToNearestClient(nodeClient, path) != null
    }

    /** The current track's state plus the accent extracted from its cover, read in one pass. */
    private class TileSnapshot(val state: MusicState?, val accent: Int?)

    private suspend fun readSnapshot(): TileSnapshot {
        val dataClient = Wearable.getDataClient(this)
        val item = try {
            val buffer = dataClient.getDataItems(
                Uri.parse("wear://*${CommPaths.DATA_MUSIC_STATE}"),
                DataClient.FILTER_LITERAL
            ).await()
            try {
                buffer.firstOrNull()?.freeze()
            } finally {
                buffer.release()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return TileSnapshot(null, null)
        }
        if (item == null) return TileSnapshot(null, null)

        val state = try {
            MusicState.parseFrom(item.data)
        } catch (e: Exception) {
            return TileSnapshot(null, null)
        }

        // The cover rides the same DataItem as an asset (as the complication reads it). Extracting
        // the accent here lets the Tile tint its transport controls with the album colour instead
        // of a fixed green; a missing/unreadable cover falls back to the default accent downstream.
        val accent = try {
            val asset = item.assets[CommPaths.ASSET_ALBUM_ART]
            val bytes = asset?.let { dataClient.getByteArrayAsset(it) }
            extractAccent(BitmapUtils.deserialize(bytes))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return TileSnapshot(state, accent)
    }

    /** Runs Palette on the cover and picks the same primary accent the now-playing faces use. */
    private fun extractAccent(bitmap: Bitmap?): Int? {
        if (bitmap == null) return null
        return try {
            val palette = Palette.from(bitmap).generate()
            val swatchInfos = palette.swatches.map { SwatchInfo(it.rgb, it.population) }
            selectPrimaryAccent(
                palette.getVibrantSwatch()?.let { SwatchInfo(it.rgb, it.population) },
                swatchInfos,
                accentSource()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The user's accent-source choice, resolved through the active face like the player does.
     *
     * The tile reads no other preference - it renders its own layout rather than a face - but this
     * one decides which colour the cover *is*, so ignoring it would put the tile on a different
     * accent from the watch face showing the same track.
     */
    private fun accentSource(): AlbumAccentSource {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return AlbumAccentSource.fromPreference(
            FaceScopedPreferences.getString(
                prefs,
                MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE,
                ThemeAppearance.resolve(prefs)
            )
        )
    }

    private fun buildLayout(
        context: Context,
        state: MusicState?,
        flipPlaying: Boolean,
        albumAccent: Int?
    ): LayoutElementBuilders.LayoutElement {
        val accent = resolveTileAccent(albumAccent)
        val onAccent = onAccentColor(accent)
        // A controller-less state is still published as a valid proto with all media fields
        // empty. Treating every non-error proto as an active session left a row of dead controls
        // below "Nothing playing" after playback ended.
        val hasMusic = state != null && !state.error && (
            state.title.isNotBlank() ||
                state.artist.isNotBlank() ||
                state.playing ||
                state.durationMs > 0L
            )
        val title = if (hasMusic && state!!.title.isNotBlank()) state.title else context.getString(
            com.svartifoss.snfell.R.string.tile_nothing_playing
        )
        val artist = if (hasMusic) {
            state!!.artist
        } else {
            context.getString(com.svartifoss.snfell.R.string.tile_open_app)
        }
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
            .addContent(controlButton(context, ID_SKIP_PREV, ICON_PREV, accent = false, accentColor = accent, onAccentColor = onAccent))
            .addContent(Spacer.Builder().setWidth(dp(8f)).build())
            .addContent(
                controlButton(
                    context,
                    ID_PLAY_PAUSE,
                    if (playing) ICON_PAUSE else ICON_PLAY,
                    accent = true,
                    accentColor = accent,
                    onAccentColor = onAccent
                )
            )
            .addContent(Spacer.Builder().setWidth(dp(8f)).build())
            .addContent(controlButton(context, ID_SKIP_NEXT, ICON_NEXT, accent = false, accentColor = accent, onAccentColor = onAccent))
            .build()

        // Second, slimmer row: -10s/+10s relative seek (long-pressing skip can't be used for
        // scrubbing - the system claims Tile/widget long-presses for its own editor).
        val seekRow = Row.Builder()
            .setWidth(wrap())
            .addContent(smallControlButton(context, ID_SEEK_BACK, ICON_SEEK_BACK))
            .addContent(Spacer.Builder().setWidth(dp(40f)).build())
            .addContent(smallControlButton(context, ID_SEEK_FORWARD, ICON_SEEK_FORWARD))
            .build()

        val openAppButton = Button.Builder(context, openAppClickable)
            .setIconContent(ICON_OPEN_APP)
            .setContentDescription(context.getString(com.svartifoss.snfell.R.string.tile_open_app))
            .setButtonColors(ButtonColors(accent, onAccent))
            .setSize(ButtonDefaults.LARGE_SIZE)
            .build()

        val contentColumn = Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(textColumn)
            .addContent(Spacer.Builder().setHeight(dp(10f)).build())
            .apply {
                if (hasMusic) {
                    addContent(controlsRow)
                    if (state!!.seekable && state.durationMs > 0L) {
                        addContent(Spacer.Builder().setHeight(dp(6f)).build())
                        addContent(seekRow)
                    }
                } else {
                    addContent(openAppButton)
                }
            }
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
                contentColumn
            )
            .build()
    }

    private fun controlButton(
        context: Context,
        clickId: String,
        iconId: String,
        accent: Boolean,
        accentColor: Int,
        onAccentColor: Int
    ): Button {
        val clickable = Clickable.Builder()
            .setId(clickId)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        val colors = if (accent) {
            ButtonColors(accentColor, onAccentColor)
        } else {
            ButtonColors(WatchTheme.SURFACE_DARK, WatchTheme.ON_SURFACE)
        }

        return Button.Builder(context, clickable)
            .setIconContent(iconId)
            .setContentDescription(
                context.getString(
                    when (clickId) {
                        ID_SKIP_PREV -> com.svartifoss.snfell.R.string.action_name_skip_prev
                        ID_SKIP_NEXT -> com.svartifoss.snfell.R.string.action_name_skip_next
                        ID_PLAY_PAUSE -> if (iconId == ICON_PAUSE) {
                            com.svartifoss.snfell.R.string.action_name_pause
                        } else {
                            com.svartifoss.snfell.R.string.action_name_play
                        }
                        else -> com.svartifoss.snfell.R.string.action_name_play_pause
                    }
                )
            )
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
            .setContentDescription(
                context.getString(
                    if (clickId == ID_SEEK_BACK) {
                        com.svartifoss.snfell.R.string.tile_rewind_10
                    } else {
                        com.svartifoss.snfell.R.string.tile_forward_10
                    }
                )
            )
            .setButtonColors(ButtonColors(WatchTheme.SURFACE_DARK, WatchTheme.TEXT_SECONDARY))
            // Keep the secondary visual weight, but retain the platform's 48dp touch minimum.
            .setSize(dp(48f))
            .build()
    }

    /**
     * Resolves the fill for the accent controls: the album accent when available, else the default
     * green. Very dark or washed-out album colours are lifted so the filled button never sinks into
     * the black Tile background.
     */
    private fun resolveTileAccent(raw: Int?): Int {
        val base = raw ?: return WatchTheme.ACCENT_DEFAULT
        val hsv = FloatArray(3)
        Color.colorToHSV(base, hsv)
        if (hsv[2] < 0.55f) hsv[2] = 0.55f
        if (hsv[1] > 0.9f) hsv[1] = 0.9f
        return Color.HSVToColor(hsv)
    }

    /** Picks the icon/label tint (black or white) that reads best on [accent]. */
    private fun onAccentColor(accent: Int): Int {
        val luminance =
            (0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent)) / 255.0
        return if (luminance > 0.6) WatchTheme.BACKGROUND_BLACK else WatchTheme.COLOR_WHITE
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
        private const val RESOURCES_VERSION = "4"
        // Android may throttle Tile requests made more often than once a minute. State changes
        // still request an immediate refresh through MusicStateListenerService.
        private const val REFRESH_INTERVAL_MS = 60_000L

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
        private const val ICON_OPEN_APP = "ic_open_app"

    }
}
