# Player chrome layout — study and plan

Status: **proposal**, written 2026-09-16 in response to user feedback about the now-playing screen.
Supersedes the uncommitted `docs/player-layout-validation.md`, which documents an earlier attempt at
the same defects (see *The earlier attempt* below).

The subject is the chrome that surrounds a now-playing face — the clock, the four quadrant hints,
the mini-button row, the Up Next pill — and how the fifteen shipping faces share the screen with it.
It is deliberately not about any one face's composition.

---

## 1. What was reported

1. **Text overlap** — track titles sit too close to the playback circle.
2. **Hidden buttons** — the top and bottom quadrant actions work but their icons are not visible.
3. **Mini buttons** — turning the mini buttons on hides the bottom quadrant icon instead of showing both.
4. **Inconsistent icons** — "Repeat once" assigned on the *Music playing* tab draws the plain repeat
   glyph, while the same thing assigned on the *No playback* tab draws the glyph with a numeral.
5. **Hiding the playback icon does nothing**, with a request for a custom transparent PNG instead.

Items 1–3 and 5 are one subject. Item 4 is unrelated and is handled separately in §7.

---

## 2. The fifteen faces

`ThemeAppearance.ALLOWED_BASE_FACES` minus `ArchivedFaces.KEYS` is exactly fifteen:

| Face | Renderer | Top band | Centre | Bottom band |
| --- | --- | --- | --- | --- |
| classic | View | clock, title/artist/time block | bezel seek ring, centre tap zone | free |
| expressive | Compose | metadata @ .17 | prev / cookie / next, contour ring | track time under the ring |
| poster | Compose | metadata | full-bleed artwork | play glyph @ −.24, progress line, time |
| studio | Compose | metadata | artwork | progress orb + play glyph on the floor |
| material | Compose | metadata @ .17 | prev / disc / next, ring | track time under the disc |
| immersive | Compose | free | artwork | **grounded** title/artist/time block |
| carousel | Compose | artist row | cover rail | title band under the rail |
| chat | Compose | thread | thread | voice bubble + **hosted mini buttons** |
| split | Compose | cover | seam + badge | opaque album panel (own backdrop) |
| note | Compose | free | cover disc + sentence | track time |
| verse | Compose | running head | lyric reel | elapsed time |
| metadata | Compose | identity header | measured table | table continues |
| artist | Compose | free | performer picture | **grounded** name/track/time block |
| ribbon | Compose | free | cover rails | title band under the rails |
| frame | Compose | card top | card artwork | card bottom |

