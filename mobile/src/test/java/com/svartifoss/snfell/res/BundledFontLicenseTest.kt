package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the legal and packaging halves of every font addition together.
 *
 * A font is rendered by the phone preview and by the watch from two different APKs. It is easy
 * to add one copy, or to add the binary without its attribution, and only discover the mismatch
 * after a release. These are intentionally checked as files rather than through generated R ids:
 * the license directory is the source-of-truth artifact reviewers and release builders can audit.
 */
class BundledFontLicenseTest {

    private val addedFonts = listOf(
            "lato",
            "merriweather_sans",
            "source_sans_3",
            "source_serif_4",
            "libre_baskerville",
            "libre_franklin",
            "ibm_plex_sans",
            "ibm_plex_mono",
            "ibm_plex_serif",
            "work_sans",
            "karla",
            "sora",
            "urbanist",
            "be_vietnam_pro",
            "red_hat_display",
            "red_hat_text",
            "red_hat_mono",
            "syne",
            "instrument_sans",
            "figtree",
            "gabarito",
            "bricolage_grotesque",
            "dm_serif_display",
            "cormorant_garamond",
            "cormorant",
            "bitter",
            "spectral",
            "vollkorn",
            "raleway",
            "league_spartan",
            "saira",
            "signika",
            "sofia_sans",
            "overpass",
            "public_sans",
            "questrial",
            "dosis",
            "prompt",
            "unbounded",
            "recursive",
            "trispace",
            "noto_sans",
            "noto_serif",
            "noto_sans_mono",
            "heebo",
            "hind",
            "hind_madurai",
            "kumbh_sans",
            "alegreya",
            "assistant")

    @Test
    fun `every added font has matching APK copies and an OFL notice`() {
        val notices = resolve("mobile/src/main/res/raw/notices.xml").readText()
        assertTrue(
                "the license dialog must include every added font",
                addedFonts.all { key ->
                    notices.contains("<name>${displayName(key)}</name>") &&
                            notices.contains("<license>SIL Open Font License v1.1</license>")
                })

        addedFonts.forEach { key ->
            val mobile = resolve("mobile/src/main/res/font/${key}_regular.ttf")
            val wear = resolve("wear/src/main/res/font/${key}_regular.ttf")
            val license = resolve("licenses/$key/OFL.txt")
            val mobilePackagedLicense =
                    resolve("mobile/src/main/assets/licenses/fonts/$key/OFL.txt")
            val wearPackagedLicense = resolve("wear/src/main/assets/licenses/fonts/$key/OFL.txt")
            assertTrue("missing phone font: $mobile", mobile.isFile)
            assertTrue("missing Wear font: $wear", wear.isFile)
            assertTrue("missing license: $license", license.isFile)
            assertTrue("missing phone packaged license: $mobilePackagedLicense", mobilePackagedLicense.isFile)
            assertTrue("missing Wear packaged license: $wearPackagedLicense", wearPackagedLicense.isFile)
            assertArrayEquals(
                    "phone and Wear copies differ for $key",
                    mobile.readBytes(), wear.readBytes())
            assertArrayEquals(
                    "phone packaged license differs from the source license for $key",
                    license.readBytes(), mobilePackagedLicense.readBytes())
            assertArrayEquals(
                    "Wear packaged license differs from the source license for $key",
                    license.readBytes(), wearPackagedLicense.readBytes())
            assertTrue(
                    "$license is not the SIL Open Font License 1.1",
                    license.readText().contains("SIL Open Font License, Version 1.1"))
        }
    }

    private fun displayName(key: String): String = key.split('_').joinToString(" ") { word ->
        word.replaceFirstChar(Char::uppercaseChar)
    }.replace("Ibm", "IBM")
            .replace("Dm ", "DM ")

    private fun resolve(relative: String): File = listOf(
            File(relative),
            File("../$relative"))
            .firstOrNull(File::exists)
            ?: File(relative)
}
