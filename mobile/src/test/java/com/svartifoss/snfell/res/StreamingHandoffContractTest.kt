package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pins the shape that keeps `StreamingPlaybackActivity` safe to export.
 *
 * The activity is browsable - it has to be, it is the only kind of link the watch can open on the
 * phone - so any web page can open it too. It is harmless only because the link carries nothing but
 * a one-time token and every launch it makes was planned inside this app (`VisiblePlaybackRegistry`).
 * The day it reads an action, a package or an extra from the link it becomes an "intent
 * redirection" hole: a page could have it start another app's non-browsable components under this
 * app's identity. Google Play flags exactly that, and nothing at compile time would.
 */
class StreamingHandoffContractTest {

    private fun repoFile(path: String): File {
        listOf(File(path), File("../$path")).forEach { if (it.exists()) return it }
        fail("Not found from either module dir or repo root: $path")
        error("unreachable")
    }

    private val activitySource by lazy {
        repoFile("mobile/src/main/java/com/svartifoss/snfell/view/handoff/StreamingPlaybackActivity.kt").readText()
    }

    private val manifest by lazy { repoFile("mobile/src/main/AndroidManifest.xml").readText() }

    @Test
    fun theActivityIsDeclaredAsABrowsableLinkForItsOwnHost() {
        val match = Regex(
                "<activity\\s+android:name=\"\\.view\\.handoff\\.StreamingPlaybackActivity\"(.*?)</activity>",
                RegexOption.DOT_MATCHES_ALL).find(manifest)
        if (match == null) fail("StreamingPlaybackActivity is not declared in the phone manifest")
        val declaration = match!!.groupValues[1]
        assertTrue(declaration.contains("android:exported=\"true\""))
        assertTrue(declaration.contains("android.intent.action.VIEW"))
        assertTrue(declaration.contains("android.intent.category.BROWSABLE"))
        assertTrue(declaration.contains("android:scheme=\"svartifoss\""))
        assertTrue(declaration.contains("android:host=\"play-shortcut\""))
        // Invisible and gone in one frame, like the audio-output trampoline.
        assertTrue(declaration.contains("android:noHistory=\"true\""))
        assertTrue(declaration.contains("android:excludeFromRecents=\"true\""))
    }

    @Test
    fun theActivityReadsNothingFromTheLinkButTheToken() {
        val forbidden = listOf("getStringExtra", "getParcelableExtra", "getBundleExtra", ".extras",
                "getExtras", "getSerializableExtra", "Intent(", "setComponent", "setClassName",
                "parseUri")
        forbidden.forEach { call ->
            assertFalse("StreamingPlaybackActivity must not use $call: every launch comes from the registry",
                    activitySource.contains(call))
        }
        val parameters = Regex("getQueryParameter\\(([^)]*)\\)").findAll(activitySource)
                .map { it.groupValues[1].trim() }.toList()
        assertTrue("the token is the one parameter read", parameters.isNotEmpty())
        parameters.forEach { assertTrue("unexpected query parameter $it", it.endsWith("TOKEN_PARAMETER")) }
    }

    @Test
    fun theActivityStartsOnlyWhatTheRegistryPlanned() {
        assertTrue(activitySource.contains("VisiblePlaybackRegistry.claim("))
        assertTrue(activitySource.contains("VisibleLaunchIntents.start("))
        assertFalse(activitySource.contains("startActivity("))
    }

    @Test
    fun playFromSearchDeclarationsAreVisibleToTheApp() {
        // Without this query an alternative client's voice-search entry is invisible on Android 11+,
        // and the plan silently never offers the name search for it.
        assertTrue(manifest.contains("<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />"))
    }
}
