package com.example.autoiprotator

import android.content.Context
import java.io.File

object OpenVpnProfileManager {

    private const val PROFILE_FILE = "active-vpn-profile.ovpn"

    fun save(context: Context, profile: String): Result<File> =
        runCatching {
            require(profile.isNotBlank()) {
                "VPN profile is empty"
            }

            require(
                profile.contains("<ca>") ||
                profile.contains("ca ") ||
                profile.contains("remote ")
            ) {
                "Invalid OpenVPN profile"
            }

            val file = File(context.filesDir, PROFILE_FILE)
            file.writeText(profile)
            file
        }

    fun load(context: Context): Result<String> =
        runCatching {
            val file = File(context.filesDir, PROFILE_FILE)

            check(file.exists()) {
                "No OpenVPN profile saved"
            }

            file.readText()
        }

    fun exists(context: Context): Boolean {
        return File(context.filesDir, PROFILE_FILE).exists()
    }

    fun delete(context: Context): Boolean {
        return File(context.filesDir, PROFILE_FILE).delete()
    }
}