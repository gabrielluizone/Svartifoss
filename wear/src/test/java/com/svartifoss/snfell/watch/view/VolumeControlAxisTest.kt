package com.svartifoss.snfell.watch.view

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeControlAxisTest {

    @Test
    fun `every layout is answered for`() {
        // An exhaustive `when` already guarantees this at compile time; the test exists so a new
        // layout added with a hasty `else` branch still has to state which way its controls read.
        VolumeLayout.values().forEach { VolumeControlAxis.forLayout(it) }
    }

    @Test
    fun `arcs that fill upwards keep their controls above and below`() {
        listOf(
                VolumeLayout.EDGE,
                VolumeLayout.EDGE_TALL,
                VolumeLayout.EDGE_RIGHT,
                VolumeLayout.RING,
                VolumeLayout.DIAL,
                VolumeLayout.VERTICAL_LEFT
        ).forEach {
            assertEquals(it.name, VolumeControlAxis.VERTICAL, VolumeControlAxis.forLayout(it))
        }
    }

    @Test
    fun `bars that fill sideways move their controls onto the horizontal axis`() {
        listOf(
                VolumeLayout.EDGE_TOP,
                VolumeLayout.EDGE_BOTTOM,
                VolumeLayout.METER,
                VolumeLayout.METER_BOTTOM
        ).forEach {
            assertEquals(it.name, VolumeControlAxis.HORIZONTAL, VolumeControlAxis.forLayout(it))
        }
    }
}
