package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HandGestureAvailabilityTest {

    @Test
    fun `every state survives the round trip the two apps send it over`() {
        for (availability in HandGestureAvailability.values()) {
            assertEquals(availability, HandGestureAvailability.fromCode(availability.code))
        }
    }

    @Test
    fun `an absent field reads as no answer rather than as a verdict`() {
        // proto2 hands back 0 for an optional int32 a pre-field watch build never wrote, and the
        // phone must not turn that into "this watch cannot do it" - that is the one message the
        // user would act on by giving up.
        assertEquals(HandGestureAvailability.UNKNOWN, HandGestureAvailability.fromCode(0))
        assertNotEquals(HandGestureAvailability.UNSUPPORTED, HandGestureAvailability.fromCode(0))
    }

    @Test
    fun `a state this build has never heard of is no answer either`() {
        // The watch app updates separately from the phone app, so a newer watch can report a
        // state added after this phone build shipped.
        assertEquals(HandGestureAvailability.UNKNOWN, HandGestureAvailability.fromCode(99))
        assertEquals(HandGestureAvailability.UNKNOWN, HandGestureAvailability.fromCode(-1))
    }

    @Test
    fun `codes are distinct, since the wire form is the code and not the ordinal`() {
        val codes = HandGestureAvailability.values().map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }
}
