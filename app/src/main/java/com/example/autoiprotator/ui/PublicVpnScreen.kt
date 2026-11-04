package com.example.autoiprotator.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class PublicVpnScreen(private val context: Context) {

    private val cyan = Color.rgb(0, 225, 255)
    private val green = Color.rgb(0, 255, 105)
    private val red = Color.rgb(255, 45, 55)
    private val white = Color.rgb(240, 250, 255)
    private val muted = Color.rgb(145, 180, 205)

    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button

    var onConnect: (() -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null

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

    private fun makeButton(
        value: String,
        color: Int,
        stroke: Int
    ) = Button(context).apply {
        text = value
        textSize = 17f
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(white)
        background = makeBackground(color, stroke)
        stateListAnimator = null
        elevation = dp(4).toFloat()
    }

    fun createView(): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(7, 29, 51), Color.rgb(2, 12, 29))
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), cyan)
            }
        }

        panel.addView(
            makeLabel("PUBLIC VPN", 19f, cyan, true),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            )
        )

        statusText = makeLabel("DISCONNECTED", 17f, muted, true)
        panel.addView(
            statusText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(55)
            )
        )

        connectButton = makeButton("CONNECT", Color.rgb(0, 100, 35), green)
        panel.addView(
            connectButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
            ).apply { topMargin = dp(10) }
        )

        disconnectButton = makeButton("DISCONNECT", Color.rgb(100, 0, 15), red)
        panel.addView(
            disconnectButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
            ).apply { topMargin = dp(12) }
        )

        connectButton.setOnClickListener {
            setConnecting()
            val callback = onConnect
            if (callback == null) {
                setConnectionFailed()
            } else {
                callback.invoke()
            }
        }

        disconnectButton.setOnClickListener {
            disconnectButton.isEnabled = false
            val callback = onDisconnect
            if (callback == null) {
                setConnected(false)
            } else {
                callback.invoke()
            }
        }

        setConnected(false)
        return panel
    }

    fun setConnected(connected: Boolean) {
        statusText.text = if (connected) "CONNECTED" else "DISCONNECTED"
        statusText.setTextColor(if (connected) green else muted)
        connectButton.isEnabled = !connected
        disconnectButton.isEnabled = connected
    }

    fun setConnecting() {
        statusText.text = "CONNECTING..."
        statusText.setTextColor(cyan)
        connectButton.isEnabled = false
        disconnectButton.isEnabled = false
    }

    fun setConnectionFailed() {
        statusText.text = "CONNECTION FAILED"
        statusText.setTextColor(red)
        connectButton.isEnabled = true
        disconnectButton.isEnabled = false
    }
}