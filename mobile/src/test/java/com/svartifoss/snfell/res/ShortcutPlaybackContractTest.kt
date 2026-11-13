package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pins three things about how a shortcut picked on the watch is carried out on the phone. Each was
 * found on an Android 14 emulator with a stand-in player (`docs/streaming-app-interop.md`, section
 * 5.7), each failed without an error anywhere, and none is visible in a review of the line that
 * would undo it.
 */
class ShortcutPlaybackContractTest {

    private fun repoFile(path: String): File {
        listOf(File(path), File("../$path")).forEach { if (it.exists()) return it }
        fail("Not found from either module dir or repo root: $path")
        error("unreachable")
    }

    private val service by lazy {
        repoFile("mobile/src/main/java/com/svartifoss/snfell/music/MusicService.kt").readText()
    }

    private fun functionBody(name: String): String {
        val start = service.indexOf("fun $name(")
        if (start < 0) fail("MusicService has no function $name")
        val next = Regex("\n    (?:private |internal |override |suspend |public )*fun ").find(service, start + 1)
        return service.substring(start, next?.range?.first ?: service.length)
    }

    /**
     * When the process comes up cold on a watch command, the notification listener's own start is
     * delivered right before the watch's. A bare `stopSelf()` in its branch tore the service down
     * just after the watch's start had run, cancelling the command it had taken.
     */
    @Test
    fun aStartThatIsNotFromTheWatchStopsTheServiceOnlyIfItIsTheLatestStart() {
        val onStart = functionBody("onStartCommand")
        val branch = onStart.substringAfter("} else if (!startedFromWatch) {", "")
                .substringBefore("} else if (action == ACTION_NOTIFICATION_SERVICE_ACTIVATED)")
        assertTrue("the branch for a start that is not from the watch was not found", branch.isNotEmpty())
        val code = branch.lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
        assertTrue("it must stop with stopSelf(startId), which leaves a newer start alone",
                code.contains("stopSelf(startId)"))
        assertFalse("a bare stopSelf() discards the watch's start queued behind this one",
                code.contains("stopSelf()"))
    }

    /**
     * Both visible routes are followed by `VisibleOpenFollowUp`, which never presses play on the
     * track an app came up holding. The loop it replaced did, and started the wrong music.
     */
    @Test
    fun everyVisibleOpenIsFollowedByThePolicy() {
        assertTrue(functionBody("startStreamingLinkWithPlaybackNudge").contains("followVisibleOpen("))
        assertTrue(functionBody("playThroughHandoff").contains("followVisibleOpen("))
        val follow = functionBody("followVisibleOpen")
        assertTrue(follow.contains("VisibleOpenFollowUp("))
        // The one play press of the visible routes, and only where the policy asks for it.
        val press = follow.indexOf("transportControls?.play()")
        assertTrue("followVisibleOpen must press play for Step.PRESS_PLAY", press > 0)
        assertTrue(follow.lastIndexOf("Step.PRESS_PLAY", press) > follow.lastIndexOf("Step.REQUEST", press))
        assertFalse(functionBody("startStreamingLinkWithPlaybackNudge").contains("transportControls"))
        assertFalse(functionBody("playThroughHandoff").contains("transportControls"))
        assertFalse("the blind press must not come back", service.contains("pressPlayAfterNavigating("))
    }

    /**
     * The app that is asked to play is not always the app the link opens in: a YouTube link to a
     * video or playlist is played by YouTube Music and opened by the YouTube app. Asking only the
     * app that declares the saved link (as 4.2 to 4.3.1 did) skipped every silent route for those
     * links; opening the link in the app that was asked to play would change what reaches the
     * screen. So the silent steps take `playbackPackage` and the visible open keeps `targetPackage`.
     */
    @Test
    fun theSilentStepsAskThePlaybackAppAndTheVisibleOpenKeepsItsOwn() {
        val ladder = functionBody("playDeepLinkNow")
        assertTrue(ladder.contains("StreamingShortcutRoutes.playbackPackage("))
        assertTrue("the playback app defaults to the app the link opens in",
                ladder.contains("val playbackPackage = targetPackage ?:"))
        assertTrue("step 1 must be asked of the playback app", ladder.contains(
                "requestStreamingPlayback(link, service, playbackPackage, query, preferSearch)"))
        assertTrue("step 2 must be asked of the playback app, with the visible app passed on",
                ladder.contains("startBrowserThenVisibleOpen(link, service, playbackPackage, targetPackage,"))

        val browserStep = functionBody("startBrowserThenVisibleOpen")
        assertTrue(browserStep.contains("MediaBrowserPlayback.play("))
        assertTrue("when the browser did not play, the link is opened where it always was",
                browserStep.contains(
                        "startStreamingLinkWithPlaybackNudge(link, service, visiblePackage,"))
    }

    /**
     * The receiver that lets `adb` stand in for a watch exists in debug builds only, and only the
     * shell may send to it.
     */
    @Test
    fun theSimulatedWatchCommandReceiverIsDebugOnlyAndShellOnly() {
        val name = "SimulatedWatchCommandReceiver"
        val debugManifest = repoFile("mobile/src/debug/AndroidManifest.xml").readText()
        val declaration = Regex("<receiver(.*?)/>", RegexOption.DOT_MATCHES_ALL).findAll(debugManifest)
                .map { it.groupValues[1] }.firstOrNull { it.contains(name) }
        if (declaration == null) fail("$name is not declared in the debug manifest")
        assertTrue(declaration!!.contains("android:permission=\"android.permission.DUMP\""))

        listOf("main", "play", "github", "playRelease").forEach { sourceSet ->
            val manifest = File(repoFile("mobile/src/main/AndroidManifest.xml").parentFile.parentFile,
                    "$sourceSet/AndroidManifest.xml")
            if (manifest.exists()) {
                assertFalse("$name must not be declared in src/$sourceSet", manifest.readText().contains(name))
            }
            val sources = File(manifest.parentFile, "java")
            if (sources.isDirectory) {
                val found = sources.walkTopDown().filter { it.isFile && it.extension == "kt" }
                        .firstOrNull { it.readText().contains("class $name") }
                assertTrue("$name must live in src/debug only, found in ${found?.path}", found == null)
            }
        }
    }
}
