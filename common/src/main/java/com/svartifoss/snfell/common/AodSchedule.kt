package com.svartifoss.snfell.common

/**
 * Time-of-day dimming for the always-on display.
 *
 * The AOD already has one brightness, and it is right for a bright afternoon and wrong at three in
 * the morning, when the same dim outline is the brightest thing in a dark bedroom. Nobody wants to
 * open a settings page at night to turn it down, so the dimming follows the hour instead: the
 * ambient screen refreshes once a minute anyway, which is all the resolution a schedule needs.
 *
 * Pure so the hour boundaries - the part that is easy to get off by one, and impossible to notice
 * from the wrist until the night it matters - are pinned by a JVM test.
 */
enum class AodNightDim(val token: String, private val fromHour: Int, private val untilHour: Int,
                       val factor: Float) {
    /** Always the chosen brightness. */
    OFF("off", 0, 0, 1f),

    /** From 22:00 to 07:00, at half brightness. */
    NIGHT("night", 22, 7, 0.5f),

    /** From 19:00 to 08:00, at three-fifths brightness: dusk to well after dawn. */
    LONG_NIGHT("long_night", 19, 8, 0.6f);

    /** True when [hourOfDay] (0-23) falls inside this schedule. A schedule that wraps midnight is one window. */
    fun covers(hourOfDay: Int): Boolean {
        if (this == OFF) return false
        val hour = hourOfDay.coerceIn(0, 23)
        return if (fromHour > untilHour) hour >= fromHour || hour < untilHour
        else hour in fromHour until untilHour
    }

    /** The multiplier on the AOD's brightness at [hourOfDay]. */
    fun factorAt(hourOfDay: Int): Float = if (covers(hourOfDay)) factor else 1f

    companion object {
        fun fromPref(value: String?): AodNightDim = entries.firstOrNull { it.token == value } ?: OFF
    }
}

/**
 * How far the always-on display moves its content to keep a static image from burning in.
 *
 * Every ambient refresh shifts the whole screen by a few pixels. The distance used to be a constant;
 * a wider shift spreads the wear further at the price of the clock visibly hopping, a gentler one
 * the reverse, and which trade is right depends on the panel and on who is looking at it.
 */
enum class AodShift(val token: String, val maxPixels: Int) {
    GENTLE("gentle", 3),
    STANDARD("standard", 5),
    WIDE("wide", 9);

    companion object {
        fun fromPref(value: String?): AodShift = entries.firstOrNull { it.token == value } ?: STANDARD
    }
}

/** The ambient styles that draw a screen of their own and nothing of the player. */
object AodStandaloneStyles {
    const val CHRONO = "chrono"
    const val MINUTE_RING = "minute_ring"
    const val HEX_FRAME = "hex_frame"

    val ALL: Set<String> = setOf(CHRONO, MINUTE_RING, HEX_FRAME)

    fun isStandalone(style: String): Boolean = style in ALL
}
