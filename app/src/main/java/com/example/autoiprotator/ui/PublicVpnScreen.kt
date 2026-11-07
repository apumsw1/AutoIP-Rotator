package com.example.autoiprotator.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.autoiprotator.ProtonGuestBackend
import com.example.autoiprotator.ProtonGuestBackendFactory
import com.example.autoiprotator.ProtonGuestController
import com.example.autoiprotator.ProtonGuestState

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

    private var connected = false
    private var busy = false

    private val backend: ProtonGuestBackend by lazy {
        ProtonGuestBackendFactory.create(context.applicationContext)
    }

    private val controller: ProtonGuestController by lazy {
        ProtonGuestController(backend) { state ->
            activity.runOnUiThread {
                updateState(state)
            }
        }
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
            connected -> "DISCONNECT PROTON VPN"
            else -> "⚡ CONNECT PROTON GUEST"
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
            makeLabel("◈ PROTON GUEST VPN ◈", 20f, cyan, true),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        panel.addView(
            makeLabel(
                "FREE PROTON VPN CONNECTION",
                11f,
                muted,
                true
            )
        )

        statusText = makeLabel(
            "● DISCONNECTED",
            18f,
            muted,
            true
        )

        panel.addView(
            statusText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(55)
            )
        )

        detailText = makeLabel(
            "PROTON GUEST READY",
            12f,
            muted
        )

        panel.addView(
            detailText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
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
                setDisconnecting()
                controller.disconnect()
            } else {
                startGuestConnection()
            }
        }

        updateButton()
        return panel
    }

    private fun startGuestConnection() {
        val permissionIntent =
            android.net.VpnService.prepare(context)

        if (permissionIntent != null) {
            Toast.makeText(
                context,
                "Allow VPN permission first, then connect again.",
                Toast.LENGTH_LONG
            ).show()

            activity.startActivityForResult(
                permissionIntent,
                VPN_PERMISSION_REQUEST
            )
            return
        }

        setConnecting()
        controller.connect()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            setConnecting()
            controller.connect()
        } else {
            setConnectionFailed("VPN permission denied")
        }
    }

    private fun updateState(state: ProtonGuestState) {
        when (state) {
            is ProtonGuestState.Disconnected -> {
                connected = false
                busy = false
                statusText.text = "● DISCONNECTED"
                statusText.setTextColor(muted)
                detailText.text = "PROTON GUEST READY"
                detailText.setTextColor(muted)
            }

            is ProtonGuestState.WaitingForPermission -> {
                setConnecting()
                detailText.text = "WAITING FOR VPN PERMISSION"
            }

            is ProtonGuestState.CreatingGuestSession -> {
                setConnecting()
                detailText.text = "CREATING PROTON GUEST SESSION..."
            }

            is ProtonGuestState.DiscoveringServers -> {
                setConnecting()
                detailText.text = "SEARCHING PROTON SERVERS..."
            }

            is ProtonGuestState.Connecting -> {
                setConnecting()
                detailText.text = "ESTABLISHING PROTON TUNNEL..."
            }

            is ProtonGuestState.Connected -> {
                connected = true
                busy = false
                statusText.text = "● PROTON VPN CONNECTED"
                statusText.setTextColor(green)
                detailText.text = "SERVER: ${state.serverName}"
                detailText.setTextColor(green)
            }

            is ProtonGuestState.Error -> {
                setConnectionFailed(state.message)
            }
        }

        updateButton()
    }

    private fun setConnecting() {
        connected = false
        busy = true
        statusText.text = "CONNECTING..."
        statusText.setTextColor(cyan)
        detailText.text = "STARTING PROTON GUEST"
        detailText.setTextColor(cyan)
        updateButton()
    }

    private fun setDisconnecting() {
        busy = true
        statusText.text = "DISCONNECTING..."
        statusText.setTextColor(yellow)
        detailText.text = "CLOSING PROTON TUNNEL"
        detailText.setTextColor(yellow)
        updateButton()
    }

    private fun setConnectionFailed(message: String) {
        connected = false
        busy = false
        statusText.text = "✕ CONNECTION FAILED"
        statusText.setTextColor(red)
        detailText.text = message
        detailText.setTextColor(yellow)
        updateButton()

        Toast.makeText(
            context,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    fun setConnected(isConnected: Boolean) {
        connected = isConnected
        busy = false

        statusText.text =
            if (connected) "● PROTON VPN CONNECTED"
            else "● DISCONNECTED"

        statusText.setTextColor(
            if (connected) green else muted
        )

        detailText.text =
            if (connected) "SECURE TUNNEL ACTIVE"
            else "PROTON GUEST READY"

        updateButton()
    }

    fun setStatus(message: String, isConnected: Boolean) {
        connected = isConnected
        busy = false
        statusText.text = message
        statusText.setTextColor(
            if (connected) green else yellow
        )
        detailText.text =
            if (connected) "SECURE TUNNEL ACTIVE"
            else "CHECK PROTON SERVICE"

        updateButton()
    }

    fun setConnectionFailedPublic(message: String) {
        setConnectionFailed(message)
    }

    fun shutdown() {
        if (backend is com.example.autoiprotator.ProtonGuestWireGuardBackend) {
            backend.shutdown()
        }
    }

    companion object {
        const val VPN_PERMISSION_REQUEST = 4193
    }
}