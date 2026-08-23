package com.svartifoss.snfell.common

import java.util.Locale

/**
 * How the metadata face's rows are grouped, and how the numeric ones are written out.
 *
 * Two jobs that both have to be shared. The **groups** are what makes this face a set of pieces
 * rather than one fixed table: each is a face-scoped preference, so a watch can carry the tags and
 * drop the file details, or the other way round, and a saved theme takes the choice with it. The
 * **formatters** are pure because a bitrate rendered one way on the phone's preview and another on
 * the wrist is the same class of drift `PaletteTransforms` and `WatchTypography` exist to prevent.
 *
 * Grouped rather than one preference per field, deliberately. A row only exists when the playing app
 * actually published that tag, so per-field switches would mostly control rows that are not there -
 * twenty settings to hide what is already hidden. The groups are the divisions a user can reason
 * about without knowing which tags their player fills in.
 */
object TrackMetadataFields {

    /**
     * A block of rows that can be turned off as a unit.
     *
     * [preferenceKey] is face-scoped (see `FaceScopedPreferences.SCOPED_KEYS`), so the metadata face
     * can show everything while another face using the same theme shows only the essentials.
     */
    enum class Group(val preferenceKey: String, val defaultVisible: Boolean) {
        /** Album, album artist, track and disc position. What most players publish. */
        CORE("wear_metadata_show_core", true),

        /** Year, date, genre, and the release details an online lookup can add. */
        RELEASE("wear_metadata_show_release", true),

        /** Composer, writer, author - the people, where the player bothers to name them. */
        CREDITS("wear_metadata_show_credits", true),

        /**
         * ISRC and MusicBrainz ids. Off by default: they are catalogue numbers, useful when you
         * want one and noise on a wrist when you do not.
         */
        IDENTIFIERS("wear_metadata_show_identifiers", false),

        /** Codec, bitrate, sample rate, channels, file size. Local tracks only. */
        TECHNICAL("wear_metadata_show_technical", true),

        /**
         * How this playback is happening rather than what is playing: elapsed position to the
         * millisecond, speed, where the sound is coming out, and where the audio itself is coming
         * from (a file on the phone, or a URL).
         *
         * The one group whose rows a *streaming* track can fill when [TECHNICAL] draws nothing at
         * all - there is no file to describe, but there is always a URI and always an output.
         */
        PLAYBACK("wear_metadata_show_playback", true),
    }

    /**
     * Where the sound is actually coming out.
     *
     * Travels as a code rather than a label so the watch localises it; [fromCode] is what turns it
     * back, and an unrecognised code is [UNKNOWN] rather than a crash - a newer phone build may
     * name an output this one has never heard of.
     */
    enum class Output(val code: Int) {
        UNKNOWN(0),
        SPEAKER(1),
        WIRED(2),
        BLUETOOTH(3),
        USB(4),

        /** The session is casting: the phone is a remote control, not the thing making the sound. */
        REMOTE(5);

        companion object {
            fun fromCode(code: Int): Output = entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }

    /**
     * Where the audio itself comes from, worked out from the URI the player published.
     *
     * A *kind*, not a formatted string, for the same reason [Output] is a code: the label is the
     * watch's to translate. Refusing to guess past these three is deliberate - a `rtsp://` or an
     * app's own private scheme is honestly "somewhere else", and inventing a name for it would put
     * a confident wrong word on the screen.
     */
    enum class Origin {
        /** http(s) - a stream, and the case where there is no file to measure. */
        STREAM,

        /** `file://` - a path this phone can name. */
        FILE,

        /** `content://` - a file behind a provider, which is how MediaStore hands them out. */
        CONTENT,

        OTHER;

        companion object {
            /** Null when there is no URI at all, which is a different thing from [OTHER]. */
            fun of(uri: String?): Origin? {
                val scheme = uri?.trim()?.substringBefore("://", "")
                        ?.takeIf { it.isNotEmpty() }
                        ?.lowercase(Locale.US)
                        ?: return null
                return when (scheme) {
                    "http", "https" -> STREAM
                    "file" -> FILE
                    "content" -> CONTENT
                    else -> OTHER
                }
            }
        }
    }