Nine of the fifteen put something in the bottom band; five put something in the top band; three
(expressive, material, and classic's seek ring) own the centre with a control the text must clear.

---

## 3. Root causes

### 3.1 The quadrant hints are not drawn on fourteen of fifteen faces

`applyScreenThemeNow` opened with

```kotlin
if (screenFace in composeFaces) { icons.forEach { it.visibility = View.GONE }; …; return }
```

so **all four** hints are hidden on every Compose face — not only the top and bottom ones the report
names. The icons also lived inside `FourWayTouchLayout`, which is ordered *below* the Compose host in
`activity_main.xml`, so even made visible they would have been painted over by any face with an
opaque backdrop.

Expressive and Material are the partial exception: `leftActionIcon`/`rightActionIcon` replace the
glyphs on their own prev/next buttons, and those buttons run the LEFT/RIGHT quadrant actions
(`onSkipPreviousTap` → `executeAction(ScreenQuadrant.LEFT)`). So on those two faces the left and
right quadrants *are* represented — as transport buttons. On the other twelve Compose faces they are
represented by nothing at all.

### 3.2 The clock evicts the top hint

On Classic the top hint was hidden outright whenever *Always show time* was on:

```kotlin
val hideTop = icon === binding.iconTop && alwaysShowTime
```

Both want the apex: `music_screen_icon_offset` is 4dp and `FaceGeometry.Classic.CLOCK_TOP_PADDING_DP`
is 5dp. The collision was resolved by deleting one of the two.

### 3.3 The bottom band has two owners and no arbiter

The mini row rests on the bezel chord: `autoRowBottomMarginForWidthPx` returns
`r − √(r² − (w/2)²) + 6dp`. That margin is a function of the row's *width*, so a narrow row sits much
lower — on a 192dp screen a single 52dp button rests 9.6dp from the glass, which is inside the bottom
hint's 4…28dp band. The row only dodged the hint when `screenFace !in composeFaces`, i.e. on Classic,
where the hint was the only thing drawn.

### 3.4 Text and the transport circle were never allocated separate space

Expressive's metadata column was top-anchored at `.17` with
`heightIn(max = screen * 0.33f - ringBottom)` and `clipToBounds()`. On a 192dp screen that is
32.6dp…61dp, and the ring's top edge is at 61dp — **a designed gap of zero**, with clipping as the
only protection. That is defect 1 exactly.

### 3.5 Hiding controls was disabled on the two faces that most needed it

```kotlin
val keepsEssentialTransport = screenFace == "expressive" || screenFace == "material"
showControls = playerControlsVisible || keepsEssentialTransport
```

and the phone hid the switch for those faces. Even where `showControls` was honoured it only set
`alpha = 0f`: the cookie kept its box, Expressive's contour ring is drawn unconditionally
("the Expressive ring is structural"), and Material's disc outline likewise. So the band was never
reclaimed and something round stayed on screen. That is defect 5 exactly.

### 3.6 Nine formulas for one question

Faces that do reserve lower space each invented their own arithmetic:

| Site | Expression |
| --- | --- |
| Expressive / curated Up Next pill | `maxOf(screen * .07f, bottomHintInsetDp.dp)` |
| Note | `maxOf(screen * .07f, screen * (1f - miniButtonsTopFraction) + 6.dp)` |
| Verse | `maxOf(screen * .065f, screen * (1f - miniButtonsTopFraction) + 6.dp)` |
| Chat | `maxOf(screen * BOTTOM_PADDING_FRACTION, bottomHintInsetDp.dp)` |
| Artist | `maxOf(screen * EDGE_PADDING_FRACTION, bottomHintInsetDp.dp)` |
| Studio | `maxOf(screen * .04f, bottomHintInsetDp.dp)` |
| Expressive / Material / Carousel / Ribbon | `minOf(screen * miniButtonsTopFraction, screen - bottomHintInsetDp)` |
| curated `TrackFooter` | `screen * miniButtonsTopFraction.coerceIn(.20f,.95f) - screen * .50f - 10.dp` |
| `blockBottomClearanceFraction` | `maxOf(EDGE_MARGIN_FRACTION, 1f - miniButtonsTopFraction)` |

`miniButtonsTopFraction` (a fraction from the top) and `bottomHintInsetDp` (dp from the bottom) are
two encodings of the same fact, and `MainActivity` writes the first from three different places —
once folding the second into it. A face combining them double-counts in some states and not others.
This is the structural reason the screen looks improvised, and it is what any fix has to remove
rather than extend.

---

## 4. The earlier attempt (uncommitted, in the working tree)

It is green — `:common:`, `:wear:` and `:mobile:` debug unit tests all pass — and parts of it are the
right idea:

* moving `icon_top`/`icon_bottom` above the Compose host in `activity_main.xml`;
* dropping the `keepsEssentialTransport` carve-out;
* making the mini row dodge the bottom hint on every face;
* allocating disjoint metadata / transport / time bands instead of assuming them;
* the repeat-icon work in §7.

What makes it the wrong base to build on:

1. **`common/.../PlayerControlGeometry.kt` is a second geometry registry** beside `FaceGeometry`,
   built around `when (face) { "expressive" …; "material" …; else -> null }`. Thirteen of the fifteen
   faces fall into `else`, so `miniRowScale` returns `1f` for them and `transportLayout` is never
   consulted. It is two faces' private arithmetic placed in `common` as if it were shared.
2. **It adds a third lower-band channel** (`bottomHintInsetDp`) beside the two that already existed,
   and the faces pick between the three inconsistently — Artist reads one, Note and Verse read
   another, Expressive reads both. §3.6 got worse, not better.
3. **Chrome shrinks to make room.** The top hint drops to 16dp and is shoved sideways by
   `topHintOffset` when the clock is on; the bottom hint drops to 16dp when mini buttons exist; the
   Classic clock is scaled down by `fitClassicClockBesideHint` to 24% of the screen width. This is
   precisely "torto e de um canto só".
4. **It bypasses the shared text helpers.** Expressive and Material lost `blockLineInsets`,
   `blockPlacement`, `blockSafeVerticalInset` and `blockDesignedTopPadding` in favour of a private
   `FittedFaceContent` band, so the round-screen per-line inset work documented in CLAUDE.md no
   longer applies to them.
5. **Left and right are still unrepresented** on twelve faces, and `CONTROL_STYLE_FACES` was widened
   to every face to justify it while `wear_quadrant_tap_flash` stayed Classic-only.

**Decision: evolve it in place, no revert.** Nothing in the tree is thrown away — the four unrelated
workstreams (repeat icons, pinch calibration, the Play `standalone` manifest fix, the Special Elite
keyword narrowing) stay untouched, and the layout work stays as the starting point. The five
problems above are then closed as refactors of those same files rather than as a rewrite:

| Problem in the attempt | How it closes |
| --- | --- |
| `PlayerControlGeometry` is a private two-face registry in `common` | Generalised into `PlayerChromeLayout` + per-face entries in `FaceGeometry`; `transportLayout` stays, keyed by real per-face numbers instead of `when (face)` with an `else -> null` |
| Three lower-band channels | `bottomHintInsetDp` and `miniButtonsTopFraction` collapse into the one `FaceSafeArea` field; the nine expressions in §3.6 become one helper call |
| Chrome shrinks (16dp hints, 24%-wide clock, `topHintOffset`) | Deleted, replaced by R1–R3: fixed cross, curved clock, concentric bottom arcs |
| Expressive/Material bypass the shared text helpers | `blockLineInsets` / `blockPlacement` / `blockSafeVerticalInset` restored around the allocated bands |
| Left and right still unrepresented | R8 |

The order in §9 is written so the tree stays green and shippable between steps.

---

## 5. The design

### R1 — The bezel cross

Every drawn hint sits on the bezel ring at its cardinal angle — top 12, bottom 6, left 9, right 3 —
at one shared edge inset and one shared size. No hint is ever resized or displaced to dodge
something; the four positions are fixed so the set always reads as a cross on a round screen. What
yields is never the cross.

### R2 — The apex belongs to the hint; the clock takes the arc

`CurvedClock` already exists in `WatchScreenChrome.kt` and is used by the queue and menu screens;
the player faces deliberately use the straight `FaceClock`. When a top hint is drawn *and* the clock
is on, the player clock switches to the curved variant on the bezel arc and the hint keeps the apex
just inside it. Both remain centred on 12 o'clock, so the pair is symmetric about the vertical axis,
and neither is shrunk. With no top hint configured the straight clock is unchanged, so no existing
install's appearance moves.

### R3 — The bottom is two concentric arcs

The bottom hint hugs the bezel at 6 o'clock. The mini row — or the Up Next pill — rests on the arc
immediately above it with one shared gap. The chord margin the row already computes stays; what
changes is that the reserved band is derived once, for every face, instead of only where a Classic
text block happened to be measured.

### R4 — One safe area, one API

A new pure `common/.../PlayerChromeLayout.kt` takes the discrete facts the host knows —
screen size, roundness, which hints are drawn, hint size from the screen-theme tokens, clock state,
the mini row's measured extent and placement, pill state — and returns two things:

* `ChromePlacement` for each visible chrome item, consumed identically by wear `MainActivity` (to
  place real Views) and by `WatchPreviewView` (to draw the miniature);
* `FaceSafeArea(topDp, bottomDp, sideDp)` — the band the face must keep clear.

`NowPlayingFaceState` carries the safe area as **one** field. `bottomHintInsetDp` and
`miniButtonsTopFraction` are removed, and every one of the nine expressions in §3.6 is replaced by a
single `FaceChrome` helper. `PlayerControlGeometry` is folded into `FaceGeometry`, which is where
per-face numbers already live.

### R5 — A face may decline, once, in a registry

Immersive's own source records the opposite policy on purpose: its grounded block *must* stay on the
floor, and lifting it "made the text float mid-screen and broke the composition the face exists
for". That stays true. Declining the lower band becomes a named entry in one registry beside
`SELF_COMPOSED_FACES`, not a missing call, and a source-sweep test fails for any face that neither
reads the safe area nor is listed.

### R6 — Space is negotiated by priority, never by shrinking chrome

When the bands do not fit — a 192dp screen with a clock, a top hint, two metadata lines, Expressive's
transport, a track time, a mini row and a bottom hint genuinely cannot hold all of it — the order is
fixed and shared:

1. chrome keeps its designed size;
2. the face's optional elements yield in a documented order (track time, then the artist row, then
   the transport diameter down to its minimum);
