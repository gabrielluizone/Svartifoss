package com.svartifoss.snfell.review

import androidx.appcompat.app.AppCompatActivity

/**
 * GitHub (sideload) build: there is no rating prompt. The in-app review card is a service of the
 * Google Play Store app, so a build that did not come from the Store has nothing to ask and nowhere
 * to post a review - and the library that talks to it is a `play`-only dependency, which is why
 * this is an inert twin rather than the same code guarded by a flag. The Play implementation, and
 * the reasons it asks the way it does, live in `src/play`; `src/main` (MainActivity, the Developer
 * settings) only ever calls this object. FlavorReviewIsolationTest pins the split.
 */
object ReviewPrompt {

    const val SUPPORTED = false

    @Suppress("UNUSED_PARAMETER")
    fun attach(activity: AppCompatActivity) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun showNow(activity: AppCompatActivity) = Unit
}
