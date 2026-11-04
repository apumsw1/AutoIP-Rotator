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

class PublicVpnScreen(
    private val context: Context
) {

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

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun background(
        color: Int,
        stroke: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), stroke)
        }
    }

    private fun createLabel(
        textValue: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(context).apply {
            text = textValue
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

    private fun createButton(
        title: String,
        color: Int,
        stroke: Int
    ): Button {
        return Button(context).apply {
            text = title
            textSize = 17f
            isAllCaps = false
            ty