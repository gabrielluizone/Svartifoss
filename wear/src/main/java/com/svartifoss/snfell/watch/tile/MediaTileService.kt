package com.svartifoss.snfell.watch.tile

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.em
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
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
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CompactChip
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
 * buttons plus a slimmer volume row, without opening the app. Tapping the text opens
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
 * green; an artwork-free idle state keeps the last album colour, and only a fresh install with no
 * cover history falls back to [WatchTheme.ACCENT_DEFAULT].
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
                snapshot.accent,
                requestParams.deviceConfiguration)

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
            .addIdToImageMapping(ICON_VOLUME_DOWN, resourceById(com.svartifoss.snfell.common.R.drawable.action_volume_down))
            .addIdToImageMapping(ICON_VOLUME_UP, resourceById(com.svartifoss.snfell.common.R.drawable.action_volume_up))
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

        // Volume carries only a direction. The phone applies one native session step to its LIVE
        // controller, so repeated taps never overwrite one another with this Tile's stale volume.
        val volumeDirection = when (clickedId) {
            ID_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            ID_VOLUME_UP -> AudioManager.ADJUST_RAISE
            else -> null
        }
        if (volumeDirection != null) {
            val payload = java.nio.ByteBuffer.allocate(Int.SIZE_BYTES).putInt(volumeDirection).array()
            return messageClient.sendMessageToNearestClient(
                nodeClient,
                CommPaths.MESSAGE_ADJUST_VOLUME,
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
            return TileSnapshot(null, TileAlbumAccent.lastKnown(this))
        }
        if (item == null) return TileSnapshot(null, TileAlbumAccent.lastKnown(this))

        val state = try {
            MusicState.parseFrom(item.data)
        } catch (e: Exception) {
            return TileSnapshot(null, TileAlbumAccent.lastKnown(this))
        }

        // The cover rides the same DataItem as an asset (as the complication reads it). Extracting
        // the accent here lets the Tile tint its transport controls with the album colour instead
        // of a fixed green; a missing/unreadable cover retains the last successful album accent.
        val accent = try {
            TileAlbumAccent.fromAssetOrLast(this, dataClient, item.assets[CommPaths.ASSET_ALBUM_ART])
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TileAlbumAccent.lastKnown(this)
        }
        return TileSnapshot(state, accent)
    }

    private fun buildLayout(
        context: Context,
        state: MusicState?,
        flipPlaying: Boolean,
        albumAccent: Int?,
        deviceParameters: androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val accent = TileAlbumAccent.displayColor(albumAccent)
        val onAccent = TileAlbumAccent.contentColor(accent)
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
            .apply {
                if (hasMusic) {
                    addContent(
                        Text.Builder(
                            context,
                            context.getString(com.svartifoss.snfell.R.string.queue_now_playing)
                        )
                            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                            .setColor(argb(accent))
                            .setMaxLines(1)
                            .build()
                    )
                    addContent(Spacer.Builder().setHeight(dp(2f)).build())
                }
                addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(title)
                        .setFontStyle(
                            LayoutElementBuilders.FontStyle.Builder()
                                .setSize(sp(16f))
                                .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                                .setVariant(LayoutElementBuilders.FONT_VARIANT_TITLE)
                                .setLetterSpacing(em(0.01f))
                                // Tiles render outside the app process, so @font resources cannot
                                // be loaded. Prefer the system Google Sans family where exposed,
                                // with the officially supported Roboto family as a safe fallback.
                                .setPreferredFontFamilies(
                                    GOOGLE_SANS_FONT,
                                    LayoutElementBuilders.FontStyle.ROBOTO_FONT
                                )
                                .setColor(argb(WatchTheme.ON_SURFACE))
                                .build()
                        )
                        .setMaxLines(1)
                        .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                        .setLineHeight(sp(20f))
                        .build()
                )
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
            .addContent(Spacer.Builder().setWidth(dp(6f)).build())
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
            .addContent(Spacer.Builder().setWidth(dp(6f)).build())
            .addContent(controlButton(context, ID_SKIP_NEXT, ICON_NEXT, accent = false, accentColor = accent, onAccentColor = onAccent))
            .build()

        // Volume is available even when the active session is not seekable, and is more useful
        // from a glanceable surface than another pair of transport controls.
        val volumeRow = Row.Builder()
            .setWidth(wrap())
            .addContent(
                volumeChip(
                    context,
                    deviceParameters,
                    ID_VOLUME_DOWN,
                    ICON_VOLUME_DOWN
                )
            )
            .addContent(Spacer.Builder().setWidth(dp(18f)).build())
            .addContent(
                volumeChip(
                    context,
                    deviceParameters,
                    ID_VOLUME_UP,
                    ICON_VOLUME_UP
                )
            )
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
            .addContent(Spacer.Builder().setHeight(dp(if (hasMusic) 8f else 12f)).build())
            .apply {
                if (hasMusic) {
                    addContent(controlsRow)
                    addContent(Spacer.Builder().setHeight(dp(5f)).build())
                    addContent(volumeRow)
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

    /** The volume row's compact variant - visually secondary to the main transport row. */
    private fun volumeChip(
        context: Context,
        deviceParameters: androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters,
        clickId: String,
        iconId: String
    ): CompactChip {
        val clickable = Clickable.Builder()
            .setId(clickId)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        return CompactChip.Builder(context, clickable, deviceParameters)
            .setIconContent(iconId)
            .setContentDescription(
                context.getString(
                    if (clickId == ID_VOLUME_DOWN) {
                        com.svartifoss.snfell.R.string.action_name_volume_down
                    } else {
                        com.svartifoss.snfell.R.string.action_name_volume_up
                    }
                )
            )
            .setChipColors(
                ChipColors(
                    WatchTheme.SURFACE_DARK,
                    WatchTheme.TEXT_SECONDARY,
                    WatchTheme.ON_SURFACE,
                    WatchTheme.TEXT_SECONDARY
                )
            )
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
        private const val RESOURCES_VERSION = "5"
        // Android may throttle Tile requests made more often than once a minute. State changes
        // still request an immediate refresh through MusicStateListenerService.
        private const val REFRESH_INTERVAL_MS = 60_000L

        private const val ID_OPEN_APP = "open_app"
        private const val ID_PLAY_PAUSE = "tile_play_pause"
        private const val ID_SKIP_NEXT = "tile_skip_next"
        private const val ID_SKIP_PREV = "tile_skip_prev"
        private const val ID_VOLUME_DOWN = "tile_volume_down"
        private const val ID_VOLUME_UP = "tile_volume_up"

        private const val ICON_PREV = "ic_prev"
        private const val ICON_NEXT = "ic_next"
        private const val ICON_PLAY = "ic_play"
        private const val ICON_PAUSE = "ic_pause"
        private const val ICON_VOLUME_DOWN = "ic_volume_down"
        private const val ICON_VOLUME_UP = "ic_volume_up"
        private const val ICON_OPEN_APP = "ic_open_app"

        private const val GOOGLE_SANS_FONT = "google-sans"

    }
}
