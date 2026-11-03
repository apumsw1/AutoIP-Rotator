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
import android.view.animation.DecelerateInterpolator
import android.widget.*
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var engine: VpnEngine
    private lateinit var status: TextView
    private lateinit var personalConnectButton: Button
    private lateinit var timerSpinner: Spinner
    private lateinit var connectionTimeLabel: TextView
    private lateinit var rotationCountdownLabel: TextView
    private lateinit var personalPanel: LinearLayout
    private lateinit var publicPanel: LinearLayout
    private lateinit var publicStatus: TextView
    private lateinit var serverSpinner: Spinner
    private lateinit var serverDetails: TextView
    private lateinit var serverLoader: VpnGateServerLoader

    private val executor = Executors.newSingleThreadExecutor()
    private val timerHandler = Handler(Looper.getMainLooper())

    private var configText: String? = null
    private var connected = false
    private var reconnectInProgress = false
    private var connectionStartedAt = 0L
    private var nextReconnectAt = 0L
    private var activeIntervalMs = 0L
    private var publicServers: List<VpnGateServer> = emptyList()
    private var selectedPublicServer: VpnGateServer? = null
    private var logoAnimator: ObjectAnimator? = null
    private var pulseAnimator: ObjectAnimator? = null

    private val timerOptions = arrayOf(
        "∞ Unlimited — Stable IP",
        "5 seconds — Auto Reconnect",
        "10 seconds — Auto Reconnect",
        "30 seconds — Auto Reconnect",
        "1 minute — Auto Reconnect",
        "2 minutes — Auto Reconnect",
        "5 minutes — Auto Reconnect"
    )

    private val timerTick = object : Runnable {
        override fun run() {
            if (!connected || isFinishing) return

            val now = System.currentTimeMillis()
            val elapsed = ((now - connectionStartedAt) / 1000L).coerceAtLeast(0L)
            connectionTimeLabel.text = "CONNECTED TIME: ${formatDuration(elapsed)}"

            if (activeIntervalMs == 0L) {
                rotationCountdownLabel.text = "AUTO-RECONNECT: UNLIMITED"
            } else {
                val remaining = ((nextReconnectAt - now).coerceAtLeast(0L) / 1000L)
                rotationCountdownLabel.text = "NEXT RECONNECT: ${formatDuration(remaining)}"
                if (now >= nextReconnectAt && !reconnectInProgress) {
                    reconnectUsingCurrentConfig()
                }
            }
            timerHandler.postDelayed(this, 1000L)
        }
    }

    private val bg = Color.rgb(4, 9, 18)
    private val cardTop = Color.rgb(13, 35, 48)
    private val cardBottom = Color.rgb(7, 17, 30)
    private val cyan = Color.rgb(0, 235, 255)
    private val neon = Color.rgb(0, 255, 130)
    private val white = Color.rgb(235, 250, 255)
    private val muted = Color.rgb(150, 180, 195)
    private val warning = Color.rgb(255, 190, 50)

    companion object {
        private const val VPN_REQUEST = 4101
        private const val FILE_REQUEST = 4102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = VpnEngine(applicationContext)
        serverLoader = VpnGateServerLoader()
        buildInterface()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun makeGradient(first: Int, second: Int, borderColor: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(first, second)
        ).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), borderColor)
        }

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD)
            else Typeface.create("sans-serif", Typeface.NORMAL)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            letterSpacing = 0.04f
        }

    private fun makeButton(value: String, enabled: Boolean = true): Button =
        Button(this).apply {
            text = value
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (enabled) bg else muted)
            isEnabled = enabled
            background = makeGradient(
                if (enabled) cyan else Color.rgb(35, 48, 60),
                if (enabled) neon else Color.rgb(20, 30, 42),
                if (enabled) cyan else Color.rgb(65, 80, 95)
            )
            elevation = dp(7).toFloat()
            stateListAnimator = null
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = makeGradient(cardTop, cardBottom, Color.rgb(0, 150, 180))
            elevation = dp(8).toFloat()
        }

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun withBottomMargin(bottom: Int) = fullWidth().apply {
        bottomMargin = dp(bottom)
    }

    private fun animateEntry(view: View) {
        view.alpha = 0.25f
        view.translationY = dp(14).toFloat()
        view.animate().alpha(1f).translationY(0f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun buildInterface() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(30))
        }

        val logo = makeText("◈ AUTOIP ROTATOR ◈", 26f, cyan, true)
        root.addView(logo, fullWidth())
        root.addView(makeText("CYBER VPN CONTROL CENTER", 12f, muted, true))
        root.addView(makeText("SYSTEM INITIALIZED  //  SECURE MODE", 10f, neon, true))

        logoAnimator = ObjectAnimator.ofFloat(logo, View.ALPHA, 0.65f, 1f).apply {
            duration = 1300
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        val statusCard = card()
        status = makeText("● PERSONAL VPN: NOT CONNECTED", 14f, warning, true)
        statusCard.addView(status, fullWidth())
        root.addView(statusCard, withBottomMargin(16))

        val modeCard = card()
        modeCard.addView(makeText("SELECT VPN MODE", 14f, cyan, true))
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val personalTab = makeButton("PERSONAL VPN")
        val publicTab = makeButton("PUBLIC VPN")
        modeRow.addView(personalTab, LinearLayout.LayoutParams(0, dp(50), 1f).apply {
            marginEnd = dp(5)
        })
        modeRow.addView(publicTab, LinearLayout.LayoutParams(0, dp(50), 1f).apply {
            marginStart = dp(5)
        })
        modeCard.addView(modeRow)
        root.addView(modeCard, withBottomMargin(16))

        buildPersonalPanel(root)
        buildPublicPanel(root)

        root.addView(makeText("NETWORK MONITOR  //  READY", 11f, neon, true))
        root.addView(makeText(
            "Reconnect uses the imported configuration. A different IP requires a different VPN endpoint.",
            10f, muted
        ))

        personalTab.setOnClickListener {
            personalPanel.visibility = View.VISIBLE
            publicPanel.visibility = View.GONE
            animateEntry(personalPanel)
        }
        publicTab.setOnClickListener {
            personalPanel.visibility = View.GONE
            publicPanel.visibility = View.VISIBLE
            animateEntry(publicPanel)
        }

        scroll.addView(root)
        setContentView(scroll)
        animateEntry(statusCard)
    }

    private fun buildPersonalPanel(root: LinearLayout) {
        personalPanel = card()
        personalPanel.addView(makeText("01  //  PERSONAL VPN", 17f, neon, true))
        personalPanel.addView(makeText("YOUR OWN WIREGUARD CONFIGURATION", 10f, muted, true))

        val importButton = makeButton("IMPORT WIREGUARD .CONF")
        importButton.setOnClickListener { openConfigPicker() }
        personalPanel.addView(importButton, fullWidth().apply {
            topMargin = dp(12)
            bottomMargin = dp(14)
        })

        personalPanel.addView(makeText("AUTO-RECONNECT INTERVAL", 13f, cyan, true))
        timerSpinner = Spinner(this)
        val timerAdapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, timerOptions
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(white)
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(15), dp(13), dp(15), dp(13))
                    background = makeGradient(
                        Color.rgb(15, 55, 72), Color.rgb(8, 30, 48), cyan
                    )
                }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(white)
                    textSize = 15f
                    setPadding(dp(18), dp(14), dp(18), dp(14))
                    setBackgroundColor(Color.rgb(10, 28, 43))
                }
        }
        timerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        timerSpinner.adapter = timerAdapter
        timerSpinner.background = makeGradient(
            Color.rgb(15, 55, 72), Color.rgb(8, 30, 48), cyan
        )
        timerSpinner.dropDownVerticalOffset = dp(6)
        timerSpinner.setSelection(0)
        timerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateReconnectInterval(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        personalPanel.addView(timerSpinner, fullWidth().apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        })

        connectionTimeLabel = makeText("CONNECTED TIME: 00:00:00", 12f, neon, true)
        rotationCountdownLabel = makeText("AUTO-RECONNECT: UNLIMITED", 12f, cyan, true)
        personalPanel.addView(connectionTimeLabel, fullWidth())
        personalPanel.addView(rotationCountdownLabel, fullWidth())

        personalConnectButton = makeButton("CONNECT PERSONAL VPN", false)
        personalConnectButton.setOnClickListener {
            if (connected) disconnectVpn() else requestPermissionAndConnect()
        }
        personalPanel.addView(personalConnectButton, fullWidth().apply {
            topMargin = dp(10)
            bottomMargin = dp(8)
        })
        personalPanel.addView(makeText(
            "Your private configuration stays on this device.", 10f, muted
        ))
        root.addView(personalPanel, withBottomMargin(16))
    }

    private fun buildPublicPanel(root: LinearLayout) {
        publicPanel = card()
        publicPanel.visibility = View.GONE
        publicPanel.addView(makeText("02  //  PUBLIC VPN", 17f, cyan, true))
        publicPanel.addView(makeText("FREE VPN GATE SERVER DISCOVERY", 11f, muted, true))
        publicStatus = makeText("SERVER LIST NOT LOADED", 14f, warning, true)
        publicPanel.addView(publicStatus, fullWidth())

        val refreshButton = makeButton("REFRESH FREE SERVERS")
        refreshButton.setOnClickListener { loadPublicServers() }
        publicPanel.addView(refreshButton, fullWidth().apply {
            topMargin = dp(10)
            bottomMargin = dp(10)
        })
        publicPanel.addView(makeText("SELECT A VPN GATE SERVER", 12f, cyan, true))
        serverSpinner = Spinner(this)
        serverSpinner.background = makeGradient(
            Color.rgb(15, 55, 72), Color.rgb(8, 30, 48), cyan
        )
        publicPanel.addView(serverSpinner, fullWidth().apply {
            topMargin = dp(8)
            bottomMargin = dp(10)
        })
        serverDetails = makeText(
            "Tap Refresh Free Servers to retrieve available volunteer relays.", 12f, muted
        )
        publicPanel.addView(serverDetails, fullWidth())
        publicPanel.addView(makeText(
            "SERVER DISCOVERY ONLY — OPENVPN ENGINE NOT INSTALLED", 11f, warning, true
        ))
        publicPanel.addView(makeText(
            "VPN Gate servers are volunteer-operated. Availability and speed can change. Do not treat an untrusted relay as private.",
            10f, muted
        ))
        root.addView(publicPanel, withBottomMargin(16))
    }

    private fun loadPublicServers() {
        publicStatus.text = "LOADING VPN GATE SERVERS..."
        publicStatus.setTextColor(warning)
        serverLoader.load { result ->
            result.onSuccess { servers ->
                publicServers = servers
                if (servers.isEmpty()) {
                    publicStatus.text = "NO SERVERS FOUND"
                    publicStatus.setTextColor(warning)
                    serverDetails.text = "The server list was empty. Try refreshing later."
                    return@load
                }
                val labels = servers.map { server ->
                    val ping = if (server.pingMs >= 0) "${server.pingMs} ms" else "Ping unknown"
                    "${server.country} • ${server.speedMbps.toInt()} Mbps • $ping"
                }
                val adapter = ArrayAdapter(
                    this, android.R.layout.simple_spinner_item, labels
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                serverSpinner.adapter = adapter
                serverSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?, view: View?, position: Int, id: Long
                    ) {
                        selectedPublicServer = publicServers.getOrNull(position)
                        showSelectedServer()
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        selectedPublicServer = null
                    }
                }
                publicStatus.text = "FOUND ${servers.size} PUBLIC RELAYS"
                publicStatus.setTextColor(neon)
                serverSpinner.setSelection(0)
            }.onFailure { error ->
                publicStatus.text = "SERVER LIST LOAD FAILED"
                publicStatus.setTextColor(warning)
                serverDetails.text = error.message ?: "Could not load VPN Gate servers."
            }
        }
    }

    private fun showSelectedServer() {
        val server = selectedPublicServer ?: return
        val ping = if (server.pingMs >= 0) "${server.pingMs} ms" else "Unknown"
        serverDetails.text = buildString {
            append("Host: ${server.hostName}")
            append("\nIP: ${server.ip}")
            append("\nCountry: ${server.country}")
            append("\nSpeed: ${String.format(Locale.US, "%.1f Mbps", server.speedMbps)}")
            append("\nPing: $ping")
            append("\nSessions: ${server.sessions}")
            append("\n\nSelected for a future connection step. No public VPN tunnel is active.")
        }
    }

    private fun openConfigPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, FILE_REQUEST)
    }

    @Deprecated("Activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val flags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Persistent access is not supported by every document provider.
            }
            status.text = "READING CONFIGURATION..."
            status.setTextColor(warning)
            executor.execute {
                try {
                    val imported = contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                        ?: throw Exception("Could not read configuration.")
                    require(imported.contains("[Interface]") && imported.contains("[Peer]")) {
                        "Invalid WireGuard configuration."
                    }
                    runOnUiThread {
                        configText = imported
                        status.text = "PERSONAL CONFIG IMPORTED"
                        status.setTextColor(neon)
                        personalConnectButton.isEnabled = true
                        Toast.makeText(this, "Configuration imported successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        status.text = "IMPORT FAILED"
                        status.setTextColor(warning)
                        Toast.makeText(this, e.message ?: "Import failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) connectVpn()
            else {
                status.text = "VPN PERMISSION DENIED"
                status.setTextColor(warning)
            }
        }
    }

    private fun requestPermissionAndConnect() {
        if (configText.isNullOrBlank()) {
            Toast.makeText(this, "Import your .conf file first", Toast.LENGTH_SHORT).show()
            return
        }
        val permission = VpnService.prepare(this)
        if (permission != null) startActivityForResult(permission, VPN_REQUEST)
        else connectVpn()
    }

    private fun selectedReconnectIntervalMs(): Long {
        return when (timerSpinner.selectedItemPosition) {
            1 -> 5_000L
            2 -> 10_000L
            3 -> 30_000L
            4 -> 60_000L
            5 -> 120_000L
            6 -> 300_000L
            else -> 0L
        }
    }

    private fun updateReconnectInterval(position: Int) {
        activeIntervalMs = when (position) {
            1 -> 5_000L
            2 -> 10_000L
            3 -> 30_000L
            4 -> 60_000L
            5 -> 120_000L
            6 -> 300_000L
            else -> 0L
        }
        if (connected) {
            nextReconnectAt = if (activeIntervalMs == 0L) 0L
            else System.currentTimeMillis() + activeIntervalMs
            timerHandler.removeCallbacks(timerTick)
            timerHandler.post(timerTick)
        } else {
            rotationCountdownLabel.text = if (activeIntervalMs == 0L)
                "AUTO-RECONNECT: UNLIMITED"
            else "AUTO-RECONNECT: ${formatDuration(activeIntervalMs / 1000L)}"
        }
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun startConnectionClock() {
        timerHandler.removeCallbacks(timerTick)
        connectionStartedAt = System.currentTimeMillis()
        nextReconnectAt = if (activeIntervalMs == 0L) 0L
        else connectionStartedAt + activeIntervalMs
        startConnectedPulse()
        timerHandler.post(timerTick)
    }

    private fun stopConnectionClock() {
        timerHandler.removeCallbacks(timerTick)
        pulseAnimator?.cancel()
        pulseAnimator = null
        connectionTimeLabel.text = "CONNECTED TIME: 00:00:00"
        rotationCountdownLabel.text = if (activeIntervalMs == 0L)
            "AUTO-RECONNECT: UNLIMITED"
        else "AUTO-RECONNECT: ${formatDuration(activeIntervalMs / 1000L)}"
    }

    private fun startConnectedPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofFloat(status, View.ALPHA, 0.55f, 1f).apply {
            duration = 850
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun connectVpn() {
        val config = configText ?: return
        personalConnectButton.isEnabled = false
        status.text = "CONNECTING PERSONAL VPN..."
        status.setTextColor(warning)
        executor.execute {
            try {
                engine.connect(config)
                runOnUiThread {
                    connected = true
                    status.text = "PERSONAL VPN CONNECTED"
                    status.setTextColor(neon)
                    personalConnectButton.text = "DISCONNECT PERSONAL VPN"
                    personalConnectButton.isEnabled = true
                    startConnectionClock()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    connected = false
                    stopConnectionClock()
                    status.text = "CONNECTION FAILED"
                    status.setTextColor(warning)
                    personalConnectButton.text = "CONNECT PERSONAL VPN"
                    personalConnectButton.isEnabled = !configText.isNullOrBlank()
                    Toast.makeText(this, e.message ?: "VPN connection failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun reconnectUsingCurrentConfig() {
        val config = configText ?: run {
            disconnectVpn()
            return
        }
        reconnectInProgress = true
        status.text = "RECONNECTING PERSONAL VPN..."
        status.setTextColor(warning)
        executor.execute {
            try {
                engine.disconnect()
                engine.connect(config)
                runOnUiThread {
                    reconnectInProgress = false
                    if (!connected || isFinishing) return@runOnUiThread
                    status.text = "PERSONAL VPN CONNECTED"
                    status.setTextColor(neon)
                    nextReconnectAt = if (activeIntervalMs == 0L) 0L
                    else System.currentTimeMillis() + activeIntervalMs
                }
            } catch (e: Exception) {
                runOnUiThread {
                    reconnectInProgress = false
                    connected = false
                    stopConnectionClock()
                    status.text = "RECONNECT FAILED"
                    status.setTextColor(warning)
                    personalConnectButton.text = "CONNECT PERSONAL VPN"
                    personalConnectButton.isEnabled = !configText.isNullOrBlank()
                    Toast.makeText(this, e.message ?: "Automatic reconnect failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun disconnectVpn() {
        personalConnectButton.isEnabled = false
        timerHandler.removeCallbacks(timerTick)
        status.text = "DISCONNECTING..."
        status.setTextColor(warning)
        executor.execute {
            try {
                engine.disconnect()
                runOnUiThread {
                    connected = false
                    reconnectInProgress = false
                    stopConnectionClock()
                    status.text = "PERSONAL VPN DISCONNECTED"
                    status.setTextColor(white)
                    personalConnectButton.text = "CONNECT PERSONAL VPN"
                    personalConnectButton.isEnabled = !configText.isNullOrBlank()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "DISCONNECT FAILED"
                    status.setTextColor(warning)
                    personalConnectButton.isEnabled = true
                    if (connected) timerHandler.post(timerTick)
                    Toast.makeText(this, e.message ?: "Disconnect failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        timerHandler.removeCallbacksAndMessages(null)
        logoAnimator?.cancel()
        pulseAnimator?.cancel()
        serverLoader.shutdown()
        executor.shutdown()
        super.onDestroy()
    }
}
