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
    fun `only the subject cards carry a heading`() {
        // The headings are aliases of strings every locale already translates, so a card costs no
        // translation - but only the cards built from the model have one to show. The identity
        // card and the two at the bottom are declared in the layout with headings of their own.
        val subjects = listOf(
                PlayerSlot.LAYOUT,
                PlayerSlot.TRACK_INFO,
                PlayerSlot.PROGRESS,
                PlayerSlot.CONTROLS,
                PlayerSlot.CLOCK)
        assertEquals(subjects, PlayerEditorModel.GROUPS)
        PlayerSlot.entries.filterNot { it in subjects }.forEach {
            assertNull("$it is not a subject card and has no heading", it.headingRes)
        }
    }

    @Test
    fun `every subject card holds settings and nothing else`() {
        // A subject card draws a toggle row or a picker row per spec. An action has no value to
        // show, so it belongs to the fixed button at the bottom and would render as nothing.
        PlayerEditorModel.GROUPS.forEach { slot ->
            val specs = PlayerEditorModel.specsFor(slot)
            assertTrue("$slot should not be empty", specs.isNotEmpty())
            specs.forEach {
                assertTrue("${it.key} in $slot", it.value !is PlayerValueSpec.Action)
            }
        }
        PlayerEditorModel.specsFor(PlayerSlot.DETAIL).forEach {
            assertTrue("${it.key}", it.value is PlayerValueSpec.Toggle)
        }
        PlayerEditorModel.specsFor(PlayerSlot.BEHAVIOUR).forEach {
            assertTrue("${it.key}", it.value is PlayerValueSpec.Toggle)
        }
        PlayerEditorModel.specsFor(PlayerSlot.ACTION).forEach {
            assertTrue("${it.key}", it.value is PlayerValueSpec.Action)
        }
    }

    @Test
    fun `every control lives in exactly one card`() {
        // A control declared twice would render twice, and one declared in no card would be
        // reachable only by search.
        val controls = PlayerEditorModel.specs.map { it.control }
        assertEquals(
                "Each control should be declared once, except the generated metadata blocks",
                controls.filterNot { it == PlayerControl.METADATA_GROUPS }.toSet().size,
                controls.filterNot { it == PlayerControl.METADATA_GROUPS }.size)
        assertEquals(
                "Every control should have a spec",
                PlayerControl.entries.toSet(),
                controls.toSet())
    }

    /**
     * The point of grouping by subject: everything a person needs to tune the ring is in one card,
     * and what a switch reveals sits directly beneath the switches that reveal it.
     */
    @Test
    fun `the ring's rows share a card and follow the switches that reveal them`() {
        val progress = PlayerEditorModel.specsFor(PlayerSlot.PROGRESS).map { it.control }

        PlayerEditorModel.RING_DEPENDENTS.forEach {
            assertTrue("$it should be in the Progress card", it in progress)
        }
        val lastSwitch = maxOf(
                progress.indexOf(PlayerControl.EDGE_PROGRESS),
                progress.indexOf(PlayerControl.EDGE_SEEK))
        val firstDependent = PlayerEditorModel.RING_DEPENDENTS.minOf { progress.indexOf(it) }
        val lastDependent = PlayerEditorModel.RING_DEPENDENTS.maxOf { progress.indexOf(it) }
        assertTrue("The ring's switches should come first", lastSwitch < firstDependent)
        // Nothing unrelated between them: Track time used to sit in the middle of the ring's rows.
        assertEquals(
                "The ring's dependent rows should be contiguous",
                PlayerEditorModel.RING_DEPENDENTS.size - 1,
                lastDependent - firstDependent)
        assertTrue(
                "Track time belongs after the ring, not inside it",
                progress.indexOf(PlayerControl.TRACK_TIME_MODE) > lastDependent)
    }

    @Test
    fun `the ring's rows are revealed by exactly the state each one needs`() {
        fun revealed(control: PlayerControl, arc: Boolean, seek: Boolean, solid: Boolean) =
                PlayerEditorModel.isRevealed(control, RingState(arc, seek, solid))

        // Neither switch on: the ring is never on screen, so none of it has anything to act on.
        PlayerEditorModel.RING_DEPENDENTS.forEach {
            assertFalse("$it with the ring unreachable",
                    revealed(it, arc = false, seek = false, solid = true))
        }

        // The resting arc alone reaches everything.
        PlayerEditorModel.RING_DEPENDENTS.forEach {
            assertTrue("$it with the arc on and a solid ring",
                    revealed(it, arc = true, seek = false, solid = true))
        }

        // Edge seek alone reveals the ring on a drag, so its style and layout still apply - but
        // the position mark marks the *resting* ring, which is not there.
        assertTrue(revealed(PlayerControl.RING_STYLE, arc = false, seek = true, solid = true))
        assertTrue(revealed(PlayerControl.RING_LAYOUT, arc = false, seek = true, solid = true))
        assertTrue(revealed(PlayerControl.RING_GRADIENT, arc = false, seek = true, solid = true))
        assertFalse(revealed(PlayerControl.SEEK_MARKER, arc = false, seek = true, solid = true))

        // Only the solid ring blends the companion colours.
        assertFalse(revealed(PlayerControl.RING_GRADIENT, arc = true, seek = true, solid = false))
        assertTrue(revealed(PlayerControl.RING_STYLE, arc = true, seek = true, solid = false))
    }

    @Test
    fun `no control outside the ring is gated on it`() {
        val ring = RingState(edgeArcOn = false, edgeSeekOn = false, solid = false)
        PlayerControl.entries.filterNot { it in PlayerEditorModel.RING_DEPENDENTS }.forEach {
            assertTrue("$it should not depend on the ring", PlayerEditorModel.isRevealed(it, ring))
        }
    }

    @Test
    fun `revealedIn drops the ring rows the ring cannot use`() {
        fun progressKeys(ring: RingState) = PlayerEditorModel
                .revealedIn(PlayerSlot.PROGRESS, "classic", ring)
                .map { it.control }

        val off = progressKeys(RingState(edgeArcOn = false, edgeSeekOn = false, solid = true))
        assertTrue(off.none { it in PlayerEditorModel.RING_DEPENDENTS })
        // The switches that reveal them, and what is not about the ring, are never gated.
        assertTrue(PlayerControl.EDGE_PROGRESS in off)
        assertTrue(PlayerControl.EDGE_SEEK in off)
        assertTrue(PlayerControl.TRACK_TIME_MODE in off)

        val on = progressKeys(RingState(edgeArcOn = true, edgeSeekOn = true, solid = true))
        assertTrue(on.containsAll(PlayerEditorModel.RING_DEPENDENTS))
    }

    @Test
    fun `only the faces that own a layout control have a layout card`() {
        val owners = setOf("carousel", "note", "chat", "metadata", "split")
        ThemeAppearance.ALLOWED_BASE_FACES.forEach { face ->
            assertEquals(
                    "Layout card for $face",
                    face in owners,
                    PlayerEditorModel.visibleIn(PlayerSlot.LAYOUT, face).isNotEmpty())
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
        // Several settings apply everywhere, so no face can land on the page with an empty
        // surface. The face selector is the one identity row every face keeps; Control style
        // joins it only on the faces in CONTROL_STYLE_FACES - see that set's own doc for why.
        ThemeAppearance.ALLOWED_BASE_FACES.forEach { face ->
            assertTrue(
                    "Settings for $face",
                    PlayerEditorModel.GROUPS.sumOf {
                        PlayerEditorModel.visibleIn(it, face).size
                    } >= 3)
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

    /**
     * The tap confirmation is drawn inside the tap ripple above whatever the face painted, so the
     * Player page offers it everywhere - the same answer search and the legacy row already give.
     * It was left Classic-only here once, which is what "search finds it but it is not an option"
     * looked like.
     */
    @Test
    fun `the tap confirmation is offered on every face`() {
        ThemeAppearance.ALLOWED_BASE_FACES.forEach {
            assertTrue(it, PlayerEditorModel.appliesToFace(PlayerControl.QUADRANT_FLASH, it))
            assertTrue(it, PlayerEditorModel.visibleIn(PlayerSlot.CONTROLS, it)
                    .any { spec -> spec.control == PlayerControl.QUADRANT_FLASH })
        }
    }

    /**
     * Note and Metadata offer "Hidden" inside their Cover shape picker, so the separate chip for
     * the same switch is not drawn - and a search for that switch has to land on the picker.
     */
    @Test
    fun `a cover switch folded into its shape picker is reached through the picker`() {
        PlayerEditorModel.COVER_VISIBILITY_BY_SHAPE.forEach { (shapeKey, visibilityKey) ->
            val shape = PlayerEditorModel.specFor(shapeKey)
            val visibility = PlayerEditorModel.specFor(visibilityKey)
            assertNotNull(shapeKey, shape)
            assertNotNull(visibilityKey, visibility)
            assertEquals(PlayerSlot.LAYOUT, shape!!.slot)
            assertTrue(visibility!!.value is PlayerValueSpec.Toggle)
            ThemeAppearance.ALLOWED_BASE_FACES.forEach { face ->
                assertEquals("$shapeKey and $visibilityKey must belong to the same face ($face)",
                        PlayerEditorModel.appliesToFace(shape.control, face),
                        PlayerEditorModel.appliesToFace(visibility.control, face))
                assertFalse("$visibilityKey is drawn twice on $face",
                        PlayerEditorModel.GROUPS
                                .flatMap { PlayerEditorModel.visibleIn(it, face) }
                                .any { it.key == visibilityKey })
            }
            assertEquals(shapeKey, PlayerEditorModel.editorKeyFor(visibilityKey))
        }
        assertEquals(setOf("wear_note_cover_shape", "wear_metadata_cover_shape"),
                PlayerEditorModel.COVER_VISIBILITY_BY_SHAPE.keys)
        // Every other key answers for itself - Chat keeps its own row.
        assertEquals("wear_chat_show_cover", PlayerEditorModel.editorKeyFor("wear_chat_show_cover"))
        assertTrue(PlayerEditorModel.visibleIn(PlayerSlot.LAYOUT, "chat")
                .any { it.key == "wear_chat_show_cover" })
    }

    /**
     * Faces that do not offer *Show player controls*, each with the reason. Every registered face
     * is either offered the switch or named here, so a new face fails this test until somebody
     * decides - the alternative is a switch that does nothing, or a working one nobody can reach.
     */
    private val noPlayerControlsSwitch = mapOf(
            "expressive" to "keeps its central transport whatever the switch says",
            "material" to "keeps its central transport whatever the switch says",
            "immersive" to "draws no playback control",
            "depth" to "draws no playback control",
            "carousel" to "draws no playback control",
            "split" to "draws no playback control",
            "note" to "draws no playback control",
            "verse" to "draws no playback control",
            "metadata" to "draws no playback control")

    @Test
    fun `the player controls switch is offered exactly where it hides something`() {
        ThemeAppearance.ALLOWED_BASE_FACES.forEach { face ->
            val offered = PlayerEditorModel.appliesToFace(PlayerControl.PLAYER_CONTROLS, face)
            val reason = noPlayerControlsSwitch[face]
            assertTrue(
                    "$face is neither offered Show player controls nor listed with a reason",
                    offered || reason != null)
            assertFalse("$face is offered the switch but listed as having nothing to hide",
                    offered && reason != null)
        }
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
                PlayerEditorModel.PLAYER_CONTROLS_FACES +
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
        assertEquals(
                PlayerValueSpec.Toggle(true),
                PlayerEditorModel.specFor(MiscPreferences.WEAR_PROGRESS_GRADIENT.key)?.value)
    }

    /**
     * The resting ring is edited where it is drawn.
     *
     * Its style, layout and gradient used to sit on the Panels page's Seek tab, which holds the
     * seek overlay - a different surface. Touching any of the three previewed the player, so three
     * of that tab's five controls jumped to another screen, which reads as a broken preview. They
     * are here now, beside the switch that turns their ring on.
     */
    @Test
    fun `the resting ring is edited on the player page`() {
        assertEquals(
                PlayerValueSpec.Choice("solid"),
                PlayerEditorModel.specFor(MiscPreferences.WEAR_PROGRESS_STYLE.key)?.value)
        assertEquals(
                PlayerValueSpec.Choice("edge"),
                PlayerEditorModel.specFor(MiscPreferences.WEAR_PROGRESS_LAYOUT.key)?.value)

        // The ring applies to every face, so none of the three may be face-gated - they are gated
        // on the ring's own switches instead, which renderPlayerEditor reads and appliesToFace
        // deliberately cannot. Same distinction the position mark already documents.
        for (control in listOf(PlayerControl.RING_STYLE, PlayerControl.RING_LAYOUT,
                PlayerControl.RING_GRADIENT)) {
            for (face in ThemeAppearance.ALLOWED_BASE_FACES) {
                assertTrue(
                        "$control must be offered on $face",
                        PlayerEditorModel.appliesToFace(control, face))
            }
        }
    }

    /**
     * Pickers whose value is the whole answer, so their row needs no sentence under it. Each is
     * declared `summary="%s"`, and none has a written description to show.
     */
    private val namedByTheirValue = mapOf(
            "wear_progress_style" to "Dashed, Comet, Needle: the value names what it draws",
            "wear_progress_layout" to "Bezel edge, Open at bottom: the value names the geometry",
            "wear_carousel_card_shape" to "a shape is its own name",
            "wear_note_cover_shape" to "a shape is its own name",
            "wear_chat_cover_shape" to "a shape is its own name",
            "wear_metadata_cover_shape" to "a shape is its own name")

    /**
     * A picker declared `summary="%s"` reports only its current value, which its row already shows
     * on the right - so without a description of its own the row says what it is set to and never
     * what it does. Four of them had a written, translated description that no screen displayed,
     * because the value took the summary's place. Every such picker now either declares one or is
     * named above with the reason it needs none, so the next picker added fails here instead of
     * shipping as a title and a value.
     */
    @Test
    fun `a picker that only reports its value has a description or a reason not to`() {
        val cardPickers = PlayerEditorModel.GROUPS
                .flatMap { PlayerEditorModel.specsFor(it) }
                .filter { it.value is PlayerValueSpec.Choice }
                .map { it.key }
                .toSet()
        val valueOnly = valueOnlyPickersInXml().intersect(cardPickers)

        val undescribed = valueOnly.filter {
            PlayerEditorModel.specFor(it)?.descriptionRes == null && it !in namedByTheirValue
        }
        assertTrue(
                "These pickers show only their value on the Player page, so a person sees what " +
                        "they are set to and never what they do. Give each a descriptionRes, or " +
                        "name it in namedByTheirValue with the reason it needs none: $undescribed",
                undescribed.isEmpty())

        // The exemptions cannot go stale in either direction.
        namedByTheirValue.keys.forEach {
            assertTrue("$it is no longer a value-only picker; drop it from the list", it in valueOnly)
            assertNull(
                    "$it declares a description, so it needs no exemption",
                    PlayerEditorModel.specFor(it)?.descriptionRes)
        }
    }

    private fun watchFaceSettingsXml(): File =
            File("src/main/res/xml/watch_face_settings.xml").takeIf { it.exists() }
                    ?: File("mobile/src/main/res/xml/watch_face_settings.xml")

    /** Keys of the ListPreferences whose summary is the `%s` template, i.e. only the value. */
    private fun valueOnlyPickersInXml(): Set<String> =
            Regex("""<[\w.]*ListPreference\b(.*?)/>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(watchFaceSettingsXml().readText())
                    .mapNotNull { match ->
                        val attributes = match.groupValues[1]
                        val key = Regex("""android:key="([^"]+)"""").find(attributes)
                                ?.groupValues?.get(1) ?: return@mapNotNull null
                        val summary = Regex("""android:summary="([^"]*)"""").find(attributes)
                                ?.groupValues?.get(1)
                        key.takeIf { summary == "%s" }
                    }
                    .toSet()

    /** The rows inside the five categories the Player page owns, read straight from the XML. */
    private fun playerRowsInXml(): Set<String> {
        val text = watchFaceSettingsXml().readText()
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
