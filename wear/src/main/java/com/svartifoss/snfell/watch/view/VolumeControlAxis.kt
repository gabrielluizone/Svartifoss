package com.svartifoss.snfell.watch.view

/**
 * Which axis the louder/quieter controls sit on for a given [VolumeLayout].
 *
 * The rule is that the controls follow the direction the fill actually travels: an arc that fills
 * bottom-to-top puts them above and below, one that fills left-to-right puts them either side.
 * Anything else asks the user to press "up" sideways.
 *
 * Shared because there are two surfaces drawing those controls - the transient overlay's glyphs in
 * `MainActivity.applyVolumePanelLayout` and the dedicated volume screen's step buttons - and they
 * had already disagreed: the screen pinned both buttons to the bottom corners regardless of layout,
 * so with the default left-edge arc the arc filled upwards while its controls sat side by side.
 */
enum class VolumeControlAxis {
    /** Quieter below, louder above. */
    VERTICAL,

    /** Quieter to the left, louder to the right. */
    HORIZONTAL;

    companion object {
        fun forLayout(layout: VolumeLayout): VolumeControlAxis = when (layout) {
            VolumeLayout.EDGE,
            VolumeLayout.EDGE_TALL,
            VolumeLayout.EDGE_RIGHT,
            VolumeLayout.RING,
            VolumeLayout.HALO,
            VolumeLayout.HALO_TOP,
            VolumeLayout.HALO_BOTTOM,
            VolumeLayout.DOUBLE_EDGE,
            VolumeLayout.DIAL,
            // The upright meters fill bottom-to-top like the edge arcs, so they read the same way
            // even though they are not arcs.
            VolumeLayout.VERTICAL_LEFT,
            VolumeLayout.VERTICAL_RIGHT -> VERTICAL

            VolumeLayout.EDGE_TOP,
            VolumeLayout.EDGE_BOTTOM,
            VolumeLayout.METER,
            VolumeLayout.METER_TOP,
            VolumeLayout.METER_BOTTOM -> HORIZONTAL
        }
    }
}
