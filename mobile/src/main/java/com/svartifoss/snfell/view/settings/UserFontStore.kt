package com.svartifoss.snfell.view.settings

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.UserFontContract
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * The one typeface the user imported from their own storage: importing it, holding it, rendering
 * with it on the phone, and getting it onto the wrist.
 *
 * ## Why the file is copied rather than referenced
 *
 * The picker hands back a `content://` URI, and a URI is a permission, not a file. The grant can be
 * revoked, the backing app uninstalled, the file moved or deleted - and the moment any of that
 * happens the watch face falls back to a default typeface with nothing on either screen explaining
 * why. Copying the bytes into [filesDir] at import turns a font from something the app *may* be
 * able to read into something it has. It also puts the file where `ConfigBackup`'s internal-files
 * sweep will carry it, so an imported font survives a reinstall the way the rest of the
 * configuration does.
 *
 * ## Validation is by parse, not by name
 *
 * The extension and the reported MIME type are both advisory - providers disagree about font types
 * and plenty report `application/octet-stream` for a perfectly good TTF - so the real check is
 * asking the platform's own font loader to build a [Typeface] from the copied file. A file that
 * parses here is a file both this app's preview and the watch can render, because both use the same
 * loader; a file that does not is rejected while there is still a screen to say so.
 */
object UserFontStore {

    /** Its own directory under `filesDir` so the backup sweep sees one predictable path, and so
     *  clearing the font is a directory delete rather than a guess at which file is current. */
    private const val FONT_DIRECTORY = "userFont"

    /**
     * Fixed name regardless of what the imported file was called.
     *
     * The display name is a preference; the file is storage. Keeping the original name here would
     * mean the directory could hold two fonts after a re-import that changed the extension, and
     * nothing would say which one was live.
     */
    private const val FONT_FILE = "user_font.ttf"

    /** Result of an import attempt, with enough detail for the settings screen to say what to do
     *  about it. Each case corresponds to a distinct message; none of them is "something failed". */
    sealed class ImportResult {
        /** The font is stored and already on its way to the watch. */
        data class Imported(val displayName: String) : ImportResult()

        /** The file is larger than [UserFontContract.MAX_FONT_BYTES], with its actual size so the
         *  message can name it - "too large" without a number leaves nothing to act on. */
        data class TooLarge(val byteCount: Long) : ImportResult()

        /** The file is not a font this device can render - a WOFF, a truncated download, or
         *  something with a font's extension and another format's contents. */
        object NotAFont : ImportResult()

        /** The picked URI could not be read at all: a revoked grant, a provider that went away, or
         *  a file removed between the picker showing it and this running. */
        object Unreadable : ImportResult()
    }

    private fun fontDirectory(context: Context): File =
            File(context.applicationContext.filesDir, FONT_DIRECTORY)

    /** The stored font file, whether or not it currently exists. */
    fun fontFile(context: Context): File = File(fontDirectory(context), FONT_FILE)

    /** True when a usable imported font is on disk. Checks the file rather than the preference,
     *  because the two can disagree - a restore brings the file back, and a failed write leaves the
     *  name behind - and the file is the half that decides whether anything can be rendered. */
    fun hasFont(context: Context): Boolean =
            fontFile(context).let { it.isFile && it.length() > 0L }

    /** The imported font's display name, or null when none is loaded. */
    fun displayName(context: Context): String? {
        if (!hasFont(context)) return null
        return PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
                .getString(MiscPreferences.USER_FONT_NAME.key, null)
                ?.takeIf { it.isNotBlank() }
    }

    /**
     * Copies [uri] into internal storage, validates it, and publishes it to the watch.
     *
     * The copy goes to a temporary file first and is only promoted once it has parsed as a font, so
     * a rejected import cannot leave the previous working font replaced by a broken one - the case
     * that matters, since the font may be in use on the wrist at the time.
     *
     * Blocking on I/O. Call it off the main thread.
     */
    fun import(context: Context, uri: Uri): ImportResult {
        val appContext = context.applicationContext
        val directory = fontDirectory(appContext)
        if (!directory.isDirectory && !directory.mkdirs()) {
            Timber.w("Could not create the imported-font directory")
            return ImportResult.Unreadable
        }

        val staging = File(directory, "$FONT_FILE.importing")
        val bytes = try {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Timber.w(e, "Could not read the picked font")
            null
        } ?: return ImportResult.Unreadable

        if (bytes.size > UserFontContract.MAX_FONT_BYTES) {
            return ImportResult.TooLarge(bytes.size.toLong())
        }
        if (!UserFontContract.isAcceptableSize(bytes.size.toLong())) {
            return ImportResult.NotAFont
        }

        try {
            staging.writeBytes(bytes)
        } catch (e: IOException) {
            Timber.w(e, "Could not stage the imported font")
            staging.delete()
            return ImportResult.Unreadable
        }

        if (!parsesAsFont(staging)) {
            staging.delete()
            return ImportResult.NotAFont
        }

        val target = fontFile(appContext)
        if (!staging.renameTo(target)) {
            // renameTo fails when the target exists on some filesystems, which is the ordinary
            // re-import case rather than an error.
            target.delete()
            if (!staging.renameTo(target)) {
                Timber.w("Could not promote the imported font into place")
                staging.delete()
                return ImportResult.Unreadable
            }
        }

        val displayName = readDisplayName(appContext, uri) ?: target.name
        PreferenceManager.getDefaultSharedPreferences(appContext).edit()
                .putString(MiscPreferences.USER_FONT_NAME.key, displayName)
                .apply()
        cachedTypeface = null
        cachedTypefaceFingerprint = null
        publishToWatch(appContext)
        return ImportResult.Imported(displayName)
    }

