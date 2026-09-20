# Player layout checks

> **Reverted on 2026-09-19.** The layout this checklist validates was taken back out before it shipped, and the player is back to its 4.0 layout - see the status note at the top of `docs/player-chrome-layout-plan.md`. Two checks still apply, because those features were kept: item 5 of the first *Device validation* list (the Repeat once icon) and item 9, *The tap confirmation*. Everything else describes a layout the app no longer has.

## Revision after the audit — 2026-09-16

The earlier claim of complete layout correctness was not supported by the tests. The audit
found concrete regressions, and this revision changes the following paths:

| Audited case | Implementation change |
| --- | --- |
| Expressive/Material: track time overlaps mini buttons | Shared `PlayerControlGeometry.transportLayout` allocates separate metadata, transport and time bands above the actual lower controls. Transport geometry adapts to the available space. |
| Note/Verse: bottom hint covers the footer | Footer padding follows the reserved lower band. Artist's bottom text and Studio's bottom transport also clear the hint. |
| Ribbon/Carousel: the title below the covers reaches the bottom hint | Shared `coverRailLayout` allocates title space above the lower controls and reduces the artwork first. The title is measured and fitted into that band. |
| Chat: bottom hint covers hosted mini buttons | The face-owned row reserves the hint's height and gap; it no longer relies on the shared Android row's placement. |
| Small screens: mini buttons shrink to 4–9dp | Scale has a 0.78 floor (a 38dp control remains at least 29.6dp). Crowded centered faces flatten steep curves; count-aware widths also apply to Compose rows. |
| Artist/source glyph at 200% consumes the title's height | `FittedFaceContent` measures the whole title/artist block before fitting it. The title is no longer a weighted child receiving the artist row's leftover height. |
| Large clock overlaps its neighboring top hint | Both View and Compose clocks fit into a separate horizontal band when the hint is visible. |
| Expressive idle screen is empty | The face now renders its idle state instead of returning while the host also hides the shared idle group. |
| Hidden glyph reappears during tap feedback | Hidden controls no longer flash; active icon animations are cancelled when styling is reapplied. Chat also applies the shared icon scale/alpha. |

The phone preview uses the same transport allocation and lower reservations, including stopped
mini buttons. Repeat presets retain their parameter-specific transmitted icon assets.

### What automated validation establishes

- Geometry tests cover 160, 192, 224, 225 and 240dp screens, both centered transport faces,
  four lower boundaries, time on/off and each metadata placement. They assert separation of the
  allocated rectangles; they do not assert pixel appearance.
- Mini-button tests enforce the scale floor, including crowded curved rows. Cover-rail tests
  check that Ribbon/Carousel artwork, titles and the lower controls occupy separate bands.
- Existing tests cover settings availability, repeat drawable selection, XML hierarchy and
  rendering-source contracts. They do not exercise real watch touch dispatch.
- The affected common/mobile/wear debug unit-test suites passed. The complete command is
  `JAVA_HOME=/home/gabrielskaftell/jdks/jdk-21.0.11+10 ./gradlew test assembleGithubDebug -Pkapt.incremental.apt=false --continue`.
  Final result: **BUILD SUCCESSFUL**, 449 tasks, 59 seconds. Common ran 544 tests per variant,
  mobile 441 and wear 179, with zero failures or errors. The GitHub debug phone and watch APKs
  were generated, and their DEX files contain the new layout helpers.

### Remaining limits

**Visual validation is still required before calling this correct for all combinations.** No
watch or emulator was available. Custom fonts, system font scaling, long localized strings,
300% element sizes, moved metadata, side rails and actual taps still need rendered checks.
Fitting a crowded composition can reduce text and transport size; passing the allocation tests
alone does not establish that every extreme configuration is comfortable to read or tap.
Ambient rendering has separate geometry and needs its own comparison. The checklist below is
a release validation gate, not a record of completed device tests.

## Device validation

Use matching phone/watch builds. Device validation is still needed; no watch or emulator was
connected during implementation.

1. On Expressive and Material, use a long track title with the artist enabled. Try Automatic and
   wrapping text on a small round display and a larger display. The text must stop above the
   playback ring with a visible gap. Repeat with larger system fonts.
2. Assign top and bottom quadrant actions in both playing and stopped configurations. Check
   Classic, Expressive, Material, and another Compose face. Both hints should be visible above
   artwork. **Superseded:** the top hint is no longer shrunk and moved aside for the clock - the
   clock curves to the bezel and the hint keeps the apex. See item 1 of the 2026-09-16 section.
3. Enable one, two, then three mini buttons. Test flat, curved, and side-rail arrangements.
   The bottom hint must remain visible and tappable. Check that titles, time labels, and the
   central ring remain readable on the smallest display; also check custom typography settings.
