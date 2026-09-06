---
title: Product Overview
aliases:
  - What Is Svartifoss
tags:
  - svartifoss/product
summary: A public explanation of Svartifoss, its users, value, and boundaries.
---

# Product Overview

Svartifoss is an open-source Android companion for people who want richer control of phone playback from a Wear OS watch. It reads the media session already published by a music, podcast, audiobook, or other media app; mirrors title, artist, artwork, playback state, position, queue, and available actions; and lets the watch send commands back.

Its differentiator is not merely remote play/pause. The watch becomes a configurable input surface: physical buttons, screen regions, swipes, the digital crown or touch bezel, on-screen mini buttons, the quick-actions panel, and supported one-handed gestures can be mapped to actions. The presentation layer is equally configurable through in-app now-playing faces, typography, album-derived color, artwork treatments, background stacks, progress treatments, panels, and always-on variants.

## Who it is for

- Wear OS users who want physical or eyes-free media control.
- Users whose preferred media app exposes a standard Android media session but has limited watch support.
- Tinkerers who want per-face appearance and control mappings.
- Open-source contributors interested in Android media interoperability, cross-device synchronization, or round-screen UI.

## The two-app product

The phone and watch APKs are peers with different responsibilities:

- The **phone app** requests notification access, observes active media sessions, executes actions, stores configuration, performs network requests, manages themes, and sends state to the watch.
- The **watch app** captures wrist input, renders now-playing and secondary screens, exposes Tiles and a complication, and mirrors playback through a local proxy media session.

They communicate through the Wearable Data Layer and must share both the `com.svartifoss.snfell` application ID and signing certificate.

## Compatibility model

Android's media APIs are optional in practice. A player may expose transport controls but no queue, advertise a capability it ignores, implement a command without advertising it, or publish several media sessions with different data. Svartifoss therefore uses layered fallbacks:

- notification actions before generic session custom actions;
- the playing session before a sibling session that owns the queue;
- a live queue before recent-track history;
- direct media-session playback before `MediaBrowserService` and visible deep-link fallback;
- app metadata before local-file inspection and optional MusicBrainz enrichment.

The result is broad compatibility, not a promise that every feature is available with every player.

## What it does not do

- It does not play, download, or stream audio itself.
- It does not require a Svartifoss cloud account for ordinary control.
- It does not make arbitrary third-party media IDs portable between apps.
- Its “faces” are now-playing layouts inside the app, not Wear OS watch-face packages.
- It is not currently distributed through Google Play or F-Droid.
- Playlist links are shortcuts into installed services, not direct Spotify, YouTube Music, or other account/API integrations.

## Ownership model

The phone is authoritative for persistent state. The watch can perform local work—open a screen, update a progress anchor, apply a face choice immediately—but any setting that must survive reconnection is sent back to the phone and redistributed through the normal synchronization path.

This ownership rule is the foundation for [System architecture](../02-architecture/system-architecture.md) and [Preferences and state sync](../02-architecture/preferences-and-state-sync.md).

## Related notes

- [Feature map](feature-map.md)
- [User journeys](user-journeys.md)
- [Trust, privacy, and distribution](trust-privacy-and-distribution.md)
- [Playback and media sessions](../02-architecture/playback-and-media-sessions.md)

