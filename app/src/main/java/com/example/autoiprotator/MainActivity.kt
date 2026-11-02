
package com.example.autoiprotator

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter

class MainActivity : Activity() {

    private var connected = false
    private lateinit var status: TextView
    private lateinit var connectButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 24)
            setBackgroundColor(Color.rgb(12, 18, 32))
        }

        fun label(text: String, size: Float, color: Int): TextView {
            return TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                setPadding(8, 12, 8, 12)
            }
        }

        val white = Color.WHITE
        val green = Color.rgb(63, 220, 160)
        val muted = Color.rgb(165, 180, 205)

        root.addView(label("AUTOIP ROTATOR", 25f, green))
        root.addView(label("VPN CONTROL CENTER", 13f, muted))

        status = label("● DISCONNECTED", 20f, white)
        root.addView(status)

        root.addView(label("SELECT SERVER", 15f, muted))

        val servers = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Custom VPN Configuration",
                    "WireGuard configuration required")
            )
        }
        root.addView(servers)

        root.addView(label("AUTO ROTATION INTERVAL", 15f, muted))

        val timers = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("5 seconds", "10 seconds", "30 seconds",
                    "1 minute", "2 minutes", "5 minutes")
            )
        }
        root.addView(timers)

        connectButton = Button(this).apply {
            text = "CONNECT"
            setOnClickListener {
                status.text = "VPN ENGINE NOT CONFIGURED"
                status.setTextColor(Color.YELLOW)
            }
        }
        root.addView(connectButton)

        val note = label(
            "Choose a server configuration to prepare your VPN connection.",
            13f, muted
        )
        root.addView(note)

        root.addView(label("Secure connection • Custom server support",
            12f, green))

        setContentView(root)
    }
}
