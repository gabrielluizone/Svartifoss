package com.svartifoss.snfell.res

import com.svartifoss.snfell.common.ThemeAppearance
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The Watch tab's miniature has to show what the wrist will actually draw, and nothing enforced
 * that.
 *
 * `WatchPreviewView` re-implements every face in Canvas because `mobile` cannot depend on `wear`,
 * so the two sides are ~7,800 and ~7,600 lines of parallel drawing code kept in step entirely by
 * hand. Every failure that has come out of that gap is quiet in the same way: the preference moves,
 * the miniature does not, and nothing throws - so the only signal a user gets is that the setting
 * "does not work", from the one screen built to tell them what it does.
 *
 * These checks are deliberately structural rather than pixel-based. There is no Robolectric or
 * instrumented setup here (see *Build & test commands*), and a screenshot test would pin the
 * drawing rather than the wiring - while the wiring is what keeps breaking: a face added to the
 * registry and not to the dispatch, a preference read on the watch and never read here, a drawing
 * path written and never called, a constant copied across the module boundary and then changed on
 * one side only.
 *
 * They read source and resources directly, the same way [TranslatedArrayAlignmentTest] and
 * [AppearancePreferenceScopingTest] do, because the invariant spans two Gradle modules and no
 * single function owns it.
 */
class WatchPreviewParityTest {

    private companion object {

        /**
         * Watch-face settings with genuinely nothing to draw in a still miniature.
         *
         * Each of these earns its place by being about *behaviour* rather than appearance. Adding
         * to this set is the moment to stop and check that the setting really changes nothing on
         * screen - which is exactly the mistake the rest of this test exists to catch.
         */
        val NOT_PREVIEWABLE = mapOf(
                "wear_keep_screen_on" to
                        "Holds the display against the inactivity timeout; draws nothing.",
                "wear_gestures_mode" to
                        "Gates whether a gesture executes. No gesture is performed in a preview.",
                "queue_remote_artwork" to
                        "A network toggle for queue covers. The miniature uses sample or already " +
                                "loaded artwork either way, so it can honestly show neither state.",
                "wear_quadrant_tap_flash" to
                        "Touch feedback shown while a quadrant is being pressed. A still " +
                                "miniature has no press to render."
        )

        /**
         * Real gaps: settings the watch honours and the preview silently ignores.
         *
         * This set is meant to shrink to nothing. It is separate from [NOT_PREVIEWABLE] on purpose
         * - one is a decision, this is a debt - so that "the preview ignores this" can never be
         * quietly reclassified as "this cannot be previewed".
         */
        val KNOWN_GAPS = emptyMap<String, String>()

        /** The four axes each Google Sans Flex family exposes, read by prefix rather than by name. */
        val FLEX_AXIS_SUFFIXES = listOf("_width", "_optical_size", "_grade", "_roundness")

        /** Rows that are buttons, links or explanatory text rather than stored settings. */
        val NON_PREFERENCE_ROWS = setOf(
                "reset_appearance",
                "watch_streaming_shortcuts",
                "quick_panel_open_note",
                "screen_buttons_hint",
                "wear_flex_axes_hint",
                "typography_editor_surface",
                "color_editor_surface",
                "panel_editor_surface",
                "player_editor_surface"
        )

    }

    // ------------------------------------------------------------------ faces

    /**
     * A face registered in [ThemeAppearance.ALLOWED_BASE_FACES] but missing from the preview's
     * dispatch falls through to `else` and is previewed as Classic - so the Watch tab shows the
     * wrong face entirely while every other part of the app behaves correctly.
     */
    @Test
    fun everyRegisteredFaceHasAPlayerMiniature() {
        val dispatch = whenBranchLiterals("drawPlayerSurface", "demonstratedFace")
        assertTrue("Could not read the preview's face dispatch", dispatch.size > 10)

        // Classic is the else branch: it is the fallback the others are distinguished from.
        val missing = ThemeAppearance.ALLOWED_BASE_FACES
                .filterNot { it == ThemeAppearance.DEFAULT_FACE }
                .filterNot { it in dispatch }

        if (missing.isNotEmpty()) {
            fail("Faces with no miniature - the Watch tab previews these as Classic:\n  " +
                    missing.sorted().joinToString("\n  ") +
                    "\nAdd a branch to WatchPreviewView.drawPlayerSurface.")
        }
    }

