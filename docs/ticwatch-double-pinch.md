# TicWatch double pinch investigation

## Cause and input selection

The original player subscribed only through Wear SDK `GestureInputManager`, introduced with
the API 36.1 gesture feature. TicWatch Pro 5 running Wear OS 4 / Android 13 (API 33) cannot use
that path. Physical support for a pinch gesture does not imply support for this public API.

On the tested Pro 5, firmware `TMDB.240925.002`, an ordinary app can register for the vendor
sensor `mobvoi_pinch` (vendor `mobvoi`, type `33171134`, on-change). Both wakeup and non-wakeup
variants delivered the same event timestamp. Payloads associated with guided double pinches
had `values[0] == 2`; this is handled as one completed gesture, not two events to combine.
The payload interpretation is empirical, not a published Mobvoi API contract.

In native mode (the default), the controller prefers a supported public Wear input, otherwise this vendor sensor. It chooses
only one sensor, preferring non-wakeup, and uses the same delivery request as the diagnostic
app (`SENSOR_DELAY_FASTEST`). This parameter does not tune recognition sensitivity.
The native path uses no app-owned accelerometer/gyroscope classifier. The optional experimental
path below uses these sensors only in the interactive player or calibration screen. Neither path
adds a background service or sensor permission.

## Physical observations

Local diagnostic capture: `20260911-232524`, guided probe version 2. Each completed run had
eight attempts, with a 1.8-second gesture window and a 2.2-second rest between attempts.
Counts below assign sensor events by their receipt time to the gesture windows; they do not
independently establish what movement occurred on the wrist.

| Guided instruction | Windows with a native detection | Detections outside gesture windows |
| --- | --- | --- |
| Natural double pinch, first complete run | 5/8 | 0 |
| Natural double pinch, second complete run | 5/8 | 0 |
| More deliberate double pinch | 8/8 | 0 |
| Move the arm without pinching | 1/8 | 0 |

Incomplete runs are excluded. All events had value `2`, with no repeated timestamps when only
the non-wakeup variant was subscribed. The negative run contains a possible false positive.
These small samples establish usable native detections, not general accuracy or a sensitivity
improvement. The user reported that the final guided test felt satisfactory. No documented
Mobvoi sensitivity control was found.

## Integration and validation boundary

The player listens only with an assigned action in the current playback config, while resumed, focused, attached and outside ambient mode. Leaving the player or entering AOD unregisters the sensor; queued callbacks from the old subscription cannot dispatch. A process-owned timestamp filter suppresses already consumed events across pauses and Activity recreation. It does not persist across process death/reboot. Whether this firmware replays old events into a new process has not been established; discarding the first event unconditionally would lose a real gesture. Revised 2026-09-19: the Android contract for on-change sensors reports the current value on activation, so an event arriving within 300 ms of registration (`REPLAY_WINDOW_MS`, app clock only) is treated as that replay - it seeds the watermark and is not dispatched. A real double pinch that soon after the player takes focus is not a realistic gesture.

The existing playing/stopped configurations, pseudo-button code and capability wire values are unchanged. The Controls row was archived until 2026-09-19; it is now public, marked BETA. Availability READY means a supported input was found; subscription failures and missed detections remain possible and are logged.

The user subsequently confirmed playback control in Svartifoss, but reported intermittent misses
even with an active screen. There is no new device log for these misses. When testing, check Play/Pause with both configurations assigned, absence of
actions in AOD/menu/queue/home, and successful resubscription on return. Logs distinguish
`listening to Mobvoi sensor`, `Mobvoi double pinch received`, `configured player action dispatched`
and `player subscription stopped`. Verify actual phone playback as well as a dispatched command.

Samsung/Galaxy gesture integration is deferred; these Mobvoi observations do not establish it.

Validation of the initial native integration on 2026-09-12 with JDK 21:

- `./gradlew :wear:testGithubDebugUnitTest :wear:assembleGithubDebug`: passed, 178 watch tests,
  including nine new event-filter regression tests.
- `./gradlew test :wear:assembleGithubDebug`: passed on the initial native code across all current
  module/flavor variants (3,500 test executions, no failures or skips).
