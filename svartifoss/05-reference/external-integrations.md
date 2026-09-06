---
title: External Integrations
tags:
  - svartifoss/reference
  - integrations
summary: Platform, media-player, web-service, Firebase, update, Tasker, and notification interoperability boundaries.
---

# External Integrations

## Android media platform

Svartifoss primarily integrates through:

- `MediaSession`/`MediaController` for state and transport;
- `MediaBrowserService` for background playback, search, and library browsing;
- `NotificationListenerService` for active notifications and real media action `PendingIntent`s;
- `AudioManager` media keys and volume as fallbacks;
- MediaStore/local content reads for artwork and technical metadata.

Player behavior varies. The repository's evidence-based compatibility record is `docs/player-integration-notes.md`, covering observed Retro Music, Echo, InnerTune, and SoundCloud behavior. Recheck moving players rather than generalizing one app's IDs or capability flags.

## Wear OS and Google Play Services

- Wearable Data Layer: messages, DataItems/assets, channels, nodes, and capabilities.
- Wearable Buttons and rotary input APIs.
- Wear primary semantic gesture API on API 36.1+ with hardware/user-setting probes.
- Wear Tiles/ProtoLayout and watch-face complication data source APIs.
- Ongoing Activity and a local `MediaSessionCompat` proxy.
- Remote activity opening for phone deep-link fallback.

## Streaming link integrations

The shortcut parser recognizes the following provider-owned link families and can target their official Android packages when installed:

| Service | Recognized links | Android package |
|---|---|---|
| YouTube Music | `music.youtube.com`, `youtube.com`, `youtu.be` | `com.google.android.apps.youtube.music` |
| Spotify | `spotify:`, `*.spotify.com`, `*.spotify.link` | `com.spotify.music` |
| Deezer | `deezer:`, `*.deezer.com`, `*.deezer.page.link` | `deezer.android.app` |
| TIDAL | `tidal:`, `*.tidal.com` | `com.aspiro.tidal` |
| Apple Music | `musics:`, `music.apple.com` | `com.apple.android.music` |
| Amazon Music | `amzn-music:`, `music.amazon.*`, `amazon.com/music` | `com.amazon.mp3` |
| SoundCloud | `soundcloud:`, `*.soundcloud.com` | `com.soundcloud.android` |
| Qobuz | `*.qobuz.com` | `com.qobuz.music` |
| Bandcamp | `*.bandcamp.com` | `com.bandcamp.android` |
| Audiomack | `*.audiomack.com` | `com.audiomack` |
| Mixcloud | `*.mixcloud.com` | `com.mixcloud.player` |
| Pandora | `*.pandora.com`, `*.pandora.app.link` | `com.pandora.android` |

Everything else uses the generic safe-URI fallback. Host matching is boundary-aware, so a lookalike such as `bandcamp.com.example.org` is not treated as Bandcamp.

These are installed-app/media-contract integrations, not OAuth account or direct catalogue APIs. Recognition means Svartifoss can classify the share link and try the right Android app; it does not promise that a provider will accept background autoplay. The target app, account tier, region, and link type still control the final behavior. Optional thumbnails use public oEmbed-style endpoints where supported.

The built-in account routes are deliberately narrower than the provider table. Only stable, account-independent destinations are shipped: YouTube Music Liked Music, Spotify Liked Songs, SoundCloud Likes, and Deezer Flow. A Qobuz, Bandcamp, Audiomack, Mixcloud, Pandora, Apple Music, Amazon Music, or TIDAL personal collection is added as a normal shared shortcut because its content identifier belongs to that user or may vary by region.

### Verification for the additional providers

The five added mappings are grounded in provider-owned link documentation and official Android listings:

- **Qobuz:** [playlist sharing](https://help.qobuz.com/en/articles/10144-how-can-i-share-a-playlist) and [Android app](https://play.google.com/store/apps/details?id=com.qobuz.music);
- **Bandcamp:** [public playlist links](https://get.bandcamp.help/en/articles/15263059-playlists-on-the-web-beta) and [Android app](https://play.google.com/store/apps/details?id=com.bandcamp.android);
- **Audiomack:** [official entity/slug URL model](https://audiomack.com/data-api/docs) and [Android app](https://play.google.com/store/apps/details?id=com.audiomack);
- **Mixcloud:** [sharing shows, tracks, profiles, and playlists](https://help.mixcloud.com/hc/en-us/articles/360004031440-Embedding-content-using-the-Mixcloud-widget) and [Android app](https://play.google.com/store/apps/details?id=com.mixcloud.player);
- **Pandora:** [mobile content sharing](https://help.pandora.com/s/article/Sharing-on-Pandora-1519949305261) and [Android app](https://play.google.com/store/apps/details?id=com.pandora.android).

Deezer documents Flow as its account-wide infinite personalized soundtrack in the [official Deezer newsroom](https://newsroom-deezer.com/2026/02/deezer-launches-flow-tuner-personalized-recommendations/). The built-in action uses Deezer's own `/flow` route and still passes through the same verified playback/fallback ladder as a saved link.

Spotify's direct Web API/App Remote option is documented in `docs/spotify-integration-assessment.md`; current quota/access constraints make it unsuitable as the general open-source path.

## Lyrics and metadata

- **LRCLIB:** metadata-based synced/plain lyric lookup; phone-only, on demand, in-memory cache.
- **MusicBrainz:** optional fuzzy enrichment for release/identifier data; off by default.
- **Remote artwork URLs:** supplied by the media app for queue covers; bounded phone fetch and disk cache.

## Firebase

The phone uses Firebase products for distinct purposes:

- Crashlytics for optional crash reports;
- Analytics for documented anonymous diagnostics;
- Cloud Messaging for opt-out developer announcements through a topic;
- Authentication for anonymous community reactions and Google-linked authors;
- Firestore for theme intake, reviews, quotas, private reaction/install/report ledgers, and account-erasure requests.

The static public community catalogue is not served from Firestore.

## GitHub

- GitHub Releases is the APK distribution and update source.
- GitHub Pages serves the landing page, privacy policy, moderator page shell, and static theme catalogue.
- GitHub Actions runs the trusted theme publisher on a schedule or manual dispatch.

Exact APK asset names are part of the updater contract.

## Tasker

If Tasker is installed, the action picker exposes tasks through WearUtils/Tasker integration. The phone manifest declares the relevant package visibility and permission. Task execution stays on the phone.

## Wear Vibration Center

The phone exposes a functioning notification-provider AIDL interoperability subsystem to the separate Wear Vibration Center application. Its `com.matejdro.wearvibrationcenter.notificationprovider` package is intentionally preserved as the external binding contract.

## Package Installer

Phone and watch self-updates use Android `PackageInstaller` sessions rather than generic file-view intents. Both validate the APK package before commit and surface required user confirmation/unknown-app permission through explicit UI state.

## Privacy rule

Every integration should be mapped to its trigger, identifier/payload, storage, retention, failure behavior, and user control in the maintained privacy policy. See [Trust, privacy, and distribution](../01-product/trust-privacy-and-distribution.md).

## Related notes

- [Content features](../02-architecture/content-features.md)
- [Community themes](../02-architecture/community-themes.md)
- [Observability and debugging](../04-development/observability-and-debugging.md)
