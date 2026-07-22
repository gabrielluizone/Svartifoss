package com.svartifoss.snfell.config.actionlist

import android.content.Context
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.FaceScopedPreferences
import androidx.preference.PreferenceManager
import android.net.Uri
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.auto.factory.AutoFactory
import com.google.auto.factory.Provided
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.PlayPlaylistShortcutAction
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.actions.StandardIcons
import com.svartifoss.snfell.config.CustomIconStorage
import com.svartifoss.snfell.config.WatchInfoProvider
import com.svartifoss.snfell.config.buttons.ConfigConstants
import com.svartifoss.snfell.music.ShortcutArtworkFetcher
import com.svartifoss.snfell.proto.WatchList
import com.svartifoss.snfell.util.launchWithPlayServicesErrorHandling
import com.matejdro.wearutils.miscutils.BitmapUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.tasks.await

@AutoFactory
class ActionListTransmitter(actionList: ActionList,
                            @Provided private val customIconStorage: CustomIconStorage,
                            @Provided private val context: Context,
                            @Provided private val watchInfoProvider: WatchInfoProvider) {

    private val dataClient = Wearable.getDataClient(context)

    init {
        resendIfNeeded(actionList)
    }

    private fun resendIfNeeded(actionList: ActionList) {
        GlobalScope.launchWithPlayServicesErrorHandling(context) {
            val dataOnWatch = dataClient.getDataItems(Uri.parse("wear://*${CommPaths.DATA_LIST_ITEMS}")).await()

            val missingMetadata = dataOnWatch.any { item ->
                try {
                    WatchList.parseFrom(item.data).actionsList.any { action ->
                        !action.hasIconTintable() ||
                                (action.actionKey == PlayPlaylistShortcutAction::class.java.canonicalName &&
                                        (!action.hasRemoteUri() || !action.hasIconIsCoverArt()))
                    }
                } catch (_: Exception) {
                    true
                }
            }
            if (!dataOnWatch.any() || missingMetadata) {
                sendConfigToWatch(actionList.actions)
            }

            dataOnWatch.release()
        }
    }


    suspend fun sendConfigToWatch(actions: List<PhoneAction>) {
        val density = watchInfoProvider.value?.watchInfo?.displayDensity ?: 1f
        val targetIconSize = (ConfigConstants.MENU_ICON_SIZE_DP * density).toInt()
        val coverIconSizePx = (watchInfoProvider.value?.watchInfo?.displayWidth?.takeIf { it > 0 }
                ?: DEFAULT_COVER_ICON_SIZE_PX).coerceAtMost(ShortcutArtworkFetcher.MAX_THUMBNAIL_PX)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val appearanceContext = ThemeAppearance.resolve(prefs)
        val coverArtwork = FaceScopedPreferences.getString(
                prefs, MiscPreferences.WEAR_QUEUE_STYLE, appearanceContext
        ) in MiscPreferences.COVER_LIST_STYLES
        val shortcutCoverEnabled = FaceScopedPreferences.getBoolean(
                prefs, MiscPreferences.WEAR_QUICK_PANEL_SHORTCUT_COVER, appearanceContext
        )

        val putDataRequest = PutDataRequest.create(CommPaths.DATA_LIST_ITEMS)
        val protoBuilder = WatchList.newBuilder()

        for ((index, action) in actions.withIndex()) {
            val actionProto = WatchList.WatchListAction.newBuilder()
            actionProto.actionTitle = action.title
            actionProto.actionKey = action.javaClass.canonicalName
            actionProto.iconTintable = action.iconTintable
            actionProto.iconIsCoverArt = action.isCoverArt
            action.remoteUri?.takeIf(String::isNotBlank)?.let { actionProto.remoteUri = it }
            protoBuilder.addActions(actionProto.build())

            if (action.customIconUri == null &&
                    StandardIcons.hasIcon(actionProto.actionKey)) {
                // We already have vector icon of this on the watch.
                // No need to waste bluetooth bandwith by transferring it

                continue
            }

            var icon = BitmapUtils.getBitmap(customIconStorage[action])
            // 40dp is sized for the menu's circular thumbnail slot. The Cover pill style stretches
            // real cover art (not just any non-tintable icon - a plain app-launcher icon stays
            // small) across the whole row, where that is visibly soft - so only entries the watch
            // might actually fill a pill with are sent larger, and only when the feature that does
            // so is on. Sized to the paired watch's actual screen width rather than a fixed guess,
            // clamped to what ShortcutArtworkFetcher ever caches (shrinkPreservingRatio only
            // shrinks, so sending more than that would just waste bandwidth).
            val iconSize = if (coverArtwork && shortcutCoverEnabled && action.isCoverArt) {
                coverIconSizePx
            } else {
                targetIconSize
            }
            icon = BitmapUtils.shrinkPreservingRatio(icon, iconSize, iconSize, true)

            val iconData = BitmapUtils.serialize(icon)
            val assetKey = CommPaths.ASSET_BUTTON_ICON_PREFIX + index
            if (iconData != null) {
                putDataRequest.putAsset(assetKey, Asset.createFromBytes(iconData))
            }
        }

        putDataRequest.data = protoBuilder.build().toByteArray()

        // Urgent: otherwise the Data Layer batches this and the action-menu list edited on the
        // phone reaches the watch only after unrelated urgent traffic flushes the queue. See the
        // matching note in ButtonConfigTransmitter.
        putDataRequest.setUrgent()

        dataClient.putDataItem(putDataRequest).await()
    }
}

/** Fallback artwork resolution for shortcut covers when the Cover pill style fills a whole row,
 *  used only until a watch has reported its actual display width. */
private const val DEFAULT_COVER_ICON_SIZE_PX = 400
