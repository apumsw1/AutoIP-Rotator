package com.example.autoiprotator

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build

object VpnGateOpenVpnAdapter {

    fun connect(
        context: Context,
        server: VpnGateServer
    ): Result<Unit> = runCatching {

        require(server.openVpnConfig.isNotBlank()) {
            "VPN Gate configuration is empty."
        }

        require(server.openVpnConfig.contains("client")) {
            "Invalid OpenVPN profile."
        }

        require(server.openVpnConfig.contains("remote ")) {
            "OpenVPN server address is missing."
        }

        check(VpnService.prepare(context) == null) {
            "VPN permission is required."
        }

        val intent = Intent(
            context,
            OpenVpnVpnService::class.java
        ).apply {
            action = OpenVpnVpnService.ACTION_CONNECT
            putExtra(
                OpenVpnVpnService.EXTRA_CONFIG,
                server.openVpnConfig
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun disconnect(context: Context) {
        val intent = Intent(
            context,
            OpenVpnVpnService::class.java
        ).apply {
            action = OpenVpnVpnService.ACTION_DISCONNECT
        }

        context.startService(intent)
    }
}