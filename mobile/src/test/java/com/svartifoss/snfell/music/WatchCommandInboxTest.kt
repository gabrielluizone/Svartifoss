package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchCommandInboxTest {
    @Test
    fun coldStartRetainsFirstCommandAndOrderedTapsUntilSessionIsReady() {
        val inbox = WatchCommandInbox()
        val received = mutableListOf<Int>()
        var starts = 0
        val startService = { starts++; true }

        inbox.receive(command(1), true, startService)
        inbox.receive(command(1), true, startService) // The second listener sees the same event.
        inbox.receive(command(2), true, startService)
        inbox.receive(command(3), true, startService)
        assertEquals(1, starts)
        assertTrue(received.isEmpty())

        inbox.attach { received.add(it.requestId) }
        assertEquals(listOf(1, 2, 3), received)
        inbox.receive(command(1), true, startService) // Delayed duplicate after startup.
        assertEquals(listOf(1, 2, 3), received)
    }

    @Test
    fun readyServiceExecutesImmediatelyWithoutAnotherForegroundStart() {
        val inbox = WatchCommandInbox()
        val received = mutableListOf<Int>()
        inbox.attach { received.add(it.requestId) }

        inbox.receive(command(7), true) { error("A running service must not be restarted") }
        assertEquals(listOf(7), received)
        inbox.receive(command(7), true) { error("Duplicate must not restart the service") }
        inbox.receive(command(8), true) { error("Repeated tap must not restart the service") }
        assertEquals(listOf(7, 8), received)
    }

    @Test
    fun dedupUsesSenderAndRequestIdentityWithoutTreatingIdsAsStateVersions() {
        val inbox = WatchCommandInbox()
        val received = mutableListOf<Pair<String, Int>>()
        inbox.attach { received.add(it.sourceNodeId to it.requestId) }

        listOf(command(10), command(9), command(10, "other-watch"), command(9)).forEach {
            inbox.receive(it, true) { error("Already running") }
        }
        assertEquals(listOf("watch" to 10, "watch" to 9, "other-watch" to 10), received)
    }

    @Test
    fun refusedBackgroundStartDoesNotReplayOldTapsOnLaterLaunch() {
        val inbox = WatchCommandInbox()
        val received = mutableListOf<Int>()
        inbox.receive(command(1), true) { false }
        inbox.receive(command(2), true) { true }
        inbox.attach { received.add(it.requestId) }
        assertEquals(listOf(2), received)
    }

    @Test
    fun closingAndAcknowledgementMessagesDoNotStartAStoppedService() {
        val inbox = WatchCommandInbox()
        val received = mutableListOf<Int>()
        inbox.receive(command(1), false) { error("Must stay stopped") }
        inbox.attach { received.add(it.requestId) }
        assertTrue(received.isEmpty())

        inbox.receive(command(2), false) { error("Already running") }
        assertEquals(listOf(2), received)
    }

    @Test
    fun dedupSurvivesServiceRecreationAndOldTeardownCannotDetachNewService() {
        val inbox = WatchCommandInbox()
        val received = mutableListOf<Int>()
        val oldReceiver: (WatchCommand) -> Unit = { received.add(it.requestId) }
        val newReceiver: (WatchCommand) -> Unit = { received.add(it.requestId) }
        inbox.attach(oldReceiver)
        inbox.receive(command(1), true) { error("Already running") }
        inbox.detach(oldReceiver)
        inbox.receive(command(2), true) { true }
        inbox.attach(newReceiver)
        inbox.detach(oldReceiver)

        inbox.receive(command(1), true) { error("Duplicate from the old listener") }
        inbox.receive(command(3), true) { error("Old teardown must not detach the new service") }
        assertEquals(listOf(1, 2, 3), received)
    }

    @Test
    fun startupAbortedBeforeSessionIsReadyClearsPendingCommandsAndAllowsANewStart() {
        val inbox = WatchCommandInbox()
        val received = mutableListOf<Int>()
        val oldReceiver: (WatchCommand) -> Unit = { received.add(it.requestId) }
        val newReceiver: (WatchCommand) -> Unit = { received.add(it.requestId) }
        var starts = 0
        inbox.receive(command(1), true) { starts++; true }
        inbox.beginStartup(oldReceiver)
        inbox.receive(command(2), true) { error("Startup already requested") }
        inbox.detach(oldReceiver)

        inbox.receive(command(3), true) { starts++; true }
        inbox.beginStartup(newReceiver)
        inbox.detach(oldReceiver)
        inbox.attach(newReceiver)
        assertEquals(2, starts)
        assertEquals(listOf(3), received)
    }

    @Test
    fun deliveryHistoryHasABoundedSize() {
        val inbox = WatchCommandInbox(historyCapacity = 2)
        val received = mutableListOf<Int>()
        inbox.attach { received.add(it.requestId) }
        listOf(1, 2, 3, 2, 3, 1).forEach {
            inbox.receive(command(it), true) { error("Already running") }
        }
        assertEquals(listOf(1, 2, 3, 1), received)
    }

    @Test
    fun reentrantDeliveryCannotOvertakeCommandsAlreadyWaitingForStartup() {
        val inbox = WatchCommandInbox()
        val received = mutableListOf<Int>()
        inbox.receive(command(1), true) { true }
        inbox.receive(command(2), true) { error("Startup already requested") }
        inbox.attach {
            received.add(it.requestId)
            if (it.requestId == 1) inbox.receive(command(3), true) { error("Already running") }
        }
        assertEquals(listOf(1, 2, 3), received)
    }

    private fun command(id: Int, node: String = "watch") = WatchCommand(
            sourceNodeId = node,
            requestId = id,
            path = "/Messages/SkipNext",
            data = ByteArray(0))
}
