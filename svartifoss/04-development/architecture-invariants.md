---
title: Architecture Invariants
aliases:
  - Non-Negotiable Rules
tags:
  - svartifoss/development
  - invariants
summary: Cross-device and cross-file rules whose violation commonly compiles but fails silently.
---

# Architecture Invariants

These are not style preferences. Each protects a distributed, persisted, or mirrored boundary where a mistake often compiles and looks correct on one side.

## 1. One application identity

`mobile` and `wear` must keep the same `applicationId`—`com.svartifoss.snfell`—and the same signing certificate. Data Layer routing and in-place upgrades depend on both.

## 2. Phone-owned persistent configuration

The watch may apply UI changes optimistically, but durable settings are stored and republished by the phone. A watch-originated face selection must return to the phone or it will be reverted on the next authoritative sync.

## 3. One snapshot feeds both preference transports

The immediate preference message and durable DataItem use the same filtered snapshot. The message must be attempted independently before the DataItem write. The DataItem owns removal; the message is additive.

## 4. Active appearance is mandatory

Global behavior and the active face/custom scope must be transmitted whole. Inactive scopes are expendable cache within a budget. Theme-size checks must project through the same selector using the prospective custom scope.

## 5. Wire compatibility is additive

Do not rename paths, action IDs, capability names, protobuf field numbers, or stable input codes casually. Add optional fields and explicit codes; preserve legacy fields while independently updated pairs need them. Never serialize enum ordinals.

## 6. Never compare clocks across devices

Send sample age as a duration. The watch extrapolates with its own monotonic clock and measures round trips using its own echoed token. Cross-device wall-clock subtraction creates persistent position and lyric skew.

## 7. Media IDs belong to their source app

Round-trip IDs from the media app that issued them. Do not construct them, reuse them across packages, send a browsable ID to play, or aim a queue ID at a different session.

## 8. Capability flags are evidence, not truth

If an unsupported command safely becomes a no-op, issue and verify it before falling back. Players both under-report supported operations and advertise operations they ignore.

## 9. Shared decisions live in `common`

If phone preview and watch UI must agree, share the resolver, vocabulary, geometry, or primitive. When drawing cannot be shared, enforce source/resource parity. A local magic number is often a future silent divergence.

## 10. A face owns composition, not the settings system

Faces normally consume shared background, palette, typography, progress, and ambient state. A face-specific drawing exception must be deliberate because it may make a phone control inert on one face.

## 11. Foreground-service starts are answered immediately

Every `startForegroundService` route must promote before branch-specific work. Shutdown communication cannot be attached to a lifecycle that the same action immediately destroys.

## 12. Terminal sends outlive their screen

Queue selection, deep-link verdicts, install metrics, and shutdown sends often coincide with `finish()`, `stopSelf()`, or process death. Use the established process/`NonCancellable` owner and a bound, not an activity lifecycle job.

## 13. Public themes are data only

Accept only typed allowlisted values. No arbitrary URL, intent, path, or executable extension belongs in a profile. Revalidate on the phone, Firestore boundary, and trusted publisher.

## 14. Theme schemas move as one system

Scoped definitions, JSON constraints, publisher schema, moderator schema, defaults fixture, numeric ranges, and vocabulary tests must remain aligned. A key or picker value added to only one place makes valid-looking themes fail later.

## 15. Privacy descriptions follow behavior

Any endpoint, upload, permission, identifier, cache retention, or default change may require both `docs/privacy-policy.md` and its hand-maintained HTML twin, plus Data Safety drafts where relevant.

## 16. Locale catalog and resources agree

Every supported locale needs resources in `mobile`, `wear`, and `common`, a modern BCP-47 registry/config entry, and correctly indexed picker arrays. Missing localized array entries shift labels onto wrong values instead of simply falling back.

## 17. Cache lifetime matches ownership

User-owned assets, persistent learned assets, and disposable remote caches need separate directories and eviction/backup policies. Do not put an unbounded cache under a directory traversed by backup.

## 18. Preserve intentional legacy names

`MusicCenterPhone`/`MusicCenterWatch`, stable action class strings, historical preference keys, the `com.matejdro` notification-provider AIDL package, and WearUtils packages are compatibility surface—not unfinished renaming.

## Related notes

- [Phone-watch communication](../02-architecture/phone-watch-communication.md)
- [Preferences and state sync](../02-architecture/preferences-and-state-sync.md)
- [Change playbooks](change-playbooks.md)
- [Source-of-truth matrix](../05-reference/source-of-truth-matrix.md)

