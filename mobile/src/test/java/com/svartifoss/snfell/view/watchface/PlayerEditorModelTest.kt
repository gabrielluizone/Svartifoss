package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.TrackMetadataFields
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Player page's compact editor against the preferences it is a view over.
 *
 * This one carries more than the parity checks its three siblings do, because the Player page is
 * where the per-face capability rules live: which controls a given face can actually consume is a
 * decision list that goes stale silently when a face is added, and it now exists in two places -
 * the fragment's `updatePlayerCapabilityVisibility` and [PlayerEditorModel.appliesToFace]. These
 * tests are what make the second copy checkable.
 */
class PlayerEditorModelTest {

    @Test
    fun `every declared player row is owned by the editor`() {
        val declared = playerRowsInXml()
        assertTrue("watch_face_settings.xml should declare player rows", declared.size > 15)

        val missing = declared - PlayerEditorModel.keys
        assertTrue(
                "These Player rows have no editor control, so the Player page cannot reach them: " +
                        "$missing",
                missing.isEmpty())
    }

    @Test
    fun `every editor key is a real preference`() {
        val orphans = PlayerEditorModel.keys - playerRowsInXml()
        assertTrue(
                "These editor keys are not declared in watch_face_settings.xml: $orphans",
                orphans.isEmpty())
    }

    @Test
    fun `resetting every face is no longer offered on the page`() {
        // Removed deliberately: a whole-library reset one mistap from "reset this layout" was too
        // destructive to sit there permanently. FaceResetMigrationPrompt still performs it, once,
        // where it can explain why it is asking - so the helper survives while the row does not.
        assertNull(PlayerEditorModel.specFor("reset_all_faces"))
        assertFalse("reset_all_faces" in playerRowsInXml())
        assertTrue("reset_appearance" in PlayerEditorModel.keys)
    }

    @Test
    fun `only the face selector escapes face scoping`() {
        // Scoping the face selector by face would be circular; everything else on an appearance
        // page must be per-face or it silently repaints every face at once.
        val unscoped = PlayerEditorModel.keys
                .filter { PlayerEditorModel.specFor(it)?.persisted == true }
                .filterNot(FaceScopedPreferences::isScoped)

        assertEquals(listOf(MiscPreferences.WEAR_SCREEN_FACE.key), unscoped)
    }

    @Test
    fun `the reset row is an action rather than a value`() {
        assertFalse(PlayerEditorModel.specFor("reset_appearance")!!.persisted)
    }

    @Test
    fun `every element chip carries a short label and every other row does not`() {
        // A chip shows a noun; a row shows its own title. Getting this backwards is what would put
        // "Show the app icon" into a chip field five sentences wide.
        PlayerEditorModel.specsFor(PlayerSlot.ELEMENT).forEach {
            assertNotNull("${it.key} needs a chip label", it.chipLabelRes)
        }
        PlayerSlot.entries.filterNot { it == PlayerSlot.ELEMENT }.forEach { slot ->
            PlayerEditorModel.specsFor(slot).forEach {
                assertNull("${it.key} should not carry a chip label", it.chipLabelRes)
            }
        }
    }

    @Test
    fun `every element is a toggle and every choice row is a choice`() {
        // A chip can only answer yes or no, so a multi-way value in that slot would render as a
        // checkbox over a value it cannot express.
        PlayerEditorModel.specsFor(PlayerSlot.ELEMENT).forEach {
            assertTrue("${it.key}", it.value is PlayerValueSpec.Toggle)
        }
        PlayerEditorModel.specsFor(PlayerSlot.DETAIL).forEach {
            assertTrue("${it.key}", it.value is PlayerValueSpec.Toggle)
        }
        PlayerEditorModel.specsFor(PlayerSlot.CHOICE).forEach {
            assertTrue("${it.key}", it.value is PlayerValueSpec.Choice)
        }
    }

    @Test
    fun `the details rows come from the metadata registry`() {
        // Derived, not declared: CLAUDE.md already records that a new group has to reach
        // EXPORTABLE and SCOPED_KEYS, and a fourth place to register it is the drift this avoids.
        assertEquals(
                TrackMetadataFields.Group.entries.map { it.preferenceKey },
                PlayerEditorModel.specsFor(PlayerSlot.DETAIL).map { it.key })
        TrackMetadataFields.Group.entries.forEach { group ->
            assertEquals(
                    "${group.name} default",
                    PlayerValueSpec.Toggle(group.defaultVisible),
                    PlayerEditorModel.specFor(group.preferenceKey)?.value)
        }
    }

