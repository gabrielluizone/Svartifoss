package com.svartifoss.snfell.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

private fun photoMatrix(
        saturation: Float = 1f,
        contrast: Float = 1f,
        brightness: Float = 0f,
        redScale: Float = 1f,
        greenScale: Float = 1f,
        blueScale: Float = 1f,
        redOffset: Float = 0f,
        greenOffset: Float = 0f,
        blueOffset: Float = 0f
): FloatArray {
    val inverse = 1f - saturation
    val r = 0.213f * inverse
    val g = 0.715f * inverse
    val b = 0.072f * inverse
    val contrastOffset = 127.5f * (1f - contrast) + brightness
    return floatArrayOf(
            (r + saturation) * contrast * redScale, g * contrast * redScale,
            b * contrast * redScale, 0f, contrastOffset + redOffset,
            r * contrast * greenScale, (g + saturation) * contrast * greenScale,
            b * contrast * greenScale, 0f, contrastOffset + greenOffset,
            r * contrast * blueScale, g * contrast * blueScale,
            (b + saturation) * contrast * blueScale, 0f, contrastOffset + blueOffset,
            0f, 0f, 0f, 1f, 0f)
}

/**
 * Lightweight photo-editor treatments for album artwork. The same 4x5 matrices feed Android
 * Views on the watch and Canvas in the phone preview, so a filter never becomes a second bitmap
 * pipeline that can drift between the two devices.
 */
