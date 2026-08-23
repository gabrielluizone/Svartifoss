package com.svartifoss.snfell.notifications

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import timber.log.Timber

/**
 * The notification glyph each music app posts, remembered across runs.
 *
 * The playing app's icon on the Split face's seam, and beside the artist line on every other face,
 * is not the app's launcher icon - it is the monochrome template the app puts in its own
 * notification, which the watch then tints to whatever colour that surface is using. That is the
 * mark a user learns to associate with the app inside this app. An action list that answers the
 * same question ("which app is this row?") with a full-colour launcher icon is answering it in a
 * second visual language, and the two sit inches apart on the same watch.
 *
 * **Why this has to be persisted, rather than read live from [MediaNotificationActions].** That
 * registry holds the notifications that are posted *right now*. The rows that want a glyph are
 * launcher rows - "Play YouTube Music" - and the entire reason to tap one is that the app is not
 * currently playing, so its notification is exactly what does not exist at that moment. Reading
 * live would mean the glyph appeared only for apps the user did not need to launch. Remembering it
 * from the last time that app *did* post one turns a coincidence into a property.
 *
 * The cache is written whenever a media notification for a package is seen and never expires: an
 * app's notification glyph is part of its brand and changes about as often. A stale one after a
 * rebrand is corrected by the app playing once.
 *
 * Files are one flat-white PNG per package, a few kilobytes each, in the app's own cache-adjacent
 * files dir. Losing them costs nothing but a fallback to the launcher icon until that app plays.
 */
object AppGlyphStore {

    private const val DIR_NAME = "app_glyphs"

    /**
     * Its own SharedPreferences file, not the default one.
     *
     * The default file is mirrored wholesale to the watch, and these two numbers are bookkeeping
     * about a phone-side cache - the same reasoning `WatchThemeRepository` records for keeping the
     * theme library out of it.
     */
    private const val PREFS_NAME = "app_glyph_store"
    private const val KEY_GENERATION = "generation"
    private const val KEY_TRANSMITTED = "transmitted_generation"

    /**
     * Package -> glyph, including **misses**.
     *
     * Negative entries are the point rather than an optimisation detail: `defaultIcon` is a getter
     * called once per row while a list is being built, and on a phone with no cached glyphs at all
     * a miss would otherwise be a `File.exists()` per row per rebuild. A null value here means
     * "checked the disk, nothing there".
     */
    private val memory = HashMap<String, ByteArray?>()

    /** Notified with the package name when a glyph is learned or genuinely changes. */
    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()

