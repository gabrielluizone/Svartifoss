package com.svartifoss.snfell.view.output

import com.svartifoss.snfell.view.output.AudioOutputRoute.BLUETOOTH_SETTINGS
import com.svartifoss.snfell.view.output.AudioOutputRoute.MEDIA_OUTPUT_DIALOG
import com.svartifoss.snfell.view.output.AudioOutputRoute.MEDIA_OUTPUT_PANEL
import com.svartifoss.snfell.view.output.AudioOutputRoute.SYSTEM_SWITCHER
import com.svartifoss.snfell.view.output.AudioOutputRoute.VOLUME_PANEL
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioOutputRoutesTest {

    @Test
    fun `each version tries what it has, best first, and always ends in Bluetooth settings`() {
        assertEquals(listOf(SYSTEM_SWITCHER, VOLUME_PANEL, BLUETOOTH_SETTINGS), AudioOutputRoute.forSdk(36))
        assertEquals(listOf(SYSTEM_SWITCHER, VOLUME_PANEL, BLUETOOTH_SETTINGS), AudioOutputRoute.forSdk(34))
        assertEquals(listOf(MEDIA_OUTPUT_DIALOG, VOLUME_PANEL, BLUETOOTH_SETTINGS), AudioOutputRoute.forSdk(33))
        assertEquals(listOf(MEDIA_OUTPUT_DIALOG, VOLUME_PANEL, BLUETOOTH_SETTINGS), AudioOutputRoute.forSdk(31))
        assertEquals(listOf(MEDIA_OUTPUT_PANEL, VOLUME_PANEL, BLUETOOTH_SETTINGS), AudioOutputRoute.forSdk(30))
        // Android 10 has the media-output panel, behind a system permission - so not offered.
        assertEquals(listOf(VOLUME_PANEL, BLUETOOTH_SETTINGS), AudioOutputRoute.forSdk(29))
        assertEquals(listOf(BLUETOOTH_SETTINGS), AudioOutputRoute.forSdk(23))
    }
}
