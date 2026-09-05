# Author screenshots for community themes — design plan

Let a theme's author attach one real screenshot of their own watch — the **Player** — to a
submission, so the gallery can show what the theme actually looks like on a wrist instead of only a
locally-rendered miniature.

> **Status: shipped (2026-09-01).** All four phases are done. An author attaches a photograph, a
> moderator judges it beside the render, the publisher commits it, and the gallery shows it on the
> Player with a labelled control to switch back. Both privacy-policy files, the Data Safety draft
> and `CHANGELOG.md` are updated, and `online-themes-plan.md` §7 carries an amendment pointing
> here instead of contradicting this document. It extends [`online-themes-plan.md`](online-themes-plan.md) and **reverses that
> document's §7**; read §2 below before anything else.
>
> **Revised during implementation (2026-09-01):** the screenshot is written **after** the intake,
> not before. Reading the rules made the earlier ordering's hole plain — see §4.2.
>
> **Scope narrowed (2026-09-01):** one screenshot, of the Player surface only. An earlier draft of
> this plan allowed five (Player, Volume, Progress, Quick panel, Queue). The Player is where a
> person spends nearly all of their time, so the other four buy little and cost a great deal — see
> §4.1 for what the cut buys, §6 for what it does to the moderation queue, and §10 for how the other
> surfaces would arrive later without disturbing anything already published.

---

## 1. What is being asked

