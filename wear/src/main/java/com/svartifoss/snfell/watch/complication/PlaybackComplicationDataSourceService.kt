package com.svartifoss.snfell.watch.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.DynamicComplicationText
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.TrackTimeFormat
import com.svartifoss.snfell.common.displayArtist
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.watch.util.WatchLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant

/**
 * The second complication: playback rather than artwork - a play/pause glyph, the elapsed time and
 * the track's progress, and a tap that plays or pauses without opening anything.
 *
 * Three shapes, for three kinds of slot:
 * - **Ranged value**: the progress through the track, the glyph and the elapsed time. Nothing in
 *   the media apps this was compared with offers one, and it is the one that fits the round slot
 *   most faces have.
 * - **Short text**: the glyph and the elapsed time, with the title as the small line for faces that
 *   draw one.
 * - **Monochromatic image**: the glyph alone - the smallest possible play/pause button.
 *
 * It is a separate source from [AlbumArtComplicationDataSourceService] because the two answer
 * different questions and a face shows one answer per slot: that one *is* the cover and opens the
 * app, this one is a control. Its kdoc also explains why it never offered short text - most faces
 * draw only the text of one, so the cover would have gone missing - which is exactly the shape that
 * suits a control.
 *
 * **The time moves without this app.** The phone does not publish while only the position changes,
 * so on Wear OS 4 and later the value and the elapsed time are platform expressions counted from
 * the moment the track started ([PlaybackComplicationMath.trackStartEpochMs]); the face's renderer
 * evaluates them every second on its own and this source is asked again only when something else
 * changes (see [com.svartifoss.snfell.watch.communication.GlanceableSurfaces]). Older watches show
 * the fallback values, which are true when written and stand still until the next state.
 */
