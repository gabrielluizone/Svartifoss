package com.svartifoss.snfell.res

import com.svartifoss.snfell.common.AppIconStyle
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Pins the Play build's launcher-icon choice, which is spread over six places that nothing else
 * connects: the enum, the two Play manifests, the two apps' icon resources, the picker's arrays and
 * translations, and the picker's own previews. Every one of them fails quietly when one is missed -
 * a style with no alias is a choice that changes nothing on the device, a missing translation
 * falls back to English in one language, and a preview that drifts from its icon promises a colour
 * the launcher never shows - so each is a structural check over the files rather than a hope.
 *
 * Reads the tree off disk like its siblings in this package; no Android context. The wear module
 * and `common` are inputs of this task in `mobile/build.gradle`, since nothing else in this module
 * depends on them and an edit there would otherwise leave the task UP-TO-DATE.
 */
class AppIconStyleContractTest {

    // ---- reading the tree ----

    private fun file(path: String): File =
            listOf(File(path), File("../$path")).firstOrNull { it.exists() }
                    ?: fail("Not found from either module dir or repo root: $path").let { error("unreachable") }

    private fun exists(path: String) = File(path).exists() || File("../$path").exists()

    private fun text(path: String): String = file(path).readText()

    private fun xml(path: String): Document =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file(path))

    private fun Document.all(tag: String): List<Element> =
            (0 until getElementsByTagName(tag).length).map { getElementsByTagName(tag).item(it) as Element }

    private fun Element.attr(name: String): String = getAttribute(name)

    private val styles = AppIconStyle.entries

    private val phoneColourFiles = listOf(
            "mobile/src/play/res/values/launcher_preview_colors.xml",
            "mobile/src/play/res/values/colors.xml",
            "mobile/src/main/res/values/colors.xml",
            "common/src/main/res/values/colors.xml")

    private val watchColourFiles = listOf(
            "wear/src/play/res/values/colors.xml",
            "wear/src/main/res/values/colors.xml",
            "common/src/main/res/values/colors.xml")

    /** A colour as the given module would resolve it, following `@color/` references. */
    private fun colour(files: List<String>, ref: String): String {
        val name = ref.removePrefix("@color/")
        for (path in files) {
            val hit = xml(path).all("color").firstOrNull { it.attr("name") == name } ?: continue
            val value = hit.textContent.trim()
            return if (value.startsWith("@color/")) colour(files, value) else value.uppercase()
        }
        fail("No colour $name in $files")
        error("unreachable")
    }

    // ---- the enum, the picker and the translations list the same styles ----

    private fun arrayItems(path: String, name: String): List<String> {
        val array = xml(path).all("string-array").firstOrNull { it.attr("name") == name }
                ?: fail("$path has no array $name").let { error("unreachable") }
        return array.getElementsByTagName("item").let { items ->
            (0 until items.length).map { items.item(it).textContent.trim() }
        }
    }

    @Test
    fun thePickerValuesAreTheEnumInOrder() {
        assertEquals(
                "ListPreference pairs entries with values by index, so the arrays and the enum must agree",
                styles.map { it.preferenceValue },
                arrayItems("mobile/src/main/res/values/arrays.xml", "app_icon_style_values"))
    }

    @Test
    fun thePickerEntriesNameTheSameStylesInTheSameOrder() {
        assertEquals(
                styles.map { "@string/app_icon_style_${it.preferenceValue}" },
                arrayItems("mobile/src/main/res/values/strings.xml", "app_icon_style_entries"))
    }

    @Test
    fun everyLanguageNamesEveryStyle() {
        val res = file("mobile/src/main/res")
        val localeDirs = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") && f.name != "values-night" }!!
                .filter { File(it, "strings.xml").exists() }
        assertTrue("expected the base and the translated locales", localeDirs.size >= 40)

        val missing = mutableListOf<String>()
        localeDirs.sortedBy { it.name }.forEach { dir ->
            val names = xml("mobile/src/main/res/${dir.name}/strings.xml").all("string")
                    .associate { it.attr("name") to it.textContent.trim() }
            styles.forEach { style ->
                if (names["app_icon_style_${style.preferenceValue}"].isNullOrBlank()) {
                    missing += "${dir.name}: app_icon_style_${style.preferenceValue}"
                }
            }
        }
        assertEquals("A style with no label in a language shows English there, or fails the release lint", emptyList<String>(), missing)
    }

    @Test
    fun theSettingsRowIsThePreviewPickerAndDefaultsToTheDefaultStyle() {
        val row = xml("mobile/src/main/res/xml/settings.xml").all("com.svartifoss.snfell.view.settings.AppIconStylePreference")
                .singleOrNull { it.attr("android:key") == "app_icon_style" }
                ?: fail("the app_icon_style row must use AppIconStylePreference so its dialog shows previews").let { error("unreachable") }
        assertEquals(AppIconStyle.DEFAULT.preferenceValue, row.attr("android:defaultValue"))
        assertEquals("@array/app_icon_style_entries", row.attr("android:entries"))
        assertEquals("@array/app_icon_style_values", row.attr("android:entryValues"))
    }

    @Test
    fun thePickerAppliesTheChosenIconItselfAndDoesNotRelyOnTheSyncCoordinator() {
        // The coordinator also applies the alias on every change of the key, but it stops itself on
        // a phone with no Wearable API, which left the launcher icon unchanged after a choice. The
        // picker is the one place a person chooses, so it must apply the alias too.
        val picker = text("mobile/src/main/java/com/svartifoss/snfell/view/settings/AppIconStylePreference.kt")
        assertTrue(picker.contains("LauncherIconController.apply(context, value)"))
    }

    // ---- the manifests ----

    private fun aliases(manifest: String): Map<String, Element> =
            xml(manifest).all("activity-alias").associateBy { it.attr("android:name") }

    @Test
    fun eachPlayManifestDeclaresExactlyOneAliasPerStyle() {
        listOf("mobile/src/play/AndroidManifest.xml", "wear/src/play/AndroidManifest.xml").forEach { path ->
            val declared = aliases(path)
            assertEquals("$path must declare one alias per AppIconStyle, and no other",
                    styles.map { it.aliasName }.toSet(), declared.keys)

            styles.forEach { style ->
                val alias = declared.getValue(style.aliasName)
                assertEquals("$path ${style.aliasName} must be exported or the launcher cannot start it",
                        "true", alias.attr("android:exported"))
                assertEquals("@mipmap/ic_launcher_play_${style.preferenceValue}", alias.attr("android:icon"))
                assertEquals(alias.attr("android:icon"), alias.attr("android:roundIcon"))
                val launcher = alias.getElementsByTagName("category")
                assertTrue("$path ${style.aliasName} must carry the LAUNCHER category",
                        (0 until launcher.length).any {
                            (launcher.item(it) as Element).attr("android:name") == "android.intent.category.LAUNCHER"
                        })
            }
        }
    }

    @Test
    fun onlyTheDefaultAliasStartsEnabled() {
        listOf("mobile/src/play/AndroidManifest.xml", "wear/src/play/AndroidManifest.xml").forEach { path ->
            val enabled = aliases(path).filterValues { it.attr("android:enabled") == "true" }.keys
            assertEquals("$path: two enabled aliases would put two icons in the launcher",
                    setOf(AppIconStyle.DEFAULT.aliasName), enabled)
        }
    }

    @Test
    fun theApplicationIconIsTheDefaultStylesIcon() {
        listOf("mobile/src/play/AndroidManifest.xml", "wear/src/play/AndroidManifest.xml").forEach { path ->
            val application = xml(path).all("application").single()
            assertEquals("@mipmap/ic_launcher_play_${AppIconStyle.DEFAULT.preferenceValue}", application.attr("android:icon"))
        }
    }

    private fun style(path: String, name: String): Element =
            xml(path).all("style").singleOrNull { it.attr("name") == name }
                    ?: fail("$path has no style $name").let { error("unreachable") }

    private fun styleItems(path: String, name: String): Map<String, String> {
        val items = style(path, name).getElementsByTagName("item")
        return (0 until items.length).associate {
            val item = items.item(it) as Element
            item.attr("name") to item.textContent.trim()
        }
    }

    @Test
    fun theWatchSplashBelowAndroid12ShowsTheIconTheDefaultEntryLaunchesWith() {
        // Wear OS app quality WO-V15: the branded launch must show the launcher icon. Below Android
        // 12 (Wear OS 3) the library draws it into the window background from the theme in values/,
        // which can only be one icon, so it follows the default alias - the one a reviewer and a
        // new install see.
        assertTrue(text("wear/src/play/res/drawable/splash_screen.xml")
                .contains("@mipmap/ic_launcher_play_${AppIconStyle.DEFAULT.preferenceValue}"))
        assertEquals("@drawable/splash_screen",
                styleItems("wear/src/main/res/values/styles.xml", "AppTheme.Starting")["windowSplashScreenAnimatedIcon"])
    }

    @Test
    fun theWatchSplashOnAndroid12AndLaterIsTheIconOfTheEntryThatWasOpened() {
        // An alias cannot carry a theme of its own, so a launch theme that names an icon names the
        // same one for every style: the watch went on opening with the gradient icon after Cream
        // and charcoal had been chosen. The Play theme names none, and it has to be an explicit
        // @null - the androidx theme it inherits fills the platform attribute with the generic
        // Android icon, so a theme that merely leaves the item out would show that robot instead of
        // the launcher icon.
        val items = styleItems("wear/src/play/res/values-v31/styles.xml", "AppTheme.Starting")
        assertEquals("@null", items["android:windowSplashScreenAnimatedIcon"])
        assertFalse("an icon of the library's own here would be the one every style opens with",
                items.containsKey("windowSplashScreenAnimatedIcon"))
    }

    @Test
    fun bothWatchLaunchThemesShareTheirBackgroundAndThePostSplashTheme() {
        // postSplashScreenTheme is what hands MainActivity back to AppTheme: without it the player
        // would run on the splash theme. The Play override changes only the icon, so it inherits
        // both from one base rather than repeating them where an edit could miss one.
        val base = styleItems("wear/src/main/res/values/styles.xml", "Base.AppTheme.Starting")
        assertEquals("@style/AppTheme", base["postSplashScreenTheme"])
        assertEquals("@android:color/black", base["windowSplashScreenBackground"])
        listOf("wear/src/main/res/values/styles.xml", "wear/src/play/res/values-v31/styles.xml").forEach {
            assertEquals("$it: AppTheme.Starting must build on the shared base",
                    "Base.AppTheme.Starting", style(it, "AppTheme.Starting").attr("parent"))
        }
    }

    @Test
    fun noOtherManifestDeclaresAnAlias() {
        listOf(
                "mobile/src/main/AndroidManifest.xml", "mobile/src/github/AndroidManifest.xml",
                "wear/src/main/AndroidManifest.xml", "wear/src/github/AndroidManifest.xml"
        ).filter { exists(it) }.forEach {
            assertFalse("$it must not declare launcher aliases: the GitHub build keeps its original launcher entry",
                    text(it).contains("<activity-alias"))
        }
    }

    // ---- the icons ----

    @Test
    fun everyAliasIconExistsInBothResourceForms() {
        // lint's MissingDefaultResource is fatal for a release build: a mipmap that exists only
        // under -anydpi-v26 has no default, and the pre-26 form is what an older phone loads.
        listOf("mobile", "wear").forEach { module ->
            styles.forEach { style ->
                val name = "ic_launcher_play_${style.preferenceValue}.xml"
                assertTrue("$module is missing mipmap/$name", exists("$module/src/play/res/mipmap/$name"))
                assertTrue("$module is missing mipmap-anydpi-v26/$name",
                        exists("$module/src/play/res/mipmap-anydpi-v26/$name"))
            }
        }
    }

    // ---- the flat styles: the requested colours, device by device ----

    /**
     * A style drawn as one flat background with the shared mark tinted a single colour, on each
     * device. A colour is the name of its resource in `common` and the hex it was asked for.
     * Cream is one pair with its roles swapped between the devices; Charcoal Feather is a palette
     * of its own per device. Solid and Gradient are not here: they draw the mark untinted.
     */
    private class Flat(
            val style: AppIconStyle,
            val phoneBackground: Pair<String, String>,
            val phoneMark: Pair<String, String>,
            val watchBackground: Pair<String, String>,
            val watchMark: Pair<String, String>)

    private val flatStyles = listOf(
            Flat(AppIconStyle.CREAM,
                    phoneBackground = "launcher_charcoal" to "#32312F",
                    phoneMark = "launcher_cream" to "#F5F0E5",
                    watchBackground = "launcher_cream" to "#F5F0E5",
                    watchMark = "launcher_charcoal" to "#32312F"),
            Flat(AppIconStyle.FEATHER,
                    phoneBackground = "launcher_feather_phone_background" to "#435244",
                    phoneMark = "launcher_feather_phone_mark" to "#BCB8AD",
                    watchBackground = "launcher_feather_watch_background" to "#F5F3D9",
                    watchMark = "launcher_feather_watch_mark" to "#6C5E3B"))

    @Test
    fun everyStyleButTheTwoOriginalsIsCheckedAsAFlatStyle() {
        // A style missing from the table above would be covered by none of the tests below.
        assertEquals(styles.toSet() - setOf(AppIconStyle.SOLID, AppIconStyle.GRADIENT),
                flatStyles.map { it.style }.toSet())
    }

    @Test
    fun theFlatStylesUseTheRequestedColours() {
        flatStyles.forEach { flat ->
            listOf(flat.phoneBackground, flat.phoneMark, flat.watchBackground, flat.watchMark).forEach { (name, hex) ->
                assertEquals("${flat.style.name}: $name", hex,
                        colour(listOf("common/src/main/res/values/colors.xml"), name))
            }
        }
    }

    private fun adaptive(path: String): Pair<String, String> {
        val doc = xml(path)
        return doc.all("background").single().attr("android:drawable") to
                doc.all("foreground").single().attr("android:drawable")
    }

    private fun tintOf(path: String): String = xml(path).all("bitmap").single().attr("android:tint")

    /**
     * Themed icons (Android 13+, and the launchers that offer them) repaint an app's icon from
     * its `<monochrome>` layer in the wallpaper's colours; an adaptive icon without one is left as
     * the coloured icon among tinted ones, which is how the app stood out on every such home
     * screen. The layer is the shared mark - white on transparent, the same alpha every style
     * recolours - because the launcher only reads its alpha, so no style needs a drawing of its
     * own, and the layer stays in step with the foreground by being the same file.
     */
    @Test
    fun everyPhoneAdaptiveIconHasAThemedMonochromeLayer() {
        val icons = listOf(
                "mobile/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
                "mobile/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml") +
                styles.map { "mobile/src/play/res/mipmap-anydpi-v26/ic_launcher_play_${it.preferenceValue}.xml" }
        icons.forEach { path ->
            assertEquals("$path: monochrome layer", "@mipmap/ic_launcher_foreground",
                    xml(path).all("monochrome").single().attr("android:drawable"))
        }
    }

    @Test
    fun thePhoneFlatIconsAreTheirBackgroundWithTheirMark() {
        flatStyles.forEach { flat ->
            val value = flat.style.preferenceValue
            val (background, foreground) = adaptive("mobile/src/play/res/mipmap-anydpi-v26/ic_launcher_play_$value.xml")
            assertEquals("${flat.style.name}: phone background", flat.phoneBackground.second, colour(phoneColourFiles, background))
            assertEquals("@drawable/ic_launcher_play_${value}_foreground", foreground)
            assertEquals("${flat.style.name}: phone mark", flat.phoneMark.second,
                    colour(phoneColourFiles, tintOf("mobile/src/play/res/drawable/ic_launcher_play_${value}_foreground.xml")))
        }
    }

    @Test
    fun theWatchFlatIconsAreTheirBackgroundWithTheirMark() {
        flatStyles.forEach { flat ->
            val value = flat.style.preferenceValue
            val (background, foreground) = adaptive("wear/src/play/res/mipmap-anydpi-v26/ic_launcher_play_$value.xml")
            assertEquals("${flat.style.name}: watch background", flat.watchBackground.second, colour(watchColourFiles, background))
            assertEquals("@drawable/ic_launcher_play_${value}_foreground", foreground)
            assertEquals("${flat.style.name}: watch mark", flat.watchMark.second,
                    colour(watchColourFiles, tintOf("wear/src/play/res/drawable/ic_launcher_play_${value}_foreground.xml")))
        }
    }

    @Test
    fun thePreAdaptiveFormsUseTheSameColoursAsTheAdaptiveOnes() {
        flatStyles.forEach { flat ->
            val value = flat.style.preferenceValue
            mapOf("mobile" to (flat.phoneBackground.first to flat.phoneMark.first),
                    "wear" to (flat.watchBackground.first to flat.watchMark.first))
                    .forEach { (module, colours) ->
                        val layers = xml("$module/src/play/res/mipmap/ic_launcher_play_$value.xml").all("item")
                        assertEquals("${flat.style.name} $module background layer", "@color/${colours.first}", layers[0].attr("android:drawable"))
                        assertEquals("${flat.style.name} $module mark layer",
                                "@drawable/ic_launcher_play_${value}_foreground", layers[1].attr("android:drawable"))
                        assertTrue(text("$module/src/play/res/drawable/ic_launcher_play_${value}_foreground.xml")
                                .contains("@color/${colours.second}"))
                    }
        }
    }

    @Test
    fun theFeatherStyleIsNamedAsRequestedInEnglishAndPortuguese() {
        // Charcoal Feather / Pena de Carvão, after the haibane of Haibane Renmei: the two names were
        // asked for, so the other 42 locales are free to translate but these two are not.
        fun name(locale: String) = xml("mobile/src/main/res/$locale/strings.xml").all("string")
                .single { it.attr("name") == "app_icon_style_feather" }.textContent.trim()
        assertEquals("Charcoal Feather", name("values"))
        assertEquals("Pena de Carvão", name("values-pt-rBR"))
        assertEquals("Pena de Carvão", name("values-pt-rPT"))
    }

    @Test
    fun theCreamStyleIsJustCreamInEveryLanguage() {
        // It was "Cream and charcoal" and was shortened on request to one untranslated word, so the
        // picker row reads the same everywhere. A translator "restoring" a local name would undo that.
        val res = file("mobile/src/main/res")
        val locales = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") && File(f, "strings.xml").exists() }!!
        assertTrue("expected the base and the translated locales", locales.size >= 40)
        locales.sortedBy { it.name }.forEach { dir ->
            val name = xml("mobile/src/main/res/${dir.name}/strings.xml").all("string")
                    .singleOrNull { it.attr("name") == "app_icon_style_cream" }?.textContent?.trim()
            assertEquals("${dir.name}: app_icon_style_cream", "Cream", name)
        }
    }

    // ---- the picker's previews ----

    private fun previewPath(device: String, style: AppIconStyle) =
            "mobile/src/play/res/drawable/launcher_preview_${device}_${style.preferenceValue}.xml"

    @Test
    fun everyStyleHasAPreviewForBothDevicesAndTheSeamNamesThem() {
        val seam = text("mobile/src/play/java/com/svartifoss/snfell/view/settings/AppIconPreviews.kt")
        styles.forEach { style ->
            listOf("phone", "watch").forEach { device ->
                assertTrue("missing ${previewPath(device, style)}", exists(previewPath(device, style)))
                assertTrue("AppIconPreviews must map ${style.name} to launcher_preview_${device}_${style.preferenceValue}",
                        seam.contains("AppIconStyle.${style.name} -> R.drawable.launcher_preview_${device}_${style.preferenceValue}"))
            }
        }
    }

    /** The colours a preview paints with: the shape's fill or gradient ends, and the mark's tint. */
    private data class Painted(val background: List<String>, val markTint: String?)

    private fun painted(path: String): Painted {
        val doc = xml(path)
        val solid = doc.all("solid").map { it.attr("android:color") }
        val gradient = doc.all("gradient").flatMap { listOf(it.attr("android:startColor"), it.attr("android:endColor")) }
        val tint = doc.all("bitmap").single().attr("android:tint").ifEmpty { null }
        return Painted(solid + gradient, tint)
    }

    @Test
    fun thePhonePreviewsPaintWithTheColoursTheRealPhoneIconsUse() {
        styles.forEach { style ->
            val real = "mobile/src/play/res/mipmap-anydpi-v26/ic_launcher_play_${style.preferenceValue}.xml"
            val (background, foreground) = adaptive(real)
            val expectedBackground = if (background.startsWith("@color/")) listOf(background) else {
                // A gradient: the real icon's background is a shape with the two end colours.
                xml("mobile/src/play/res/drawable/${background.removePrefix("@drawable/")}.xml").all("gradient")
                        .single().let { listOf(it.attr("android:startColor"), it.attr("android:endColor")) }
            }
            val preview = painted(previewPath("phone", style))
            assertEquals("${style.name}: the phone preview's background must be the real icon's",
                    expectedBackground, preview.background)

            val expectedTint = if (foreground.startsWith("@drawable/")) {
                tintOf("mobile/src/play/res/drawable/${foreground.removePrefix("@drawable/")}.xml")
            } else null
            assertEquals("${style.name}: the phone preview's mark must be tinted as the real icon's is",
                    expectedTint, preview.markTint)
        }
    }

    @Test
    fun theWatchPreviewsPaintWithTheColoursTheRealWatchIconsUse() {
        styles.forEach { style ->
            val (background, foreground) = adaptive("wear/src/play/res/mipmap-anydpi-v26/ic_launcher_play_${style.preferenceValue}.xml")
            val expected: List<String> = if (background.startsWith("@color/")) {
                listOf(colour(watchColourFiles, background))
            } else {
                xml("wear/src/play/res/drawable/${background.removePrefix("@drawable/")}.xml").all("gradient").single()
                        .let { listOf(colour(watchColourFiles, it.attr("android:startColor")), colour(watchColourFiles, it.attr("android:endColor"))) }
            }
            val preview = painted(previewPath("watch", style))
            assertEquals("${style.name}: the watch preview's background must be the watch icon's",
                    expected, preview.background.map { colour(phoneColourFiles, it) })

            // Flat watch styles draw the shared dark mark untinted; the preview recolours the
            // phone's copy of it, so the colour it uses must be the mark's own.
            val expectedMark = if (foreground.startsWith("@drawable/")) {
                colour(watchColourFiles, tintOf("wear/src/play/res/drawable/${foreground.removePrefix("@drawable/")}.xml"))
            } else darkMarkColour()
            val actualMark = colour(phoneColourFiles, preview.markTint ?: fail("no tint").let { error("unreachable") })
            // The shared mark is flat to within a few levels, not to the digit, so the preview's
            // single colour is allowed to be that close to it - and no closer is needed to look right.
            assertTrue("${style.name}: the watch preview's mark is $actualMark but the watch icon's is $expectedMark",
                    within(expectedMark, actualMark, 3))
        }
    }

    private fun within(a: String, b: String, tolerance: Int): Boolean =
            (0 until 3).all { Math.abs(a.substring(1 + 2 * it, 3 + 2 * it).toInt(16) - b.substring(1 + 2 * it, 3 + 2 * it).toInt(16)) <= tolerance }

    // ---- the shared artwork behind both ----

    /**
     * A decoded launcher mark: [rgba] holds four bytes per pixel. Decoded by hand because the unit
     * test classpath has no image library (`javax.imageio` is not visible to this module's tests)
     * and the two marks are the one simple shape of PNG - 8-bit RGBA, not interlaced - which the
     * decoder insists on, so a re-exported mark in another format fails loudly instead of being
     * misread.
     */
    private class Mark(val width: Int, val height: Int, private val rgba: ByteArray) {
        fun alpha(x: Int, y: Int) = rgba[(y * width + x) * 4 + 3].toInt() and 0xFF
        fun channel(x: Int, y: Int, c: Int) = rgba[(y * width + x) * 4 + c].toInt() and 0xFF
    }

    private fun glyph(path: String): Mark {
        val bytes = file(path).readBytes()
        fun int(at: Int) = ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
                ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)
        assertEquals("not a PNG: $path", 0x89, bytes[0].toInt() and 0xFF)
        var width = 0
        var height = 0
        val compressed = ByteArrayOutputStream()
        var at = 8
        while (at < bytes.size) {
            val length = int(at)
            val type = String(bytes, at + 4, 4, Charsets.US_ASCII)
            when (type) {
                "IHDR" -> {
                    width = int(at + 8)
                    height = int(at + 12)
                    assertEquals("$path must be 8-bit", 8, bytes[at + 16].toInt())
                    assertEquals("$path must be RGBA", 6, bytes[at + 17].toInt())
                    assertEquals("$path must not be interlaced", 0, bytes[at + 20].toInt())
                }
                "IDAT" -> compressed.write(bytes, at + 8, length)
            }
            at += 12 + length
        }
        val stride = width * 4
        val raw = ByteArray((stride + 1) * height)
        val inflater = Inflater().apply { setInput(compressed.toByteArray()) }
        var filled = 0
        while (filled < raw.size && !inflater.finished()) filled += inflater.inflate(raw, filled, raw.size - filled)
        inflater.end()
        assertEquals("truncated image data in $path", raw.size, filled)

        val pixels = ByteArray(stride * height)
        for (y in 0 until height) {
            val filter = raw[y * (stride + 1)].toInt()
            for (i in 0 until stride) {
                val x = raw[y * (stride + 1) + 1 + i].toInt() and 0xFF
                val left = if (i >= 4) pixels[y * stride + i - 4].toInt() and 0xFF else 0
                val up = if (y > 0) pixels[(y - 1) * stride + i].toInt() and 0xFF else 0
                val upLeft = if (y > 0 && i >= 4) pixels[(y - 1) * stride + i - 4].toInt() and 0xFF else 0
                val value = when (filter) {
                    0 -> x
                    1 -> x + left
                    2 -> x + up
                    3 -> x + (left + up) / 2
                    4 -> {
                        val p = left + up - upLeft
                        val pa = Math.abs(p - left)
                        val pb = Math.abs(p - up)
                        val pc = Math.abs(p - upLeft)
                        x + if (pa <= pb && pa <= pc) left else if (pb <= pc) up else upLeft
                    }
                    else -> fail("unknown PNG filter $filter in $path").let { error("unreachable") }
                }
                pixels[y * stride + i] = value.toByte()
            }
        }
        return Mark(width, height, pixels)
    }

    private val phoneGlyph = "mobile/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png"
    private val watchGlyph = "common/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png"

    /** The watch mark's colour as drawn: the mean of its fully opaque pixels, which are flat to a few levels. */
    private fun darkMarkColour(): String {
        val image = glyph(watchGlyph)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (image.alpha(x, y) == 255) { r += image.channel(x, y, 0); g += image.channel(x, y, 1); b += image.channel(x, y, 2); n++ }
        }
        assertTrue(n > 1000)
        return "#%02X%02X%02X".format((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    @Test
    fun thePhoneAndWatchMarksAreTheSameShape() {
        // The phone's preview of the watch icon recolours the phone's own copy of the mark rather
        // than shipping a second one, which is only honest while the two are one drawing, and the
        // tinted cream icons rely on the alpha channel being the whole shape.
        val a = glyph(phoneGlyph)
        val b = glyph(watchGlyph)
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
        var worst = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            worst = maxOf(worst, Math.abs(a.alpha(x, y) - b.alpha(x, y)))
        }
        assertEquals("the phone and watch marks differ in shape", 0, worst)
    }

    @Test
    fun theMarkIsFlatColourSoATintReproducesItExactly() {
        // A tint replaces the colour and keeps the alpha, so it can only stand in for the artwork
        // where the artwork is one colour. The phone's is pure white; the watch's is a flat near-black.
        val phone = glyph(phoneGlyph)
        for (y in 0 until phone.height) for (x in 0 until phone.width) {
            if (phone.alpha(x, y) == 255) {
                for (c in 0..2) assertEquals("phone mark pixel ($x,$y) channel $c", 255, phone.channel(x, y, c))
            }
        }
        val mean = darkMarkColour()
        val channels = listOf(1, 3, 5).map { mean.substring(it, it + 2).toInt(16) }
        assertTrue("the watch mark should be near-black, was $mean", channels.all { it < 64 })
    }

    // ---- the previews are laid out for the size the picker row gives them ----

    @Test
    fun thePreviewMarkIsEnlargedTheWayALauncherEnlargesIt() {
        // An adaptive icon is 108dp of artwork behind a 72dp window, so the mark is drawn 1.5x the
        // window around its centre: on a 44dp preview that is 11dp of negative inset per side.
        val row = xml("mobile/src/main/res/layout/item_icon_style_choice.xml")
        listOf("preview_phone", "preview_watch").forEach { id ->
            val view = row.all("ImageView").single { it.attr("android:id") == "@+id/$id" }
            assertEquals("44dp", view.attr("android:layout_width"))
            assertEquals("44dp", view.attr("android:layout_height"))
            assertEquals("fitXY", view.attr("android:scaleType"))
        }
        val window = 44.0
        val inset = window * (108.0 / 72.0 - 1.0) / 2.0
        styles.forEach { style ->
            listOf("phone", "watch").forEach { device ->
                val markItem = xml(previewPath(device, style)).all("item").last()
                listOf("left", "top", "right", "bottom").forEach { side ->
                    assertEquals("${style.name} $device $side", "${-inset.toInt()}dp", markItem.attr("android:$side"))
                }
            }
        }
    }
}
