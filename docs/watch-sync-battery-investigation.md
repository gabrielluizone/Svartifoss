# Watch sync battery drain investigation

Status: **fixed in the working tree; the storm itself was not reproduced** (2026-11-04). First measured on 2026-10-28 on the owner's devices: in one 24-minute window the phone app pushed 29.6 MB to the watch, a full music state roughly every 1.7 s. The instrumentation below did not catch that storm again in two reproductions and a week of use, but it found what turns any repeating trigger into a storm. The dedupe compared the cover by reference, and every metadata read returns a new `Bitmap`, so with YouTube Music every callback became a full 12 KB transmission. *Findings with the instrumented build* has the evidence, *Fixes* what changed, and *Verification* the measurements taken afterwards.

The sections from *Setup* to *Acceptance targets* are the original brief, with line numbers from 4.3.1.

## Setup

- Phone: Pixel 3, Android 10 (API 29), Svartifoss **4.3** (Play flavor, installed 2026-10-26). `git diff 4.3 4.3.1` touches `MusicService.kt`, but none of the transmit or dedupe code, so the line numbers below (HEAD `2fb4ab70`, 4.3.1) describe the build that was measured.
- Watch: TicWatch Pro 5 (Wear OS 4), paired through Mobvoi Health over Bluetooth Classic.
- Player: YouTube Music. Its session exposes four custom actions (like, shuffle, repeat, dislike); the log summary shows `actions=3`.
- The owner reported that the phone loses charge fast while music plays and while it sits idle. This app is one of several contributors (see *Not caused by this app*).

## Evidence

### Data Layer traffic

Source: `adb shell dumpsys activity service com.google.android.gms/.wearable.service.WearableService`, taken at 14:09 and 14:18. Its *Connection stats* section lists bytes per package for each Bluetooth connection:

| Connection | Duration | Svartifoss writes | Svartifoss bytes | RPCs | DataItem SETs |
| --- | --- | --- | --- | --- | --- |
| 13:24:21 → 13:49:06, right after a phone reboot; closed with `CLOSE_REASON_IO_EXCEPTION` | 24 min 45 s | 3,195 | 29,557,974 (98.5% of the link) | 865 | 1,665 |
| 13:51:51 → 14:19 | 27 min 09 s | 87 | 635,361 | 19 | 34 |

In the *Data Usage Stats* table the app appears as `<hidden#137935e9>` (the dump hides non-Google package names there):

| Path | Bytes sent | Messages | Average |
| --- | --- | --- | --- |
| `RpcRequest` | 10,153,512 | 898 | ≈ 11.3 KB |
| `SetDataItem` | 19,623,475 | 2,313 | ≈ 8.5 KB |
| `SetAsset` | 416,348 | 71 | ≈ 5.9 KB |

The *71-RpcTracker* section showed every `/Messages/MusicState` message at **12,157 bytes**.

Reading:

- 865 transmissions in 24.75 min is about one every 1.7 s. The state build runs on every state tick, "roughly once a second while a track plays" (comment at `MusicService.kt:2671`). The dedupe therefore failed on nearly every tick for the whole window.
- There were about two DataItem puts per message, but only 71 asset uploads. `transmitToWear` only issues the state-only put when `artChanged` is true. So the cover most likely compared unequal (`sameAs` false) on most transmissions, yet encoded to bytes the watch already had, since an asset whose digest is unchanged should not travel again. If so, the phone also re-encoded the cover as JPEG about 865 times. This is an inference: verify it.

### Logs from the second connection

Steady playback was deduped correctly: there was no transmission between 13:55:23 and 13:56:56. Each track change, however, produced a burst. At the boundary, YouTube Music briefly reports the previous track's position for the new track, and the `backdrop` marker comes and goes (date, tag and PID trimmed):

