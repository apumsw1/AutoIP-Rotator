
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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.example.autoiprotator.OpenVpnVpnService
import com.example.autoiprotator.RiseupServer
import com.example.autoiprotator.RiseupVpnClient

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
    private lateinit var serverSpinner: Spinner
    private lateinit var refreshButton: Button
    private lateinit var connectButton: Button

    private val servers = mutableListOf<RiseupServer>()

    private var selectedServer: RiseupServer? = null
    private var connected = false
    private var connecting = false
    private var disconnecting = false
    private var discovering = false
    private var receiverRegistered = false
    private var permissionPending = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context?,
            intent: Intent?
        ) {
            if (intent?.action != OpenVpnVpnService.ACTION_STATUS) return

            val isConnected = intent.getBooleanExtra(
                OpenVpnVpnService.EXTRA_CONNECTED,
                false
            )

            val message = intent.getStringExtra(
                OpenVpnVpnService.EXTRA_MESSAGE
            ) ?: "VPN status updated"

            activity.runOnUiThread {
                when {
                    isConnected -> {
                        connected = true
                        connecting = false
                        disconnecting = false

                        statusText.text = "● RISEUP VPN CONNECTED"
                        statusText.setTextColor(green)
                        detailText.text = message
                    }

                    message.contains("disconnect", true) ||
                    message.contains("teardown", true) -> {
                        connected = false
                        connecting = false
                        disconnecting = false

                        statusText.text = "● DISCONNECTED"
                        statusText.setTextColor(yellow)
                        detailText.text = message
                    }

                    message.contains("failed", true) ||
                    message.contains("error", true) ||
                    message.contains("exception", true) -> {
                        connected = false
                        connecting = false
                        disconnecting = false

                        statusText.text = "✕ CONNECTION FAILED"
                        statusText.setTextColor(red)
                        detailText.text = message
                    }

                    connecting -> {
                        statusText.text = "● CONNECTING..."
                        statusText.setTextColor(cyan)
                        detailText.text = message
                    }

                    else -> {
                        detailText.text = message
                    }
                }

                updateButtons()
            }
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * context.resources.displayMetrics.density
        ).toInt()
    }

    private fun background(
        color: Int,
        stroke: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), stroke)
        }
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            typeface = if (bold) {
                Typeface.DEFAULT_BOLD
            } else {
                Typeface.DEFAULT
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
    }

    private fun makeButton(
        value: String,
        color: Int,
        stroke: Int
    ): Button {
        return Button(context).apply {
            text = value
            textSize = 14f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            background = background(color, stroke)
            stateListAnimator = null
        }
    }

    fun createView(): LinearLayout {

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(15), dp(18), dp(15), dp(18))

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
            label("◈ RISEUP PUBLIC VPN ◈", 20f, cyan, true),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        )

        panel.addView(
            label(
                "FREE COMMUNITY VPN SERVERS",
                11f,
                muted,
                true
            )
        )

        statusText = label(
            "● DISCONNECTED",
            17f,
            yellow,
            true
        )

        panel.addView(
            statusText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        detailText = label(
            "Refresh to discover available servers.",
            12f,
            muted
        )

        panel.addView(
            detailText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(55)
            )
        )

        serverSpinner = Spinner(context)

        serverSpinner.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            mutableListOf("No servers loaded")
        )

        panel.addView(
            serverSpinner,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(55)
            )
        )

        refreshButton = makeButton(
            "↻ REFRESH FREE SERVERS",
            Color.rgb(0, 65, 105),
            cyan
        )

        panel.addView(
            refreshButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(8)
            }
        )

        connectButton = makeButton(
            "⚡ CONNECT RISEUP VPN",
            Color.rgb(0, 75, 40),
            green
        )

        panel.addView(
            connectButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                topMargin = dp(12)
            }
        )

        refreshButton.setOnClickListener {
            discoverServers()
        }

        connectButton.setOnClickListener {
            when {
                connected -> disconnect()
                connecting || disconnecting || discovering -> return@setOnClickListener
                else -> requestPermissionAndConnect()
            }
        }

        serverSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    selectedServer = servers.getOrNull(position)
                    updateButtons()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                    selectedServer = null
                    updateButtons()
                }
            }

        registerStatusReceiver()
        updateButtons()

        return panel
    }

    private fun registerStatusReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter(
            OpenVpnVpnService.ACTION_STATUS
        )

        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                statusReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(statusReceiver, filter)
        }

        receiverRegistered = true
    }

    private fun discoverServers() {
        if (discovering || connecting || disconnecting) return

        discovering = true
        detailText.text = "CONTACTING RISEUP API..."
        statusText.text = "● SEARCHING SERVERS"
        statusText.setTextColor(cyan)
        updateButtons()

        RiseupVpnClient.discoverServers { result ->
            activity.runOnUiThread {
                discovering = false

                result.onSuccess { found ->
                    servers.clear()
                    servers.addAll(found)

                    val names = found.map {
                        "${it.location.uppercase()} | ${it.name} | ${it.protocol.uppercase()}:${it.port}"
                    }

                    serverSpinner.adapter = ArrayAdapter(
                        context,
                        android.R.layout.simple_spinner_dropdown_item,
                        names
                    )

                    selectedServer = found.firstOrNull()

                    statusText.text = "● ${found.size} SERVERS FOUND"
                    statusText.setTextColor(green)
                    detailText.text = "Select a server and connect."

                    Toast.makeText(
                        context,
                        "${found.size} Riseup servers found.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                result.onFailure { error ->
                    statusText.text = "✕ SERVER DISCOVERY FAILED"
                    statusText.setTextColor(red)
                    detailText.text =
                        error.message ?: "Unable to load servers."
                }

                updateButtons()
            }
        }
    }

    private fun requestPermissionAndConnect() {
        val server = selectedServer

        if (server == null) {
            Toast.makeText(
                context,
                "Refresh and select a server first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val permissionIntent = VpnService.prepare(context)

        if (permissionIntent != null) {
            permissionPending = true

            activity.startActivityForResult(
                permissionIntent,
                VPN_PERMISSION_REQUEST
            )
        } else {
            connectSelectedServer()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (!permissionPending) return

        permissionPending = false

        if (granted) {
            connectSelectedServer()
        } else {
            showError("VPN permission denied.")
        }
    }

    private fun connectSelectedServer() {
        if (connecting || connected) return

        val server = selectedServer

        if (server == null) {
            showError("No server selected.")
            return
        }

        connecting = true
        disconnecting = false

        statusText.text = "● CONNECTING..."
        statusText.setTextColor(cyan)
        detailText.text = "REQUESTING RISEUP CERTIFICATE..."
        updateButtons()

        RiseupVpnClient.connect(
            context.applicationContext,
            server
        ) { result ->
            activity.runOnUiThread {
                result.onSuccess {
                    statusText.text = "● STARTING VPN TUNNEL"
                    statusText.setTextColor(yellow)
                    detailText.text =
                        "Waiting for OpenVPN tunnel confirmation..."
                }

                result.onFailure { error ->
                    connecting = false
                    showError(
                        error.message ?: "Connection failed."
                    )
                }

                updateButtons()
            }
        }
    }

    private fun disconnect() {
        if (disconnecting) return

        disconnecting = true
        connecting = false

        statusText.text = "● DISCONNECTING..."
        statusText.setTextColor(yellow)
        detailText.text = "Closing VPN tunnel..."
        updateButtons()

        try {
            RiseupVpnClient.disconnect(context)
        } catch (error: Exception) {
            disconnecting = false
            showError(
                error.message ?: "Disconnect failed."
            )
        }
    }

    private fun showError(message: String) {
        connecting = false
        disconnecting = false
        connected = false

        statusText.text = "✕ CONNECTION FAILED"
        statusText.setTextColor(red)
        detailText.text = message

        Toast.makeText(
            context,
            message,
            Toast.LENGTH_LONG
        ).show()

        updateButtons()
    }

    private fun updateButtons() {
        if (!::connectButton.isInitialized) return

        connectButton.text = when {
            connected -> "DISCONNECT RISEUP VPN"
            connecting -> "CONNECTING..."
            disconnecting -> "DISCONNECTING..."
            else -> "⚡ CONNECT RISEUP VPN"
        }

        connectButton.isEnabled =
            connected ||
            (
                !connecting &&
                !disconnecting &&
                !discovering &&
                selectedServer != null
            )

        refreshButton.isEnabled =
            !discovering && !connecting && !disconnecting && !connected

        serverSpinner.isEnabled =
            !connecting && !disconnecting && !connected

        connectButton.background = background(
            if (connected || disconnecting) {
                Color.rgb(75, 8, 18)
            } else {
                Color.rgb(0, 75, 40)
            },
            if (connected || disconnecting) red else green
        )
    }

    fun shutdown() {
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(statusReceiver)
            } catch (_: Exception) {
            }

            receiverRegistered = false
        }
    }

    companion object {
        const val VPN_PERMISSION_REQUEST = 4193
    }
}
