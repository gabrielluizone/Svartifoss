package com.svartifoss.snfell.watch.res

import com.svartifoss.snfell.common.ArchivedFaces
import com.svartifoss.snfell.common.PlayerChromeLayout
import com.svartifoss.snfell.common.ThemeAppearance
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One question, one answer: how much of the screen the shared chrome has already spoken for.
 *
 * Six faces each carried their own expression over two fields that described the same band, plus a
 * third the host wrote from three places. No two agreed, some double-counted the bottom hint and
 * others missed it, and none of it threw - the screen simply looked improvised. What stops that
 * coming back is not a comment but this: a face reads `state.safeArea` or it is named as declining,
 * and nothing in between compiles past here.
 */
class FaceSafeAreaContractTest {

    private val faceDirectory = repoFile("wear/src/main/java/com/svartifoss/snfell/watch/view/face")

    private fun repoFile(relative: String): File =
            listOf(File("../$relative"), File(relative))
                    .firstOrNull { it.exists() }
                    ?: throw AssertionError("Could not locate $relative")

    private fun faceSources(): List<File> =
            faceDirectory.listFiles { file -> file.extension == "kt" }.orEmpty().sortedBy { it.name }

    /**
     * The three channels this replaced. Any of them reappearing means the band is being described
     * twice again, which is the state every one of the reported overlaps came from.
     */
    @Test
    fun `no face describes the bottom band in its own terms`() {
        val retired = listOf("miniButtonsTopFraction", "bottomHintInsetDp", "topHintVisible")
        val offenders = mutableListOf<String>()
        for (source in faceSources() + listOf(
                repoFile("wear/src/main/java/com/svartifoss/snfell/watch/view/MainActivity.kt"))) {
            val text = source.readText()
            for (field in retired) {
                if (text.contains(field)) offenders += "${source.name} still uses $field"
            }
        }
        assertTrue(
                "The bottom band has one description, NowPlayingFaceState.safeArea:\n" +
                        offenders.joinToString("\n"),
                offenders.isEmpty())
    }

    /**
     * Faces whose *file* need not read the safe area, and why.
     *
     * Two different reasons, deliberately kept apart. `declinesLowerContentBand` is a design
     * decision about a face that *has* something down there and accepts the overlay; this is the
     * weaker statement that there is nothing text-bearing in the band to begin with. A face that
     * simply forgot to read it looks exactly like both, which is the whole reason for the list.
     */
    /** The files that render a now-playing face, as opposed to the contract or the shared parts. */
    private val FACE_FILES: Set<String> by lazy {
        faceSources().map { it.name }
                .filter { it.endsWith("Face.kt") || it == "CuratedPlayerFaces.kt" }
                .filterNot { it == "NowPlayingFace.kt" }
                .toSet()
    }

    private val EXEMPT_FACE_FILES = mapOf(
            "FrameFace.kt" to
                    "its card ends at .795 and only artwork lies beneath; the title and artist sit " +
                            "in the card's upper half",
            "SplitFace.kt" to
                    "split declines the band - see PlayerChromeLayout.declinesLowerContentBand")

    /**
     * Every face reads the one description of the bottom band, or is named above with its reason.
     *
     * This is the half that the retired-field sweep cannot catch: a face that never described the
     * band in its own terms and never read the shared one is silent in exactly the same way, and
     * drew its lower content under the mini buttons with nothing anywhere to say why. Metadata was
     * that face - it budgeted its table against the whole screen height on the one composition
     * whose premise is fitting as many rows as the screen holds.
     */
    @Test
    fun `every face either reads the safe area or says why it does not need to`() {
        val offenders = mutableListOf<String>()
        for (source in faceSources()) {
            // Only the files that render faces. The shared helpers take the same state and would
            // otherwise match any signature-based rule, and NowPlayingFace.kt is the contract.
            if (source.name !in FACE_FILES) continue
            val text = source.readText()
            if (text.contains("state.safeArea")) continue
            if (source.name in EXEMPT_FACE_FILES) continue
            offenders += source.name
        }
        assertTrue(
                "These faces neither read NowPlayingFaceState.safeArea nor are listed as not " +
                        "needing to, so their lower content draws under the mini buttons with " +
                        "nothing to say why:\n" + offenders.joinToString("\n"),
                offenders.isEmpty())
    }

    /** An exemption that names the registry must actually be in it. */
    @Test
    fun `a face exempted for declining the band really declines it`() {
        for ((file, reason) in EXEMPT_FACE_FILES) {
            if (!reason.contains("declines")) continue
            val face = file.removeSuffix("Face.kt").lowercase()
            assertTrue("$file claims to decline the band but is not in the registry",
                    PlayerChromeLayout.declinesLowerContentBand(face))
        }
    }

    /**
     * Immersive is the one face that lets the row draw over it, and its own source says why. The
     * registry exists so that decision is visible; a face that simply forgot to read the safe area
     * looks identical in the source and is not the same thing.
     */
    @Test
    fun `declining the lower band is a listed decision, not a missing call`() {
        val living = ThemeAppearance.ALLOWED_BASE_FACES - ArchivedFaces.KEYS
        val declining = living.filter { PlayerChromeLayout.declinesLowerContentBand(it) }
        assertTrue("Immersive and Split decline today; adding to that list is a design decision " +
                "and belongs with a reason at the registry, not at a draw site. Found: $declining",
                declining.toSet() == setOf("immersive", "split"))
        assertTrue("A declining face must still clear the bottom hint",
                PlayerChromeLayout.resolve(PlayerChromeLayout.Inputs(
                        screenDp = 192f, round = true, face = "immersive",
                        configuredQuadrants = setOf(3),
                        lowerContent = PlayerChromeLayout.LowerContent(150f, 42f)))
                        .safeArea.bottomDp > 0f)
    }
}
