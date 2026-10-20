package com.svartifoss.snfell.watch.tile

import android.content.Context
import android.net.Uri
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Chip
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
import com.svartifoss.snfell.proto.CustomList
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.util.WatchLanguage
import com.svartifoss.snfell.watch.view.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * "Recently played": the last tracks the phone played, one tap from starting again - a third Tile
 * beside [MediaTileService] and [ShortcutsTileService], built from the app's own history.
 *
 * Its data is [CommPaths.DATA_RECENT_TRACKS], the few newest entries of the phone's track history,
 * which already backs the queue's "Recently played" fallback. A history entry is only an artist and
 * a title - no media id survives from the app that played it - so a tap plays it the way that
 * fallback does: [RecentTrackLaunchActivity] asks the phone to search for it and play the result
 * (`MESSAGE_PLAY_FROM_SEARCH`). That lands on the same track for the app it was played in, and is
 * the honest best for anything else.
 *
 * Laid out like the shortcuts Tile and for the same reason: Tiles do not scroll, and two full rows
 * plus a compact overflow are what fits a 192dp round screen.
 */
class RecentTracksTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(
            requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val (tracks, albumAccent) = coroutineScope {
            val tracksRequest = async { readTracks() }
            val accentRequest = async { TileAlbumAccent.readCurrentOrLast(this@RecentTracksTileService) }
            tracksRequest.await() to accentRequest.await()
        }
        val layout = buildLayout(
                WatchLanguage.localized(this@RecentTracksTileService),
                tracks,
                requestParams.deviceConfiguration,
                albumAccent)
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
                .addIdToImageMapping(ICON_TRACK,
                        resourceById(com.svartifoss.snfell.common.R.drawable.action_play_filled))
                .addIdToImageMapping(ICON_OPEN_APP,
                        resourceById(com.svartifoss.snfell.R.drawable.ic_complication_picker))
                .build()
    }

    private class Track(val title: String, val artist: String)

    private suspend fun readTracks(): List<Track> = try {
        val buffer = Wearable.getDataClient(this).getDataItems(
                Uri.parse("wear://*${CommPaths.DATA_RECENT_TRACKS}"),
                DataClient.FILTER_LITERAL
        ).await()
        val list = try {
            buffer.firstOrNull()?.let { CustomList.parseFrom(it.data) }
        } finally {
            buffer.release()
        }
        list?.actionsList.orEmpty()
                .filter { it.entryTitle.isNotBlank() }
                .map { Track(it.entryTitle, it.entrySubtitle) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Could not read recent tracks for the Tile")
        emptyList()
    }

    private fun buildLayout(
            context: Context,
            tracks: List<Track>,
            deviceParameters: DeviceParameters,
            albumAccent: Int?
    ): LayoutElementBuilders.LayoutElement {
        val accent = TileAlbumAccent.displayColor(albumAccent)
        val width = chipWidth(deviceParameters.screenWidthDp)
        val column = Column.Builder()
                .setWidth(dp(width))
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .addContent(Text.Builder(context,
                        context.getString(com.svartifoss.snfell.R.string.recent_tile_title))
                        .setTypography(Typography.TYPOGRAPHY_TITLE3)
                        .setColor(argb(WatchTheme.ON_SURFACE))
                        .setMaxLines(1)
                        .build())
                .addContent(Spacer.Builder().setHeight(dp(8f)).build())

        if (tracks.isEmpty()) {
            column.addContent(Text.Builder(context,
                    context.getString(com.svartifoss.snfell.R.string.recent_tile_empty))
                    .setTypography(Typography.TYPOGRAPHY_BODY2)
                    .setColor(argb(WatchTheme.ON_SURFACE))
                    .setMaxLines(2)
                    .build())
            column.addContent(Spacer.Builder().setHeight(dp(10f)).build())
            column.addContent(openAppChip(context, deviceParameters,
                    context.getString(com.svartifoss.snfell.R.string.tile_open_app), accent))
        } else {
            tracks.take(MAX_DIRECT_TRACKS).forEachIndexed { index, track ->
                if (index > 0) column.addContent(Spacer.Builder().setHeight(dp(5f)).build())
                column.addContent(trackChip(context, track, index, deviceParameters, width))
            }
            if (tracks.size > MAX_DIRECT_TRACKS) {
                column.addContent(Spacer.Builder().setHeight(dp(7f)).build())
                column.addContent(openAppChip(context, deviceParameters,
                        context.getString(com.svartifoss.snfell.R.string.shortcuts_tile_more), accent))
            }
        }

        return LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .setModifiers(androidx.wear.protolayout.ModifiersBuilders.Modifiers.Builder()
                        .setBackground(androidx.wear.protolayout.ModifiersBuilders.Background.Builder()
                                .setColor(argb(WatchTheme.BACKGROUND_BLACK))
                                .build())
                        .build())
                .addContent(column.build())
                .build()
    }

    private fun trackChip(
            context: Context,
            track: Track,
            index: Int,
            deviceParameters: DeviceParameters,
            widthDp: Float
    ): Chip {
        val clickable = Clickable.Builder()
                .setId("recent_$index")
                .setOnClick(ActionBuilders.LaunchAction.Builder()
                        .setAndroidActivity(ActionBuilders.AndroidActivity.Builder()
                                .setPackageName(context.packageName)
                                .setClassName(RecentTrackLaunchActivity::class.java.name)
                                .addKeyToExtraMapping(RecentTrackLaunchActivity.EXTRA_TITLE,
                                        ActionBuilders.AndroidStringExtra.Builder().setValue(track.title).build())
                                .addKeyToExtraMapping(RecentTrackLaunchActivity.EXTRA_ARTIST,
                                        ActionBuilders.AndroidStringExtra.Builder().setValue(track.artist).build())
                                .build())
                        .build())
                .build()
        val builder = Chip.Builder(context, clickable, deviceParameters)
                .setPrimaryLabelContent(track.title)
                .setIconContent(ICON_TRACK)
                .setWidth(widthDp)
                .setContentDescription(context.getString(
                        com.svartifoss.snfell.R.string.recent_tile_play_description,
                        listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(", ")))
                .setChipColors(ChipColors(
                        WatchTheme.SURFACE_DARK,
                        WatchTheme.ACCENT_DEFAULT,
                        WatchTheme.ON_SURFACE,
                        WatchTheme.TEXT_SECONDARY))
        if (track.artist.isNotBlank()) builder.setSecondaryLabelContent(track.artist)
        return builder.build()
    }

    private fun openAppChip(
            context: Context,
            deviceParameters: DeviceParameters,
            label: String,
            accent: Int
    ): CompactChip {
        val clickable = Clickable.Builder()
                .setId(ID_OPEN_APP)
                .setOnClick(ActionBuilders.LaunchAction.Builder()
                        .setAndroidActivity(ActionBuilders.AndroidActivity.Builder()
                                .setPackageName(context.packageName)
                                .setClassName(MainActivity::class.java.name)
                                .build())
                        .build())
                .build()
        val onAccent = TileAlbumAccent.contentColor(accent)
        return CompactChip.Builder(context, label, clickable, deviceParameters)
                .setIconContent(ICON_OPEN_APP)
                .setContentDescription(label)
                .setChipColors(ChipColors(accent, onAccent, onAccent, onAccent))
                .build()
    }

    private fun chipWidth(screenWidthDp: Int): Float =
            (screenWidthDp * CHIP_WIDTH_FRACTION).coerceIn(MIN_CHIP_WIDTH_DP, MAX_CHIP_WIDTH_DP)

    private fun resourceById(resId: Int): ResourceBuilders.ImageResource =
            ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(resId)
                            .build())
                    .build()

    private companion object {
        const val RESOURCES_VERSION = "1"
        /** Refreshed by the state listener when the list changes; this is only the backstop. */
        const val REFRESH_INTERVAL_MS = 15 * 60_000L

        /** The shortcuts Tile's numbers, for the same 192dp round screen. */
        const val MAX_DIRECT_TRACKS = 2
        const val CHIP_WIDTH_FRACTION = 0.82f
        const val MIN_CHIP_WIDTH_DP = 148f
        const val MAX_CHIP_WIDTH_DP = 172f

        const val ID_OPEN_APP = "recent_open_app"
        const val ICON_TRACK = "ic_track"
        const val ICON_OPEN_APP = "ic_open_app"
    }
}
