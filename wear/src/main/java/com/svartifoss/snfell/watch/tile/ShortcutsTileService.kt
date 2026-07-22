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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        val shortcuts = readShortcuts()
        val layout = buildLayout(this@ShortcutsTileService, shortcuts, requestParams.deviceConfiguration)

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
        deviceParameters: DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val column = Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(context, context.getString(com.svartifoss.snfell.R.string.shortcuts_tile_title))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(WatchTheme.TEXT_SECONDARY))
                    .setMaxLines(1)
                    .build()
            )
            .addContent(Spacer.Builder().setHeight(dp(6f)).build())

        if (shortcuts.isEmpty()) {
            column.addContent(
                Text.Builder(context, context.getString(com.svartifoss.snfell.R.string.shortcuts_tile_empty))
                    .setTypography(Typography.TYPOGRAPHY_BODY2)
                    .setColor(argb(WatchTheme.ON_SURFACE))
                    .setMaxLines(2)
                    .build()
            )
            column.addContent(Spacer.Builder().setHeight(dp(8f)).build())
            column.addContent(openAppChip(context, deviceParameters))
        } else {
            // Keep the last slot for "More in app" when the library doesn't fully fit, so nothing
            // is silently unreachable from the Tile.
            val truncated = shortcuts.size > MAX_VISIBLE
            val visible = shortcuts.take(if (truncated) MAX_VISIBLE - 1 else MAX_VISIBLE)
            visible.forEachIndexed { index, shortcut ->
                if (index > 0) column.addContent(Spacer.Builder().setHeight(dp(6f)).build())
                column.addContent(shortcutChip(context, shortcut, deviceParameters))
            }
            if (truncated) {
                column.addContent(Spacer.Builder().setHeight(dp(6f)).build())
                column.addContent(openAppChip(context, deviceParameters))
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
        deviceParameters: DeviceParameters
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
            .setWidth(expand())
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

    private fun openAppChip(context: Context, deviceParameters: DeviceParameters): Chip {
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

        return Chip.Builder(context, clickable, deviceParameters)
            .setPrimaryLabelContent(context.getString(com.svartifoss.snfell.R.string.shortcuts_tile_more))
            .setIconContent(ICON_OPEN_APP)
            .setWidth(expand())
            .setChipColors(
                ChipColors(
                    WatchTheme.SURFACE_DARK,
                    WatchTheme.ACCENT_DEFAULT,
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
        private const val RESOURCES_VERSION = "1"
        // Shortcuts change rarely; the Tile re-reads on every render request. A generous freshness
        // interval keeps it from going stale if it stays on screen after an edit on the phone.
        private const val REFRESH_INTERVAL_MS = 60_000L

        // ProtoLayout Tiles don't scroll - cap what we render so the column always fits the screen.
        private const val MAX_VISIBLE = 4

        private const val ID_OPEN_APP = "shortcuts_open_app"
        private const val ICON_SHORTCUT = "ic_shortcut"
        private const val ICON_OPEN_APP = "ic_open_app"
    }
}
