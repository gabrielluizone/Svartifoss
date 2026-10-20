package com.svartifoss.snfell.watch.view.volume

import android.content.Context
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.PhoneAudioBlock

/**
 * What the watch says about a [PhoneAudioBlock], or null when there is nothing to say.
 *
 * The screen shows a glyph rather than these words (see `PanelReadout.applyAudioBlock`); they are
 * what the readout announces to a screen reader, worded once for the player's volume overlay and
 * the dedicated volume screen alike.
 */
fun phoneAudioBlockText(context: Context, block: PhoneAudioBlock): String? = when (block) {
    PhoneAudioBlock.NONE -> null
    PhoneAudioBlock.DO_NOT_DISTURB -> context.getString(R.string.audio_block_dnd)
    PhoneAudioBlock.MUTED -> context.getString(R.string.audio_block_muted)
    PhoneAudioBlock.SAFE_VOLUME_HELD -> context.getString(R.string.audio_block_safe_volume)
}