    /**
     * The always-on styles have their own dispatch, and it is a separate list from the faces: a
     * face can exist without an AOD style of its own, but a style offered in the picker that the
     * preview does not know is drawn as the Classic AOD.
     */
    @Test
    fun everyOfferedAodStyleHasAMiniature() {
        val dispatch = whenBranchLiterals("drawAodSurface", "style")
        assertTrue("Could not read the preview's AOD dispatch", dispatch.size > 5)

        val offered = stringArray("wear_aod_style_values")
        assertTrue("wear_aod_style_values should not be empty", offered.isNotEmpty())

        val missing = offered
                // "follow" is resolved to a concrete style before the dispatch, and "classic" is
                // the else branch.
                .filterNot { it == "follow" || it == "classic" }
                .filterNot { it in dispatch }

        if (missing.isNotEmpty()) {
            fail("Always-on styles with no miniature - previewed as the Classic AOD:\n  " +
                    missing.sorted().joinToString("\n  ") +
                    "\nAdd them to the when in WatchPreviewView.drawAodSurface.")
        }
    }

    // ------------------------------------------------------- preference reach

    /**
     * Every visual setting on the Watch face screen must actually be read by the preview.
     *
     * This is the check that would have caught the source-icon controls reaching one face out of
     * eighteen: the key was read, drawn once, and looked wired up. Reading is the floor, not the
     * ceiling - a key read and then ignored still passes here - but a key never read cannot
     * possibly change the miniature, and that is the failure users report.
     */
    @Test
    fun everyVisualWatchFaceSettingIsReadByThePreview() {
        // Deliberately the snapshot rather than the whole file. A key name also appears in
        // surfaceForPreference's routing table, so a file-wide search passes for a key that is
        // routed to a surface and then never actually read, which is precisely the bug shape.
        //
        // "The snapshot" includes the private helpers it calls: a read that needs a migration or a
        // fallback gets lifted into one (readColorTreatment), and that is a better-factored read,
        // not a missing one. One level of expansion is enough - a helper that needs its own helper
        // to reach a preference has stopped being a read and become a subsystem.
        val snapshot = snapshotAndItsHelpers()
        val preview = previewSource
        val byConstant = preferenceConstants()

        val unread = declaredKeys()
                .filterNot { it.startsWith("cat_") }
                .filterNot { it in NON_PREFERENCE_ROWS }
                .filterNot { it in NOT_PREVIEWABLE }
                .filterNot { it in KNOWN_GAPS }
                .filterNot { key -> snapshot.contains("\"$key\"") }
                // Read through its MiscPreferences definition rather than as a literal.
                .filterNot { key ->
                    byConstant[key]?.let { snapshot.contains("MiscPreferences.$it") } == true
                }
                // The six Flex axis families are read by prefix (readFlexAxes("wear_title_font_flex")
                // builds "..._width" and friends), so the prefix is what has to be present.
                .filterNot { key -> FLEX_AXIS_SUFFIXES.any { suffix ->
                    key.endsWith(suffix) &&
                            snapshot.contains("\"${key.removeSuffix(suffix)}\"")
                } }
                // The Metadata face's six content blocks are the one family read outside the
                // snapshot - previewMetadataRows resolves them from the shared registry as it
                // builds the table, so they are never named individually anywhere.
                .filterNot { key ->
                    key.startsWith("wear_metadata_show_") &&
                            preview.contains("metadataGroupPreference")
                }

        if (unread.isNotEmpty()) {
            fail("Watch face settings the preview never reads - changing these cannot change " +
                    "the miniature:\n  " + unread.sorted().joinToString("\n  ") +
                    "\nRead them in WatchPreviewView.readPreferenceSnapshot, or list them in " +
                    "NOT_PREVIEWABLE with the reason there is nothing to draw.")
        }
    }

