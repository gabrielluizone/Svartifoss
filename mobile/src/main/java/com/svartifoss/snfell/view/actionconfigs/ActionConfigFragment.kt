package com.svartifoss.snfell.view.actionconfigs

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.view.mainactivity.MainActivity

abstract class ActionConfigFragment<T : PhoneAction> : Fragment {
    constructor(contentLayoutId: Int) : super(contentLayoutId)
    constructor() : super()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Config fragments are committed asynchronously AFTER the host dialog's view tree was
        // already walked by the accent pass, so they'd otherwise keep theme-default (sage)
        // widgets - e.g. the seconds EditText's selection handles - under a custom accent.
        (activity as? MainActivity)?.applyAccentToView(view)
    }

    abstract fun save(action: T)

    fun load(action: T) {
        lifecycleScope.launchWhenStarted {
            onLoad(action)
        }
    }

    abstract fun onLoad(action: T)
}
