
package com.example.autoiprotator

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var engine: VpnEngine
    private lateinit var status: TextView
    private lateinit var connectionTime: TextView
    private lateinit var countdown: TextView
    private lateinit var connectButton: Button
    private lateinit var timerSpinner: Spinner
    private lateinit var personalPanel: LinearLayout
    private lateinit var publicPanel: LinearLayout

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var configText: String? = null
    private var connected = false
    private var publicSelected = true
    private var reconnecting = false
    private var startedAt = 0L
    private var nextReconnect = 0L
    private var interval = 0L

    private val cyan = Color.rgb(0, 235, 255)
    private val green = Color.rgb(0, 255, 130)
    private val dark = Color.rgb(4, 9, 18)
    private val white = Color.rgb(235, 250, 255)
    private val muted = Color.rgb(150, 180, 195)
    private val warning = Color.rgb(255, 190, 50)

    private val options = arrayOf(
        "Unlimited - Stable IP",
        "5 seconds",
        "10 seconds",
        "30 seconds",
        "1 minute",
        "2 minutes",
        "5 minutes"
    )

    companion object {
        private const val VPN_REQUEST = 4101
        private const val FILE_REQUEST = 4102
    }

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    private fun gradient(a: Int, b: Int): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(a, b)
        ).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), cyan)
        }
    }

    private fun label(
        textValue: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private fun button(textValue: String) =
        Button(this).apply {
            text = textValue
            isAllCaps = false
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(dark)
            background = gradient(cyan, green)
            stateListAnimator = null
        }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(15), dp(15), dp(15), dp(15))
        background = gradient(
            Color.rgb(13, 35, 48),
            Color.rgb(7, 17, 30)
        )
        elevation = dp(6).toFloat()
    }

    private fun params() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        bottomMargin = dp(15)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = VpnEngine(applicationContext)
        buildUI()
    }

    private fun buildUI() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(dark)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(25))
        }

        val logo = label("AUTOIP ROTATOR", 25f, cyan, true)
        root.addView(logo, params())

        ObjectAnimator.ofFloat(
            logo, View.ALPHA, 0.6f, 1f
        ).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        root.addView(
            label("SECURE NETWORK CONNECTION", 11f, green, true),
            params()
        )

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val personalTab = button("PERSONAL VPN")
        val publicTab = button("PUBLIC VPN")

        tabs.addView(personalTab, LinearLayout.LayoutParams(0, dp(52), 1f))
        tabs.addView(publicTab, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(tabs, params())

        personalPanel = card()
        personalPanel.addView(
            label("PERSONAL VPN", 19f, cyan, true)
        )
        personalPanel.addView(
            label("YOUR PRIVATE WIREGUARD CONNECTION", 11f, muted)
        )

        val importButton = button("IMPORT WIREGUARD .CONF")
        importButton.setOnClickListener { openFilePicker() }
        personalPanel.addView(importButton, params())

        publicPanel = card()
        publicPanel.addView(
            label("PUBLIC VPN — GUEST MODE", 19f, cyan, true)
        )
        publicPanel.addView(
            label("NO ACCOUNT REQUIRED", 13f, green, true)
        )
        publicPanel.addView(
            label(
                "Guest mode selected. Public VPN server discovery and an authorized guest tunnel are not configured yet.",
                12f,
                muted
            )
        )
        publicPanel.addView(
            label(
                "The app will only show CONNECTED when a real VPN tunnel is active.",
                12f,
                white
            )
        )

        root.addView(personalPanel, params())
        root.addView(publicPanel, params())

        val statusCard = card()
        status = label(
            "PUBLIC VPN: NOT CONNECTED",
            16f,
            warning,
            true
        )
        connectionTime = label(
            "CONNECTED TIME: 00:00:00",
            14f,
            green,
            true
        )
        statusCard.addView(status)
        statusCard.addView(connectionTime)
        root.addView(statusCard, params())

        val timerCard = card()
        timerCard.addView(
            label("AUTO-RECONNECT TIMER", 17f, cyan, true)
        )

        timerSpinner = Spinner(this)
        timerSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            options
        )
        timerCard.addView(timerSpinner, params())

        countdown = label(
            "AUTO-RECONNECT: UNLIMITED",
            13f,
            cyan,
            true
        )
        timerCard.addView(countdown)
        root.addView(timerCard, params())

        timerSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    interval = when (position) {
                        1 -> 5000L
                        2 -> 10000L
                        3 -> 30000L
                        4 -> 60000L
                        5 -> 120000L
                        6 -> 300000L
                        else -> 0L
                    }

                    if (connected) {
                        nextReconnect = if (interval == 0L) {
                            0L
                        } else {
                            System.currentTimeMillis() + interval
                        }
                    }

                    updateCountdown()
                }

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) = Unit
            }

        connectButton = button("CONNECT")
        connectButton.textSize = 19f

        connectButton.setOnClickListener {
            if (publicSelected) {
                connectPublicGuest()
            } else {
                if (connected) disconnectVpn()
                else requestVpnPermission()
            }
        }

        root.addView(
            connectButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(62)
            ).apply {
                bottomMargin = dp(18)
            }
        )

        root.addView(
            label("NETWORK MONITOR READY", 13f, green, true)
        )

        personalTab.setOnClickListener {
            selectPersonal()
        }

        publicTab.setOnClickListener {
            selectPublic()
        }

        scroll.addView(root)
        setContentView(scroll)

        selectPublic()
    }

    private fun selectPersonal() {
        publicSelected = false
        publicPanel.visibility = View.GONE
        personalPanel.visibility = View.VISIBLE

        status.text = if (connected) {
            "PERSONAL VPN: CONNECTED"
        } else {
            "PERSONAL VPN: NOT CONNECTED"
        }

        status.setTextColor(if (connected) green else warning)
        connectButton.text = if (connected) "DISCONNECT" else "CONNECT"
        connectButton.isEnabled = connected || !configText.isNullOrBlank()
    }

    private fun selectPublic() {
        publicSelected = true
        personalPanel.visibility = View.GONE
        publicPanel.visibility = View.VISIBLE

        status.text = "PUBLIC VPN: NOT CONNECTED"
        status.setTextColor(warning)
        connectButton.text = "CONNECT"
        connectButton.isEnabled = true
    }

    private fun connectPublicGuest() {
        Toast.makeText(
            this,
            "Guest VPN server is not configured. No false connection will be shown.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, FILE_REQUEST)
    }

    @Deprecated("Activity result compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FILE_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return

            executor.execute {
                try {
                    val text = contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: throw Exception("Unable to read configuration.")

                    require(
                        text.contains("[Interface]") &&
                        text.contains("[Peer]")
                    ) {
                        "Invalid WireGuard configuration."
                    }

                    runOnUiThread {
                        configText = text
                        status.text = "PERSONAL VPN CONFIG READY"
                        status.setTextColor(green)

                        if (!publicSelected) {
                            connectButton.isEnabled = true
                        }

                        Toast.makeText(
                            this,
                            "Configuration imported.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            e.message ?: "Import failed.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) {
                connectVpn()
            } else {
                status.text = "VPN PERMISSION DENIED"
                status.setTextColor(warning)
            }
        }
    }

    private fun requestVpnPermission() {
        if (configText.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Import your .conf file first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val intent = VpnService.prepare(this)

        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST)
        } else {
            connectVpn()
        }
    }

    private fun connectVpn() {
        val config = configText ?: return

        connectButton.isEnabled = false
        status.text = "CONNECTING PERSONAL VPN"
        status.setTextColor(warning)

        executor.execute {
            try {
                engine.connect(config)
                val active = engine.isConnected()

                runOnUiThread {
                    connected = active

                    if (active) {
                        startedAt = System.currentTimeMillis()
                        nextReconnect = if (interval == 0L) {
                            0L
                        } else {
                            startedAt + interval
                        }

                        status.text = "PERSONAL VPN: CONNECTED"
                        status.setTextColor(green)
                        connectButton.text = "DISCONNECT"
                        connectButton.isEnabled = true
                        handler.removeCallbacks(clockTask)
                        handler.post(clockTask)
                    } else {
                        throw Exception("VPN tunnel is not active.")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    connected = false
                    status.text = "CONNECTION FAILED"
                    status.setTextColor(warning)
                    connectButton.text = "CONNECT"
                    connectButton.isEnabled = true

                    Toast.makeText(
                        this,
                        e.message ?: "VPN connection failed.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun disconnectVpn() {
        connectButton.isEnabled = false

        executor.execute {
            try {
                engine.disconnect()

                runOnUiThread {
                    connected = false
                    handler.removeCallbacks(clockTask)
                    connectionTime.text = "CONNECTED TIME: 00:00:00"
                    status.text = "PERSONAL VPN: DISCONNECTED"
                    status.setTextColor(white)
                    connectButton.text = "CONNECT"
                    connectButton.isEnabled = !configText.isNullOrBlank()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    connectButton.isEnabled = true
                    Toast.makeText(
                        this,
                        e.message ?: "Disconnect failed.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private val clockTask = object : Runnable {
        override fun run() {
            if (!connected) return

            val now = System.currentTimeMillis()
            val elapsed = (now - startedAt) / 1000L

            connectionTime.text =
                "CONNECTED TIME: ${formatTime(elapsed)}"

            updateCountdown()

            if (
                interval > 0L &&
                now >= nextReconnect &&
                !reconnecting
            ) {
                reconnectVpn()
            }

            handler.postDelayed(this, 1000L)
        }
    }

    private fun reconnectVpn() {
        val config = configText ?: return
        if (!connected || reconnecting) return

        reconnecting = true
        status.text = "RECONNECTING PERSONAL VPN"
        status.setTextColor(warning)

        executor.execute {
            try {
                engine.disconnect()
                engine.connect(config)
                val active = engine.isConnected()

                runOnUiThread {
                    reconnecting = false

                    if (active && connected) {
                        nextReconnect =
                            System.currentTimeMillis() + interval

                        status.text = "PERSONAL VPN: CONNECTED"
                        status.setTextColor(green)
                    } else {
                        connected = false
                        status.text = "RECONNECT FAILED"
                        status.setTextColor(warning)
                        connectButton.text = "CONNECT"
                        connectButton.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    reconnecting = false
                    connected = false
                    status.text = "RECONNECT FAILED"
                    status.setTextColor(warning)
                    connectButton.text = "CONNECT"
                    connectButton.isEnabled = true
                }
            }
        }
    }

    private fun updateCountdown() {
        if (interval == 0L) {
            countdown.text = "AUTO-RECONNECT: UNLIMITED"
            return
        }

        val remaining =
            ((nextReconnect - System.currentTimeMillis())
                .coerceAtLeast(0L)) / 1000L

        countdown.text =
            "NEXT RECONNECT: ${formatTime(remaining)}"
    }

    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60

        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            h, m, s
        )
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdown()
        super.onDestroy()
    }
}