    /** A stale exemption would hide a real gap, so both escape hatches are kept honest. */
    @Test
    fun everyExemptedSettingStillExists() {
        val keys = declaredKeys()
        (NOT_PREVIEWABLE + KNOWN_GAPS).forEach { (key, reason) ->
            assertTrue(
                    "$key is exempted from the preview check (\"$reason\") but no longer appears " +
                            "on the Watch face screen - remove it from this test",
                    key in keys)
        }
        val snapshot = functionBody(previewSource, "readPreferenceSnapshot")
        KNOWN_GAPS.keys.forEach { key ->
            assertTrue(
                    "$key is listed as a KNOWN_GAP but the preview now reads it - delete the " +
                            "entry, the debt is paid",
                    !snapshot.contains("\"$key\""))
        }
    }

    // ------------------------------------------------------------- dead paths

    /**
     * A drawing function with no caller is the shape both of the preview's worst bugs took: the
     * adaptive-title renderer and the per-face always-on renderers were all written, documented as
     * the path every face used, and then never wired to the dispatch - so the setting they
     * implemented silently did nothing while the code that would have honoured it sat right there.
     */
    @Test
    fun thePreviewHasNoUnreachableDrawingCode() {
        val source = previewSource
        val declared = Regex("""^\s*private fun (draw[A-Za-z0-9]*)\(""", RegexOption.MULTILINE)
                .findAll(source)
                .map { it.groupValues[1] }
                .toSet()
        assertTrue("Expected to find the preview's drawing functions", declared.size > 20)

        val unreachable = declared.filter { name ->
            Regex("""\b${Regex.escape(name)}\s*\(""").findAll(source).count() <= 1
        }

        if (unreachable.isNotEmpty()) {
            fail("Drawing code in WatchPreviewView that nothing calls:\n  " +
                    unreachable.sorted().joinToString("\n  ") +
                    "\nEither route the dispatch through it or delete it - a documented renderer " +
                    "with no caller reads as working and is not.")
        }
    }

    // --------------------------------------------------------------- geometry

    /**
     * Face geometry must be read from [FaceGeometry], never re-declared as a literal.
     *
     * These numbers used to exist twice - once in each Wear face and once in the preview's
     * companion - on the reasoning that `mobile` cannot depend on `wear`. True, and beside the
     * point: `common` is a dependency of both, so the copies were a choice rather than a
     * constraint. They now live in one place and the drift is impossible instead of merely
     * checked, which is why the old value-by-value comparison this test replaced is gone.
     *
     * What is still possible is someone adding a *new* copy - writing `.52f` back into a face
     * because the shared name was not obvious - so the rule that remains is the structural one:
     * a declaration named after something FaceGeometry owns must take its value from there.
     */
    @Test
    fun faceGeometryIsReadFromTheSharedRegistryAndNotRecopied() {
        val owned = Regex("""^\s*(?:const )?val ([A-Z][A-Z_0-9]*)\s*(?::[^=]+)?=""",
                RegexOption.MULTILINE)
                .findAll(repoFile(
                        "common/src/main/java/com/svartifoss/snfell/common/FaceGeometry.kt")
                        .readText())
                .map { it.groupValues[1] }
                .toSet()
        assertTrue("Expected FaceGeometry to declare constants", owned.size > 15)

        val sources = wearFaceSources() + listOf(repoFile(
                "mobile/src/main/java/com/svartifoss/snfell/view/watchface/WatchPreviewView.kt"))
        val recopied = mutableListOf<String>()
        sources.forEach { file ->
            val text = file.readText()
            Regex("""^\s*(?:private )?(?:const )?val ([A-Z][A-Z_0-9]*)\s*(?::[^=]+)?=\s*([^\n]+)""",
                    RegexOption.MULTILINE)
                    .findAll(text)
                    .forEach { match ->
                        val (name, value) = match.destructured
                        // Compared on the bare name so the preview's CAROUSEL_-prefixed aliases and
                        // a face's own short name are both caught.
                        val bare = name.removePrefix("CAROUSEL_").removePrefix("CHAT_")
                                .removePrefix("CLASSIC_").removePrefix("IMMERSIVE_")
                                .removePrefix("SPLIT_").removePrefix("NOTE_")
                                .removePrefix("VERSE_").removePrefix("METADATA_")
                        if ((name in owned || bare in owned) && !value.contains("FaceGeometry")) {
                            recopied += "${file.name}: $name = ${value.trim()}"
                        }
                    }
        }

        if (recopied.isNotEmpty()) {
            fail("Face geometry re-declared instead of read from FaceGeometry:\n  " +
                    recopied.joinToString("\n  ") +
                    "\nThese are shared through `common`; a second copy is a drift waiting to " +
                    "happen and is what the registry exists to prevent.")
        }
    }

