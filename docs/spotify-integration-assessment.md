# Spotify integration — assessment

**Conclusion: do not build this.** Not for technical reasons — both routes are technically
straightforward — but because Spotify's own access rules make a sideloaded app by an individual
developer ineligible. Recorded here so the question does not get re-opened from first principles.

Researched 2026-08-07 against Spotify's published documentation and repositories. Their terms change;
re-check before reversing this.

## The problem it would have solved

Picking an artist from the watch (search result, saved shortcut, or library row) does not start
playback on Spotify. `playFromUri` on an artist only navigates, and `MediaBrowserPlayback` falls
through because Spotify rejects unknown browser clients. This is documented in
`player-integration-notes.md` and in `playDeepLink`'s own comments.

## Route A — Web API (`spotify/android-auth` + HTTPS)

Technically the better shape: `android-auth` is real Apache-2.0 source, actively maintained (last
push 2026-07), and the Web API is plain HTTPS with no binary dependency. `PUT /v1/me/player/play`
takes a context URI, so an artist would play properly.

**Blocked by quota mode.** From Spotify's *Quota modes* documentation:

- Newly-created apps start in **development mode**.
- Development mode allows **up to 5 authenticated users**, each of whom must be **manually added to
  an allowlist** in the developer dashboard.
- The app owner must have Spotify Premium for a development-mode app to function at all.

Escaping that requires **extended quota mode**, and since 15 May 2025 Spotify accepts those
applications only from organizations, never individuals. The published requirements are:

- A legally registered business entity
- An active, launched service
- **At least 250,000 monthly active users**
- Availability in key Spotify markets
- Commercial viability

Svartifoss meets none of these and structurally cannot. So this route is permanently capped at five
hand-allowlisted people — which is not a feature, it is a private build for the author and four
friends.

Two further constraints, worth recording independently:

- Playback control endpoints are **Premium-only**, stated on the endpoint reference itself.
- The endpoint reference carries a policy note: *"Streaming applications may not be commercial. The
  Spotify Platform can not be used to develop commercial streaming integrations."* Relevant beyond
  this decision: it would constrain any future paid tier or premium-unlock model that shipped a
  Spotify integration.

## Route B — App Remote SDK (`spotify/android-sdk`)

Controls the installed Spotify app directly, so it needs neither Web API quota nor network round
trips for metadata. It is the route that would actually fit how this app works.

Its costs:

- **It is a binary blob.** `app-remote-lib/` contains `spotify-app-remote-release-0.8.0.aar` (132KB),
  docs, and a 28-byte `build.gradle`. There is no source. The repository's Apache-2.0 licence covers
  the samples and the auth library, not this artifact. Shipping it puts a proprietary dependency
  inside an APK served from the project's own F-Droid repo — exactly what F-Droid's `NonFreeDep`
  anti-feature exists to flag.
- **It is stale.** Still labelled a Beta release; the repository's last push was 2024-08-19,
  two years before this assessment.
- **Premium is required** to play a track URI.
- **Unconfirmed, and decisive if true:** whether development mode's 5-user allowlist applies to App
  Remote as well as to the Web API. The quota-modes documentation is written about the Web API, and
  the App Remote README never mentions development mode, allowlists or quota. But App Remote still
  requires a client id registered in the same dashboard, against the same app record that carries the
  quota mode. **Verify this before spending any effort here** — if the cap applies, Route B dies for
  the same reason Route A does, and everything else about it is moot.

## What to do instead

Nothing Spotify-specific. The generic work has better returns and is not gated by anyone's approval:

- Library browsing (shipped) works with any player exposing a MediaBrowser tree.
- `MediaSessionCapabilities` lets transport features degrade honestly per app.
- The remaining gaps in `player-integration-notes.md` are addressable without a partner agreement.

Spotify users keep what already works: deep links open the app, and the existing `playDeepLink`
escalation plays precise entities. The artist case stays unsolved, and this document is the reason.
