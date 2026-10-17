package com.svartifoss.snfell.watch.communication

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The watch's own `com.google.android.wearable.intent.RemoteIntent`, and why it has to exist.
 *
 * wearutils' "install the phone app" screen opens the Play listing on the phone through the Wear
 * Support Library's `RemoteIntent`. The watch build excludes that library (its classes duplicate the
 * platform's `com.google.android.wearable` shared library, which has no `intent` package), so the
 * class was missing at runtime and tapping the screen's only button died with
 * `NoClassDefFoundError`. That went unseen for as long as the button was cut off by the screen's edge
 * on a small round watch; fixing the layout is what made the button reachable and the crash with it.
 * The shim is the class that screen already names, over `RemoteActivityHelper`.
 *
 * Three ways it can quietly stop working, each pinned here against the text, since the caller lives in
 * a submodule and compiled its constants in: the caller stops using the class (the shim is then dead
 * weight and the exclusion's reason is gone), a result code drifts from the real library's (the
 * caller compares against its own inlined copy, so a drift reports a failure as success), or the
 * helper's failure stops being caught (a watch with no phone paired - a reviewer's - then loses the
 * whole process on a tap instead of seeing "Could not open on phone").
 */
class RemoteIntentShimContractTest {

    private companion object {
        val SHIM = File("src/main/java/com/google/android/wearable/intent/RemoteIntent.kt")

        // Tests run from the module directory; the submodule is its sibling.
        val NOTICE = File(
                "../wearutils/src/main/java/com/matejdro/wearutils/companionnotice/PhoneAppNoticeActivity.java")
    }

    @Test
    fun `the notice screen still calls the class the shim stands in for`() {
        assertTrue("${NOTICE.path} is missing - has wearutils moved?", NOTICE.exists())
        val source = NOTICE.readText()
        assertTrue("PhoneAppNoticeActivity no longer imports com.google.android.wearable.intent." +
                "RemoteIntent: if it moved to RemoteActivityHelper itself, delete the shim " +
                "(${SHIM.path}) and this test.",
                source.contains("import com.google.android.wearable.intent.RemoteIntent;"))
        assertTrue("PhoneAppNoticeActivity no longer calls RemoteIntent.startRemoteActivity",
                source.contains("RemoteIntent.startRemoteActivity("))
    }

    @Test
    fun `the shim has the signature and result codes of the real class`() {
        assertTrue("${SHIM.path} is missing: the notice screen's button would crash with " +
                "NoClassDefFoundError on the watch", SHIM.exists())
        val source = SHIM.readText()
        assertTrue("The shim must be a Kotlin object named RemoteIntent",
                Regex("""\bobject\s+RemoteIntent\b""").containsMatchIn(source))
        // The real values, read off the Wear Support Library 2.9.0 class with javap.
        assertTrue("RESULT_OK must be 0, as in the real class",
                Regex("""const\s+val\s+RESULT_OK\s*=\s*0\b""").containsMatchIn(source))
        assertTrue("RESULT_FAILED must be 1, as in the real class",
                Regex("""const\s+val\s+RESULT_FAILED\s*=\s*1\b""").containsMatchIn(source))
        // The caller is Java: it links against a static method with exactly this descriptor.
        assertTrue("startRemoteActivity must be @JvmStatic fun (Context, Intent, ResultReceiver?)",
                Regex("""@JvmStatic\s+fun\s+startRemoteActivity\(\s*context:\s*Context,\s*intent:\s*Intent,\s*resultReceiver:\s*ResultReceiver\?\s*\)""")
                        .containsMatchIn(source))
    }

    @Test
    fun `a failure of the helper reaches the receiver instead of the process`() {
        val source = SHIM.readText()
        val cancellation = source.indexOf("catch (e: CancellationException)")
        val throwable = source.indexOf("catch (e: Throwable)")
        assertTrue("The shim must catch Throwable, not Exception: with no companion node the platform " +
                "fails the helper's future with a bare Throwable, which an Exception catch lets " +
                "through to the coroutine and so to the process", throwable >= 0)
        assertTrue("Cancellation must be rethrown before everything is swallowed",
                cancellation in 0 until throwable)
        val send = source.indexOf("resultReceiver?.send(")
        assertTrue("The receiver must be told the outcome after the attempt, success or not",
                send > throwable)
        assertEquals("The helper must be given a time limit, or a paired phone that is out of reach " +
                "leaves the caller's spinner turning forever",
                1, Regex("""withTimeoutOrNull\(""").findAll(source).count())
    }
}
