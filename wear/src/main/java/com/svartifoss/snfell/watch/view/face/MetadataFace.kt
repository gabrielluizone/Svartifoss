package com.svartifoss.snfell.watch.view.face

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import android.os.SystemClock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CoverShape
import com.svartifoss.snfell.common.RoundScreenText
import com.svartifoss.snfell.common.FaceGeometry
import com.svartifoss.snfell.common.TrackMetadataFields
import com.svartifoss.snfell.common.TrackMetadataFields.Group
import com.svartifoss.snfell.proto.TrackMetadata
import com.svartifoss.snfell.watch.view.compose.FaceClock
import kotlinx.coroutines.delay

/** Vertical band the table occupies, as fractions of screen height - see [RoundScreenText]. */
private const val TABLE_TOP = 0.30f
private const val TABLE_BOTTOM = 0.82f

/**
 * How many rows fit, measured rather than assumed.
 *
 * A wrist fits what a wrist fits. Rather than scroll - which would fight the host's configurable
 * up/down swipe actions, since those are pseudo-buttons on this screen - the face draws the highest
 * priority rows it has room for and the user chooses *which kinds* of row compete for that room
 * through the group switches. That is what makes those switches load-bearing rather than
 * decorative: turning off the file details is how you make space for the credits.
 *
 * Derived from the actual height instead of a constant, because a fixed count is either wasteful on
 * a 45mm watch or overflowing on a 40mm one, and there is no single number that is right for both.
 */
private val ROW_HEIGHT = FaceGeometry.Metadata.ROW_HEIGHT_DP.dp

/** Share of the screen the table may occupy, once the identity block above it has had its own. */
private const val TABLE_HEIGHT_FRACTION = FaceGeometry.Metadata.TABLE_HEIGHT_FRACTION

private const val MIN_ROWS = FaceGeometry.Metadata.MIN_ROWS
private const val MAX_ROWS = FaceGeometry.Metadata.MAX_ROWS

private const val LABEL_ALPHA = 0.52f
private const val VALUE_ALPHA = 0.92f

/**
 * How often the millisecond position readout is recomputed, in ms.
 *
 * Not the display's frame rate, deliberately. Three digits stepping ~16 times a second already
 * reads as a running counter - past that the eye cannot follow the last digit anyway, and the
 * difference is spent waking the composition on a wrist. Aligned to a frame regardless (see
 * [LivePositionRow]), so the redraws that do happen are not off-cadence ones.
 */
private const val POSITION_REFRESH_MS = 60L

/**
 * Metadata: what the playing track actually is, rather than what it looks like.
 *
 * Every other face in the collection is built around the artwork and treats the tags as a caption.
 * This one inverts that - a small cover, the title and artist, and then as much of the record's
 * actual detail as the screen will hold: album and position on it, who wrote it, when it came out,
 * and for a local file what the encoding is.
 *
 * Three things are load-bearing.
 *
 * **It never says "unknown".** A row exists only when the phone sent a value for it, so a streaming
 * track that publishes three tags draws three rows rather than twenty labelled blanks. That is not
 * a nicety: a table full of "—" reads as a broken screen, and the honest signal that a player is
 * stingy with its metadata is a short table.
 *
 * **It does not wait for the network.** The phone answers with the player's own tags immediately
 * and sends a second, enriched answer later if the optional online lookup is switched on (see
 * `MusicService.sendTrackMetadataToWatch`). The table is on the wrist before any lookup starts, and
 * simply grows if one succeeds.
 *
 * **It composes, it does not decide.** Backdrop through [PlayerBackgroundTreatment], shading
 * through [PlayerBackgroundTreatment], colours and fonts from the state - so every background style,
 * colour treatment and typography preference reaches this face exactly as it reaches the others.
 * What it owns is the arrangement.
 */
