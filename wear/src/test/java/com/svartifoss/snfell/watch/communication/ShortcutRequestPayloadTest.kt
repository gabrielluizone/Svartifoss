package com.svartifoss.snfell.watch.communication

import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.proto.CustomListItemAction
import com.svartifoss.snfell.proto.ShortcutPlayMode
import com.svartifoss.snfell.proto.StreamingShortcutVerdict
import org.junit.Assert.*
import org.junit.Test

class ShortcutRequestPayloadTest {
    @Test fun ordinarySelectionsKeepTheirOriginalPayload() {
        val original = CustomListItemAction.newBuilder().setListId(CustomLists.PLAYLIST)
                .setEntryId("track").build().toByteArray()
        assertArrayEquals(original, customListItemPayload(CustomLists.PLAYLIST, "track"))
    }

    @Test fun shortcutRequestCarriesItsIdentityAndPlayModeTogether() {
        val request = CustomListItemAction.parseFrom(customListItemPayload(
                CustomLists.PLAYLIST_SHORTCUTS, "package|https://example.com/playlist",
                ShortcutPlayMode.SHUFFLE, "request-2", "Artist"))
        assertEquals("request-2", request.requestId)
        assertEquals(ShortcutPlayMode.SHUFFLE, request.playMode)
        assertEquals("Artist", request.searchQuery)
    }

    @Test fun evenASilentSuccessIdentifiesItsRequest() {
        val verdict = StreamingShortcutVerdict.parseFrom(StreamingShortcutVerdict.newBuilder()
                .setRequestId("request-2").build().toByteArray())
        assertEquals("request-2", verdict.requestId)
        assertFalse(verdict.hasOpenUri())
    }
}
