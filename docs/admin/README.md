# Community-theme moderation page

`docs/admin/` is a static GitHub Pages page. It has no privileged key: Firebase Authentication
identifies the reviewer and the Firestore rules decide whether that Firebase Auth UID is a moderator.
The page can only make one `pending` → `approved` or `rejected` decision. Publishing is still a
separate GitHub Action using a service account.

## One-time Firebase setup

1. In Firebase Console, open **Project settings** (the gear next to Project Overview), then under
   **Your apps** choose **Add app** → **Web** (`</>`). Register a web app for the moderation page;
   it does not need Firebase Hosting.
2. Copy that web app's configuration object into `firebase-config.js`. This configuration is public
   client metadata. Do **not** put a service-account JSON or any private key in this file.
3. In **Security** → **Authentication** → **Sign-in method**, enable **Google**. Under
   **Settings** →
   **Authorized domains**, add the exact GitHub Pages host (for example `gabrielluizone.github.io`)
   and any local development host you use.
4. Deploy the checked-in `firestore.rules` using the command in `firebase/README.md`.
5. Open the deployed `/admin/` page once and sign in. The header displays the Firebase Auth UID.
   In **Databases & Storage** → **Firestore** → **Data**, create a document at
   `communityThemeModerators/<that exact UID>` with any harmless field such as
   `createdAt: <current timestamp>`. The Firebase Console uses administrative credentials, so it
   can create this allowlist document even though browser clients cannot.
6. Reload the page. It can now read pending submissions and make a one-time decision for each.

The Firebase Web config API key identifies the project but does not grant database access. The
rules and Auth domain configuration above are what protect the page.

## Operational flow

1. A user submits a theme from the Android app; it appears as `pending`.
2. The moderator checks the client-supplied WebP as a visual aid, the public name/pseudonym, and
   the browser's profile JSON/digest check, then approves or rejects it. The bitmap is not proof
   of the JSON; the trusted publisher does the final full schema validation.
3. The publication workflow reads only `approved` documents, validates them again with its trusted
   service account, commits approved profiles to `docs/themes/`, and marks them `published`.

Never use the Firebase Console to hand-change a queued theme's status: doing so skips the page's
reviewer audit fields and can create a document the publisher deliberately ignores.

## What the page can do

It lists submissions by status — pending, approved, published, rejected, withdrawn, or all — rather
than only the pending queue, so an approval made by mistake is visible instead of disappearing
until the next publication run.

Four actions, each written as one atomic batch that updates `themeIntake` and records who acted in
`themeIntakeReview`. The rules verify the pair with `getAfter`, so neither write is accepted alone:

- **Approve / Reject** a pending submission. Approve stays disabled until the browser's own payload
  check has re-parsed the profile and recomputed its fingerprint.
- **Reopen for review** an approved or rejected one, which is the way back from a mistaken verdict
  while the theme is still not public.
- **Withdraw / Delete**, available in every state including on a moderator's own theme. It only
  sets the status; the publisher removes the file, the catalogue entry and the likes on its next
  run, then deletes the record. A listing nobody can take down would be worse than one its own
  author could also remove, which is why the self-moderation ban does not extend to this.
- **Correct public name or author**, available only before the theme is public. Once the file is
  committed to Git under the old text, changing the record alone would leave the two disagreeing
  with nothing to notice it; withdraw and resubmit instead.

A moderator can never publish, and can never decide or reopen their own submission.
