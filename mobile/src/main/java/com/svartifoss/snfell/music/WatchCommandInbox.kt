package com.svartifoss.snfell.music

internal data class WatchCommand(
        val sourceNodeId: String,
        val requestId: Int,
        val path: String,
        val data: ByteArray
)

/**
 * Joins the manifest listener and the running service's listener without executing a tap twice.
 * All calls run on the main thread. Only service startup queues commands; a ready receiver runs
 * them immediately, in arrival order. Identical payloads with different request IDs are separate
 * taps, so transport actions are never conflated or retried.
 */
internal class WatchCommandInbox(private val historyCapacity: Int = 512) {
    private data class Identity(val sourceNodeId: String, val requestId: Int)

    private val recent = LinkedHashSet<Identity>()
    private val pending = java.util.ArrayDeque<WatchCommand>()
    private val pendingIdentities = HashSet<Identity>()
    private var owner: ((WatchCommand) -> Unit)? = null
    private var receiver: ((WatchCommand) -> Unit)? = null
    private var starting = false
    private var draining = false

    init {
        require(historyCapacity > 0)
    }

    fun receive(command: WatchCommand, canStartService: Boolean, startService: () -> Boolean) {
        val identity = command.identity()
        if (identity in recent || identity in pendingIdentities) return
        if (receiver == null && !canStartService) return

        pending.addLast(command)
        pendingIdentities.add(identity)
        if (receiver != null) {
            drain()
        } else if (!starting) {
            starting = true
            if (!startService()) {
                // A refused background start must not leave a skip/toggle waiting to execute
                // when the user happens to open the app much later.
                starting = false
                pending.clear()
                pendingIdentities.clear()
            }
        }
    }

    fun beginStartup(receiver: (WatchCommand) -> Unit) {
        owner = receiver
        this.receiver = null
    }

    fun attach(receiver: (WatchCommand) -> Unit) {
        if (owner != null && owner !== receiver) return
        owner = receiver
        this.receiver = receiver
        starting = false
        drain()
    }

    fun detach(receiver: (WatchCommand) -> Unit) {
        if (owner === receiver) {
            owner = null
            this.receiver = null
            starting = false
            // onCreate can stop the service before it becomes ready (for example when
            // notification access is absent). Do not strand that aborted startup's commands.
            pending.clear()
            pendingIdentities.clear()
        }
    }

    private fun drain() {
        if (draining) return
        draining = true
        try {
            while (pending.isNotEmpty()) {
                val receiver = receiver ?: break
                val command = pending.removeFirst()
                val identity = command.identity()
                pendingIdentities.remove(identity)
                recent.add(identity)
                if (recent.size > historyCapacity) {
                    val oldest = recent.iterator()
                    oldest.next()
                    oldest.remove()
                }
                receiver(command)
            }
        } finally {
            draining = false
        }
    }

    private fun WatchCommand.identity() = Identity(sourceNodeId, requestId)
}
