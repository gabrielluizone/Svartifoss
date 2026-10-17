package com.svartifoss.snfell.actions.symfonium

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.SelectableAction
import com.svartifoss.snfell.common.R as commonR
import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.music.SymfoniumPlayback
import com.svartifoss.snfell.view.actionconfigs.ActionConfigFragment
import com.svartifoss.snfell.view.actionconfigs.SymfoniumSyncConfigFragment
import javax.inject.Inject

/** Forces one configured Symfonium provider to synchronize. */
class SymfoniumSyncAction : SelectableAction {
    companion object {
        const val KEY_PROVIDER_ID = "PROVIDER_ID"
    }

    var providerId: Int

    constructor(context: Context) : super(context) {
        providerId = 0
    }

    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle) {
        providerId = bundle.getInt(KEY_PROVIDER_ID, 0)
    }

    override fun writeToBundle(bundle: PersistableBundle) {
        super.writeToBundle(bundle)
        bundle.putInt(KEY_PROVIDER_ID, providerId)
    }

    override fun retrieveTitle(): String = context.getString(R.string.action_symfonium_sync)

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(context, commonR.drawable.action_open_playlist)!!

    override val configFragment: Class<out ActionConfigFragment<out PhoneAction>>
        get() = SymfoniumSyncConfigFragment::class.java

    override fun isEqualToAction(other: PhoneAction): Boolean {
        other as SymfoniumSyncAction
        return super.isEqualToAction(other) && providerId == other.providerId
    }

    class Handler @Inject constructor(private val service: MusicService) :
            ActionHandler<SymfoniumSyncAction> {
        override suspend fun handleAction(action: SymfoniumSyncAction) {
            val sent = SymfoniumPlayback.syncProvider(service, action.providerId)
            if (!sent) {
                timber.log.Timber.w("Could not sync Symfonium provider %d", action.providerId)
            }
        }
    }
}
