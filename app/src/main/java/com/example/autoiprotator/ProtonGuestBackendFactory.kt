package com.example.autoiprotator

import android.content.Context

object ProtonGuestBackendFactory {

    @Volatile
    private var controller: ProtonGuestController? = null

    @Synchronized
    fun create(
        context: Context,
        configSource: ProtonGuestConfigSource,
        onStateChanged: (ProtonGuestState) -> Unit
    ): ProtonGuestController {
        controller?.let { return it }

        val tunnel = ProtonGuestWireGuardTunnel(
            context.applicationContext
        )

        val backend = ProtonGuestBackendAdapter(
            configSource,
            tunnel
        )

        return ProtonGuestController(
            backend,
            onStateChanged
        ).also {
            controller = it
        }
    }

    @Synchronized
    fun release() {
        controller = null
    }
}