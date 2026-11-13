package com.svartifoss.snfell.watch.view.face

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A face's authored line height must follow the size the text finally renders at.
 *
 * Faces pass an absolute leading beside their designed size (Artist 15sp for a 13sp title), and
 * the size then moves - the user's size control, a text fit, a shrink step - while an absolute
 * leading would not. At 220% the Artist face drew a 28.6sp title on 15sp lines, so a wrapped
 * title laid its second line over the first. [proportionalTo] restates it in `em`.
 */
class ProportionalLeadingTest {

    @Test
    fun anAbsoluteLeadingBecomesRelativeToItsDesignedSize() {
        val leading = 15.sp.proportionalTo(13.sp)
        assertTrue(leading.isEm)
        assertEquals(15f / 13f, leading.value, 1e-6f)
        // The 100% design is unchanged: 15/13 em of 13sp is 15sp.
        assertEquals(15f, leading.value * 13f, 1e-4f)
        // And at 220% the lines sit 33sp apart instead of 15sp.
        assertEquals(33f, leading.value * 13f * 2.2f, 1e-3f)
    }

    @Test
    fun aLeadingThatAlreadyFollowsTheSizeOrNamesNoneIsLeftAlone() {
        assertEquals(TextUnit.Unspecified, TextUnit.Unspecified.proportionalTo(13.sp))
        assertEquals(1.2f.em, 1.2f.em.proportionalTo(13.sp))
        assertEquals(15.sp, 15.sp.proportionalTo(TextUnit.Unspecified))
        assertEquals(15.sp, 15.sp.proportionalTo(0.sp))
    }

    /** All three text helpers hand the leading on through it, or a face that passes one through
     *  that helper is back to lines laid over each other at any size above 100%. */
    @Test
    fun everyTextHelperRestatesTheLeading() {
        val chrome = listOf(
                File("src/main/java/com/svartifoss/snfell/watch/view/face/FaceChrome.kt"),
                File("wear/src/main/java/com/svartifoss/snfell/watch/view/face/FaceChrome.kt"))
                .first { it.isFile }
                .readText()
        // AdaptiveTitleText's final branch, ArtistLineTextPass and TitleLineTextPass.
        assertEquals(
                "Each of the three helpers' drawing passes must restate the leading",
                3,
                Regex("""lineHeight\.proportionalTo\(fontSize\)""").findAll(chrome).count())
    }
}