    /**
     * Chat is intentionally dense: a one- or two-dp drift in its timestamp, day chip or action
     * row pushes the whole bottom-anchored thread into a visibly different composition. Merely
     * checking that both renderers mention the face is not enough; these are the layout metrics
     * that must keep one shared owner.
     */
    @Test
    fun chatFaceAndPreviewReadTheSameLayoutContract() {
        val face = repoFile(
                "wear/src/main/java/com/svartifoss/snfell/watch/view/face/ChatFace.kt").readText()
        val preview = previewSource
        val metrics = listOf(
                "SIDE_PADDING_FRACTION",
                "TOP_PADDING_FRACTION",
                "BOTTOM_PADDING_FRACTION",
                "DAY_CHIP_TEXT_SP",
                "DAY_CHIP_HORIZONTAL_PADDING_DP",
                "DAY_CHIP_VERTICAL_PADDING_DP",
                "DAY_TO_MESSAGE_GAP_DP",
                "CURRENT_BUBBLE_TITLE_SP",
                "CURRENT_BUBBLE_ARTIST_SP",
                "CURRENT_BUBBLE_MAX_WIDTH_DP",
                "CURRENT_BUBBLE_HORIZONTAL_PADDING_DP",
                "CURRENT_BUBBLE_VERTICAL_PADDING_DP",
                "CURRENT_TO_VOICE_GAP_DP",
                "BUBBLE_CORNER_DP",
                "BUBBLE_TAIL_CORNER_DP",
                "VOICE_BUBBLE_HEIGHT_DP",
                "VOICE_BUBBLE_HORIZONTAL_PADDING_DP",
                "AVATAR_SIZE_DP",
                "AVATAR_TO_WAVE_GAP_DP",
                "WAVE_TO_GLYPH_GAP_DP",
                "WAVE_HEIGHT_DP",
                "WAVE_PLAYHEAD_PULSE_MIN_SCALE",
                "WAVE_PLAYHEAD_PULSE_HALF_CYCLE_MS",
                "PLAY_GLYPH_SIZE_DP",
                "PLAY_GLYPH_MARK_DP",
                "TIME_TOP_PADDING_DP",
                "TIME_END_PADDING_DP",
                "TIME_TO_TICKS_GAP_DP",
                "TIME_TEXT_SP",
                "TICK_WIDTH_DP",
                "TICK_HEIGHT_DP",
                "VOICE_TO_ACTION_GAP_DP",
                "ACTION_DIAMETER_FRACTION",
                "ACTION_GAP_FRACTION",
                "ACTION_MIN_DIAMETER_DP",
                "ACTION_MIN_DESIGNED_DIAMETER_DP",
                "ACTION_MAX_DESIGNED_DIAMETER_DP",
                "ACTION_GLYPH_FRACTION"
        )

        val missingOnWatch = metrics.filterNot { metric ->
            face.contains("FaceGeometry.Chat.$metric")
        }
        val missingOnPreview = metrics.filterNot { metric ->
            preview.contains("FaceGeometry.Chat.$metric")
        }

        if (missingOnWatch.isNotEmpty() || missingOnPreview.isNotEmpty()) {
            fail(
                    "Chat layout values must be read from FaceGeometry on both sides." +
                            "\nMissing on Watch: ${missingOnWatch.joinToString()}" +
                            "\nMissing on Preview: ${missingOnPreview.joinToString()}"
            )
        }
    }

