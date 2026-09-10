package com.svartifoss.snfell.logging

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Asks the framework, once per process, whether it really has the API level it advertises, and
 * labels the session with the answer. See [PlatformApiIntegrity] for why.
 *
 * The three members probed are exactly the three that have been seen to be missing on a device
 * reporting 34, one per library that failed: `AccessibilityNodeInfo.AccessibilityAction`'s
 * `ACTION_SCROLL_IN_DIRECTION` (androidx.core accessibility, reached from every ViewPager2 and
 * RecyclerView), `TextView.setLineHeight(int, float)` (androidx.core widget, reached from the
 * inflation of any Material component whose style sets a line height) and
 * `android.credentials.CredentialManager` (androidx.credentials). They are looked up
 * reflectively - a lookup returns an absence, where a direct reference would raise the very
 * linkage error this exists to explain - and all three are public SDK members, so the non-SDK
 * interface restrictions do not apply to them.
 */
object PlatformApiProbe {

    /**
     * Runs the probe and records the verdict.
     *
     * Called once from `WearMusicCenter.onCreate`, after Timber is planted so a warning has
     * somewhere to go and after [CrashReporting] has read the user's choice: a device fact is
     * still telemetry, so an opt-out silences it like everything else.
     *
     * Deliberately never throws. It runs before the first activity, and a diagnostic that can
     * take the process down is worse than the condition it reports on.
     */
    fun runAndReport() {
        val report = try {
            PlatformApiIntegrity.resolve(Build.VERSION.SDK_INT, absentMembers())
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not probe the platform's API integrity")
            return
        }

        if (!report.consistent) {
            // WARN rather than ERROR on purpose. TimberCrashlytics turns ERROR into a non-fatal
            // issue of its own, and this device is going to crash inside a library within
            // seconds anyway; as a breadcrumb the message lands on that real crash, which is the
            // report somebody will actually be reading.
            Timber.w("Platform reports API %d without carrying it: %s",
                    Build.VERSION.SDK_INT, report.summary)
        }

        if (CrashReporting.enabled) {
            try {
                FirebaseCrashlytics.getInstance()
                        .setCustomKey(PlatformApiIntegrity.CUSTOM_KEY, report.summary)
            } catch (e: RuntimeException) {
                Timber.w(e, "Could not record the platform API integrity key")
            }
        }
    }

    private fun absentMembers(): List<String> {
        if (Build.VERSION.SDK_INT < PlatformApiIntegrity.PROBED_FROM_SDK) return emptyList()
        return buildList {
            if (!hasField(AccessibilityNodeInfo.AccessibilityAction::class.java,
                            "ACTION_SCROLL_IN_DIRECTION")) {
                add("AccessibilityAction.ACTION_SCROLL_IN_DIRECTION")
            }
            if (!hasMethod(TextView::class.java, "setLineHeight",
                            Int::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!)) {
                add("TextView.setLineHeight")
            }
            if (!hasClass("android.credentials.CredentialManager")) {
                add("android.credentials.CredentialManager")
            }
        }
    }

    private fun hasField(owner: Class<*>, name: String): Boolean = try {
        owner.getField(name)
        true
    } catch (_: NoSuchFieldException) {
        false
    } catch (_: LinkageError) {
        false
    }

    private fun hasMethod(owner: Class<*>, name: String, vararg types: Class<*>): Boolean = try {
        owner.getMethod(name, *types)
        true
    } catch (_: NoSuchMethodException) {
        false
    } catch (_: LinkageError) {
        false
    }

    // initialize = false: the question is whether the class is there, and running a framework
    // class's static initializer to find out is a side effect this has no business causing.
    private fun hasClass(name: String): Boolean = try {
        Class.forName(name, false, PlatformApiProbe::class.java.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    } catch (_: LinkageError) {
        false
    }
}