    @Test
    fun `details are offered for the metadata face alone`() {
        ThemeAppearance.ALLOWED_BASE_FACES.forEach { face ->
            assertEquals(
                    "Details for $face",
                    face == "metadata",
                    PlayerEditorModel.visibleIn(PlayerSlot.DETAIL, face).isNotEmpty())
        }
    }

    @Test
    fun `every face keeps something to edit`() {
        // Several elements apply everywhere, so no face can land on the page with an empty
        // surface. The face selector is the one identity row every face keeps; Control style
        // joins it only on the faces in CONTROL_STYLE_FACES - see that set's own doc for why.
        ThemeAppearance.ALLOWED_BASE_FACES.forEach { face ->
            assertTrue(
                    "Elements for $face",
                    PlayerEditorModel.visibleIn(PlayerSlot.ELEMENT, face).size >= 3)
            val expectedIdentityRows = if (face in PlayerEditorModel.CONTROL_STYLE_FACES) 2 else 1
            assertEquals(
                    "Identity rows for $face",
                    expectedIdentityRows,
                    PlayerEditorModel.visibleIn(PlayerSlot.IDENTITY, face).size)
        }
    }

    @Test
    fun `control style is offered only where a face draws icons it can restyle`() {
        // Persistent icon-based transport: Classic and the curated faces sharing its glyph
        // vocabulary, plus Expressive's always-shown cookie glyph.
        listOf(
                "classic", "expressive", "vinyl", "poster", "studio", "halo", "aurora", "eclipse",
                "spectrum", "material"
        ).forEach {
            assertTrue(it, PlayerEditorModel.appliesToFace(PlayerControl.SCREEN_THEME, it))
        }
        // Frame and Ribbon draw no persistent icon, but both pass state into CenterGestureRegion,
        // so the transient tap-confirmation glyph still honours the setting.
        listOf("frame", "ribbon").forEach {
            assertTrue(it, PlayerEditorModel.appliesToFace(PlayerControl.SCREEN_THEME, it))
        }
        // No icon-based transport at all, or CenterGestureRegion called without state: the
        // picker would change nothing.
        listOf(
                "immersive", "depth", "carousel", "chat", "split", "note", "verse", "metadata"
        ).forEach {
            assertFalse(it, PlayerEditorModel.appliesToFace(PlayerControl.SCREEN_THEME, it))
        }
    }

    @Test
    fun `face-specific controls appear for exactly one face`() {
        mapOf(
                PlayerControl.QUADRANT_FLASH to "classic",
                PlayerControl.CAROUSEL_SHAPE to "carousel",
                PlayerControl.NOTE_COVER_SHAPE to "note",
                PlayerControl.SPLIT_PANEL to "split",
                PlayerControl.EXPRESSIVE_SEEK to "expressive"
        ).forEach { (control, owner) ->
            val faces = ThemeAppearance.ALLOWED_BASE_FACES.filter {
                PlayerEditorModel.appliesToFace(control, it)
            }
            assertEquals("$control", listOf(owner), faces)
        }
    }

    @Test
    fun `the two fixed-transport faces hide the player controls chip`() {
        // Expressive and Material must keep their central transport, so the toggle cannot apply.
        assertFalse(PlayerEditorModel.appliesToFace(PlayerControl.PLAYER_CONTROLS, "expressive"))
        assertFalse(PlayerEditorModel.appliesToFace(PlayerControl.PLAYER_CONTROLS, "material"))
        assertTrue(PlayerEditorModel.appliesToFace(PlayerControl.PLAYER_CONTROLS, "classic"))
    }

