---
title: Existing Documentation
aliases:
  - Documentation Status Matrix
tags:
  - svartifoss/reference
  - documentation
summary: The purpose and current authority level of documentation already present in the repository.
---

# Existing Documentation

## Current operational and public documents

| File | Role | Notes |
| --- | --- | --- |
| `CLAUDE.md` | deep architectural guidance | read before communication, preferences, playback, or watch UI changes; contains current rationale and historical failure modes |
| `AGENTS.md` | concise repository guide | should not contradict the deeper guidance |
| `README.md` | public project/install/build overview | useful, but some counts/community details reflect earlier behavior |
| `CHANGELOG.md` | user-facing release chronology | separates `Unreleased` from versioned sections |
| `docs/index.html` | published GitHub Pages landing page | hand-maintained public feature copy |
| `docs/privacy-policy.md` | maintained privacy-policy source | behavioral/legal authority for public description |
| `docs/privacy-policy.html` | published privacy page | manually synchronized with the Markdown source |
| `docs/ux-information-architecture.md` | phone information architecture | concise product/navigation contract |
| `docs/player-integration-notes.md` | observed media-app behavior | source/read-date-specific; revalidate moving third-party apps |

## Shipped-feature design records

| File | Status |
| --- | --- |
| `docs/online-themes-plan.md` | community-theme design record; most of the feature shipped, theme updates remain unbuilt |
| `docs/theme-screenshots-plan.md` | shipped amendment adding one author Player photo to otherwise synthetic previews |
| `docs/wear-modernization-plan.md` | historical roadmap with many implemented phases: proxy media session, Tiles, complication, Compose screens/faces |
| `docs/spotify-integration-assessment.md` | recorded closed assessment of direct Spotify integration under current quota/access terms |

## Shelved store material

`docs/play-console-*.md` and `fastlane/metadata/` were prepared for a Play Store attempt that was shelved. They are not current distribution or package-identity requirements. In particular, any proposal for a separate watch package conflicts with the live Data Layer identity invariant.

The F-Droid repository formerly under `docs/fdroid/` is retired. GitHub Releases is the active distribution channel.

## Infrastructure documentation

| File/directory | Role |
| --- | --- |
| `firebase/README.md` | emulator/rules orientation; some schema/quota prose may lag executable rules/tests |
| `docs/admin/README.md` | moderator-page setup and operation |
| `.github/community-theme-publisher/` tests/source | executable documentation of trusted publication behavior |
| `firestore.rules` | security behavior, not merely documentation |

## Design and raw references

- `icons/`: source SVGs, not the built Android drawable catalogue.
- `wearmediatemplate/`: Google media-control visual guidance.
- `licenses/`: legally significant font license texts.
- `archived/`: parked artifacts, not current source of truth.
- local third-party source checkouts such as `echo/` and `retromusic/`: research inputs, not Svartifoss modules.

## Known snapshot drift

Before copying older prose, recheck these current-source facts:

- locale registry: 40 including English;
- face registry: 20 recognized, 6 currently archived, 14 normally offered;
- community quota: fixed window, up to 10 submissions per 24 hours;
- community reactions: anonymous Firebase identity can be created silently; Google-linked identity is for author submission/account work;
- latest implemented Wear modernization phases.

## Related notes

- [Source guide](../00-orientation/source-guide.md)
- [Documentation maintenance](../04-development/documentation-maintenance.md)
- [History and direction](../01-product/history-and-direction.md)

