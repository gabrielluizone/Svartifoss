package com.svartifoss.snfell.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AccentFloorStyle
import com.svartifoss.snfell.common.AppearanceNumericRanges
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.ArchivedFaces
import com.svartifoss.snfell.common.BackgroundLayerColor
import com.svartifoss.snfell.common.BackgroundLayerStack
import com.svartifoss.snfell.common.ColorModifier
import com.svartifoss.snfell.common.OverlayBackdropResolver
import com.svartifoss.snfell.common.QueueAppearance
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.PlayerShadingStyle
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.view.watchface.AlbumArtStylePick
import com.svartifoss.snfell.view.watchface.AppearanceOptionCatalog
import com.svartifoss.snfell.view.watchface.PanelOptionCatalog
import com.svartifoss.snfell.view.watchface.MiniButtonIconLoader
import com.svartifoss.snfell.view.watchface.VisualChoiceCatalog
import com.svartifoss.snfell.view.watchface.WatchPreviewView
import com.svartifoss.snfell.view.watchface.theme.WatchThemeProfile
import com.svartifoss.snfell.view.watchface.theme.WatchThemeRepository
import com.svartifoss.snfell.view.watchface.theme.WatchThemeValue
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.File
import java.util.zip.CRC32
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Debug only: renders the Watch tab's preview for every value of every setting on
 * `watch_face_settings.xml` and writes down which settings change nothing on the miniature, and
 * how many distinct pictures each picker can actually show.
 *
 * Each value goes through [WatchPreviewView.showPreference], the call a row, a dialog and a picker
 * grid all make, so a setting reported dead here is one whose preview a user sees not move. Whether
 * that is the preview lying or the watch ignoring the value too is for the reader to decide - this
 * only measures.
 *
 * ```
 * adb shell am broadcast -n com.svartifoss.snfell/.debug.PreviewSweepReceiver --es mode faces
 * adb shell am broadcast -n com.svartifoss.snfell/.debug.PreviewSweepReceiver --es mode active
 *     [--es faces classic,verse] [--es keys album_art_style,wear_font] [--ei cap 12]
 * ```
 *
 * Output: `files/preview_sweep/<mode>.tsv`, one line per context and setting, and a `.done` file
 * when it has finished. Exists in debug builds only, and its manifest entry requires `DUMP`.
 */
class PreviewSweepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PreviewSweep.start(
                context.applicationContext,
                mode = intent.getStringExtra("mode") ?: "faces",
                faceFilter = intent.getStringExtra("faces")?.split(',')
                        ?.map(String::trim)?.filter(String::isNotEmpty)?.toSet(),
                keyFilter = intent.getStringExtra("keys")?.split(',')
                        ?.map(String::trim)?.filter(String::isNotEmpty)?.toSet(),
                cap = intent.getIntExtra("cap", 12),
                extra = intent.getStringExtra("with")?.split(',')
                        ?.mapNotNull { pair -> pair.split('=', limit = 2).takeIf { it.size == 2 } }
                        ?.associate { (k, v) -> k.trim() to v.trim() }.orEmpty(),
                dumpKeys = intent.getStringExtra("dump")?.split(',')
                        ?.map(String::trim)?.filter(String::isNotEmpty)?.toSet().orEmpty())
    }
}

internal object PreviewSweep {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val SIZE = 288
    private const val SIGNATURE_GRID = 24
    private const val VISIBLE_CHANNEL_DELTA = 8

    private class KeySpec(val key: String, val tag: String, val values: List<Any>, val total: Int)

    private class SweepContext(val label: String, val face: String, val profile: WatchThemeProfile)

    private class KeyResult(val spec: KeySpec) {
        val crcs = ArrayList<Long>()
        val coarse = ArrayList<String>()
        var basePixels: IntArray? = null
        var maxChangedPct = 0f
        var nondeterministic = false
        val surfaces = LinkedHashSet<String>()
        var error: String? = null
    }

    @Volatile
    private var running = false