3. the title's existing shrink → wrap → scroll cascade absorbs what is left.

One order, expressed once, instead of a per-face clamp at each draw site.

### R7 — Hiding the controls reclaims the band

`showControls = false` removes the transport from the layout rather than setting `alpha = 0f`:
Expressive's cookie *and its contour ring*, Material's disc and ring, and every curated glyph.
The freed band is returned to the safe area, so the text recentres instead of holding a gap around
something that is no longer there. The switch is offered on every face.

### R8 — The left and right hints

The cross is only a cross with four arms. The two faces that already represent LEFT/RIGHT as their
own transport buttons (expressive, material) are named in one registry and skip those two arms; the
other thirteen draw them. Same mechanism as `MiniButtonPlacement.isHostedByFace`, same reasoning.

---

## 6. How this is kept true

Following the repository's existing conventions rather than inventing a new gate:

* `PlayerChromeLayout` is pure and JVM-tested — placements, safe area and the R6 priority order, at
  160 / 192 / 224 / 240dp, round and square, with each chrome combination.
* A wear source-sweep test (the shape of `AmbientFaceContractTest` and `ArtistLineTypographyTest`)
  asserts that no face computes a lower-band inset itself and that every face either reads the safe
  area or is named in the R5 registry.
* `WatchPreviewParityTest` gains a check that the miniature resolves chrome through
  `PlayerChromeLayout` rather than its own literals — the preview is the one surface that must not
  lie about this.
