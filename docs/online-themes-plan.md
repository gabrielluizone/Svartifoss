# Online themes — design plan

A curated, community-submitted gallery of watch appearance themes: users publish the profiles they
build in the Watch tab, browse what others published, and apply them on their own watch.

> **Status: Version 4.0 implementation (2026-08-24).** The repository now contains local
> discovery (search, base-face filters, newest/most-liked ordering, and an explicit Liked filter),
> a detail screen, private likes, Android submission intake, Firestore rules, a restricted reviewer
> page, and the trusted
> GitHub publisher workflow. Production deployment requires the Firebase configuration/rules and
> the publisher's `FIREBASE_SERVICE_ACCOUNT` repository secret. Theme-update submissions remain
> future work. This document records the decisions so later work does not have to be re-derived
> from first principles.
>
> **Decisions confirmed (2026-08-23):**
> - Scope is an **open store with a moderation queue** (hundreds of submissions), not a small
>   hand-curated set.
>
> **Revised (2026-08-26):** identity is **Google Sign-In via Firebase Auth, required only to
> submit**. A person may also explicitly connect it in Settings ahead of time; Firebase restores
> that local connection, so later submissions do not ask again. Liking, unliking and the private
> **Liked** filter use an anonymous Firebase account provisioned silently. See §5.
> - Approved themes are **hosted in this repository** and served from GitHub Pages. Approval is a
>   commit.
> - **Comments and theme updates are out of scope.** Likes are private reactions, not discussion.

---

## 1. The problem it solves

The Watch tab lets a user build a complete appearance profile and save it to a phone-local library
(`WatchThemeRepository`, up to `MAX_PROFILES` = 24). That library is **phone-local and private**:
the only way a theme reaches another person today is a full `ConfigBackup` export, which carries
everything else the user owns — button configs, action list, search history, saved shortcuts — and
replaces the recipient's whole library rather than adding one theme.

So a good theme is effectively unshareable. Every user starts from the built-in faces, and a theme
the author builds cannot reach anyone.

## 2. What already exists (most of the hard part)

The wire format is done, and it is already hardened against hostile input — which is the part that
usually makes user-generated content expensive.