```
13:56:56.550 TransmittingToWear 'in aeternum' by 'Wounded Masquerade' playing at 113359/113359ms vol=0.56 seq=0 backdrop actions=3
13:56:56.690 TransmittingToWear 'Suicide' by 'Decalius' playing at 0/225721ms vol=0.56 seq=0 actions=3
13:56:56.826 TransmittingToWear 'Suicide' by 'Decalius' playing at 113733/225721ms vol=0.56 seq=0 actions=3
13:56:56.866 TransmittingToWear 'Suicide' by 'Decalius' playing at 113778/225721ms vol=0.56 seq=0 actions=3
13:56:56.903 TransmittingToWear 'Suicide' by 'Decalius' playing at 113819/225721ms vol=0.56 seq=0 actions=3
13:56:57.397 TransmittingToWear 'Suicide' by 'Decalius' playing at 114300/225721ms vol=0.56 seq=0 backdrop actions=3
13:56:57.467 TransmittingToWear 'Suicide' by 'Decalius' playing at 1217/225721ms vol=0.56 seq=0 backdrop actions=3
```

That is seven states (about 12 KB as a message plus one or two puts each) in under a second, and each one passes the 1.5 s seek threshold (`SEEK_DETECTION_THRESHOLD_MS`, `MusicService.kt:151`).

### Battery side

- The kernel wake lock `hal_bluetooth_lock`, which the Bluetooth HAL holds while traffic crosses the UART, was active for 902 s of the first ~40 min after boot (38%). It was active 12–16% in a later 6-minute idle test, when this app sent almost nothing; that remainder comes from other traffic on the link. While that lock is held, the CPU cannot suspend.
- The process `com.svartifoss.snfell` used 2 min 24 s of CPU in its first 44 min. Its CPU time was flat over a 10 s sample taken while idle afterwards.

### Not caused by this app (exclude it when measuring)

- Mobvoi Health mirrors the same media notification to the watch through `/media_controls_bridging_v2/rpc/media_notification_message` (about 7.6 KB per update).
- The watch's own traffic: heartbeats, notification bridging, and its internet proxy over Bluetooth.

## Code path (HEAD 4.3.1)

- **Triggers.** `ActiveMediaSessionProvider` re-publishes the controller on every `onMetadataChanged` (`:264`, `:305`). It does the same on `onPlaybackStateChanged` when `PlaybackReportPolicy` says the change is worth reporting (`:292`). A playing state seen on an owned callback calls `updateControllerIfNeeded` (`:237`). `MusicService` observes the provider and calls `buildMusicStateAndTransmit` (`MusicService.kt:1657`). That function also has about 15 other callers: preferences, shuffle/repeat, the volume observer, Do Not Disturb, seeks, backdrop resolution, the live recheck, `CoverFallback`, `MESSAGE_WATCH_OPENED`, and `reportRefused`, which clears `previousMusicState`.
- **Cover.** The cover comes from `ALBUM_ART`, then `ART`, then `DISPLAY_ICON` (`:1722`). It may then be replaced by `higherResolutionArtOrSame` (`:1734`, defined at `:2678`).
- **Dedupe** (`:1914–1921`). A transmission is skipped only when all of these hold:
  - `albumArt === previousAlbumArt` (reference identity);
  - the source icon bytes are equal;
  - the backdrop bytes are equal;
  - `equalsIgnoringTime` holds (`:4547`). It compares many fields: `mediaActionsList` with its PNG bytes, `hasBackdropArt`, `backdropArtPending`, `live`, `audioBlock`, volume, and a position extrapolated with `playbackSpeed`.
- **Sending** (`transmitToWear`, `:2405`). It always sends the MessageClient copy (`sendMusicStateMessageAsync`, `:2541`). When the cover changed by `sameAs`, it adds an urgent state-only put. It then makes the final urgent put with up to three assets.
- **Size.** The KDoc at `:2557` says a MusicState is "normally a few hundred bytes". With YouTube Music it is 12,157 bytes, because every `MediaAction` carries its rasterized icon inline (`builder.iconPng`, `:1827`, `:1855`). Every transmission pays that cost twice: once in the message and once in the DataItem.

