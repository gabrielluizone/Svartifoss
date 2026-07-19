package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AodPresentationTest {
    @Test
    fun treatmentParserUsesBlurAsSafeBackwardCompatibleDefault() {
        assertEquals(AodArtTreatment.BLUR, AodArtTreatment.fromPreference(null))
        assertEquals(AodArtTreatment.BLUR, AodArtTreatment.fromPreference("unknown"))
        assertEquals(AodArtTreatment.CLEAR, AodArtTreatment.fromPreference("clear"))
        assertEquals(
                AodArtTreatment.MONOCHROME_BLUR,
                AodArtTreatment.fromPreference("monochrome_blur"))
        assertEquals(AodArtTreatment.FOLLOW, AodArtTreatment.fromPreference("follow"))
    }

    @Test
    fun explicitTreatmentsResolveTheirOwnBlurAndColorPolicy() {
        val blur = resolveAodArtwork(true, "classic", AodArtTreatment.BLUR, "hidden")
        assertTrue(blur.visible)
        assertTrue(blur.blurred)
        assertFalse(blur.monochrome)

        val clear = resolveAodArtwork(true, "material", AodArtTreatment.CLEAR, "blur_bw")
        assertTrue(clear.visible)
        assertFalse(clear.blurred)
        assertFalse(clear.monochrome)

        val mono = resolveAodArtwork(
                true, "expressive", AodArtTreatment.MONOCHROME_BLUR, "cover")
        assertTrue(mono.visible)
        assertTrue(mono.blurred)
        assertTrue(mono.monochrome)
    }

    @Test
    fun followMirrorsEveryInteractiveArtworkVariant() {
        assertEquals(
                AodArtworkSpec(visible = true),
                resolveAodArtwork(true, "classic", AodArtTreatment.FOLLOW, "cover"))
        assertEquals(
                AodArtworkSpec(visible = true, blurred = true),
                resolveAodArtwork(true, "classic", AodArtTreatment.FOLLOW, "blur"))
        assertEquals(
                AodArtworkSpec(visible = true, monochrome = true),
                resolveAodArtwork(true, "classic", AodArtTreatment.FOLLOW, "bw"))
        assertEquals(
                AodArtworkSpec(visible = true, blurred = true, monochrome = true),
                resolveAodArtwork(true, "classic", AodArtTreatment.FOLLOW, "blur_bw"))
        assertEquals(
                AodArtworkSpec(visible = true, blurred = true),
                resolveAodArtwork(true, "classic", AodArtTreatment.FOLLOW, "expressive"))
        assertFalse(resolveAodArtwork(
                true, "classic", AodArtTreatment.FOLLOW, "hidden").visible)
        assertFalse(resolveAodArtwork(
                true, "classic", AodArtTreatment.FOLLOW, "eclipse").visible)
    }

    @Test
    fun minimalEclipseAndDisabledArtNeverRequestArtworkWork() {
        assertEquals(
                AodArtworkSpec(visible = false),
                resolveAodArtwork(true, "minimal", AodArtTreatment.BLUR, "cover"))
        assertEquals(
                AodArtworkSpec(visible = false),
                resolveAodArtwork(true, "eclipse", AodArtTreatment.BLUR, "cover"))
        assertEquals(
                AodArtworkSpec(visible = false),
                resolveAodArtwork(false, "material", AodArtTreatment.BLUR, "cover"))
    }
}