@Composable
fun MetadataFace(
        state: NowPlayingFaceState,
        listener: NowPlayingFaceListener,
        coverShape: CoverShape = CoverShape.ROUNDED,
        showCover: Boolean = true
) {
    if (state.ambient) {
        MetadataAmbient(state)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        val rowBudget = ((maxHeight * TABLE_HEIGHT_FRACTION) / ROW_HEIGHT)
                .toInt()
                .coerceIn(MIN_ROWS, MAX_ROWS)

        PlayerBackgroundTreatment(state)

        val inset = RoundScreenText.sideInsetFor(top = TABLE_TOP, bottom = TABLE_BOTTOM)

        Column(
                modifier = Modifier
                        .fillMaxSize()
                        .padding(start = screen * inset, end = screen * inset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Identity(state, screen, coverShape, showCover)
            Box(Modifier.height(screen * 0.045f))
            MetadataTable(state, rowBudget)
        }

        FaceClock(
                visible = state.showClock,
                color = Color(state.clockColor),
                fontFamily = state.clockFont,
                typography = state.clockTypography)

        // No visible control of its own, so the middle of the screen is the target - the host's own
        // centre tap zone is GONE for every Compose face, and a face that wires nothing here has no
        // working play/pause at all.
        CenterGestureRegion(listener = listener, size = screen * 0.5f, pulseSize = screen * 0.32f)
    }
}

/** Cover, title and artist: the part every face shows, kept small so the table has the screen. */
@Composable
private fun Identity(
        state: NowPlayingFaceState,
        screen: Dp,
        coverShape: CoverShape,
        showCover: Boolean
) {
    val art = state.albumArt?.takeUnless { state.albumArtHidden }
    if (art != null && showCover) {
        val size = screen * 0.17f
        Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                        .size(size)
                        .clip(coverShape.toComposeShape(size)))
        Box(Modifier.height(screen * 0.025f))
    }

    if (state.showTitle && state.title.isNotBlank()) {
        Text(
                text = state.title,
                color = titleTextColor(state, Color.White),
                fontFamily = state.titleFont,
                fontWeight = state.titleFontWeight,
                fontStyle = state.titleFontStyle,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
    }
    if (state.showArtist && state.artist.isNotBlank()) {
        // The shared helper rather than a bare Text: this line already picked up the artist's
        // family, weight and slant by hand and so looked wired up, while the size, tracking and
        // opacity controls sitting beside them in the Text tab did nothing here at all.
        ArtistLineText(
                text = state.artist,
                state = state,
                color = Color(state.artistColor),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
    }
}

/**
 * One line of the table.
 *
 * [lines] is both what the value may wrap to and what the row costs against the screen's budget,
 * so a two-line row displaces a one-line row rather than overflowing past the bottom of the
 * display - which is what a plain `take(n)` would have done the moment anything wrapped.
 */
private data class MetadataEntry(
        val label: String,
        val value: String,
        val lines: Int = 1,
        /**
         * Drawn without the label column, across the full width.
         *
         * For the one value no 90dp column can hold: a signed CDN URL is several hundred
         * characters, and squeezing it beside a label shows about six of them.
         */
        val wide: Boolean = false,
)

@Composable
private fun MetadataTable(state: NowPlayingFaceState, rowBudget: Int) {
    // Nothing at all until the phone has answered. "No details for this track" is a *result*, and
    // showing it while the answer is still in flight flashes something untrue for the length of a
    // Bluetooth round trip.
    if (state.metadata == null) return

    val rows = metadataRows(state)
    // The live position is drawn separately from the rest, and by its own composable, so that the
    // ~16 updates a second it needs invalidate that one row instead of rebuilding the whole table
    // around it. It leads because it is the only row that is *about right now*.
    val showsPosition = TrackMetadataFields.Group.PLAYBACK in state.metadataGroups &&
            (state.durationMs > 0L || state.positionMs > 0L)

    if (rows.isEmpty() && !showsPosition) {
        Text(
                text = stringResource(R.string.metadata_none),
                color = Color.White.copy(alpha = LABEL_ALPHA),
                fontFamily = state.artistFont,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        return
    }

    var remaining = rowBudget
    if (showsPosition) {
        LivePositionRow(state)
        remaining -= 1
    }
    for (entry in rows) {
        if (entry.lines > remaining) continue
        remaining -= entry.lines
        MetadataRow(entry, state)
    }
}

/**
 * The elapsed position, to the millisecond, refreshed fast enough for that to be true.
 *
 * The host publishes a position on a 500 ms ticker, which is right for a `1:23` line and useless
 * for a `1:23.456` one - the last three digits would step by 500, twice a second, and read as a
 * broken clock rather than as a running one. The digits in between are extrapolated here, and by
 * exactly the rule everything else in this app follows: the sample is an anchor, and what is added
 * to it is elapsed time from *this device's* monotonic clock, never a subtraction between the two
 * devices' wall clocks (see `PlaybackPositionEstimate`, which owns the same reasoning for the
 * sample's own trip across Bluetooth).
 *
 * Re-anchored on every sample, so a seek lands on the next tick instead of being caught up to; and
 * running only while playing, since a paused position is not moving and there is nothing to
 * extrapolate. Both of those also mean this costs nothing on the face's most common state.
 */
@Composable
private fun LivePositionRow(state: NowPlayingFaceState) {
    val sample = state.positionMs
    val playing = state.playing
    val anchorRealtime = remember(sample, playing) { SystemClock.elapsedRealtime() }
    var elapsed by remember { mutableStateOf(0L) }

    LaunchedEffect(sample, playing) {
        elapsed = 0L
        if (!playing) return@LaunchedEffect
        while (true) {
            withFrameMillis { elapsed = SystemClock.elapsedRealtime() - anchorRealtime }
            delay(POSITION_REFRESH_MS)
        }
    }

    val value = TrackMetadataFields.formatPlaybackPosition(
            (sample + elapsed).coerceAtLeast(0L), state.durationMs) ?: return
    MetadataRow(MetadataEntry(stringResource(R.string.metadata_position), value), state)
}

@Composable
private fun MetadataRow(entry: MetadataEntry, state: NowPlayingFaceState) {
    // A wrapped value needs a smaller face to be worth wrapping to: at 11sp two lines hold barely
    // more than one, and the whole point of spending a second row is fitting more of the string.
    val valueSize = if (entry.lines > 1) 9.sp else 11.sp

    if (entry.wide) {
        Text(
                text = entry.value,
                color = Color.White.copy(alpha = VALUE_ALPHA),
                fontFamily = state.artistFont,
                fontSize = valueSize,
                lineHeight = valueSize * 1.15f,
                maxLines = entry.lines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp))
        return
    }

    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
                text = entry.label,
                color = Color.White.copy(alpha = LABEL_ALPHA),
                fontFamily = state.artistFont,
                fontSize = 9.sp,
                letterSpacing = 0.06.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.width(52.dp))
        Box(Modifier.width(5.dp))
        Text(
                text = entry.value,
                color = Color.White.copy(alpha = VALUE_ALPHA),
                fontFamily = state.artistFont,
                fontSize = valueSize,
                lineHeight = valueSize * 1.15f,
                maxLines = entry.lines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f))
    }
}

