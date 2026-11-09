
package com.example.autoiprotator

import android.animation.ArgbEvaluator
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
import com.example.autoiprotator.ui.PublicVpnScreen
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
    private lateinit var publicVpnScreen: PublicVpnScreen
    private lateinit var personalTab: Button
    private lateinit var publicTab: Button
    private lateinit var monitorText: TextView
    private lateinit var statusIcon: TextView
    private lateinit var mainStatusTitle: TextView
    private lateinit var creationLabel: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var configText: String? = null
    private var connected = false
    private var publicSelected = false
    private var reconnecting = false
    private var startedAt = 0L
    private var nextReconnect = 0L
    private var interval = 0L

    private val cyan = Color.rgb(0, 225, 255)
    private val green = Color.rgb(0, 255, 105)
    private val dark = Color.rgb(2, 8, 20)
    private val white = Color.rgb(240, 250, 255)
    private val muted = Color.rgb(145, 180, 205)
    private val warning = Color.rgb(255, 190, 40)
    private val red = Color.rgb(255, 45, 55)

    private val options = arrayOf(
        "∞ Unlimited — Stable IP",
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

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun gradient(
        a: Int,
        b: Int,
        stroke: Int = cyan,
        radius: Int = 22
    ): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(a, b)
        ).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        gravityValue: Int = Gravity.CENTER
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = gravityValue
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setPadding(dp(5), dp(5), dp(5), dp(5))
    }

    private fun makeButton(
        value: String,
        start: Int,
        end: Int,
        stroke: Int,
        textColor: Int = white
    ) = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        background = gradient(start, end, stroke, 40)
        stateListAnimator = null
        elevation = dp(5).toFloat()
    }

    private fun card(stroke: Int = cyan) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = gradient(
                Color.rgb(7, 29, 51),
                Color.rgb(2, 12, 29),
                stroke,
                24
            )
            elevation = dp(5).toFloat()
        }

    private fun addSpace(parent: LinearLayout, height: Int = 12) {
        parent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(height))
        )
    }

    private fun addCard(parent: LinearLayout, child: View) {
        parent.addView(
            child,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        )
    }

    private fun animateView(view: View) {
        ObjectAnimator.ofFloat(
            view,
            View.ALPHA,
            0.72f,
            1f
        ).apply {
            duration = 1300
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun animateCreationText(view: TextView) {

        ObjectAnimator.ofFloat(
            view,
            View.TRANSLATION_Y,
            0f,
            -dp(5).toFloat(),
            0f
        ).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }

        val colors = intArrayOf(
            Color.rgb(255, 190, 40),
            Color.rgb(0, 225, 255),
            Color.rgb(100, 145, 255),
            Color.rgb(210, 90, 255),
            Color.rgb(255, 190, 40)
        )

        ValueAnimator.ofObject(
            ArgbEvaluator(),
            *colors.toTypedArray()
        ).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART

            addUpdateListener {
                view.setTextColor(it.animatedValue as Int)
            }

            start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = VpnEngine(applicationContext)
        buildUI()
    }

    private fun buildUI() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(dark)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(25))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(1, 9, 25),
                    Color.rgb(3, 19, 39),
                    Color.rgb(1, 7, 19)
                )
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // LEFT SHIELD LOGO
        header.addView(
            label("🛡", 38f, cyan, true),
            LinearLayout.LayoutParams(dp(58), dp(68))
        )

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        titleBox.addView(label("AUTOIP ROTATOR", 23f, cyan, true))

        // FLOATING, COLOR-CHANGING CREATION TEXT
        creationLabel = label(
            "♛  M'r ApurbA Creation  ♛",
            13f,
            warning,
            true
        )

        titleBox.addView(creationLabel)

        header.addView(
            titleBox,
            LinearLayout.LayoutParams(0, dp(70), 1f)
        )

        // RIGHT SIDE: SAME SHIELD LOGO
        header.addView(
            label("🛡", 38f, cyan, true),
            LinearLayout.LayoutParams(dp(48), dp(68))
        )

        addCard(root, header)

        addCard(
            root,
            label("SECURE NETWORK CONNECTION", 11f, green, true)
        )

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = gradient(
                Color.rgb(2, 15, 35),
                Color.rgb(1, 9, 24),
                cyan,
                40
            )
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        personalTab = makeButton(
            "♟  PERSONAL VPN",
            Color.rgb(0, 115, 220),
            Color.rgb(0, 220, 255),
            cyan
        )

        publicTab = makeButton(
            "ϟ  PUBLIC VPN",
            Color.rgb(2, 15, 35),
            Color.rgb(2, 15, 35),
            Color.TRANSPARENT
        )

        tabs.addView(
            personalTab,
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )

        tabs.addView(
            publicTab,
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )

        addCard(root, tabs)

        personalPanel = card()

        personalPanel.addView(
            label("PERSONAL WIREGUARD VPN", 17f, cyan, true)
        )

        personalPanel.addView(
            label(
                "YOUR PRIVATE VPN CONFIGURATION",
                11f,
                muted,
                true
            )
        )

        val importButton = makeButton(
            "IMPORT WIREGUARD .CONF",
            Color.rgb(0, 80, 135),
            Color.rgb(0, 170, 205),
            cyan
        )

        importButton.setOnClickListener {
            openFilePicker()
        }

        addSpace(personalPanel, 8)

        personalPanel.addView(
            importButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        publicVpnScreen = PublicVpnScreen(this)
        publicPanel = publicVpnScreen.createView()

        addCard(root, personalPanel)
        addCard(root, publicPanel)

        val statusCard = card()

        val statusHeading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        statusIcon = label("●", 25f, warning, true)

        statusHeading.addView(
            statusIcon,
            LinearLayout.LayoutParams(dp(45), dp(45))
        )

        mainStatusTitle = label(
            "PERSONAL VPN STATUS:",
            16f,
            white,
            true,
            Gravity.CENTER_VERTICAL
        )

        statusHeading.addView(
            mainStatusTitle,
            LinearLayout.LayoutParams(0, dp(45), 1f)
        )

        statusCard.addView(statusHeading)

        status = label("DISCONNECTED", 29f, warning, true)
        statusCard.addView(status)

        val divider = View(this).apply {
            setBackgroundColor(Color.rgb(0, 100, 145))
        }

        statusCard.addView(
            divider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            )
        )

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        timeRow.addView(
            label("◷", 34f, cyan, true),
            LinearLayout.LayoutParams(dp(65), dp(65))
        )

        val timeBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        timeBox.addView(
            label("CONNECTED TIME:", 13f, white, true, Gravity.START)
        )

        connectionTime = label(
            "00:00:00",
            25f,
            white,
            true,
            Gravity.START
        )

        timeBox.addView(connectionTime)

        timeRow.addView(
            timeBox,
            LinearLayout.LayoutParams(0, dp(70), 1f)
        )

        statusCard.addView(timeRow)
        addCard(root, statusCard)

        val timerCard = card()

        timerCard.addView(
            label("⟳  AUTO-RECONNECT INTERVAL", 17f, cyan, true)
        )

        timerSpinner = Spinner(this)
        timerSpinner.adapter = DarkSpinnerAdapter(this, options)

        timerCard.addView(
            timerSpinner,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(55)
            )
        )

        countdown = label(
            "AUTO-RECONNECT: UNLIMITED",
            13f,
            cyan,
            true
        )

        timerCard.addView(countdown)
        addCard(root, timerCard)

        timerSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
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
                    parent: AdapterView<*>?
                ) = Unit
            }

        connectButton = makeButton(
            "ϟ  CONNECT",
            Color.rgb(0, 120, 30),
            Color.rgb(0, 230, 65),
            green
        )

        connectButton.textSize = 22f

        connectButton.setOnClickListener {
            if (!publicSelected) {
                if (connected) {
                    disconnectVpn()
                } else {
                    requestVpnPermission()
                }
            }
        }

        root.addView(
            connectButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(66)
            ).apply {
                bottomMargin = dp(17)
                topMargin = dp(2)
            }
        )

        val monitorCard = card(green)
        monitorCard.orientation = LinearLayout.HORIZONTAL
        monitorCard.gravity = Gravity.CENTER

        monitorCard.addView(
            label("▮▮▮", 22f, green, true),
            LinearLayout.LayoutParams(dp(65), dp(48))
        )

        monitorText = label(
            "NETWORK MONITOR  //  READY",
            14f,
            green,
            true
        )

        monitorCard.addView(
            monitorText,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        addCard(root, monitorCard)

        root.addView(
            label(
                "Auto IP Rotation for a Stable & Secure Connection",
                12f,
                muted
            )
        )

        root.addView(
            label("SECURE • PRIVATE • CONTROLLED", 10f, cyan, true)
        )

        personalTab.setOnClickListener {
            selectPersonal()
        }

        publicTab.setOnClickListener {
            selectPublic()
        }

        scroll.addView(root)
        setContentView(scroll)

        selectPersonal()
        animateView(root.getChildAt(0))
        animateCreationText(creationLabel)
    }

    private fun selectPersonal() {
        publicSelected = false

        publicPanel.visibility = View.GONE
        personalPanel.visibility = View.VISIBLE
        connectButton.visibility = View.VISIBLE

        personalTab.background = gradient(
            Color.rgb(0, 100, 205),
            Color.rgb(0, 210, 255),
            cyan,
            40
        )

        publicTab.background = gradient(
            Color.rgb(2, 15, 35),
            Color.rgb(2, 15, 35),
            Color.TRANSPARENT,
            40
        )

        mainStatusTitle.text = "PERSONAL VPN STATUS:"
        updateConnectionUI()
    }

    private fun selectPublic() {
        publicSelected = true

        personalPanel.visibility = View.GONE
        publicPanel.visibility = View.VISIBLE
        connectButton.visibility = View.GONE

        publicTab.background = gradient(
            Color.rgb(0, 100, 205),
            Color.rgb(0, 210, 255),
            cyan,
            40
        )

        personalTab.background = gradient(
            Color.rgb(2, 15, 35),
            Color.rgb(2, 15, 35),
            Color.TRANSPARENT,
            40
        )

        mainStatusTitle.text = "PUBLIC VPN STATUS:"
        status.text = "USE PUBLIC VPN PANEL"
        status.setTextColor(cyan)
        statusIcon.setTextColor(cyan)
        connectionTime.text = "00:00:00"
        monitorText.text = "PUBLIC VPN  //  READY"
    }

    private fun updateConnectionUI() {
        if (connected) {
            status.text = "CONNECTED"
            status.setTextColor(green)
            statusIcon.text = "●"
            statusIcon.setTextColor(green)

            connectButton.text = "◉  DISCONNECT"
            connectButton.background = gradient(
                Color.rgb(150, 0, 15),
                Color.rgb(255, 35, 45),
                red,
                40
            )

            monitorText.text = "NETWORK MONITOR  //  CONNECTED"
        } else {
            status.text = "DISCONNECTED"
            status.setTextColor(warning)
            statusIcon.text = "●"
            statusIcon.setTextColor(warning)

            connectButton.text = "ϟ  CONNECT"
            connectButton.background = gradient(
                Color.rgb(0, 120, 30),
                Color.rgb(0, 230, 65),
                green,
                40
            )

            monitorText.text = if (configText.isNullOrBlank()) {
                "NETWORK MONITOR  //  IMPORT CONFIG"
            } else {
                "NETWORK MONITOR  //  READY"
            }
        }

        connectButton.isEnabled =
            connected || !configText.isNullOrBlank()
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

        if (requestCode == PublicVpnScreen.VPN_PERMISSION_REQUEST) {
            publicVpnScreen.onPermissionResult(resultCode == RESULT_OK)
            return
        }

        if (
            requestCode == FILE_REQUEST &&
            resultCode == RESULT_OK
        ) {
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

                        if (!publicSelected) {
                            status.text = "CONFIG READY"
                            status.setTextColor(green)
                            monitorText.text =
                                "NETWORK MONITOR  //  CONFIG READY"

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
        status.text = "CONNECTING..."
        status.setTextColor(warning)
        monitorText.text = "NETWORK MONITOR  //  CONNECTING"

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

                        updateConnectionUI()

                        handler.removeCallbacks(clockTask)
                        handler.post(clockTask)
                    } else {
                        connected = false
                        updateConnectionUI()
                        status.text = "CONNECTION FAILED"
                        status.setTextColor(warning)
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    connected = false
                    updateConnectionUI()

                    status.text = "CONNECTION FAILED"
                    status.setTextColor(warning)

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
                    reconnecting = false

                    handler.removeCallbacks(clockTask)
                    connectionTime.text = "00:00:00"

                    updateConnectionUI()
                    connectButton.isEnabled =
                        !configText.isNullOrBlank()
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

            connectionTime.text = formatTime(elapsed)
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

        status.text = "RECONNECTING..."
        status.setTextColor(warning)
        monitorText.text = "NETWORK MONITOR  //  RECONNECTING"

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

                        updateConnectionUI()
                    } else {
                        connected = false
                        updateConnectionUI()
                        status.text = "RECONNECT FAILED"
                        status.setTextColor(warning)
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    reconnecting = false
                    connected = false

                    updateConnectionUI()
                    status.text = "RECONNECT FAILED"
                    status.setTextColor(warning)
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

        countdown.text = "NEXT RECONNECT: ${formatTime(remaining)}"
    }

    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60

        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            h,
            m,
            s
        )
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        publicVpnScreen.shutdown()
        executor.shutdown()
        super.onDestroy()
    }
}