## Hypotheses for the storm, most likely first

1. **Two sessions taking turns.** If two sessions both looked like the playing one, consecutive builds would alternate between two tracks and two covers. That fits all three observations: `sameAs` false each time, art bytes the watch already had, and one transmission per tick. Right after a reboot, any player restoring a session (or the watch-side player) is a candidate.
2. **A field that never settles.** Candidates: a position extrapolated from a stale `lastPositionUpdateTime`, or `playbackSpeed` 0 while buffering; `live`; `audioBlock`; the volume; or action icons that rasterize differently between calls.
3. **The player republishing metadata with a new `Bitmap`.** The dedupe compares the cover by reference, so the same pixels in a fresh object always count as new art. By itself this would not make `sameAs` false, so check it together with hypothesis 1.
4. **A loop with the watch** right after the link comes up, through `/Messages/WatchOpened` or `/IdleMessages/StartService`. Check the inbound paths in RpcTracker during a reproduction.

## Reproduce and measure

Preparation:

- Run `adb devices -l`. The watch may be attached over wireless debugging, so pass `-s <phone>` to every command.
- The test phone keeps a 1 MiB log buffer, which held only ~14 min of logs. Run `adb -s <phone> logcat -G 16M` before reproducing; it lasts until the next reboot.
- Count transmissions: `adb -s <phone> logcat -v time -s WearMusicCenter:D | grep TransmittingToWear`.

Take a snapshot before and after each scenario:

- The WearableService dump above. Use the Svartifoss rows of *Connection stats*, the `<hidden#…>` rows of *Data Usage Stats* (Minute, 10 Minutes and Hour columns), and the last 100 RPCs in *71-RpcTracker*.
- `adb -s <phone> shell dumpsys batterystats --reset` before, `dumpsys batterystats` after. Read *Kernel Wake lock hal_bluetooth_lock* and the Bluetooth Rx/Tx time. With root, `/sys/kernel/debug/wakeup_sources` gives the same wake lock live.

Scenarios. The first one matches the observed storm.

1. Reboot the phone while YouTube Music holds a session (once playing, once paused), with the watch connected and the link coming up during boot.
2. Steady playback, with the watch app closed and then open.
3. Track changes: auto-advance, a skip from the phone, a skip from the watch.
4. Paused for 10+ minutes, then with the phone screen off and locked.
5. Another player (Retro Music, for example), to see whether the storm is specific to YouTube Music.

## Instrument first

Make the `TransmittingToWear` debug line say **why** it transmitted. Include:

- which condition defeated the dedupe: art reference, source icon, backdrop, or the first differing field in `equalsIgnoringTime`, with the expected and actual position on a seek;
- the caller (metadata, playback state, or other);
- the controller package and session token.

One reproduction with that line should identify the storm.

## Findings with the instrumented build

The instrumented phone build (4.3.1 plus the reason line, debug, versionCode 93) ran on the Pixel 3 from 2026-10-28 15:26. The line now reads, for example:

```
TransmittingToWear 'Free Me' by 'Sia' playing at 1424/286116ms seq=1793236180274 actions=3 | why: cover none -> 4bfb9a4 544x544 | via: session provider: metadata of playing com.google.android.apps.youtube.music#7a9f3bb | session: com.google.android.apps.youtube.music#7a9f3bb
TransmittingToWear seq=1793236180274 cover encoded 30254 B 9c6ed8e0 from 4bfb9a4 544x544
```

