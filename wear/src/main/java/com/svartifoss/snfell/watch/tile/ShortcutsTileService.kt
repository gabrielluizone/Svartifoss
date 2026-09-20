package com.svartifoss.snfell.watch.tile

import android.content.Context
import android.net.Uri
import androidx.wear.protolayout.ActionBuilders
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
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.proto.CustomList
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.MainActivity
import com.svartifoss.snfell.watch.util.WatchLanguage
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
 * A second, browse-style Tile that lists the user's saved streaming shortcuts (playlists/albums the
 * phone can open), so they are one tap away without opening the app - the watch equivalent of the
 * phone's shortcut list. It is a sibling of [MediaTileService]: the user adds whichever Tile(s) they
 * want to the carousel.
 *
 * The shortcuts already ride the Data Layer as the [CommPaths.DATA_STREAMING_SHORTCUTS] DataItem the
 * phone publishes ([com.svartifoss.snfell.music.PlaylistShortcutStorage]); each entry's `entryId` is
 * already the launch target in the `targetPackage|uri` remote-URI form. Tapping a chip launches the
 * tiny [ShortcutLaunchActivity], which opens the URI on the phone (via the Wear phone bridge, since
 * a background phone service can't start an Activity) and asks the phone to play it - the exact same
 * two-step flow the now-playing menu uses (see MusicViewModel.executeItemFromCustomMenu).
 */
class ShortcutsTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        // The shortcut list and current album colour are independent Data Layer reads.
        val (shortcuts, albumAccent) = coroutineScope {
            val shortcutsRequest = async { readShortcuts() }
            val accentRequest = async {
                TileAlbumAccent.readCurrentOrLast(this@ShortcutsTileService)
            }
            shortcutsRequest.await() to accentRequest.await()
        }
        // Localized once here so every label built below resolves in the app language rather
        // than the watch's own system locale - a TileService gets no attachBaseContext.
        val layout = buildLayout(
                WatchLanguage.localized(this@ShortcutsTileService),
                shortcuts,
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
            .addIdToImageMapping(
                ICON_SHORTCUT,
                resourceById(com.svartifoss.snfell.common.R.drawable.action_open_playlist)
            )
            .addIdToImageMapping(
                ICON_OPEN_APP,
                resourceById(com.svartifoss.snfell.R.drawable.ic_complication_picker)
            )
            .build()
    }

    /** A shortcut ready to render: its display name and the launch target (remote-URI form). */
    private class ShortcutEntry(val title: String, val subtitle: String, val entryId: String)

    private suspend fun readShortcuts(): List<ShortcutEntry> {
        return try {
            val buffer = Wearable.getDataClient(this).getDataItems(
                Uri.parse("wear://*${CommPaths.DATA_STREAMING_SHORTCUTS}"),
                DataClient.FILTER_LITERAL
            ).await()
            val list = try {
                buffer.firstOrNull()?.let { CustomList.parseFrom(it.data) }
            } finally {
                buffer.release()
            }
            list?.actionsList.orEmpty()
                // The phone publishes a single SPECIAL_ITEM_ERROR placeholder when the library is
                // empty; that is not a launchable shortcut.
                .filter { it.entryId.isNotBlank() && it.entryId != CustomLists.SPECIAL_ITEM_ERROR }
                .map { ShortcutEntry(it.entryTitle, it.entrySubtitle, it.entryId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not read streaming shortcuts for the Tile")
            emptyList()
        }
    }

    private fun buildLayout(
        context: Context,
        shortcuts: List<ShortcutEntry>,
        deviceParameters: DeviceParameters,
        albumAccent: Int?
    ): LayoutElementBuilders.LayoutElement {
        val accent = TileAlbumAccent.displayColor(albumAccent)
        val column = Column.Builder()
            .setWidth(dp(shortcutChipWidth(deviceParameters.screenWidthDp)))
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(context, context.getString(com.svartifoss.snfell.R.string.shortcuts_tile_title))
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(argb(WatchTheme.ON_SURFACE))
                    .setMaxLines(1)
                    .build()
            )
            .addContent(Spacer.Builder().setHeight(dp(8f)).build())

        if (shortcuts.isEmpty()) {
            column.addContent(
                Text.Builder(context, context.getString(com.svartifoss.snfell.R.string.shortcuts_tile_empty))
                    .setTypography(Typography.TYPOGRAPHY_BODY2)
                    .setColor(argb(WatchTheme.ON_SURFACE))
                    .setMaxLines(2)
                    .build()
            )
            column.addContent(Spacer.Builder().setHeight(dp(10f)).build())
            column.addContent(
                openAppChip(
                    context,
                    deviceParameters,
                    context.getString(com.svartifoss.snfell.R.string.tile_open_app),
                    accent
                )
            )
        } else {
            // Two generous, two-line rows remain readable on the smallest supported round screens.
            // A compact final action makes the rest explicitly reachable instead of squeezing a
            // third or fourth full-size Chip beyond the circular safe area.
            val truncated = shortcuts.size > MAX_DIRECT_SHORTCUTS
            val visible = shortcuts.take(MAX_DIRECT_SHORTCUTS)
            visible.forEachIndexed { index, shortcut ->
                if (index > 0) column.addContent(Spacer.Builder().setHeight(dp(5f)).build())
                column.addContent(
                    shortcutChip(
                        context,
                        shortcut,
                        deviceParameters,
                        shortcutChipWidth(deviceParameters.screenWidthDp)
                    )
                )
            }
            if (truncated) {
                column.addContent(Spacer.Builder().setHeight(dp(7f)).build())
                column.addContent(
                    openAppChip(
                        context,
                        deviceParameters,
                        context.getString(com.svartifoss.snfell.R.string.shortcuts_tile_more),
                        accent
                    )
                )
            }
        }

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
            .addContent(column.build())
            .build()
    }

    private fun shortcutChip(
        context: Context,
        shortcut: ShortcutEntry,
        deviceParameters: DeviceParameters,
        widthDp: Float
    ): Chip {
        val clickable = Clickable.Builder()
            .setId(shortcut.entryId)
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(context.packageName)
                            .setClassName(ShortcutLaunchActivity::class.java.name)
                            .addKeyToExtraMapping(
                                ShortcutLaunchActivity.EXTRA_ENTRY_ID,
                                ActionBuilders.AndroidStringExtra.Builder()
                                    .setValue(shortcut.entryId)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val builder = Chip.Builder(context, clickable, deviceParameters)
            .setPrimaryLabelContent(shortcut.title)
            .setIconContent(ICON_SHORTCUT)
            .setWidth(widthDp)
            .setContentDescription(
                listOf(shortcut.title, shortcut.subtitle)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
            )
            .setChipColors(
                ChipColors(
                    WatchTheme.SURFACE_DARK,
                    WatchTheme.ACCENT_DEFAULT,
                    WatchTheme.ON_SURFACE,
                    WatchTheme.TEXT_SECONDARY
                )
            )
        if (shortcut.subtitle.isNotBlank()) {
            builder.setSecondaryLabelContent(shortcut.subtitle)
        }
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

        return CompactChip.Builder(context, label, clickable, deviceParameters)
            .setIconContent(ICON_OPEN_APP)
            .setContentDescription(label)
            .setChipColors(
                ChipColors(
                    accent,
                    TileAlbumAccent.contentColor(accent),
                    TileAlbumAccent.contentColor(accent),
                    TileAlbumAccent.contentColor(accent)
                )
            )
            .build()
    }

    /** Keeps full-width chips inside the usable chord of a round display. */
    private fun shortcutChipWidth(screenWidthDp: Int): Float =
        (screenWidthDp * CHIP_WIDTH_FRACTION).coerceIn(MIN_CHIP_WIDTH_DP, MAX_CHIP_WIDTH_DP)

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
        private const val RESOURCES_VERSION = "1"
        // Shortcuts change rarely; the Tile re-reads on every render request. A generous freshness
        // interval keeps it from going stale if it stays on screen after an edit on the phone.
        private const val REFRESH_INTERVAL_MS = 60_000L

        // ProtoLayout Tiles don't scroll. Two full rows plus a compact overflow action stay inside
        // a 192dp round screen; three or four full Chips do not.
        private const val MAX_DIRECT_SHORTCUTS = 2
        private const val CHIP_WIDTH_FRACTION = 0.82f
        private const val MIN_CHIP_WIDTH_DP = 148f
        private const val MAX_CHIP_WIDTH_DP = 172f

        private const val ID_OPEN_APP = "shortcuts_open_app"
        private const val ICON_SHORTCUT = "ic_shortcut"
        private const val ICON_OPEN_APP = "ic_open_app"
    }
}
