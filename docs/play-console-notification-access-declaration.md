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
> Android exposes "what's currently playing on this phone" through
> `MediaSessionManager.getActiveSessions()`, and that API requires the
> caller to hold notification-listener access. When the user selects
> "From current media app" for the quick-actions panel, Svartifoss also
> mirrors up to four buttons from the active media notification so that the
> watch uses the same actions and icons as the phone player.
>
> The app's `NotificationListenerService` implementation filters posted
> notifications to media playback notifications only. From the current
> media notification it reads only action labels, action icons and their
> local `PendingIntent`s. It keeps those intents in process memory, sends
> only the labels/icons to the paired watch, and invokes an intent when the
> user taps the corresponding watch button. It does not inspect or retain
> message bodies, emails or unrelated notification content.
>
> the local Wearable Data Layer connection (Bluetooth/Wi-Fi Direct between
> the user's own two devices). It is never sent to any server we operate,
> and no notification content of any kind leaves the device.
> Playback metadata and media-action labels/icons are sent only to the
> user's own paired Wear OS watch over the Wearable Data Layer connection.
> They are never sent to any server we operate.
> the local Wearable Data Layer connection (Bluetooth/Wi-Fi Direct between
> the user's own two devices). It is never sent to any server we operate,
> and no notification content of any kind leaves the device.

## If asked "does your app read notification content?"

> Svartifoss does not read message, email or unrelated notification content.
> It reads media-session playback metadata and, only for the active media
> notification, the action labels/icons needed to reproduce its controls on
> the paired watch.

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
5. Select Watch → Panels → Quick actions source → From current media app,
   open the watch quick-actions panel, and show that its four controls match
   the current media notification.
