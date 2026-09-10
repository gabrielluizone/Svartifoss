# Phone customization memory investigation

A user reported 3.36 GB while customizing the phone app. The report does not identify the app
version, Android version, selected font/theme, measurement tool, or whether the number is
resident memory or virtual address space. On the connected Pixel 3 (Android 10, Play build 77),
a two-minute Flex-font exercise reached roughly 239–306 MiB `TOTAL PSS`, not 3.36 GB. Its RSS
fluctuated substantially, so it is not evidence of a leak by itself. This short run cannot rule
out a longer path or a different device-specific trigger.

## Findings and fixes

### Font creation during preview redraws

`WatchPreviewView.flexPreviewTypeface` built a new file-backed `Typeface` for every request,
including repeated requests with identical axes during a single frame. Animated previews call
these helpers continuously. `WatchFontCatalog.previewTypefaceFor` also loaded the default Flex
font from its file on every call; menu text then derived bold variants with `Typeface.create`.

Android's [Typeface implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/graphics/java/android/graphics/Typeface.java)
only uses the builder's dynamic cache for asset-backed inputs. Style and weight variants are
kept in process-wide maps keyed by the native identity of the base font. Repeated file loads
followed by style derivation can therefore retain distinct native font families, while direct
per-frame variable-font builds also cause unnecessary allocation and file mapping churn.

The phone now reuses font instances: stable bundled/default families, and a 32-entry cache for
variable-axis combinations shared by the preview and font resolver. Derived variable fonts also
use this cache, including the playback-time text when it follows a title or artist font. Weak
references preserve the axes of a font still in use after eviction without retaining its native
family. The variable-font builder
is guarded for the phone's API 23 minimum; axis settings require API 26. Preference values and
watch synchronization are unchanged.

### Gallery screenshot retention

`CommunityGalleryAdapter.screenshots` held every loaded author screenshot in a map until the
catalogue was replaced. Recycling a card did not remove its image from that map. Browsing more
themes therefore increased retained bitmap memory with no byte budget.

Published screenshots are bounded to 512 × 512 pixels, up to 1 MiB per ARGB bitmap. The gallery
now limits its screenshot cache to 8 MiB and clears recycled cards' image references. Cache
eviction releases its reference without recycling a bitmap that an on-screen card may still use.
The disk cache remains available when a previously evicted card returns. Outstanding image loads
are cancelled when replacing the catalogue or destroying the activity.

## Corrected release artifact

The corrected Play artifact is
`mobile/build/outputs/apk/play/release/mobile-play-release.apk`:

- package `com.svartifoss.snfell`, version 4.0, version code 78;
- SHA-256 `7d79e42861195f6649c0263a35150a42d5b0ebc665c2d1c84310fb65e65ee2ff`;
- v1 and v2 APK signatures verify successfully;
- its permission list is identical to the captured Play build 77 installed on the Pixel.

The release bytecode was inspected after shrinking and signing. It contains the 32-entry,
synchronized `FlexTypefaceCache`, and `WatchFontCatalog.flexTypefaceFor` calls that cache before
building a variable typeface. The gallery screenshot cache is an `LruCache`, uses each bitmap's
`allocationByteCount`, and clears with `evictAll`. Its byte budget is 8 MiB.

This locally signed artifact cannot update the Play-installed app in place: its signing
certificate differs from the Play certificate. It must be distributed through the matching Play
signing flow, or installed as an isolated test package. The corrected code has therefore been
verified in the release binary, but has not replaced build 77 on the Pixel.

## Remaining device verification

Compare the old and fixed builds on the same phone, with the same saved appearance and track.
Keep the app in the foreground and record snapshots after warm-up, then at 1, 5 and 10 minutes:

```sh
adb shell dumpsys meminfo com.svartifoss.snfell
```

Run these cases separately:

1. Select Google Sans Flex and leave an animated or scrolling preview visible. Enable the
   selected font on other screens and preview a panel with bold text as well.
2. Repeatedly change Flex weight/axes, open and close the font picker, and navigate between
   customization pages. Include playback-time text set to Follow with an explicit weight or
   italic setting, and Aurora's italic title. Compare their appearance with the watch; derived
   Flex styles now resolve through axes instead of Android's global style cache. Also revisit
   more than 16 bundled font families.
3. Browse many gallery cards with author screenshots, scroll back, and repeat. Check that
   returning images load correctly and no recycled-bitmap error occurs.
4. Exercise a non-Flex font as a control. On API 23–25 verify that selecting Flex renders its
   fallback without a missing-API crash.

Compare `TOTAL PSS`, `TOTAL RSS` when available, native heap, graphics, and font/file mappings;
see Android's [dumpsys memory documentation](https://developer.android.com/tools/dumpsys#meminfo).
Do not use virtual address size alone as the RAM measurement. In Android Studio's Memory
Profiler, compare allocations and retained `Typeface`/bitmap instances after the same actions
using a [heap dump](https://developer.android.com/studio/profile/capture-heap-dump).
The goal is a stable working set after warm-up, rather than cumulative growth with each frame
or each gallery card. JVM tests can verify font cache reuse and eviction;
they cannot establish the device's native-memory plateau.

## Local validation

- `:mobile:testGithubDebugUnitTest`: 432 tests passed, including six new cache regression tests.
- `./gradlew test :mobile:assembleGithubDebug`: passed across all modules and flavors; produced
  `mobile/build/outputs/apk/github/debug/mobile-github-debug.apk`.
- `:mobile:assemblePlayRelease`: passed and produced the corrected release artifact above.
- `apksigner verify --verbose`: v1 and v2 verification passed for that artifact.
- `git diff --check`: passed.
