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
enum class AlbumArtFilter(val matrixValues: FloatArray?) {
    NONE(null),
    MONOCHROME(photoMatrix(saturation = 0f)),
    WARM(photoMatrix(saturation = 1.08f, contrast = 1.04f,
            redScale = 1.09f, blueScale = .91f, redOffset = 5f)),
    COOL(photoMatrix(saturation = 1.04f, contrast = 1.03f,
            redScale = .92f, greenScale = 1.02f, blueScale = 1.10f, blueOffset = 5f)),
    GOLDEN(photoMatrix(saturation = 1.12f, contrast = 1.04f, brightness = 3f,
            redScale = 1.12f, greenScale = 1.04f, blueScale = .82f, redOffset = 7f)),
    ROSE(photoMatrix(saturation = 1.02f, contrast = .98f, brightness = 4f,
            redScale = 1.10f, greenScale = .96f, blueScale = 1.02f, redOffset = 7f)),
    VINTAGE(photoMatrix(saturation = .72f, contrast = .92f, brightness = 5f,
            redScale = 1.08f, greenScale = 1.01f, blueScale = .84f,
            redOffset = 8f, greenOffset = 3f)),
    FADED(photoMatrix(saturation = .70f, contrast = .78f, brightness = 13f,
            redScale = 1.04f, blueScale = .96f)),
    MATTE(photoMatrix(saturation = .86f, contrast = .82f, brightness = 17f,
            redScale = 1.02f, blueScale = .98f)),
    VIVID(photoMatrix(saturation = 1.38f, contrast = 1.08f, brightness = 2f)),
    PUNCH(photoMatrix(saturation = 1.20f, contrast = 1.24f, brightness = -4f)),
    PASTEL(photoMatrix(saturation = .76f, contrast = .78f, brightness = 20f,
            redScale = 1.04f, greenScale = 1.02f, blueScale = 1.06f)),
    SEPIA(floatArrayOf(
            .393f, .769f, .189f, 0f, 0f,
            .349f, .686f, .168f, 0f, 0f,
            .272f, .534f, .131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f)),
    CYANOTYPE(floatArrayOf(
            .10f, .18f, .07f, 0f, -8f,
            .16f, .36f, .14f, 0f, 5f,
            .22f, .50f, .28f, 0f, 18f,
            0f, 0f, 0f, 1f, 0f)),
    TEAL_ORANGE(floatArrayOf(
            1.08f, .08f, -.10f, 0f, 5f,
            -.04f, 1.02f, .04f, 0f, 0f,
            -.08f, .14f, 1.02f, 0f, 4f,
            0f, 0f, 0f, 1f, 0f)),
    HIGH_CONTRAST(photoMatrix(saturation = 1.05f, contrast = 1.38f, brightness = -6f)),
    SOFT_LIGHT(photoMatrix(saturation = .92f, contrast = .86f, brightness = 12f,
            redScale = 1.03f, blueScale = 1.02f)),
    NIGHT(photoMatrix(saturation = .82f, contrast = 1.12f, brightness = -13f,
            redScale = .82f, greenScale = .94f, blueScale = 1.16f, blueOffset = 7f));

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
}
