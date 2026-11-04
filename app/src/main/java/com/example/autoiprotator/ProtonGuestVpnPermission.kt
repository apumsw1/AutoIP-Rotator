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
}