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

Authenticated users can create and read only their own **pending** intake documents. They cannot
update or delete them; once reviewed, the document stops being readable by its author so the
moderator's Firebase UID is never exposed to them. Creation is tied atomically to a private
`communityThemeSubmissionQuota/<Firebase Auth UID>` document, which the rules require to point at
the exact new intake ID and update its server timestamp. This permits one submission per account
per 24 hours even from a modified client; users can read only their own quota document and cannot
delete it. A deliberately provisioned moderator is the sole browser-side exception: an existing
`communityThemeModerators/<Firebase Auth UID>` document grants that signed-in account permission to
change exactly one **other person's** `pending` intake document to `approved` or `rejected`, adding
its own `reviewedBy` UID and a server timestamp. It cannot edit content, delete, or publish a
document. The Firebase Admin SDK used by the GitHub Action is the only publisher.

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

It covers atomic intake/quota creation, the 24-hour rate limit, owner-only reads, and one-time
third-party moderator decisions. The emulator may download its local JAR on the first run.
