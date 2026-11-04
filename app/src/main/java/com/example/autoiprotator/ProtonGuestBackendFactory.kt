package com.example.autoiprotator

import android.content.Context

object ProtonGuestBackendFactory {

    fun create(
        context: Context,
        configSource: ProtonGuestConfigSource,
        onStateChanged: (ProtonGuestState) -> Unit
    ): ProtonGuestController {

        val tunnel = ProtonGuestWireGuardTunnel(
            context.applicationContext
        )

        val backend = ProtonGuestBackendAdapter(
            configSource = configSource,
            tunnel = tunnel
        )

        return ProtonGuestController(
            backend = backend,
            onStateChanged = onStateChanged
        )
    }
}