    /** `mm:ss`, or `h:mm:ss` past an hour. Null when there is no duration to show. */
    fun formatDuration(durationMs: Long): String? {
        if (durationMs <= 0L) return null
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /**
     * Bitrate in kbps.
     *
     * Rounded to whole kbps because the extra digits are noise: a file reports 320537 bps and the
     * only thing anyone wants from that is "320". `MediaMetadataRetriever` reports bits per second,
     * so a value small enough to already be kbps is a player that got its own units wrong and is
     * rejected rather than shown as a fraction.
     */
    fun formatBitrate(bitsPerSecond: Long): String? {
        if (bitsPerSecond < 1000L) return null
        return "${bitsPerSecond / 1000} kbps"
    }

    /**
     * Sample rate in kHz, with the one decimal that matters (44.1, 48, 96, 192).
     *
     * Trailing `.0` is dropped: "48 kHz" is how it is written everywhere, "48.0 kHz" is how a
     * number formatter writes it.
     */
    fun formatSampleRate(hz: Long): String? {
        if (hz <= 0L) return null
        val khz = hz / 1000.0
        return if (khz == khz.toLong().toDouble()) {
            "${khz.toLong()} kHz"
        } else {
            String.format(Locale.US, "%.1f kHz", khz)
        }
    }

    /**
     * Channel count as the name people use for it.
     *
     * "2" tells a reader nothing they did not already assume; "Stereo" does. Past six channels the
     * naming stops being standard, so it falls back to the count with a unit.
     */
    fun formatChannels(channels: Int): String? = when {
        channels <= 0 -> null
        channels == 1 -> "Mono"
        channels == 2 -> "Stereo"
        channels == 6 -> "5.1"
        channels == 8 -> "7.1"
        else -> "$channels ch"
    }

    /** File size in MB, or KB below a megabyte. */
    fun formatFileSize(bytes: Long): String? = when {
        bytes <= 0L -> null
        bytes < 1024L * 1024L -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    /**
     * `7` or `7 / 12`, and with a disc when there is more than one.
     *
     * A disc number of 1 is dropped: nearly every single-disc release reports it, and printing
     * "Disc 1" on all of them turns a useful detail into a constant.
     */
    fun formatTrackPosition(trackNumber: Long, trackCount: Long, discNumber: Long): String? {
        if (trackNumber <= 0L) return null
        val position = if (trackCount > 0L) "$trackNumber / $trackCount" else "$trackNumber"
        return if (discNumber > 1L) "$position (disc $discNumber)" else position
    }

    /**
     * The codec name out of a MIME type - `audio/mpeg` becomes `MPEG`, `audio/flac` becomes `FLAC`.
     *
     * The subtype only, uppercased, with the `x-` vendor prefix stripped. Showing the whole MIME
     * string is showing a header field; showing the codec is showing what the file is.
     */
    fun formatCodec(mimeType: String?): String? {
        val subtype = mimeType?.substringAfterLast('/')?.trim()?.removePrefix("x-")
        return subtype?.takeIf { it.isNotEmpty() }?.uppercase(Locale.US)
    }

    /**
     * `m:ss.mmm`, or `h:mm:ss.mmm` past an hour.
     *
     * The millisecond form exists because this face's whole premise is showing what the others
     * summarise, and `3:45` is a summary: it is the same number every other face already draws.
     * Three digits rather than two is deliberate - the position is *extrapolated* between the
     * phone's samples (see `PlaybackPositionEstimate`), and hundredths would imply a precision the
     * transport cannot support while thousandths read as a running counter, which is what it is.
     *
     * Negative durations are refused rather than clamped: a player that reports one has published
     * something meaningless, and `0:00.000` would look like a real answer.
     */
    fun formatPreciseDuration(durationMs: Long): String? {
        if (durationMs < 0L) return null
        val millis = durationMs % 1000
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d.%03d", hours, minutes, seconds, millis)
        } else {
            String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, millis)
        }
    }

    /**
     * `1:23.456 / 3:45.678`, or just the position when the player publishes no duration.
     *
     * The total is included even though the host's own track-time readout may also be showing it,
     * unlike [formatDuration]'s row which is suppressed in that case: a bare elapsed figure to the
     * millisecond is a number with nothing to measure it against, and the pair is the reading.
     * A position past the duration is clamped, because extrapolation overshoots by design at the
     * end of a track and `3:46.010 / 3:45.678` reads as a bug rather than as rounding.
     */
    fun formatPlaybackPosition(positionMs: Long, durationMs: Long): String? {
        if (positionMs < 0L) return null
        val position = if (durationMs > 0L) positionMs.coerceAtMost(durationMs) else positionMs
        val elapsed = formatPreciseDuration(position) ?: return null
        val total = formatPreciseDuration(durationMs).takeIf { durationMs > 0L } ?: return elapsed
        return "$elapsed / $total"
    }

    /**
     * `1.25x` - and null at ordinary speed.
     *
     * A row reading "1x" on every track is a constant, and this screen's rule is that a row exists
     * only when it says something. The tolerance is there because a player computing its own rate
     * publishes 0.999998 rather than 1, and rounding it to a displayed "1x" would bring the
     * constant back through the side door.
     */
    fun formatSpeed(speed: Float): String? {
        if (!speed.isFinite() || speed <= 0f) return null
        if (kotlin.math.abs(speed - 1f) < SPEED_TOLERANCE) return null
        val text = String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
        return "${text}x"
    }

    /** How far from 1.0 a playback speed has to be before it is worth a row. */
    const val SPEED_TOLERANCE = 0.01f

    /**
     * The host a streaming URI points at - `i.scdn.co`, `rr3---sn-x.googlevideo.com`.
     *
     * The one part of a signed CDN URL that means anything at a glance, and the only part that fits
     * a wrist on its own line. A leading `www.` goes, since it is never the informative half.
     * Null for anything without a host, which includes every local URI.
     */
    fun uriHost(uri: String?): String? {
        val trimmed = uri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val afterScheme = trimmed.substringAfter("://", "").takeIf { it.isNotEmpty() } ?: return null
        return afterScheme
                .substringBefore('/')
                .substringBefore('?')
                .substringAfterLast('@')
                .removePrefix("www.")
                .takeIf { it.isNotEmpty() }
    }
}
