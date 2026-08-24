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