An author takes a screenshot on the watch (Wear OS companion "Take wearable screenshot", or the
watch's own screenshot gesture on One UI Watch — either way the file ends up on the phone). When
submitting a theme, they may attach it. Attaching is optional.

On the reading side, a gallery visitor sees the author's screenshot on the Player surface when one
exists, and can switch to the locally-rendered preview — which is what the detail screen shows
today, driven by their own album art — with one control. Attaching nothing leaves a theme looking
exactly as it does now.

The detail screen's other five surfaces (always-on display, Volume, Progress, Quick panel, Queue)
continue to render locally, unchanged.

Two things this is *not*: it is not the app capturing anything from the watch (see §7), and it is
not a replacement for the synthetic preview (see §6, where that preview becomes the verifier).

## 2. The decision this reverses

[`online-themes-plan.md` §7](online-themes-plan.md) rejected shipping preview images, in one
sentence: *"shipping preview images has a cost: hundreds of PNGs committed to the repo grow forever
and can never be removed from git history."* The split it chose instead — an uploaded WebP that
exists **only** for moderation and that the publisher must discard rather than commit — is
implemented and working today.

That reasoning is still correct. What changes is not the cost but what is bought with it:

- §7 was weighing images that carried **no information the app could not already draw**. A committed
  PNG of a synthetic render is pure repository weight in exchange for nothing. An author's
  screenshot carries what the miniature structurally cannot: the theme on a real display, with real
  artwork, at real size, including every place the Canvas mirror in
  [`WatchPreviewView.kt`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/WatchPreviewView.kt)
  quietly disagrees with the Compose face it mirrors — which is the exact class of drift
  `WatchPreviewParityTest` exists because nothing else catches.
- The cost is **one optional image per theme**. §8 sizes it: a few megabytes across a catalogue of
  hundreds, against ~88 MB for the five-surface version this plan started as.
- The reversal is bounded: the *cards* stay synthetic and the image is fetched only on a detail page
  someone deliberately opened, so the property §3 of the other plan protects — the whole gallery is
  one cached request — is untouched.

So: reversed deliberately, for a specific gain, with the growth bounded and disclosed. §7 of that
document should be amended to point here rather than left contradicting this one.

## 3. What already exists in its favour

**Image bytes already travel app → Firestore → moderator.** Every submission today carries a
200×200 WebP as base64 in the intake document —
[`renderModerationPreview`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/SubmitCommunityThemeActivity.kt#L447)
encodes it, `moderationPreviewWebpBase64` in
[`CommunityThemeSubmissionRepository.kt`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/CommunityThemeSubmissionRepository.kt#L239)
writes it, [`firestore.rules`](../firestore.rules#L491) bounds it at 64 KB of base64, and
[`admin.js`](admin/admin.js#L184) renders it. The encode path even handles the pre-API-30
`WEBP`/`WEBP_LOSSY` split already. What is missing is only the second half: publisher → `docs/` →
app.

**The surfaces are already a selector.**
[`CommunityThemeDetailActivity.kt`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/CommunityThemeDetailActivity.kt#L56)
already presents Player / AOD / Volume / Progress / Quick panel / Queue as a
`MaterialButtonToggleGroup` over one `WatchPreviewView`. Showing a downloaded bitmap for the Player
is a substitution inside `showSurface`, not a new screen — and it is the same substitution any
future surface would need, which is what makes §10's expansion cheap.

**The download and cache shapes exist twice.**
[`OnlineThemesRepository`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/OnlineThemesRepository.kt#L378)
has ETag-conditional fetching with an atomic disk cache under `cacheDir` — text only, so it needs a
binary sibling — and
[`ShortcutArtworkFetcher.downloadBytes`](../mobile/src/main/java/com/svartifoss/snfell/music/ShortcutArtworkFetcher.kt#L112)
already has the byte-download hardening this project standardized on (explicit `User-Agent`,
redirect handling, `Connection: close`).

**No new permission.** `ActivityResultContracts.PickVisualMedia` needs none, on every supported API
level. The app never reads the gallery; the user hands it one file.

## 4. Architecture

The governing constraint from the parent plan holds: **reading needs no account and no live
backend, writing goes through Firebase, and the client is never trusted.** The screenshot follows
the same path as the theme itself, one hop at a time.

**Everything below keeps a surface dimension — in the file name, in the profile field, in the
document path — even though exactly one value is legal today.** That is the point of settling the
format first. `player` being the only accepted value is a *rule and a client restriction*, not a
shape; adding Volume later is then a new entry in a literal list, while a format without the
dimension would make it a migration of every already-published theme. The dimension costs one path
segment and buys the whole of §10.

### 4.1 Capture and attach (phone)

The author already has the file. The submit screen gains one optional slot, opening the photo
picker and producing a normalized image:

- centre-crop to square (watch framebuffers are already square; this only rescues an odd one),
- downscale to at most **450×450**, never upscale,
- re-encode with `Bitmap.compress(WEBP_LOSSY, 85)`.

**450 is what the scope cut buys.** The five-surface draft targeted 360×360 at quality 80 purely to
keep §8's table survivable; with one image per theme the budget per image is five times larger, so
the target can match the common watch framebuffer (450/454 on most round models) and typically not
downscale at all. The result is sharp on a high-density phone rather than visibly soft — which
matters, because looking closely at the Player is the entire purpose of the feature.

Re-encoding through a `Bitmap` is what strips EXIF, which matters more than it looks: a screenshot
shared through a phone gallery can carry metadata the author never intended to publish. That is why
the re-encode is unconditional rather than skipped when the source is already small enough. The slot
shows the result under a round mask, so what the author approves is what the gallery will show.

The decision table — the surface set, the pixel target, the quality, the byte ceilings — belongs in a
new pure `common/.../CommunityThemeScreenshots.kt` with a JVM test, in the shape this project uses
for every decision subtle enough to have a wrong answer. The Android encode stays in the activity;
the policy does not.

**AOD could never have been in the surface set**, whatever the scope. Wear OS does not deliver touch
in ambient — the system wakes the watch instead — so an always-on screen cannot be screenshotted at
all. Offering the slot would only ever be filled by an image that is not what it claims to be. It is
excluded permanently, not deferred like the other four.

### 4.2 Intake (Firestore)

**One document at `themeIntakeShots/{themeId}/surfaces/{surface}`**, carrying `ownerUid`, `surface`,
`webpBase64`, `createdAt`. Create-only, owner-only, `surface` restricted to a literal list holding
`player` alone, `webpBase64` bounded at **128 KB** with the same character-class regex the existing
preview field uses.

The subcollection holds one member today and is still the right shape: it is one write either way,
and it is the storage half of the forward-compatibility argument above. Overloading a single
`themeIntakeShots/{themeId}` document instead would save nothing now and cost a rules rewrite later.

It is a **separate document, written after the intake**, and both halves of that sentence are
load-bearing.

**Separate, because it must not spend the submission batch's budget.** Submission already writes
intake, quota, account and name reservation in one batch, and Firestore's **1000-expression budget
is shared across the whole batch** — a limit that, as `CLAUDE.md` records, is only reached on an
account's *second* submission and reports as an indistinguishable `PERMISSION_DENIED`. A screenshot
has nothing to do with quota or ownership reservation, so it stays out, as its own request.

**After, because that is what bounds it.** An earlier draft of this plan had the screenshot written
*first*, so that a failure could be reported before the author consumed quota. Reading the rules
made the hole plain: a document written before its intake can only be checked against an invented
UUID, so any signed-in account could store unlimited 128 KB documents entirely outside the
three-per-day submission limit. Requiring the intake to already exist, to belong to the caller, and
to still be **`pending`** ties every screenshot to a rate-limited submission and makes an orphan
impossible — so the orphan-sweeping pass that draft needed does not exist and is not needed. The
`pending` clause is the sharp one: without it an author could wait for approval and then attach an
image nobody reviewed, putting unreviewed bytes straight into a public commit. The ordering benefit
that was given up is small, and §4.2's next paragraph is why.

**Failure is harmless by construction.** If the shot write fails, the theme is simply published
without an image — indistinguishable from an author who chose not to attach one. Nothing needs to
be rolled back, so no atomicity is bought. The publisher applies the same rule to every later
failure: a screenshot that will not decode, whose owner disagrees with its intake's, or that names a
surface the registry does not publish, is dropped while the theme publishes normally. A moderator
approved a theme; a bad picture is not a reason to withhold it.

**And a hijack is refused twice.** The rules bind the screenshot to an intake the caller owns, and
the publisher independently refuses any shot whose `ownerUid` disagrees with its intake's.

### 4.3 Review

The admin page renders the submitted screenshot **beside the synthetic Player render of the same
theme, at the same size**. That pairing is the whole review UX: a shot that is not this theme, or
not a watch at all, is obvious in one glance instead of requiring the moderator to hold the profile
in their head.

The review record gains one field, `shotsAccepted` (boolean), so a moderator can **approve a theme
and drop its image**. Without it, one bad screenshot forces rejecting an otherwise good theme, which
is a worse outcome for everyone. The name stays plural for the same forward-compatibility reason the
paths do. It lives in `themeIntakeReview/{themeId}` with the rest of the verdict, never on the
intake — the reason that split exists is that Firestore grants read access **per document, never per
field**, and putting a reviewer-authored field on the intake would leak it to every author.

### 4.4 Publication (Git + Pages)

The publisher writes `docs/themes/shots/<uuid>-player.webp` and records the surfaces present in the
**profile** file, not the index:

```json
"screenshots": ["player"]
```

An array of one, for the reason stated at the top of §4. The index stays untouched on purpose: it is
one fetch for the entire gallery, and the cards are synthetic, so nothing in the list view needs to
know. The detail screen fetches the profile anyway.

Before any byte is committed, the publisher **re-validates the image itself**, with no new
dependency: parse the RIFF container, require the `RIFF`/`WEBP` magic, accept only a simple `VP8 `
lossy chunk, read width and height out of the VP8 frame header, and require square dimensions inside
a fixed range. Rejecting `VP8X` is the load-bearing part of that list — the extended format is what
can carry animation, EXIF and XMP, so refusing those is what makes "no personal metadata reaches
the public site" a property of the pipeline rather than a promise about the client. A colour
profile turned out to be the deliberate exception — see the correction under Phase 1. Roughly forty lines,
fail-closed on everything else, in the same spirit as every other publisher gate: **nothing an APK
sent is trusted as publication data.**

Deletion hooks into the two paths that already exist and must not be forgotten by either:
`applyWithdrawals` and `applyAccountErasure` unlink a theme's shot file in the same commit-then-
Firestore order the theme file itself uses.

### 4.5 Reading (app)

`OnlineThemesRepository` gains `requestBytes` beside `requestJson`, sharing the ETag store and cache
directory, writing `shot-<id>-player.webp` through the same `AtomicFile`. The fetch happens **only
when a detail screen opens**, and only when that profile declares a screenshot, so browsing the grid
costs exactly what it costs today.

On the Player surface the detail screen defaults to the author's screenshot when one exists, labels
which of the two it is showing, and offers one control to switch to the live render. The other five
surfaces have no shot and therefore no control — they behave exactly as they do today, which also
means the control never appears where it would do nothing.

A persisted phone-local preference (`community_theme_show_screenshots`, default on) makes the choice
stick, and it gates the **download**, not just the display — so turning it off is also the
data-saving answer, and the app then never requests an image at all.

That key is a phone-only browsing preference: it does **not** go in `MiscPreferences.EXPORTABLE` or
`SCOPED_KEYS`, so it never reaches the watch and never enters a theme profile.

## 5. Rejected alternatives

**Firebase Storage.** The obvious home for image bytes, and wrong here. It is a product the project
does not currently use ([`firebase.json`](../firebase.json) declares Firestore alone), so it means
new rules, a new emulator surface, and — the actual objection — a **metered egress path that grows
with the gallery's popularity**. The parent plan's whole architecture exists to keep the read side
free and static; introducing a per-view cost for the most-viewed asset in the app inverts that. With
one image per theme the argument is only stronger: GitHub Pages serves a few megabytes for nothing.

**A screenshot per surface (the original scope).** Five images multiply the moderation load (§6),
force a smaller pixel target to keep the repository sane (§8), and buy the least-visited surfaces of
the app. Deferred rather than refused — §10 and the surface dimension throughout §4 are what keep
that door open.

**A slot for AOD.** See §4.1: it cannot be captured, so the slot could only ever hold something
else. Permanently excluded.

**~~Screenshots on the gallery cards.~~ Reversed (2026-09-01): the premise was wrong.** The stated
objection was that per-card images would turn scrolling into N downloads and destroy "the whole
gallery stays one cached request". The gallery has never been one request:
`OnlineThemesActivity.loadPreview` already fetches `docs/themes/<id>.json` for **every visible
card**, lazily on bind, so the miniature has a profile to render. Adding one image fetch per card
that declares a photograph is the same shape of work against the same disk cache — and the profile
the card already downloads is exactly what declares whether a photograph exists, so the index still
does not need to carry it.

What the objection got right is that the cost is per visible row, which is why the load stays lazy
and cached rather than eager. The photograph is drawn over the miniature, so the render remains
underneath and the setting can reveal it again with no reload.

**Dropping the surface dimension now that there is one surface.** Saves a path segment and a JSON
array today, and converts §10 from an additive change into a migration of every published theme.

**Client-side "is this really a watch screenshot" detection.** There is no honest version of this.
The answer is human review (§6) plus the synthetic render staying one tap away (§4.5).

## 6. Moderation

This is where the scope cut pays for itself, and the honest accounting changes with it.

The parent plan's §6 is built on one property: *"A theme is data, never code. The worst a malicious
submission can do is look bad."* Free text — the theme name and author name — is the only real abuse
surface, which is why one human can run an open queue at all.

**An arbitrary image still breaks that property.** The worst case stops being "an ugly theme" and
becomes illegal or abusive content served from `gabrielluizone.github.io`, under the maintainer's
own account. No automated gate meaningfully helps: the existing gates check originality, duplication
and rate, and none of them can look at a picture. That much is unchanged, and it is the reason the
mitigations below are not optional.

What changes is the volume. At five images the queue's per-item review time went up several times
over, permanently. At one, it is **one more glance per submission** — the moderator is already
looking at a synthetic preview on that page, and the new image sits beside it at the same size.
That is a cost worth paying for what §2 says the image buys; the five-image version was arguably
not.

The mitigations:

- **Bounded count, enforced at the rules boundary.** One document per enumerated surface, and one
  legal surface — not "as many as the client sends".
- **The pairing in §4.3.** Judging "is this this theme" is a comparison, not a memory exercise.
- **Partial approval (`shotsAccepted`).** A good theme is never lost to one bad image.
- **The existing rate limit already applies.** Three submissions per rolling 24 hours per account
  caps images at three per account per day, enforced where a modified client cannot reach it.

## 7. Privacy, consent and copyright

**The current policy forbids exactly this, in as many words.**
[`docs/privacy-policy.md`](privacy-policy.md) says of the detail preview: *"It never requests,
takes, or sends a screenshot or automatic capture from your watch."* The parent plan repeats it.
That sentence was written about the **app** capturing something, and this feature is a person
attaching a file they already have — but the sentence as written does not survive the distinction,
and pretending otherwise would be the wrong way to treat a published claim.

So the wording changes, in both places, and the change is real rather than cosmetic:

- `docs/privacy-policy.md` — the *Community themes* section, both the "previews are synthetic"
  paragraph and the "Submitting a theme" one.
- `docs/privacy-policy.html` — the hand-transcribed twin, which does **not** sync automatically;
  the matching sections are copied across by hand and the "Last updated" date bumped in both.
- The Data Safety draft under `docs/play-console-*.md`, which now has a user-supplied image type to
  declare.

**What is actually published.** A watch screenshot contains the album cover that was playing, the
track title and artist, and the clock. All of it becomes public, permanently. The submit screen must
say so plainly *before* the picker opens — not in a policy link — and specifically that Git history
means a later withdrawal removes the image from the site but not from the repository's past. That
last point is already true of the theme JSON, but a settings blob and a photograph of someone's
wrist do not weigh the same, and the consent should reflect the heavier one.

**Copyright.** The embedded cover art is a third party's. Practical exposure is low (thumbnail-size,
non-commercial, author-initiated), but the consent text should have the author confirm they are
publishing it deliberately, and the copy can reasonably suggest picking a track whose cover they are
comfortable making public.

## 8. Things that bite later

**Repository growth.** At 450×450 and quality 85, an album-art screenshot lands around 45–70 KB;
call it 55 KB. One optional image per theme:

| | every theme attaches | ~60% attach |
|---|---|---|
| 100 themes | ~6 MB | ~3 MB |
| 500 themes | ~28 MB | ~17 MB |
| 1000 themes | ~55 MB | ~33 MB |

Comfortable, and the reason 450 was affordable in §4.1. For contrast, the five-surface draft's
ceiling was ~88 MB at 500 themes, which is what forced 360×360 there. GitHub Pages' soft limits are
fine at these numbers, but every clone carries the whole history forever and none of it can be
removed without rewriting history — so the levers, in the order to pull them if real numbers come in
worse, are: reduce the pixel target, drop quality. Both are one constant in
`CommunityThemeScreenshots.kt`, and both apply only to *future* submissions, since what is committed
is committed. **This table is also the budget §10 would spend**: adding a second surface doubles
every cell.

**A screenshot is unverifiable, and always will be.** Nothing prevents an author attaching a
beautiful image of a different theme. Human review is the only check, and it is a spot check. This
is precisely why the synthetic render must remain reachable in one tap rather than being replaced:
the locally-rendered preview is the honest one, generated from the profile that will actually be
installed, and keeping it one control away is what stops the gallery from becoming a place where
what you see is unrelated to what you get.

**Forty locales gate the release.** `CommunityThemeTranslationTest` requires every gallery string to
exist in every supported locale. The attach slot, consent copy, the shot/synthetic switch, error
states and moderation copy are a block of new strings, and the test fails until all of them are
translated — an opt-in screen falling back to English is exactly the gap nobody testing in their own
language ever sees. The scope cut helps here too: one slot means one set of surface-specific
strings, not five.

**The rules test suite only catches this on the second submission.** Any change to the submission
path needs `npm test --prefix firebase` re-run, because the expression-budget failure mode described
in §4.2 does not appear on a first submission from a fresh account. The shot write is outside the
batch specifically to avoid it, and the test is what proves it stayed outside.

**`WatchPreviewView` remains the fallback for every surface, so it does not get to rot.** It is
tempting to treat a screenshot as superseding the miniature. It does not: it is optional, one
surface out of six, and switchable off. `WatchPreviewParityTest` stays exactly as load-bearing as it
is today.

## 9. Phasing

**Phase 1 — the irreversible half, nothing user-visible. Done (2026-09-01).** The public file
layout (`docs/themes/shots/<uuid>-<surface>.webp`), the optional `screenshots` array in the profile
file, the `themeIntakeShots` rules, the publisher's WebP container validator, the ownership
cross-check, the `shotsAccepted` verdict, and removal in `applyWithdrawals`/`applyAccountErasure`
plus at finalization — pinned by nine new emulator tests and nine new publisher tests. Then the
moderator page's paired view and its verdict checkbox. Orphan sweeping turned out to be unnecessary
once the write moved after the intake (§4.2). This phase fixes the format that later phases, every
already-published theme, and §10 must live with, which is why it came first even though it shows
nothing.

Two things were learned building it and are worth keeping. `publicProfilesMatch` deliberately
**ignores** the `screenshots` field: it asks whether a committed file still corresponds to the
approved intake, and a screenshot is not part of the intake — comparing it would turn a good
published theme into one that can never finalize, because finalization is exactly what deletes the
stored document. And the container validator accepts a `VP8X` extended file only when its flags
claim nothing but alpha or a colour profile *and* no EXIF/XMP/animation chunk is actually present,
because a flag byte is a claim; refusing `VP8X` outright was the original plan and would have
rejected legitimate encoder output.

**Corrected after the first real submissions (2026-09-01).** Two photographs were dropped before
anyone saw one in the gallery, and each exposed a category error at a different depth. The first was
in the *reporting*: `invalid-screenshot-image` covered eight unrelated checks, so the log named a
category rather than a cause and left nothing to act on. Splitting the codes and logging the chunk
table produced the real answer on the very next run — `chunks=[VP8X:10, ICCP:536, VP8 :6916]`.

The second was in the *rule*. "Metadata" had been treated as one thing, when EXIF can carry a
location and XMP an author, while an ICC profile only describes a colour space. Android attaches one
to every screenshot, so refusing ICCP refused every real submission there would ever be — and the
profile is functional besides, since a Display P3 picture published without it renders in the wrong
colours. EXIF, XMP and animation are now refused **by chunk name in every container form**, which is
stricter than the flag test it replaced; alpha and ICC are accepted. Checks that only encoded
assumptions about the encoder — exactly one chunk, an exactly-tiling RIFF length, no lossless — were
dropped in the same pass. A boundary should refuse what is unsafe, not what is unfamiliar.

**Phase 2 — the author side. Done (2026-09-01).**
[`CommunityThemeScreenshots.kt`](../common/src/main/java/com/svartifoss/snfell/common/CommunityThemeScreenshots.kt)
and its JVM test, the attach slot and round-masked preview in `SubmitCommunityThemeActivity`, the
normalize/encode step, the consent copy above the button, and `attachScreenshot` in
`CommunityThemeSubmissionRepository`.

Three things are worth keeping. The picked image is **composited onto an opaque ground** before
encoding, which makes libwebp drop the alpha channel and emit the simple `VP8 ` chunk the publisher
prefers — and re-encoding at all is what strips EXIF, so removing a location from an image that
passed through a phone gallery is a property of this step rather than something merely disallowed
later. A **quality ladder** (85/75/65/55) means an unusually noisy picture is refused here, where the
message can name the picture, instead of at the Firestore write as a bare `PERMISSION_DENIED` after
the author has already signed in. And the **picked URI, not the encoded bytes**, goes into saved
instance state: 128 KB through a saved-state binder transaction is the wrong thing to do, while
re-normalizing covers the case this actually happens in (a rotation, same process) and degrades to
an empty slot after process death.

The contract now spans three languages, so `CommunityThemeScreenshotContractTest` reads
`firestore.rules` and `publisher.mjs` directly and pins their surface list and bounds against the
Kotlin constants. **`mobile/build.gradle` declares both files as test inputs**, for the reason it
already declares the wear face directory: without that the task stayed `UP-TO-DATE` and a
deliberately broken bound was reported as a pass — verified, not assumed.

Its strings are in English and Brazilian Portuguese only, matching every other string on this
screen; `CommunityThemeTranslationTest` pins the *gallery* strings, and the submit screen has never
been in its list. Phase 3's strings are gallery strings and do fall under it.

**Phase 3 — the reader side. Done (2026-09-01).** `requestBytes` and the binary ETag cache in
`OnlineThemesRepository`, the `screenshots` field on `OnlineTheme`, the overlay and labelled switch
on the Player surface in `CommunityThemeDetailActivity`, the persisted download-gating preference,
and an image variant of `WatchPreviewFullScreen` so enlarging either source answers the same
question at the same geometry.

Three details worth keeping. The photograph is drawn **over** the live `WatchPreviewView` rather
than replacing it, so switching back is immediate and costs no second parse of the profile. A
response that is not a WebP — a 404 served as an HTML page, most obviously — is refused **before**
the disk cache, because a cached one would be handed to `BitmapFactory` on every later visit and
leave a permanent blank where the fallback belongs. And an unknown surface name in a profile is
dropped rather than refused, since this build may be older than the catalogue and the degraded
result is exactly what a theme with no screenshot already shows.

The published file path now has one owner, `CommunityThemeScreenshots.SHOTS_DIRECTORY`/`fileName`,
pinned against the publisher by `CommunityThemeScreenshotContractTest`. It earns its place: a path
built differently on either side is an error nowhere — the fetch 404s, the gallery reports "no
picture", and every screenshot silently stops appearing while the catalogue, the commit and the
detail screen all look completely correct.

**Phase 4 — documentation, which is a gate and not a follow-up. Done (2026-09-01).** Both privacy
policy files at 01-09-2026, the Data Safety draft, `CHANGELOG.md`, and the amendment to
[`online-themes-plan.md` §7](online-themes-plan.md) so the two documents stop contradicting each
other.

On locales: the attach slot and the detail-screen strings are English and Brazilian Portuguese,
which is what every other string on both screens already is. `CommunityThemeTranslationTest` pins
the gallery *list* strings in `watch_themes_strings.xml`; neither the submit screen nor the detail
screen has ever been in its scope, and widening it is a separate decision from this feature.

## 10. Open questions

- **When, and whether, the other four surfaces arrive.** Volume, Progress, Quick panel and Queue are
  deferred, not refused, and §4 is built so that adding one is a value in a literal list plus a slot
  in the submit screen — no migration of published themes, no format change. The three things to
  weigh first: what it does to the queue (§6), the doubling of every cell in §8's table per surface,
  and whether real submissions show authors wanting it at all. Watching which surfaces people ask
  about after shipping the Player is a cheaper way to answer that than guessing now.
- **Whether the download preference defaults on or off.** Proposed: **on**, because the image is the
  point of the feature and the fetch is already bounded to a detail page the user deliberately
  opened — unlike `queue_remote_artwork`, though, off here degrades nothing, so the opt-in argument
  is available and this is a defensible place to change one's mind. It is a privacy-doc-relevant
  default either way.
- ~~**Whether a card may ever use the author's screenshot.**~~ **Answered (2026-09-01): yes, and it
  shipped.** Reading the gallery to cost it out showed the reason for deferring it was false — see
  §5. Worth remembering as a method note: the objection had been written from the design rather
  than from the code, and it survived two review passes because it sounded right.
- **Whether 450×450 at quality 85 survives contact with real submissions.** It is sized against §8's
  table and a detail card that may render larger on a high-density phone. Calibrate on real images
  before the number is baked into a hundred published themes.
- **Whether an author can replace a screenshot without resubmitting the theme.** Theme *updates* are
  already the parent plan's one unbuilt part; the screenshot inherits that gap, and a shot-only
  update is a smaller version of the same problem — possibly a good place to build the update path
  first.
- **Whether the moderation preview and the author's screenshot should merge.** They are different
  images serving different purposes today; if a shot exists, the reviewer arguably wants it as the
  queue thumbnail. Deferred, because it couples two things that are currently independent.
