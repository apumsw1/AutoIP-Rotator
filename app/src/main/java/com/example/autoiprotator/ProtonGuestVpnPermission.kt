package com.example.autoiprotator

import android.content.Context
import android.content.Intent
import android.net.VpnService

object ProtonGuestVpnPermission {

    fun getPermissionIntent(context: Context): Intent? {
        return VpnService.prepare(context)
    }

    fun isPermissionGranted(context: Context): Boolean {
        return VpnService.prepare(context) == null
    }

    fun requestPermission(
        context: Context,
        onPermissionRequired: (Intent) -> Unit,
        onPermissionGranted: () -> Unit
    ) {
        val permissionIntent = getPermissionIntent(context)

        if (permissionIntent == null) {
            onPermissionGranted()
        } else {
            onPermissionRequired(permissionIntent)
        }
    }
}