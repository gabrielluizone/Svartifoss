---
title: Glossary
tags:
  - svartifoss/orientation
  - reference
summary: Project-specific vocabulary used throughout the Svartifoss knowledge base.
---

# Glossary

**Action**  
A phone-side `PhoneAction` selected for a button, gesture, menu row, or shortcut. Most actions execute on the phone; screen-opening actions may be intercepted and performed locally by the watch.

**Action configuration**  
A mapping from `ButtonInfo`—physical or pseudo button plus gesture—to a serialized action. Svartifoss keeps separate configurations for *music playing* and *no playback*.

**Active media session / active controller**  
The Android `MediaSession` currently selected by the phone as the source of playback state. Several sessions may exist for one app, and the session that is playing may not be the one that publishes a queue.

**Appearance context**  
The resolved styling context: either a built-in face scope or the fixed `custom_active` snapshot used by an applied saved theme.

**Appearance scope**  
The suffix attached to a face-scoped preference, such as `key@classic`. Scoping lets each now-playing face retain independent appearance choices.

**Built-in face**  
A renderer identified by a stable key in `ThemeAppearance.ALLOWED_BASE_FACES`. A face owns composition; shared preferences own treatments such as typography, colors, background layers, and controls.

**Community theme**  
A validated appearance profile published through a moderated pipeline. It contains typed, allowlisted data—not executable code, file paths, intents, or arbitrary URLs.

**Custom list**  
A generic protobuf list displayed on the watch. Queue pages, history, search results, playlist shortcuts, search history, and media-library pages reuse this transport shape.

**DataItem**  
A durable Wearable Data Layer record replicated between paired devices. It can carry assets and survive process death, but delivery can lag.

**Data Layer**  
Google Play Services' local phone↔watch communication system. Svartifoss uses its `DataClient`, `MessageClient`, `ChannelClient`, capabilities, and nodes.

**Face**  
An in-app now-playing layout. It is not an Android/Wear OS system watch face. The term is retained throughout the code and UI.

**Idle message**  
A phone→watch message under `/IdleMessages/` that a manifest listener may receive even while the main watch UI is not running.

**Media action**  
An action mirrored from the active media notification or, as a fallback, the media session. A phone-local opaque ID remains the execution token; semantic metadata is presentation-only.

**Message**  
An immediate, transient Data Layer payload. It is appropriate for commands and time-sensitive responses, but it has no durable replay guarantee and cannot carry Data Layer assets.

**Phone authority**  
The design rule that persistent configuration, media access, most action execution, and network work belong to the phone. Watch-local optimistic changes must converge back through the phone.

**Player / media app**  
The third-party Android app that owns the actual audio and media session: for example a local player or streaming client. Svartifoss controls it through Android platform contracts.

**Proxy media session**  
The watch-side `WatchMediaSession`. It mirrors phone state and forwards transport controls so Wear OS system media surfaces can interact with the phone's playback.

**Quick actions panel**  
The overlay opened from the now-playing screen, normally by a center double tap or an assigned action. Its three round slots and one long row are pseudo buttons in the normal action pipeline.

**Scoped preference**  
An appearance key whose persisted value is qualified by face. Behavior preferences generally remain global.

**Transport sequence**  
A monotonic value attached to state or preference snapshots so an older payload replayed later cannot overwrite newer state.

**WearUtils**  
The `wearutils/` Git submodule, a project fork of WearUtils. It supplies shared watch/phone utilities including preference transport, logging, task integration, and companion support.

## Related notes

- [Product overview](../01-product/product-overview.md)
- [System architecture](../02-architecture/system-architecture.md)
- [Communication contracts](../05-reference/communication-contracts.md)
- [Preference domains](../05-reference/preference-domains.md)

