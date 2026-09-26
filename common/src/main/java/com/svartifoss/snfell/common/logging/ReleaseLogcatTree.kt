package com.svartifoss.snfell.common.logging

import timber.log.Timber

/**
 * Logcat for a release build: warnings and errors, as `AndroidDebugTree(false)` always printed -
 * without paying for everything it then threw away.
 *
 * The Timber this app bundles formats every message (a `String.format` over its arguments) and
 * works out its tag (a `Throwable` created just to walk the stack) in the base class, *before* a
 * tree's `log` gets to decide whether it wants the line. `AndroidDebugTree(false)` discards
 * verbose, debug and info - nearly everything the app logs - so in release every one of those calls
 * was formatted and stack-walked for nothing, on the main thread more often than not, on both the
 * phone and the watch.
 *
 * Only the three levels it drops are short-circuited. Warnings and errors go through untouched:
 * the tag is read from a fixed depth of the call stack, and an override that called on to the base
 * class would put itself at that depth and log every line under this class's name.
 */
class ReleaseLogcatTree : Timber.AndroidDebugTree(false) {
    override fun v(message: String?, vararg args: Any?) = Unit
    override fun v(t: Throwable?, message: String?, vararg args: Any?) = Unit
    override fun d(message: String?, vararg args: Any?) = Unit
    override fun d(t: Throwable?, message: String?, vararg args: Any?) = Unit
    override fun i(message: String?, vararg args: Any?) = Unit
    override fun i(t: Throwable?, message: String?, vararg args: Any?) = Unit
}