class PlaybackComplicationDataSourceService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
            withContext(Dispatchers.IO) {
                build(request.complicationType, readState())
            }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
            build(type, MusicState.newBuilder()
                    .setTitle("Preview Track")
                    .setPlaying(true)
                    .setPositionMs(83_000L)
                    .setDurationMs(214_000L)
                    .build(), preview = true)

    private suspend fun readState(): MusicState? = try {
        val buffer = Wearable.getDataClient(this).getDataItems(
                Uri.parse("wear://*${CommPaths.DATA_MUSIC_STATE}"),
                DataClient.FILTER_LITERAL
        ).await()
        val item = try {
            buffer.firstOrNull()?.freeze()
        } finally {
            buffer.release()
        }
        item?.let { MusicState.parseFrom(it.data) }?.takeIf { !it.error }
    } catch (e: Exception) {
        // A Data Layer that cannot be read right now is drawn as "nothing playing": unlike the
        // cover there is nothing cached worth keeping, and a stale "pause" would offer a tap that
        // does the opposite of what it shows.
        Timber.w(e, "Playback complication: could not read the music state")
        null
    }

    private fun build(type: ComplicationType, state: MusicState?, preview: Boolean = false): ComplicationData? {
        val strings = WatchLanguage.localized(this)
        val hasTrack = state != null && (state.title.isNotBlank() || state.durationMs > 0L)
        // Shown as what a tap will do, the way every transport control is: a pause glyph while
        // playing. A toggle sent a moment ago is drawn as done (see the receiver).
        val playing = hasTrack && PlaybackComplicationToggleReceiver.displayedPlaying(state!!.playing)
        val glyph = MonochromaticImage.Builder(Icon.createWithResource(this,
                if (playing) com.svartifoss.snfell.common.R.drawable.action_pause_filled
                else com.svartifoss.snfell.common.R.drawable.action_play_filled)).build()
        val description = PlainComplicationText.Builder(when {
            !hasTrack -> strings.getString(R.string.complication_playback_idle_description)
            playing -> strings.getString(R.string.complication_playback_pause_description, describe(state!!))
            else -> strings.getString(R.string.complication_playback_play_description, describe(state!!))
        }).build()
        val tap = if (preview) null else PlaybackComplicationToggleReceiver.pendingIntent(this)

        if (!hasTrack) {
            val idle = PlainComplicationText.Builder(strings.getString(R.string.complication_playback_idle)).build()
            return when (type) {
                ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(idle, description)
                        .setMonochromaticImage(glyph).setTapAction(tap).build()
                ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(0f, 0f, 1f, description)
                        .setMonochromaticImage(glyph).setTapAction(tap).build()
                ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(glyph, description)
                        .setTapAction(tap).build()
                else -> null
            }
        }

        val now = System.currentTimeMillis()
        val math = PlaybackComplicationMath(
                positionMs = state!!.positionMs,
                durationMs = state.durationMs,
                // A toggle just sent freezes the drawn position rather than moving it: the phone
                // has the real answer and will say so in a moment.
                playing = playing && state.playing,
                speed = state.playbackSpeed,
                sampledAtEpochMs = state.positionUpdateTime)
        val animate = math.playing && !preview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val elapsed = elapsedText(math, now, animate)

        return when (type) {
            ComplicationType.RANGED_VALUE -> {
                val fallback = math.fractionAt(now)
                val builder = if (animate && math.hasLength) {
                    RangedValueComplicationData.Builder(progressExpression(math, now), fallback, 0f, 1f, description)
                } else {
                    RangedValueComplicationData.Builder(fallback, 0f, 1f, description)
                }
                builder.setMonochromaticImage(glyph)
                        .setText(elapsed)
                        .setTapAction(tap)
                        .build()
            }
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(elapsed, description)
                    .setMonochromaticImage(glyph)
                    .apply {
                        if (state.title.isNotBlank()) {
                            setTitle(PlainComplicationText.Builder(state.title).build())
                        }
                    }
                    .setTapAction(tap)
                    .build()
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(glyph, description)
                    .setTapAction(tap)
                    .build()
            else -> null
        }
    }

    private fun describe(state: MusicState): String {
        val artist = state.displayArtist()
        return if (artist.isNotBlank()) "${state.title}, $artist" else state.title
    }

    /**
     * The elapsed time, written the way every other readout in the app writes it ([TrackTimeFormat]).
     * While playing on Wear OS 4+ it is an expression the face counts up itself; the fallback is
     * the time at [nowEpochMs].
     */
    private fun elapsedText(math: PlaybackComplicationMath, nowEpochMs: Long, animate: Boolean): ComplicationText {
        val fallback = TrackTimeFormat.format(math.positionAt(nowEpochMs))
        if (!animate) return PlainComplicationText.Builder(fallback).build()
        return DynamicComplicationText(elapsedExpression(math, nowEpochMs), fallback)
    }

    /** Seconds of the track played so far, counted by the platform from the track's start. */
    private fun playedSeconds(math: PlaybackComplicationMath, nowEpochMs: Long): DynamicFloat =
            DynamicInstant.withSecondsPrecision(Instant.ofEpochMilli(math.trackStartEpochMs(nowEpochMs)))
                    .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
                    .toIntSeconds()
                    .asFloat()
                    .times(math.effectiveSpeed)

    /** Progress 0..1, clamped at both ends - a watch clock slightly behind the phone's must not draw
     *  a negative arc, and a track that has run long must not overfill. */
    private fun progressExpression(math: PlaybackComplicationMath, nowEpochMs: Long): DynamicFloat {
        val raw = playedSeconds(math, nowEpochMs).times(1000f / math.durationMs)
        val atMostOne = DynamicFloat.onCondition(raw.gt(1f)).use(1f).elseUse(raw)
        return DynamicFloat.onCondition(atMostOne.lt(0f)).use(0f).elseUse(atMostOne)
    }

    /** "m:ss" under an hour and "h:mm:ss" from the first hour - [TrackTimeFormat]'s rule, as an
     *  expression, so the counted text and the fallback never disagree about the format. Capped at
     *  the track's length when it has one. */
    private fun elapsedExpression(math: PlaybackComplicationMath, nowEpochMs: Long): DynamicString {
        var seconds = playedSeconds(math, nowEpochMs)
        if (math.hasLength) {
            val cap = math.durationMs / 1000f
            seconds = DynamicFloat.onCondition(seconds.gt(cap)).use(cap).elseUse(seconds)
        }
        seconds = DynamicFloat.onCondition(seconds.lt(0f)).use(0f).elseUse(seconds)
        val total: DynamicInt32 = seconds.asInt()
        val twoDigits = DynamicInt32.IntFormatter.Builder().setMinIntegerDigits(2).build()
        val secs = total.rem(60).format(twoDigits)
        val minutesAndSeconds = total.div(60).format()
                .concat(DynamicString.constant(":"))
                .concat(secs)
        // Only a track that can reach an hour needs the second form evaluated at all.
        if (math.hasLength && math.durationMs < ONE_HOUR_MS) return minutesAndSeconds
        val hoursMinutesSeconds = total.div(3600).format()
                .concat(DynamicString.constant(":"))
                .concat(total.rem(3600).div(60).format(twoDigits))
                .concat(DynamicString.constant(":"))
                .concat(secs)
        return DynamicString.onCondition(total.gte(3600))
                .use(hoursMinutesSeconds)
                .elseUse(minutesAndSeconds)
    }

    private companion object {
        const val ONE_HOUR_MS = 3_600_000L
    }
}