    /** The Classic player is still View-based on the watch, but its text scale and the miniature's
     * central chord are part of the same visual contract. */
    @Test
    fun classicTypographyUsesTheSharedContract() {
        val watch = repoFile(
                "wear/src/main/java/com/svartifoss/snfell/watch/view/MainActivity.kt").readText()
        val preview = previewSource
        val chrome = repoFile(
                "wear/src/main/java/com/svartifoss/snfell/watch/view/compose/WatchScreenChrome.kt")
                .readText()
        val watchMetrics = listOf(
                "TITLE_MAX_SP",
                "TITLE_MIN_SP",
                "ARTIST_MAX_SP",
                "ARTIST_MIN_SP",
                "CLOCK_SP",
                "TRACK_TIME_SP",
                "SOURCE_ICON_SIZE_ARTIST_FACTOR",
                "SOURCE_ICON_END_MARGIN_ARTIST_FACTOR"
        )
        val previewMetrics = watchMetrics +
                listOf("ROUND_BOX_INSET_FRACTION", "SQUARE_TEXT_MARGIN_DP")

        val missingOnWatch = watchMetrics.filterNot { watch.contains("FaceGeometry.Classic.$it") }
        val missingOnPreview = previewMetrics.filterNot {
            preview.contains("FaceGeometry.Classic.$it")
        }
        val missingOnChrome = listOf("CLOCK_SP", "CLOCK_TOP_PADDING_DP").filterNot {
            chrome.contains("FaceGeometry.Classic.$it")
        }
        if (missingOnWatch.isNotEmpty() || missingOnPreview.isNotEmpty() || missingOnChrome.isNotEmpty()) {
            fail(
                    "Classic metrics must have one owner in FaceGeometry." +
                            "\nMissing on Watch: ${missingOnWatch.joinToString()}" +
                            "\nMissing on Preview: ${missingOnPreview.joinToString()}" +
                            "\nMissing on Compose clock: ${missingOnChrome.joinToString()}"
            )
        }
    }

    /**
     * Classic is View-based on the watch, so its available width and its artist overflow rule are
     * not inferred from the title. The preview needs to retain both parts of that layout contract:
     * BoxInsetLayout's round-screen chord and the artist's own smart two-line sizing.
     */
    @Test
    fun classicArtistAndInsetFollowTheViewLayoutContract() {
        val preview = previewSource
        val geometry = repoFile(
                "common/src/main/java/com/svartifoss/snfell/common/FaceGeometry.kt").readText()
        val layout = repoFile("wear/src/main/res/layout/activity_main.xml").readText()
        val squareDimensions = repoFile("wear/src/main/res/values/dimens.xml").readText()
        val roundDimensions = repoFile("wear/src/main/res/values-round/dimens.xml").readText()
        val artistView = layout.substringAfter("android:id=\"@+id/text_artist\"")
                .substringBefore("</com.svartifoss.snfell.watch.view.OutlineTextView>")

        assertTrue("Classic artist must use its own smart overflow plan",
                preview.contains("planClassicArtist("))
        assertTrue("Classic artist's two-line cap must be shared",
                preview.contains("FaceGeometry.Classic.ARTIST_MAX_LINES"))
        assertTrue("Classic preview source glyph must follow the visible artist line",
                preview.contains("val hasClassicSourceGlyph = artistVisible &&"))
        assertTrue("The shared contract must preserve the View cap",
                geometry.contains("const val ARTIST_MAX_LINES = 2"))
        assertTrue("Watch artist View must remain two lines", artistView.contains("android:maxLines=\"2\""))
        assertTrue("Classic watch content is inset to the round-screen chord",
                layout.contains("app:boxedEdges=\"all\""))
        assertTrue("Square Classic must retain its XML margin",
                squareDimensions.contains("music_screen_text_margin\">30dp"))
        assertTrue("Round Classic must remove that explicit margin",
                roundDimensions.contains("music_screen_text_margin\">0dp"))
        assertTrue("Preview must use the shared round inset", preview.contains(
                "FaceGeometry.Classic.ROUND_BOX_INSET_FRACTION"))
        assertTrue("Preview must use the shared square margin", preview.contains(
                "FaceGeometry.Classic.SQUARE_TEXT_MARGIN_DP"))
    }

