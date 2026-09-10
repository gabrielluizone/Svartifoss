package com.svartifoss.snfell.view.settings

import com.svartifoss.snfell.common.WatchTypography

/**
 * Reuses native Flex fonts across preview frames without retaining every slider value forever.
 * Only font variation settings identify a font; size, opacity and tracking live on the Paint.
 * The value is generic so the allocation and eviction policy can be exercised without Android.
 */
internal class FlexTypefaceCache<T>(private val maxEntries: Int = 32) {
    init {
        require(maxEntries > 0)
    }

    private val entries = object : LinkedHashMap<String, T>(maxEntries, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>?): Boolean =
                size > maxEntries
    }

    @Synchronized
    fun getOrLoad(
            spec: WatchTypography.TextSpec,
            axes: WatchTypography.FlexAxes,
            load: (String) -> T
    ): T {
        val settings = WatchTypography.flexVariationSettings(spec, axes)
        // A null fallback is also a result: retrying an unsupported font every frame is expensive.
        if (entries.containsKey(settings)) return entries.getValue(settings)
        return load(settings).also { entries[settings] = it }
    }
}