[`WatchThemeRepository.prepareCommunityThemeSubmission`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/WatchThemeRepository.kt#L298)
creates a fresh, self-contained public profile from a saved user-owned theme,
with explicitly typed values (`{"type": "string"|"boolean"|"int", "value": …}`).
It never sends the phone-local library ID or gallery-install provenance. The tolerant
[`parseProfile`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/WatchThemeRepository.kt#L967)
remains for old local backups; the public network path goes through
[`parsePublishedProfile`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/WatchThemeRepository.kt#L341),
which:

- accepts only current `FaceScopedPreferences.SCOPED_KEYS` and the exact JSON type for each one,
- rejects unknown/archived faces and oversized text or materialized snapshots,
- fills any omitted current-version setting from the shipped base-face default, never the recipient's
  personal preferences,
- and refuses a candidate that would push the final phone-to-watch preference payload past its
  conservative transport budget.

Two consequences worth stating plainly:

**A theme is data, never code.** The worst a malicious submission can do is look bad. There is no
execution surface, no URL, no file path, no intent — only enumerated preference keys with typed
values. That removes most of the risk that a user-content feature normally carries, and it is why
this is a reasonable feature for a solo developer to host at all.

**Forward/backward compatibility is explicit at the gallery boundary.** The index carries the
profile schema, base face and minimum app version; an older client keeps incompatible cards visible
with a requirement rather than downloading and applying a partial look. Local backup import remains
tolerant of old data. (See §8.)

**Size:** ~143 keys ≈ 6–9 KB of JSON per theme, and it compresses well (highly repetitive). An index
entry is ~200 bytes. At 500 themes the index is ~100 KB — one fetch, ETag-cacheable.

## 3. Architecture

The governing decision is to **split reading from writing.** They have opposite requirements, and
merging them is what would make this expensive to run.

- **Browsing and discovery** — what essentially every user does — need no account and no live
  backend. Search, base-face filtering, and newest/most-liked ordering run locally over the one
  ETag-cached public catalogue. The separately chosen **Liked** filter is the sole exception: it
  reads a person's own vote documents only for IDs already present in that catalogue.
- **Writing** — submitting and an explicit like/unlike — needs identity and Firestore. Neither
  operation is needed to browse, inspect a theme, or install it.

The app cannot write to the repository (that is exactly the problem an embedded PAT would create,
see §4), so a bridge is required, and GitHub Actions is the natural place for it:

```
                    ┌──────────────────────────┐
   phone app ──────>│  Firestore (submission   │<────── admin page
   (submit / like)  │  queue + private likes)  │        (approve/reject)
                    └────────────┬─────────────┘
                                 │ reads approved and aggregates likes
                                 │ (service account in repo secrets)
                    ┌────────────▼─────────────┐
                    │  GitHub Action           │
                    │  (cron + manual dispatch)│
                    └────────────┬─────────────┘
                                 │ commits with native GITHUB_TOKEN
                    ┌────────────▼─────────────┐
   phone app <──────│  docs/themes/*.json      │
   (browse, apply)  │  served by GitHub Pages  │
                    └──────────────────────────┘
```

**Why this shape:**

- The Action runs *inside* this repository, so it commits with the natively provided `GITHUB_TOKEN`.
  No write credential exists anywhere that could leak. The Firebase service-account key lives in
  repo secrets, server-side. The APK carries nothing privileged — it can create only a constrained
  submission or its signed-in user's private like, and Firestore security rules bound both.
- It needs **no Cloud Functions**, which matters: Functions making outbound network calls require the
  Blaze plan. Here all the "server" logic is a workflow file that runs for free.
- Publishing is a batch operation that produces **a reviewable git diff** — a second safety net that
  hosting on Firebase would not provide.
- If Firebase is ever shut off, the static gallery keeps working. Submissions and likes stop.

The publisher is implemented as the repository's first GitHub Actions workflow. It runs only on the
default branch, by daily schedule or manual dispatch, and uses a repository secret named
`FIREBASE_SERVICE_ACCOUNT` to read/finalize approved records, reconcile the server-only published
theme markers, and aggregate private likes into the static catalogue. That secret and its
least-privilege Firestore service account must be configured before production approval can publish
a submission or refresh public like totals.

### Layout

```
docs/themes/index.json      # id, name, author, baseFace, revision, schemaVersion, publishedAt, likes
docs/themes/<id>.json       # validated public profile body + store metadata
```

Shard the index if it ever passes a few thousand entries; at hundreds it is one file.

`likes` is an aggregate calculated by the trusted publisher, never a counter written by an APK.
It is intentionally eventually consistent: a recent like or unlike may not change the public count
or its popularity ordering until the next publisher run. This preserves one static browse request
instead of adding a Firestore read for every card.

### Staying on the free (Spark) plan

**Do not use Firebase Storage** — new buckets require the Blaze plan. Put the moderation preview WebP
**inside the Firestore document as base64** instead. A 200×200 WebP is ~10–15 KB, ~20 KB base64'd;
with the ~7 KB theme body that is ~27 KB against Firestore's 1 MiB document limit. Everything fits
in Firestore, on Spark, with no card on file.

*(Re-check both plan requirements before building — Firebase's free-tier boundaries have moved
recently and will move again.)*

## 4. Rejected alternatives

**GitHub PAT embedded in the app.** Non-starter. The app is sideloaded, the APK is public on GitHub
Releases, and pulling a string out of an APK is trivial — anyone would have write access to the
repository. Asking users for *their own* PAT fixes the security and kills the feature: almost no user
of this app has a GitHub account, let alone knows how to mint a scoped token.

**Hosting approved themes in Firebase instead of the repo.** Rejected per the confirmed decision, and
it also loses the reviewable diff and makes the gallery dependent on a service staying funded.

**Cloud Function writing to GitHub.** Works, but needs Blaze and puts a GitHub token in function
config. The Action inverts this for free.

**Comments.** Rejected. It is the only part that creates *ongoing* obligation rather than one-time
work — moderation across 14 locales, with no tooling, by one person — and it is the classic abuse
vector. The value is low: nobody needs to discuss a theme, they need to see it and apply it. A
**report button** covers the real case (an offensive listing) at a fraction of the cost.

## 5. Identity

Firebase Auth carries submission ownership and private likes, but the two use **different kinds of
account**, and the split is the point.

**Submission uses Google Sign-In.** A person can either select **Submit to community** for their
own saved profile and tap **Sign in and submit**, or explicitly connect Google first from
**Settings → Data & support → Community themes → Community account**. Firebase restores that
connection locally, so a later submission reuses it without reopening Credential Manager.

**Liking uses an anonymous account.** Demanding a Google account for a heart tap asks for an
identity out of all proportion to the act, and the friction falls hardest on the one interaction the
gallery most needs in order to rank anything at all. So the first like signs in anonymously with no
prompt and no visible account; unliking and the private **Liked** filter ride on the same UID. The
filter reads only that UID's reactions for known public catalogue IDs; it never asks Firestore to
enumerate voters.

Two consequences follow, and both are accepted rather than engineered around:

- **The anonymous UID is local**, so clearing app data or reinstalling yields a new one and the same
  person can vote again. The counts are a rough popularity signal, not a ballot, and a vote ledger
  that anyone can already influence with a second Google account was never going to be one.
- **The rules must now separate the two.** `identifiedUser()` gates every intake and quota rule
  while the like rules accept a bare `signedIn()`; without that, enabling anonymous auth would have
  silently opened submission to free, unlimited, disposable accounts and made the per-24-hours
  quota meaningless. Its second clause tests the linked `google.com` identity as well as the
  provider, because `sign_in_provider` keeps reporting `"anonymous"` after an account is upgraded —
  so testing the provider alone would refuse submissions from everyone who liked a theme first.

To keep those reactions across that upgrade, signing in to submit **links** the Google credential
onto the existing anonymous account rather than replacing it, falling back to an ordinary sign-in
only when the Google account already exists in its own right.

Firebase Auth processes the Google credential/account information needed for authentication and
returns a UID, which is written as `ownerUid` in the private Firestore intake record. The Android
client deliberately does **not** send the Google profile name or email in that document; the author
supplies a separate public pseudonym or chooses the public credit **Anonymous**. The UID makes an
intake record attributable to the same Firebase identity without making that identity public.

This current phase provides a device-local account screen for explicit Google connection and
disconnection, but not comments, a submission history, or theme updates. **Remove from this
device** signs out locally; it is not self-service Firebase-account or submission deletion. A like
does not publish a profile, expose a voter, or make a public account page.
Takedown/removal requests use the existing address in
[`privacy-policy.md`](privacy-policy.md#contact); they are handled manually. A current public
listing can be removed going forward, but a profile/name/pseudonym already committed to the public
repository can remain in Git history and third-party copies. The privacy-policy and Data Safety
documents therefore describe authored-content storage explicitly rather than claiming an automatic
account deletion mechanism.

### Deleting an account (4.0)

Two things make this different from an ordinary "delete my account" button, and both push the work
out of the app.

An APK can delete its own Firebase identity and nothing else. The themes that account published
live in Git, behind a workflow the app cannot reach, so an in-app deletion would destroy exactly the
half that could later ask for them to come down and leave the public half standing. The request is
therefore recorded as one create-only Firestore document and carried out by the publisher, which
already owns both sides: it withdraws files in a commit first and writes Firestore after, the same
ordering publication uses and for the same reason.

And the interesting half is not deleting -- it is that **the answer is not the app's to guess**. A
published theme carries a pseudonym that is already public and that other people have installed;
withdrawing it and leaving it are both entirely reasonable, so `themeDisposition` (`keep` /
`delete`) is asked once, stored on the request, and re-validated by the publisher. Neither value
preserves a submission that is not public yet: "keep my themes" can only mean the ones people can
see. An anonymous like-only account is not asked at all, since it cannot own a theme, and the
unprompted disposition is `keep` so that an account which unexpectedly does own something public has
it left alone rather than silently withdrawn.

The request is deliberately one-way. Allowing a client to edit or withdraw it would let it change
between the commit that removed a theme file and the Firestore writes that follow, which is the one
state this pipeline has no way to reconcile.

## 6. Moderation at scale

An open queue of hundreds with one human reviewer means **the reviewer is the bottleneck**. The queue
has to be designed to protect that person from the start. Human review is the *last* filter, not the
first.

### Automated gates, before anything reaches the queue

1. **Minimum originality — implemented locally and rechecked by the publisher.** The app resolves
   the selected base face's shipped defaults and counts only complete typed settings that differ
   **and can affect that face's complete visual state**. Face-only controls, obsolete migration
   fields, and settings hidden by the submitted state do not inflate the count. The current
   threshold is **12 applicable visual settings**. A candidate below it cannot open Google Sign-In
   or enter the queue; the UI tells the author exactly how many changes it has. This is a
   usability/spam filter, not a security boundary: a modified client can evade it, so the trusted
   publisher applies the same rule before public release.
2. **Exact-duplicate detection — implemented by the trusted publisher.** The common policy
   computes a type-aware SHA-256 digest over the normalized base face and settings. The Android
   client has no privileged published-digest index, so it does not reject a candidate merely from
   a client-side lookup. Before committing, the publisher compares each approved profile with the
   complete static catalogue and with other approved profiles in the same run; an exact duplicate
   is terminally rejected without producing a public file.
3. **Rate limiting per account — enforced at the rules boundary.** A private
   `communityThemeSubmissionQuota/<Firebase Auth UID>` document is written in the same Firestore
   transaction as a new intake record. Version 2 retains the last three submission timestamps;
   `getAfter` rules bind that window to the exact new submission and enforce **at most ten
   submissions in a 24-hour period**. Version 3 replaced version 2's rolling history with a fixed
   window: proving a *rolling* limit needs the timestamp of every submission in it, so each extra
   one costs a stored field and a rule branch inside the batch whose expression budget is already
   the sharp edge of this path. A fixed window costs the same at any allowance, and gives up only
   the boundary — a burst can straddle two windows, which the moderation queue still gates. A modified client cannot skip, reset, or delete
   the quota record; it can only read its own.

The typed schema and digest are Android-free functions in `common/`; the shared semantic and
applicability contract is data in `common/src/main/assets/community-theme-constraints.json`, consumed
by both Android and the Node publisher. JVM and Node tests pin the defaults, values, and threshold.
The fixed threshold is deliberately visible here so it can be calibrated from real, moderated
submissions rather than silently changing as a product rule.

### What is left for a human

After those gates, what actually needs judgement is **the theme name and the author name** — free
text is the real abuse surface, since the theme itself is inert.

### Where review happens

The Firebase Console is not usable for this: no preview grid, no bulk actions, and 143 keys of JSON
tell a reviewer nothing about how a theme looks.

Instead: **a static admin page on GitHub Pages**, gated by Firebase Auth with a moderator's UID
allowlisted in security rules. It shows a grid of pending previews and can make one `pending` →
`approved` or `rejected` decision, writing only the decision, reviewer UID, and timestamp back to
Firestore. It cannot edit the submitted content or publish a theme.

Flow: triage themes on the page → the trusted publisher runs (daily schedule or manual dispatch) →
it reads approved-but-unpublished entries, validates them again, writes `docs/themes/<id>.json`,
updates the index, and commits. Only after the Git push succeeds does it revalidate the static files
and mark matching intake documents `published`. The checked-in workflow still needs its service
account secret before production use.

## 7. Previews

> **Amended (2026-09-01).** The decision below — no preview images in Git, ever — was reversed for
> one narrow case: an author may now attach **one** photograph of their own watch's Player screen to
> a submission, which the publisher commits beside the theme. The reasoning here still holds for
> everything else, and nothing in this section changes for a theme without one: cards stay
> synthetic, every other surface stays locally rendered, and the moderation preview is still
> discarded rather than published. See [`theme-screenshots-plan.md`](theme-screenshots-plan.md) for
> why the reversal was worth its cost, what it does to the repository's growth, and what it does to
> the moderation queue — which is the part that actually got more expensive.

Approving a theme is a **visual** judgement, but shipping preview images has a cost: hundreds of PNGs
committed to the repo grow forever and can never be removed from git history.

**The split that avoids it:** the uploaded preview exists only for moderation — it lives as a
base64-encoded WebP in the Firestore document, the reviewer looks at it, and the trusted publisher
must discard it rather than commit it on publication. The public gallery **renders each miniature
locally on the phone** with
[`WatchPreviewView`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/WatchPreviewView.kt).

Gallery cards and the 200×200 off-screen moderation renderer both force the bundled sample track
and artwork; the moderation renderer also fixes its clock and animation phase. The gallery detail
screen opens before **Add and apply** and renders its Player, always-on display (AOD), Volume,
Progress, Quick panel, and Queue locally from the selected profile. Its labels, timing, and queue
remain synthetic sample data. To make the detail preview feel more familiar, it may use only the
current album cover already held in memory on the phone. It does not use the current title, artist,
playback position, queue, or other live media metadata, and that cover is neither uploaded nor
transmitted to the watch. No screenshot or automatic watch-screen capture is requested, created,
or sent. The repository carries only public JSON and, since the amendment above, whatever an author
deliberately attached; no live song title, artist, cover, clock time, or
other currently-playing media data is captured in a submission preview. The normal app produces
that preview from the same profile it uploads, but a modified client could still send a mismatched
bitmap. The reviewer page therefore labels the bitmap as advisory, checks the profile JSON and
settings digest before enabling approval, and the trusted publisher — not the preview — is the final
validation boundary.

Phase 1 put the local-rendered-miniature risk behind real gallery usage. The remaining production
check is scroll performance on representative phones as the public catalogue grows.

## 8. Things that bite later

**Schema versioning is currently store-hostile.**
[`validateImport`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/WatchThemeRepository.kt#L607)
requires `schemaVersion` to equal `LIBRARY_SCHEMA` exactly, and
[`parseState`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/WatchThemeRepository.kt#L948)
accepts only `1..LIBRARY_SCHEMA` — **both reject a newer schema.** In a store that means the day
`LIBRARY_SCHEMA` is bumped, every theme published afterwards silently breaks on older installs. The
index must carry `schemaVersion` per theme so the client can filter what it cannot read and show
"requires app version X" rather than failing quietly.

**New faces have the same shape of problem.** `parseProfile` rejects a `baseFace` outside
`ALLOWED_BASE_FACES`, so a theme built on a face added in a later release disappears without
explanation on older installs. The index already needs `baseFace`; pair it with a minimum app version.

**Archived faces must not be submittable.** `ArchivedFaces.KEYS` (currently `vinyl`, `halo`, `aurora`,
`eclipse`, `spectrum`, `depth`) are hidden from the pickers; accepting submissions built on them
would publish themes most users cannot see the base of.

**Public name ≠ Google account.** Once an approved theme becomes a commit in a public repository, the
public credit is in git history permanently, and rewriting history is not an option. At submission,
the author can choose a **pseudonym** or the public credit **Anonymous**; the Google account name and
email are never published by default. The app can disconnect an account from the current device,
but there is no self-service account or submission deletion. The privacy policy instead provides the existing email address for manual
takedown/removal requests: a current listing can be removed going forward, but Git history and
third-party copies may retain what was already public.

**Likes retain a static catalogue.** A person creates or removes a like only with an explicit touch
on a published theme. If authentication is needed, that touch is what opens Google Sign-In. Each
vote is an immutable private document at
`communityThemeLikes/<themeId>/voters/<Firebase Auth UID>`; a client can read or delete only its
own vote and can never list voters or write a public counter. A signed-in person opening details may
read only that same private document to reflect their own selected state. The explicitly selected
**Liked** filter performs the same private read only for IDs already contained in the public static
catalogue; it does not list or query a voter collection. The trusted publisher uses Firestore's
aggregate count and later bakes the total into `index.json`, so the displayed number and most-liked
ordering may lag a reaction until the next run but browsing stays static and anonymous.

**Network stance.** The project is deliberately opt-in about network paths. Gallery browsing follows
the lyrics precedent rather than the shortcut-artwork one: opening the gallery *is* the consent, since
nothing is fetched until a user navigates there. Submission and liking are explicit actions by
construction.

## 9. Phasing

**Phase 1 — read-only gallery, hand-populated.** Static catalogue in `docs/themes/`, a browse screen,
and apply-a-theme. The author populates the catalogue by hand with 15–20 themes. No identity, no
Firebase, no backend, no obligations.

**Implementation checkpoint (2026-08-24).** The app has the separate community-gallery screen,
an ETag-backed cache for the Pages catalogue/profiles, local `WatchPreviewView` miniatures using
the sample track, and safe add-and-apply into the phone-local library. The first four seed themes
live under `docs/themes/`; expanding that hand-authored set and testing scroll performance on real
phones is the next Phase-1 task. Installing a published theme deliberately creates a local identity,
so edits stay local and a later update flow can offer a choice instead of overwriting them.

This already delivers the original motivating case ("I like a theme the author made and cannot get
it"), and more importantly it is how the locally-rendered-miniature risk (§7) gets tested before
anything depends on it.

**Phase 2 — submission and moderation.** The Android intake is now additive to Phase 1: a person
chooses a user-owned local profile, receives a fresh public UUID and complete typed profile body,
passes the local 12-applicable-setting originality preflight, and then either reuses a Google
connection made in Settings or explicitly invokes Google Sign-In.
Firestore stores the UID-owned immutable pending record, public theme name/pseudonym, fixed-sample
WebP review preview, client version, and server timestamp. The versioned rules enforce at most ten
submissions in a 24-hour period. The restricted static reviewer page makes a one-time
decision possible without exposing a write credential in the APK. The checked-in GitHub publisher
writes/commits the approved static profiles
and then finalizes their Firestore status; Firebase/Auth deployment and its service-account secret
are still required before the production queue can publish approved items. This phase also updates
the privacy policy and Data Safety documentation.

**Phase 3 — discovery, details, and likes.** The gallery now searches by theme name, author
pseudonym, or base face; filters by base face; and locally orders the static catalogue by newest or
most liked. Its optional **Liked** filter is an explicit authenticated action that checks only the
person's own vote documents for public catalogue IDs. A card opens a detail screen before
installation, including synthetic watch-surface previews and metadata; the detail preview may use
only the current local album cover, never live title, artist, timing, queue, a screenshot, or an
upload. Likes require an explicit heart touch and silently provision an anonymous Firebase account;
their private per-UID documents are aggregated only by the trusted publisher. Theme updates are still not implemented:
they can reuse the `revision` field the profile already carries, re-enter the queue, overwrite
`docs/themes/<id>.json`, bump the index, and let the app compare with the locally installed revision
to offer a choice.

## 9a. Amendment: downloads and reports (3.4)

Two things this plan did not have, added on top of the ledger shape §5 already established for
likes.

**A download figure.** §3 chose a static catalogue served from Pages precisely so browsing costs
no backend, and the consequence nobody wrote down is that nothing on the serving side can ever
observe a download — a request there measures a phone refreshing its cached copy of the whole
list, and the profile fetch on the way to a detail screen is a preview, not an install. So the
number counts the one event that *is* observable: an install succeeding on a phone, written as
`communityThemeInstalls/<theme>/installers/<uid>` and totalled by the publisher exactly like a
like. It is create-only — removing a theme from My themes does not un-download it, and a delete
would make a periodically-republished number walk backwards. It rides the like-refresh interval
rather than taking one of its own, so the two figures on a card were last correct at the same
moment. That interval is twelve hours; it began at a week, and a week turned the deferral into the
bug it was meant to prevent — the published figures visibly never moved, which reads as votes not
being counted rather than as votes not yet being committed.

**A report path.** §6 built moderation as a queue a person reads, with no way for anyone but the
moderator to put something into it — which is a gap rather than a design, and one Play's
user-generated-content policy requires closing. `communityThemeReports/<theme>/themeReporters/<uid>`
is the same private per-account document, with three differences that follow from it being the one
write made about somebody else: no count is ever aggregated into the catalogue, the reported
author can never read a report or learn who filed one, and it cannot be withdrawn. Like a like, it
takes an anonymous account: demanding an identity before somebody can flag offensive content puts
the cost on the wrong person. The moderator page reads them with a collection-group query, which
needs a recursive-wildcard rule and therefore a subcollection name (`themeReporters`) that nothing
else in the database will claim.

Neither changes §7's privacy position or §4's rejected alternatives. Both are recorded in
`docs/privacy-policy.md` and its hand-transcribed HTML twin.

## 10. Open questions

- Whether the current 12-applicable-setting originality threshold should be raised or lowered after reviewing
  real submissions. It is intentionally a documented, conservative starting point rather than a
  hidden permanent policy.
- Whether hand-picked featuring should join the existing newest and most-liked orderings without
  making a curator-controlled sort look like a popularity count.
- Whether `MAX_PROFILES` (24) should apply to installed store themes, or whether store themes live in
  a separate bucket from user-authored ones.
- How a user's own submissions are listed back to them across devices (needs a Firestore query by
  UID; cheap, but a screen). Account deletion needs the same query and does it in the publisher
  rather than the app, deliberately: the rules still let an author read only their own *pending*
  submission, because widening that to approved and rejected ones would expose the reviewer UID on
  each. So the deletion screen asks its question without listing what it applies to, which is the
  honest shape until that screen exists.