    /** The Chat column keeps its fixed spacer even when CurrentMessageBubble returns no content;
     * its timestamp also belongs between the voice bubble and the action row. */
    @Test
    fun chatKeepsItsStructuralGapsForEveryMetadataAndTimeState() {
        val face = repoFile(
                "wear/src/main/java/com/svartifoss/snfell/watch/view/face/ChatFace.kt").readText()
        val preview = previewSource

        assertTrue("Watch Chat keeps the current-to-voice spacer outside its optional bubble",
                face.contains("Spacer(Modifier.height(FaceGeometry.Chat.CURRENT_TO_VOICE_GAP_DP.dp))"))
        assertTrue("Watch Chat conditionally places the timestamp below the voice bubble",
                face.contains("if (state.showTrackTime)"))
        assertTrue("Preview must preserve that spacer when no current bubble is drawn",
                preview.contains("var currentTop = bubbleTop - dp(CHAT_CURRENT_TO_VOICE_GAP_DP)"))
        assertTrue("Preview must reserve the optional timestamp before the action row",
                preview.contains("val chatTimestampHeight"))
        assertTrue("Preview voice bubble must account for the timestamp height", preview.contains(
                "dp(CHAT_VOICE_TO_ACTION_GAP_DP) - chatTimestampHeight"))
    }

    /** Chat's waveform is a progress surface, and its compact timestamp is a right-aligned row
     * before the delivered ticks. Both details are easy to lose in the Canvas renderer because
     * Compose provides the Row alignment and animation frame scheduling automatically. */
    @Test
    fun chatWaveformAndTimestampUseTheWatchStateContract() {
        val face = repoFile(
                "wear/src/main/java/com/svartifoss/snfell/watch/view/face/ChatFace.kt").readText()
        val preview = functionBody(previewSource, "drawChatPlayer")

        assertTrue("Watch waveform must receive the resolved progress colour",
                face.contains("played = Color(state.progressColor)"))
        assertTrue("Watch pulse timing must be read from the shared contract", face.contains(
                "FaceGeometry.Chat.WAVE_PLAYHEAD_PULSE_HALF_CYCLE_MS"))
        assertTrue("Preview waveform must use the resolved progress colour", preview.contains(
                "val chatProgressColor = resolveTint(progressMode, progressCustom, progressDesaturated)"))
        assertTrue("Preview waveform must pulse its playhead bar", preview.contains(
                "index == played) waveformPulse"))
        assertTrue("Preview Chat time must end before the delivered ticks", preview.contains(
                "textPaint.textAlign = Paint.Align.RIGHT"))
    }