1. **Every read of the metadata returns a new cover `Bitmap`, so the dedupe never held for YouTube Music.** `MediaController.getMetadata()` is a binder call that hands back a freshly unparcelled `MediaMetadata`, bitmap included. The dedupe's comment assumed the reference stays the same until the app publishes new metadata; that is true of one `MediaMetadata` object, not of repeated reads. The `why:` field showed a different identity on every line, with `cover reused` (same pixels) on most. In 42 minutes of ordinary playback (21:21 to 22:03), 72 of 184 transmissions (39%) had the cover reference as their only reason: 34 with identical pixels, 13 re-encoded, and 25 superseded before their final put. So every provider callback was a transmission, which is the amplifier a storm needs.
2. **A track change is a burst.** Those 42 minutes held 29 groups of transmissions; the largest was 15 in 3.5 s (21:28:04, a skip and a skip back). A typical YouTube Music change: the old track paused at its end, the new track at 0, the new track again with the old track's position (`position expected 82 ms, got 65475 ms`), rereads with only a new cover object, the position corrected, the backdrop lookup landing, and the like state. The stale-position state reached the watch, which then showed the new track at the old track's time for 0.3 to 0.7 s.
3. **YouTube Music publishes a new track before its cover.** At 22:09:39 'Free Me' arrived with no bitmap and no art address, and its own cover came 0.96 s later. In between, `CoverFallback` started an online lookup: Deezer, and the YouTube Data API first when a key is configured, at 100 quota units per search from a project-wide 10,000 a day. Its answer then caused one more transmission, with the same pixels as the player's cover.
4. **Other triggers.** `notification actions changed` caused 21 transmissions in the 42 minutes, 20 of them with nothing new but the cover reference. The 9 `volume changed` transmissions were real volume changes. `watch app opened` sent a full state 34 ms after the one the service had just sent on starting.
5. **Size.** With the media notification's three action icons inline, the message was 12,135 to 13,734 bytes (afternoon). In the evening the same three actions travelled without icons and the messages were 329 to 363 bytes; why the icons were missing then was not established. The icons are what make a state 12 KB.
6. **Steady state.** The connection from 14:54 to 20:47 (5 h 53 min, mostly playback) carried 546 messages and 2.9 MB from Svartifoss; the one from 21:00 to 22:06 carried 260 messages and 1.55 MB. `hal_bluetooth_lock` was held 6 min 31 s in the 6 h 40 min after the batterystats reset (1.6%), against 38% in the storm window.

Reproductions:

- **Scenario 1** (reboot with YouTube Music playing, 2026-10-28 22:06, phone on USB). The link came up at 22:08:18, under a minute after boot. YouTube Music did not restore a session: the only session after boot was `com.pallo.passiontimerscoped`, in state NONE. Svartifoss started on unlock (22:08:27), sent one "nothing playing" state and stopped on `WatchClosed`. After the media play key (22:08:58) it sent 8 states in the first 70 s (the watch app opening and one track change), then none for 4 minutes. No storm.
- The phone's wall clock jumped back about 2.5 minutes shortly after that boot. `positionUpdateTime` is wall-clock time, so a jump like that makes one dedupe comparison see a seek. It cannot by itself explain a storm.
- **2026-11-04.** The connection logs since 08:36 show no Svartifoss traffic at all. Mobvoi Health moved 4.3 MB (877 writes, 418 DataItem puts) in that day's 6 h 13 min connection, which belongs under *Not caused by this app*.

