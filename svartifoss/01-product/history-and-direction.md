---
title: History and Direction
tags:
  - svartifoss/product
  - history
summary: The project's lineage, architectural milestones, and intentionally open directions.
---

# History and Direction

Svartifoss is a rename and continuation of **Music Center for Wear** by matejdro. The repository still carries that lineage in capability names, some comments, the upstream Git remote, the WearUtils fork, and one intentionally preserved AIDL package used for Wear Vibration Center interoperability.

## Milestones

| Era | Product and architecture shift |
| --- | --- |
| 1.11 | Watch visual redesign, seek interaction, quick actions, queue/history, and richer playback state |
| 1.12 | Wear foundation modernization and the watch-side proxy media session; Compose queue work begins |
| 2.0 | Svartifoss rebrand, broader action system, Tiles/complication, and renewed phone UI |
| 2.2 | Cable-free update path for the watch and more robust self-update behavior |
| 3.0 | Per-face appearance scopes, richer customization, localization expansion, and performance work |
| 3.1–3.2 | Broader color, typography, surface, queue, and face systems; compatibility and preview-parity hardening |
| 4.0 (unreleased) | Local theme library matured into a moderated public community gallery with account and privacy workflows; ordered background layers, broader panels and face behavior, author screenshots, stronger cross-language schema parity, and continued regression hardening — merged from what had been separate `3.3`/`Unreleased` changelog sections and renamed 4.0, none of it shipped yet |

`CHANGELOG.md` is the user-facing chronology. Commit history and `CLAUDE.md` explain why many apparently small policies exist.

## Modernization direction

The watch app is deliberately hybrid:

- the now-playing host remains a large View-based `MainActivity` because it owns mature input dispatch, overlays, ambient mode, and classic rendering;
- new self-contained watch screens and most newer faces use Compose;
- `WatchMediaSession` already makes phone playback visible to Wear OS system surfaces;
- Tiles and the album-art/title complication are implemented.

New watch UI should generally prefer Compose, but a wholesale rewrite is not assumed to be safer than incremental extraction.

## Deliberate non-directions

- The retired F-Droid repository is not to be revived accidentally.
- The shelved split Play Store listing is not the current product model.
- Spotify Web API/App Remote integration is recorded as a closed assessment under current quota constraints; playlist links and Android media contracts remain the practical path.
- Community-theme updates are not implemented. Installing records provenance so a future revision can be detected, but local edits are never silently overwritten.
- Comments and a live per-card backend were intentionally excluded from the community gallery to keep moderation and cost bounded.

## How to treat roadmap documents

Roadmap filenames are historical. Before assuming a phase is pending, compare the document with source and [Existing documentation](../05-reference/existing-documentation.md). Several planned phases have already shipped.

## Related notes

- [Product overview](product-overview.md)
- [Watch UI and appearance](../02-architecture/watch-ui-and-appearance.md)
- [Existing documentation](../05-reference/existing-documentation.md)
- [Releases and signing](../04-development/releases-and-signing.md)

