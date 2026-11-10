# Memory investigation (2026-11-04)

Prompted by Settings → Developer options → **Running services** on the Pixel 3 (Android 10) showing **Svartifoss — 298 MB — 3 processes and 1 service**. Both devices were running the `play` debug builds left from the watch-sync battery work (phone 93, watch 193).

## What that 298 MB is

Svartifoss has one process. The Running services row adds every process that is being kept alive *on the app's behalf*, and `dumpsys activity processes` shows two:

```
Proc # 9: vis F/ /FGS 20987:com.google.android.gms/u0a168 (service)
    com.google.android.gms/.wearable.service.WearableService<=Proc{21694:com.svartifoss.snfell/u0a337}
Proc #14: vis F/ /FGS 13792:com.google.android.gms.unstable/u0a168 (service)
    com.google.android.gms/.droidguard.DroidGuardService<=Proc{20987:com.google.android.gms/u0a168}
```

`dumpsys meminfo -c` at the same moment (KiB, PSS including swapped pages, which is what Android 10 reports):

| Process | KiB |
|---|---|
| `com.google.android.gms` | 166,966 |
| `com.google.android.gms.unstable` | 32,896 |
| `com.svartifoss.snfell` | 88,696 |
| Sum | 288,558 (≈ 295 MB in the screen's SI units) |

About 200 MB of the row is Google Play services, which hosts the Wear OS Data Layer and runs for the watch connection whatever this app does. It lands in our row because `MusicService` registers a `MessageClient` listener and `WatchInfoProvider` a `DataClient` listener for the life of the service (both removed in `onDestroy`). While music plays with the watch connected that binding is needed, so the row will always include Play services then. Not changed.

On Android 10 the TOTAL in `dumpsys meminfo` is resident PSS **plus** SwapPss (zram). Of the phone process's 88 MB, only 42.6 MB was resident; 45.5 MB was swapped out.

## The app's own share

### Debug builds are not representative

`dumpsys package dexopt` showed `status=run-from-apk` on both devices: an `adb`-installed debug build is not compiled, so ART inflates the whole unminified dex into anonymous memory.

| | dex, debug | dex, release |
|---|---|---|
| Phone | 43.9 MB (33 files) | 8.2 MB |
| Watch | 49.9 MB (34 files) | 4.6 MB |

meminfo matched exactly: `.dex mmap` was 6.3 MB resident + 36.6 MB swapped on the phone, 15.0 MB + 33.8 MB on the watch. A Play install is compiled at install time and its code is read from file-backed pages. Measure memory on release builds.

### Phone (MusicService + NotificationService running, no activity)

`am dumpheap` + a small HPROF reader: 5.7 MB of live Java objects, 19 bitmaps totalling 3.9 MB (current cover 480×270, one bundled cover and a generated one at 256×256, the rest garbage awaiting collection). No font buffers, no large arrays. The 11 `OpenSSL Sockets` meminfo listed were unreachable by the time of the dump. Nothing to fix here.

### Watch (WatchMusicService running, no activity, charging)

PSS 127.5 MB including 57.9 MB swapped. App heap 19.7 MB, of which **15.1 MB** was eight `DirectByteBuffer`s with heap arrays:

| Buffer capacity | File | Copies |
|---|---|---|
| 1,973,548 | `res/font/google_sans_regular.ttf` | 4 |
| 1,975,756 | `res/font/google_sans_bold.ttf` | 4 |

Their only roots were JNI globals (the native font objects), so which load paths made the four copies is not visible from the Java heap.

Bitmaps: 76, ~19.6 MB if ARGB_8888. Reachable: the four prefetched neighbour covers (466×466, 3.3 MB) held by `PhoneConnection.prefetchedNeighbourArtwork` with no activity open; the current cover, the complication's cover and the media session's copy (480×270 each); ten streaming-shortcut covers (~270×270, ~2.8 MB). About 12 MB was unreachable decode churn not yet collected.

## Causes and changes

1. **Fonts were stored compressed in the APK** (125 `.ttf`, 42.8 MB, stored as 20.7 MB). `android.graphics.fonts.Font.Builder` first tries `AssetManager.openNonAssetFd` and maps the file; that only works for a stored (uncompressed) entry, so every load of a compressed font fell back to `ByteBuffer.allocateDirect` plus a full read. The platform keeps every family it derived a style from, so those copies were never freed. Fixed with `androidResources { noCompress 'ttf', 'otf' }` in both app modules; AGP writes the same rule into the bundle (`BundleConfig.compression.uncompressed_glob` gained `**[tT][tT][fF]` and `**[oO][tT][fF]`), so Play's split APKs keep it. Cost: debug APKs grew from 51.3 to 73.1 MB (phone) and 43.3 to 65.3 MB (watch); about 22 MB of installed size per device. Pinned by `FontPackagingContractTest`.
2. **The phone's font picker made it worse**: `WatchFontCatalog.bundledTypefaces` keeps each base typeface on purpose (see its comment), and the picker draws each row in its own font, so scrolling through it loads all 122 fonts it names (40.2 MB of files, plus repeated loads of Google Sans) into the process that then lives on as the music service — measured below at about 57 MB. With fonts mapped, keeping them costs only the pages that were read, and those are clean and droppable.
3. **Neighbour covers** (`PhoneConnection.receiveAdjacentArtwork`): every announcement read and decoded all four covers, even when the previous announcement or the playing cover already had them decoded, and an album's tracks got four identical bitmaps. Now resolved through `PrefetchedCoverReuse` (reuse by asset id, one decode per distinct picture), and an announcement arriving with no position viewer drops the held list instead of keeping four stale bitmaps for as long as the screen stays dark.

## Second pass (2026-11-06): the phone's previews

### Community gallery

Procedure: Watch tab, the theme card, **Community themes**, wait 8 s, four swipes down and four back, then `dumpsys meminfo` and a heap dump.

| | Native heap allocated, themes screen → gallery | Reachable bitmaps in the gallery |
|---|---|---|
| Before | 58.5 → 127.3 MB (+69 MB) | 146, 53.4 MiB |
| After | 41.8 → 71.8 MB (+30 MB) | 51, 18.0 MiB |

Every gallery card is a `WatchPreviewView`, and each preview built its own procedural sample covers - the sharp cover, an alternate, and three blurs - although they are a pure function of the preview's size and the blur radius, and every card is the same size. Twelve cards held 60 bitmaps of 417x417, 39.6 MiB of identical pictures. `SharedArtworkCache` now hands every preview the same ones (weakly held, never recycled by a preview, with `recycleOwned` refusing any bitmap the cache handed out, since `FrostedEdges.compose` and `BitmapBlur` return their input at radius 0); what remains at 417 px is one set per distinct blur radius among the themes on screen. The demo mini-button glyphs are shared the same way. The other gallery bitmaps are the adapter's own bounded cache of author screenshots, which dies with the screen.

### Font picker rows and the platform's text caches

The picker's rows now load through `WatchFontCatalog.listRowTypefaceFor`, which does not pin what it loads. After the picker and a forced GC, the app's own `Typeface` objects fell from 132 to 42 (the dumps also hold 116 system typefaces as `ROOT_VM_INTERNAL` roots, the same in every dump, which are not the app's). The fonts themselves stayed alive: the native font objects hold JNI global references to their buffers, and Skia's glyph cache and the layout cache keep the native fonts. `am send-trim-memory` is refused for this process ("Unable to set a background trim level on a foreground process", because the notification listener binding ranks it as foreground), so those caches are only emptied by their own eviction here. The change makes the fonts freeable rather than freed; no memory drop could be shown for it.

Tools for this pass: a forced GC on a debug build is `adb shell run-as com.svartifoss.snfell kill -10 <pid>` (ART logs "SIGUSR1 forcing GC"); `KEYCODE_HOME`/`KEYCODE_BACK` are ignored on this Pixel 3 (Lawnchair), so use the HOME intent or the toolbar's back arrow.

### Not changed in this pass

- With the UI left by Home, the stopped `MainActivity` keeps its views and its preview's bitmaps for as long as the music service keeps the process alive (`Activities: 1`, 320 views, ~48 MB native allocated, ~42 MB of it already in zram on the measurement above). Releasing the preview's bitmaps when its window is hidden would save roughly 5 to 9 MB there, much of which zram already compresses well; left for now.

## Deliberately not changed

- The Play services attribution: the binding is what receives watch messages while the service runs.
- Streaming-shortcut covers stay at the 270 px the phone sends: a cover can fill a whole menu pill.
- EmojiCompat on the watch: about 0.2 MB of metadata objects; the emoji font itself is mapped.
- `Bitmap.recycle()` on replaced covers: a recycled bitmap still referenced by a drawing crashes; fewer decodes reduce the churn instead.

## How to measure

```
adb shell dumpsys meminfo -d com.svartifoss.snfell      # per-category PSS, swap, object counts
adb shell dumpsys meminfo -c | grep -E 'snfell|gms'     # one line per process
adb shell dumpsys procstats --hours 24 com.svartifoss.snfell   # min/avg/max PSS per state
adb shell dumpsys activity processes                    # adjSource: who keeps whom alive
adb shell am dumpheap com.svartifoss.snfell /data/local/tmp/x.hprof   # debuggable builds only
```

The HPROF reader used here parses Android's raw format (keeps the app/zygote/image heap tags), lists bitmaps with dimensions and a strong path to a root, and large primitive arrays; it is short enough to rewrite (record tags 0x01/0x02/0x0C/0x1C, sub-tags 0x20-0x23, 0xC3, 0xFE and the Android root tags 0x89-0x90).

## Before and after on the devices

### Phone, font picker (Pixel 3, Android 10, play debug 93)

Procedure: open the app, Watch tab, Text page, tap **Typeface**, scroll the list to the end and back to the top (the list opens at the selected font, so both directions are needed for every row to be drawn), Cancel, then `dumpsys meminfo` and `am dumpheap`.

Before (fonts compressed), 2026-11-04:

| Moment | Native heap PSS | Process total (PSS + swap) |
|---|---|---|
| Watch tab open | 52.4 MB | 204.1 MB |
| After the picker | 86.6 MB (+22.9 MB swapped) | 268.6 MB |

On Android 10 a compressed font is inflated into **native** memory (`Font.Builder(Resources, int)` uses `nGetNativeAsset`/`nGetAssetBuffer` there; the `openNonAssetFd` + heap-buffer path seen on the watch is Android 11+), so the copies show up as native heap rather than as Java arrays. The heap dump held 265 `DirectByteBuffer`s whose capacity equals a bundled font's file size, covering 123 distinct fonts and 114.2 MiB of capacity; Google Sans regular and bold were each present 12 times.

After (fonts stored), 2026-11-06, same procedure. No music was playing this time, so the starting point is lower than before (the preview was drawing the sample art, not a real cover); compare what the picker *added*:

| Moment | Native heap PSS | Process total (PSS + swap) |
|---|---|---|
| Watch tab open | 32.1 MB | 172.0 MB |
| After the picker | 43.4 MB (+1.1 MB swapped) | 158.0 MB |

The picker added about 57 MB before (34.2 MB resident plus 22.9 MB pushed to swap, all held for the life of the process) and about 12 MB after: the native typeface and text-layout structures for 123 loaded fonts, not copies of the files. The mapped font pages themselves were 31 KB of `.apk mmap` by the time of the measurement, since clean file pages are simply dropped. The heap dump still held 261 font-sized `DirectByteBuffer`s (Google Sans regular and bold ten times each), now all views of the mapped APK.

### Watch (TicWatch Pro 5, Wear OS 4, play debug 193)

After, 2026-11-06, app just opened through its launcher alias: app heap **3.2 MiB** (before: 19.7 MiB). Seven font-sized `DirectByteBuffer`s (Google Sans regular ×4, bold ×3), **none** with a Java-heap copy (before: eight, 15.1 MiB); meminfo now lists a `.ttf mmap` row of 496 KB PSS for them. The process was fresher than in the "before" dump (fewer bitmaps), so only the font rows compare directly.

The neighbour-cover change was not measured on the device: it needs music playing through several track changes. Its log line (`Holding N of N neighbouring cover(s) ready (K decoded)`) says how many covers each announcement decoded; `PrefetchedCoverReuseTest` pins the rule.
