
package com.example.autoiprotator

/**
 * JNI entry point for the OpenVPN 3 native library.
 *
 * The matching native JNI implementation and Android TUN integration
 * must be built before start() can establish a VPN connection.
 */
object OpenVpn3Bridge {

    private const val LIBRARY_NAME = "openvpn3_jni"

    @Volatile
    private var libraryLoaded = false

    @Synchronized
    private fun loadNativeLibrary() {
        if (libraryLoaded) return

        System.loadLibrary(LIBRARY_NAME)
        libraryLoaded = true
    }

    /**
     * Starts OpenVPN using a complete .ovpn configuration.
     * Requires the native JNI library and TUN callbacks.
     */
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
                "A valid Android VPN tunnel file descriptor is required."
            }

            loadNativeLibrary()
            nativeStart(config, tunFd)
        }

    fun stop(): Result<Int> =
        runCatching {
            loadNativeLibrary()
            nativeStop()
        }

    fun status(): Result<String> =
        runCatching {
            loadNativeLibrary()
            nativeGetStatus()
        }
}
