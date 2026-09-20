package com.svartifoss.snfell.common

import android.content.SharedPreferences
import com.matejdro.wearutils.preferences.definition.Preferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class PinchPreferencesTest {
    private val sensitivity = MiscPreferences.WEAR_PINCH_SENSITIVITY.key
    private val gap = MiscPreferences.WEAR_PINCH_MAX_GAP.key
    private val cooldown = MiscPreferences.WEAR_PINCH_COOLDOWN.key

    @Test fun `integer settings from the original calibration build remain readable`() {
        val prefs = preferences(mutableMapOf(sensitivity to 140, gap to 900, cooldown to 1200))
        // Android's typed getter throws for these persisted values, including on next launch.
        assertThrows(ClassCastException::class.java) {
            Preferences.getInt(prefs, MiscPreferences.WEAR_PINCH_SENSITIVITY)
        }
        assertEquals(PinchDetectorSettings(140, 900, 1200), PinchPreferences.readSettings(prefs))
    }

    @Test fun `text and mixed settings are readable while an older phone still syncs integers`() {
        for (values in listOf(
                mutableMapOf<String, Any?>(sensitivity to "140", gap to "900", cooldown to "1200"),
                mutableMapOf<String, Any?>(sensitivity to "140", gap to 900, cooldown to "1200")
        )) {
            assertEquals(PinchDetectorSettings(140, 900, 1200),
                    PinchPreferences.readSettings(preferences(values)))
        }
    }

    @Test fun `missing or corrupt settings default and out of range values are clamped`() {
        assertEquals(PinchDetectorSettings(), PinchPreferences.readSettings(preferences(mutableMapOf())))
        assertEquals(PinchDetectorSettings(), PinchPreferences.readSettings(preferences(mutableMapOf(
                sensitivity to true, gap to "2147483648", cooldown to "broken"))))
        assertEquals(PinchDetectorSettings(200, 250, 3000),
                PinchPreferences.readSettings(preferences(mutableMapOf(
                        sensitivity to Int.MAX_VALUE, gap to "-10", cooldown to 9000))))
    }

    @Test fun `saving repairs integer storage and round trips through the standard reader`() {
        val values = mutableMapOf<String, Any?>(sensitivity to 140, gap to 900, cooldown to 1200,
                MiscPreferences.WEAR_PINCH_CALIBRATION.key to "1;0.03;1.0;0.5",
                "unrelated" to true)
        val prefs = preferences(values)
        PinchPreferences.writeSettings(prefs.edit(), PinchPreferences.readSettings(prefs))
        assertEquals("140", values[sensitivity])
        assertEquals("900", values[gap])
        assertEquals("1200", values[cooldown])
        assertEquals(140, Preferences.getInt(prefs, MiscPreferences.WEAR_PINCH_SENSITIVITY))
        assertEquals(900, Preferences.getInt(prefs, MiscPreferences.WEAR_PINCH_MAX_GAP))
        assertEquals(1200, Preferences.getInt(prefs, MiscPreferences.WEAR_PINCH_COOLDOWN))
        assertEquals(PinchDetectorSettings(140, 900, 1200), PinchPreferences.readSettings(prefs))
        assertEquals("1;0.03;1.0;0.5", values[MiscPreferences.WEAR_PINCH_CALIBRATION.key])
        assertEquals(true, values["unrelated"])
    }

    @Test fun `saving normalizes all three settings`() {
        val prefs = preferences(mutableMapOf())
        PinchPreferences.writeSettings(prefs.edit(), PinchDetectorSettings(0, 9000, -1))
        assertEquals(PinchDetectorSettings(50, 1500, 250), PinchPreferences.readSettings(prefs))
    }

    /** Strict Android-style typed reads, without requiring an Android runtime. */
    private fun preferences(values: MutableMap<String, Any?>): SharedPreferences {
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putString" -> { values[args!![0] as String] = args[1] as String?; proxy }
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
                arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getAll" -> values.toMap()
                "getString" -> (values[args!![0]] ?: args[1]) as String?
                "edit" -> editor
                else -> error("Unexpected preference call: ${method.name}")
            }
        } as SharedPreferences
    }
}