- Generated `wear/build/outputs/apk/github/debug/wear-github-debug.apk`; APK signature verified.

## Optional experimental detector and calibration

At the user's request, the action window now offers **Pinch settings**. Native remains the default.
The phone dialog uses the live app accent and separates detector selection, calibration and
sensitivity. Timing controls expand under **More adjustments**. Native mode hides the controls
that do not apply to it. Content scrolls within the available window height while Save/Cancel
remain visible, and action labels can wrap instead of being truncated. EN and pt-BR gesture
instructions are deliberately short; the Controls section still reports input availability.
Experimental mode uses gravity-removed acceleration plus a gyro rotation gate, two distinct
impulses, hysteresis, a 120 ms minimum impulse separation and a configurable command cooldown.
It runs instead of the native sensor, never alongside it. It requires a validated calibration.
The IMU reader requests 50 Hz, discards stale gyro input, and resets after gaps; the filter has a
400 ms warmup. Raising the sampling request does not establish better physical recognition.

The controls are global input preferences shared by playing/stopped assignments:

| Control | Default | Range | Meaning |
| --- | --- | --- | --- |
| Sensitivity | 100% | 50–200% | Scales the calibrated acceleration threshold, above a noise guard |
| Time between pinches | 800 ms | 250–1500 ms | Maximum separation of two distinct impulses |
| Time between commands | 1000 ms | 250–3000 ms | Suppresses further pairs after one detection |

These five `MiscPreferences` keys (`wear_hand_gesture_mode`, `wear_pinch_sensitivity`, `wear_pinch_max_gap`, `wear_pinch_cooldown`, `wear_pinch_calibration`) are global and EXPORTABLE, not face-scoped. There is no migration: absent mode is native, absent profile is uncalibrated. A user may save experimental without a profile (revised 2026-09-19: refusing it made Save look dead). The watch starts no detector until a valid profile exists and starts it on its own once one is saved; it also rejects an invalid/missing profile, including one arriving from a backup.

**Calibrate on watch** sends `/IdleMessages/OpenPinchCalibration` to the reachable watch. Keep Svartifoss open on the watch when requesting this; the OS can refuse background activity launches. The watch collects a countdown/warmup, 4 seconds of rest, six 3-second double-pinch windows separated by 2-second rests, then a 2-second transition and 6 seconds of arm movement without pinching (`PinchCalibrationTimeline`, shared by the sample filing and the screen). Raw samples remain only in memory. Pausing, losing focus or leaving display state ON interrupts capture - except that an interruption during the movement step, with at least ~2 s of movement recorded, fits what was captured, because that step is the one that makes Wear OS decide the wrist was lowered. KEEP_SCREEN_ON is cleared when capture finishes or stops. There are no music commands or vibrations during calibration or the 20-second test screen.

The fitter uses rest noise, a robust trial peak and pulse rotation. It rejects incomplete, corrupt or inseparable data and checks for at least one detection in two thirds of the usable trial windows (never fewer than three); a trial broken by a sensor gap is dropped, not fatal. Negative motion is judged by running the detector over it: the first version refused a profile if any single movement sample passed the gyro and amplitude gates, which ordinary arm movement does constantly, so almost every real calibration failed (reported 2026-09-19). Now only detector firings during rest or movement count against a profile, and the fit searches a grid of thresholds (60% down to 30% of the median attempt peak, above the noise guard) and rotation limits instead of fixing the threshold at 60% - that peak is often the arm, not the fingers. Ringing within 120 ms of a rise now extends that pulse instead of cancelling it. A refusal names its reason on the watch, shows the recognised count and taps per attempt, and logs the numbers; the capture screen draws the live motion/rotation signal against the target line. This fit check reuses training examples; it is not an accuracy estimate. **Test without actions** counts detections on fresh movements for the user to compare before saving. Calibration always starts with standard detector timing/sensitivity; the test uses the currently saved phone settings.

Saving sends a bounded, validated `PinchCalibrationMessage` from `pinch_calibration.proto`,
containing request ID and versioned profile. The phone persists it before echoing an ACK with
the same ID/profile; the watch matches source node as well. Saving is handled before the music
command inbox, so it cannot start playback or a foreground music service. A failed acknowledgement
keeps the result available for retry; it does not report success. The existing preference
coordinator then synchronizes the profile normally. No existing action/WatchInfo wire codes change.

