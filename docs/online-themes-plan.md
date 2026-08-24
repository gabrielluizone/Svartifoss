# Online themes — design plan

A curated, community-submitted gallery of watch appearance themes: users publish the profiles they
build in the Watch tab, browse what others published, and apply them on their own watch.

> **Status: Phase 2 implementation (2026-08-24).** The repository now contains the read-only
> gallery, Android submission intake, Firestore rules, a restricted reviewer page, and the trusted
> GitHub publisher workflow. Production use still requires the Firebase configuration/rules to be
> deployed and the publisher's `FIREBASE_SERVICE_ACCOUNT` repository secret to be configured. Likes
> and theme updates remain future work. This document records the decisions so later work does not
> have to be re-derived from first principles.
>
> **Decisions confirmed (2026-08-23):**
> - Scope is an **open store with a moderation queue** (hundreds of submissions), not a small
>   hand-curated set.
> - Identity is **Google Sign-In via Firebase Auth**, required only to submit — never to browse.
> - Approved themes are **hosted in this repository** and served from GitHub Pages. Approval is a
>   commit.
> - **Comments are out of scope.** Likes and theme updates are separate Phase-3 work.

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

**Size:** ~115 keys ≈ 6–9 KB of JSON per theme, and it compresses well (highly repetitive). An index
entry is ~200 bytes. At 500 themes the index is ~100 KB — one fetch, ETag-cacheable.

## 3. Architecture

The governing decision is to **split reading from writing.** They have opposite requirements, and
merging them is what would make this expensive to run.

- **Reading** — what essentially every user does — needs no account, no backend, and no quota.
- **Writing** — submitting, which is rare — is the only current operation that needs identity and
  a server. Likes are a later phase.

The app cannot write to the repository (that is exactly the problem an embedded PAT would create,
see §4), so a bridge is required, and GitHub Actions is the natural place for it:

