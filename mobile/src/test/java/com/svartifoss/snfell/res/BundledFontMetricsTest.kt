package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fifty-seven bundled fonts carry corrected vertical metrics, and must keep carrying them.
 *
 * Every renderer centres a line of text by its line box - Compose measures ascent..descent, a
 * `TextView` sized `wrap_content` is that box, and the phone preview's Canvas sites all place a
 * baseline from `Paint.fontMetrics`. So where the glyphs land inside that box is the font's
 * decision, not the app's, and upstream several of these faces put that box well off-centre from
 * their own ink - Special Elite by 0.144 em, Josefin Sans by -0.101 em, and a dozen more by smaller
 * but still visible amounts. That reads as text sitting high or low inside every pill, chip and
 * centred container in the app.
 *
 * Each is corrected to centre the font's own *flat* capital letters (E, F, H, I, L - chosen for
 * having a genuinely flat top and bottom, unlike a round letter's baseline overshoot or a swash
 * tail), which is where the well-behaved fonts in this catalogue (Inter, Poppins, Montserrat)
 * already put their own box centre. See each font's `licenses/<name>/MODIFICATIONS.txt` for the
 * exact numbers and, where the font has old-style numerals or another design quirk that made
 * centring on raw digit ink the wrong target, why.
 *
 * Fixing it in the font rather than at the dozens of call sites that centre text is what makes it
 * one fix per font instead of many that can drift - the phone preview and the watch cannot
 * disagree about a number neither of them computes. The risk is the quiet one: a font file is a
 * binary, so re-downloading it from upstream would revert the correction with nothing to notice.
 * Hence this.
 *
 * **Three bundled fonts were measured and deliberately left untouched**, because the same
 * mechanical correction that helps the fifty-seven above would have made one of these worse or
 * introduced a new defect rather than fixing one:
 * - `lobster_regular.ttf` - its digits already centre within 0.003 em; the earlier alarm on this
 *   font came from averaging its *capital letters*, and Lobster's capital L carries a decorative
 *   descending flourish that pulled that average down by itself. The font was never actually
 *   miscentred where it's read (its digits); only the wrong yardstick said otherwise.
 * - `press_start_2p_regular.ttf` - centring its flat caps requires a small *positive* hhea.descent
 *   (the box's floor sitting above the baseline), which is outside the range every other font in
 *   this catalogue uses and untested against Android's renderer. The offset it would fix (0.063 em)
 *   is smaller than several fonts left alone elsewhere in the catalogue (Oswald, 0.048 em); the
 *   risk of an unfamiliar signed value was judged not worth that benefit.
 * - `indie_flower_regular.ttf` - a handwriting face whose "flat" capitals disagree with each other
 *   by 0.12 em (E, F, H, I and L are simply drawn at different heights, which is normal for
 *   handwriting), so there is no reliable target to centre on; and its 'g' descender is drawn far
 *   deeper than any other letter as a deliberate flourish, already exactly matching the old descent
 *   line. Centring the (unreliable) cap average would have clipped that flourish for no reliable
 *   gain - its digits, the content type actually reported as miscentred elsewhere, were already
 *   only 0.059 em off, in the same range as fonts left alone throughout this file.
 */
class BundledFontMetricsTest {

    private data class Metrics(val unitsPerEm: Int, val ascent: Int, val descent: Int)

    /**
     * name -> (ascent, descent), both in the font's own units.
     *
     * For every font except the two named in [GROWN_BOX_FONTS], ascent - descent is exactly the
     * font's own upstream total - line height, spacing, wrapping and every reserved height are
     * therefore unchanged, and only the baseline's position within the box moves. See
     * [upstreamTotals] for what "unchanged" means for each font, since several already carried
     * more than one em of headroom before this fix touched anything.
     */
    private val corrected = mapOf(
            "special_elite_regular.ttf" to (1734 to -314),
            "playfair_display_regular.ttf" to (1020 to -313),
            "crete_round_regular.ttf" to (946 to -330),
            "zilla_slab_regular.ttf" to (925 to -275),
            "silkscreen_regular.ttf" to (952 to -328),
            "josefin_sans_regular.ttf" to (851 to -149),
            "courier_prime_regular.ttf" to (1744 to -556),
            "teko_regular.ttf" to (1028 to -405),
            "bree_serif_regular.ttf" to (1012 to -346),
            "crimson_pro_regular.ttf" to (862 to -276),
            "exo_2_regular.ttf" to (945 to -255),
            "aleo_regular.ttf" to (958 to -242),
            "barlow_condensed_regular.ttf" to (950 to -250),
            "bebas_neue_regular.ttf" to (950 to -250),
            "oxanium_regular.ttf" to (917 to -227),
            "yanone_kaffeesatz_regular.ttf" to (918 to -218),
            "overpass_regular.ttf" to (1966 to -566),
            "hind_regular.ttf" to (1138 to -463),
            "vollkorn_regular.ttf" to (1034 to -359),
            "hind_madurai_regular.ttf" to (1027 to -353),
            "alata_regular.ttf" to (1058 to -322),
            "heebo_regular.ttf" to (2232 to -776),
            "alfa_slab_one_regular.ttf" to (1074 to -295),
            "assistant_regular.ttf" to (984 to -324),
            "shrikhand_regular.ttf" to (1062 to -396),
            "plus_jakarta_sans_regular.ttf" to (1002 to -258),
            "black_ops_one_regular.ttf" to (1944 to -616),
            "play_regular.ttf" to (903 to -254),
            "abril_fatface_regular.ttf" to (1024 to -325),
            "varela_round_regular.ttf" to (951 to -253),
            "roboto_mono_regular.ttf" to (2078 to -623),
            "outfit_regular.ttf" to (968 to -292),
            "spectral_regular.ttf" to (1091 to -431),
            "noto_sans_mono_regular.ttf" to (1038 to -324),
            "noto_sans_regular.ttf" to (1038 to -324),
            "noto_serif_regular.ttf" to (1038 to -324),
            "kanit_regular.ttf" to (1070 to -425),
            "dosis_regular.ttf" to (998 to -266),
            "rajdhani_regular.ttf" to (960 to -316),
            "space_mono_regular.ttf" to (1090 to -391),
            "lato_regular.ttf" to (1916 to -484),
            "sofia_sans_regular.ttf" to (928 to -272),
            "russo_one_regular.ttf" to (952 to -253),
            "titillium_web_regular.ttf" to (1106 to -415),
            "ibm_plex_mono_regular.ttf" to (999 to -301),
            "ibm_plex_sans_regular.ttf" to (999 to -301),
            "ibm_plex_serif_regular.ttf" to (999 to -301),
            "source_code_pro_regular.ttf" to (958 to -299),
            "lexend_regular.ttf" to (975 to -275),
            "mulish_regular.ttf" to (980 to -275),
            "quicksand_regular.ttf" to (975 to -275),
            "sora_regular.ttf" to (995 to -265),
            "league_spartan_regular.ttf" to (1800 to -480),
            "moms_typewriter.ttf" to (1053 to -254),
            "libre_baskerville_regular.ttf" to (1050 to -280),
            "questrial_regular.ttf" to (888 to -226),
            "domine_regular.ttf" to (950 to -230))

    /**
     * The corrections that could not preserve their font's original box height - see each one's
     * own `licenses/<name>/MODIFICATIONS.txt` (or, for the fonts this project has never had a
     * license file for - see [FONTS_MISSING_A_LICENSE_FILE] - the plain numbers below, since a
     * modification notice would have nowhere to attach). All of them had upstream boxes with
     * little or no room below their own ordinary lowercase descenders (g, j, p, q, y): sliding the
     * baseline up enough to fix the top would have clipped the bottom. Each box was grown by the
     * smallest amount that centres the flat-cap band *and* keeps clearing those descenders.
     */
    private val GROWN_BOX_FONTS = setOf(
            "oxanium_regular.ttf", "yanone_kaffeesatz_regular.ttf", "league_spartan_regular.ttf",
            "moms_typewriter.ttf", "libre_baskerville_regular.ttf", "questrial_regular.ttf",
            "domine_regular.ttf")

    /**
     * Bundled fonts this project has never had a `licenses/<name>/` entry for at all - a gap that
     * predates this fix and is unrelated to it. The metrics correction was still applied (a small
     * internal metrics tweak is standard practice under any font license, including the OFL every
     * other font in this catalogue ships under), but no `MODIFICATIONS.txt` was written for these
     * four, since it would cite a license file that was never added to this repository. Adding the
     * missing upstream license text is a separate task from font metrics.
     */
    private val FONTS_MISSING_A_LICENSE_FILE = setOf(
            "league_spartan_regular.ttf", "hind_madurai_regular.ttf", "moms_typewriter.ttf",
            "libre_baskerville_regular.ttf", "noto_sans_mono_regular.ttf", "noto_sans_regular.ttf",
            "noto_serif_regular.ttf", "sofia_sans_regular.ttf", "ibm_plex_mono_regular.ttf",
            "ibm_plex_sans_regular.ttf", "ibm_plex_serif_regular.ttf")

    @Test
    fun `every corrected font keeps its pinned vertical metrics`() {
        corrected.forEach { (name, expected) ->
            val (expectedAscent, expectedDescent) = expected
            fontCopies(name).forEach { file ->
                val metrics = verticalMetrics(file)
                assertEquals(
                        "${file.path}: got upstream's ascent again - text in every centred " +
                                "container using this font will sit off-centre. Re-derive the " +
                                "correction (see the class doc) rather than adjusting this " +
                                "expectation, and update licenses/<name>/MODIFICATIONS.txt.",
                        expectedAscent,
                        metrics.ascent)
                assertEquals(
                        "${file.path} has upstream's descent again - see the ascent message.",
                        expectedDescent,
                        metrics.descent)
            }
        }
    }

    /**
     * Every correction preserves its own font's original line-box height *except* the two in
     * [GROWN_BOX_FONTS], which grow by exactly the documented amount and no more. This does not
     * assert one shared total - several of these faces already carried more than one em of
     * headroom upstream, for their own ascenders/descenders, and that headroom is inherited rather
     * than invented.
     */
    @Test
    fun `every correction preserves or deliberately grows its font's line-box height`() {
        val upstreamTotals = mapOf(
                "special_elite_regular.ttf" to 2048,
                "playfair_display_regular.ttf" to 1333,
                "crete_round_regular.ttf" to 1276,
                "zilla_slab_regular.ttf" to 1200,
                "silkscreen_regular.ttf" to 1280,
                "josefin_sans_regular.ttf" to 1000,
                "courier_prime_regular.ttf" to 2300,
                "teko_regular.ttf" to 1433,
                "bree_serif_regular.ttf" to 1358,
                "crimson_pro_regular.ttf" to 1138,
                "exo_2_regular.ttf" to 1200,
                "aleo_regular.ttf" to 1200,
                "barlow_condensed_regular.ttf" to 1200,
                "bebas_neue_regular.ttf" to 1200,
                "oxanium_regular.ttf" to 1000,
                "yanone_kaffeesatz_regular.ttf" to 935,
                "overpass_regular.ttf" to 2532,
                "hind_regular.ttf" to 1601,
                "vollkorn_regular.ttf" to 1393,
                "hind_madurai_regular.ttf" to 1380,
                "alata_regular.ttf" to 1380,
                "heebo_regular.ttf" to 3008,
                "alfa_slab_one_regular.ttf" to 1369,
                "assistant_regular.ttf" to 1308,
                "shrikhand_regular.ttf" to 1458,
                "plus_jakarta_sans_regular.ttf" to 1260,
                "black_ops_one_regular.ttf" to 2560,
                "play_regular.ttf" to 1157,
                "abril_fatface_regular.ttf" to 1349,
                "varela_round_regular.ttf" to 1204,
                "roboto_mono_regular.ttf" to 2701,
                "outfit_regular.ttf" to 1260,
                "spectral_regular.ttf" to 1522,
                "noto_sans_mono_regular.ttf" to 1362,
                "noto_sans_regular.ttf" to 1362,
                "noto_serif_regular.ttf" to 1362,
                "kanit_regular.ttf" to 1495,
                "dosis_regular.ttf" to 1264,
                "rajdhani_regular.ttf" to 1276,
                "space_mono_regular.ttf" to 1481,
                "lato_regular.ttf" to 2400,
                "sofia_sans_regular.ttf" to 1200,
                "russo_one_regular.ttf" to 1205,
                "titillium_web_regular.ttf" to 1521,
                "ibm_plex_mono_regular.ttf" to 1300,
                "ibm_plex_sans_regular.ttf" to 1300,
                "ibm_plex_serif_regular.ttf" to 1300,
                "source_code_pro_regular.ttf" to 1257,
                "lexend_regular.ttf" to 1250,
                "mulish_regular.ttf" to 1255,
                "quicksand_regular.ttf" to 1250,
                "sora_regular.ttf" to 1260,
                "league_spartan_regular.ttf" to 1840,
                "moms_typewriter.ttf" to 1193,
                "libre_baskerville_regular.ttf" to 1240,
                "questrial_regular.ttf" to 1030,
                "domine_regular.ttf" to 1140)
        // The exact new total for each grown-box font, so a future edit cannot creep the growth
        // past what its MODIFICATIONS.txt actually documents.
        val grownTotals = mapOf(
                "oxanium_regular.ttf" to 1144,
                "yanone_kaffeesatz_regular.ttf" to 1136,
                "league_spartan_regular.ttf" to 2280,
                "moms_typewriter.ttf" to 1307,
                "libre_baskerville_regular.ttf" to 1330,
                "questrial_regular.ttf" to 1114,
                "domine_regular.ttf" to 1180)
        corrected.forEach { (name, expected) ->
            val (ascent, descent) = expected
            val expectedTotal = if (name in GROWN_BOX_FONTS) {
                grownTotals.getValue(name)
            } else {
                upstreamTotals.getValue(name)
            }
            assertEquals(
                    "$name: ascent - descent should equal ${
                        if (name in GROWN_BOX_FONTS) "its documented grown total" else "its upstream total"
                    }, or the correction changed how tall a line of this font is by an amount " +
                            "nobody decided",
                    expectedTotal,
                    ascent - descent)
        }
    }

    /**
     * The preview and the watch each load their own copy of every bundled font, so a correction
     * applied to one of them would show the phone and the wrist centring text differently - the
     * exact class of drift `WatchPreviewParityTest` exists for, one level further down.
     */
    @Test
    fun `both modules ship the same file for every corrected font`() {
        corrected.keys.forEach { name ->
            val (mobile, wear) = fontCopies(name)
            assertArrayEquals(
                    "${mobile.path} and ${wear.path} differ. Both must be the same file: the " +
                            "phone preview loads one and the watch loads the other.",
                    mobile.readBytes(),
                    wear.readBytes())
        }
    }

    /**
     * The three fonts documented in the class doc as deliberately left untouched must keep their
     * exact upstream metrics - if a future pass "corrects" one, it should be a considered decision
     * that also rewrites the class doc's explanation, not an automated sweep re-applying the same
     * formula that was rejected here for a specific, documented reason.
     */
    @Test
    fun `the fonts left deliberately unmodified have not been touched`() {
        val untouched = mapOf(
                "lobster_regular.ttf" to (1000 to -250),
                "press_start_2p_regular.ttf" to (1000 to 0),
                "indie_flower_regular.ttf" to (971 to -488))
        untouched.forEach { (name, expected) ->
            val (expectedAscent, expectedDescent) = expected
            fontCopies(name).forEach { file ->
                val metrics = verticalMetrics(file)
                assertEquals(
                        "${file.path}: ascent changed. If this was a deliberate fix, update the " +
                                "class doc's explanation of why it was previously left alone " +
                                "instead of just this expectation.",
                        expectedAscent,
                        metrics.ascent)
                assertEquals(
                        "${file.path}: descent changed - see the ascent message.",
                        expectedDescent,
                        metrics.descent)
            }
        }
    }

    /** Reads `head.unitsPerEm` and `hhea.ascent/descent` straight out of the TrueType tables. */
    private fun verticalMetrics(file: File): Metrics {
        val bytes = file.readBytes()
        val head = tableOffset(bytes, "head")
        val hhea = tableOffset(bytes, "hhea")
        return Metrics(
                unitsPerEm = bytes.uint16(head + 18),
                ascent = bytes.int16(hhea + 4),
                descent = bytes.int16(hhea + 6))
    }

    private fun tableOffset(bytes: ByteArray, tag: String): Int {
        val tables = bytes.uint16(4)
        for (index in 0 until tables) {
            val record = 12 + index * 16
            val name = String(bytes, record, 4, Charsets.US_ASCII)
            if (name == tag) return bytes.int32(record + 8)
        }
        throw AssertionError("the font has no $tag table")
    }

    private fun ByteArray.uint16(at: Int): Int =
            ((this[at].toInt() and 0xFF) shl 8) or (this[at + 1].toInt() and 0xFF)

    private fun ByteArray.int16(at: Int): Int = uint16(at).let {
        if (it >= 0x8000) it - 0x10000 else it
    }

    private fun ByteArray.int32(at: Int): Int =
            (uint16(at) shl 16) or uint16(at + 2)

    private fun fontCopies(name: String): List<File> = listOf(
            resolve("mobile", name),
            resolve("wear", name))

    private fun resolve(module: String, name: String): File = listOf(
            File("../$module/src/main/res/font/$name"),
            File("$module/src/main/res/font/$name"))
            .first(File::isFile)
}
