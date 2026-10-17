package com.svartifoss.snfell.common

import java.util.Locale

/**
 * How a position or a length is written wherever the player shows track time.
 *
 * `m:ss` under an hour and `h:mm:ss` from the first hour on, so a four-hour podcast reads
 * `4:00:00` rather than `240:00`. The minutes field used to just keep counting: every surface
 * carried a copy of `"%d:%02d"` - the watch's two track-time lines, its progress screen, the quick
 * panel's seek block, the phone's player dialog and the Watch tab's miniature - and none of them
 * knew an hour existed. The copies had also drifted (some clamped a negative reading and some
 * printed `0:-1`, one pinned its digits to `Locale.US` while the rest followed the device), so they
 * now share this one, for the reason `FaceGeometry` and `PaletteTransforms` live in `common`: the
 * miniature on the phone must not be able to disagree with the wrist about what a position looks
 * like.
 *
 * The hour is not zero-padded, as the minute is not: `1:03:10`, never `01:03:10`. That is how the
 * music and video apps that feed this one write it, and a padded first field would cost a character
 * of a line that already has to fit a 192 dp round display next to its own total
 * (`1:03:10 / 4:00:00`).
 */
object TrackTimeFormat {

    /**
     * [timeMs] as `m:ss`, or `h:mm:ss` once it reaches an hour. Never negative: a position
     * extrapolated just before the sample that corrects it can briefly land under zero, and
     * `0:-1` reads as a bug rather than as rounding.
     *
     * @param locale only decides the digits (some locales have their own); the layout is the same
     *   everywhere. Callers keep the locale they always used - the progress screen's is fixed.
     */
    fun format(timeMs: Long, locale: Locale = Locale.getDefault()): String {
        val totalSeconds = (timeMs / 1000L).coerceAtLeast(0L)
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(locale, "%d:%02d", minutes, seconds)
        }
    }
}
