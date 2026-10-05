# Tips in the Play Store build

The Play build can take a tip from someone who wants to say thanks. It is the Play build's replacement for the Buy Me a Coffee and Ko-fi buttons, which exist only in the GitHub build. This page is for whoever sets up Play Console and for whoever touches the code next; it is not user-facing.

## What it is, and what it is not

- The Play listing is a paid app, so whoever installed it already has every feature. **A tip unlocks nothing**, and the app is built so that it cannot: no code outside the `play` flavor can even name the billing library, which `FlavorSupportIsolationTest` pins. Keep it that way. If a tip ever gated anything it would stop being a tip, it would need server-side verification, and the privacy policy and the Play Console declarations would all change.
- It is sold through **Google Play Billing**. Play's Payments policy requires that for anything sold inside a Play listing: an in-app button, link or message that leads to any other way of paying is not allowed (section 4, with exceptions that do not cover a person taking tips). That is why Buy Me a Coffee and Ko-fi are not hidden in the Play build but absent from it - their strings, icons and URLs live in `mobile/src/github` and the Play artifact contains none of them. The README and the website may still link to them: that is outside the app.
- The amounts are **not in the code**. Each product's price is set in Play Console, per country, and the app shows the string Play formatted for the account (`R$ 10,00`, `$1.99`). Repricing a tier, or adding a country's price, never needs a release.
- Wording is "tip" and "support", never "donation": a donation has a legal meaning for non-profits that this is not.

## Products to create in Play Console

Four **one-time products** (Monetize with Play → Products → One-time products; older Consoles call them In-app products). Product ids are permanent - a deleted id can never be reused - so the neutral names below survive a repricing:

| Product id | Base price (US dollars) |
|---|---|
| `support_tip_1` | US$ 1 |
| `support_tip_2` | US$ 2 |
| `support_tip_3` | US$ 5 |
| `support_tip_4` | US$ 10 |

Set the price in dollars and let Play convert it for every other country and currency (Brazil included); review the converted prices in the Console, since Play rounds them to its own price points.

- Make each one **Active** and give it a purchase option with a price. The name and description shown on Play's purchase sheet come from the product, so write them as a tip ("Tip for the developer") and not as a feature.
- The Console has no "consumable" switch. A product is consumable because the app consumes it, which this app does after every successful purchase - that is what lets the same person tip twice, and it also acknowledges the purchase. A purchase that is never acknowledged is refunded by Play after three days, so a failed consume is retried and then picked up again at the next start.
- Create as many as you like up to four; the app lists the ones that exist, cheapest first, and **hides the whole option** while none can be bought (no products yet, Play not available, no Play Store on the phone). To add a fifth tier, add its id to `TipProducts.IDS` and to this table - `FlavorSupportIsolationTest` fails until the guide lists every id the code asks for.
- Play's service fee on these products is "15% or lower" under its reduced-fee programs, depending on the account; confirm the rate shown in your own Console.
- **The command line cannot create them.** `publishPlayReleaseProducts` (Gradle Play Publisher 3.12.1) calls the legacy `inappproducts` endpoint, which Play answers for this app with `403 PERMISSION_DENIED: Please migrate to the new publishing API` (checked on 2026-10-05 with `bootstrapPlayReleaseListing --products`, which also shows the service account itself is accepted). So the products are created in the Console, or through the newer `monetization.onetimeproducts` REST API, which the plugin does not use.
- **Upload first, create the products after.** The Console normally refuses to create a product until a build that declares the `com.android.vending.BILLING` permission has been uploaded to some track, which is what the `play` flavor now does and the `github` flavor does not.

The form (Monetize with Play → Products → One-time products → Create one-time product). Everything is the same on all four except the product ID and the price:

| Field | Value |
|---|---|
| Product ID | `support_tip_1` to `support_tip_4` (permanent) |
| Name | English only, one per tier (up to 55 characters; Play recommends 20 or fewer so it fits one line): `Tip for the developer` (1, as created), `Silver Splash tip` (2), `Golden Cascade tip` (3), `Diamond Falls tip` (4). The names play on Svartifoss, "Black Falls", and keep the word *tip* so a payment sheet or a bank statement says what was bought |
| Description | English only, under 80 characters, no promise of a reward and no repeated disclaimer: (1) `A thank-you tip. It unlocks nothing; everything is already yours :D` as created; (2) `A little shine for the dev's day. Thanks for making waves!`; (3) `Golden thanks that keep the music flowing. You're awesome!`; (4) `Diamond-grade gratitude, straight from the heart. You're a legend!` |
| Tags (optional) | `tip`; nothing in the app reads it |
| Icon (optional) | 32-bit PNG, 512 x 512 px, up to 1 MB. Play's guidance asks for no text, promotion or branding; the icon in use is the app logo alone, centred on the accent colour (`#55776F`) at about a quarter of the tile, at the owner's choice. The tiers grow with the amount: `support_tip_1` is the plain accent colour, `support_tip_2` a silver plate, `support_tip_3` gold and `support_tip_4` a faceted diamond blue, each with the same logo and no text. If Play ever flags it as branding, a plain white heart on the same colour is the fallback |
| Purchase option ID | `buy` on all four (a separate field from the product ID: lowercase letters, digits and hyphens only, **no underscores**, so `support_tip_2` is refused here; it only has to be unique within a product) |
| Purchase type | Buy |
| Advanced options → Quantities and limits | leave **off**: the app consumes one purchase per tap and ignores a quantity |
| Advanced options → Classification (digital content or service) | pick the one the Console's own description fits best; it does not affect the app |
| Price | the base price in US dollars from the table above, then convert the other regions; activate the product |

There is nothing to fill in for age rating: the product page shows a read-only **Age rating (shown in applicable US states)** row, inherited from the app's own rating (App content → Content rating), which reads *All ages*. Adding purchases can make the questionnaire ask to be revisited; it is about the in-app purchases label, not the rating itself.

## Testing

Play Billing only works with a build Play knows about, so a debug APK installed with `adb` will find no products and the option will stay hidden - which is the correct behaviour, not a bug.

1. Upload a `play` release bundle (`./gradlew :mobile:bundlePlayRelease`) to the internal testing track and install it from the testing link on a device whose Play account is a **license tester** (Play Console → Settings → License testing). Purchases by license testers are free test purchases.
2. Open the drawer: the **Support** section appears once Play answers. Tap **Support the developer**, pick an amount, complete Play's sheet. A toast thanks you, and the purchase is consumed (check Order management: it should not stay as an unconsumed purchase).
3. Buy again: it must work, because the first purchase was consumed. Cancel Play's sheet: nothing is shown.

Three things that make a correct build look broken:

- **A draft release reaches nobody.** The default upload status is `draft`; the testers get the build only after the release is reviewed and rolled out to the internal track in the Console.
- **A sideloaded copy cannot be updated by Play.** Play re-signs with its own key, not `release.keystore`, so a phone that has the `github` build (or any build installed with `adb`) refuses the Play install with a signature conflict. Export a settings backup, uninstall, then install from the testing link - on the watch too, because a Play phone app next to a sideloaded watch app will not pair (different signing keys).
- **New products take a while.** Play can need minutes to hours before a freshly activated product reaches the Billing library; until then the option stays hidden.

## What the code does

- `support/TipJar.kt` is the process-wide connection to Play. It connects once, reads the products, settles any tip that was paid for and never consumed (the app was killed between the payment and the consume), and starts purchases. Pending payments (cash at a shop, a bank slip) are enabled, as Billing requires for one-time products: they are announced once and consumed only when Play reports them purchased.
- `support/TipLedger.kt` is the pure bookkeeping around that - who is thanked, what is consumed, what is left alone - and is what the JVM tests (`mobile/src/testPlay`) pin: a tip is thanked once, a failed consume is retried without thanking twice, a pending payment is not consumed, and **nothing but our own four products is ever consumed**, because consuming something else the person paid for would hand it back for sale.
- `support/TipDialog.kt` is the amounts dialog and `support/DrawerSupportSection.kt` wires the drawer; the github flavor has its own `DrawerSupportSection` with the two links. `MainActivity` only calls `DrawerSupportSection.bind`.
- Nothing is sent anywhere by the app: Play handles the payment, and the app is handed only a purchase token and an order id, which stay on the phone. There is deliberately no server and no verification of purchases, because a forged tip unlocks nothing.

## Declarations that follow from this

- **Store listing**: Play shows "Offers in-app purchases" once the bundle carries the Billing permission.
- **Data safety**: the app never receives payment details, and the purchase token and order id are not transmitted, so *Financial info* stays "not collected". Re-check against the live form before submitting, as the wording changes.
- **Privacy policy**: `docs/privacy-policy.md` and its hand-copied `docs/privacy-policy.html` describe it under *Tips in the Play Store build*.
- **Billing library**: Play stops accepting releases on an old major version roughly every year (version 8 or newer is required for new releases since 31 August 2026). The version is `google-billing` in `libs.toml`; when it moves, build the `play` flavor and run the `testPlay` suite.
