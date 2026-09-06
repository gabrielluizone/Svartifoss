---
title: Svartifoss Second Brain
aliases:
  - Svartifoss Knowledge Base
tags:
  - svartifoss
  - documentation
summary: A public, source-grounded knowledge base for understanding the Svartifoss product and codebase.
---

# Svartifoss Second Brain

This folder is a public, self-contained knowledge base for **Svartifoss**: the Android and Wear OS system that turns a watch into a deeply customizable controller for media playing on a phone.

The shortest route in is [Home](Home.md). It gives you the system in one screen and branches into product, architecture, codebase, development, and reference maps.

## Open it in Obsidian

1. Install [Obsidian](https://obsidian.md/).
2. Choose **Open folder as vault**.
3. Select this `svartifoss/` directory, not the repository root.
4. Open `Home.md`, or open `Svartifoss.canvas` for the visual map.

No community plugins are required. The vault uses standard Markdown links, YAML frontmatter, tables, callouts, and Mermaid diagrams. Those notes remain readable on GitHub and in ordinary text editors.

## What is inside

| Area | What it answers |
| --- | --- |
| [Orientation](00-orientation/how-to-use-this-vault.md) | How to read the vault and judge the authority of a claim |
| [Product](01-product/product-map.md) | What Svartifoss is, who it serves, and what users can do |
| [Architecture](02-architecture/architecture-map.md) | How the phone, watch, shared contracts, storage, and services cooperate |
| [Codebase](03-codebase/codebase-map.md) | Where responsibilities live in the repository |
| [Development](04-development/development-map.md) | How to build, test, change, debug, localize, and release it safely |
| [Reference](05-reference/reference-map.md) | Protocol paths, protobuf models, authorities, and document status |

## Scope and accuracy

This vault describes the checked-out source tree as reviewed on **2026-09-04**. Build metadata currently identifies both apps as version `4.0`; the working source may include changes intended for a later release. Where release copy and source code differ, the notes call out the distinction and follow the executable source and its tests.

The material is intentionally public-facing:

- it explains architecture and operational boundaries without copying credentials or local secrets;
- it distinguishes current behavior from historical plans and abandoned distribution drafts;
- it gives repository-relative source paths so claims can be checked against code;
- it records invariants and reasons, not only directory names.

The project is licensed under GPL-3.0; see the repository's `COPYING` file.

## A note on code links

Paths such as `mobile/src/main/java/com/svartifoss/snfell/music/MusicService.kt` are relative to the repository root. They are written as code paths because an Obsidian vault cannot safely navigate to files outside its own root. Use your editor or repository browser to open them.

Some explanatory notes shorten a repeated package prefix to forms such as `mobile/...`, `wear/...`, or `common/...`. The [Source Index](05-reference/source-index.md) provides full repository-relative paths for direct lookup.
