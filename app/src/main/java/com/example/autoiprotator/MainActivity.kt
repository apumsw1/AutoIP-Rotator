package com.example.autoiprotator

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
import android.widget.*
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var engine: VpnEngine

    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private lateinit var countryText: TextView
    private lateinit var warpText: TextView
    private lateinit var connectButton: Button
    private lateinit var autoSwitch: Switch
    private lateinit var intervalSpinner: Spinner

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var profile: String? = null
    private var connected = false
    private var connecting = false
    private var reconnecting = false
    private var autoReconnect = false
    private var reconnectInterval = 60000L

    private var nextReconnect = 0L

    private val vpnRequestCode = 4101

    private val cyan = Color.rgb(0, 225, 255)
    private val green = Color.rgb(0, 230, 100)
    private val dark = Color.rgb(2, 9, 23)
    private val white = Color.WHITE
    private val muted = Color.rgb(150, 180, 205)
    private val red = Color.rgb(255, 55, 65)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun background(
        color: Int,
        stroke: Int = cyan,
        radius: Int = 20
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }
    }

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(9), dp(8), dp(9))
        }
    }

    private fun makeButton(value: String): Button {
        return Button(this).apply {
            text = value
            textSize = 19f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            background = background(
                Color.rgb(0, 125, 45),
                green,
                40
            )
            stateListAnimator = null
        }
    }

    private fun addSpace(parent: LinearLayout, height: Int = 12) {
        parent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(height))
        )
    }

    private fun makeCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = background(
                Color.rgb(7, 25, 45),
                cyan,
                22
            )
        }
    }

    private fun addCard(parent: LinearLayout, child: View) {
        parent.addView(
            child,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(15)
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        engine = VpnEngine(applicationContext)

        buildUI()
        loadSavedProfile()
        checkNetwork()
    }

    private fun buildUI() {

        val scroll = ScrollView(this).apply {
            setBackgroundColor(dark)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(17), dp(22), dp(17), dp(25))
        }

        root.addView(
            makeText("🛡", 55f, cyan, true)
        )

        root.addView(
            makeText("ULTRA VPN", 32f, cyan, true)
        )

        root.addView(
            makeText(
                "CLOUDFLARE WARP CONNECTION",
                12f,
                green,
                true
            )
        )

        addSpace(root, 22)

        val statusCard = makeCard()

        statusCard.addView(
            makeText("CONNECTION STATUS", 14f, muted, true)
        )

        statusText = makeText(
            "DISCONNECTED",
            26f,
            Color.YELLOW,
            true
        )

        statusCard.addView(statusText)

        addCard(root, statusCard)

        val ipCard = makeCard()

        ipCard.addView(
            makeText("PUBLIC IP ADDRESS", 14f, cyan, true)
        )

        ipText = makeText(
            "Checking...",
            21f,
            white,
            true
        )

        ipCard.addView(ipText)

        addSpace(ipCard)

        ipCard.addView(
            makeText("COUNTRY / LOCATION", 13f, muted, true)
        )

        countryText = makeText(
            "Checking...",
            19f,
            white,
            true
        )

        ipCard.addView(countryText)

        addSpace(ipCard)

        warpText = makeText(
            "WARP: NOT VERIFIED",
            15f,
            Color.YELLOW,
            true
        )

        ipCard.addView(warpText)

        addCard(root, ipCard)

        connectButton = makeButton("⚡ START VPN")

        connectButton.setOnClickListener {
            if (connecting || reconnecting) return@setOnClickListener

            if (connected) {
                disconnectVpn()
            } else {
                requestVpnPermission()
            }
        }

        root.addView(
            connectButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(65)
            ).apply {
                bottomMargin = dp(18)
            }
        )

        val autoCard = makeCard()

        autoCard.addView(
            makeText("AUTO RECONNECT", 18f, cyan, true)
        )

        autoSwitch = Switch(this).apply {
            text = "Enable automatic reconnect"
            textSize = 14f
            setTextColor(white)

            setOnCheckedChangeListener { _, checked ->
                autoReconnect = checked

                if (checked && connected) {
                    nextReconnect =
                        System.currentTimeMillis() + reconnectInterval
                }
            }
        }

        autoCard.addView(autoSwitch)

        addSpace(autoCard)

        autoCard.addView(
            makeText("RECONNECT INTERVAL", 13f, muted, true)
        )

        intervalSpinner = Spinner(this)

        val intervalOptions = arrayOf(
            "30 seconds",
            "1 minute",
            "2 minutes",
            "5 minutes"
        )

        intervalSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            intervalOptions
        )

        intervalSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    reconnectInterval = when (position) {
                        0 -> 30000L
                        1 -> 60000L
                        2 -> 120000L
                        else -> 300000L
                    }

                    if (connected && autoReconnect) {
                        nextReconnect =
                            System.currentTimeMillis() + reconnectInterval
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) = Unit
            }

        autoCard.addView(
            intervalSpinner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        addCard(root, autoCard)

        root.addView(
            makeText(
                "POWERED BY CLOUDFLARE WARP",
                12f,
                cyan,
                true
            )
        )

        root.addView(
            makeText(
                "ULTRA VPN • SECURE NETWORK",
                10f,
                muted
            )
        )

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun loadSavedProfile() {
        profile = CloudflareWarpClient.getSavedProfile(this)
    }

    private fun requestVpnPermission() {

        val intent = VpnService.prepare(this)

        if (intent != null) {
            startActivityForResult(intent, vpnRequestCode)
        } else {
            startConnection()
        }
    }

    @Deprecated("Activity result compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == vpnRequestCode) {
            if (resultCode == RESULT_OK) {
                startConnection()
            } else {
                showError("VPN permission was denied.")
            }
        }
    }

    private fun startConnection() {

        if (connecting || connected) return

        connecting = true

        statusText.text = "CONNECTING..."
        statusText.setTextColor(Color.YELLOW)

        connectButton.isEnabled = false
        connectButton.text = "PLEASE WAIT..."

        executor.execute {
            try {
                val config = profile
                    ?: CloudflareWarpClient.register(this).also {
                        profile = it
                    }

                engine.connect(config)

                val active = engine.isConnected()

                if (!active) {
                    throw IllegalStateException(
                        "WireGuard tunnel did not become active."
                    )
                }

                runOnUiThread {
                    connecting = false
                    connected = true

                    statusText.text = "TUNNEL ACTIVE"
                    statusText.setTextColor(green)

                    connectButton.text = "STOP VPN"
                    connectButton.isEnabled = true
                    connectButton.background = background(red, red, 40)

                    if (autoReconnect) {
                        nextReconnect =
                            System.currentTimeMillis() + reconnectInterval
                    }

                    checkNetwork()
                    handler.removeCallbacks(reconnectTask)
                    handler.postDelayed(reconnectTask, 5000)
                }

            } catch (error: Exception) {
                runOnUiThread {
                    connecting = false
                    connected = false

                    statusText.text = "CONNECTION FAILED"
                    statusText.setTextColor(red)

                    connectButton.text = "⚡ START VPN"
                    connectButton.isEnabled = true
                    connectButton.background =
                        background(Color.rgb(0, 125, 45), green, 40)

                    showError(
                        error.message ?: "Unable to connect to WARP."
                    )
                }
            }
        }
    }

    private fun disconnectVpn() {

        connectButton.isEnabled = false
        statusText.text = "DISCONNECTING..."

        executor.execute {
            try {
                engine.disconnect()

                runOnUiThread {
                    connected = false
                    connecting = false
                    reconnecting = false

                    handler.removeCallbacks(reconnectTask)

                    statusText.text = "DISCONNECTED"
                    statusText.setTextColor(Color.YELLOW)

                    connectButton.text = "⚡ START VPN"
                    connectButton.isEnabled = true
                    connectButton.background =
                        background(Color.rgb(0, 125, 45), green, 40)

                    warpText.text = "WARP: DISCONNECTED"
                    warpText.setTextColor(Color.YELLOW)
                }

            } catch (error: Exception) {
                runOnUiThread {
                    connectButton.isEnabled = true
                    showError(
                        error.message ?: "Disconnect failed."
                    )
                }
            }
        }
    }

    private fun checkNetwork() {

        ipText.text = "Checking..."
        countryText.text = "Checking..."
        warpText.text = "WARP: VERIFYING..."

        executor.execute {
            try {
                val trace = CloudflareWarpClient.checkTrace()

                val ip = trace["ip"] ?: "Unknown"
                val country = trace["loc"] ?: "Unknown"
                val warp = trace["warp"] ?: "unknown"

                runOnUiThread {
                    ipText.text = ip
                    countryText.text = country

                    if (warp.equals("on", true) ||
                        warp.equals("plus", true)
                    ) {
                        warpText.text = "WARP: VERIFIED"
                        warpText.setTextColor(green)
                    } else {
                        warpText.text = "WARP: NOT VERIFIED"
                        warpText.setTextColor(Color.YELLOW)
                    }
                }

            } catch (error: Exception) {
                runOnUiThread {
                    ipText.text = "Unavailable"
                    countryText.text = "Unavailable"
                    warpText.text = "WARP: CHECK FAILED"
                    warpText.setTextColor(red)
                }
            }
        }
    }

    private val reconnectTask = object : Runnable {
        override fun run() {

            if (!connected) return

            if (autoReconnect &&
                System.currentTimeMillis() >= nextReconnect &&
                !reconnecting
            ) {
                reconnectVpn()
            } else {
                checkNetwork()
                handler.postDelayed(this, 30000)
            }
        }
    }

    private fun reconnectVpn() {

        val config = profile ?: return

        if (!connected || reconnecting) return

        reconnecting = true

        statusText.text = "RECONNECTING..."
        statusText.setTextColor(Color.YELLOW)

        executor.execute {
            try {
                engine.disconnect()
                Thread.sleep(1000)
                engine.connect(config)

                val active = engine.isConnected()

                runOnUiThread {
                    reconnecting = false
                    connected = active

                    if (active) {
                        statusText.text = "TUNNEL ACTIVE"
                        statusText.setTextColor(green)

                        nextReconnect =
                            System.currentTimeMillis() + reconnectInterval

                        checkNetwork()
                    } else {
                        statusText.text = "RECONNECT FAILED"
                        statusText.setTextColor(red)
                    }

                    handler.postDelayed(reconnectTask, 5000)
                }

            } catch (error: Exception) {
                runOnUiThread {
                    reconnecting = false
                    connected = false

                    statusText.text = "RECONNECT FAILED"
                    statusText.setTextColor(red)

                    connectButton.text = "⚡ START VPN"
                    connectButton.isEnabled = true
                }
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdown()
        super.onDestroy()
    }
}
