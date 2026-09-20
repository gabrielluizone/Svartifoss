package com.svartifoss.snfell.watch.tile

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The two Tiles must remain recognisable before either one is added to the carousel.
 *
 * Wear OS builds that chooser from service metadata, not from the live ProtoLayout. Reusing the
 * app name, provider icon and preview therefore made two different features appear as two copies
 * of "Svartifoss" even though their live layouts were unrelated. This pins all three identity
 * channels because changing only one still leaves some OEM pickers ambiguous.
 */
class TilePickerIdentityContractTest {

    @Test
    fun `tile providers have distinct functional identities`() {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(moduleDir(), "src/main/AndroidManifest.xml"))

        val media = service(document.documentElement, MEDIA_SERVICE)
        val shortcuts = service(document.documentElement, SHORTCUTS_SERVICE)

        assertEquals("@string/queue_now_playing", androidAttribute(media, "label"))
        assertEquals("@string/shortcuts_tile_title", androidAttribute(shortcuts, "label"))
        assertDistinct("labels", androidAttribute(media, "label"), androidAttribute(shortcuts, "label"))
        assertDistinct("icons", androidAttribute(media, "icon"), androidAttribute(shortcuts, "icon"))

        val mediaPreview = previewResource(media)
        val shortcutsPreview = previewResource(shortcuts)
        assertDistinct("previews", mediaPreview, shortcutsPreview)
        assertDrawableExists(mediaPreview)
        assertDrawableExists(shortcutsPreview)
    }

    private fun service(root: Element, className: String): Element {
        val services = root.getElementsByTagName("service")
        for (index in 0 until services.length) {
            val service = services.item(index) as? Element ?: continue
            if (androidAttribute(service, "name") == className) return service
        }
        throw AssertionError("Missing Tile service $className")
    }

    private fun previewResource(service: Element): String {
        val metadata = service.getElementsByTagName("meta-data")
        for (index in 0 until metadata.length) {
            val entry = metadata.item(index) as? Element ?: continue
            if (androidAttribute(entry, "name") == PREVIEW_METADATA) {
                return androidAttribute(entry, "resource")
            }
        }
        throw AssertionError("${androidAttribute(service, "name")} has no Tile preview")
    }

    private fun assertDistinct(kind: String, first: String, second: String) {
        assertTrue("first Tile $kind should not be blank", first.isNotBlank())
        assertTrue("second Tile $kind should not be blank", second.isNotBlank())
        assertNotEquals("Both Tile providers reuse the same $kind", first, second)
    }

    private fun assertDrawableExists(reference: String) {
        val name = reference.removePrefix("@drawable/")
        assertTrue(
            "Tile preview $reference does not exist",
            File(moduleDir(), "src/main/res/drawable/$name.xml").isFile
        )
    }

    private fun androidAttribute(element: Element, name: String): String =
        element.getAttributeNS(ANDROID_NAMESPACE, name)

    private fun moduleDir(): File =
        listOf(File("."), File("wear"))
            .firstOrNull { File(it, "src/main/AndroidManifest.xml").isFile }
            ?: throw AssertionError("Could not locate the wear module from ${File(".").absolutePath}")

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val PREVIEW_METADATA = "androidx.wear.tiles.PREVIEW"
        const val MEDIA_SERVICE = ".watch.tile.MediaTileService"
        const val SHORTCUTS_SERVICE = ".watch.tile.ShortcutsTileService"
    }
}
