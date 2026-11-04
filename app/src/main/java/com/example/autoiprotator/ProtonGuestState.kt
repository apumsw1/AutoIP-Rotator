
package com.example.autoiprotator

/**
 * Represents the real connection lifecycle of Proton Guest VPN.
 * Connected must only be emitted after the VPN backend confirms
 * that the tunnel is active.
 */
sealed class ProtonGuestState {

    object Disconnected : ProtonGuestState()

    object WaitingForPermission : ProtonGuestState()

    object CreatingGuestSession : ProtonGuestState()

    object DiscoveringServers : ProtonGuestState()

    object Connecting : ProtonGuestState()

    data class Connected(
        val serverName: String,
        val connectedAtMillis: Long
    ) : ProtonGuestState()

    data class Error(
        val message: String
    ) : ProtonGuestState()
}
