# Play Console — Notification Access permission declaration

Use this text when Play Console asks you to justify the
`BIND_NOTIFICATION_LISTENER_SERVICE` ("Notification access") permission,
under **App content → Permissions declaration**.

If the form gives you a category/checkbox list first, pick the option
closest to **"reading media session / now-playing information to mirror
or control playback on another device"** (wording varies by form version).
Always fill in the free-text "core use case" box with the text below —
that's what a human reviewer actually reads.

---

## Core use case (free-text box)

> Svartifoss is a Wear OS companion app: it mirrors whatever music is
> currently playing on the phone to a paired watch, and lets the watch
> send back transport controls (play/pause, skip, volume, seek).
>
> Android only exposes "what's currently playing on this phone" through
> `MediaSessionManager.getActiveSessions()`, and that API requires the
> caller to hold notification-listener access as a prerequisite — this is
> the sole reason the permission is requested.
>
> The app's `NotificationListenerService` implementation
> (`NotificationService.kt`) does **not** override `onNotificationPosted`
> or `onNotificationRemoved`, and never calls `getActiveNotifications()`.
> It does not read, store, or transmit the content of any notification
> from any app. The only thing read is standard media-session metadata
> (track title, artist, album art, playback position/state, shuffle/repeat
> state) exposed by whichever app is currently playing music — the same
> information already shown on the phone's lock screen and in the system
> media player.
>
> This metadata is sent only to the user's own paired Wear OS watch, over
> the local Wearable Data Layer connection (Bluetooth/Wi-Fi Direct between
> the user's own two devices). It is never sent to any server we operate,
> and no notification content of any kind leaves the device.

## If asked "does your app read notification content?"

> No. The app reads media-session playback metadata via
> `MediaSessionManager`, not notification content via
> `NotificationListenerService.getActiveNotifications()` or
> `onNotificationPosted()`. Neither of the latter two APIs is used
> anywhere in the codebase.

## If asked for a demo video / test instructions

Reviewers may ask for a short screen recording or step-by-step
instructions showing the permission in use, since the feature requires a
paired Wear OS watch to fully demonstrate. Suggested steps to include:

1. Play any song in any music app on the test phone.
2. Open Svartifoss → Settings → grant notification access when prompted
   (Android will show its standard "Allow Svartifoss to have notification
   access?" system dialog — this is the exact permission being declared).
3. Show the now-playing screen on the paired Wear OS watch mirroring the
   phone's track/artist/art in real time.
4. Optionally, press a transport control on the watch and show playback
   change on the phone, to demonstrate the two-way (not just read-only)
   nature of the feature.
