package com.example.autoiprotator

object OpenVpn3Bridge {

    private const val LIBRARY_NAME = "openvpn3_jni"

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
        service: OpenVpnVpnService
    ): Int

    @JvmStatic
    external fun nativeStop(): Int

    @JvmStatic
    external fun nativeGetStatus(): String

    fun start(
        config: String,
        service: OpenVpnVpnService
    ): Result<Int> = runCatching {
        require(config.isNotBlank()) {
            "OpenVPN configuration is empty"
        }

        loadNativeLibrary()

        val result = nativeStart(config, service)

        check(result == 0) {
            "OpenVPN start failed ($result): ${nativeGetStatus()}"
        }

        result
    }

    fun stop(): Result<Int> = runCatching {
        loadNativeLibrary()

        val result = nativeStop()

        check(result == 0) {
            "OpenVPN stop failed ($result)"
        }

        result
    }

    fun status(): Result<String> = runCatching {
        loadNativeLibrary()
        nativeGetStatus()
    }
}