7. **On this phone the quick panel never gets the notification's actions.** A diagnostic added with the fixes (`Quick panel actions: … from …`, logged once per change) reads `3 from session custom actions, 0 with an icon; notifications: 1 stored, 0 for this session, 0 without one` while YouTube Music has a single session. So `MediaNotificationActions.actionsForSession` finds YouTube Music's notification but not as belonging to the playing session, and the panel falls back to the session's custom actions (like, shuffle, repeat), whose vector icons render transparent outside the app; the watch then draws its own glyphs. The likely cause is Android 10's `MediaSession.Token.equals`, which also compares the uid of the process that created the token: the copy in the notification is created by the app, the one from `getActiveSessions` by `system_server`. Not verified, and not fixed here, since the same match also guards `execute`, `executeLike` and `likedStateForSession`. A fix to consider: accept the package's notification when the package has exactly one active session. Accepting it temporarily (a test build, since reverted) is how the icon keys below were checked with real icons. **Update 2026-11-14:** the icons did not "render transparent outside the app". `AppCompatResources.getDrawable` threw `IllegalStateException: This app has been built with an incorrect configuration. Please configure your build for VectorDrawableCompat` for YouTube Music's Context (its vector check loads AppCompat's own test drawable by id through the Context it is given), until a Context of ours had passed the check, which is the "0 icons, then 3 minutes later" observation; the platform's `Context.getDrawable` loaded all three (like a 65x65 bitmap, shuffle and repeat layer lists) and the log read `3 with an icon`. The token match accepting a package's only session (54480f1e) then exposed the opposite problem: YouTube Music's compact notification is Previous/Play/Next, so the panel became a second transport row; `chooseQuickActionSource` now keeps the custom actions when the notification holds only transport.

What the storm's own figures still say: the constant 12,157-byte message suggests the same track re-sent each time rather than two tracks alternating. Two puts per message mean the interim put ran on most transmissions, which happens when the cover's pixels changed or the transmission before had no cover. The trigger remains unknown. The fixes below remove the amplifier, and bound and name whatever is left.

## Fix directions to evaluate

1. **Compare the cover by content in the dedupe.** For example, cache a hash or `sameAs` result per `Bitmap` reference. The two-step metadata case described at `:1909` still works, because artwork that arrives later differs in content too.
2. **Take the icons out of the state.** Send each icon once, as an asset or a DataItem keyed by content hash, and put only that key in `MediaAction`. The message and the DataItem both shrink from about 12 KB to a few hundred bytes.
3. **Coalesce bursts.** Use a short trailing window around track changes, as the preference sync does with its 120 ms window. Seven callbacks in under a second should give one or two transmissions.
4. **Add a guard rail.** Cap transmissions that are not caused by a user action (for example, N per 10 s) and log when the cap trips, so the next regression shows up in logcat instead of in the battery.
5. **Review `setUrgent()`** on puts that follow a message already carrying the same snapshot.

Keep what the comments protect: two-step artwork, the `albumArtPending` crossfade, propagation of seeks, likes, shuffle and repeat, error recovery (`transmitError`), the refused-transport correction, and snapshot ordering (`MusicAssetOrder`).

## Fixes

Made on 2026-11-04, in the working tree.

1. **The cover is compared by its pixels** (direction 1). `MusicService.buildMusicStateAndTransmit` uses `Bitmap.sameAs`, a memcmp of the pixel buffer; the two-step artwork case still transmits, because late artwork differs in its pixels. The dedupe itself moved into the pure, JVM-tested `MusicStateDedupe`, which names the first field that differs for the log line.
2. **Transmissions are paced** (directions 3 and 4), by the pure `MusicTransmitPacer`. A state inside 1 s of the last one sent waits, and when the wait is over the session is read again, so what goes out is the newest state: the first state of a burst still leaves at once and the rest of the burst becomes one. A budget of 30 transmissions, refilled at one every 4 s, bounds any sustained cause. A state the budget holds back logs `Music state transmissions over budget` at warning level, at most once a minute, with a count of what asked for the recent transmissions. Warnings reach logcat in release builds too. Every wait ends in a fresh read, so a state can be delayed but never dropped. `transmitError` cancels a pending read, so it cannot replace the error.
3. **Action icons travel once, by key** (direction 2). `MediaAction.iconKey` is a content digest (`MediaActionIcons`, 64 bits of SHA-256). A watch that sets `WatchInfo.actionIconsByKey` gets no `iconPng` in the state; the bytes ride the final DataItem put as the asset `ActionIcon_<key>`, which the Data Layer transfers once per digest. The watch keeps icons by key (`PhoneConnection.actionIcons`) and redraws an open quick panel when one lands; until then it draws the action's own glyph. Older watches, and any watch whose info has not arrived yet, keep getting the bytes inline, and an older phone's inline icons still draw on a new watch.
4. **A missing cover is looked up only after 2 s without one** (new, from finding 3). `CoverLookupGrace` restarts the wait whenever the player publishes a cover of its own, and `CoverFallback` has the state rebuilt when the wait ends, so a player that never sends one (YouTube) still gets its fallback cover, 2 s later than before.
5. **`setUrgent()` stays on both puts** (direction 5, evaluated). A current watch takes the message first, but the interim put is what older watches and the durable store get first. A non-urgent put may wait for the next batch, which would land the interim phase after the final one: the case `MusicAssetOrder` exists for.

The instrumentation stays in. It costs a few strings per transmission, and the pacer now bounds transmissions.

## Acceptance targets

| Scenario | Target |
| --- | --- |
| Steady playback, between track changes | 0 transmissions |
| Track change | ≤ 2 transmissions |
| Phone reboot or link reconnect | ≤ 3 transmissions |
| MusicState with YouTube Music | < 1 KB |
| 30 min of playback, Svartifoss bytes on the link | < 300 KB (the storm moved 29.6 MB in 25 min) |

## Verification

Phone build 93 and watch build 193 (both play debug, with the fixes) on the same devices, 2026-11-04, YouTube Music.

- **Skips from the phone**: 3 transmissions each (old track paused, new track, new track with its cover), against 7 to 15 before. A backdrop lookup that lands later adds one more, as it should.
- **Steady playback** (15:34:06 to 15:37:43, watch app open): no transmission at all. The natural track change at 15:37:43 then cost one state, with its cover already in it, and one more when the backdrop lookup landed 3 s later. The window from 15:34:24 to 15:38:16 carried 156 KB from Svartifoss: two states of 319 bytes, 22 playback-sync replies of about 70 bytes (the watch asks while a position is on screen), and the rest pictures, namely the new cover (54 KB), the backdrop (60 KB), the neighbouring cover that entered the prefetch window and the queue's new thumbnails.
- **Watch app opening**: no second transmission. Before, `watch app opened` re-sent the state 34 ms after the first.
- **Cover lookup**: the YouTube Music covers arrived inside the 2 s wait, and no fallback lookup started.
- **Size**: with the old watch (191) the phone kept the inline form, but on this phone those states had no icons at all (finding 7), so the inline path was not exercised with real icons. With the new watch, `WatchInfo.actionIconsByKey` reached the phone and the states left with `3 action icon(s) by key`; `/Messages/MusicState` measured 548 to 556 bytes with three notification icons, against 12,135 to 13,734 with the same icons inline. The watch logged `Action icons: 3 of 3 new key(s) loaded`, the interim put of a skip `0 of 1` (it carries no assets, by design) and the final put `1 of 1`, and the quick panel drew the app's own icons.
- **Battery**: not measured. The phone was on USB for the reboot test, and `batterystats` counts nothing on external power (`Time on battery: 0ms`). A battery check needs the phone unplugged, over wireless adb, for a few hours.

Against the acceptance targets:

| Scenario | Target | Measured with the fixes |
| --- | --- | --- |
| Steady playback, between track changes | 0 transmissions | 0 |
| Track change | ≤ 2 transmissions | 1 on auto-advance and 3 on a skip, plus 1 when a backdrop lookup lands |
| Phone reboot or link reconnect | ≤ 3 transmissions | not re-measured; a service restart sent 1 |
| MusicState with YouTube Music | < 1 KB | 319 B without icons, 548 to 556 B with three icons by key |
| 30 min of playback, Svartifoss bytes on the link | < 300 KB | not met: about 156 KB per track change, nearly all of it pictures |

What is left:

- **The storm's trigger.** Unknown. If it recurs, `Music state transmissions over budget` names the causes in logcat, and the state now costs about half a kilobyte, with the budget capping it at 15 a minute.
- **Pictures per track change.** The cover, the backdrop and the prefetched neighbour cover are about 150 KB per change, which is why the 30-minute target is out of reach while they travel at their current size. Whether to send them smaller, or to prefetch fewer neighbours, is a product decision.
- **Finding 7**, the token match that keeps the quick panel on its own glyphs on this phone.


## Second pass (2026-11-11): pictures, and the always-on display

Same devices, phone `play` debug 93 and watch `play` debug 193 (the fixes above), YouTube Music.

### What the link carries now

`dumpsys` of `WearableService` on the phone at 12:36, 1 h 33 min after a reboot:

| Svartifoss, since boot | Bytes | Messages |
| --- | --- | --- |
| `SetAsset` (pictures) | 3,694,346 | 766 |
| `SetDataItem` | 361,199 | 291 |
| `RpcRequest` (messages) | 80,508 | 131 |

Pictures are 89% of what the app sends. The connection that ran from 11:02 to 12:03 carried 2.76 MB from Svartifoss, and the one from 12:03 had carried 1.37 MB after 31 minutes. The watch counted 6.27 MB of Svartifoss assets received in its own window (about 8 hours).

The *Sent assets* list for 12:26 to 12:34 (three track changes) held eight covers at the watch's 466 px, 421,874 bytes between them, and about 44 KB of 96 px queue thumbnails.

### Every cover sent ahead was sent twice

`publishAdjacentTrackArtwork` sends the covers two tracks either way, resolved from each queue row's address at the watch's size. When one of those tracks starts, `transmitToWear` encodes the cover again from the player's own bitmap (YouTube Music: 544 px). Same sleeve, two scalers, two encodings: not one byte agrees, so the Data Layer, which addresses assets by a digest of their bytes, transferred it again, and the watch's check for a cover it already holds never matched either, so it read and decoded the second copy too. In the window above, Coma went out at 12:26:39 as a neighbour and again at 12:30:10 as 52,800 bytes; Психолог - Hardstyle at 12:26:39 and again at 12:33:12 as 35,873. In steady playback that is every cover twice.

### Format

Measured offline on real covers (84 at 466 px, from the phone's cache and the same CDN addresses fetched at two sizes):

| Encoding at 466 px | Mean size | PSNR | SSIM |
| --- | --- | --- | --- |
| JPEG 85 (what was sent) | 48.2 KB | 34.60 dB | 0.936 |
| WebP 80 | 31.9 KB | 34.68 dB | 0.934 |
| WebP 85 | 39.3 KB | 35.74 dB | 0.947 |
| JPEG 90 | 61.0 KB | 36.05 dB | 0.953 |

A second sample of 70 cached covers: JPEG 85 51.1 KB at 35.25 dB, WebP 80 35.6 KB at 35.35 dB; JPEG 88 (the backdrop's quality) 57.8 KB at 36.64 dB, WebP 85 42.9 KB at 36.67 dB. Queue thumbnails: at 96 px JPEG 85 3.22 KB / 33.02 dB against WebP 80 1.94 KB / 33.35 dB, at 320 px 21.8 KB / 35.25 dB against 13.2 KB / 35.19 dB.

YouTube Music *videos* are the one kind of picture where WebP 80 scores lower. Their queue rows carry a signed 400 x 225 thumbnail (`hqdefault.jpg?sqp=...`); on ten of them JPEG 85 came out at 28.2 KB and 37.91 dB, WebP 80 at 17.7 KB and 36.89 dB (SSIM 0.968 against 0.980), and WebP 84 would match the JPEG at 20.2 KB. Quality stays at 80: that thumbnail is only the stand-in a predicted skip draws until the playing cover arrives, and the playing cover of a video is a 466 px crop of the player's 853 x 479 picture, which behaves like a cover.

### Telling one cover from another

To recognise the second copy, `CoverSignature` reduces a picture to the mean colour of each cell of a 16 x 16 grid. Calibration, all at 466 px:

| Pair | Mean difference (0 to 255) | Largest cell |
| --- | --- | --- |
| Same cover from the CDN at 466 and 544 px, the second scaled down (26 covers) | 0.04 to 1.60, one at 4.29 | 1 to 7, one at 22 |
| The same, one pixel off | 0.51 to 2.71, one at 4.68 | 3 to 14, one at 25 |
| Two different covers (73 covers, 2,628 pairs) | closest 19.37, 0.1st percentile 23.3, median 81.2 | closest pair 178 |

The thresholds are a mean of 3.0 and a cell of 20. The one outlier pair is two renditions the image host served differently; refusing it only means sending it as before.

### CPU is not where the battery goes

Per-thread CPU of the app process, from `/proc/<pid>/task/*/stat`, over 4 min 7 s of playback with one track change: phone 1.04 s (0.4%), of which the main thread 0.42 s, JIT 0.31 s (debug builds only), coroutine workers 0.13 s and GC 0.09 s; watch 0.44 s (0.2%), main thread 0.20 s, JIT 0.14 s. Radio time, which every transfer and every message costs on both devices, is the larger lever.

### Changes

1. **A cover is sent once.** `WatchCoverLedger` keeps the last eight encodings by track title and `CoverSignature`; `encodeArtworkForWatch` reuses the bytes already sent when the same track's same-sized picture comes round again. Requiring the title as well as the picture keeps two dark sleeves with different small lettering apart, and requiring the size keeps a thumbnail sent ahead (because nothing larger was found) from standing in for a full-size cover. On the watch the reused bytes have the same asset id, so `deliverAlbumArt` publishes the bitmap it already decoded for the prediction. It does not apply to YouTube Music videos, by design: what is sent ahead for one is the 400 x 225 queue thumbnail and what plays is a 466 px crop of an 853 x 479 picture, and the sharper of two sizes always has to reach the watch.
2. **WebP** (`WatchImageCodec`): quality 80 for covers and thumbnails, 85 for the backdrop. Every watch-side reader goes through `BitmapFactory`, so older watch builds read it too.
3. **No position checks while the screen is ambient.** `PositionViewers` separates a screen that shows the position from one that shows it moving; an ambient player or lyrics screen is the first and not the second, and with no moving readout the check loop is not running at all. It used to run through the always-on display with the player left open: 8 s after every track change, then 16, 32 and 64 s, for a readout redrawn once a minute. The neighbouring covers are still decoded for an ambient screen, since a stem button skips from there.

Cost: one 16 x 16 fingerprint per encoded cover, read a row at a time, and up to eight encodings (about 250 KB) held on the phone.

### Verification on the devices

Phone and watch builds with the changes installed at 16:18 (play debug 93/193), same playlists, same session.

- **A cover goes out once.** At 16:23:01 the phone sent Бессонница's cover ahead as a neighbour, 26,376 bytes of WebP. When the song began at 16:25:02 the log read `cover already on the watch 26376 B ... (mean 0.66, largest cell 4 from decd639 544x544)`: the player's 544 px cover was recognised as the same picture and its bytes were not encoded or sent again. The link's *Sent assets* list for 16:25:02 holds only a 2,200-byte queue thumbnail and the 34,986-byte cover of the next new neighbour. With the old build every such start sent a second copy: 52,800 bytes for Coma and 35,873 for Психолог - Hardstyle in the morning window.
- **WebP is read on the watch.** The watch logged its neighbouring covers decoded and the Artist face drew the cover; no decode error in its log.
- **Videos** (YouTube Music's 853 x 479 covers): the playing cover is still sent next to the 400 x 225 thumbnail sent ahead, as intended. Before, three track changes of videos cost 112 KB of covers (37 KB each); after, covers of the same kind were 8 to 22 KB, but the tracks differ, so the offline measurement above is the controlled comparison.
- **Ambient position checks**: not verified on these devices, because the watch's always-on display is off (`settings get global ambient_enabled` = 0) and the player is stopped, not ambient, when its screen turns off. `PositionViewersTest` covers it, including the calls in both screens' sources.
- The first state after the phone's reinstall re-sent the queue's 24 thumbnails and the four neighbours once, as WebP: a picture whose encoding changed is a new asset to the Data Layer, once.
