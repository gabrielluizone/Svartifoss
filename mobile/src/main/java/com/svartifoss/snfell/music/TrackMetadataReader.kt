package com.svartifoss.snfell.music

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.session.MediaController
import android.net.Uri
import android.provider.OpenableColumns
import com.svartifoss.snfell.common.TrackMetadataFields
import com.svartifoss.snfell.proto.TrackMetadata
import timber.log.Timber

/**
 * Everything the phone can say about the playing track without asking anyone.
 *
 * Two sources, in cost order. The playing app's own [MediaMetadata] is free and instant - it is
 * already in memory, and it is where album, credits, genre, year and track position come from when
 * the player bothers to publish them. The **file** is the second, and only local tracks have one:
 * bitrate, sample rate, channel count and size are not `MediaMetadata` fields at all, they are
 * properties of the encoded audio, so the only way to know them is to open it.
 *
 * Absent is absent. Every field here is written only when it has a real value, because the face
 * draws a row per populated field - so a streaming track that publishes three tags produces three
 * rows rather than twenty labelled blanks. That is the rule the whole screen rests on: it never
 * says "unknown", it just does not say.
 */
object TrackMetadataReader {

    /**
     * `MediaMetadataCompat.METADATA_KEY_DOWNLOAD_STATUS`, spelled out.
     *
     * The framework's own [MediaMetadata] never declared this key - only AndroidX's compat wrapper
     * does. The value it writes lands in the very bundle the framework class reads back, so the
     * string *is* the whole contract, and taking a dependency on media-compat here would be adding
     * one to name a constant. Every other key on this screen comes from [MediaMetadata] itself,
     * which is why this one is called out rather than quietly inlined.
     */
    private const val KEY_DOWNLOAD_STATUS = "android.media.metadata.DOWNLOAD_STATUS"

