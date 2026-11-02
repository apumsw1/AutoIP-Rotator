
package com.example.autoiprotator

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var engine: VpnEngine
    private lateinit var status: TextView
    private lateinit var connectButton: Button

    private val executor = Executors.newSingleThreadExecutor()
    private var configText: String? = null
    private var connected = false

    companion object {
        private const val VPN_REQUEST = 4101
        private const val FILE_REQUEST = 4102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        engine = VpnEngine(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 24)
            setBackgroundColor(Color.rgb(12, 18, 32))
        }

        fun label(text: String, size: Float, color: Int) =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                setPadding(8, 12, 8, 12)
            }

        val green = Color.rgb(63, 220, 160)
        val muted = Color.rgb(165, 180, 205)

        root.addView(label("AUTOIP ROTATOR", 25f, green))
        root.addView(label("VPN CONTROL CENTER", 14f, muted))

        status = label("● CONFIGURATION NOT IMPORTED", 17f, Color.WHITE)
        root.addView(status)

        val importButton = Button(this).apply {
            text = "IMPORT WIREGUARD .CONF"
            setOnClickListener { openConfigPicker() }
        }
        root.addView(importButton)

        root.addView(label("AUTO ROTATION INTERVAL", 15f, muted))

        val timers = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf(
                    "5 seconds", "10 seconds", "30 seconds",
                    "1 minute", "2 minutes", "5 minutes"
                )
            )
        }
        root.addView(timers)

        connectButton = Button(this).apply {
            text = "CONNECT"
            isEnabled = false
            setOnClickListener {
                if (connected) disconnectVpn()
                else requestPermissionAndConnect()
            }
        }
        root.addView(connectButton)

        root.addView(
            label(
                "Import your WireGuard configuration before connecting.",
                13f, muted
            )
        )

        root.addView(
            label(
                "Automatic IP rotation is not active yet.",
                12f, green
            )
        )

        setContentView(root)
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
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FILE_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return

            try {
                val flags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Some file providers do not support persistent access.
            }

            status.text = "● READING CONFIGURATION..."

            executor.execute {
                try {
                    val imported = contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: throw Exception("Could not read file.")

                    require(
                        imported.contains("[Interface]") &&
                        imported.contains("[Peer]")
                    ) {
                        "Invalid WireGuard configuration."
                    }

                    runOnUiThread {
                        configText = imported
                        status.text = "● CONFIGURATION IMPORTED"
                        status.setTextColor(greenColor())
                        connectButton.isEnabled = true
                        Toast.makeText(
                            this, "Configuration imported",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        status.text = "● IMPORT FAILED"
                        status.setTextColor(Color.YELLOW)
                        Toast.makeText(
                            this,
                            e.message ?: "Import failed",
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
                status.text = "● VPN PERMISSION DENIED"
                status.setTextColor(Color.YELLOW)
            }
        }
    }

    private fun greenColor() = Color.rgb(63, 220, 160)

    private fun requestPermissionAndConnect() {
        if (configText.isNullOrBlank()) {
            Toast.makeText(
                this, "Import a .conf file first",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val permission = VpnService.prepare(this)

        if (permission != null) {
            startActivityForResult(permission, VPN_REQUEST)
        } else {
            connectVpn()
        }
    }

    private fun connectVpn() {
        val config = configText ?: return

        connectButton.isEnabled = false
        status.text = "● CONNECTING..."
        status.setTextColor(Color.YELLOW)

        executor.execute {
            try {
                engine.connect(config)

                runOnUiThread {
                    connected = true
                    status.text = "● CONNECTED"
                    status.setTextColor(greenColor())
                    connectButton.text = "DISCONNECT"
                    connectButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    connected = false
                    status.text = "● CONNECTION FAILED"
                    status.setTextColor(Color.YELLOW)
                    connectButton.text = "CONNECT"
                    connectButton.isEnabled = true

                    Toast.makeText(
                        this,
                        e.message ?: "VPN connection failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun disconnectVpn() {
        connectButton.isEnabled = false
        status.text = "● DISCONNECTING..."

        executor.execute {
            try {
                engine.disconnect()

                runOnUiThread {
                    connected = false
                    status.text = "● DISCONNECTED"
                    status.setTextColor(Color.WHITE)
                    connectButton.text = "CONNECT"
                    connectButton.isEnabled = !configText.isNullOrBlank()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "● DISCONNECT FAILED"
                    status.setTextColor(Color.YELLOW)
                    connectButton.isEnabled = true
                    Toast.makeText(
                        this,
                        e.message ?: "Disconnect failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
