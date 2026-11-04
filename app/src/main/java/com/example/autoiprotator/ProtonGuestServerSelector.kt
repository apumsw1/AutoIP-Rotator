package com.example.autoiprotator

object ProtonGuestServerSelector {

    fun select(
        servers: List<ProtonFreeServer>
    ): Result<ProtonFreeServer> {

        val validServers = servers
            .filter { server ->
                server.id.isNotBlank() &&
                server.name.isNotBlank() &&
                server.countryCode.matches(
                    Regex("^[A-Z]{2}$")
                )
            }
            .distinctBy { it.id }
            .sortedWith(
                compareBy<ProtonFreeServer> {
                    it.name.lowercase()
                }.thenBy {
                    it.id
                }
            )

        val selected = validServers.firstOrNull()
            ?: return Result.failure(
                IllegalStateException(
                    "No valid Proton Free servers are available."
                )
            )

        return Result.success(selected)
    }
}