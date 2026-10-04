package com.svartifoss.snfell.common

/** Main-thread owned. An old response or timer must never consume a newer request. */
class ShortcutRequestTracker {
    data class Request(val id: String, val uri: String, val acceptsLegacy: Boolean)
    private var pending: Request? = null
    private var legacyAmbiguous = false

    fun begin(id: String, uri: String, allowLegacy: Boolean): Request {
        if (pending != null) legacyAmbiguous = true
        return Request(id, uri, allowLegacy && !legacyAmbiguous).also { pending = it }
    }

    fun take(id: String?): Request? {
        val current = pending ?: return null
        if (id != current.id && !(id == null && current.acceptsLegacy)) return null
        pending = null
        return current
    }
}
