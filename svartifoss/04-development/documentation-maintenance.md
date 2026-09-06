---
title: Documentation Maintenance
tags:
  - svartifoss/development
  - documentation
summary: How to keep public copy, design records, privacy pages, code guidance, and this vault synchronized.
---

# Documentation Maintenance

Documentation is part of Svartifoss's product and compatibility surface. The repository contains current guidance, generated/public infrastructure, historical plans, and shelved drafts; updates should preserve those distinctions.

## Update by change type

| Change | Documentation to review |
| --- | --- |
| user-visible behavior | `CHANGELOG.md`, `README.md`, `docs/index.html`, relevant vault product/architecture note |
| network, privacy, account, upload, permission, or default | `docs/privacy-policy.md` **and** `docs/privacy-policy.html`; relevant Data Safety drafts; trust/community notes |
| module, build, test, or architectural invariant | `CLAUDE.md`, `AGENTS.md` if its summary would conflict, and codebase/development vault notes |
| face added/archived | source registry, picker copy, README/landing counts and names, vault face catalogue |
| release | version fields, `CHANGELOG.md`, release notes/assets; snapshot values where intentionally recorded |
| community pipeline | design records, moderator/publisher READMEs, rules docs, community architecture and authority matrix |
| localization | public language count, locale registry, localization note |

## Privacy Markdown and HTML

`docs/privacy-policy.md` is the maintained source. `docs/privacy-policy.html` is a manually styled transcription served publicly; it is not generated. Any semantic edit must be copied into the HTML and both “Last updated” dates must match.

## Plans versus present tense

- Preserve design records as explanations of decisions.
- Add clear status/amendment blocks rather than rewriting history into a fictional original plan.
- Do not cite `docs/play-console-*.md` or `fastlane/` as current distribution.
- Check current code before repeating counts, quotas, defaults, versions, or feature availability.

## Maintaining this vault

1. Update the narrow note that owns the concept.
2. Check backlinks/maps whose summary might now be false.
3. Keep source paths repository-relative and avoid private/local paths.
4. Preserve public neutrality: explain the actual distribution/state without copying informal criticism from old store copy.
5. Change `snapshot` language when updating time-sensitive values.
6. Validate all Markdown links and the Canvas JSON.

One simple local link check can parse Markdown targets, ignore web URLs/anchors, resolve them relative to each note, and report missing files. Also search for unexpanded wiki links if the vault standard remains ordinary Markdown:

```sh
rg -n '\[\x5B[^]]+\]\]' svartifoss
```

No result is expected unless the vault intentionally adopts wiki links later. Standard Markdown was chosen so the public folder renders correctly on both Obsidian and GitHub.

## Current drift worth knowing

At this snapshot, some older public/repository prose still reflects:

- a historical 14-language count rather than the 40-locale registry;
- 20 “built-in” faces without distinguishing 6 archived renderers;
- earlier community-like identity and three-submission rolling-limit behavior rather than current anonymous reactions and a fixed ten-per-24-hour window;
- roadmap phases that have already been implemented.

This vault states current source behavior and flags its snapshot instead of silently reproducing those claims.

## Related notes

- [Source guide](../00-orientation/source-guide.md)
- [Existing documentation](../05-reference/existing-documentation.md)
- [Source-of-truth matrix](../05-reference/source-of-truth-matrix.md)
