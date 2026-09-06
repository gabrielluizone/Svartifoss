---
title: Trust, Privacy, and Distribution
aliases:
  - Privacy and Distribution
tags:
  - svartifoss/product
  - privacy
  - security
summary: The public trust model: local control, optional network paths, community identity, permissions, and distribution.
---

# Trust, Privacy, and Distribution

## Core control is local

Normal phone↔watch operation uses Google Play Services' Wearable Data Layer between paired devices. It does not require a Svartifoss account or a Svartifoss application server. Media state, artwork, configuration, commands, and lists travel over that paired-device channel.

Svartifoss does need Android **notification access** on the phone to observe active media sessions and media-notification actions reliably. Media-library reads are requested in context for local queue artwork and file metadata. Install-package permission is used by the built-in updater. Tasker and Wear Vibration Center integrations have their own optional interop permissions.

## Network surface

Network behavior is concentrated on the phone. Depending on settings and explicit user actions, it includes:

| Path | Purpose | Typical trigger/default |
| --- | --- | --- |
| GitHub Releases API and assets | Check for and download updates | throttled update check; user-controlled |
| GitHub Pages theme catalogue | Browse and install community themes | only when the gallery is used |
| Firebase Authentication and Firestore | community reactions, submissions, reports, moderation-facing account actions | explicit community action; reactions can use silent anonymous identity |
| Firebase Crashlytics | optional crash diagnostics | governed by the privacy preference |
| Firebase Analytics | anonymous usage diagnostics | documented in the maintained privacy policy |
| Firebase Cloud Messaging | occasional developer announcements | topic subscription, independently toggleable |
| Streaming-service oEmbed endpoints | optional shortcut thumbnails | off by default |
| Remote artwork URLs published by a media app | queue row covers | enabled by default because many streaming queues have no local art source |
| LRCLIB | lyrics matched from track metadata | on-demand when a lyrics surface needs it; feature enabled by default |
| MusicBrainz | optional metadata enrichment | off by default |

The maintained legal and behavioral authority is `docs/privacy-policy.md`, manually mirrored into `docs/privacy-policy.html` for the published site. Any new endpoint, permission, identifier, upload, default, or retention change must update both.

## Community trust boundary

Browsing reads static, reviewed JSON from GitHub Pages. Writing is deliberately separate:

- a client writes a constrained intake request to Firestore;
- Firestore rules enforce identity, ownership, quota, shape, and immutable transitions;
- a moderator approves or rejects but cannot publish directly;
- a trusted GitHub Actions publisher validates again, commits public files, and finalizes Firestore state.

A theme is allowlisted typed data, never code. Public profiles cannot carry arbitrary URLs, paths, or intents. Individual voter, installer, and reporter ledgers are private; only aggregate likes and installs are eventually written to the static catalogue. Reports are never published as a count.

The optional author photo is selected through the system photo picker, decoded and re-encoded to strip metadata, constrained in size and format, reviewed, and committed only after approval. Synthetic previews remain the norm for other surfaces.

## Distribution

Svartifoss is currently **sideload-only**. GitHub Releases is the single distribution channel; the old self-hosted F-Droid repository is retired, and Play Console documents in `docs/` are drafts from a shelved attempt.

Initial installation is manual. Later releases can be installed with the built-in updater. Release assets must retain their exact names, and both APKs must remain signed with the same long-lived certificate.

> [!warning] Signing is part of connectivity
> A mismatched certificate is not only an update problem. The Wearable Data Layer routes between matching package identities signed as one application family; the phone and watch can silently stop seeing each other if identity or signing diverges.

## Public commitments

- GPL-3.0 source availability.
- No account for core media control or gallery browsing.
- Explicit, documented reasons for permissions and network calls.
- User controls for crash reports, announcements, and several optional fetches.
- No arbitrary executable content in community themes.

For exact, legally maintained wording, use the published privacy policy rather than this architectural summary.

## Related notes

- [Community themes](../02-architecture/community-themes.md)
- [Storage and caching](../02-architecture/storage-and-caching.md)
- [Observability and debugging](../04-development/observability-and-debugging.md)
- [Existing documentation](../05-reference/existing-documentation.md)

