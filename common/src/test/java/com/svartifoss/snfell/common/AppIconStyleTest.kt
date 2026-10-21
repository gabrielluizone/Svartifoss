package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconStyleTest {

    @Test
    fun `preference values round trip`() {
        AppIconStyle.entries.forEach { style ->
            assertEquals(style, AppIconStyle.fromPreference(style.preferenceValue))
        }
    }

    @Test
    fun `missing and unknown values use gradient`() {
        assertEquals(AppIconStyle.GRADIENT, AppIconStyle.fromPreference(null))
        assertEquals(AppIconStyle.GRADIENT, AppIconStyle.fromPreference("neon"))
        assertEquals(AppIconStyle.GRADIENT, AppIconStyle.DEFAULT)
    }

    @Test
    fun `the stored default is the default style`() {
        assertEquals(AppIconStyle.DEFAULT.preferenceValue, MiscPreferences.APP_ICON_STYLE.defaultValue)
    }

    @Test
    fun `preference values are unique and lower case`() {
        val values = AppIconStyle.entries.map { it.preferenceValue }
        assertEquals("two styles share a preference value", values.size, values.toSet().size)
        values.forEach {
            assertEquals("preference values are protocol identifiers shared with the watch", it.lowercase(), it)
            assertTrue(it.matches(Regex("[a-z]+")))
        }
    }

    /**
     * The alias is how the launcher tells the styles apart: two styles on one alias would leave
     * the other's icon permanently unreachable, and a name that is not a relative class reference
     * (".Something") would be looked up under the wrong package.
     */
    @Test
    fun `every style has its own relative alias`() {
        val aliases = AppIconStyle.entries.map { it.aliasName }
        assertEquals("two styles share a launcher alias", aliases.size, aliases.toSet().size)
        aliases.forEach {
            assertTrue("$it must be a relative class name", it.startsWith(".LauncherIcon"))
        }
    }

    @Test
    fun `the styles added after the original two keep the picker order`() {
        // The enum is the picker's order: the arrays pair with it by index, so a new style goes last.
        assertEquals(listOf("solid", "gradient", "cream", "feather"), AppIconStyle.entries.map { it.preferenceValue })
        assertEquals(".LauncherIconCream", AppIconStyle.CREAM.aliasName)
        assertEquals(".LauncherIconFeather", AppIconStyle.FEATHER.aliasName)
    }
}