The new controls are translated into every supported locale (`pinch_settings.xml` on the phone, `pinch_calibration.xml` on the watch); the watch's calibration screen shrinks a long translation to fit its line instead of truncating it, down to a floor, and only then ellipsizes. Both phone and watch APKs must be updated with the same signing key. A different wrist, strap fit or watch needs a new calibration. Physical accuracy, battery cost and UI interaction of this experimental path still require the user's device test; no device connection is currently available. Similar arm motion can still be indistinguishable from a pinch. Galaxy support remains deferred.

Validation of the experimental implementation on 2026-09-12 (JDK 21): affected common, watch
and phone debug suites passed; `./gradlew test :wear:assembleGithubDebug :mobile:assembleGithubDebug`
then passed with 3,552 executions across all variants, no failures or skips. The new tests cover
22 filter/detector/fitter cases and four profile-transfer/default/sync-registry cases. These are
automated tests, not a physical recognition benchmark. Both APK signatures were verified and
share the same signing certificate. Test copies are in `build/pinch-calibration/`:
`svartifoss-celular-calibracao.apk` and `svartifoss-relogio-calibracao.apk`.

Phone UI revision: an external Robolectric/API 33 rendering harness checked the actual settings
DialogFragment and the gesture-picker layout at 320/360 dp widths, EN/pt-BR, light/dark themes,
and font scales 1.0/1.3 with a custom pink accent. Its checks cover text height and ellipsis,
slider value/label baselines, footer bounds and minimum Save touch height. The picker uses a
sample action label/icon to exercise the layout; it does not run the real action configuration.
Fifteen renderings are saved in `build/pinch-calibration/ui-preview/`. This is simulated Android
rendering, not a screenshot or gesture test from the user's phone/watch. The harness is outside
the project source and adds no production dependency.

After the UI revision, `./gradlew test :mobile:assembleGithubDebug` passed (3,552 test executions,
no failures or skips). The phone-only update is `build/pinch-calibration/svartifoss-celular-interface.apk`
(4.0 / 81); its signature matches the previous calibration APKs. The existing watch calibration
APK remains compatible. The temporary render test is absent from the normal test output and APK.

## Persisted settings crash (2026-09-16)

Code inspection found a reproducible preference type mismatch: `PinchSettingsDialog` saved
sensitivity, gap and cooldown with `SharedPreferences.Editor.putInt`, but the phone dialog,
watch player controller and calibration test read them through `Preferences.getInt`, which
actually calls `SharedPreferences.getString`. Once saved and synced, these values cause a
`ClassCastException`, including when reopening the player even in native mode. Clearing app
data removes the offending keys but a subsequent sync can restore them.

`PinchPreferences` now reads both stored integers and strings, falls back for malformed values,
and clamps detector limits. Saving uses the project's standard string representation. Both
apps use this reader, so existing calibration and unrelated settings remain intact; no reset
or eager migration is necessary, including with an older phone still syncing integer values.
Regression tests exercise strict typed reads, old and mixed storage, save/read compatibility,
normalization and preservation of the profile. This establishes the code defect, not a match
to a captured crash from the user's watch; no device was connected during this investigation.

Validation: JDK 21 affected debug suites passed, followed by
`./gradlew test :wear:assembleGithubDebug :mobile:assembleGithubDebug` (successful).
The five new preference regression cases passed. Both debug APK signatures were verified and
match. Update copies are `build/pinch-calibration/svartifoss-celular-correcao-pinca.apk` and
`build/pinch-calibration/svartifoss-relogio-correcao-pinca.apk`. Physical calibration and
recognition still require testing on the watch.

## References

- [Wear SDK GestureInputManager](https://developer.android.com/reference/com/google/wear/input/GestureInputManager)
- [Android sensor reporting modes](https://source.android.com/docs/core/interaction/sensors/report-modes)
- [SensorManager registration parameters](https://developer.android.com/reference/android/hardware/SensorManager)