/**
 * Ambient Metadata: the identity and the first couple of rows, outlined on black.
 *
 * A dense table redrawn once a minute is a wall of small text that is mostly stale, and burning a
 * static grid into an OLED panel is exactly what an always-on variant must not do. The rows that
 * survive are the ones that answer "what am I listening to" at a glance.
 */
@Composable
private fun MetadataAmbient(state: NowPlayingFaceState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tint = Color(state.ambientTint)
        Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = maxWidth * 0.16f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                    text = state.title,
                    color = tint.copy(alpha = 0.9f * state.ambientIntensity),
                    fontFamily = state.titleFont,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center)
            metadataRows(state).take(2).forEach { entry ->
                Text(
                        text = "${entry.label}  ${entry.value}",
                        color = tint.copy(alpha = 0.6f * state.ambientIntensity),
                        fontFamily = state.artistFont,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * The rows to draw, in the order they compete for the screen.
 *
 * Priority is deliberate rather than the order the proto happens to declare: album and position on
 * it are what a listener actually wants first, then when it came out, then who made it, then how it
 * is reaching them, then the file, then catalogue numbers. Everything a group's switch excludes is
 * skipped before it can take a row from something the user did leave on.
 *
 * The live elapsed position is *not* here - see [LivePositionRow], which draws itself so its
 * refresh rate does not rebuild this list sixteen times a second.
 */
@Composable
private fun metadataRows(state: NowPlayingFaceState): List<MetadataEntry> {
    val meta = state.metadata ?: return emptyList()
    val rows = mutableListOf<MetadataEntry>()

    fun add(label: String, value: String?, lines: Int = 1, wide: Boolean = false) {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return
        rows.add(MetadataEntry(label, text, lines, wide))
    }

    fun enabled(group: Group) = group in state.metadataGroups

    // The identity to deduplicate against is the *metadata payload's* own, never the two lines on
    // screen. Those become status text the moment playback stops - the artist line reads "Playback
    // Stopped" while paused - and comparing against that made every dedup below fail exactly then:
    // pausing made an "Album artist" row appear carrying the very name the check exists to
    // suppress, directly under an artist line that had been replaced by a status message. The
    // screen lines are still the fallback for a payload with no echo, but only when they are
    // themselves real metadata.
    val trackTitle = meta.takeText { it.title }
            ?: state.title.takeUnless { state.titleIsStatus }?.trim()
    val trackArtist = meta.takeText { it.artist }
            ?: state.artist.takeUnless { state.artistIsStatus }?.trim()

    if (enabled(Group.CORE)) {
        // Several players set the album to the track's own name for a single. A row repeating the
        // line directly above it is worse than no row.
        add(stringResource(R.string.metadata_album),
                meta.takeText { it.album }?.takeUnless { it.equals(trackTitle, true) })
        // The album artist is the same person as the artist on the overwhelming majority of
        // records, and the artist is already the second line of this screen.
        add(stringResource(R.string.metadata_album_artist),
                meta.takeText { it.albumArtist }?.takeUnless { it.equals(trackArtist, true) })
        add(stringResource(R.string.metadata_track), TrackMetadataFields.formatTrackPosition(
                meta.trackNumber, meta.trackCount, meta.discNumber))
        // The host draws the elapsed/total readout over every face when the track time is on, and
        // the Playback block draws a millisecond one of its own - either makes this a second copy
        // of the same number.
        if (!state.showTrackTime && !enabled(Group.PLAYBACK)) {
            add(stringResource(R.string.metadata_duration),
                    TrackMetadataFields.formatDuration(meta.durationMs))
        }
        // Only ever true; the phone does not write the field otherwise, because "Compilation: no"
        // is a row about the absence of a property.
        if (meta.compilation) {
            add(stringResource(R.string.metadata_compilation),
                    stringResource(R.string.metadata_value_yes))
        }
    }
    if (enabled(Group.RELEASE)) {
        add(stringResource(R.string.metadata_genre), meta.takeText { it.genre })
        // The bare year is preferred over the free-text date when both exist: it is the one a
        // reader wants and the one that always fits, and printing both is the same fact twice.
        add(stringResource(R.string.metadata_year), meta.year.takeIf { it > 0 }?.toString()
                ?: meta.takeText { it.date })
        add(stringResource(R.string.metadata_released), meta.takeText { it.releaseDate })
        add(stringResource(R.string.metadata_label), meta.takeText { it.label })
        add(stringResource(R.string.metadata_country), meta.takeText { it.releaseCountry })
    }
    if (enabled(Group.CREDITS)) {
        add(stringResource(R.string.metadata_composer), meta.takeText { it.composer })
        add(stringResource(R.string.metadata_writer), meta.takeText { it.writer })
        add(stringResource(R.string.metadata_author), meta.takeText { it.author })
    }
    if (enabled(Group.PLAYBACK)) {
        // Only when it is not 1x - see formatSpeed. A row reading "1x" on every track is a
        // constant, and a constant is not information.
        add(stringResource(R.string.metadata_speed),
                TrackMetadataFields.formatSpeed(state.playbackSpeed))
        add(stringResource(R.string.metadata_output), outputLabel(meta.outputKind))
        add(stringResource(R.string.metadata_origin), originLabel(meta.takeText { it.mediaUri }))
        add(stringResource(R.string.metadata_host),
                TrackMetadataFields.uriHost(meta.takeText { it.mediaUri }))
        add(stringResource(R.string.metadata_file), meta.takeText { it.fileName })
        add(stringResource(R.string.metadata_download), downloadLabel(meta.downloadStatus))
    }
    if (enabled(Group.TECHNICAL)) {
        add(stringResource(R.string.metadata_codec),
                TrackMetadataFields.formatCodec(meta.takeText { it.mimeType }))
        add(stringResource(R.string.metadata_bitrate),
                TrackMetadataFields.formatBitrate(meta.bitrate))
        add(stringResource(R.string.metadata_sample_rate),
                TrackMetadataFields.formatSampleRate(meta.sampleRateHz))
        add(stringResource(R.string.metadata_channels),
                TrackMetadataFields.formatChannels(meta.channels))
        add(stringResource(R.string.metadata_file_size),
                TrackMetadataFields.formatFileSize(meta.fileSizeBytes))
    }
    if (enabled(Group.IDENTIFIERS)) {
        add(stringResource(R.string.metadata_isrc), meta.takeText { it.isrc })
        add(stringResource(R.string.metadata_recording_id), meta.takeText { it.recordingMbid })
        add(stringResource(R.string.metadata_release_id), meta.takeText { it.releaseMbid })
    }
    // Ungrouped, both of them: the playing app is provenance rather than a kind of detail, and
    // the free description is the one row a player with no tags at all may still fill.
    add(stringResource(R.string.metadata_about), meta.takeText { it.description })
    add(stringResource(R.string.metadata_source), meta.takeText { it.sourceLabel })

    // Last, and across the full width: the address itself. It is the only value on this screen that
    // a label column physically cannot hold - a signed CDN URL runs to several hundred characters -
    // so it takes three lines of the whole width and is still routinely cut. Kept anyway, and kept
    // *last*, because the rows above already answer "where is this coming from" in a form that
    // fits; this one is for when the exact address is what you came for. Bottom of the priority
    // list means it is also the first thing a small screen drops.
    if (enabled(Group.PLAYBACK)) {
        add(stringResource(R.string.metadata_url), meta.takeText { it.mediaUri }, lines = 3,
                wide = true)
    }
    return rows
}

/** Localised on the watch rather than the phone - see `metadata.proto`'s note on `outputKind`. */
@Composable
private fun outputLabel(code: Int): String? = when (TrackMetadataFields.Output.fromCode(code)) {
    TrackMetadataFields.Output.SPEAKER -> stringResource(R.string.metadata_output_speaker)
    TrackMetadataFields.Output.WIRED -> stringResource(R.string.metadata_output_wired)
    TrackMetadataFields.Output.BLUETOOTH -> stringResource(R.string.metadata_output_bluetooth)
    TrackMetadataFields.Output.USB -> stringResource(R.string.metadata_output_usb)
    TrackMetadataFields.Output.REMOTE -> stringResource(R.string.metadata_output_remote)
    TrackMetadataFields.Output.UNKNOWN -> null
}

/**
 * Whether the audio is a stream or a file, from the URI's scheme.
 *
 * `content://` deliberately reads as "on this phone" rather than as its scheme name: a provider URI
 * is how Android hands out a local file, and "Content" would be describing the plumbing to someone
 * asking about their music. [TrackMetadataFields.Origin.OTHER] gets no row at all - an app's
 * private scheme is honestly unknown, and naming it would put a confident wrong word on screen.
 */
@Composable
private fun originLabel(uri: String?): String? =
        when (TrackMetadataFields.Origin.of(uri)) {
            TrackMetadataFields.Origin.STREAM -> stringResource(R.string.metadata_origin_stream)
            TrackMetadataFields.Origin.FILE,
            TrackMetadataFields.Origin.CONTENT -> stringResource(R.string.metadata_origin_local)
            TrackMetadataFields.Origin.OTHER, null -> null
        }

/** `MediaDescription.STATUS_*`; -1 is the phone saying the player published nothing. */
@Composable
private fun downloadLabel(status: Int): String? = when (status) {
    0 -> stringResource(R.string.metadata_download_no)
    1 -> stringResource(R.string.metadata_download_running)
    2 -> stringResource(R.string.metadata_download_yes)
    else -> null
}

/** Proto string fields default to `""` rather than being absent, and an empty row is a row claiming
 *  a field exists when it does not. */
private inline fun TrackMetadata.takeText(select: (TrackMetadata) -> String?): String? =
        select(this)?.trim()?.takeIf { it.isNotEmpty() }
