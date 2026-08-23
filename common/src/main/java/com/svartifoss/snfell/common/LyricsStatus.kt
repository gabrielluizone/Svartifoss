package com.svartifoss.snfell.common

/**
 * The `status` field of `lyrics.proto`'s `LyricsResponse`, as named constants.
 *
 * Kept as an int in the proto (matching `MusicState.repeatMode` and the rest of this schema) so an
 * older watch reading a status it has never heard of gets a number it can fall back on rather than
 * a parse failure.
 */
object LyricsStatus {
    /** Timed lines are available; `lrc` is set. */
    const val SYNCED = 0

    /** Lyrics exist but carry no timing anywhere; `plain` is set. */
    const val PLAIN = 1

    /** The lookup completed and this track genuinely has no lyrics. */
    const val NONE = 2

    /**
     * The lookup could not be completed - offline, rate limited, service down.
     *
     * Deliberately distinct from [NONE]: one is an answer and the other is a missing answer. Only
     * this one is worth retrying, and only this one should tell the user to check their phone's
     * connection - reporting "no lyrics for this song" when the phone simply had no signal is the
     * kind of wrong that makes people stop trusting the screen.
     */
    const val FAILED = 3

    /** The user turned the lyrics lookup off; nothing was requested from the network. */
    const val DISABLED = 4
}
