package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the small XML contracts behind the phone theme-library UX. */
class ThemeUiResourceContractTest {

    @Test
    fun `destructive apply-to-all action is not exposed`() {
        val settings = mobileResource("xml/watch_face_settings.xml").readText()

        assertFalse(settings.contains("apply_appearance_to_all_faces"))
    }

    @Test
    fun `indeterminate community theme waits use music bars`() {
        listOf(
                "layout/activity_community_theme_gallery.xml",
                "layout/item_community_theme_card.xml",
                "layout/activity_submit_community_theme.xml"
        ).forEach { path ->
            val layout = mobileResource(path).readText()
            assertTrue("$path should use MusicLoadingBarsView", layout.contains(
                    "com.svartifoss.snfell.view.MusicLoadingBarsView"))
            assertFalse("$path should not expose a circular ProgressBar", layout.contains(
                    "<ProgressBar"))
        }
    }

    @Test
    fun `installed community theme uses a saved marker instead of badge text`() {
        val card = mobileResource("layout/item_community_theme_card.xml").readText()

        assertTrue(card.contains("@+id/community_theme_installed_marker"))
        assertTrue(card.contains("android:src=\"@drawable/ic_playlist_add_check\""))
        assertTrue(card.contains("android:contentDescription=\"@string/online_theme_installed\""))
        assertFalse(card.contains("android:text=\"@string/online_theme_installed\""))
    }

    @Test
    fun `community dialogs reuse the accent floor dialog contract`() {
        val gallery = mobileSource(
                "view/watchface/theme/OnlineThemesActivity.kt").readText()
        val detail = mobileSource(
                "view/watchface/theme/CommunityThemeDetailActivity.kt").readText()
        val styling = mobileSource("view/LyraDialogStyling.kt").readText()
        val styles = mobileResource("values/styles.xml").readText()

        assertTrue(gallery.contains("AlertDialog.Builder(this)"))
        assertTrue(gallery.contains(".setSingleChoiceItems(labels, selectedIndex)"))
        assertTrue(gallery.contains("applyLyraDialogStyling"))
        assertFalse(gallery.contains("MaterialAlertDialogBuilder"))
        assertFalse(gallery.contains("R.layout.item_described_choice"))

        assertTrue(detail.contains("AlertDialog.Builder(this)"))
        assertTrue(detail.contains("positiveColor = error.currentTextColor"))
        assertTrue(detail.contains("applyLyraDialogStyling"))
        assertFalse(detail.contains("MaterialAlertDialogBuilder"))

        assertTrue(styling.contains("CheckedTextView"))
        assertTrue(styling.contains("state_checked"))
        assertTrue(styles.contains("<style name=\"LyraAlertDialogTheme\""))
        assertTrue(styles.contains("<item name=\"alertDialogTheme\">" +
                "@style/LyraAlertDialogTheme</item>"))
    }

    private fun mobileResource(path: String): File = listOf(
            File("src/main/res/$path"),
            File("mobile/src/main/res/$path")
    ).first { it.isFile }

    private fun mobileSource(path: String): File = listOf(
            File("src/main/java/com/svartifoss/snfell/$path"),
            File("mobile/src/main/java/com/svartifoss/snfell/$path")
    ).first { it.isFile }
}
