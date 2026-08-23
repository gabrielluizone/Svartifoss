package com.svartifoss.snfell.config.actionlist

import com.svartifoss.snfell.actions.PhoneAction

interface ActionList {
    var actions: List<PhoneAction>

    fun commit()

    /**
     * Re-send the current actions to the watch without writing them to disk.
     *
     * For when nothing about the configuration changed but its *rendering* did - the icons are
     * rasterized at transmit time, so an action whose icon source has since improved (see
     * `AppGlyphStore`) is stale on the watch until something sends it again. Deliberately not
     * [commit]: this is reached from a notification arriving in the background, and a background
     * event has no business rewriting the user's config file to bytes it did not change.
     */
    fun retransmit()
}