    fun start(
            app: Context,
            mode: String,
            faceFilter: Set<String>?,
            keyFilter: Set<String>?,
            cap: Int,
            extra: Map<String, String> = emptyMap(),
            dumpKeys: Set<String> = emptySet()
    ) {
        if (running) {
            Timber.w("PreviewSweep already running")
            return
        }
        running = true
        thread(name = "preview-sweep-io") {
            val icons = MiniButtonIconLoader.loadConfiguredIcons(app, playing = true)
            val menu = MiniButtonIconLoader.loadMenuEntries(app)
            Handler(Looper.getMainLooper()).post {
                try {
                    Runner(app, mode, faceFilter, keyFilter, cap, icons, menu, extra, dumpKeys).begin()
                } catch (error: Throwable) {
                    Timber.e(error, "PreviewSweep could not start")
                    running = false
                }
            }
        }
    }

    private class Runner(
            private val app: Context,
            private val mode: String,
            faceFilter: Set<String>?,
            keyFilter: Set<String>?,
            cap: Int,
            icons: com.svartifoss.snfell.view.watchface.PreviewButtonIcons,
            menu: List<com.svartifoss.snfell.view.watchface.PreviewMenuEntry>,
            private val extra: Map<String, String>,
            private val dumpKeys: Set<String>
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private val renderer = WatchPreviewView(ContextThemeWrapper(app, R.style.AppTheme)).apply {
            setModerationPreviewMode(true)
            val spec = View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY)
            measure(spec, spec)
            layout(0, 0, SIZE, SIZE)
            setButtonIcons(icons)
            setMenuEntries(menu)
        }
        private val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        private val pixels = IntArray(SIZE * SIZE)
        private val keys: List<KeySpec> = inventory(cap).filter { keyFilter == null || it.key in keyFilter }
        private val contexts: List<SweepContext> = buildContexts(faceFilter)
        private val outDir = File(app.filesDir, "preview_sweep").apply { mkdirs() }
        private val out = File(outDir, "$mode${if (extra.isEmpty()) "" else "-with"}.tsv")
        private val started = SystemClock.uptimeMillis()

        private var contextIndex = 0
        private var keyIndex = 0
        private var valueIndex = -1
        private var current: KeyResult? = null

        fun begin() {
            File(outDir, "$mode.done").delete()
            Timber.i("PreviewSweep extra candidates %s", extra)
            out.writeText(listOf("context", "face", "key", "tag", "surfaces", "values", "of",
                    "distinct", "coarse", "maxChangedPct", "nondet", "error", "groups")
                    .joinToString("\t") + "\n")
            Timber.i("PreviewSweep %s: %d contexts x %d settings, %d icons",
                    mode, contexts.size, keys.size, 0)
            handler.post(::step)
        }

        private fun step() {
            try {
                advance()
            } catch (error: Throwable) {
                Timber.e(error, "PreviewSweep step failed")
                current?.error = error.javaClass.simpleName + ":" + error.message
                finishKey()
                keyIndex++
                valueIndex = -1
            }
            if (contextIndex < contexts.size) {
                handler.post(::step)
            } else {
                File(outDir, "$mode.done").writeText(
                        "${(SystemClock.uptimeMillis() - started) / 1000}s\n")
                Timber.i("PreviewSweep %s done in %ds", mode,
                        (SystemClock.uptimeMillis() - started) / 1000)
                renderer.clearThemeProfile()
                running = false
            }
        }

