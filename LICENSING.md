# Licensing

Svartifoss is free/libre and open-source software, licensed under the
**GNU General Public License, version 3** — see [`COPYING`](COPYING). It is a
fork of [Music Center for Wear](https://github.com/matejdro/WearMusicCenter) by
matejdro, which is also GPLv3.

"Free" here refers to freedom, not price. Anyone may inspect, build, run,
modify and redistribute the source at no cost. What the app does at runtime is
described in [`docs/privacy-policy.md`](docs/privacy-policy.md).

A price on a store listing (for example a paid Google Play entry) pays only for
the convenience of a pre-built, signed, automatically updated binary. It does
not restrict any right the GPLv3 grants: a copy obtained that way is the same
GPLv3 software as a build from source, and may be redistributed freely.
Svartifoss ships **no** licence check, DRM, or Google Play Licensing (LVL)
code.

## Proprietary dependencies

Svartifoss links the proprietary **Google Play Services** and **Firebase**
client libraries:

- **Wearable Data Layer API** (`play-services-wearable`, `MessageClient` /
  `DataClient`) — the entire phone ⟷ watch transport. There is no free
  re-implementation, and removing it removes the app's reason to exist.
- **Firebase** — Crashlytics (user-optional; opt out in *Settings → Data &
  support → Privacy*), Analytics (Firebase's standard automatic events, with no
  account, name, email or content attached), Cloud Messaging (occasional
  developer announcements; opt out in the same place), and Auth + Firestore
  (used only when you submit or react to a Community theme).

These libraries are proprietary and their terms are not compatible with the GNU
GPL in the strict sense. Two facts reduce, but do not by themselves remove, the
tension:

1. The dependency is **inherited** — Music Center for Wear has always used the
   Wearable Data Layer API.
2. On the Google-certified Android and Wear OS devices Svartifoss targets,
   Google Play Services is a pre-installed platform component — the situation
   the GPLv3 section 1 "System Libraries" clause describes. Whether it strictly
   qualifies is a judgement call the Free Software Foundation has not ruled on.

## Additional permission under GNU GPL version 3, section 7

The following additional permission is granted by **Gabriel Luiz**, copyright
holder of the modifications that make up Svartifoss, and applies **only to the
portions of Svartifoss in which Gabriel Luiz holds copyright**:

> If you modify Svartifoss, or any covered work, by linking or combining it
> with the proprietary Google Play Services and Firebase client libraries —
> libraries in the `com.google.android.gms.*` and `com.google.firebase.*`
> namespaces, including in particular the Wearable Data Layer API
> (`play-services-wearable`) — or with a modified version of those libraries,
> the licensor grants you additional permission to convey the resulting work.
> The GNU GPL version 3 continues to govern the covered portions themselves.

This permission does **not** extend to code for which copyright is held by
others — in particular the code inherited from Music Center for Wear by
matejdro, which remains under the GNU GPL version 3 with no such exception. As
permitted by section 7, you may remove this additional permission from your
copy of the covered portions, or carry it forward.

## Still outstanding

Full clarity for distributing a combined binary also requires the same section
7 additional permission from the upstream copyright holders (matejdro and any
other Music Center for Wear contributors), or a determination that the System
Library clause applies. A request for that upstream permission is drafted in
[`docs/upstream-linking-exception-request.md`](docs/upstream-linking-exception-request.md).
Any upstream copyright holder who objects to the combination described here is
invited to write to **gabrielsvafoss@gmail.com**.

## Bundled fonts

Third-party fonts bundled with the app carry their own licences, reproduced
under [`licenses/`](licenses/).
