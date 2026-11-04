package com.example.autoiprotator

object ProtonGuestStateText {

    fun getStatus(state: ProtonGuestState): String {
        return when (state) {
            is ProtonGuestState.Disconnected ->
                "Disconnected"

            is ProtonGuestState.WaitingForPermission ->
                "Waiting for VPN permission"

            is ProtonGuestState.CreatingGuestSession ->
                "Creating guest session..."

            is ProtonGuestState.DiscoveringServers ->
                "Finding available servers..."

            is ProtonGuestState.Connecting ->
                "Connecting to VPN..."

            is ProtonGuestState.Connected ->
                "Connected to ${state.serverName}"

            is ProtonGuestState.Error ->
                "Connection failed: ${state.message}"
        }
    }

    fun isConnected(state: ProtonGuestState): Boolean {
        return state is ProtonGuestState.Connected
    }

    fun canConnect(state: ProtonGuestState): Boolean {
        return state is ProtonGuestState.Disconnected ||
            state is ProtonGuestState.Error
    }
}