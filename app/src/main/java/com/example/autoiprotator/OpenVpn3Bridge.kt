
package com.example.autoiprotator

object OpenVpn3Bridge {

    private const val LIBRARY_NAME = "openvpn3_jni"

    private const val SUCCESS = 0

    @Volatile
    private var libraryLoaded = false

    @Synchronized
    private fun loadNativeLibrary() {
        if (!libraryLoaded) {
            System.loadLibrary(LIBRARY_NAME)
            libraryLoaded = true
        }
    }

    @JvmStatic
    external fun nativeStart(
        config: String,
        tunFd: Int
    ): Int

    @JvmStatic
    external fun nativeStop(): Int

    @JvmStatic
    external fun nativeGetStatus(): String

    fun start(config: String, tunFd: Int): Result<Int> =
        runCatching {
            require(config.isNotBlank()) {
                "OpenVPN configuration is empty."
            }

            require(tunFd >= 0) {
                "Invalid VPN tunnel descriptor."
            }

            loadNativeLibrary()

            val result = nativeStart(config, tunFd)

            check(result == SUCCESS) {
                val detail = runCatching {
                    nativeGetStatus()
                }.getOrDefault("Unknown native error")

                "OpenVPN failed ($result): $detail"
            }

            result
        }

    fun stop(): Result<Int> =
        runCatching {
            loadNativeLibrary()

            val result = nativeStop()

            check(result == SUCCESS) {
                "OpenVPN stop failed: $result"
            }

            result
        }

    fun status(): Result<String> =
        runCatching {
            loadNativeLibrary()
            nativeGetStatus()
        }
}