* A structural test asserts the four hint placements are symmetric: same size, same edge inset, one
  per cardinal angle.

None of this establishes that the result *looks* right. The earlier attempt's validation checklist
in `docs/player-layout-validation.md` remains the honest statement of what only a device can settle,
and should be kept and re-run against this work.

---

## 7. The repeat icon (unrelated to the layout)

Two independent causes, both real:

1. **`SetRepeatModeAction.defaultIcon` was `action_repeat` for every mode**, including
   `REPEAT_MODE_ONE`, while `RepeatOneAction` uses `action_repeat_one` — the glyph with the numeral.
2. **Two different actions carry the identical user-facing title "Repeat one"**:
   `R.string.action_repeat_one` on the toggle and `R.string.action_set_repeat_one` on the preset.
   Assigning the toggle on one tab and the preset on the other is assigning two different actions
   that read as the same one, which is what the report describes.

Cause 1 is fixed in the working tree (parameterised `defaultIcon`, plus transmitting the asset,
since the watch resolves a local vector per action *key* and cannot know the parameter). Two
refinements are worth making: express the transmit rule as "skip the asset only when the action's
`defaultIcon` is provably the resource the watch would pick locally", so the next parameterised
action is covered without another special case; and keep skipping it for `OFF`/`ALL`, where the
generic vector is already correct.

Cause 2 needs the preset's title to be distinct from the toggle's, which is a new string in all
forty-five locales (`TranslatedArrayAlignmentTest`'s neighbourhood — a plain string, not a picker
array, so index alignment does not apply).

---

## 8. Decisions taken

| Question | Decision |
| --- | --- |
| Base | Evolve the working tree in place. No revert of the layout work, no loss of the four unrelated workstreams. |
| Clock vs top hint | R2 — automatic. The existing `CurvedClock` takes the arc whenever a top hint is drawn beside it; no new preference, no vocabulary change, no appearance change for anyone without a top quadrant action. |
| Left/right hints | R8 — the full cross on thirteen faces, with expressive and material named in one registry because their transport buttons already are that affordance. |
| Hiding the controls | R7 — the band is returned to the content and the text recentres. |

---

## 9. Sequencing

Each step leaves the tree building and green, so the work can stop at any boundary.

1. **`PlayerChromeLayout` in `common`, pure and JVM-tested** — hint placements, the clock style, the
   lower content's resting line and `SafeArea`, plus the two registries (R5, R8). Consumed by
   nobody, so it changes no behaviour. ✅ *Done — 30 tests in `PlayerChromeLayoutTest`.*

   Scope note: the face-internal transport allocation (`PlayerControlGeometry.transportLayout`,
   `coverRailLayout`) deliberately stayed where it is. It is a different question — how a face
   divides the space it *has* — and it composes with this cleanly, since its `lowerTop` argument
   becomes `safeArea.bottomDp`. It gains real per-face entries and moves in step 3, when its
   callers migrate; moving it now would have meant editing every caller in a step that is supposed
   to edit none.
