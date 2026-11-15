package com.svartifoss.snfell.res

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pins what the "What's new" window depends on that no single function owns: every note exists in
 * every locale (a missing one falls back to English, or fails the release's `MissingTranslation`
 * check), every translation carries the placeholders the code fills in, and the window is wired to
 * the build that has no updater of its own and to nothing else.
 */
class WhatsNewContractTest {

    private fun repoFile(path: String): File {
        listOf(File(path), File("../$path")).forEach { if (it.exists()) return it }
        fail("Not found from either module dir or repo root: $path")
        error("unreachable")
    }

    private val res get() = repoFile("mobile/src/main/res")

    private fun strings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val node = nodes.item(it)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    private fun placeholders(text: String) = Regex("%\\d\\$[sd]").findAll(text).map { it.value }.sorted().toList()

    private val base get() = strings(File(res, "values/whats_new_strings.xml"))

    /** Folders that are real translations: a `values-*` with its own strings.xml. */
    private val localeFolders get() = res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!
            .sortedBy { it.name }

    @Test
    fun theBaseDeclaresEveryNoteTheCodeReads() {
        val read = Regex("R\\.string\\.(whats_new_item_\\w+)")
                .findAll(repoFile("mobile/src/main/java/com/svartifoss/snfell/whatsnew/WhatsNewPrompt.kt").readText())
                .map { it.groupValues[1] }.toSet()
        assertTrue("the window reads no notes at all", read.isNotEmpty())
        assertEquals("the notes the window reads and the notes the base declares differ", read, base.keys)
    }

    @Test
    fun everyTranslatedLocaleCarriesEveryNote() {
        val folders = localeFolders
        assertTrue("found no translated locales; the test would pass vacuously", folders.size >= 40)
        folders.forEach { folder ->
            val file = File(folder, "whats_new_strings.xml")
            assertTrue("${folder.name} has no whats_new_strings.xml", file.exists())
            val translated = strings(file)
            assertEquals("${folder.name} must define exactly the base notes", base.keys, translated.keys)
            base.forEach { (key, english) ->
                val text = translated.getValue(key)
                assertTrue("${folder.name}/$key is empty", text.isNotBlank())
                assertEquals(
                        "${folder.name}/$key must carry the same placeholders as the English",
                        placeholders(english), placeholders(text))
            }
        }
    }

    @Test
    fun theNotesFitOneScreenOfADialog() {
        // A greeting, not a changelog: if this grows past a handful of lines it should be cut, not scrolled.
        assertTrue("more than eight notes", base.size <= 8)
        base.forEach { (key, text) -> assertTrue("$key is longer than 140 characters", text.length <= 140) }
    }

    @Test
    fun theWindowIsWiredOnlyWhereThereIsNoUpdaterScreen() {
        val main = repoFile("mobile/src/main/java/com/svartifoss/snfell/view/mainactivity/MainActivity.kt").readText()
        val call = main.indexOf("WhatsNewPrompt.attach(this)")
        assertTrue("MainActivity never attaches the What's new window", call >= 0)
        val guard = main.lastIndexOf("if (!UpdateGateway.SUPPORTS_SELF_UPDATE)", call)
        assertTrue("the window must be gated on the build having no self-updater", guard >= 0 && call - guard < 200)
    }

    @Test
    fun theGreetingKeepsItsStateOutOfThePreferencesTheWatchMirrors() {
        val source = repoFile("mobile/src/main/java/com/svartifoss/snfell/whatsnew/WhatsNewPrompt.kt").readText()
        assertTrue("must use a preferences file of its own", source.contains("getSharedPreferences(PREFS"))
        assertFalse("must not touch the default preferences, which are mirrored to the watch",
                source.contains("getDefaultSharedPreferences"))
    }

    @Test
    fun theGreetingIsAScreenRegisteredNotExportedAndBuiltFromTheUpdatesScreensPieces() {
        val prompt = repoFile("mobile/src/main/java/com/svartifoss/snfell/whatsnew/WhatsNewPrompt.kt").readText()
        assertTrue("the prompt must open the screen", prompt.contains("WhatsNewActivity::class.java"))

        val manifest = repoFile("mobile/src/main/AndroidManifest.xml").readText()
        val declaration = Regex("<activity[^>]*\\.whatsnew\\.WhatsNewActivity[^>]*/>").find(manifest)
        assertTrue("WhatsNewActivity must be declared in the main manifest", declaration != null)
        assertTrue("WhatsNewActivity must not be exported", declaration!!.value.contains("android:exported=\"false\""))

        // The point of a screen over a dialog: it is the Updates screen's sibling, with the cover
        // and the version cards, not a plain message box.
        val layout = repoFile("mobile/src/main/res/layout/activity_whats_new.xml").readText()
        assertTrue("the cover is the Svartifoss waterfall", layout.contains("SvartifossWaterfallView"))
        assertTrue("the status card", layout.contains("@string/update_state_current"))
        assertTrue("the version card", layout.contains("@string/update_device_watch") && layout.contains("@+id/text_phone_version"))
        assertTrue("the notes are rows in a card", layout.contains("@+id/notes_container"))
    }

    @Test
    fun everyRowHasAGlyphThatExists() {
        val prompt = repoFile("mobile/src/main/java/com/svartifoss/snfell/whatsnew/WhatsNewPrompt.kt").readText()
        val glyphs = Regex("R\\.drawable\\.(\\w+)").findAll(prompt).map { it.groupValues[1] }.toList()
        assertEquals("one glyph per note", base.size, glyphs.size)
        glyphs.forEach { name ->
            assertTrue("$name is not a drawable of the phone app",
                    File(res, "drawable/$name.xml").exists() || File(res, "drawable/$name.png").exists())
        }
    }

    @Test
    fun theBuildCarriesTheNotesItShows() {
        val gradle = repoFile("mobile/build.gradle").readText()
        val versionCode = Regex("versionCode\\s+(\\d+)").find(gradle)!!.groupValues[1].toLong()
        val first = Regex("FIRST_RELEASE_WITH_NOTES\\s*=\\s*(\\d+)L")
                .find(repoFile("mobile/src/main/java/com/svartifoss/snfell/whatsnew/WhatsNewPrompt.kt").readText())!!
                .groupValues[1].toLong()
        assertTrue(
                "FIRST_RELEASE_WITH_NOTES ($first) is above this build's versionCode ($versionCode): " +
                        "the window could never show on the build that is meant to carry it",
                first <= versionCode)
    }
}
