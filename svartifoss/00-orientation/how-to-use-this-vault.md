---
title: How to Use This Vault
aliases:
  - Reading Guide
tags:
  - svartifoss/orientation
summary: How the knowledge base is organized and how to navigate it in Obsidian or GitHub.
---

# How to Use This Vault

This vault is organized as a set of **maps of content** rather than a linear manual. Begin at [Home](../Home.md), enter the area that matches your question, and follow the related-note links at the end of each page.

## The five lenses

- [Product](../01-product/product-map.md) explains observable behavior and user value.
- [Architecture](../02-architecture/architecture-map.md) explains boundaries, ownership, and flows.
- [Codebase](../03-codebase/codebase-map.md) connects those concepts to concrete source locations.
- [Development](../04-development/development-map.md) explains how to change the system safely.
- [Reference](../05-reference/reference-map.md) provides exact lookup tables and authority maps.

The same concept may appear through more than one lens. For example, playlist shortcuts are a feature in the product map, a cross-device action flow in the architecture map, and a set of storage and service classes in the codebase map. Links connect those views instead of duplicating every detail.

## Obsidian features used

The vault works with core Obsidian only:

- Markdown links form the graph and remain valid on GitHub.
- YAML `tags`, `aliases`, and `summary` fields improve search and backlinks.
- Mermaid diagrams show flows without external image files.
- Callouts mark decisions, cautions, and stable mental models.
- `Svartifoss.canvas` provides a spatial overview.

Useful Obsidian actions include **Open linked view**, **Backlinks**, **Local graph**, and Quick Switcher's title search. Search for a source class name, preference key, or Data Layer path when you already have a concrete clue.

## How to read a claim

The vault is an interpretation of the repository, not a replacement for it. Each technical note names the source files that own the behavior. Use [Source guide](source-guide.md) to understand which artifacts are current authorities, maintained public copy, historical design records, or abandoned drafts.

> [!tip] Paths are evidence
> A path in backticks is relative to the repository root. Search it in the source tree; do not prepend `svartifoss/`.

## Vocabulary

Start with the [Glossary](glossary.md) when a note uses terms such as *DataItem*, *appearance scope*, *face*, *custom list*, *proxy media session*, or *active controller*. Those words have specific meanings here.

## Maintaining the vault

When behavior changes, update the smallest authoritative note first, then any map whose summary has become false. The [Documentation maintenance](../04-development/documentation-maintenance.md) note contains a review checklist and a link-validation recipe.

## Related notes

- [Home](../Home.md)
- [Source guide](source-guide.md)
- [Glossary](glossary.md)
- [Existing documentation](../05-reference/existing-documentation.md)

