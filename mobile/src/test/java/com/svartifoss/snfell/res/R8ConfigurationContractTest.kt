package com.svartifoss.snfell.res

import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.common.actions.StandardActions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pins how both release builds run R8, and the few names R8 is not allowed to touch.
 *
 * Play Console grades each bundle on how much of its code R8 was allowed to optimize, rename and
 * remove (the percentages R8 writes into the bundle's r8.json) and flags any category under 25%.
 * 4.2 went out with `-dontoptimize` (from plain proguard-android.txt) and `-dontobfuscate`, graded
 * 0% for both. Either switch is the obvious one-line fix for some release-only crash, and a keep
 * rule over a whole package is the next most obvious, so both are refused here.
 *
 * The other half is the names that *are* data, where obfuscation breaks the app without a single
 * error at build time: a `PhoneAction`'s class name is stored in every saved action and is the key
 * the watch matches on, protobuf-lite resolves message fields by name, and the watch's exception
 * names are filtered on by the phone. Each rule is checked against the code that depends on it, so
 * moving a class or changing a constructor fails here rather than in an installed release build.
 * A keep rule naming a class that no longer exists is silently a no-op to R8.
 *
 * Reads the tree off disk like its siblings in this package.
 */
class R8ConfigurationContractTest {

    private fun repoFile(path: String): File {
        listOf(File(path), File("../$path")).forEach { if (it.exists()) return it }
        fail("Not found from either module dir or repo root: $path")
        error("unreachable")
    }

    private fun text(path: String): String = repoFile(path).readText()

    private fun codeLines(path: String, comment: String): List<String> = text(path).lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(comment) }

    private val appBuildFiles = listOf("mobile/build.gradle", "wear/build.gradle")
    private val appRuleFiles = listOf("mobile/proguard-rules.pro", "wear/proguard-rules.pro")

    @Test
    fun bothAppsUseTheOptimizingDefaults() {
        appBuildFiles.forEach { path ->
            val lines = codeLines(path, "//").filter { it.contains("getDefaultProguardFile") }
            assertTrue("$path must name its default R8 configuration", lines.isNotEmpty())
            lines.forEach {
                assertTrue("$path must use proguard-android-optimize.txt - plain " +
                        "proguard-android.txt carries -dontoptimize, graded 0% by Play: $it",
                        it.contains("'proguard-android-optimize.txt'"))
            }
        }
    }

    @Test
    fun noRulesFileSwitchesAnR8PassOff() {
        val switches = listOf("-dontoptimize", "-dontobfuscate", "-dontshrink", "-optimizations")
        appRuleFiles.forEach { path ->
            val offending = codeLines(path, "#").filter { line -> switches.any { line.startsWith(it) } }
            assertEquals("$path turns an R8 pass off for the whole app. Keep the class that needs " +
                    "it instead (and -optimizations is ignored by R8 altogether).",
                    emptyList<String>(), offending)
        }
    }

    @Test
    fun noRulesFileKeepsAWholePackage() {
        val keepTarget = Regex("""^-keep\S*\s+(?:@\S+\s+)?(?:public\s+)?(?:class|interface|enum)\s+(\S+)""" +
                """(\s+(?:extends|implements)\s)?""")
        appRuleFiles.forEach { path ->
            val broad = codeLines(path, "#").filter { line ->
                val match = keepTarget.find(line) ?: return@filter false
                val target = match.groupValues[1]
                // `class * extends X` is a hierarchy, not everything.
                val narrowedToAHierarchy = match.groupValues[2].isNotEmpty()
                (!narrowedToAHierarchy && (target == "*" || target == "**")) ||
                        (target.endsWith(".**") && target.startsWith("com.svartifoss"))
            }
            assertEquals("$path keeps a whole package, which takes all of it out of optimization " +
                    "and obfuscation. Keep the classes whose names are actually data.",
                    emptyList<String>(), broad)
        }
    }

    @Test
    fun actionsKeepTheNameAndConstructorTheDeserializerLooksUp() {
        val rules = text("mobile/proguard-rules.pro")
        assertTrue("mobile/proguard-rules.pro must keep every PhoneAction subclass by name with its " +
                "(Context, PersistableBundle) constructor",
                Regex("""-keep class \* extends com\.svartifoss\.snfell\.actions\.PhoneAction \{\s*""" +
                        """public <init>\(android\.content\.Context, android\.os\.PersistableBundle\);\s*}""")
                        .containsMatchIn(rules))

        // The rule names the class by its full name; R8 says nothing if that stops matching.
        assertEquals("com.svartifoss.snfell.actions.PhoneAction", PhoneAction::class.java.name)

        val deserializer = text("mobile/src/main/java/com/svartifoss/snfell/actions/PhoneAction.kt")
        assertTrue("PhoneAction.deserialize no longer looks the constructor up as " +
                "(Context, PersistableBundle) - update the keep rule to the new signature",
                deserializer.contains("Class.forName(className)") && deserializer.contains(
                        "getConstructor(Context::class.java, PersistableBundle::class.java)"))
    }

    @Test
    fun bundlablesKeepTheNameAndConstructorTheirDeserializerLooksUp() {
        val rules = text("mobile/proguard-rules.pro")
        assertTrue("mobile/proguard-rules.pro must keep every Bundlable by name with its " +
                "(PersistableBundle) constructor",
                Regex("""-keep class \* extends com\.matejdro\.wearutils\.serialization\.Bundlable \{\s*""" +
                        """public <init>\(android\.os\.PersistableBundle\);\s*}""")
                        .containsMatchIn(rules))

        val bundlable = text("wearutils/src/main/java/com/matejdro/wearutils/serialization/Bundlable.java")
        assertTrue("Bundlable no longer stores and resolves the class name the keep rules protect",
                bundlable.contains("package com.matejdro.wearutils.serialization;") &&
                        bundlable.contains("getCanonicalName()") &&
                        bundlable.contains("getConstructor(PersistableBundle.class)"))
    }

    @Test
    fun everyStandardActionKeyIsTheNameOfAnAction() {
        // These strings are what the phone sends as an actionKey and the watch matches on. With
        // the keep rule above they are the classes' real names in a release build too - provided
        // they are the real names to begin with.
        val declarations = text(
                "common/src/main/java/com/svartifoss/snfell/common/actions/StandardActions.kt")
        val keys = Regex("""const val ACTION_[A-Z0-9_]+\s*=\s*\n?\s*"([^"]+)"""")
                .findAll(declarations)
                .map { it.groupValues[1] }
                .toList()
        assertTrue("Parsed no ACTION_* constants - has StandardActions' shape changed?",
                keys.size > 20)

        val notActions = keys.filterNot { key ->
            val type = try {
                Class.forName(key, false, javaClass.classLoader)
            } catch (_: ClassNotFoundException) {
                null
            }
            type != null && PhoneAction::class.java.isAssignableFrom(type)
        }
        assertEquals("These StandardActions keys are not the full name of a PhoneAction class, so " +
                "the watch can never match them against what the phone sends.",
                emptyList<String>(), notActions)
        assertTrue(StandardActions.WATCH_LOCAL.all { it in keys })
    }

    @Test
    fun bothAppsKeepProtobufMessageFields() {
        appRuleFiles.forEach { path ->
            assertTrue("$path must keep every field of every protobuf-lite message by name",
                    Regex("""-keepclassmembers class \* extends com\.google\.protobuf\.GeneratedMessageLite \{\s*""" +
                            """<fields>;\s*}""").containsMatchIn(text(path)))
        }
    }

    @Test
    fun theWatchKeepsTheExceptionNamesThePhoneFiltersOn() {
        assertTrue("wear/proguard-rules.pro must keep Throwable names: the phone drops forwarded " +
                "coroutine cancellations by finding the type name in the message",
                codeLines("wear/proguard-rules.pro", "#")
                        .contains("-keepnames class * extends java.lang.Throwable"))

        val handler = text(
                "mobile/src/main/java/com/svartifoss/snfell/logging/CrashlyticsExceptionWearHandler.kt")
        assertTrue("CrashlyticsExceptionWearHandler no longer filters on the forwarded type name; " +
                "revisit whether the watch still needs to keep Throwable names",
                handler.contains("\"CancellationException\""))
    }
}