        /** One render per message, so the main thread is never held for long. */
        private fun advance() {
            if (contextIndex >= contexts.size) return
            val sweepContext = contexts[contextIndex]
            if (keyIndex >= keys.size) {
                Timber.i("PreviewSweep %s: %s finished", mode, sweepContext.label)
                contextIndex++
                keyIndex = 0
                valueIndex = -1
                return
            }
            val spec = keys[keyIndex]
            if (valueIndex == -1) {
                renderer.setThemeProfile(sweepContext.profile)
                val result = KeyResult(spec)
                current = result
                // The first value twice: a setting whose picture is not repeatable cannot be
                // judged by comparing pictures, and is reported as such instead.
                val first = render(sweepContext, spec.key, spec.values[0], result).copyOf()
                val again = render(sweepContext, spec.key, spec.values[0], result)
                result.nondeterministic = crc(first) != crc(again)
                result.basePixels = first
                valueIndex = 0
                return
            }
            val result = current ?: return
            if (valueIndex < spec.values.size) {
                val picture = render(sweepContext, spec.key, spec.values[valueIndex], result)
                if (spec.key in dumpKeys) {
                    File(outDir, "${sweepContext.face}_${spec.key}_${valueIndex}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                result.crcs += crc(picture)
                result.coarse += coarseSignature(picture)
                val base = result.basePixels
                if (base != null) {
                    var changed = 0
                    for (i in picture.indices) {
                        if (visiblyDifferent(base[i], picture[i])) changed++
                    }
                    val pct = changed * 100f / picture.size
                    if (pct > result.maxChangedPct) result.maxChangedPct = pct
                }
                valueIndex++
                return
            }
            finishKey()
            keyIndex++
            valueIndex = -1
        }

        private fun finishKey() {
            val result = current ?: return
            current = null
            val spec = result.spec
            val sweepContext = contexts.getOrNull(contextIndex) ?: return
            val groups = spec.values.indices
                    .groupBy { result.crcs.getOrNull(it) }
                    .values
                    .filter { it.size > 1 }
                    .sortedByDescending { it.size }
                    .take(4)
                    .joinToString(" | ") { group ->
                        group.take(8).joinToString(",") { spec.values[it].toString() } +
                                if (group.size > 8) ",+${group.size - 8}" else ""
                    }
            val line = listOf(
                    sweepContext.label,
                    sweepContext.face,
                    spec.key,
                    spec.tag,
                    result.surfaces.joinToString(","),
                    spec.values.size.toString(),
                    spec.total.toString(),
                    result.crcs.toSet().size.toString(),
                    result.coarse.toSet().size.toString(),
                    "%.3f".format(result.maxChangedPct),
                    if (result.nondeterministic) "nondet" else "",
                    result.error.orEmpty(),
                    groups
            ).joinToString("\t")
            out.appendText(line + "\n")
        }

        private fun render(
                sweepContext: SweepContext,
                key: String,
                value: Any,
                result: KeyResult
        ): IntArray {
            renderer.showPreference(key, value,
                    extra.filterKeys { it != key } + companionsFor(sweepContext, key, value))
            result.surfaces += renderer.currentSurface.name
            bitmap.eraseColor(Color.TRANSPARENT)
            renderer.draw(Canvas(bitmap))
            bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            return pixels
        }

        /** What picking [value] also writes - the album art style's wash, as the picker grid shows. */
        private fun companionsFor(
                sweepContext: SweepContext,
                key: String,
                value: Any
        ): Map<String, Any?> {
            if (key != MiscPreferences.ALBUM_ART_STYLE.key) return emptyMap()
            val profile = sweepContext.profile
            fun text(k: String, d: String) = (profile.settings[k] as? WatchThemeValue.Text)?.value ?: d
            fun flag(k: String, d: Boolean) = (profile.settings[k] as? WatchThemeValue.Flag)?.value ?: d
            fun number(k: String, d: Int) = (profile.settings[k] as? WatchThemeValue.Number)?.value ?: d
            val legacy = AlbumArtStylePick.LegacyBackground(
                    dimEnabled = flag(MiscPreferences.DIM_ALBUM_ART.key,
                            MiscPreferences.DIM_ALBUM_ART.defaultValue),
                    dimPercent = number(MiscPreferences.ALBUM_ART_DIM_STRENGTH.key,
                            MiscPreferences.ALBUM_ART_DIM_STRENGTH.defaultValue),
                    shading = PlayerShadingStyle.fromPreference(text(
                            MiscPreferences.WEAR_PLAYER_SHADING_STYLE.key,
                            MiscPreferences.WEAR_PLAYER_SHADING_STYLE.defaultValue)),
                    shadingColor = BackgroundLayerColor.fromPreference(text(
                            MiscPreferences.WEAR_SHADING_COLOR_MODE.key,
                            MiscPreferences.WEAR_SHADING_COLOR_MODE.defaultValue)),
                    floor = AccentFloorStyle.fromPreference(text(
                            MiscPreferences.WEAR_ACCENT_FLOOR.key,
                            MiscPreferences.WEAR_ACCENT_FLOOR.defaultValue)),
                    floorColor = BackgroundLayerColor.fromPreference(text(
                            MiscPreferences.WEAR_ACCENT_FLOOR_COLOR_MODE.key,
                            MiscPreferences.WEAR_ACCENT_FLOOR_COLOR_MODE.defaultValue)),
                    baseWashDrawn = sweepContext.face !in BackgroundLayerStack.SELF_BACKDROP_FACES)
            val stack = AlbumArtStylePick.stackAfter(
                    storedRaw = text(MiscPreferences.WEAR_BACKGROUND_LAYERS.key, ""),
                    oldStyle = text(MiscPreferences.ALBUM_ART_STYLE.key,
                            MiscPreferences.ALBUM_ART_STYLE.defaultValue),
                    newStyle = value.toString(),
                    legacy = legacy) ?: return emptyMap()
            return mapOf(MiscPreferences.WEAR_BACKGROUND_LAYERS.key to stack)
        }

        private fun buildContexts(faceFilter: Set<String>?): List<SweepContext> {
            val repository = WatchThemeRepository(app)
            return if (mode == "active") {
                val prefs = PreferenceManager.getDefaultSharedPreferences(app)
                val appearance = ThemeAppearance.resolve(prefs)
                listOf(SweepContext("active", appearance.baseFace,
                        repository.previewProfile(prefs, appearance)))
            } else {
                // Never written: every face then reads its shipped defaults alone.
                val empty = app.getSharedPreferences("preview_sweep_empty", Context.MODE_PRIVATE)
                ThemeAppearance.ALLOWED_BASE_FACES
                        .filter { faceFilter == null || it in faceFilter }
                        .map { face ->
                            val label = if (face in ArchivedFaces.KEYS) "archived" else "face"
                            SweepContext(label, face,
                                    repository.previewProfile(empty, AppearanceContext.BuiltIn(face)))
                        }
            }
        }

        /**
         * Every setting on the page with the values its picker offers - including the ones
         * appended at runtime, assembled the way `WatchFacePrefsFragment` assembles them.
         */
        private fun inventory(cap: Int): List<KeySpec> {
            class Raw(val key: String, val tag: String, val values: List<Any>?)
            val raws = ArrayList<Raw>()
            val lists = LinkedHashMap<String, ListPreference>()
            val parser = app.resources.getXml(R.xml.watch_face_settings)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                val key = parser.getAttributeValue(ANDROID_NS, "key") ?: continue
                val tag = parser.name.substringAfterLast('.')
                when {
                    tag == "PreferenceCategory" || tag == "PreferenceScreen" -> continue
                    tag.endsWith("ListPreference") || tag == "ColorTreatmentPreference" ||
                            tag == "TransportGlyphPreference" -> {
                        val id = parser.getAttributeResourceValue(ANDROID_NS, "entryValues", 0)
                        val values = if (id == 0) emptyArray() else app.resources.getStringArray(id)
                        lists[key] = ListPreference(app).apply {
                            this.key = key
                            entries = values
                            entryValues = values
                        }
                        raws += Raw(key, tag, null)
                    }
                    tag.startsWith("Switch") -> raws += Raw(key, tag, listOf(false, true))
                    tag == "ResettableNumericEditTextPreference" -> {
                        val range = AppearanceNumericRanges.rangeFor(key) ?: 0..100
                        raws += Raw(key, tag,
                                listOf(range.first, (range.first + range.last) / 2, range.last).distinct())
                    }
                    tag == "HexColorDotPreference" ->
                        raws += Raw(key, tag, listOf("#E53935", "#1E88E5", "#43A047"))
                }
            }
            PanelOptionCatalog.apply(app.resources) { lists[it] }
            AppearanceOptionCatalog.apply(app.resources) { lists[it] }
            fun prefixed(key: String, first: String, values: Array<String>) {
                lists[key]?.entryValues = (listOf(first) + values.toList()).toTypedArray()
            }
            prefixed(MiscPreferences.WEAR_QUEUE_COVER_SHAPE.key, QueueAppearance.FOLLOW,
                    (app.resources.getStringArray(R.array.wear_cover_shape_values).toList() +
                            app.resources.getStringArray(R.array.wear_cover_shape_extra_values))
                            .distinct().toTypedArray())
            prefixed(MiscPreferences.WEAR_TITLE_COLOR_MODE.key, MiscPreferences.TITLE_COLOR_FACE_DEFAULT,
                    app.resources.getStringArray(R.array.wear_component_color_treatment_values))
            listOf(MiscPreferences.WEAR_TITLE_COLOR_MODIFIER, MiscPreferences.WEAR_ARTIST_COLOR_MODIFIER,
                    MiscPreferences.WEAR_CLOCK_COLOR_MODIFIER).forEach {
                prefixed(it.key, ColorModifier.FOLLOW,
                        app.resources.getStringArray(R.array.wear_color_modifier_values))
            }
            val shared = lists[MiscPreferences.WEAR_OVERLAY_BACKDROP_STYLE.key]?.entryValues
                    ?.map(CharSequence::toString).orEmpty().toTypedArray()
            listOf(MiscPreferences.WEAR_VOLUME_BACKDROP_STYLE, MiscPreferences.WEAR_PROGRESS_BACKDROP_STYLE,
                    MiscPreferences.WEAR_QUICK_PANEL_BACKDROP_STYLE, MiscPreferences.WEAR_QUEUE_BACKDROP_STYLE,
                    MiscPreferences.WEAR_LYRICS_BACKDROP_STYLE).forEach {
                prefixed(it.key, OverlayBackdropResolver.SHARED, shared)
            }
            return raws.mapNotNull { raw ->
                val all: List<Any> = raw.values
                        ?: lists[raw.key]?.entryValues?.map(CharSequence::toString)?.distinct()
                        ?: return@mapNotNull null
                if (all.isEmpty()) return@mapNotNull null
                val values = if (VisualChoiceCatalog.isVisual(raw.key) || all.size <= cap) {
                    all
                } else {
                    (0 until cap).map { all[it * (all.size - 1) / (cap - 1)] }.distinct()
                }
                KeySpec(raw.key, raw.tag, values, all.size)
            }
        }

        private fun crc(picture: IntArray): Long {
            val crc = CRC32()
            val bytes = ByteArray(4)
            for (pixel in picture) {
                bytes[0] = (pixel ushr 24).toByte()
                bytes[1] = (pixel ushr 16).toByte()
                bytes[2] = (pixel ushr 8).toByte()
                bytes[3] = pixel.toByte()
                crc.update(bytes, 0, 4)
            }
            return crc.value
        }

        /** Mean colour of each cell of a coarse grid, quantised: two values that land on the same
         *  signature differ by nothing a person would notice in a grid cell. */
        private fun coarseSignature(picture: IntArray): String {
            val cell = SIZE / SIGNATURE_GRID
            val builder = StringBuilder(SIGNATURE_GRID * SIGNATURE_GRID * 3)
            for (gy in 0 until SIGNATURE_GRID) for (gx in 0 until SIGNATURE_GRID) {
                var r = 0; var g = 0; var b = 0
                for (y in gy * cell until (gy + 1) * cell) for (x in gx * cell until (gx + 1) * cell) {
                    val p = picture[y * SIZE + x]
                    r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
                }
                val n = cell * cell
                builder.append((r / n / 12).toChar()).append((g / n / 12).toChar())
                        .append((b / n / 12).toChar())
            }
            return builder.toString()
        }

        private fun visiblyDifferent(a: Int, b: Int): Boolean =
                abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) > VISIBLE_CHANNEL_DELTA ||
                        abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) > VISIBLE_CHANNEL_DELTA ||
                        abs((a and 0xFF) - (b and 0xFF)) > VISIBLE_CHANNEL_DELTA ||
                        abs((a ushr 24) - (b ushr 24)) > VISIBLE_CHANNEL_DELTA
    }
}
