# Community-theme publisher

This is the trusted half of the community-theme queue. It reads only Firestore intake documents
whose status is `approved`, revalidates the complete Android profile schema and writes the static
GitHub Pages catalogue under `docs/themes/`.

It accepts no project ID, key file or application credential. The only Firebase credential is the
JSON service-account value in `FIREBASE_SERVICE_ACCOUNT`. In GitHub, create a repository secret
with exactly that name and paste the full service-account JSON into it. Give that service account
the minimum Firestore access needed to read `themeIntake` and update its status. Do not add the
JSON file to the repository or to an APK.

## Deliberate execution modes

The default is a dry run. It does Firestore reads and all validation, but writes neither local files
nor Firestore documents:

```sh
node .github/community-theme-publisher/publisher.mjs
```

Publishing needs an explicit flag and a temporary manifest path:

```sh
node .github/community-theme-publisher/publisher.mjs \
  --publish --manifest /tmp/community-theme-publication.json
```

That first phase atomically creates new profile files without overwriting an existing ID and
atomically replaces the index only after all profile writes succeed. It does **not** mark Firestore
yet. The workflow commits and pushes the files first, then runs:

```sh
node .github/community-theme-publisher/publisher.mjs \
  --finalize /tmp/community-theme-publication.json
```

Finalization rechecks the committed profile and index against the still-approved Firestore document
inside a transaction, then changes only matching documents to `published` with a server timestamp.
This two-phase order means a failed Git push cannot make Firestore claim that a theme is public.

## Validation contract

The publisher is intentionally stricter than the UI:

- document and profile IDs must be lower-case RFC UUIDs and agree;
- names and pseudonyms are normalized, control-free strings of at most 48 UTF-16 code units;
- only current non-archived base faces and the current schema/revision are accepted;
- `profileJson` must contain exactly the current complete typed settings map;
- every setting must also satisfy the shared semantic contract in
  `common/src/main/assets/community-theme-constraints.json`: current selector values only,
  renderer-safe integer ranges, and either an allowed enum or an empty/canonical `#RRGGBB` color;
- its `sha256:` fingerprint is recomputed with the byte format in
  `CommunityThemeSubmissionPolicy`, compared against every published Pages profile and against
  the other approved documents in the same run;
- the profile must differ from its shipped base-face defaults in at least 12 *applicable visual*
  settings. Face-only controls, obsolete migration fields, and values hidden by the complete
  snapshot's own state do not inflate that count; and
- moderation preview bytes are validated as queue data but are never copied into Git.

`schema.mjs` mirrors `FaceScopedPreferences.SCOPED_DEFINITIONS`. When a public appearance setting
is added, its type changes, or its default changes, update that file and the shared constraints
asset in the same change. The Android public-profile parser consumes the same asset. The publisher
has one deliberately narrow legacy-read exception for an already trusted, partial Phase-1 Pages
seed; it is never accepted from an intake document, a complete public candidate, digest input, or
the originality calculation. Failing closed is preferable to publishing a profile that a release
cannot parse or incorrectly calling two themes distinct.

Git and Firestore cannot form one distributed transaction. The workflow is therefore idempotent:
if it writes/pushes the files but loses the final Firestore update, the next run recognizes the
identical static profile and only finalizes the intake status. A malformed, low-originality or
duplicate approval is terminally rejected by the trusted publisher in a Firestore transaction with
a bounded `publicationFailure` code, `rejectedBy: "community-theme-publisher"`, and a server
timestamp. Filesystem, Git, Firebase and catalogue-read failures instead fail the workflow without
changing the intake, so transient infrastructure errors are never mistaken for a user rejection.

One run contains at most 1,000 eligible publications, matching the finalization manifest limit.
When more are approved, it takes the first 1,000 in stable document-ID order and leaves the
remaining valid documents approved for the next scheduled run. Deferred documents never appear in
the index or a profile file before they are included in a manifest.
