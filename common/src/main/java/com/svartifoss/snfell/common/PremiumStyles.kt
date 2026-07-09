package com.svartifoss.snfell.common

/** Shared free/premium split for the overlay style vocabulary used by [MiscPreferences.WEAR_VOLUME_STYLE],
 *  [MiscPreferences.WEAR_QUICK_PANEL_STYLE] and [MiscPreferences.WEAR_QUEUE_STYLE] - one catalog so the
 *  phone (upsell gating in the style pickers) and the watch (defensive fallback) never disagree on which
 *  values require [MiscPreferences.PREMIUM_UNLOCKED]. */
object PremiumStyles {

    /** The 4 free-tier looks are deliberately excluded here - everything not in this set is free. */
    val PREMIUM_ONLY_STYLE_VALUES: Set<String> = setOf(
            "tonal", "neon", "gradient", "mono", "outline", "duotone", "contrast", "terminal", "frost",
    )

    /** Fallback value used in place of a locked style once entitlement is missing. */
    const val FALLBACK_STYLE_VALUE: String = "glass"

    /** Returns [value] unchanged if it's free or [isPremium] is true, otherwise [FALLBACK_STYLE_VALUE]. */
    fun sanitize(value: String, isPremium: Boolean): String =
            if (!isPremium && value in PREMIUM_ONLY_STYLE_VALUES) FALLBACK_STYLE_VALUE else value
}
