package com.svartifoss.snfell.logging

/**
 * Marks the frames of an exception the watch forwarded (ExceptionWear) so Crashlytics leaves them
 * as they are.
 *
 * Crashlytics deobfuscates every frame of a report with the R8 mapping of the build that sent it,
 * which for these is the *phone's* - while the frames were obfuscated by the watch's own, separate
 * R8 run. Both runs hand out the same short names (`a`, `a.b`, ...), and a class kept by name on
 * both sides (an activity, a fragment, a view) still has its members renamed differently in each,
 * so an unmarked watch frame is routinely "retraced" into some unrelated phone method: a confident
 * wrong answer, which is worse than an obfuscated one. A class name that no mapping can contain
 * makes every lookup miss, so the frames reach the console exactly as the watch reported them.
 *
 * To read one, drop the prefix and retrace it with that watch build's mapping
 * (`wear/build/outputs/mapping/<variant>/mapping.txt`, or the file Play Console offers for the watch
 * bundle).
 */
object WatchCrashFrames {
    /** A colon can never appear in a DEX class name, so no mapping can match a marked frame. */
    const val PREFIX = "watch:"

    fun mark(frames: Array<StackTraceElement>): Array<StackTraceElement> = Array(frames.size) { index ->
        val frame = frames[index]
        if (frame.className.startsWith(PREFIX)) {
            frame
        } else {
            StackTraceElement(PREFIX + frame.className, frame.methodName, frame.fileName,
                    frame.lineNumber)
        }
    }
}
