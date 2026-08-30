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
        assertTrue(card.contains("android:src=\"@drawable/ic_download_for_offline\""))
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

    @Test
    fun `community submission follows the runtime accent with readable controls`() {
        val source = mobileSource(
                "view/watchface/theme/SubmitCommunityThemeActivity.kt").readText()

        assertTrue(source.contains("LyraAccent.affectsResolvedColor"))
        assertTrue(source.contains("submitButton.backgroundTintList"))
        assertTrue(source.contains("LyraAccent.foregroundFor(accent)"))
        assertTrue(source.contains("submitButton.iconTint"))
        assertTrue(source.contains("progress.setBarsColor(accentOnBackground)"))
        assertTrue(source.contains("LyraAccent.applyToEditText(input, accentOnBackground)"))
        assertTrue(source.contains("layout.boxStrokeColor = accentOnBackground"))
        assertTrue(source.contains("layout.setCounterTextColor"))
        assertTrue(source.contains("dialog.applyLyraDialogStyling"))
    }

    @Test
    fun `community submission fields use centered Google Sans metrics`() {
        val layout = mobileResource("layout/activity_submit_community_theme.xml").readText()

        listOf("public_name_input", "author_input").forEach { id ->
            val input = selfClosingElementWithId(layout, id)
            assertTrue("$id should center its line vertically",
                    input.contains("android:gravity=\"start|center_vertical\""))
            assertTrue("$id should exclude asymmetric font padding",
                    input.contains("android:includeFontPadding=\"false\""))
            assertTrue("$id should have a stable line height",
                    input.contains("app:lineHeight=\"21sp\""))
            assertTrue("$id should not add letter spacing",
                    input.contains("android:letterSpacing=\"0\""))
        }
    }

    @Test
    fun `community submission can hide its public pseudonym without changing account ownership`() {
        val layout = mobileResource("layout/activity_submit_community_theme.xml").readText()
        val source = mobileSource(
                "view/watchface/theme/SubmitCommunityThemeActivity.kt").readText()
        val strings = mobileResource("values/watch_themes_strings.xml").readText()

        assertTrue(layout.contains("@+id/anonymous_author_switch"))
        assertTrue(layout.contains("@string/community_theme_submit_publish_anonymously"))
        assertTrue(source.contains("anonymousAuthorSwitch.isChecked"))
        assertTrue(source.contains("ANONYMOUS_AUTHOR = \"Anonymous\""))
        assertTrue(source.contains("authorLayout.visibility"))
        assertTrue(source.contains("submissionRepository.signInWithGoogle"))
        assertTrue(strings.contains("name=\"community_theme_submit_publish_anonymously\""))
    }

    @Test
    fun `community account has an explicit device-only disconnect surface`() {
        val settings = mobileResource("xml/settings.xml").readText()
        val layout = mobileResource("layout/activity_community_theme_account.xml").readText()
        val activity = mobileSource(
                "view/watchface/theme/CommunityThemeAccountActivity.kt").readText()
        val auth = mobileSource(
                "view/watchface/theme/CommunityThemeGoogleAuthentication.kt").readText()
        val submission = mobileSource(
                "view/watchface/theme/SubmitCommunityThemeActivity.kt").readText()
        val manifest = mobileManifest().readText()

        assertTrue(settings.contains("android:key=\"cat_community_themes\""))
        assertTrue(settings.contains("android:key=\"community_theme_account\""))
        assertTrue(settings.contains("android:icon=\"@drawable/ic_account_circle\""))
        assertTrue(layout.contains("android:src=\"@drawable/ic_account_circle\""))
        assertTrue(layout.contains("@+id/button_community_account_sign_in"))
        assertTrue(layout.contains("@+id/button_community_account_sign_out"))
        assertTrue(activity.contains("signOutFromThisDevice"))
        assertFalse(activity.contains(".delete()"))
        assertTrue(auth.contains("CommunityThemeAccountState.GOOGLE"))
        assertTrue(submission.contains("restoreLastAuthorPseudonym()"))
        assertTrue(submission.contains("rememberAuthorPseudonym(author)"))
        assertTrue(manifest.contains("CommunityThemeAccountActivity"))
    }

    @Test
    fun `account deletion asks what should happen to already published themes`() {
        val layout = mobileResource("layout/activity_community_theme_account.xml").readText()
        val dialog = mobileResource("layout/dialog_community_account_delete.xml").readText()
        val activity = mobileSource(
                "view/watchface/theme/CommunityThemeAccountActivity.kt").readText()
        val repository = mobileSource(
                "view/watchface/theme/CommunityThemeAccountDeletion.kt").readText()
        val rules = repositoryFile("firestore.rules").readText()

        assertTrue(layout.contains("@+id/button_community_account_delete"))
        assertTrue(dialog.contains("@+id/community_account_delete_keep"))
        assertTrue(dialog.contains("@+id/community_account_delete_remove"))
        // Both halves of the choice must reach Firestore under the exact values its rules accept,
        // and the request has to stay a create so the publisher's Git and Firestore halves agree.
        assertTrue(repository.contains("KEEP_THEMES(\"keep\")"))
        assertTrue(repository.contains("DELETE_THEMES(\"delete\")"))
        assertTrue(rules.contains("match /communityThemeAccountDeletion/{uid}"))
        assertTrue(rules.contains("data.themeDisposition in [\"keep\", \"delete\"]"))
        assertFalse(rules.contains("allow update: if signedIn()\n          && uid == request.auth.uid"))
        // The app records the decision; it never deletes the identity from the device that holds it.
        assertFalse(activity.contains(".delete()"))
        assertTrue(activity.contains("requestDeletion"))
    }

    @Test
    fun `an author can see their own submissions without seeing who reviewed them`() {
        val rules = repositoryFile("firestore.rules").readText()
        val repository = mobileSource(
                "view/watchface/theme/CommunityThemeSubmissions.kt").readText()
        val admin = repositoryFile("docs/admin/admin.js").readText()

        // The split is what makes the author read safe; a reviewer field back on the intake
        // document would leak to every one of those reads at once.
        assertTrue(rules.contains("match /themeIntakeReview/{themeId}"))
        assertFalse(rules.contains("after.reviewedBy"))
        assertTrue(rules.contains("resource.data.ownerUid == request.auth.uid)"))
        assertTrue(repository.contains("whereEqualTo(FIELD_OWNER_UID, uid)"))
        // Every moderator action writes the transition and its author in one batch.
        assertTrue(admin.contains("firebase.writeBatch"))
        assertTrue(admin.contains("REVIEW_COLLECTION"))
    }

    @Test
    fun `the community account leads Settings General rather than Data and support`() {
        val settings = mobileResource("xml/settings.xml").readText()
        val catalog = mobileSource("view/settings/SettingsCatalog.kt").readText()

        // Page order comes from the XML, so leading General means leading the file.
        val communityIndex = settings.indexOf("android:key=\"cat_community_themes\"")
        val updatesIndex = settings.indexOf("android:key=\"cat_updates\"")
        assertTrue(communityIndex in 0 until updatesIndex)
        assertTrue(catalog.contains("SECTION_GENERAL to setOf(\n                    \"cat_community_themes\""))
    }

    @Test
    fun `Watch Text uses one contextual editor while legacy rows remain searchable`() {
        val settings = mobileResource("xml/watch_face_settings.xml").readText()
        val layout = mobileResource("layout/pref_typography_editor.xml").readText()
        val source = mobileSource("view/watchface/WatchFacePrefsFragment.kt").readText()
        val host = mobileSource("view/mainactivity/MainActivity.kt").readText()

        assertTrue(settings.contains("android:key=\"cat_wf_typography_editor\""))
        assertTrue(settings.contains("android:key=\"typography_editor_surface\""))
        listOf(
                "cat_wf_typography_font",
                "cat_wf_typography_title",
                "cat_wf_typography_artist",
                "cat_wf_typography_track_time",
                "cat_wf_typography_clock",
                "cat_wf_typography_icon",
                "cat_wf_typography_flex"
        ).forEach { assertTrue("legacy search category $it should remain", settings.contains(it)) }

        listOf(
                "typography_font_button",
                "typography_target_scroll",
                "typography_target_group",
                "typography_target_title",
                "typography_target_artist",
                "typography_target_track_time",
                "typography_target_clock",
                "typography_target_icon",
                "typography_target_lyrics",
                "typography_flex_button",
                "typography_element_font_button",
                "typography_element_flex_button",
                "typography_weight_button",
                "typography_italic_button",
                "typography_size_button",
                "typography_opacity_button",
                "typography_tracking_button"
        ).forEach { assertTrue("compact editor should expose $it", layout.contains("@+id/$it")) }

        assertTrue(source.contains("initTypographyEditor()"))
        assertTrue(source.contains("TypographyEditorModel.searchTargetFor(key)"))
        assertTrue(source.contains("!compactTypography || key == TYPOGRAPHY_EDITOR_CATEGORY"))
        assertTrue(source.contains("root.setTag(R.id.tag_handles_accent_locally, true)"))
        assertTrue(source.contains("LyraAccent.affectsResolvedColor(baseKey)"))
        assertTrue(source.contains("typographyEditor?.revealAndPulse("))
        assertTrue(host.contains("view.getTag(R.id.tag_handles_accent_locally) == true"))
    }

    private fun mobileResource(path: String): File = listOf(
            File("src/main/res/$path"),
            File("mobile/src/main/res/$path")
    ).first { it.isFile }

    private fun mobileSource(path: String): File = listOf(
            File("src/main/java/com/svartifoss/snfell/$path"),
            File("mobile/src/main/java/com/svartifoss/snfell/$path")
    ).first { it.isFile }

    private fun mobileManifest(): File = listOf(
            File("src/main/AndroidManifest.xml"),
            File("mobile/src/main/AndroidManifest.xml")
    ).first { it.isFile }

    /** Repository-root infrastructure the app has to agree with, such as the Firestore rules. */
    private fun repositoryFile(path: String): File = listOf(
            File("../$path"),
            File(path)
    ).first { it.isFile }

    private fun selfClosingElementWithId(xml: String, id: String): String {
        val marker = "android:id=\"@+id/$id\""
        val idIndex = xml.indexOf(marker)
        require(idIndex >= 0) { "Missing XML id $id" }
        val start = xml.lastIndexOf('<', idIndex)
        val end = xml.indexOf("/>", idIndex)
        require(start >= 0 && end >= 0) { "Expected a self-closing XML element for $id" }
        return xml.substring(start, end + 2)
    }
}
