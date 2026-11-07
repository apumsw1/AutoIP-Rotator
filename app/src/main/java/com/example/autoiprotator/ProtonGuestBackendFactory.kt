
package com.example.autoiprotator

import android.content.Context

object ProtonGuestBackendFactory {

    fun create(context: Context): ProtonGuestBackend {
        return ProtonGuestWireGuardBackend(
            context = context.applicationContext,
            configSource = ProtonGuestUnavailableConfigSource()
        )
    }
}
