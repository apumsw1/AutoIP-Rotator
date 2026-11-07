package com.example.autoiprotator

import java.util.Collections

class ProtonGuestServerSelector {

    private var lastServerId: String? = null
    private var rotationQueue: MutableList<ProtonFreeServer> = mutableListOf()

    @Synchronized
    fun selectServer(
        servers: List<ProtonFreeServer>,
        rotate: Boolean
    ): Result<ProtonFreeServer> {

        if (servers.isEmpty()) {
            return Result.failure(
                IllegalStateException("No Proton free servers available")
            )
        }

        val availableServers = servers.distinctBy { it.id }

        if (!rotate) {
            val stableServer = availableServers.firstOrNull {
                it.id == lastServerId
            } ?: availableServers.first()

            lastServerId = stableServer.id
            return Result.success(stableServer)
        }

        if (rotationQueue.none { it.id != lastServerId } ||
            rotationQueue.none { queued ->
                availableServers.any { it.id == queued.id }
            }
        ) {
            rotationQueue = availableServers
                .filter { it.id != lastServerId }
                .shuffled()
                .toMutableList()
        }

        val nextServer = rotationQueue.firstOrNull {
            it.id != lastServerId &&
                availableServers.any { server -> server.id == it.id }
        } ?: availableServers.firstOrNull {
            it.id != lastServerId
        } ?: availableServers.first()

        rotationQueue.removeAll { it.id == nextServer.id }
        lastServerId = nextServer.id

        return Result.success(nextServer)
    }

    @Synchronized
    fun reset() {
        lastServerId = null
        rotationQueue.clear()
    }
}