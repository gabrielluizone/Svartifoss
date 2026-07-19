package com.svartifoss.snfell.config.buttons

import android.content.Context
import android.media.AudioManager
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
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.config.CustomIconStorage
import com.svartifoss.snfell.config.WatchInfoProvider
import com.svartifoss.snfell.proto.WatchActions
import com.svartifoss.snfell.util.launchWithPlayServicesErrorHandling
import com.matejdro.wearutils.miscutils.BitmapUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.tasks.await

@AutoFactory
class ButtonConfigTransmitter(buttonConfig: ButtonConfig,
                              @Provided private val context: Context,
                              @Provided private val watchInfoProvider: WatchInfoProvider,
                              @Provided private val customIconStorage: CustomIconStorage,
                              private val endpointPath: String) {

    private val dataClient = Wearable.getDataClient(context)

    init {
        resendIfNeeded(buttonConfig)
    }

    private fun resendIfNeeded(buttonConfig: ButtonConfig) {
        GlobalScope.launchWithPlayServicesErrorHandling(context) {
            val dataOnWatch = dataClient.getDataItems(Uri.parse("wear://*$endpointPath")).await()

            val missingRemoteUri = dataOnWatch.any { item ->
                try {
                    WatchActions.parseFrom(item.data).actionsList.any { action ->
                        action.actionKey == PlayPlaylistShortcutAction::class.java.canonicalName &&
                                !action.hasRemoteUri()
                    }
                } catch (_: Exception) {
                    true
                }
            }
            if (!dataOnWatch.any() || missingRemoteUri) {
                sendConfigToWatch(buttonConfig.getAllActions())
            }

            dataOnWatch.release()
        }
    }

    suspend fun sendConfigToWatch(buttons: Collection<Map.Entry<ButtonInfo, PhoneAction>>) {
        val density = watchInfoProvider.value?.watchInfo?.displayDensity ?: 1f
        val targetIconSize = (ConfigConstants.BUTTON_ICON_SIZE_DP * density).toInt()

        val putDataRequest = PutDataRequest.create(endpointPath)
        val protoBuilder = WatchActions.newBuilder()
        protoBuilder.volumeStep = volumeStep

        for ((buttonInfo, action) in buttons) {
            val buttonInfoProto = buttonInfo.buildProtoVersion()
            val actionKey = action.javaClass.canonicalName ?: action.javaClass.name
            buttonInfoProto.actionKey = actionKey
            buttonInfoProto.actionTitle = action.title
            buttonInfoProto.iconTintable = action.iconTintable
            action.remoteUri?.takeIf(String::isNotBlank)?.let { buttonInfoProto.remoteUri = it }
            protoBuilder.addActions(buttonInfoProto.build())

            if (buttonInfo.physicalButton) {
                // No need to send action images for physical buttons
                continue
            }

            if (action.customIconUri == null &&
                    StandardIcons.hasIcon(buttonInfoProto.actionKey)) {
                // We already have vector icon of this on the watch.
                // No need to waste bluetooth bandwith by transferring it

                continue
            }


            var icon = BitmapUtils.getBitmap(customIconStorage[action])
            icon = BitmapUtils.shrinkPreservingRatio(icon, targetIconSize, targetIconSize, true)

            val iconData = BitmapUtils.serialize(icon)
            val assetKey = CommPaths.ASSET_BUTTON_ICON_PREFIX + buttonInfo.getKey()
            if (iconData != null) {
                putDataRequest.putAsset(assetKey, Asset.createFromBytes(iconData))
            }
        }

        putDataRequest.data = protoBuilder.build().toByteArray()

        // Urgent: without it the Data Layer batches this DataItem for power reasons and can delay
        // the sync by minutes, so a button config edited on the phone appeared on the watch only
        // after some *other* urgent traffic (a control message from tapping/turning the watch)
        // flushed the pipe. Music state is already sent urgent; user-edited config must be too.
        putDataRequest.setUrgent()

        dataClient.putDataItem(putDataRequest).await()
    }

    private val volumeStep by lazy {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val volumeSteps = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumeStep = 1f / volumeSteps

        volumeStep
    }
}
