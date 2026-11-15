# Watch tab preview honesty audit (2026-11-14)

Question asked before 4.4: the Watch tab's preview is meant to show what a setting does on the watch, and the Mini buttons page showed nothing at all - which other settings are like that?

## Method

`mobile/src/debug/java/com/svartifoss/snfell/debug/PreviewSweepReceiver.kt` (debug builds only, behind `DUMP`) renders the Watch tab's preview for every value of every setting in `watch_face_settings.xml`, including the options appended at runtime (`PanelOptionCatalog`, `AppearanceOptionCatalog`, the derived pickers), through `WatchPreviewView.showPreference` - the call a row, a dialog and a picker grid all make. Each value is drawn at 288 px and hashed; a setting whose values all produce the same picture is "dead" in that context, and for pickers the number of distinct pictures is recorded.

```
adb shell am broadcast -n com.svartifoss.snfell/.debug.PreviewSweepReceiver --es mode faces    # 22 faces, shipped defaults
adb shell am broadcast -n com.svartifoss.snfell/.debug.PreviewSweepReceiver --es mode active   # the active theme
    [--es faces verse,note] [--es keys album_art_style] [--es with key=value,...] [--es dump key] [--ei cap 12]
adb exec-out run-as com.svartifoss.snfell cat files/preview_sweep/faces.tsv
```

`with` adds fixed companion values to every render (used to set the text-effect colours to white, so a black outline on a black face is not mistaken for a missing one); `dump` writes each value's picture as a PNG. Run on a Pixel 3 with the user's own button configuration. Profile mode always shows the bundled sample cover and the playing state.

A dead setting is only a candidate. Each one was then checked against the page's own visibility rules (is the row offered in that context?) and against the watch's source (does the wrist draw it?). Three outcomes: hidden correctly (fine), offered but dead on the watch too (the page should not offer it), or alive on the watch but not in the preview (the preview is wrong).

`sweep-after-faces.tsv` and `sweep-after-text-effects-white.tsv` are the results after the fixes. The first sweep's file was lost with the session scratchpad; its matrix is summarised below.

## Fixed

Offered, but changing nothing on the watch either:

- **Mini buttons, row set to Never** (the default on Split, Verse, Ribbon, Frame and Matejdro): arrangement, shape, background and opacity were offered and every picture in their grids was identical. The Appearance card now appears only when the row is on (`MiniButtonEditorModel.isRevealed`); search redirects to "Show mini buttons".
- **Artwork filter with a background that hides the cover**: thirty identical pictures. Hidden unless the face draws a cover of its own regardless (`FaceElements.artworkFilterApplies`, `COVER_UNDER_HIDDEN_BACKGROUND_FACES` = Carousel, Chat, Note, Ribbon, Frame, Depth, pinned against the faces' source by `FaceElementsContractTest`).
- **Clock tone and contrast correction outside the album colour**, and **title tone in "face" mode** (`ColorEditorModel.appliesInMode`; the watch's `resolveClockColor` uses both only for "album").
- **Always-on transport, progress and Up Next switches** on Carousel, Chat, Split, Note, Verse, Metadata, Ribbon and Frame, whose ambient variants read none of them (only `ExpressiveFace` and `CuratedAmbientFace` do). `AodEditorModel.VISUAL_STYLES` narrowed; a test that pinned the old expectation for Ribbon and Frame was wrong and was updated.
- **Always-on artwork controls** on Chat, Split, Note, Ribbon and Frame, whose ambient variants paint an opaque black field (`ARTLESS_STYLES`).
- **Artist size and text effects on Note**, where the artist is a span of the title's sentence and takes the title's size and effects (`TypographyEditorModel.NOTE_ARTIST_FROM_TITLE`); search redirects to the title's control.

Drawn on the watch, but not in the preview:

- **Text shadow, outline and background on Classic and Matejdro** (the default face drew bare text), and artist case there; title outline, background and case on Chat and Metadata; the title's outline and background on Note's sentence; artist opacity, case, outline, background, overflow mode and alignment on Split.
- **The shared mini-button row** on Split, Verse, Metadata, Ribbon and Frame (the watch draws it over every face but Chat); and **while paused**, where the preview returned no buttons although the watch shows the stopped config's.
- **The clock** on Verse, Metadata and Artist.
- **The progress ring while its style, layout, weight or gradient is picked** on faces whose resting ring is off but edge seek is on (Chat, Split, Note, Verse, Artist, Ribbon, Frame, Matejdro): every one of the 21 styles was the same picture.
- **The play icon picker** previewed the playing state, where only the pause mark is drawn.
- **Picker pictures without the user's own mini buttons and app mark**: the off-screen renderer behind the grids never received them (`WatchLookThumbnailer.setButtonIcons` / `setSourceGlyph`).

## Not fixed (for a decision)

- **"Show Up Next pill" with the mini buttons on**: the watch draws the pill only while the row is not, so the switch does nothing while mini buttons are configured and shown. The same gate hides Poster's progress line ("Show internal progress" on Poster). Gating these needs the configured buttons, which the settings page does not read today.
- **Controls that only act on the tap confirmation** on Frame, Ribbon and Artist (control style, play and pause icons, show player controls): a still picture never shows it. Artist draws that confirmation too but is missing from `CONTROL_STYLE_FACES` and `TransportGlyphs.FACES`.
- **Time- or motion-based settings**: always-on night dimming and position shift, the seek marker's four modes (all drawn as during a drag), Expressive's seek mode, the cover fade.
- **Settings the sample content cannot demonstrate**: artist overflow mode (the sample name is short), app mark size and opacity when nothing is playing (no mark to draw), artwork source (online, artist photo and custom pictures are never fetched for the preview), accent source and artist contrast correction on the sample cover, the Metadata face's file-details group, quick-panel shortcut covers without shortcut covers.
- **Colour-treatment pickers on single-colour surfaces** (volume, quick panel, queue, lyrics, title, artist): of nine options, Follow, Expressive, Monochrome and Duotone draw the same picture, because the primary is anchored to the album and those surfaces use only the primary.
- **Archived faces**: Aurora's miniature ignores the title's typography; Matejdro's colour treatment changes nothing.

## Before the fixes (faces sweep, shipped defaults, Pixel 3)

Dead in the preview on: mini buttons mode (Split, Verse, Metadata, Ribbon, Frame); mini-button styles (those plus Chat for placement, Matejdro); ring style, layout, weight and gradient (Chat, Split, Note, Verse, Artist, Ribbon, Frame, Matejdro); clock typography and colour (Verse, Metadata, Artist); play icon (all 22); title shadow (Classic); title outline and background with white colours (Classic, Aurora, Chat, Note, Metadata, Matejdro); artist outline and background (Classic, Split, Note, Matejdro); artist case (Classic, Split, Matejdro); title case (Aurora, Chat, Metadata); artist opacity (Split); always-on transport, progress and pills (Classic, Carousel, Chat, Split, Note, Verse, Metadata, Artist, Ribbon, Frame, Matejdro); always-on artwork (Eclipse, Chat, Split, Note, Ribbon, Frame); artwork filter (Eclipse, Verse, Ribbon, Frame).