    /** Immersive is sparse enough that a few points or one font size visibly change the whole
     * composition. Its text block is therefore a shared contract rather than two tuned copies. */
    @Test
    fun immersiveFaceAndPreviewReadTheSameLayoutContract() {
        val watch = repoFile(
                "wear/src/main/java/com/svartifoss/snfell/watch/view/face/CuratedPlayerFaces.kt")
                .readText()
        val preview = previewSource
        val metrics = listOf(
                "SIDE_PADDING_FRACTION",
                "BOTTOM_PADDING_FRACTION",
                "TITLE_SP",
                "TITLE_LINE_HEIGHT_SP",
                "ARTIST_TOP_PADDING_DP",
                "ARTIST_SP",
                "ARTIST_LINE_HEIGHT_SP",
                "SOURCE_ICON_SIZE_DP",
                "TRACK_TIME_TOP_PADDING_DP",
                "TRACK_TIME_SP",
                "TRACK_TIME_LINE_HEIGHT_SP"
        )
        val missingOnWatch = metrics.filterNot { watch.contains("FaceGeometry.Immersive.$it") }
        val missingOnPreview = metrics.filterNot { preview.contains("FaceGeometry.Immersive.$it") }
        if (missingOnWatch.isNotEmpty() || missingOnPreview.isNotEmpty()) {
            fail(
                    "Immersive layout values must be read from FaceGeometry on both sides." +
                            "\nMissing on Watch: ${missingOnWatch.joinToString()}" +
                            "\nMissing on Preview: ${missingOnPreview.joinToString()}"
            )
        }
    }

    /** FaceClock is placed by a layout-top padding in Compose but Canvas needs a baseline. Keep
     * that translation centralized so player faces cannot reintroduce their own guessed offset. */
    @Test
    fun playerClockUsesTheSharedTopAnchorAndCanvasBaselineConversion() {
        val preview = previewSource
        val chrome = repoFile(
                "wear/src/main/java/com/svartifoss/snfell/watch/view/compose/WatchScreenChrome.kt")
                .readText()
        val classicLayout = repoFile("wear/src/main/res/layout/activity_main.xml").readText()

        assertTrue(
                "Compose FaceClock must read the shared top padding",
                chrome.contains("FaceGeometry.Classic.CLOCK_TOP_PADDING_DP"))
        assertTrue(
                "Preview FaceClock must turn the shared top padding into a Canvas baseline",
                preview.contains("dp(CLASSIC_CLOCK_TOP_PADDING_DP) - textPaint.fontMetrics.ascent"))
        assertTrue(
                "Player clock call sites must use the baseline-aware helper",
                !preview.contains("drawSmallClock(canvas, cx, cy - radius"))
        assertTrue(
                "Classic XML must retain the same 5dp top padding",
                classicLayout.contains("android:paddingTop=\"5dp\""))
    }

    /** SourceIconGlyph is ContentScale.Fit on the watch. The preview must not crop a rectangular
     * notification icon while scaling it into the same square slot. */
    @Test
    fun previewSourceGlyphUsesFitLikeTheWatch() {
        val preview = previewSource
        val chrome = repoFile(
                "wear/src/main/java/com/svartifoss/snfell/watch/view/face/FaceChrome.kt").readText()
        assertTrue("Watch source glyph must use ContentScale.Fit", chrome.contains("ContentScale.Fit"))
        assertTrue(
                "Preview source glyph must use the smaller bitmap scale, equivalent to Fit",
                preview.contains("val scale = min(diameter / glyph.width, diameter / glyph.height)"))
        assertTrue(
                "Artist text opacity must not be folded into the sibling source-icon tint",
                functionBody(preview, "drawArtistLine").contains("diameter, color)"))
    }

    // ----------------------------------------------------------------- source

    private val previewSource: String by lazy {
        repoFile("mobile/src/main/java/com/svartifoss/snfell/view/watchface/WatchPreviewView.kt")
                .readText()
    }

    private fun wearFaceSources(): List<File> =
            repoFile("wear/src/main/java/com/svartifoss/snfell/watch/view/face")
                    .listFiles { file -> file.extension == "kt" }
                    .orEmpty()
                    .sortedBy { it.name }

