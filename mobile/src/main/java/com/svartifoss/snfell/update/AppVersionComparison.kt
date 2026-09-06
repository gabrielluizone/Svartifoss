package com.svartifoss.snfell.update

import com.svartifoss.snfell.BuildConfig

/**
 * Pure segment-wise version comparison with semver-style pre-release precedence
 * ("2.1.1" < "2.2", "3.1-beta1" < "3.1-beta2" < "3.1"). Tags may carry a leading "v".
 *
 * This lives in `src/main` (not the `github`-only [UpdateChecker]) because it is needed by both
 * distribution flavors: the community-theme gallery uses it to decide whether a published theme
 * requires a newer app version than the one installed. The GitHub self-updater is the only other
 * caller and it is absent from the Play build.
 *
 * The pre-release half is not cosmetic and must not be discarded: this project ships betas as
 * `3.1-beta1` / `3.1-beta2`, so a comparison that reads only the numeric part sees both as "3.1"
 * and reports every beta as up to date - which stranded pre-release users on the build they
 * happened to install, both for the next beta *and* for the eventual final release.
 *
 * Ordering follows semver's rule, since that is what the tags are shaped like:
 *  - the numeric release part decides first;
 *  - on a tie, *having* a pre-release suffix ranks BELOW not having one, so 3.1 supersedes
 *    3.1-beta2 (this is what lets a beta tester be offered the stable release);
 *  - two pre-releases compare identifier by identifier, and each identifier compares its digit
 *    runs numerically - "beta10" is newer than "beta2", which a plain string compare gets
 *    backwards. Letter runs compare lexically, which happens to order alpha < beta < rc.
 */
object AppVersionComparison {

    fun isNewerThanInstalled(tag: String): Boolean = isNewer(tag, BuildConfig.VERSION_NAME)

    fun isNewer(tag: String, installed: String): Boolean {
        val candidate = parseVersion(tag)
        // A tag with no numeric part at all ("latest", "") is never treated as an update.
        if (candidate.numbers.isEmpty()) {
            return false
        }
        return compareVersions(candidate, parseVersion(installed)) > 0
    }

    private class Version(val numbers: List<Int>, val preRelease: List<String>)

    private fun parseVersion(version: String): Version {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        val separator = cleaned.indexOf('-')
        val releasePart = if (separator >= 0) cleaned.substring(0, separator) else cleaned
        val preReleasePart = if (separator >= 0) cleaned.substring(separator + 1) else ""

        val numbers = releasePart.split('.', ' ')
                .mapNotNull { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() }
        val preRelease = preReleasePart.split('.', '-', ' ').filter { it.isNotBlank() }
        return Version(numbers, preRelease)
    }

    private fun compareVersions(a: Version, b: Version): Int {
        for (i in 0 until maxOf(a.numbers.size, b.numbers.size)) {
            // A missing segment reads as 0, so "2.2" and "2.2.0" are the same version.
            val result = a.numbers.getOrElse(i) { 0 }.compareTo(b.numbers.getOrElse(i) { 0 })
            if (result != 0) {
                return result
            }
        }

        // The one without a suffix is the finished release, and outranks any of its pre-releases.
        if (a.preRelease.isEmpty() && b.preRelease.isEmpty()) {
            return 0
        }
        if (a.preRelease.isEmpty()) {
            return 1
        }
        if (b.preRelease.isEmpty()) {
            return -1
        }
        for (i in 0 until maxOf(a.preRelease.size, b.preRelease.size)) {
            // Fewer identifiers ranks lower ("beta" < "beta.1").
            val x = a.preRelease.getOrNull(i) ?: return -1
            val y = b.preRelease.getOrNull(i) ?: return 1
            val result = compareIdentifiers(x, y)
            if (result != 0) {
                return result
            }
        }
        return 0
    }

    /** Compares one pre-release identifier, reading its digit runs as numbers ("beta2" < "beta10"). */
    private fun compareIdentifiers(a: String, b: String): Int {
        val runsA = digitAndLetterRuns(a)
        val runsB = digitAndLetterRuns(b)
        for (i in 0 until maxOf(runsA.size, runsB.size)) {
            val x = runsA.getOrNull(i) ?: return -1
            val y = runsB.getOrNull(i) ?: return 1
            val numberX = x.toIntOrNull()
            val numberY = y.toIntOrNull()
            val result = when {
                numberX != null && numberY != null -> numberX.compareTo(numberY)
                // A numeric run ranks below an alphabetic one, as in semver.
                numberX != null -> -1
                numberY != null -> 1
                else -> x.compareTo(y, ignoreCase = true)
            }
            if (result != 0) {
                return result
            }
        }
        return 0
    }

    /** "beta10" -> ["beta", "10"], so the digits can be compared as a number rather than as text. */
    private fun digitAndLetterRuns(identifier: String): List<String> {
        val runs = mutableListOf<String>()
        var start = 0
        while (start < identifier.length) {
            val digits = identifier[start].isDigit()
            var end = start
            while (end < identifier.length && identifier[end].isDigit() == digits) {
                end++
            }
            runs += identifier.substring(start, end)
            start = end
        }
        return runs
    }
}