    /**
     * Packages already announced in this process.
     *
     * A glyph change costs whoever is listening a full config re-transmit, so an app that varies
     * its notification icon while running (a download badge, a cast state) must not be able to put
     * the Data Layer in a loop. The later change is still *stored* - the next process start picks
     * it up - it simply does not trigger another push.
     */
    private val announced = HashSet<String>()

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }

    /** Records [png] as [packageName]'s glyph, if it is new. No-op for a null icon, so an app that
     *  publishes an unreadable one keeps whatever was learned before. */
    fun remember(context: Context, packageName: String, png: ByteArray?) {
        if (png == null || png.isEmpty() || packageName.isBlank()) return
        // Compared against the *disk-backed* value, not the memory map alone. On a cold start the
        // map is empty, so the first notification of every app would look like a change and
        // announce a re-transmit that has nothing to re-transmit - once per music app, every
        // launch.
        if (glyph(context, packageName)?.contentEquals(png) == true) return

        synchronized(this) { memory[packageName] = png }
        val file = fileFor(context, packageName) ?: return
        try {
            file.parentFile?.mkdirs()
            // Temp file plus rename, matching BundleFileSerialization: a half-written PNG read back
            // by the next process would decode to null and look exactly like "no glyph", which is
            // the one failure that would be silent.
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeBytes(png)
            if (!temp.renameTo(file)) {
                temp.delete()
            }
        } catch (e: Exception) {
            Timber.v(e, "Could not cache the notification glyph for %s", packageName)
        }

        // Bumped before the listeners run, so a retransmit triggered by one reads the generation
        // it is actually sending.
        bumpGeneration(context)

        val announce = synchronized(this) { announced.add(packageName) }
        if (announce) {
            listeners.forEach { it.invoke(packageName) }
        }
    }

    /**
     * Whether the icons on the watch predate the glyphs this phone has learned.
     *
     * The live listener above only helps when something is listening, and the case it cannot cover
     * is the common one: `MusicService` is stopped, a music app posts, the glyph is cached with
     * nobody to act on it. Without this check the next process start would find the bytes already
     * on disk, take the "nothing changed" branch in [remember], and never announce - leaving the
     * launcher icon on the watch permanently, which is exactly the bug the cache was added to fix.
     *
     * A counter rather than a per-package flag because the consumer re-sends whole configs anyway;
     * what it needs to know is "is anything newer than my last push", not which app.
     */
    fun needsRetransmit(context: Context): Boolean {
        val prefs = prefs(context)
        return prefs.getLong(KEY_GENERATION, 0L) != prefs.getLong(KEY_TRANSMITTED, 0L)
    }

    /**
     * Records that the configs have been sent at the current generation.
     *
     * Marked when the send is *scheduled*, not when the watch acknowledges - there is no
     * acknowledgement to wait for. A DataItem put succeeds locally with the watch out of range and
     * Play Services replicates it on reconnect, so the honest failure mode here is a lost icon
     * refresh rather than a lost config, and paying for it with a re-push on every start would be
     * the worse trade.
     */
    fun markRetransmitted(context: Context) {
        val prefs = prefs(context)
        prefs.edit()
                .putLong(KEY_TRANSMITTED, prefs.getLong(KEY_GENERATION, 0L))
                .apply()
    }

    private fun bumpGeneration(context: Context) {
        val prefs = prefs(context)
        prefs.edit().putLong(KEY_GENERATION, prefs.getLong(KEY_GENERATION, 0L) + 1L).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** [packageName]'s remembered glyph, or null if this phone has never seen it post one. */
    fun glyph(context: Context, packageName: String): ByteArray? {
        if (packageName.isBlank()) return null
        synchronized(this) {
            if (memory.containsKey(packageName)) return memory[packageName]
        }
        val bytes = try {
            fileFor(context, packageName)?.takeIf { it.isFile }?.readBytes()
        } catch (e: Exception) {
            Timber.v(e, "Could not read the cached notification glyph for %s", packageName)
            null
        }
        synchronized(this) { memory[packageName] = bytes }
        return bytes
    }

    /**
     * [packageName]'s remembered glyph as a drawable, or null.
     *
     * A caller that gets non-null here must also report the icon as **tintable**: the bytes are a
     * flat-white template on transparency (see `MediaNotificationActions.rasterizePng`), so drawn
     * untinted on a light surface it is an invisible white shape.
     */
    fun drawable(context: Context, packageName: String): Drawable? {
        val bytes = glyph(context, packageName) ?: return null
        val bitmap = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        } ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }

    /** Test/diagnostic hook: forget everything held in memory so the next read hits the disk. */
    fun invalidateMemoryCache() {
        synchronized(this) {
            memory.clear()
            announced.clear()
        }
    }

    /**
     * One file per package.
     *
     * A package name is already restricted to letters, digits, `_` and `.`, so it is a safe file
     * name as it stands - but it is data arriving from another app, and building a path out of
     * unvalidated input is the shape of a traversal bug even when this particular input cannot be
     * one. Anything unexpected is refused rather than sanitised into something that looks fine.
     */
    private fun fileFor(context: Context, packageName: String): File? {
        if (!packageName.matches(SAFE_PACKAGE)) return null
        return File(File(context.filesDir, DIR_NAME), "$packageName.png")
    }

    private val SAFE_PACKAGE = Regex("[A-Za-z0-9_.]{1,200}")
}
