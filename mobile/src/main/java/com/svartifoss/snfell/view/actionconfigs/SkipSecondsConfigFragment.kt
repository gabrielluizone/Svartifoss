package com.svartifoss.snfell.view.actionconfigs

import android.widget.EditText
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.playback.SkipThirtySecondsAction

class SkipSecondsConfigFragment : ActionConfigFragment<SkipThirtySecondsAction>(R.layout.fragment_config_skip_seconds) {
    override fun onLoad(action: SkipThirtySecondsAction) {
        requireView().findViewById<EditText>(R.id.seconds_box).setText(action.secondsToSkip.toString())
    }

    override fun save(action: SkipThirtySecondsAction) {
        action.secondsToSkip = requireView().findViewById<EditText>(R.id.seconds_box).text.toString().toInt()
    }
}
