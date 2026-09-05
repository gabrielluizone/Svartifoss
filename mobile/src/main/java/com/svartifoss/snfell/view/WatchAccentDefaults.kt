package com.svartifoss.snfell.view

/**
 * The static neutral watch accent: what every phone-side surface draws with before an album
 * accent or a custom colour exists - the preview's fallback triad, the colour-treatment
 * swatches, and the colour picker's default.
 *
 * Mirror of `WatchTheme.ACCENT_DEFAULT` on the wear side (and of wear's `theme_accent` /
 * `common`'s `accent` colour resource). `mobile` cannot depend on `wear`, so this is the one
 * place the phone restates that value - deliberately a single constant rather than a copy per
 * call site, because four copies had already drifted to `0xFF86A69D`, a slightly different sage
 * matching neither the watch's real default nor either Lyra accent. Nothing threw; the default
 * swatch in the colour picker simply never quite agreed with the preview beside it.
 *
 * It is deliberately *not* `R.color.lyra_accent`, which is the phone's own accent and is
 * mode-aware (`#55776F` light, `#87A89F` dark). What the watch renders does not change with the
 * phone's light/dark setting, so resolving this against the phone's theme would make the
 * preview disagree with the wrist in one of the two modes.
 */
internal const val NEUTRAL_WATCH_ACCENT = 0xFF87A89F.toInt()
