package com.example.autoiprotator

/**
 * Selects Proton free servers for stable or rotating connections.
 */
class ProtonGuestServerSelector {

    private var lastServerId: String? = null

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

        val selected = if (!rotate || servers.size == 1) {
            servers.firstOrNull { it.id == lastServerId }
                ?: servers.first()
        } else {
            servers.firstOrNull { it.id != lastServerId }
                ?: servers.first()
        }

        lastServerId = selected.id
        return Result.success(selected)
    }

    @Synchronized
    fun reset() {
        lastServerId = null
    }
}