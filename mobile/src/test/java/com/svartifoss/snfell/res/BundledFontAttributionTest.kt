package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every bundled font family must be credited in the phone's open-source license dialog - not only
 * the fifty [BundledFontLicenseTest] pins by name.
 *
 * That test keeps the legal and packaging halves of one specific catalog-expansion batch together,
 * but it was never meant to be (and does not claim to be) the full sweep: for a long stretch the
 * catalog held ~70 older bundled families - Poppins, Montserrat, Inter, Roboto Mono, Orbitron,
 * Marcellus and the rest - whose `licenses/<name>/OFL.txt` existed on disk but was never quoted in
 * `notices.xml`, so the app's own "Licenses" screen never credited most of what it actually ships.
 * The gap was invisible precisely because it looked identical to a font that had never been
 * bundled at all: nothing failed, nothing warned, the font rendered fine, and only reading the
 * license screen against the `res/font/` directory by hand would have shown the mismatch.
 *
 * This does not re-typed the fifty-plus display names [BundledFontLicenseTest] already owns (that
 * would drift the moment a font's name has an exception, the way "ABeeZee" does under the plain
 * capitalize-each-word rule). Instead it reads the one string every bundled font's own
 * `licenses/<name>/` folder already carries - its license file's opening line - and asserts that
 * exact text is quoted somewhere in `notices.xml`. That is a weaker check than matching by name,
 * but it is the one invariant that cannot silently drift: `notices.xml`'s `<copyright>` values were
 * built directly from these opening lines, so a font credited by hand still passes, and a font
 * whose entry was simply forgotten is the only way to fail it.
 *
 * A `licenses/<name>/` folder is looked up both by the font's key (`be_vietnam_pro`) and by the
 * same key with underscores turned to hyphens (`abril-fatface`) - the two spellings the catalog has
 * actually used across its history - rather than requiring the two to match, which the plain
 * `BundledFontLicenseTest.addedFonts` batch can assume but the full catalog cannot.
 */
class BundledFontAttributionTest {

    @Test
    fun `every bundled font family is quoted in the license dialog`() {
        val fontDir = resolve("mobile/src/main/res/font")
        val keys = fontDir.listFiles { file -> file.extension == "ttf" }
                .orEmpty()
                .map(::keyFor)
                .toSortedSet()
        assertTrue("expected to find bundled .ttf files under ${fontDir.path}", keys.isNotEmpty())

        val notices = resolve("mobile/src/main/res/raw/notices.xml").readText()

        val problems = keys.mapNotNull { key ->
            val licenseFile = licenseFileFor(key)
                    ?: return@mapNotNull "$key: no licenses/<name>/OFL.txt or LICENSE.txt for this font"
            val openingLine = licenseFile.readLines().firstOrNull { it.isNotBlank() }?.trim()
                    ?: return@mapNotNull "$key: ${licenseFile.path} is empty"
            if (openingLine !in notices) {
                "$key: the opening line of ${licenseFile.path} is not quoted anywhere in " +
                        "notices.xml - add a <notice> entry for it"
            } else {
                null
            }
        }

        assertTrue(
                "Bundled fonts missing from the phone's open-source license dialog (Settings -> " +
                        "About/Data & support -> Licenses reads mobile/src/main/res/raw/notices.xml):\n  " +
                        problems.joinToString("\n  "),
                problems.isEmpty())
    }

    private fun keyFor(file: File): String {
        var name = file.name.removeSuffix(".ttf")
        for (suffix in listOf("_regular", "_bold", "_flex")) {
            if (name.endsWith(suffix)) return name.removeSuffix(suffix)
        }
        return name
    }

    private fun licenseFileFor(key: String): File? {
        val licenses = resolve("licenses")
        return listOf(key, key.replace("_", "-"))
                .asSequence()
                .map { File(licenses, it) }
                .filter(File::isDirectory)
                .flatMap { dir -> sequenceOf(File(dir, "OFL.txt"), File(dir, "LICENSE.txt")) }
                .firstOrNull(File::isFile)
    }

    private fun resolve(relative: String): File = listOf(
            File(relative),
            File("../$relative"))
            .firstOrNull(File::exists)
            ?: File(relative)
}
