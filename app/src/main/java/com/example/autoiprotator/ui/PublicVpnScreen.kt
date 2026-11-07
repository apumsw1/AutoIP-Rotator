package com.example.autoiprotator.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.autoiprotator.OpenVpnVpnService
import com.example.autoiprotator.VpnGateOpenVpnAdapter
import com.example.autoiprotator.VpnGateServer
import com.example.autoiprotator.VpnGateServerLoader

class PublicVpnScreen(private val context: Context) {

    private val activity = context as Activity
    private val cyan = Color.rgb(0, 235, 255)
    private val green = Color.rgb(0, 255, 130)
    private val red = Color.rgb(255, 55, 65)
    private val white = Color.rgb(240, 250, 255)
    private val muted = Color.rgb(145, 180, 205)
    private val yellow = Color.rgb(255, 190, 50)

    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var toggleButton: Button

    private val serverLoader = VpnGateServerLoader()
    private var connected = false
    private var busy = false
    private var permissionPending = false
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != OpenVpnVpnService.ACTION_STATUS) return

            val isConnected = intent.getBooleanExtra(
                OpenVpnVpnService.EXTRA_CONNECTED,
                false
            )
            val message = intent.getStringExtra(
                OpenVpnVpnService.EXTRA_MESSAGE
            ) ?: "VPN status updated"

            connected = isConnected
            busy = false

            statusText.text = if (isConnected) {
                "● VPN CONNECTED"
            } else {
                "● DISCONNECTED"
            }

            statusText.setTextColor(if (isConnected) green else yellow)
            detailText.text = message
            detailText.setTextColor(if (isConnected) green else muted)
            updateButton()
        }
    }

    var onConnect: (() -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null

    companion object {
        const val VPN_PERMISSION_REQUEST = 4193
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun makeBackground(color: Int, stroke: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun makeLabel(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private fun updateButton() {
        if (!::toggleButton.isInitialized) return

        toggleButton.text = when {
            busy && connected -> "DISCONNECTING..."
            busy -> "CONNECTING..."
            connected -> "DISCONNECT VPN"
            else -> "⚡ CONNECT PUBLIC VPN"
        }

        toggleButton.background = when {
            connected -> makeBackground(Color.rgb(75, 8, 18), red)
            busy -> makeBackground(Color.rgb(55, 45, 5), yellow)
            else -> makeBackground(Color.rgb(0, 75, 40), green)
        }

        toggleButton.isEnabled = !busy
    }

    fun createView(): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(20), dp(16), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(7, 29, 51),
                    Color.rgb(2, 12, 29),
                    Color.rgb(3, 18, 35)
                )
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), cyan)
            }
        }

        panel.addView(
            makeLabel("◈ PUBLIC VPN ◈", 20f, cyan, true),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        panel.addView(
            makeLabel("FREE COMMUNITY VPN CONNECTION", 11f, muted, true),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(35)
            )
        )

        statusText = makeLabel("● DISCONNECTED", 18f, muted, true)
        panel.addView(
            statusText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(55)
            )
        )

        detailText = makeLabel("READY TO CONNECT", 12f, muted)
        panel.addView(
            detailText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(38)
            )
        )

        toggleButton = Button(context).apply {
            textSize = 16f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            stateListAnimator = null
            elevation = dp(5).toFloat()
        }

        panel.addView(
            toggleButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(60)
            ).apply {
                topMargin = dp(12)
            }
        )

        toggleButton.setOnClickListener {
            if (busy) return@setOnClickListener

            if (connected) {
                disconnectPublicVpn()
            } else {
                requestPublicVpnPermission()
            }
        }

        registerStatusReceiver()
        setConnected(false)
        return panel
    }

    private fun registerStatusReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter(OpenVpnVpnService.ACTION_STATUS)

        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }

        receiverRegistered = true
    }

    private fun requestPublicVpnPermission() {
        val permissionIntent = VpnService.prepare(context)

        if (permissionIntent != null) {
            permissionPending = true
            setConnecting()
            activity.startActivityForResult(
                permissionIntent,
                VPN_PERMISSION_REQUEST
            )
        } else {
            loadAndConnectServer()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (!permissionPending) return
        permissionPending = false

        if (granted) {
            loadAndConnectServer()
        } else {
            setConnectionFailed("VPN permission denied")
        }
    }

    private fun loadAndConnectServer() {
        setConnecting()
        detailText.text = "SEARCHING FREE VPN GATE SERVERS..."

        serverLoader.load { result ->
            if (activity.isFinishing || activity.isDestroyed) return@load

            result.onSuccess { servers ->
                val server = servers.firstOrNull()

                if (server == null) {
                    setConnectionFailed("No VPN Gate servers found")
                    return@onSuccess
                }

                detailText.text = "CONNECTING: ${server.country} / ${server.ip}"

                val connectResult = VpnGateOpenVpnAdapter.connect(
                    context,
                    server
                )

                connectResult.onFailure { error ->
                    setConnectionFailed(
                        error.message ?: "VPN connection could not start"
                    )
                }
            }

            result.onFailure { error ->
                setConnectionFailed(
                    error.message ?: "VPN Gate server search failed"
                )
            }
        }
    }

    private fun disconnectPublicVpn() {
        setDisconnecting()

        try {
            VpnGateOpenVpnAdapter.disconnect(context)
        } catch (error: Exception) {
            setConnectionFailed(
                error.message ?: "Disconnect request failed"
            )
        }
    }

    fun setConnected(isConnected: Boolean) {
        connected = isConnected
        busy = false

        statusText.text =
            if (connected) "● VPN CONNECTED" else "● DISCONNECTED"
        statusText.setTextColor(if (connected) green else muted)

        detailText.text =
            if (connected) "SECURE TUNNEL ACTIVE"
            else "READY TO CONNECT"
        detailText.setTextColor(if (connected) green else muted)

        updateButton()
    }

    fun setConnecting() {
        connected = false
        busy = true
        statusText.text = "CONNECTING..."
        statusText.setTextColor(cyan)
        detailText.text = "ESTABLISHING VPN TUNNEL"
        detailText.setTextColor(cyan)
        updateButton()
    }

    fun setDisconnecting() {
        busy = true
        statusText.text = "DISCONNECTING..."
        statusText.setTextColor(yellow)
        detailText.text = "CLOSING VPN TUNNEL"
        detailText.setTextColor(yellow)
        updateButton()
    }

    fun setConnectionFailed(message: String = "VPN SERVICE NOT AVAILABLE") {
        connected = false
        busy = false
        statusText.text = "✕ CONNECTION FAILED"
        statusText.setTextColor(red)
        detailText.text = message
        detailText.setTextColor(yellow)
        updateButton()

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun setStatus(message: String, isConnected: Boolean) {
        connected = isConnected
        busy = false
        statusText.text = message
        statusText.setTextColor(if (connected) green else yellow)
        detailText.text =
            if (connected) "SECURE TUNNEL ACTIVE" else "CHECK VPN SERVICE"
        updateButton()
    }

    fun shutdown() {
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
        serverLoader.shutdown()
    }
}