    /** Unit tests run from either the module directory or the repository root depending on how
     *  Gradle was invoked, so both are tried - the same approach the sibling resource tests use. */
    private fun repoFile(relative: String): File =
            listOf(File("../$relative"), File(relative))
                    .firstOrNull { it.exists() }
                    ?: throw AssertionError("Could not locate $relative from ${File(".").absolutePath}")

    private fun declaredKeys(): Set<String> =
            Regex("""android:key="([^"]+)"""")
                    .findAll(repoFile("mobile/src/main/res/xml/watch_face_settings.xml").readText())
                    .map { it.groupValues[1] }
                    .toSet()

    /** key to the MiscPreferences constant that declares it. */
    private fun preferenceConstants(): Map<String, String> =
            Regex("""val\s+([A-Z_0-9]+)\s*:\s*PreferenceDefinition<[^>]*>\s*=\s*\n?\s*\w*PreferenceDefinition\("([a-z_0-9]+)"""")
                    .findAll(repoFile(
                            "common/src/main/java/com/svartifoss/snfell/common/MiscPreferences.kt")
                            .readText())
                    .associate { it.groupValues[2] to it.groupValues[1] }

    /** Picker option lists are spread across several files under `values/`, so the array is looked
     *  up by name rather than assumed to live in `arrays.xml`. */
    private fun stringArray(name: String): List<String> {
        val block = repoFile("mobile/src/main/res/values")
                .listFiles { file -> file.extension == "xml" }
                .orEmpty()
                .firstNotNullOfOrNull { file ->
                    Regex("""<string-array name="$name".*?</string-array>""",
                            RegexOption.DOT_MATCHES_ALL).find(file.readText())?.value
                }
                ?: throw AssertionError("$name not found in any mobile values/*.xml")
        return Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(block)
                .map { it.groupValues[1].trim() }
                .toList()
    }

    /**
     * The quoted branch labels of the `when (subject)` inside [function].
     *
     * Scoped to the function body rather than to the whole file, because the preview has several
     * `when`s over a variable called `style` and only the one inside the always-on renderer is the
     * dispatch this is asking about.
     */
    private fun whenBranchLiterals(function: String, subject: String): Set<String> {
        val body = functionBody(previewSource, function)
        val start = body.indexOf("when ($subject) {")
        if (start < 0) return emptySet()
        val block = balancedBlock(body, body.indexOf('{', start))
        return Regex(""""([a-z_0-9]+)"""").findAll(block).map { it.groupValues[1] }.toSet()
    }

    /** `readPreferenceSnapshot`'s body plus the bodies of the private helpers it calls. */
    private fun snapshotAndItsHelpers(): String {
        val body = functionBody(previewSource, "readPreferenceSnapshot")
        val declared = Regex("""^\s*private fun (\w+)\(""", RegexOption.MULTILINE)
                .findAll(previewSource)
                .map { it.groupValues[1] }
                .toSet()
        val called = declared.filter { name ->
            name != "readPreferenceSnapshot" &&
                    Regex("""\b${Regex.escape(name)}\s*\(""").containsMatchIn(body)
        }
        return body + called.joinToString("\n") { functionBody(previewSource, it) }
    }

    private fun functionBody(source: String, function: String): String {
        val signature = Regex("""fun $function\(""").find(source)
                ?: throw AssertionError("$function not found")
        val open = source.indexOf('{', signature.range.last)
        return balancedBlock(source, open)
    }

    /** The text between [open] and its matching brace. Comments and strings in this file do not
     *  contain unbalanced braces, so a plain depth count is enough and keeps this readable. */
    private fun balancedBlock(source: String, open: Int): String {
        var depth = 0
        var index = open
        while (index < source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, index + 1)
                }
            }
            index++
        }
        throw AssertionError("Unbalanced braces from offset $open")
    }
}
