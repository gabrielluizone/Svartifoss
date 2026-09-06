# Phone/watch communication latency investigation

The implementation contained ordering, duplicate-work and startup defects that can make the watch feel delayed even when the transport is healthy. The changes below address those concrete paths. No paired device was attached during this investigation, so this is a source and regression-test analysis, not a measured Bluetooth latency benchmark.

## Paths and findings

| Path | Existing behavior and confirmed defect | Change |
| --- | --- | --- |
| Phone settings → watch | `WatchPreferenceSyncCoordinator` already had a 120 ms trailing debounce and one pending request. However, it awaited the message and then the durable put in one worker. A slow task blocked the next snapshot on both transports. Continuing edits could also keep postponing the trailing debounce. | Retain the 120 ms transaction-coalescing allowance, bounded from the first edit. Give each transport one serial worker and one replaceable pending snapshot. Skip equal selected settings. |
| Settings receive order | `PreferenceMessageReceiver` and wearutils' `PreferenceReceiverService` used independent sequences and state files. Message B could apply, then durable A could overwrite it despite each receiver's own stale check. Both copies also committed and refreshed appearance independently. | One shared sequence, reconciliation policy and receiver lock. Atomically commit values and ordering/ownership metadata; publish the UI bus only for changed values. |
| Developer settings refresh | `MiscSettingsFragment.forceSyncWatchSettings` directly pushed the entire default preference file through `PreferencePusher`, bypassing the coordinator's filtering, size selection, fast message and ordering. | Route manual refresh through the process coordinator and await durable publication. |
| Watch commands → phone | `PhoneConnection` already cached the phone node and conflated absolute volume/seek. But the manifest `WatchListenerService` discarded the event and only started `MusicService`; its live listener could not recover the command that had already arrived. Every hot command also caused another service start. | Preserve cold-start commands until the session observer is active. Deduplicate the manifest/live delivery by node and request ID. Dispatch directly when ready. |
| Action/button configuration | `GlobalActionList.commitAgain` was never reset. One overlapping edit could cause perpetual writes and transmissions. Retransmits and startup repairs bypassed commit serialization. Button transfer also iterated a live mutable map off the main thread. | One `ConfigSyncQueue` per config, with a pending-save flag that retransmits cannot erase. Snapshot on main; save/encode off main. Reset after failures and coalesce overlapping requests. Startup repairs use this queue too. |
| Music state → watch | The sender awaited a fast message before durable publication. Older work resuming after a suspension could acquire a newer wire revision. Several artwork encoders could remain active. | Allocate one sequence per logical snapshot, shared by message/interim/final copies. Separate message delivery, cancel obsolete preparation, and serialize submitted durable puts until their actual completion. |
| Watch artwork and config decoding | Music ACK and asset loading held up later entries in a data callback. Asset jobs checked music order before suspension, but could publish old art after a newer message arrived. Config decoders were uncancelled; button assignments were cleared before new icons finished. | Load music assets separately, recheck revision at publication, and keep the settled artwork phase ahead of its interim phase across callbacks. Cancel obsolete config decoding and replace working assignments only when the new map is ready. |

The settings path uses SharedPreferences callbacks, coroutines and LiveData, rather than a buffered Flow pipeline. `PreferencesBus.postValue` already coalesces pending UI notifications; the stale-state defect occurred in preference writes before that bus was notified. The relevant fixes therefore belong in publication and receive ordering, not an extra UI debounce. Actual radio, serialization, disk and frame costs still require device traces to rank.

## Ordering and reliability

Settings remain complete selected snapshots. Active appearance scope and unscoped settings are mandatory; inactive scopes remain optional cached values under the existing size budget. No per-key delta protocol or compression was introduced.

The same `__svartifoss_preferences_sequence` accompanies the DataItem and the existing v1 message payload. Older watches ignore this extra setting. A new watch accepts legacy phones until a marked snapshot arrives, then rejects unmarked replay. Updating both APKs enables the cross-transport guarantee; downgrading only the phone after adopting the protocol is not a supported sync transition.

Messages remain additive. The watch tracks ownership of keys introduced by messages so a later durable snapshot can remove them even if their original DataItem was skipped. A durable copy at the message's sequence is allowed to reconcile deletions and inventory. A lower sequence cannot apply. Empty snapshots propagate resets without deleting watch-local preferences. Restart and failed-commit behavior are covered by regression tests.