4. Turn off **Show player controls** in the phone's Watch tab. Taps and gestures should still work,
   and the phone preview should match the watch. Ambient transport uses its separate AOD setting.
   **Superseded:** hiding no longer fades the glyph to invisible - the whole transport leaves the
   layout and the text takes the space back. See item 2 of the 2026-09-16 section.
5. Assign **Set repeat → Repeat once** to a quadrant, mini button, and action-menu entry. Its
   numbered repeat icon should match the phone preview in playing, paused, and idle states.
   Also check **Repeat once** (the toggle action), repeat-all, and a custom icon override.
6. Enter and leave ambient mode, open and close quick actions, and switch faces. Edge hints must
   stay below overlays and return to their configured appearance afterward.

Capture phone-preview and watch screenshots for the same settings before release.

---

## Device validation for the chrome rework — 2026-09-16

The six steps recorded in `docs/player-chrome-layout-plan.md` were validated **as arithmetic only**:
disjoint bands, a symmetric cross, hint corners inside the circle, the yielding order. No watch or
emulator was available at any point, so nothing below has been seen drawn. The checklist above still
applies; this is the part specific to the rework, ordered by how likely it is to be wrong and how
expensive it would be to discover late.

Build and serve: `./gradlew assembleGithubDebug` then `python3 serve_apk.py` (port 8760), and
download `mobile-debug.apk` / `wear-debug.apk` from the phone and watch browsers.

### First — the three that have never rendered

1. **The curved clock.** Turn *Always show time* on and assign any action to the **top** quadrant,
   on a round watch. Expected: the time curves along the top bezel and the quadrant icon sits at 12
   o'clock just inside it, both centred on the vertical axis. `CurvedClockView` draws with
   `Canvas.drawTextOnPath`, which has never executed here - check the glyphs are upright and read
   left to right rather than mirrored or inverted, that the text is not clipped by the bezel, and
   that it is legible at the largest clock size on the Typography page. With the top quadrant
   unassigned the clock must be the straight one it has always been, unchanged.
2. **Hiding the controls on Expressive and Material.** Watch tab → turn *Show player controls* off.
   Expected: the cookie/disc, its ring and both side buttons are gone, and the title and artist
   **recentre** into the space rather than staying up where they were. Tapping the middle must still
   toggle playback, a double tap must open the quick actions and a long press the face picker. Turn
   it back on and check the composition returns exactly as it was.
3. **Artist and Details (Metadata).** Configure two or three mini buttons. Artist's name/track block
   should now sit above the row instead of under it; Details should show fewer rows rather than rows
   hidden behind the buttons. Both are changes nobody asked for, made because the shared rule now
   reaches them - if either reads worse than before, the cheap answer is one line:
   `PlayerChromeLayout.declinesLowerContentBand` for Artist, and for Details the row budget in
   `MetadataFace`.

### Then — the reported defects, to confirm they are actually gone

4. **The cross.** Assign all four quadrants, on several faces including at least one Compose face
   and Classic. All four icons visible, same size, same distance from the glass. On Expressive and
   Material the left and right ones are deliberately absent - their prev/next buttons carry those
   actions already.
5. **Mini buttons and the bottom icon.** One, then two, then three buttons, across flat, curved and
   the side rails. The bottom icon stays visible and the row rests above it. The one-button case is
   the one that used to collide.
6. **Text and the playback circle.** A long title with the artist on, on Expressive and Material,
   on the smallest round display available. A visible gap above the ring, at Automatic and at
   wrapping text modes, and again with a larger system font. Then crowd it: clock on, top and bottom
   quadrants assigned, three mini buttons, track time on - the elapsed time should disappear before
   anything overlaps, and the control should stay large enough to hit.

### And the ordinary regressions

7. Enter and leave ambient on each face: the straight clock returns for AOD, the curved one is gone,
   and the hints are hidden. Open and close the quick panel and the volume overlay; the hints stay
   below them and come back afterwards.
8. Switch through all fifteen faces with the same settings, then compare each against the phone's
   Watch tab preview. The preview and the wrist are supposed to agree about every item above; where
   they do not, the preview is the one that lies.

### The tap confirmation (added after the chrome rework)

9. Turn **Flash icon on tap** on, and turn the corner icons off (*Show player controls*). Assign a
   different action to each of the four corners and tap each one. The action's own icon should
   appear at your fingertip inside the ripple, on its own dark disc, and fade. Check it on Classic
   and on at least two Compose faces, and confirm nothing appears when the corner icons are on but
   the setting is off. Double-tap and long-press a corner: the ripple fires, **no icon** does -
   those run different actions and the glyph shown is the single-tap one. Swipe from inside a
   corner: no icon, because no corner action ran. Finally check ambient: no icon at all.