2. **Host adoption** — `MainActivity` publishes one `SafeArea`, places the four hints from
   `HintPlacement`, and switches the clock to the arc under R2. ✅ *Done.*

   What landed: all four hints moved into one chrome group above the Compose host in
   `activity_main.xml` (the left and right ones were still inside `FourWayTouchLayout`, i.e. under
   every opaque face backdrop, which is why twelve faces showed nothing for a side quadrant); one
   `applyPlayerChrome` places all four by centring them and moving each to its cardinal point;
   `CurvedClockView` renders R2's arc as host chrome, so one implementation serves all fifteen
   faces rather than one per face kind; `bottomHintInsetDp` and `topHintVisible` are gone from
   `NowPlayingFaceState`, replaced by `safeArea`, which is the first of the three lower-band
   channels to close. Deleted: the 16dp hints, `topHintOffset`, `fitClassicClockBesideHint`, the
   `FaceClock` `reserveHint` band, and the second owner of the clock/top-hint visibility that lived
   in the preference block. `ScreenButtonsParentChainTest` gained the z-order sweep for all four
   hints and a check that none of them carries a gravity or margin of its own.

2b. **Preview parity for the chrome** — pulled forward out of step 6 rather than left to disagree
   with the watch across steps 3–5. ✅ *Done.*

   `WatchPreviewView` resolves the hints and the clock through `PlayerChromeLayout` in one
   `drawPlayerChrome` pass that runs after every face, which is both what the watch does and a fix
   in its own right: Classic and Matejdro drew their hints from inside their own composition, i.e.
   *under* their own text. `drawCurvedClock` mirrors `CurvedClockView` from the identical three
   shared functions, and the straight `drawFaceClock` stands down when the arc is the resolved
   style, exactly as the Compose `FaceClock` does. One deliberate divergence, documented at the
   site: with no quadrant configured at all the miniature shows four sample hints, the way its
   mini-button row already shows sample buttons — otherwise a fresh install would see no icons and
   the Control style picker, which restyles exactly these, would look broken.

   `PlayerControlGeometry.topHintOffset` and its test are deleted with the design they implemented.
   `WatchPreviewParityTest` gained the sweep that requires both renderers to place chrome through
   the resolver and requires `topHintOffset`, `fitClassicClockBesideHint` and `reserveHint` to stay
   gone — all three bought room by shrinking a piece of chrome.
3. **Face adoption** — every expression in §3.6 replaced by `state.safeArea`. ✅ *Done.*

   All three channels are now closed: `miniButtonsTopFraction` is gone from the face state, from
   `MainActivity` (which wrote it from three places) and from the six faces that each combined it
   with the other two in their own way. `AWAKE_PILL_TOP_FRACTION`, `RAIL_TOP_FRACTION` and
   `autoRowBottomMarginForWidthPx` went with it — the last is `bezelInsetDp` + `CHROME_GAP_DP`,
   algebraically identical, and folding it in is what lets the row's resting line account for the
   bottom hint by arithmetic instead of by reading a laid-out View's position.

   Two things the migration forced, both worth keeping:

   * **The Up Next pill is lower content, not a special case.** It is modelled as a `LowerContent`
     with a `designedMarginDp`, because it is nearly screen-wide with fully rounded ends and the
     chord would drop it a third of the way up the face. Its own numbers moved to
     `FaceGeometry.UpNextPill`, since three renderers place it.
   * **`SafeArea.lowerContentMarginDp`** — the one member that is not a keep-out. A face that
     *draws* the band's content needs the resting line; reading `bottomDp` there would have the pill
     push itself up by its own height.

   `FaceSafeAreaContractTest` (wear) is the guard: the three retired field names may not reappear in
   any face or in the host, and declining the band stays a listed decision rather than a missing
   call.

   **Open, and deliberately not changed here:** the faces that never reserved anything — Metadata in
   particular, whose table is budgeted by row count — still do not read the safe area. They are not
   in §3.6 because they had no expression to replace; each needs a decision (honour or decline),
   which is what step 6's sweep is for. Artist, which previously cleared only the hint, now lifts
   above the row as well; if that reads wrong on a device the answer is one line in the R5 registry.
