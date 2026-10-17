package com.svartifoss.snfell.common

import com.svartifoss.snfell.proto.MusicState

/** Text to show on a player surface without changing the track's actual metadata. */
fun MusicState.displayArtist(): String =
        artist.takeIf { it.isNotBlank() } ?: sourceAppName.takeIf { it.isNotBlank() }.orEmpty()
