package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pins the `github` / `play` flavor split for the in-app self-updater (see
 * docs/play-store-migration-plan.md).
 *
 * Google Play's Device and Network Abuse policy forbids an app updating itself outside Play, so the
 * `play` build must carry **none** of the updater: not the `REQUEST_INSTALL_PACKAGES` permission,
 * not the components, not the code. That is enforced only by where the files sit -
 * `src/github/` vs `src/main/` - which nothing else checks, and which a well-meaning "move this
 * back where the rest of the code is" refactor would quietly undo. Each failure here would ship a
 * Play artifact that fails review or gets pulled.
 *
 * Reads the tree off disk like the sibling `res/` invariant tests; no Android context, no flavor
 * dependency - it checks the layout is correct for both.
 */
class FlavorSelfUpdateIsolationTest {

    private fun repoFile(path: String): File {
        listOf(File(path), File("../$path")).forEach { if (it.exists()) return it }
        fail("Not found from either module dir or repo root: $path")
        error("unreachable")
    }

    private fun text(path: String): String = repoFile(path).readText()

    private fun dirEntries(path: String): Set<String> =
            (repoFile(path).listFiles()?.map { it.name } ?: emptyList()).toSet()

    // ---- manifests ----

    @Test
    fun mainManifestsCarryNoSelfUpdater() {
        listOf(
                "mobile/src/main/AndroidManifest.xml" to listOf(".update.UpdateActivity", ".update.PhoneInstallResultReceiver"),
                "wear/src/main/AndroidManifest.xml" to listOf(".watch.update.ApkReceiverService", ".watch.update.InstallResultReceiver")
        ).forEach { (path, components) ->
            val manifest = text(path)
            assertFalse(
                    "$path must not declare REQUEST_INSTALL_PACKAGES - it belongs to the github flavor only",
                    manifest.contains("<uses-permission android:name=\"android.permission.REQUEST_INSTALL_PACKAGES\""))
            components.forEach { component ->
                assertFalse(
                        "$path must not register $component - it belongs to the github flavor only",
                        manifest.contains("android:name=\"$component\""))
            }
        }
    }

    @Test
    fun githubManifestsCarryTheSelfUpdater() {
        listOf(
                "mobile/src/github/AndroidManifest.xml" to listOf(".update.UpdateActivity", ".update.PhoneInstallResultReceiver"),
                "wear/src/github/AndroidManifest.xml" to listOf(".watch.update.ApkReceiverService", ".watch.update.InstallResultReceiver")
        ).forEach { (path, components) ->
            val manifest = text(path)
            assertTrue(
                    "$path must declare REQUEST_INSTALL_PACKAGES for the self-updater",
                    manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
            components.forEach { component ->
                assertTrue("$path must register $component", manifest.contains("android:name=\"$component\""))
            }
        }
    }

    @Test
    fun playManifestsStripTheInstallPermission() {
        listOf("mobile/src/play/AndroidManifest.xml", "wear/src/play/AndroidManifest.xml").forEach { path ->
            val manifest = text(path)
            assertTrue(
                    "$path must remove REQUEST_INSTALL_PACKAGES with tools:node=\"remove\" as a belt-and-suspenders guard",
                    Regex("REQUEST_INSTALL_PACKAGES\"[\\s\\S]{0,120}tools:node=\"remove\"")
                            .containsMatchIn(manifest) ||
                    Regex("tools:node=\"remove\"[\\s\\S]{0,120}REQUEST_INSTALL_PACKAGES")
                            .containsMatchIn(manifest))
        }
    }

    // ---- source-set placement ----

    @Test
    fun theUpdaterImplementationLivesOnlyInTheGithubSourceSet() {
        val mainUpdate = dirEntries("mobile/src/main/java/com/svartifoss/snfell/update")
        assertTrue(
                "Only the flavor-agnostic version comparator may stay in src/main; found $mainUpdate",
                mainUpdate == setOf("AppVersionComparison.kt"))

        val githubUpdate = dirEntries("mobile/src/github/java/com/svartifoss/snfell/update")
        listOf("UpdateChecker.kt", "UpdateActivity.kt", "UpdateNotifier.kt",
                "ApkDownloader.kt", "PhoneApkInstaller.kt", "PhoneInstallResultReceiver.kt",
                "WatchApkPusher.kt", "UpdateGateway.kt").forEach {
            assertTrue("mobile/src/github/.../update/ must contain $it (got $githubUpdate)", it in githubUpdate)
        }

        val playUpdate = dirEntries("mobile/src/play/java/com/svartifoss/snfell/update")
        assertTrue("mobile/src/play/.../update/ must contain the no-op UpdateGateway.kt (got $playUpdate)",
                playUpdate == setOf("UpdateGateway.kt"))

        val wearGithubUpdate = dirEntries("wear/src/github/java/com/svartifoss/snfell/watch/update")
        assertTrue("wear/src/github/.../watch/update/ must contain the receiver + result receiver (got $wearGithubUpdate)",
                wearGithubUpdate == setOf("ApkReceiverService.kt", "InstallResultReceiver.kt"))

        assertFalse("wear/src/main must not contain a watch/update package",
                repoFile("wear/src/main/java/com/svartifoss/snfell/watch").resolve("update").exists())
    }

    @Test
    fun bothFlavorGatewaysDeclareTheSelfUpdateFlag() {
        assertTrue("github UpdateGateway must report SUPPORTS_SELF_UPDATE = true",
                Regex("SUPPORTS_SELF_UPDATE\\s*=\\s*true")
                        .containsMatchIn(text("mobile/src/github/java/com/svartifoss/snfell/update/UpdateGateway.kt")))
        assertTrue("play UpdateGateway must report SUPPORTS_SELF_UPDATE = false",
                Regex("SUPPORTS_SELF_UPDATE\\s*=\\s*false")
                        .containsMatchIn(text("mobile/src/play/java/com/svartifoss/snfell/update/UpdateGateway.kt")))
    }
}