enum class AlbumArtFilter(
        val preferenceValue: String,
        val matrixValues: FloatArray?
) {
    NONE("none", null),
    MONOCHROME("monochrome", photoMatrix(saturation = 0f)),
    WARM("warm", photoMatrix(saturation = 1.08f, contrast = 1.04f,
            redScale = 1.09f, blueScale = .91f, redOffset = 5f)),
    COOL("cool", photoMatrix(saturation = 1.04f, contrast = 1.03f,
            redScale = .92f, greenScale = 1.02f, blueScale = 1.10f, blueOffset = 5f)),
    GOLDEN("golden", photoMatrix(saturation = 1.12f, contrast = 1.04f, brightness = 3f,
            redScale = 1.12f, greenScale = 1.04f, blueScale = .82f, redOffset = 7f)),
    ROSE("rose", photoMatrix(saturation = 1.02f, contrast = .98f, brightness = 4f,
            redScale = 1.10f, greenScale = .96f, blueScale = 1.02f, redOffset = 7f)),
    VINTAGE("vintage", photoMatrix(saturation = .72f, contrast = .92f, brightness = 5f,
            redScale = 1.08f, greenScale = 1.01f, blueScale = .84f,
            redOffset = 8f, greenOffset = 3f)),
    FADED("faded", photoMatrix(saturation = .70f, contrast = .78f, brightness = 13f,
            redScale = 1.04f, blueScale = .96f)),
    MATTE("matte", photoMatrix(saturation = .86f, contrast = .82f, brightness = 17f,
            redScale = 1.02f, blueScale = .98f)),
    VIVID("vivid", photoMatrix(saturation = 1.38f, contrast = 1.08f, brightness = 2f)),
    PUNCH("punch", photoMatrix(saturation = 1.20f, contrast = 1.24f, brightness = -4f)),
    PASTEL("pastel", photoMatrix(saturation = .76f, contrast = .78f, brightness = 20f,
            redScale = 1.04f, greenScale = 1.02f, blueScale = 1.06f)),
    SEPIA("sepia", floatArrayOf(
            .393f, .769f, .189f, 0f, 0f,
            .349f, .686f, .168f, 0f, 0f,
            .272f, .534f, .131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f)),
    CYANOTYPE("cyanotype", floatArrayOf(
            .10f, .18f, .07f, 0f, -8f,
            .16f, .36f, .14f, 0f, 5f,
            .22f, .50f, .28f, 0f, 18f,
            0f, 0f, 0f, 1f, 0f)),
    TEAL_ORANGE("teal_orange", floatArrayOf(
            1.08f, .08f, -.10f, 0f, 5f,
            -.04f, 1.02f, .04f, 0f, 0f,
            -.08f, .14f, 1.02f, 0f, 4f,
            0f, 0f, 0f, 1f, 0f)),
    HIGH_CONTRAST("high_contrast", photoMatrix(saturation = 1.05f, contrast = 1.38f, brightness = -6f)),
    SOFT_LIGHT("soft_light", photoMatrix(saturation = .92f, contrast = .86f, brightness = 12f,
            redScale = 1.03f, blueScale = 1.02f)),
    NIGHT("night", photoMatrix(saturation = .82f, contrast = 1.12f, brightness = -13f,
            redScale = .82f, greenScale = .94f, blueScale = 1.16f, blueOffset = 7f)),
    MOSS("moss", photoMatrix(saturation = .86f, contrast = 1.02f,
            redScale = .84f, greenScale = 1.10f, blueScale = .82f,
            greenOffset = 7f)),
    LAVENDER("lavender", photoMatrix(saturation = .78f, contrast = .98f, brightness = 5f,
            redScale = 1.05f, greenScale = .90f, blueScale = 1.12f,
            redOffset = 5f, blueOffset = 8f)),
    CHERRY("cherry", photoMatrix(saturation = 1.16f, contrast = 1.06f,
            redScale = 1.14f, greenScale = .86f, blueScale = .92f,
            redOffset = 8f)),
    DEEP_SEA("deep_sea", photoMatrix(saturation = .92f, contrast = 1.10f, brightness = -4f,
            redScale = .76f, greenScale = 1.02f, blueScale = 1.16f,
            blueOffset = 9f)),
    DUST("dust", photoMatrix(saturation = .56f, contrast = .88f, brightness = 9f,
            redScale = 1.06f, greenScale = .98f, blueScale = .90f,
            redOffset = 7f, blueOffset = -2f)),
    BLEACH("bleach", photoMatrix(saturation = .18f, contrast = 1.28f, brightness = 8f)),
    MOONLIGHT("moonlight", photoMatrix(saturation = .62f, contrast = 1.08f, brightness = -12f,
            redScale = .78f, greenScale = .90f, blueScale = 1.20f, blueOffset = 10f)),
    DREAM("dream", photoMatrix(saturation = .68f, contrast = .72f, brightness = 24f,
            redScale = 1.06f, greenScale = 1.02f, blueScale = 1.10f)),
    INFRARED("infrared", floatArrayOf(
            .92f, -.18f, -.08f, 0f, 18f,
            .10f, .48f, -.04f, 0f, -4f,
            -.10f, -.06f, .58f, 0f, 28f,
            0f, 0f, 0f, 1f, 0f)),
    FOREST("forest", photoMatrix(saturation = 1.12f, contrast = 1.06f,
            redScale = .82f, greenScale = 1.12f, blueScale = .88f,
            greenOffset = 5f)),
    SILVER("silver", photoMatrix(saturation = .08f, contrast = 1.08f, brightness = 5f,
            redScale = 1.04f, greenScale = 1.02f, blueScale = 1.08f,
            blueOffset = 4f)),
    CANDY("candy", photoMatrix(saturation = 1.08f, contrast = .88f, brightness = 16f,
            redScale = 1.08f, greenScale = .94f, blueScale = 1.08f,
            redOffset = 8f, blueOffset = 8f));

    /** Lazily built because JVM policy tests inspect the matrices without Android graphics. */
    val androidColorFilter: ColorMatrixColorFilter? by lazy {
        matrixValues?.let { ColorMatrixColorFilter(ColorMatrix(it)) }
    }

    /** Bakes this filter into a bitmap for Compose-owned cover windows. */
    fun applyTo(source: Bitmap): Bitmap {
        val filter = androidColorFilter ?: return source
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = filter
        })
        return output
    }

    companion object {
        fun fromPreference(value: String?): AlbumArtFilter =
                entries.firstOrNull { it.preferenceValue == value } ?: NONE
    }
}

/**
 * Resolves the independent Filter layer while preserving old `album_art_style=filter_*` themes.
 * A deliberately chosen Filter wins over the legacy alias; `none` leaves structural monochrome
 * styles intact and otherwise means the old style's filter when reading an old configuration.
 */
fun resolveAlbumArtFilter(filterPreference: String?, style: PlayerBackgroundStyle): AlbumArtFilter =
        AlbumArtFilter.fromPreference(filterPreference).let { selected ->
            if (selected != AlbumArtFilter.NONE) selected else style.artworkFilter
        }
