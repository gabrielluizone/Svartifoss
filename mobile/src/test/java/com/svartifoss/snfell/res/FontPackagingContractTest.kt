package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pins that both apps ship their bundled fonts uncompressed.
 *
 * Android builds a font from the APK in one of two ways, and which one is decided by how the zip
 * entry is stored: an uncompressed entry is mapped straight from the file (pages loaded as they are
 * read, shared, and droppable under memory pressure), a compressed one is inflated whole into a
 * direct buffer on the Java heap - for every Typeface built from it, and for good, because the
 * platform keeps every family it ever derived a style from. On 2026-11-04 the watch held Google
 * Sans regular and bold four times each, 15 MB of a 20 MB heap with the app closed, and the phone's
 * font picker can load 122 fonts (40 MB) into a process that then lives on as the music service.
 *
 * Nothing about a compressed font fails: it renders identically and costs only memory. Hence this.
 */
class FontPackagingContractTest {

    private fun repoFile(path: String): File {
        listOf(File(path), File("../$path")).forEach { if (it.exists()) return it }
        fail("Not found from either module dir or repo root: $path")
        error("unreachable")
    }

    private val appModules = listOf("mobile", "wear")

    /** The extensions a module's build file tells the packager to leave uncompressed. */
    private fun uncompressedExtensions(module: String): Set<String> =
            repoFile("$module/build.gradle").readLines()
                    .map { it.trim() }
                    .filter { !it.startsWith("//") && it.startsWith("noCompress") }
                    .flatMap { line -> Regex("""['"]\.?([A-Za-z0-9]+)['"]""").findAll(line).map { it.groupValues[1].lowercase() }.toList() }
                    .toSet()

    @Test
    fun bothAppsLeaveFontsUncompressed() {
        appModules.forEach { module ->
            val extensions = uncompressedExtensions(module)
            assertTrue("$module/build.gradle must keep `androidResources { noCompress 'ttf', 'otf' }`. " +
                    "A compressed font is copied whole into the heap for every Typeface built from " +
                    "it and never freed; it still renders, so nothing else will notice. Found: $extensions",
                    extensions.containsAll(listOf("ttf", "otf")))
        }
    }

    @Test
    fun everyBundledFontHasAnExtensionThePackagerLeavesUncompressed() {
        appModules.forEach { module ->
            val extensions = uncompressedExtensions(module)
            val fontDirs = repoFile("$module/src").walkTopDown()
                    .filter { it.isDirectory && it.name == "font" && it.parentFile?.name == "res" }
                    .toList()
            assertTrue("$module has no res/font directory - has the layout moved?", fontDirs.isNotEmpty())
            val compressed = fontDirs.flatMap { dir -> dir.listFiles().orEmpty().toList() }
                    .filter { it.isFile && it.extension.lowercase() != "xml" }
                    .filter { it.extension.lowercase() !in extensions }
                    .map { it.path }
            assertEquals("These fonts would be packaged compressed, so every load copies them whole " +
                    "into the heap. Add their extension to noCompress in $module/build.gradle.",
                    emptyList<String>(), compressed)
        }
    }
}