The two settings workers hold at most one in-flight operation and one pending snapshot each. For example, while theme 1's put is outstanding, selecting 2, 3, 4 and 5 replaces the pending put with 5. Immediate messages continue independently. This cannot retract a theme already delivered, or know that another selection will happen in the future: selections spaced beyond the coalescing window may still be visible. It prevents the application from retaining an avoidable FIFO of obsolete snapshots and stops delayed older transport copies from rolling the watch back.

Durable failures retain exponential-backoff retry of the latest publication. Retry does not resend an unchanged immediate message. Process startup and explicit refresh still publish a fresh revision even when the values match, preserving stale-watch repair. The sequence is persisted on the phone to tolerate ordinary restarts and wall-clock rollback.

Discrete commands such as next, previous, toggle and relative seek keep their individual meaning. Only duplicate delivery of the same MessageEvent is suppressed; identical payloads with different request IDs execute separately. The cold-start inbox is process-local and is cleared on refused/aborted startup. It does not retry commands after a disconnect or process death. Absolute seek and volume retain their existing latest-pending behavior.

## Platform choices

The existing relevant DataItems were already urgent, so simply adding `setUrgent()` was not a solution. Non-urgent items may be delayed by the platform, while urgent is a request for prompt transport rather than a deadline. See [Google's DataItem guidance](https://developer.android.com/training/wearables/data/data-items).

`MessageClient` is suitable for small immediate commands, but successful submission means queued for delivery, not confirmation of execution. Durable DataItems therefore remain necessary for settings recovery. See [MessageClient reference](https://developers.google.com/android/reference/com/google/android/gms/wearable/MessageClient).

Cancelling a coroutine waiting on a Play Services Task does not by itself cancel the submitted operation; bidirectional cancellation requires a supported cancellation token. This is why obsolete encoding can be cancelled but durable puts remain serialized until the actual Task finishes. See [Kotlin's Play Services integration](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/kotlinx.coroutines.tasks/).

No evidence justified changing protobuf encoding, transport technology, artwork quality, playback-clock policy, or button gesture recognition timeouts. `StemButtonsManager` deliberately waits when a configured action needs double-tap/long-press recognition; that delay precedes transport and is distinct from command delivery.

## Validation

Regression coverage includes independent slow message/put transports, five-edit conflation, durable-only retry, cancellation boundaries, cross-transport reordering, same-sequence removals, empty snapshots, ownership preservation, restart, failed commits, cold-start dispatch, duplicate versus repeated taps, aborted service startup, config queue failure recovery, and artwork phase ordering.

Run with JDK 21 and the repository's configured Android SDK:

```sh
./gradlew :common:testDebugUnitTest :mobile:testDebugUnitTest :wear:testDebugUnitTest
./gradlew test :mobile:assembleDebug :wear:assembleDebug
```

Validation completed successfully: all 1,061 JVM tests passed in both debug and release variants (2,122 executions), including 37 new regression tests, and both debug APKs assembled. `git diff --check` also passed. No paired phone/watch was connected for runtime testing.

The music protobuf change is documentation only; no fields, application IDs, signing settings, minimum APIs or localization resources changed. The wearutils submodule remains unchanged. Settings gained transport metadata, stripped before watch preference application.

On-device follow-up is still needed to quantify improvement:

1. Compare rapid five-theme changes with the watch awake, ambient, and reconnecting; confirm final theme stays final without rollback, including language and reset changes.
2. Stop the phone service without force-stopping the app, then issue exactly one watch play/skip command with notification access granted; confirm the first command executes. Repeat several skips to verify each remains intentional.
3. Edit action assignments while refreshing icons; confirm old working buttons remain usable until replacement and the final configuration settles without repeated traffic.
4. Skip rapidly during large artwork downloads; confirm current metadata remains responsive and late old covers cannot replace the current cover.
5. Separate gesture recognition, phone service startup, radio delivery, receiving disk commit and UI frame timing in device traces. Use local monotonic durations or correlated request IDs; subtracting raw phone/watch wall clocks is not a reliable one-way latency measurement.

No millisecond speedup or end-to-end latency guarantee is claimed without those measurements.
