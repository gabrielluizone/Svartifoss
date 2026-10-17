package com.svartifoss.snfell.view.actionconfigs

import android.widget.EditText
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.symfonium.SymfoniumSyncAction

class SymfoniumSyncConfigFragment :
        ActionConfigFragment<SymfoniumSyncAction>(R.layout.fragment_config_symfonium_sync) {
    override fun onLoad(action: SymfoniumSyncAction) {
        requireView().findViewById<EditText>(R.id.symfonium_provider_id).setText(
                action.providerId.takeIf { it > 0 }?.toString().orEmpty())
    }

    override fun save(action: SymfoniumSyncAction) {
        action.providerId = requireView().findViewById<EditText>(R.id.symfonium_provider_id)
                .text.toString().trim().toIntOrNull() ?: 0
    }
}
