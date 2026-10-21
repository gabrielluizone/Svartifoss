package com.svartifoss.snfell.common

/**
 * The launcher icons the Play Store build lets the user choose between. One choice, synced from
 * the phone to the watch, so each style is really a pair of icons - the phone's and the watch's.
 *
 * - [SOLID]: the original flat background.
 * - [GRADIENT]: the Play build's branded default.
 * - [CREAM]: the same two colours on both devices with their roles swapped. The phone's icon is
 *   charcoal with a cream mark, the watch's is cream with a charcoal mark (`launcher_charcoal` and
 *   `launcher_cream` in `common`'s colours), which is the polarity each device already used for its
 *   other styles - a light mark on the phone, a dark one on the watch.
 * - [FEATHER]: Charcoal Feather (Portuguese: Pena de Carvão), after the haibane - the "charcoal
 *   feathers" - of the anime Haibane Renmei. Four colours rather than a swapped pair, still in the
 *   polarity above: the phone's icon is a dark green (#435244) with a warm grey mark (#BCB8AD), the
 *   watch's a pale yellow (#F5F3D9) with a brown mark (#6C5E3B) - the `launcher_feather_*` colours in
 *   `common`. The preference value is `feather` because values are `[a-z]+` protocol identifiers.
 *
 * Every style is an `<activity-alias>` in each module's `src/play` manifest, and [aliasName] is
 * what both launcher controllers address. The launcher only ever sees one of them enabled.
 * `AppIconStyleContractTest` pins that the enum, the manifests, the icon resources, the picker
 * arrays and the translations all list the same styles.
 */
enum class AppIconStyle(val preferenceValue: String, val aliasName: String) {
    SOLID("solid", ".LauncherIconSolid"),
    GRADIENT("gradient", ".LauncherIconGradient"),
    CREAM("cream", ".LauncherIconCream"),
    FEATHER("feather", ".LauncherIconFeather");

    companion object {
        /** What a new install, an unknown value and a missing value all resolve to. */
        val DEFAULT = GRADIENT

        /**
         * Unknown or missing values use the Play build's branded default. That also keeps an older
         * build safe when a newer phone syncs a style it has never heard of: it shows the default
         * rather than nothing.
         */
        fun fromPreference(value: String?): AppIconStyle =
                entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT
    }
}
