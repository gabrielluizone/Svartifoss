# Play Console — Wear OS app Data Safety section

Answers for the **second** app's (com.svartifoss.wrfell) App content →
Data safety, based on what's actually in `wear/build.gradle` and
`wear/src/main/AndroidManifest.xml`.

**Caveat:** same as the phone app's doc - I can't browse the live Play
Console form from here, so double-check the exact wording/categories
against what the form shows.

---

## Does your app collect or share any of the required user data types?

**No.** Unlike the phone app, the watch app:
- Has no Firebase dependency at all (no Crashlytics, no Analytics) - confirmed
  in `wear/build.gradle`.
- Has no `INTERNET` permission in its manifest - it cannot make direct
  network requests to anything.
- Only exchanges data with the paired phone over the local Wearable Data
  Layer connection (Bluetooth/Wi-Fi Direct between the user's own two
  devices) - not a server, not a third party.

So every data type in the list (Location, Personal info, Financial info,
Health and fitness, Messages, Photos and videos, Audio files, Files and
docs, Calendar, Contacts, App activity, Web browsing, App info and
performance, Device or other IDs) should be answered **not collected, not
shared**.

## Standard follow-up questions

| Question | Answer |
|---|---|
| Does your app allow account creation? | My app does not allow users to create an account |
| Can users log in with an account created outside the app? | No |
| Do you provide a way for users to request data deletion? | No (nothing is collected to delete) |
| Do you use the data for advertising or sell it? | No - no ads SDK, nothing is sold |

If the form requires picking *some* category rather than skipping entirely
(older Play Console versions sometimes did), the closest honest option is
"App info and performance" limited to standard Android crash logs the OS
itself may capture for ANRs - but since Crashlytics isn't present, there's
no active crash-reporting pipeline the developer receives from this app.
Only pick that if the form doesn't otherwise let you answer "No data
collected."
