package com.svartifoss.snfell.watch.communication

import com.svartifoss.snfell.proto.MusicState

/** The message and artwork phases of one snapshot share a revision; its settled art wins a tie. */
internal fun shouldReplaceMusicSnapshot(candidate: MusicState, current: MusicState): Boolean = when {
    candidate.seq != current.seq -> candidate.seq > current.seq
    else -> !candidate.albumArtPending || current.albumArtPending
}

/** Equal revisions must remain eligible so the durable artwork can follow its fast message. */
internal fun isCurrentMusicSnapshot(sequence: Long, lastAppliedSequence: Long): Boolean =
        sequence == 0L || sequence >= lastAppliedSequence

/** Remembers the durable artwork phase across callbacks, independently of fast messages. */
internal class MusicAssetOrder {
    private var latest: MusicState? = null

    fun accept(state: MusicState): Boolean {
        val previous = latest
        if (state.seq != 0L && previous != null && !shouldReplaceMusicSnapshot(state, previous)) {
            return false
        }
        latest = state
        return true
    }
}
