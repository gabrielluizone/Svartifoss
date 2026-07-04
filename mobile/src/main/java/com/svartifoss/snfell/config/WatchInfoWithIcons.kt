package com.svartifoss.snfell.config

import android.graphics.drawable.Drawable
import com.svartifoss.snfell.proto.WatchInfo

class WatchInfoWithIcons(val watchInfo : WatchInfo, val icons : Map<Int, Drawable>) {
    override fun toString(): String {
        return "WatchInfoWithIcons(watchInfo=$watchInfo, icons=$icons)"
    }
}