package com.svartifoss.snfell.watch.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.svartifoss.snfell.common.UserFontContract
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * The watch's copy of the typeface the user imported on their phone.
 *
 * ## Why a process-scoped holder rather than a parameter
 *
 * [watchFontFamily] resolves a catalog key to a Compose [FontFamily] and takes no [Context] - it is
 * called from about thirty places across the faces, the chrome and the panels, and threading a
 * context through all of them to serve one catalog entry would be a large change to the wrong
 * places. So this holds an application context, seeded once in `WearMusicCenter.onCreate`, the same
 * shape `AlbumPaletteCache` uses for the same reason. A resolve before that seeding returns null
 * and the caller falls through to its ordinary unknown-key fallback, which is correct rather than
 * merely safe: on a watch that has never been sent a font, that is the honest answer.
 *
 * ## Why the file is written before the typeface is built
 *
 * The bytes arrive on a Data Layer callback, and the font has to survive the app's process dying -
 * which on a watch it does constantly. Writing first and loading from the file means the awake path
 * and the cold-start path load the identical way, so a font that renders after an import also
 * renders after a reboot; building a typeface from the received array and only persisting it
 * afterwards would have left those two paths able to disagree.
 */
object UserFont {

    private const val FONT_DIRECTORY = "userFont"
    private const val FONT_FILE = "user_font.ttf"

    /** Set once from `WearMusicCenter.onCreate`, so the context-free resolvers can reach storage. */
    @Volatile
    private var appContext: Context? = null

    private var cachedTypeface: Typeface? = null
    private var cachedFamily: FontFamily? = null

    /** What each cache above was built from, tracked separately: the two are populated by
     *  different callers at different times, so one shared key would have loading the Compose
     *  family silently validate a stale Typeface (and the reverse). */
    private var cachedTypefaceKey: String? = null
    private var cachedFamilyKey: String? = null

    /** The fingerprint of the file currently on disk, as last written or last read. */
    private var storedFingerprint: String? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private fun fontFile(context: Context): File =
            File(File(context.applicationContext.filesDir, FONT_DIRECTORY), FONT_FILE)

    /** True when this watch holds a usable imported font. */
    fun isAvailable(): Boolean = appContext?.let { fontFile(it).let { f -> f.isFile && f.length() > 0L } } == true

    /**
     * True when the font this watch already holds is the one [fingerprint] names.
     *
     * Lets the receiver skip a multi-megabyte write and a typeface rebuild for a re-delivery of the
     * same font, which is the ordinary case: the phone republishes the item once per process start
     * so a watch that missed the original import still gets it.
     */
    fun holds(fingerprint: String): Boolean =
            fingerprint.isNotEmpty() && storedFingerprint == fingerprint && isAvailable()

    /**
     * Stores newly received font [bytes] and drops the cached typeface built from the previous one.
     *
     * Returns true when the font changed, which the caller uses to decide whether anything on
     * screen needs redrawing - a redundant redelivery must not restyle a face mid-track.
     */
    fun store(context: Context, bytes: ByteArray, fingerprint: String): Boolean {
        if (!UserFontContract.isAcceptableSize(bytes.size.toLong())) {
            Timber.w("Received user font is %d bytes, outside the accepted range", bytes.size)
            return false
        }
        val file = fontFile(context)
        val directory = file.parentFile
        if (directory != null && !directory.isDirectory && !directory.mkdirs()) {
            Timber.w("Could not create the user font directory on the watch")
            return false
        }
        return try {
            file.writeBytes(bytes)
            storedFingerprint = fingerprint
            invalidateCaches()
            Timber.d("Stored a user font of %d bytes", bytes.size)
            true
        } catch (e: IOException) {
            Timber.w(e, "Could not store the received user font")
            false
        }
    }

    /** Removes the font, after the phone reports that the user cleared theirs. */
    fun clear(context: Context): Boolean {
        val file = fontFile(context)
        val existed = file.isFile
        file.delete()
        storedFingerprint = null
        invalidateCaches()
        return existed
    }

    private fun invalidateCaches() {
        cachedTypeface = null
        cachedFamily = null
        cachedTypefaceKey = null
        cachedFamilyKey = null
    }

    /**
     * The imported font as a Compose [FontFamily], or null when this watch has none.
     *
     * `Font(File)` is API 26 and this module's `minSdk` is 26, so no version guard is needed - but
     * note that it is the *file* overload deliberately: the resource overload cannot see a file
     * that was not compiled into the APK, which is the entire point here.
     */
    fun fontFamily(): FontFamily? {
        val context = appContext ?: return null
        val file = fontFile(context)
        if (!file.isFile || file.length() == 0L) return null
        val key = "${file.length()}:${file.lastModified()}"
        cachedFamily?.takeIf { cachedFamilyKey == key }?.let { return it }
        val family = try {
            FontFamily(Font(file))
        } catch (e: Exception) {
            // A font the phone accepted can still fail here if the transfer truncated it. Falling
            // back rather than throwing keeps a corrupted delivery from taking down the face.
            Timber.w(e, "Stored user font could not be loaded as a Compose family")
            null
        }
        cachedFamily = family
        cachedFamilyKey = key.takeIf { family != null }
        return family
    }

    /** [fontFamily]'s [Typeface] counterpart, for the View-based classic and matejdro faces and the
     *  seek/volume readouts. Null resolves the same way an unknown catalog key does. */
    fun typeface(): Typeface? {
        val context = appContext ?: return null
        val file = fontFile(context)
        if (!file.isFile || file.length() == 0L) return null
        val key = "${file.length()}:${file.lastModified()}"
        cachedTypeface?.takeIf { cachedTypefaceKey == key }?.let { return it }
        val typeface = try {
            Typeface.createFromFile(file)
        } catch (e: RuntimeException) {
            Timber.w(e, "Stored user font could not be loaded as a Typeface")
            null
        }
        cachedTypeface = typeface
        cachedTypefaceKey = key.takeIf { typeface != null }
        return typeface
    }
}