    /**
     * Removes the imported font and tells the watch it is gone.
     *
     * Deliberately does **not** rewrite any font preference that names it. A face left pointing at
     * a font that is no longer there resolves through the catalog's ordinary unknown-key fallback,
     * which is the same behaviour a theme from a newer build gets, and it means re-importing a font
     * restores every face that was using one instead of requiring each to be set again.
     */
    fun clear(context: Context) {
        val appContext = context.applicationContext
        fontFile(appContext).delete()
        PreferenceManager.getDefaultSharedPreferences(appContext).edit()
                .remove(MiscPreferences.USER_FONT_NAME.key)
                .apply()
        cachedTypeface = null
        cachedTypefaceFingerprint = null
        publishToWatch(appContext)
    }

    /**
     * Puts the current font - or its absence - on the Data Layer for the watch.
     *
     * Called on import, on clear, and once per process start from `WearMusicCenter`. That last one
     * is not redundant: a font imported by a build before this path existed, or while the watch was
     * unreachable, has no other occasion to be sent, which is the staleness
     * `WatchThemeRepository.publishAvailableThemes` exists to repair for the theme list.
     *
     * The item is published even with no font attached, because "the user cleared their font" and
     * "this phone has never had one" have to be distinguishable on the wrist - a watch that already
     * holds a font needs to be told to stop using it.
     */
    fun publishToWatch(context: Context) {
        val appContext = context.applicationContext
        val file = fontFile(appContext)
        val bytes = if (file.isFile && file.length() in
                UserFontContract.MIN_FONT_BYTES.toLong()..UserFontContract.MAX_FONT_BYTES.toLong()) {
            try {
                file.readBytes()
            } catch (e: IOException) {
                Timber.w(e, "Could not read the stored font for transmission")
                null
            }
        } else {
            null
        }

        val request = PutDataRequest.create(CommPaths.DATA_USER_FONT).apply {
            // The fingerprint is the item's payload, so an unchanged font produces a byte-identical
            // DataItem that the Data Layer drops rather than re-delivering - which is what makes
            // the once-per-process republish above free when nothing has changed.
            data = (bytes?.let(UserFontContract::fingerprint) ?: "").toByteArray(Charsets.UTF_8)
            bytes?.let { putAsset(CommPaths.ASSET_USER_FONT, Asset.createFromBytes(it)) }
            setUrgent()
        }
        Wearable.getDataClient(appContext).putDataItem(request)
                .addOnFailureListener { error ->
                    Timber.w(error, "Could not send the imported font to the watch")
                }
    }

    /**
     * The imported font as a [Typeface], for the phone's own watch preview and font picker.
     *
     * Cached per process and keyed by the file's fingerprint, so re-importing a different font
     * replaces it rather than being masked by the previous one. Null when no font is loaded, which
     * every caller resolves the same way it resolves an unknown catalog key.
     */
    fun typeface(context: Context): Typeface? {
        val file = fontFile(context.applicationContext)
        if (!file.isFile || file.length() == 0L) {
            cachedTypeface = null
            cachedTypefaceFingerprint = null
            return null
        }
        val fingerprint = "${file.length()}:${file.lastModified()}"
        cachedTypeface?.takeIf { cachedTypefaceFingerprint == fingerprint }?.let { return it }
        val loaded = try {
            Typeface.createFromFile(file)
        } catch (e: RuntimeException) {
            // createFromFile throws a bare RuntimeException for a font it cannot parse. A stored
            // file that stops parsing is a corrupted copy rather than a rejected import, so it is
            // logged and treated as absent rather than deleted out from under the user.
            Timber.w(e, "Stored user font could not be loaded")
            null
        }
        cachedTypeface = loaded
        cachedTypefaceFingerprint = fingerprint.takeIf { loaded != null }
        return loaded
    }

    private var cachedTypeface: Typeface? = null
    private var cachedTypefaceFingerprint: String? = null

    /**
     * Whether the platform's font loader accepts [file].
     *
     * `createFromFile` does not report failure by returning null - it throws for a malformed font,
     * and for some inputs returns the default typeface instead. Both are treated as a rejection:
     * silently importing something that renders as Roboto would look exactly like the feature not
     * working.
     */
    private fun parsesAsFont(file: File): Boolean = try {
        val typeface = Typeface.createFromFile(file)
        typeface != null && typeface != Typeface.DEFAULT
    } catch (e: RuntimeException) {
        Timber.d(e, "Picked file is not a usable font")
        false
    }

    /**
     * The file's own name, for the picker row.
     *
     * Via [OpenableColumns.DISPLAY_NAME] rather than the URI's last path segment, which for a
     * MediaStore or Downloads URI is a numeric row id far more often than a name - the same trap
     * `TrackMetadataReader` documents for media files.
     */
    private fun readDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && cursor.columnCount > 0) {
                        cursor.getString(0)?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                }
    } catch (e: Exception) {
        Timber.d(e, "Could not read the picked font's name")
        null
    }
}
