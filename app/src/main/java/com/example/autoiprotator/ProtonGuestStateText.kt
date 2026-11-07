package com.example.autoiprotator

object ProtonGuestStateText {

    fun getStatus(state: ProtonGuestState): String {
        return when (state) {
            is ProtonGuestState.Disconnected ->
                "PUBLIC VPN DISCONNECTED"

            is ProtonGuestState.WaitingForPermission ->
                "WAITING FOR VPN PERMISSION"

            is ProtonGuestState.CreatingGuestSession ->
                "CREATING GUEST SESSION..."

            is ProtonGuestState.DiscoveringServers ->
                "SCANNING AVAILABLE SERVERS..."

            is ProtonGuestState.Connecting ->
                "ESTABLISHING SECURE TUNNEL..."

            is ProtonGuestState.Connected ->
                "CONNECTED: ${state.serverName}"

            is ProtonGuestState.Error ->
                "CONNECTION ERROR: ${state.message}"
        }
    }

    fun isConnected(state: ProtonGuestState): Boolean {
        return state is ProtonGuestState.Connected
    }

    fun canConnect(state: ProtonGuestState): Boolean {
        return state is ProtonGuestState.Disconnected ||
            state is ProtonGuestState.Error
    }

    fun canDisconnect(state: ProtonGuestState): Boolean {
        return state is ProtonGuestState.Connected ||
            state is ProtonGuestState.Connecting ||
            state is ProtonGuestState.DiscoveringServers ||
            state is ProtonGuestState.CreatingGuestSession
    }

    fun isBusy(state: ProtonGuestState): Boolean {
        return state is ProtonGuestState.WaitingForPermission ||
            state is ProtonGuestState.CreatingGuestSession ||
            state is ProtonGuestState.DiscoveringServers ||
            state is ProtonGuestState.Connecting
    }
}