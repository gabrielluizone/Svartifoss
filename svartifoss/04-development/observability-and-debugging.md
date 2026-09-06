---
title: Observability and Debugging
tags:
  - svartifoss/development
  - debugging
  - observability
summary: Logging architecture, privacy gates, and symptom-driven cross-device diagnosis.
---

# Observability and Debugging

## Logging and diagnostics

The phone uses Timber and Firebase Crashlytics integration under `mobile/.../logging/`. Crash report upload is governed by `CrashReporting`; automatic collection remains disabled until the app has read the current user choice, and opting out deletes queued unsent reports.

The watch uses Timber and WearUtils logging. `MusicCenterLogRequestReceiver`/`LogTransmitter` can send watch logs to the phone over `/SendLogs` and `/Channel/Logs`, allowing watch failures to accompany phone-side diagnostics. Keep log messages useful without including user media content or secrets unnecessarily.

Firebase Analytics and FCM announcements are separate from crash reporting. Announcement subscription is one topic and follows its own preference. Public privacy documentation must reflect all three.

## Diagnose by boundary

| Symptom | First boundaries to inspect |
| --- | --- |
| “No watch connected” | same package ID, signing certificate, paired node/capabilities, Play Services, both APK versions |
| Setting changes only after touching watch | fast preference message receiver, coordinator send order, manifest registration, doze logs |
| Phone preview changes but wrist does not | `EXPORTABLE`, scoped registry, active-scope selection, watch renderer read |
| Watch replays old tracks/themes | transport sequence and stale DataItem filtering |
| Progress/lyrics has a fixed offset | `positionAgeMs`, `PlaybackClock`, old-version fallback, sync RTT/rejection |
| Queue is absent | selected/sibling session queues, player behavior, history fallback |
| Queue tap does nothing | queue-owning controller, encoded IDs, send cancellation, verify/browser fallback |
| Cover missing in some rows | resolver path, media permission, remote-art toggle/network/cache, image byte limits |
| An action icon is invisible or huge | `iconTintable` versus `isCoverArt`, glyph store generation, retransmission |
| Face setting works only on some faces | standard shared helper usage, face applicability, preview/AOD contract tests |
| Tile/complication is stale | durable music DataItem, `MusicStateListenerService`, surface direct reads/refresh |
| Watch opens phone app unnecessarily | deep-link verdict ordering and backstop, direct/browser verification |
| Community submit returns permission denied | client preflight, all four batch docs, Firestore expression budget, rule/schema parity, account kind |
| Imported config crashes later | forced unparcel validation, `.corrupt` quarantine, nested bundle access |

## Useful evidence to capture

- exact phone and watch app version code/name;
- phone Android version and watch Wear OS/API version;
- media app package/version and whether it was already running;
- playing, paused-track, or truly idle state;
- affected Data Layer path and payload sequence/size;
- current face/appearance context;
- whether the event began from a cold process, sleeping watch, Tile, complication, or open app;
- permission and relevant opt-in state;
- reproducible timestamps from each device without assuming their clocks match.

## Device logs

Use Android Studio Logcat or `adb logcat` against the correct device. A phone and watch are separate ADB targets and processes despite sharing an application ID. Filter by process/package and Timber tags, and capture both sides around the same action.

For transport failures, log path, node identity, payload length, sequence, and terminal result—not raw private payload content. For media compatibility, log package/session/capability observations and which fallback was attempted.

## Crash and process-death testing

Explicitly test:

- watch asleep while phone setting changes;
- phone/watch process killed, then restarted independently;
- activity finishing immediately after a selection/send;
- service stopping while shutdown message is in flight;
- old watch with new phone and the reverse where feasible;
- stale/corrupt local config and malformed cached network responses.

## Source anchors

- `mobile/.../logging/TimberCrashlytics.kt`
- `mobile/.../logging/CrashReporting.kt`
- `mobile/.../logging/CrashlyticsExceptionWearHandler.kt`
- `wear/.../communication/MusicCenterLogRequestReceiver.kt`
- `wearutils/` logging classes

## Related notes

- [Entry points](../03-codebase/entry-points.md)
- [Phone-watch communication](../02-architecture/phone-watch-communication.md)
- [Testing strategy](testing-strategy.md)

