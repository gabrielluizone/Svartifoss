package com.svartifoss.snfell.view.watchface.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityThemeAccountStateTest {

    @Test
    fun `no Firebase user is signed out`() {
        assertEquals(
                CommunityThemeAccountState.SIGNED_OUT,
                CommunityThemeAccountStateResolver.resolve(null, emptyList()))
    }

    @Test
    fun `anonymous Firebase user is limited to likes`() {
        assertEquals(
                CommunityThemeAccountState.ANONYMOUS_LIKES,
                CommunityThemeAccountStateResolver.resolve(true, listOf("firebase")))
    }

    @Test
    fun `linked Google provider wins over anonymous sign-in event`() {
        // Firebase token metadata can keep the original anonymous sign-in event after link.
        assertEquals(
                CommunityThemeAccountState.GOOGLE,
                CommunityThemeAccountStateResolver.resolve(true, listOf("firebase", "google.com")))
    }

    @Test
    fun `persisted non anonymous session is recognized as Google connection`() {
        assertEquals(
                CommunityThemeAccountState.GOOGLE,
                CommunityThemeAccountStateResolver.resolve(false, listOf("firebase")))
    }
}

class CommunityThemeAccountActionsTest {

    @Test
    fun `a signed out account is only offered a connection`() {
        assertEquals(
                setOf(CommunityThemeAccountAction.CONNECT),
                CommunityThemeAccountActions.resolve(
                        CommunityThemeAccountState.SIGNED_OUT, deletionRequested = false))
    }

    @Test
    fun `a connected account can disconnect locally or be deleted for good`() {
        assertEquals(
                setOf(CommunityThemeAccountAction.DISCONNECT, CommunityThemeAccountAction.DELETE),
                CommunityThemeAccountActions.resolve(
                        CommunityThemeAccountState.GOOGLE, deletionRequested = false))
    }

    @Test
    fun `an anonymous likes account can still erase itself`() {
        // Its only trace is a set of private votes, which is exactly the data worth erasing.
        assertEquals(
                setOf(
                        CommunityThemeAccountAction.CONNECT,
                        CommunityThemeAccountAction.DISCONNECT,
                        CommunityThemeAccountAction.DELETE),
                CommunityThemeAccountActions.resolve(
                        CommunityThemeAccountState.ANONYMOUS_LIKES, deletionRequested = false))
    }

    @Test
    fun `a requested deletion stops offering anything that implies it can be taken back`() {
        // Firestore accepts the request as a create and nothing else, so the screen must not
        // present connecting or deleting again as a way out of one already in flight.
        CommunityThemeAccountState.entries.forEach { state ->
            assertEquals(
                    "state $state",
                    setOf(CommunityThemeAccountAction.DISCONNECT),
                    CommunityThemeAccountActions.resolve(state, deletionRequested = true))
        }
    }

    @Test
    fun `only an account that can own a submission is asked about its themes`() {
        assertTrue(CommunityThemeAccountActions.offersThemeChoice(CommunityThemeAccountState.GOOGLE))
        assertFalse(CommunityThemeAccountActions.offersThemeChoice(
                CommunityThemeAccountState.ANONYMOUS_LIKES))
        assertFalse(CommunityThemeAccountActions.offersThemeChoice(
                CommunityThemeAccountState.SIGNED_OUT))
    }

    @Test
    fun `an unprompted deletion keeps published themes rather than withdrawing them`() {
        // The conservative half of a decision that cannot apply: an account that unexpectedly does
        // own something public has it left alone instead of silently removed.
        assertEquals(
                CommunityThemeDeletionChoice.KEEP_THEMES,
                CommunityThemeAccountActions.choiceWithoutPrompt())
    }

    @Test
    fun `theme dispositions round-trip through the wire values Firestore rules accept`() {
        assertEquals("keep", CommunityThemeDeletionChoice.KEEP_THEMES.wireValue)
        assertEquals("delete", CommunityThemeDeletionChoice.DELETE_THEMES.wireValue)
        CommunityThemeDeletionChoice.entries.forEach { choice ->
            assertEquals(choice, CommunityThemeDeletionChoice.fromWire(choice.wireValue))
        }
        assertNull(CommunityThemeDeletionChoice.fromWire("everything"))
        assertNull(CommunityThemeDeletionChoice.fromWire(null))
    }
}

class CommunityThemeSubmissionStatusTest {

    @Test
    fun `every status the rules accept maps to a label the screen can show`() {
        listOf(
                "pending" to CommunityThemeSubmissionStatus.PENDING,
                "approved" to CommunityThemeSubmissionStatus.APPROVED,
                "published" to CommunityThemeSubmissionStatus.PUBLISHED,
                "rejected" to CommunityThemeSubmissionStatus.REJECTED,
                "withdrawn" to CommunityThemeSubmissionStatus.WITHDRAWN
        ).forEach { (wire, expected) ->
            assertEquals(wire, expected, CommunityThemeSubmissionStatus.fromWire(wire))
        }
    }

    @Test
    fun `an unrecognized status is shown rather than dropped`() {
        // A submission this build cannot label is still one the author sent, so the mapping is
        // total: a newer status must not make their theme disappear from their own queue.
        assertEquals(
                CommunityThemeSubmissionStatus.UNKNOWN,
                CommunityThemeSubmissionStatus.fromWire("escalated"))
        assertEquals(
                CommunityThemeSubmissionStatus.UNKNOWN,
                CommunityThemeSubmissionStatus.fromWire(null))
        // The sentinel's own empty wire value must never match anything.
        assertEquals(
                CommunityThemeSubmissionStatus.UNKNOWN,
                CommunityThemeSubmissionStatus.fromWire(""))
    }

    @Test
    fun `newest submissions come first and an unreadable timestamp sorts last`() {
        val newest = record("newest", 3_000L)
        val older = record("older", 1_000L)
        val undated = record("undated", null)

        assertEquals(
                listOf(newest, older, undated),
                CommunityThemeSubmissionOrder.sorted(listOf(older, undated, newest)))
    }

    @Test
    fun `a like count is attached only where the catalogue actually has one`() {
        // A submission still in review has no public entry. Showing it a zero would claim nobody
        // liked it, rather than that there was nothing to like yet.
        val published = record("published", 2_000L)
        val pending = record("pending", 1_000L)

        val withLikes = CommunityThemeSubmissionOrder.withLikes(
                listOf(published, pending),
                mapOf("published" to 7))

        assertEquals(7, withLikes.first { it.id == "published" }.likes)
        assertNull(withLikes.first { it.id == "pending" }.likes)
    }

    private fun record(id: String, createdAtMillis: Long?) = CommunityThemeSubmissionRecord(
            id = id,
            name = "Theme $id",
            author = "Author",
            baseFace = "poster",
            status = CommunityThemeSubmissionStatus.PENDING,
            createdAtMillis = createdAtMillis)
}