```
                    ┌──────────────────────────┐
   phone app ──────>│  Firestore (submission   │<────── admin page
   (submit)         │  queue)                  │        (approve/reject)
                    └────────────┬─────────────┘
                                 │ reads approved
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
  repo secrets, server-side. The APK carries nothing privileged — it can only submit, and Firestore
  security rules bound that.
- It needs **no Cloud Functions**, which matters: Functions making outbound network calls require the
  Blaze plan. Here all the "server" logic is a workflow file that runs for free.
- Publishing is a batch operation that produces **a reviewable git diff** — a second safety net that
  hosting on Firebase would not provide.
- If Firebase is ever shut off, the gallery keeps working. Only submission stops.

The publisher is implemented as the repository's first GitHub Actions workflow. It runs only on the
default branch, by daily schedule or manual dispatch, and uses a repository secret named
`FIREBASE_SERVICE_ACCOUNT` to read/finalize approved records. That secret and its least-privilege
Firestore service account must be configured before production approval can publish a submission.

### Layout

```
docs/themes/index.json      # id, name, author, baseFace, revision, schemaVersion, publishedAt
docs/themes/<id>.json       # validated public profile body + store metadata
```

Shard the index if it ever passes a few thousand entries; at hundreds it is one file.

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

Firebase Auth with Google Sign-In is used for submission ownership. The app does not initialize an
interactive sign-in while a person browses the gallery. It opens Credential Manager only after they
select **Submit to community** for their own saved profile and tap **Sign in and submit**.

Firebase Auth processes the Google credential/account information needed for authentication and
returns a UID, which is written as `ownerUid` in the private Firestore intake record. The Android
client deliberately does **not** send the Google profile name or email in that document; the author
supplies a separate public pseudonym. The UID makes an intake record attributable to the same
Firebase identity without making that identity public.

This current phase does not provide an app-wide account screen, likes, a submission history, or
theme updates. It also has no self-service Firebase-account or submission-deletion control.
Takedown/removal requests use the existing address in
[`privacy-policy.md`](privacy-policy.md#contact); they are handled manually. A current public
listing can be removed going forward, but a profile/name/pseudonym already committed to the public
repository can remain in Git history and third-party copies. The privacy-policy and Data Safety
documents therefore describe authored-content storage explicitly rather than claiming an automatic
account deletion mechanism.

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
   transaction as a new intake record. `getAfter` rules bind it to that exact submission and require
   at least **24 hours** since the last one. A modified client cannot skip, reset, or delete the
   quota record; it can only read its own.

The typed schema and digest are Android-free functions in `common/`; the shared semantic and
applicability contract is data in `common/src/main/assets/community-theme-constraints.json`, consumed
by both Android and the Node publisher. JVM and Node tests pin the defaults, values, and threshold.
The fixed threshold is deliberately visible here so it can be calibrated from real, moderated
submissions rather than silently changing as a product rule.

### What is left for a human

After those gates, what actually needs judgement is **the theme name and the author name** — free
text is the real abuse surface, since the theme itself is inert.

### Where review happens

The Firebase Console is not usable for this: no preview grid, no bulk actions, and 115 keys of JSON
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

Approving a theme is a **visual** judgement, but shipping preview images has a cost: hundreds of PNGs
committed to the repo grow forever and can never be removed from git history.

**The split that avoids it:** the uploaded preview exists only for moderation — it lives as a
base64-encoded WebP in the Firestore document, the reviewer looks at it, and the trusted publisher
must discard it rather than commit it on publication. The public gallery **renders each miniature
locally on the phone** with
[`WatchPreviewView`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/WatchPreviewView.kt).

The gallery and the 200×200 off-screen moderation renderer both force the bundled sample track and
artwork; the moderation renderer also fixes its clock and animation phase. The repository carries
only public JSON; no live song title, artist, cover, clock time, or other currently-playing media
data is captured in a submission preview. The normal app produces that preview from the same
profile it uploads, but a modified client could still send a mismatched bitmap. The reviewer page
therefore labels the bitmap as advisory, checks the profile JSON and settings digest before enabling
approval, and the trusted publisher — not the preview — is the final validation boundary.

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
author's name is in git history permanently, and rewriting history is not an option. So the author
picks a **pseudonym at submission time**; the Google account name and email are never published by
default. There is no self-service account or submission deletion in the current app. The privacy
policy instead provides the existing email address for manual takedown/removal requests: a current
listing can be removed going forward, but Git history and third-party copies may retain what was
already public.

**Likes with a static catalogue are future work.** No likes collection, authentication prompt for
likes, or displayed count exists in the current implementation. If added later, likes can live in
Firestore and be baked into `index.json` on each rebuild; do not add a live per-theme read that turns
a static browse into a per-card quota cost.

**Network stance.** The project is deliberately opt-in about network paths. Gallery browsing follows
the lyrics precedent rather than the shortcut-artwork one: opening the gallery *is* the consent, since
nothing is fetched until a user navigates there. Submission is explicit by construction.

## 9. Phasing

**Phase 1 — read-only gallery, hand-populated.** Static catalogue in `docs/themes/`, a browse screen,
and apply-a-theme. The author populates the catalogue by hand with 15–20 themes. No identity, no
Firebase, no backend, no obligations.

**Implementation checkpoint (2026-08-24).** The app has the separate community-gallery screen,
an ETag-backed cache for the Pages catalogue/profiles, local `WatchPreviewView` miniatures using
the sample track, and safe add-and-apply into the phone-local library. The first
four seed themes live under `docs/themes/`; expanding that hand-authored set and testing scroll
performance on real phones is the next Phase-1 task. Installing a published theme deliberately
creates a local identity, so edits stay local and a later update flow can offer a choice instead
of overwriting them.

This already delivers the original motivating case ("I like a theme the author made and cannot get
it"), and more importantly it is how the locally-rendered-miniature risk (§7) gets tested before
anything depends on it.

**Phase 2 — submission and moderation.** The Android intake is now additive to Phase 1: a person
chooses a user-owned local profile, receives a fresh public UUID and complete typed profile body,
passes the local 12-applicable-setting originality preflight, and only then explicitly invokes
Google Sign-In.
Firestore stores the UID-owned immutable pending record, public theme name/pseudonym, fixed-sample
WebP review preview, client version, and server timestamp. The versioned rules and
restricted static reviewer page make a one-time decision possible without exposing a write
credential in the APK. The checked-in GitHub publisher writes/commits the approved static profiles
and then finalizes their Firestore status; Firebase/Auth deployment and its service-account secret
are still required before the production queue can publish approved items. This phase also updates
the privacy policy and Data Safety documentation.

**Phase 3 — likes and theme updates (not implemented).** Updates can reuse the `revision` field the
profile already carries: a new revision would re-enter the queue, publishing would overwrite
`docs/themes/<id>.json` and bump the index, and the app could compare against the locally installed
revision to offer an update.

## 10. Open questions

- Whether the current 12-applicable-setting originality threshold should be raised or lowered after reviewing
  real submissions. It is intentionally a documented, conservative starting point rather than a
  hidden permanent policy.
- Whether the gallery's default sort is likes, recency, or hand-picked featuring — likely all three
  as tabs, but featuring is what makes a curated store feel curated.
- Whether `MAX_PROFILES` (24) should apply to installed store themes, or whether store themes live in
  a separate bucket from user-authored ones.
- How a user's own submissions are listed back to them across devices (needs a Firestore query by
  UID; cheap, but a screen).
