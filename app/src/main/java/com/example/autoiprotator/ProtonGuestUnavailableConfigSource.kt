
package com.example.autoiprotator

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class ProtonGuestUnavailableConfigSource : ProtonGuestConfigSource {

    private val executor = Executors.newSingleThreadExecutor()

    override fun createGuestSession(
        callback: (Result<ProtonGuestSession>) -> Unit
    ) {
        executor.execute {
            callback(
                Result.failure(
                    UnsupportedOperationException(
                        "Guest authentication requires Proton's supported challenge flow."
                    )
                )
            )
        }
    }

    override fun discoverFreeServers(
        session: ProtonGuestSession,
        callback: (Result<List<ProtonFreeServer>>) -> Unit
    ) {
        callback(Result.failure(IllegalStateException("No guest session.")))
    }

    override fun getWireGuardConfig(
        session: ProtonGuestSession,
        server: ProtonFreeServer,
        callback: (Result<ProtonGuestConnectionConfig>) -> Unit
    ) {
        callback(Result.failure(IllegalStateException("No guest session.")))
    }
}
