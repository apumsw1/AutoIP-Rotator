
package com.example.autoiprotator

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VpnGateServerLoader {

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val mainHandler =
        Handler(Looper.getMainLooper())

    fun load(
        onResult: (Result<List<VpnGateServer>>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                VpnGateRepository.fetchServers()
            }

            mainHandler.post {
                onResult(result)
            }
        }
    }

    fun shutdown() {
        executor.shutdown()
    }
}