    /**
     * The two rows are two questions, and nine faces answer them differently.
     *
     * They shared one allow-list until the placement controls were found to be producing the three
     * failures `TextBlockPlacementSupport` records - text off the glass, text on the face's own
     * furniture, and artwork moving with the text. Merging them forced every one of those faces to
     * be all-in or all-out on a pair of controls it can honour exactly one of.
     */
    @Test
    fun `the two text block rows are gated separately`() {
        // Neither, on the faces whose metadata is in independent fixed bands: one shared choice
        // can only ever move one of them.
        listOf(PlayerControl.TEXT_BLOCK_ALIGN, PlayerControl.TEXT_BLOCK_POSITION).forEach { control ->
            listOf("frame", "ribbon", "verse", "metadata").forEach { face ->
                assertFalse("$control on $face", PlayerEditorModel.appliesToFace(control, face))
            }
            listOf("classic", "artist", "immersive", "poster").forEach { face ->
                assertTrue("$control on $face", PlayerEditorModel.appliesToFace(control, face))
            }
        }

        // Align but not position: the block may be lined up, but it has nowhere to go - Carousel's
        // two bands straddle the cover rail, Split's seam is the face, Matejdro's bands already
        // fill the whole text area, and Vinyl/Halo/Spectrum/Material each keep their own furniture
        // in the band a moved block would land on.
        listOf("carousel", "split", "matejdro", "vinyl", "halo", "spectrum", "material")
                .forEach { face ->
                    assertTrue(
                            "align on $face",
                            PlayerEditorModel.appliesToFace(PlayerControl.TEXT_BLOCK_ALIGN, face))
                    assertFalse(
                            "position on $face",
                            PlayerEditorModel.appliesToFace(
                                    PlayerControl.TEXT_BLOCK_POSITION, face))
                }

        // Position but not align: aligning a bubble thread would say who sent the current track,
        // and aligning Note's sentence drags the cover disc that centres with it.
        listOf("chat", "note").forEach { face ->
            assertFalse(
                    "align on $face",
                    PlayerEditorModel.appliesToFace(PlayerControl.TEXT_BLOCK_ALIGN, face))
            assertTrue(
                    "position on $face",
                    PlayerEditorModel.appliesToFace(PlayerControl.TEXT_BLOCK_POSITION, face))
        }
    }

    /**
     * The regression this list keeps producing: a face grows its own progress element and the
     * three places that decide whether to offer the switch are not among the files touched. The
     * switch then reads on the watch and cannot be reached on the phone.
     */
    @Test
    fun `the faces that draw their own progress can switch it off`() {
        listOf("ribbon", "frame", "verse", "spectrum").forEach { face ->
            assertTrue(
                    "$face draws its own progress but cannot switch it off",
                    PlayerEditorModel.appliesToFace(PlayerControl.INTERNAL_PROGRESS, face))
        }
        // Still not offered where nothing would change: Classic's progress is the host's edge arc.
        assertFalse(PlayerEditorModel.appliesToFace(PlayerControl.INTERNAL_PROGRESS, "classic"))
    }

    @Test
    fun `every capability rule names a registered face`() {
        // A rule naming a face that no longer exists silently hides its control forever, which is
        // indistinguishable from the control never having been written.
        val named = PlayerEditorModel.INTERNAL_PROGRESS_FACES +
                PlayerEditorModel.FIXED_TRANSPORT_FACES +
                PlayerEditorModel.CONTROL_STYLE_FACES +
                PlayerEditorModel.TEXT_BLOCK_ALIGN_FACES +
                PlayerEditorModel.TEXT_BLOCK_POSITION_FACES +
                setOf("classic", "carousel", "note", "split", "expressive", "metadata")
        val unknown = named - ThemeAppearance.ALLOWED_BASE_FACES
        assertTrue("Capability rules name unregistered faces: $unknown", unknown.isEmpty())
    }

    @Test
    fun `stored defaults match the preference definitions`() {
        assertEquals(
                PlayerValueSpec.Choice("classic"),
                PlayerEditorModel.specFor(MiscPreferences.WEAR_SCREEN_FACE.key)?.value)
        assertEquals(
                PlayerValueSpec.Choice("default"),
                PlayerEditorModel.specFor(MiscPreferences.WEAR_SCREEN_THEME.key)?.value)
        // Off by default: holding a screen awake costs battery, so no face opts in for you.
        assertEquals(
                PlayerValueSpec.Toggle(false),
                PlayerEditorModel.specFor(MiscPreferences.WEAR_KEEP_SCREEN_ON.key)?.value)
        assertEquals(
                PlayerValueSpec.Toggle(true),
                PlayerEditorModel.specFor(MiscPreferences.WEAR_EDGE_PROGRESS_VISIBLE.key)?.value)
    }

    /** The rows inside the five categories the Player page owns, read straight from the XML. */
    private fun playerRowsInXml(): Set<String> {
        val xml = File("src/main/res/xml/watch_face_settings.xml").takeIf { it.exists() }
                ?: File("mobile/src/main/res/xml/watch_face_settings.xml")
        val text = xml.readText()
        val owned = setOf(
                "cat_wf_screen_behavior",
                "cat_wf_player_layout",
                "cat_wf_player_progress",
                "cat_wf_metadata",
                "cat_wf_layout_actions")

        val keys = mutableSetOf<String>()
        var category: String? = null
        Regex("""android:key="([^"]+)"""").findAll(text).forEach { match ->
            val key = match.groupValues[1]
            if (key.startsWith("cat_")) {
                category = key
                return@forEach
            }
            // The editor surface is the view, not one of the values it edits.
            if (key == "player_editor_surface") return@forEach
            if (category in owned) keys += key
        }
        return keys
    }
}