4. **R7** — controls leave the layout, and the band comes back. ✅ *Done.*

   `transportLayout` gained `showTransport`: with it false the diameter is zero, the metadata band
   grows to `METADATA_MAX_WITHOUT_TRANSPORT` and the whole group centres in what is now free -
   `follow` cannot mean "stay above nothing". Expressive and Material no longer compose their
   transport at all when hidden (it was `alpha = 0`, which is what left the hole), and each keeps a
   `CenterGestureRegion` sized from `HIDDEN_TRANSPORT_REGION_FRACTION`, because the gesture is not
   the glyph. Both rings go with their controls: they are the silhouette of the button, and a lone
   circle around nothing is not a progress indicator anybody asked for - the bezel ring
   (`wear_edge_progress_visible`) remains for position, on every face.

   The preview mirrors it: `transportLayout` is told the same thing, and both transport blocks are
   skipped rather than scaled to nothing (Expressive's canvas scale is `diameter / preferred`, which
   would have been zero). `WatchPreviewParityTest` pins all of it.
5. **R6** — one yielding order, written once. ✅ *Done.*

   `PlayerControlGeometry.allocateTransportBands` is the order: the chrome never yields (that is
   settled before it is reached), the track time goes first, the metadata gives up its designed
   share rather than let the control fall below `TRANSPORT_MIN_DIAMETER`, the control takes what is
   left down to that floor, and below all of it the text wins the remainder. The old allocation had
   **no floor at all** — the metadata took 45% and the transport took whatever remained, so a
   crowded band produced a ring a few dp across that was only tappable because the whole centre of
   the face happens to be.

   Consequences worth keeping straight:

   * **`timeHeight == 0` now means "dropped"**, and both renderers read it instead of deciding for
     themselves. Expressive also stopped reserving the band unconditionally — it asked for it even
     with the readout switched off, and now asks when the readout or a central-seek scrub will use
     it.
   * **`TrackFooter` yields instead of clamping.** Pushed past its floor the time was sitting on the
     face's own focus, which is worse than not being drawn.
   * **`minimumContentBottom` replaces three copies** of `screen * .17f + 78f` (host, preview,
     `miniRowScale`). They quoted the *preferred* transport size, so the mini-button row was being
     scaled back to protect a control that had room to shrink first.

   The allocation is deliberately **not monotonic across the time decision**: when the band grows
   enough to hold the readout again, the readout takes its 18dp back and the control returns to its
   floor. `theAllocationIsMonotonicWithinOneTimeDecision` says so explicitly, because the first
   version of that test asserted the stronger property and caught the design rather than a bug.
6. **Every face decides, and the sweep makes it say so.** ✅ *Done.*

   The chrome half landed early as 2b; this closed the rest. `FaceSafeAreaContractTest` now requires
   every face file to read `state.safeArea` or to be named with a reason, which surfaced the three
   faces that had simply never been asked:

   * **Metadata** was the real defect. It budgeted its table from the whole screen height, on the
     one composition whose premise is fitting as many rows as the screen holds - so the last rows
     drew under the mini-button row. It now budgets against what is free, on both renderers.
   * **Split** joins Immersive in `declinesLowerContentBand`. Its panel runs from the seam to the
     glass with the text top-anchored inside it, so there is nowhere for that text to retreat to:
     lifting it pushes it into the seam, which is the face. Both are already in
     `SELF_COMPOSED_FACES`, which turns the row off by default for exactly this reason.
   * **Frame** is exempt on the weaker ground that there is nothing text-bearing down there at all -
     its card ends at .795 and only artwork lies beneath. The test keeps the two kinds of exemption
     apart, and checks that anything claiming to *decline* is actually in the registry.

   **Artist** stays as step 3 left it - lifting above the row rather than only above the hint. It is
   a visible change to a face nobody complained about, and the cheap reversal is one line in the
   registry if a device says otherwise.
7. **§7 refinements** — the transmit rule and the duplicate "Repeat one" title.

---

## 10. The transparent PNG request

The second half of feedback 5 offers a custom image as an alternative to hiding the glyph. R7 gives
them what they asked for first. A user-supplied play/pause glyph is a separate feature of real size —
it is a file this phone has and the watch does not, so it travels as an asset the way
`CommPaths.DATA_USER_FONT` does, with the same import-time validation, staleness republish and
`DeviceLocalAppearance` consequences for community themes. It is not a layout change and should not
ride along with one.
