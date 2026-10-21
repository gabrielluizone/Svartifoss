package com.svartifoss.snfell.config.actionlist

import android.content.Context
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.FaceScopedPreferences
import androidx.preference.PreferenceManager
import android.graphics.drawable.Drawable
import android.net.Uri
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.auto.factory.AutoFactory
import com.google.auto.factory.Provided
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.PlayPlaylistShortcutAction
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.config.CustomIconStorage
import com.svartifoss.snfell.config.actionKeyOf
import com.svartifoss.snfell.config.lacksSeekOffset
import com.svartifoss.snfell.config.lacksShortcutDescription
import com.svartifoss.snfell.config.needsTransmittedIcon
import com.svartifoss.snfell.config.WatchInfoProvider
import com.svartifoss.snfell.config.buttons.ConfigConstants
import com.svartifoss.snfell.music.PlaylistShortcutStorage
import com.svartifoss.snfell.music.ShortcutAppMark
import com.svartifoss.snfell.music.ShortcutArtworkFetcher
import com.svartifoss.snfell.proto.WatchList
import com.svartifoss.snfell.util.launchWithPlayServicesErrorHandling
import com.matejdro.wearutils.miscutils.BitmapUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

@AutoFactory
class ActionListTransmitter(private val actionList: ActionList,
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

            // Exactly the assets this payload would put - see ButtonConfigTransmitter for why
            // asking by action key instead fired on every launch.
            val expectedAssets = actionList.actions.withIndex()
                    .filter { transmitsListIcon(it.value) }
                    .mapTo(mutableSetOf()) { CommPaths.ASSET_BUTTON_ICON_PREFIX + it.index }
            // The app glyphs too, so a menu sent before they existed - or before this phone had
            // learned one - picks them up without an edit. Glyphs arrive later than the menu (an
            // app has to post a notification first), and the learned-glyph retransmit only runs
            // while the music service is up.
            actionList.actions.withIndex()
                    .filter { appMarkOf(it.value) != null }
                    .mapTo(expectedAssets) { CommPaths.ASSET_APP_MARK_PREFIX + it.index }
            // Entries the phone now lists with a cover. A menu sent before covers existed already
            // carries an icon for a saved shortcut (its app's glyph), so the asset check alone
            // would keep that glyph on the watch until the menu happened to be edited.
            val coverIndices = actionList.actions.withIndex()
                    .filter { listCoverOf(it.value) != null }
                    .map { it.index }

            val missingMetadata = dataOnWatch.any { item ->
                try {
                    val watchActions = WatchList.parseFrom(item.data).actionsList
                    coverIndices.any { watchActions.getOrNull(it)?.iconIsCoverArt == false } ||
                    watchActions.any { action ->
                        !action.hasIconTintable() ||
                                (action.actionKey == PlayPlaylistShortcutAction::class.java.canonicalName &&
                                        (!action.hasRemoteUri() || !action.hasIconIsCoverArt())) ||
                                lacksSeekOffset(action.actionKey, action.hasSeekOffsetMs()) ||
                                lacksShortcutDescription(
                                        action.actionKey, action.hasShortcutSubtitle())
                    } || expectedAssets.any { !item.assets.containsKey(it) }
                } catch (_: Exception) {
                    true
                }
            }
            if (!dataOnWatch.any() || missingMetadata) {
                withContext(Dispatchers.Main) { actionList.retransmit() }
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
            val listCover = listCoverOf(action)
            val actionProto = WatchList.WatchListAction.newBuilder()
            actionProto.actionTitle = action.title
            actionProto.actionKey = action.javaClass.canonicalName
            actionProto.iconTintable = listCover == null && action.iconTintable
            actionProto.iconIsCoverArt = listCover != null || action.isCoverArt
            action.remoteUri?.takeIf(String::isNotBlank)?.let { actionProto.remoteUri = it }
            action.seekOffsetMs?.let { actionProto.seekOffsetMs = it }
            // Only the starred ones say so; an unstarred entry carries nothing, as before.
            if (action.inQuickPanel) actionProto.inQuickPanel = true
            action.streamingShortcut?.let { description ->
                actionProto.shortcutSubtitle = description.subtitle
                actionProto.shortcutShuffleable = description.shuffleable
                description.creator?.let { actionProto.shortcutCreator = it }
                description.description?.let { actionProto.shortcutDescription = it }
            }
            protoBuilder.addActions(actionProto.build())

            appMarkOf(action)?.let { png ->
                putDataRequest.putAsset(CommPaths.ASSET_APP_MARK_PREFIX + index, Asset.createFromBytes(png))
            }

            if (listCover == null && !needsTransmittedIcon(action, actionProto.actionKey)) {
                // The watch ships the same vector and resolves it from the action key.
                continue
            }

            var icon = BitmapUtils.getBitmap(listCover ?: customIconStorage[action])
            // 40dp is sized for the menu's circular thumbnail slot. The Cover pill style stretches
            // real cover art (not just any non-tintable icon - a plain app-launcher icon stays
            // small) across the whole row, where that is visibly soft - so only entries the watch
            // might actually fill a pill with are sent larger, and only when the feature that does
            // so is on. Sized to the paired watch's actual screen width rather than a fixed guess,
            // clamped to what ShortcutArtworkFetcher ever caches (shrinkPreservingRatio only
            // shrinks, so sending more than that would just waste bandwidth).
            //
            // Any other cover is sent at the row's size, but never below MIN_COVER_ICON_PX: the
            // shortcut's own screen blurs it into a glow built from a 64 px thumbnail, and a watch
            // that has not reported its density yet would otherwise be sent one smaller than that.
            val isCover = actionProto.iconIsCoverArt
            val iconSize = when {
                isCover && coverArtwork && shortcutCoverEnabled -> coverIconSizePx
                isCover -> maxOf(targetIconSize, MIN_COVER_ICON_PX)
                else -> targetIconSize
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

        // The collections in this menu that have official artwork the phone has not fetched yet:
        // sent above with their drawn covers, and again once the real ones arrive (opt-in).
        ShortcutArtworkFetcher.fetchMissingCollections(
                context,
                actions.filter { it.customIconUri == null }.mapNotNull { it.streamingCollection }) {
            actionList.retransmit()
            PlaylistShortcutStorage.syncToWatch(context)
        }
    }

    /** The cover [action] is listed with, unless the user gave it an icon of their own. */
    private fun listCoverOf(action: PhoneAction): Drawable? =
            action.listCover.takeIf { action.customIconUri == null }

    /**
     * The glyph of the app a streaming destination opens in, for "Open on phone" on that
     * destination's own screen - null for every other entry, and for an app this phone has not
     * learned a glyph for. One function for the send and the resend check, like [transmitsListIcon].
     */
    private fun appMarkOf(action: PhoneAction): ByteArray? {
        if (action.streamingShortcut == null) return null
        val target = action.remoteUri?.takeIf(String::isNotBlank) ?: return null
        return ShortcutAppMark.glyphPngFor(context, target)
    }

    /** Whether [action]'s entry carries an icon asset - the predicate both the send and the
     *  "is the watch's copy current" check use, so the two cannot disagree. */
    private fun transmitsListIcon(action: PhoneAction): Boolean =
            listCoverOf(action) != null || needsTransmittedIcon(action, actionKeyOf(action))
}

/** Fallback artwork resolution for shortcut covers when the Cover pill style fills a whole row,
 *  used only until a watch has reported its actual display width. */
private const val DEFAULT_COVER_ICON_SIZE_PX = 400

/** The least a cover is sent at, in pixels - enough for the shortcut screen's glow and a row's thumbnail. */
private const val MIN_COVER_ICON_PX = 96