    /**
     * Reads what [controller] is playing.
     *
     * [probeFile] gates the expensive half. It is skipped when the media-read grant is missing or
     * the track has no local file, and both of those degrade in silence on purpose: a metadata
     * screen is a bad place to start demanding permissions the user did not come here to give, and
     * a streaming track genuinely has no file to describe.
     */
    fun read(context: Context, controller: MediaController?, probeFile: Boolean): TrackMetadata {
        val builder = TrackMetadata.newBuilder()
        val meta = controller?.metadata ?: return builder.build()

        meta.text(MediaMetadata.METADATA_KEY_TITLE)?.let { builder.title = it }
        meta.text(MediaMetadata.METADATA_KEY_ARTIST)?.let { builder.artist = it }
        meta.text(MediaMetadata.METADATA_KEY_ALBUM)?.let { builder.album = it }
        meta.text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.let { builder.albumArtist = it }
        meta.text(MediaMetadata.METADATA_KEY_COMPOSER)?.let { builder.composer = it }
        meta.text(MediaMetadata.METADATA_KEY_WRITER)?.let { builder.writer = it }
        meta.text(MediaMetadata.METADATA_KEY_AUTHOR)?.let { builder.author = it }
        meta.text(MediaMetadata.METADATA_KEY_GENRE)?.let { builder.genre = it }
        meta.text(MediaMetadata.METADATA_KEY_DATE)?.let { builder.date = it }

        meta.number(MediaMetadata.METADATA_KEY_YEAR)?.let { builder.year = it }
        meta.number(MediaMetadata.METADATA_KEY_TRACK_NUMBER)?.let { builder.trackNumber = it }
        meta.number(MediaMetadata.METADATA_KEY_NUM_TRACKS)?.let { builder.trackCount = it }
        meta.number(MediaMetadata.METADATA_KEY_DISC_NUMBER)?.let { builder.discNumber = it }
        meta.number(MediaMetadata.METADATA_KEY_DURATION)?.let { builder.durationMs = it }
        // Only written when true: "Compilation: no" is a row about the absence of a property, which
        // is exactly the kind of filler this screen refuses.
        if (meta.getLong(MediaMetadata.METADATA_KEY_COMPILATION) > 0L) {
            builder.compilation = true
        }

        // Dropped when it merely repeats a line the screen already shows, which is what it holds
        // for most players. Kept when it does not - that is the case worth having it for: an upload
        // with no album and no year often still carries something here.
        meta.text(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
                ?.takeUnless { it.equalsAnyOf(builder.title, builder.artist, builder.album) }
                ?.let { builder.description = it }

        controller.packageName?.let { pkg ->
            builder.sourceLabel = appLabel(context, pkg) ?: pkg
        }

        // Download status is one of the few things a *streaming* client publishes that says
        // something the tags cannot: whether this track is on the device or is about to be pulled
        // over the network. Absent for almost every player, which is why -1 has to mean "not said"
        // - 0 is the real answer "not downloaded".
        if (meta.containsKey(KEY_DOWNLOAD_STATUS)) {
            builder.downloadStatus = meta.getLong(KEY_DOWNLOAD_STATUS).toInt()
        }

        readPlayback(context, controller, builder)

        val uri = mediaUri(meta)
        uri?.let { builder.mediaUri = it.toString() }

        val local = uri?.takeIf { it.scheme == "content" || it.scheme == "file" }
        if (local != null) {
            fileName(context, local)?.let { builder.fileName = it }
            // Gated separately from the name: reading a file's *contents* needs the media grant,
            // while its name comes from the provider and does not. Losing the bitrate row should
            // not also lose the row that says which file it is.
            if (probeFile) probe(context, local, builder)
        }
        return builder.build()
    }

    /**
     * How this playback is happening, as opposed to what is playing.
     *
     * Cheap enough to always read, and the one block a streaming track can fill when the file
     * details are empty by construction.
     */
    private fun readPlayback(
            context: Context,
            controller: MediaController,
            builder: TrackMetadata.Builder
    ) {
        val info = try {
            controller.playbackInfo
        } catch (e: Exception) {
            null
        }
        info?.playbackType?.takeIf { it > 0 }?.let { builder.playbackType = it }
        builder.outputKind = outputKind(context, info).code
    }

    /**
     * Where the sound is actually coming out.
     *
     * Casting is answered first and from the session itself, because it is the one case where the
     * phone's own audio route says nothing at all about what the user is hearing - the audio is not
     * on this device.
     *
     * Otherwise it is read from the *connected* outputs rather than from a "current route" API,
     * because there is no reliable one at this project's minimum API. `getDevices` reports what is
     * available, and for the three things a listener actually distinguishes - headphones,
     * Bluetooth, the speaker - available is a good proxy: Android routes media to a connected
     * headset in preference to the speaker, so the priority order below is the routing order. It
     * can be wrong (a paired-but-idle A2DP device left connected), and the honest scope of the row
     * is "where this would come out", which is what a listener is asking anyway.
     */
    private fun outputKind(
            context: Context,
            info: MediaController.PlaybackInfo?
    ): TrackMetadataFields.Output {
        if (info?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
            return TrackMetadataFields.Output.REMOTE
        }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return TrackMetadataFields.Output.UNKNOWN
        val types = try {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
        } catch (e: Exception) {
            return TrackMetadataFields.Output.UNKNOWN
        }
        // Every one of these is a compile-time int constant, so naming a type introduced after
        // minSdk (USB_HEADSET at 26, BLE_HEADSET at 31) is inlined as a literal and resolves
        // nothing at runtime - the trap CLAUDE.md records is about *methods*, not constants.
        return when {
            types.any {
                it == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it == AudioDeviceInfo.TYPE_BLE_SPEAKER
            } -> TrackMetadataFields.Output.BLUETOOTH
            types.any {
                it == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            } -> TrackMetadataFields.Output.WIRED
            types.any {
                it == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        it == AudioDeviceInfo.TYPE_USB_DEVICE
            } -> TrackMetadataFields.Output.USB
            types.contains(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) ->
                TrackMetadataFields.Output.SPEAKER
            else -> TrackMetadataFields.Output.UNKNOWN
        }
    }

    /**
     * The file's own name.
     *
     * Queried rather than taken from the URI: a MediaStore `content://` URI ends in the row's
     * numeric id far more often than in anything readable, so `lastPathSegment` would put "1043" on
     * the screen. Only `file://` URIs are named by their own path, and there the last segment is
     * the name by definition.
     */
    private fun fileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment?.takeIf { it.isNotBlank() }
        return try {
            context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use null
                        cursor.getString(0)?.trim()?.takeIf { it.isNotEmpty() }
                    }
        } catch (e: Exception) {
            Timber.v(e, "Could not read the playing track's file name")
            null
        }
    }

    private fun String.equalsAnyOf(vararg others: String?): Boolean =
            others.any { it != null && it.trim().equals(this.trim(), ignoreCase = true) }

    /** Blank tags are as absent as missing ones - several players publish `""` rather than nothing,
     *  and an empty row is a row that says the field exists when it does not. */
    private fun MediaMetadata.text(key: String): String? =
            getString(key)?.trim()?.takeIf { it.isNotEmpty() }

    /** Zero is how [MediaMetadata] reports "not set" for every numeric key, so it is not a value. */
    private fun MediaMetadata.number(key: String): Long? =
            getLong(key).takeIf { it > 0L }

    /**
     * The URI the player published for this item, whatever its scheme.
     *
     * Sending it is safe; *opening* it is not, which is why the caller filters to `content://` and
     * `file://` before any of the probing below runs. A streaming player commonly publishes an
     * `https://` media URI, and handing that to a [MediaMetadataRetriever] would make this screen
     * perform a network download to fill in a bitrate row - the exact thing the "never block on the
     * network" rule exists to prevent, and it would do it without the user having turned anything
     * on.
     */
    private fun mediaUri(meta: MediaMetadata): Uri? {
        val raw = meta.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)?.takeIf { it.isNotBlank() }
                ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    /**
     * Fills in what only the encoded audio knows.
     *
     * Two readers, because neither alone covers it: [MediaMetadataRetriever] is the one that reports
     * a bitrate, and [MediaExtractor] is the one that reports channel count - and its sample rate
     * works back to API 16 where the retriever's own key only arrives at 31. Every step is
     * individually guarded: a malformed or unreadable file must cost this screen its technical rows
     * and nothing else.
     */
    private fun probe(context: Context, uri: Uri, builder: TrackMetadata.Builder) {
        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toLongOrNull()
                        ?.takeIf { it > 0L }
                        ?.let { builder.bitrate = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { builder.mimeType = it }
            }
        } catch (e: Exception) {
            Timber.v(e, "Could not read container metadata for the playing track")
        }

        try {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                for (track in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(track)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (!mime.startsWith("audio/")) continue
                    if (!builder.hasMimeType()) builder.mimeType = mime
                    format.integer(MediaFormat.KEY_SAMPLE_RATE)
                            ?.let { builder.sampleRateHz = it.toLong() }
                    format.integer(MediaFormat.KEY_CHANNEL_COUNT)
                            ?.let { builder.channels = it }
                    // Only as a fallback: the retriever reports the *container's* overall rate,
                    // which is the number people quote, while this is the audio track's own. They
                    // agree for a plain music file and the container's is the better answer when
                    // both exist - but plenty of formats leave the retriever's key empty.
                    if (!builder.hasBitrate()) {
                        format.integer(MediaFormat.KEY_BIT_RATE)
                                ?.let { builder.bitrate = it.toLong() }
                    }
                    break
                }
            } finally {
                extractor.release()
            }
        } catch (e: Exception) {
            Timber.v(e, "Could not read the audio format of the playing track")
        }

        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it > 0L }?.let { builder.fileSizeBytes = it }
            }
        } catch (e: Exception) {
            Timber.v(e, "Could not measure the playing track's file")
        }
    }

    /** `getInteger` throws rather than returning a default when the key is absent, and plenty of
     *  formats omit both of the ones read here. */
    private fun MediaFormat.integer(key: String): Int? =
            if (containsKey(key)) runCatching { getInteger(key) }.getOrNull()?.takeIf { it > 0 }
            else null

    private fun appLabel(context: Context, packageName: String): String? = try {
        val manager = context.packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        null
    }

    /** [MediaMetadataRetriever] only became `AutoCloseable` at API 29, and this module still
     *  supports 23 - so the release is arranged here rather than by the language. */
    private inline fun <T> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> T): T =
            try {
                block(this)
            } finally {
                runCatching { release() }
            }
}
