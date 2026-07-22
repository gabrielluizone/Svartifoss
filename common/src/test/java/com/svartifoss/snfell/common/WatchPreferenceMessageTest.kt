package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPreferenceMessageTest {

    @Test
    fun roundTripsEverySupportedType() {
        val values = mapOf(
                "anInt" to 7,
                "aLong" to 9_000_000_000L,
                "aBool" to true,
                "aFloat" to 1.5f,
                "aString" to "hello@face",
                "aSet" to setOf("a", "b", "c")
        )
        val decoded = WatchPreferenceMessage.decode(WatchPreferenceMessage.encode(42L, values))!!
        assertEquals(42L, decoded.sequence)
        assertEquals(7, decoded.values["anInt"])
        assertEquals(9_000_000_000L, decoded.values["aLong"])
        assertEquals(true, decoded.values["aBool"])
        assertEquals(1.5f, decoded.values["aFloat"])
        assertEquals("hello@face", decoded.values["aString"])
        assertEquals(setOf("a", "b", "c"), decoded.values["aSet"])
    }

    @Test
    fun skipsUnsupportedAndNullValues() {
        val values = mapOf<String, Any?>(
                "ok" to "value",
                "nullValue" to null,
                "unsupported" to WatchPreferenceMessageTest()
        )
        val decoded = WatchPreferenceMessage.decode(WatchPreferenceMessage.encode(1L, values))!!
        assertEquals(1, decoded.values.size)
        assertEquals("value", decoded.values["ok"])
    }

    @Test
    fun malformedInputDecodesToNull() {
        assertNull(WatchPreferenceMessage.decode(null))
        assertNull(WatchPreferenceMessage.decode(ByteArray(0)))
        assertNull(WatchPreferenceMessage.decode(byteArrayOf(9, 9, 9, 9)))
    }

    @Test
    fun sequenceIsPreservedForOrderingGuard() {
        // The watch compares this to decide whether a message is newer; a later encode must carry
        // a larger sequence for that guard to work.
        val older = WatchPreferenceMessage.decode(WatchPreferenceMessage.encode(100L, mapOf("k" to 1)))!!
        val newer = WatchPreferenceMessage.decode(WatchPreferenceMessage.encode(200L, mapOf("k" to 2)))!!
        assertTrue(newer.sequence > older.sequence)
    }
}
