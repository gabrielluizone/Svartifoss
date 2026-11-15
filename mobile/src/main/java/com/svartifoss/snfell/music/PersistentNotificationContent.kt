package com.svartifoss.snfell.music

/**
 * One track as the persistent notification names it: its title, and its artist when the player
 * published one.
 */
data class NotificationTrack(val title: String, val artist: String?)

/**
 * The track after the playing one, as the queue gave it when the playing track was last read.
 *
 * [playingTitle] is the title of the track that was playing at that moment. The reading answers
 * "what comes next" only while that is still the track on screen: a track that ends or is skipped
 * changes the title before the queue's position catches up, and the successor of the previous track
 * must not be shown in its place during that gap. [PersistentNotificationContent.compose] enforces it.
 */
data class UpNextReading(val playingTitle: String, val next: NotificationTrack) {
    companion object {
        /** Null when either title is blank, because there is then nothing to show. */
        fun of(playingTitle: String?, nextTitle: String?, nextArtist: String?): UpNextReading? {
            val playing = playingTitle.clean() ?: return null
            val next = nextTitle.clean() ?: return null
            return UpNextReading(playing, NotificationTrack(next, nextArtist.clean()))
        }
    }
}

/**
 * What the persistent notification says while the service runs: the track that is playing, and the
 * track after it when that can be relied on.
 *
 * The notification used to read "Svartifoss active" and nothing else. It is the one notification the
 * app keeps up all the time and never dismisses, so it now says what is playing and what is coming
 * next, which is what a glance at the shade is for. The generic title stays for the case where no
 * track is known.
 *
 * [playing] is null when no track is known. [upNext] is null whenever the queue's next track would not
 * be what plays after this one - see [compose].
 */
data class PersistentNotificationContent(
        val playing: NotificationTrack?,
        val upNext: NotificationTrack?
) {
    companion object {
        /**
         * Builds what the notification shows from the state it describes.
         *
         * The next track is withheld under shuffle and under repeat-one. In both, the order of the
         * queue is not the order the player follows when this track ends, which is the same refusal the
         * watch's predicted track advance makes; a wrong "up next" is worse than none. It is withheld
         * too when [reading] was taken for a different track than the one playing now, which is the
         * window between a track change and the queue catching up with it.
         */
        fun compose(
                playingTitle: String?,
                playingArtist: String?,
                reading: UpNextReading?,
                shuffleOn: Boolean,
                repeatsOneTrack: Boolean
        ): PersistentNotificationContent {
            val title = playingTitle.clean()
            val playing = title?.let { NotificationTrack(it, playingArtist.clean()) }
            val upNext = reading
                    ?.takeIf { title != null && it.playingTitle.clean() == title }
                    ?.takeUnless { shuffleOn || repeatsOneTrack }
                    ?.next
            return PersistentNotificationContent(playing, upNext)
        }
    }
}

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
