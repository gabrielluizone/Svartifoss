# Firebase rules

This directory contains the versioned, production-oriented Firestore boundary for community-theme
submissions. It intentionally contains neither a Firebase project ID nor credentials.

## themeIntake contract

The app may create one immutable document at:

~~~
themeIntake/{lower-case RFC 4122 UUID}
~~~

Every create must include exactly these fields:

| Field | Constraint |
| --- | --- |
| ownerUid | Firebase Auth UID of the caller |
| status | Literal "pending" |
| submissionSchemaVersion | Integer 1 |
| name, author | Non-empty text, at most 48 characters each |
| baseFace | One currently supported, non-archived face |
| profileSchemaVersion, revision | Integer 1 |
| profileJson | Serialized public WatchThemeProfile body, 2–24,576 characters |
| settingsDigest | sha256: followed by 64 lower-case hexadecimal digits |
| moderationPreviewWebpBase64 | Base64 WebP moderation preview, 4–65,536 characters |
| clientVersion | Non-empty app version text, at most 64 characters |
| createdAt | Firestore serverTimestamp() |

profileJson is deliberately bounded JSON text rather than a free-form Firestore map. Before a
submission is approved, a trusted publisher must parse it with
WatchThemeRepository.parsePublishedProfile, verify that its id and metadata match the document,
and recompute settingsDigest. The rules cap the intake envelope; they do not treat client-supplied
JSON or a client-supplied digest as trusted publication data.

Every rule in this section requires an *identified* account — one whose token either reports a
non-anonymous `sign_in_provider` or carries a linked `google.com` identity. The second half of that
test is load-bearing: linking Google onto an anonymous account leaves `sign_in_provider` reporting
`"anonymous"` until it signs in afresh, so checking the provider alone would refuse submissions from
exactly the people who liked a theme before submitting one.

Authenticated users can create and read only their own **pending** intake documents. They cannot
update or delete them; once reviewed, the document stops being readable by its author so the
moderator's Firebase UID is never exposed to them. A deliberately provisioned moderator is the sole browser-side exception: an existing
`communityThemeModerators/<Firebase Auth UID>` document grants that signed-in account permission to
change exactly one **other person's** `pending` intake document to `approved` or `rejected`, adding
its own `reviewedBy` UID and a server timestamp. It cannot edit content, delete, or publish a
document. The Firebase Admin SDK used by the GitHub Action is the only publisher.

## Submission quota contract

Creation is tied atomically to a private
`communityThemeSubmissionQuota/<Firebase Auth UID>` document, which rules require to point at the
exact new intake ID and use Firestore server timestamps. Version 2 has this shape:

| Field | Constraint / purpose |
| --- | --- |
| ownerUid | Firebase Auth UID of the caller |
| quotaSchemaVersion | Integer `2` |
| submissionCount | Monotonic all-time count |
| lastSubmissionAt, lastSubmissionId | Newest linked intake submission |
| recentSubmissionCount | Integer 1–3 |
| recentSubmissionFirstAt | Oldest retained timestamp in the rolling window |
| recentSubmissionSecondAt | Present only once the retained history has three submissions |

The scalar timestamp history permits **at most three submissions in any rolling 24-hour window**.
When a history already holds three, the next create can shift it only after the oldest timestamp is
at least 24 hours old. A modified client cannot skip, reset, or delete the quota document; users can
read only their own. The former four-field quota shape is accepted only as the before-state of one
migration write, which starts a fresh version-2 history and cannot be used to reset an existing one.

## Likes contract

One private reaction for a published theme lives at:

~~~
communityThemeLikes/{lower-case RFC 4122 UUID}/voters/{Firebase Auth UID}
~~~

It contains exactly `schemaVersion: 1` and `createdAt: serverTimestamp()`. A signed-in caller can
create, read, or delete only their own document. They cannot update it, list voters, read another
person's vote, or write a public counter. The rules accept a new vote only after the theme has
crossed the Git publication boundary.

**These are the only rules that accept an anonymous account.** The app never asks anyone to sign in
to react, so it provisions an anonymous Firebase Auth UID silently on the first like. **Anonymous
authentication must therefore be enabled** in the Firebase project (Authentication → Sign-in
method → Anonymous); without it every like fails at sign-in, not at the rules. The accepted cost is
that clearing app data yields a new UID and permits a repeat vote — the ledger is a popularity
signal, not a ballot. Everything under `themeIntake` and `communityThemeSubmissionQuota` demands
`identifiedUser()` instead, because a quota measured in free, disposable accounts measures nothing.

The trusted publisher counts these documents with Firestore's aggregate query and writes the result
as `likes` in the static `docs/themes/index.json` catalogue. That makes the public total and
most-liked ordering eventually consistent with reactions, while browsing remains a single static
Pages request. `communityThemePublished/{themeId}` is an unreadable, server-only marker reconciled
after the Git push so hand-authored static seed themes can receive the same private likes.

Create the moderator allowlist document only in the Firebase Console (which uses administrative
credentials), after the intended reviewer signs in once and you know their Firebase Auth UID. Do
not add any client rule for `communityThemeModerators`: its contents are intentionally unreadable
and unwritable to the app and the public web page.

## Deployment

Review these rules together with the app's Firestore writer before deploying to production. The
repository deliberately does not select a Firebase project; choose it explicitly:

~~~
firebase --project YOUR_PROJECT_ID deploy --only firestore:rules
~~~

Or, in Firebase Console, open **Databases & Storage** → **Firestore** → **Rules**, replace the
editor contents with `firestore.rules`, then select **Publish**. The Console route is useful for
the first setup; keep the checked-in file as the source of truth and copy later changes back into
it before committing.

Do not deploy Firestore's test-mode rules. The included moderator exception is deliberately narrow;
the author-only client boundary remains in place for all ordinary app users.

## Local rule tests

The behavioral suite runs against a disposable Firestore Emulator project; it never contacts the
production Firebase project. From the repository root, with JDK 21 available:

~~~
npm ci --prefix firebase
npm test --prefix firebase
~~~

It covers atomic intake/quota creation, the three-per-rolling-24-hour limit and migration, owner-only
reads, one-time third-party moderator decisions, private one-vote likes, and the anonymous/identified
split: that an anonymous account can react but cannot submit, and that linking Google onto one
restores submission access. The emulator may download its local JAR on the first run.

Note how the account kinds are built in the suite. `authenticatedContext(uid)` already produces a
token whose `firebase.sign_in_provider` is `"custom"`, which satisfies `identifiedUser()` without
being spelled out; an anonymous caller has to override that claim explicitly. A test that gets this
wrong does not fail loudly — it simply exercises the wrong account kind.
