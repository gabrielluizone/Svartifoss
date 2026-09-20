package com.svartifoss.snfell.res

import com.svartifoss.snfell.common.actions.StandardActions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pins the one invariant behind `MusicService.executeAction`: every action the watch can send is
 * either executable on the phone or knowingly dropped, and never both.
 *
 * An action the phone cannot run is not a hypothetical. The button config the watch runs is pushed
 * from the phone, so a watch build older than an action still receives, renders and sends it - and
 * for the actions the watch carries out itself there is nothing here to bind a handler to, since
 * the effect is a screen on the other device. Until [StandardActions.WATCH_LOCAL] existed the phone
 * answered each of those presses by throwing "Action handler for ... missing"; the quick-actions
 * panel reported it from real installs.
 *
 * Both directions matter, and they fail in opposite ways. A watch-local action left out of the set
 * throws again. One listed there *and* bound to a handler would be silently dropped instead of run,
 * which is the worse failure: the drop branch would swallow an action the phone was able to
 * perform, and nothing anywhere would say so.
 *
 * Reads `ActionHandlersModule.kt` off disk like the sibling `res/` invariant tests, because the
 * bindings are Dagger multibindings resolved by codegen - there is no map to inspect from a plain
 * JVM test, and the annotation is the only declaration of what exists.
 */
class WatchLocalActionHandlerTest {

    private fun repoFile(path: String): File {
        listOf(File(path), File("../$path")).forEach { if (it.exists()) return it }
        fail("Not found from either module dir or repo root: $path")
        error("unreachable")
    }

    /** The simple class names bound as `@ClassKey(SomeAction::class)` in the Dagger module. */
    private fun boundHandlerClasses(): Set<String> {
        val module = repoFile(
                "mobile/src/main/java/com/svartifoss/snfell/di/ActionHandlersModule.kt").readText()
        val bound = Regex("""@ClassKey\(\s*([A-Za-z0-9_.]+)::class""")
                .findAll(module)
                .map { it.groupValues[1].substringAfterLast('.') }
                .toSet()

        assertTrue("Parsed no @ClassKey bindings - has the module's shape changed?",
                bound.size > 20)
        return bound
    }

    /** Every `ACTION_*` constant, which is to say every action a watch may ask the phone to run. */
    private fun declaredActionKeys(): Set<String> {
        val declarations = repoFile(
                "common/src/main/java/com/svartifoss/snfell/common/actions/StandardActions.kt")
                .readText()
        val keys = Regex("""const val ACTION_[A-Z0-9_]+\s*=\s*\n?\s*"([^"]+)"""")
                .findAll(declarations)
                .map { it.groupValues[1] }
                .toSet()

        assertTrue("Parsed no ACTION_* constants - has StandardActions' shape changed?",
                keys.size > 20)
        return keys
    }

    private fun simpleName(actionKey: String): String = actionKey.substringAfterLast('.')

    @Test
    fun noWatchLocalActionHasAHandlerBound() {
        val bound = boundHandlerClasses()
        val contradictions = StandardActions.WATCH_LOCAL.filter { simpleName(it) in bound }

        assertEquals("These actions are bound to a phone-side handler AND listed as watch-local," +
                " so MusicService.executeAction drops them instead of running them." +
                " Remove them from StandardActions.WATCH_LOCAL.",
                emptyList<String>(), contradictions)
    }

    @Test
    fun everyDeclaredActionIsEitherRunnableHereOrKnowinglyWatchLocal() {
        val bound = boundHandlerClasses()
        val unaccounted = declaredActionKeys()
                .filter { simpleName(it) !in bound && it !in StandardActions.WATCH_LOCAL }

        assertEquals("These actions have no ActionHandler bound in ActionHandlersModule and are" +
                " not in StandardActions.WATCH_LOCAL, so an older watch sending one crashes the" +
                " phone's executeAction. Bind a handler, or list it as watch-local.",
                emptyList<String>(), unaccounted)
    }

    @Test
    fun everyWatchLocalActionIsADeclaredAction() {
        val declared = declaredActionKeys()
        val strays = StandardActions.WATCH_LOCAL.filterNot { it in declared }

        assertEquals("WATCH_LOCAL names actions no ACTION_* constant declares - a renamed or" +
                " deleted action class leaves a key here that matches nothing.",
                emptyList<String>(), strays)
    }
}
