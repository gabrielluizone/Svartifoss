package com.svartifoss.snfell.view.watchface

import java.lang.ref.WeakReference

/**
 * Pictures every [WatchPreviewView] in the process can share because each is a pure function of
 * a few numbers - the procedural sample covers, their blurs and their frosted composition.
 *
 * Each preview used to draw its own. That cost nothing visible on the Watch tab, which has one
 * preview, and a great deal in the community gallery, where every card is a preview of the same
 * size: on 2026-11-06 twelve cards held twelve copies of the same five 417 px pictures, 39.6 MB of
 * identical bitmaps (docs/memory-investigation.md).
 *
 * Values are held **weakly**, so this never keeps a picture alive by itself: the previews drawing
 * it do, and once none is left the garbage collector takes it. That matters because the process
 * outlives every screen - it is also the music service. And since a shared picture may be on
 * screen in another preview, no preview may recycle one: [holds] is what a preview asks before it
 * recycles a bitmap it believes it owns.
 */
internal class SharedArtworkCache<K : Any, V : Any>(
        private val isUsable: (V) -> Boolean = { true }
) {
    private val entries = HashMap<K, WeakReference<V>>()

    /** The value for [key], built by [build] only when no live, usable one is cached. */
    @Synchronized
    fun obtain(key: K, build: () -> V): V {
        entries[key]?.get()?.takeIf(isUsable)?.let { return it }
        entries.entries.removeAll { it.value.get() == null }
        return build().also { entries[key] = WeakReference(it) }
    }

    /** Whether [value] is one this cache handed out - and so possibly drawn somewhere else. */
    @Synchronized
    fun holds(value: V): Boolean = entries.values.any { it.get() === value }
}
