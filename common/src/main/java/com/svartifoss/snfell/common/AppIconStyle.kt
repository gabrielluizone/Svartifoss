package com.svartifoss.snfell.common

/** The two launcher backgrounds available in the Play Store build. */
enum class AppIconStyle(val preferenceValue: String) {
    SOLID("solid"),
    GRADIENT("gradient");

    companion object {
        /** Unknown or missing values use the Play build's branded default. */
        fun fromPreference(value: String?): AppIconStyle =
                entries.firstOrNull { it.preferenceValue == value } ?: GRADIENT
    }
}
