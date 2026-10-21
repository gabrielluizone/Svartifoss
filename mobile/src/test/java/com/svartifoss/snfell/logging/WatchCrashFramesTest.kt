package com.svartifoss.snfell.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchCrashFramesTest {

    @Test
    fun everyFrameIsMarkedAndOtherwiseLeftAsTheWatchReportedIt() {
        val frames = arrayOf(
                StackTraceElement("w.a", "b", "SourceFile", 12),
                StackTraceElement("android.os.Handler", "dispatchMessage", "Handler.java", 106),
        )

        val marked = WatchCrashFrames.mark(frames)

        assertEquals(2, marked.size)
        assertEquals("watch:w.a", marked[0].className)
        assertEquals("b", marked[0].methodName)
        assertEquals("SourceFile", marked[0].fileName)
        assertEquals(12, marked[0].lineNumber)
        assertEquals("watch:android.os.Handler", marked[1].className)
        assertEquals(106, marked[1].lineNumber)
    }

    @Test
    fun aFrameWithoutFileOrLineKeepsThatToo() {
        // A native method reports line -2 and no file; retrace has to see exactly that.
        val marked = WatchCrashFrames.mark(arrayOf(StackTraceElement("w.c", "d", null, -2)))

        assertEquals(null, marked[0].fileName)
        assertEquals(-2, marked[0].lineNumber)
        assertTrue(marked[0].isNativeMethod)
    }

    @Test
    fun markingTwiceDoesNotStackPrefixes() {
        val once = WatchCrashFrames.mark(arrayOf(StackTraceElement("w.a", "b", "SourceFile", 1)))

        val twice = WatchCrashFrames.mark(once)

        assertEquals("watch:w.a", twice[0].className)
    }

    @Test
    fun theMarkCannotBePartOfAnyRealClassName() {
        // The whole point is that no R8 mapping can contain the marked name, which holds only while
        // the prefix carries a character the DEX format forbids in a class name.
        assertTrue(WatchCrashFrames.PREFIX.contains(':'))
    }
}
