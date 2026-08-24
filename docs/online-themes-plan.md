# Online themes — design plan

A curated, community-submitted gallery of watch appearance themes: users publish the profiles they
build in the Watch tab, browse what others published, and apply them on their own watch.

> **Status: parked.** Nothing here is built. Recorded so the design does not have to be re-derived
> from first principles when it is picked up.
>
> **Decisions confirmed (2026-08-23):**
> - Scope is an **open store with a moderation queue** (hundreds of submissions), not a small
>   hand-curated set.
> - Identity is **Google Sign-In via Firebase Auth**, required only to submit or like — never to
>   browse.
> - Approved themes are **hosted in this repository** and served from GitHub Pages. Approval is a
>   commit.
> - **Comments are out of scope.** Likes and theme updates are in.

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

[`WatchThemeRepository.profileToJson`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/WatchThemeRepository.kt#L459)
serializes one profile as a self-contained JSON object with explicitly typed values
(`{"type": "string"|"boolean"|"int", "value": …}`). On the way back in,
[`parseProfile`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/WatchThemeRepository.kt#L497):

- drops every key not in `FaceScopedPreferences.SCOPED_KEYS` (113 keys today),
- rejects any `baseFace` outside `ThemeAppearance.ALLOWED_BASE_FACES`,
- type-checks every value and drops what does not parse,
- and `normalizeProfile` completes anything missing from the base layout.

Two consequences worth stating plainly:

**A theme is data, never code.** The worst a malicious submission can do is look bad. There is no
execution surface, no URL, no file path, no intent — only enumerated preference keys with typed
values. That removes most of the risk that a user-content feature normally carries, and it is why
this is a reasonable feature for a solo developer to host at all.

**Forward/backward compatibility already degrades gracefully.** A theme built on a newer app version
and applied on an older one loses the keys that version does not know and fills them from the base
face. It does not fail. (With one caveat — see §8.)

**Size:** ~113 keys ≈ 6–9 KB of JSON per theme, and it compresses well (highly repetitive). An index
entry is ~200 bytes. At 500 themes the index is ~100 KB — one fetch, ETag-cacheable.

## 3. Architecture

The governing decision is to **split reading from writing.** They have opposite requirements, and
merging them is what would make this expensive to run.

- **Reading** — what essentially every user does — needs no account, no backend, and no quota.
- **Writing** — submitting and liking, both rare — is the only thing that needs identity and a
  server.

The app cannot write to the repository (that is exactly the problem an embedded PAT would create,
see §4), so a bridge is required, and GitHub Actions is the natural place for it:

```
                    ┌──────────────────────────┐
   phone app ──────>│  Firestore (submission   │<────── admin page
   (submit, like)   │  queue + likes)          │        (approve/reject)
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

This would be the repository's **first GitHub Actions workflow** (`.github/` currently holds only
`FUNDING.yml`).

### Layout

```
docs/themes/index.json      # id, name, author, baseFace, revision, schemaVersion, likes, publishedAt
docs/themes/<id>.json       # the full profile body (profileToJson output + store metadata)
```

Shard the index if it ever passes a few thousand entries; at hundreds it is one file.

### Staying on the free (Spark) plan

**Do not use Firebase Storage** — new buckets require the Blaze plan. Put the moderation preview PNG
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

Firebase Auth with Google Sign-In. One tap, every Android user already has an account, free, and the
app already depends on Play Services for the Data Layer.

Sign-in is demanded **only at the moment of submitting or liking**, never for browsing.

It buys the three things the feature needs: authorship, "update my own theme", and survival across a
phone change. (Firebase Anonymous Auth is device-bound and would fail the third.)

**The cost, stated honestly:** the moment identity and user content are stored, obligations appear
that the app does not have today — account deletion, takedown on request, and a real rewrite of
[`privacy-policy.md`](privacy-policy.md) / [`privacy-policy.html`](privacy-policy.html) and the Data
Safety draft. Today the app only emits telemetry; this makes it a host of authored content.

## 6. Moderation at scale

An open queue of hundreds with one human reviewer means **the reviewer is the bottleneck**. The queue
has to be designed to protect that person from the start. Human review is the *last* filter, not the
first.

### Automated gates, before anything reaches the queue

1. **Minimum originality.** Most submissions will be a built-in face with two keys changed. This is
   computable: resolve `FaceScopedPreferences.perFaceDefault` for the submission's `baseFace` and
   count how many of the 113 keys actually differ. Below a threshold, auto-reject with an explanation
   ("change more before submitting"). This is the direct answer to *"anyone could submit and create a
   mess of junk themes"*.
2. **Exact-duplicate detection.** Hash the normalized `settings` map and compare against published
   themes. Two identical themes under different names is the most predictable spam pattern here.
3. **Rate limiting per account.** At most N pending submissions and X per week, enforceable in
   security rules with a per-UID counter document.

Gates 1 and 2 are pure functions over data the app already has — they belong in `common/` as
Android-free functions pinned by JVM tests, following the convention the rest of the project uses for
decisions subtle enough to have caused a bug.

### What is left for a human

After those gates, what actually needs judgement is **the theme name and the author name** — free
text is the real abuse surface, since the theme itself is inert.

### Where review happens

The Firebase Console is not usable for this: no preview grid, no bulk actions, and 113 keys of JSON
tell a reviewer nothing about how a theme looks.

Instead: **a static admin page on GitHub Pages**, gated by Firebase Auth with the author's UID
allowlisted in security rules. A grid of pending previews with approve/reject, writing status back to
Firestore. One HTML file, no backend.

Flow: triage ~20 themes on the page → the Action runs (daily cron or manual dispatch) → it reads
approved-but-unpublished entries, writes `docs/themes/<id>.json`, updates the index, commits.

## 7. Previews

Approving a theme is a **visual** judgement, but shipping preview images has a cost: hundreds of PNGs
committed to the repo grow forever and can never be removed from git history.

**The split that avoids it:** the uploaded preview exists only for moderation — it lives in the
Firestore document, the reviewer looks at it, and the Action discards it on publish. It never enters
the repository. The public gallery **renders each miniature locally on the phone**, since
[`WatchPreviewView`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/WatchPreviewView.kt)
is already exactly that renderer and already lives in `mobile/`.

Three things fall out for free: the repo carries only JSON, the miniature shows the user's own
currently-playing artwork (as the Watch tab already does), and the preview can never disagree with
what the theme actually does.

It needs a "render this profile off-screen to a bitmap" entry point plus a disk cache, with lazy
loading in the list. **This is the one genuine technical risk in the proposal** — whether a grid of
locally-rendered miniatures scrolls acceptably — which is why Phase 1 (§9) is shaped to find out
early.

**Privacy detail:** render the submitted moderation preview with the **built-in sample track**, never
the live one. `WatchPreviewView` shows what is currently playing; without this, every submission
would leak what its author was listening to.

## 8. Things that bite later

**Schema versioning is currently store-hostile.**
[`validateImport`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/WatchThemeRepository.kt#L289)
requires `schemaVersion` to equal `LIBRARY_SCHEMA` exactly, and
[`parseState`](../mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/WatchThemeRepository.kt#L478)
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
default. "Delete my account" must be honest in the privacy policy: it removes the theme from the
catalogue going forward, but git history retains what was already published.

**Likes with a static catalogue.** Likes live in Firestore and are baked into `index.json` on each
rebuild, so displayed counts are slightly stale between publishes. That is fine — show an optimistic
+1 for the viewer's own like. Do not add a live per-theme read; it would turn a free static fetch into
a per-browse quota cost.

**Network stance.** The project is deliberately opt-in about network paths. Gallery browsing follows
the lyrics precedent rather than the shortcut-artwork one: opening the gallery *is* the consent, since
nothing is fetched until a user navigates there. Submission is explicit by construction.

## 9. Phasing

**Phase 1 — read-only gallery, hand-populated.** Static catalogue in `docs/themes/`, a browse screen,
and apply-a-theme. The author populates the catalogue by hand with 15–20 themes. No identity, no
Firebase, no backend, no obligations.

This already delivers the original motivating case ("I like a theme the author made and cannot get
it"), and more importantly it is how the locally-rendered-miniature risk (§7) gets tested before
anything depends on it.

**Phase 2 — submission and moderation.** Firebase Auth, the Firestore queue, the automated gates, the
admin page, and the publishing Action. This is where the privacy policy and Data Safety rewrite
happens. Purely additive to Phase 1.

**Phase 3 — likes and theme updates.** Updates reuse the `revision` field the profile already
carries: a new revision re-enters the queue, publishing overwrites `docs/themes/<id>.json` and bumps
the index, and the app compares against the locally installed revision to offer an update.

## 10. Open questions

- Threshold for the originality gate — how many of 113 keys must differ before a submission is a
  theme rather than a tweak. Needs real submissions to calibrate; start strict, loosen.
- Whether the gallery's default sort is likes, recency, or hand-picked featuring — likely all three
  as tabs, but featuring is what makes a curated store feel curated.
- Whether `MAX_PROFILES` (24) should apply to installed store themes, or whether store themes live in
  a separate bucket from user-authored ones.
- How a user's own submissions are listed back to them across devices (needs a Firestore query by
  UID; cheap, but a screen).
