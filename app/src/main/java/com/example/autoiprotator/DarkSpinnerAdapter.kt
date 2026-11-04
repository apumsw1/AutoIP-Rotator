package com.example.autoiprotator

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class DarkSpinnerAdapter(
    context: Context,
    items: Array<String>
) : ArrayAdapter<String>(
    context,
    android.R.layout.simple_spinner_item,
    items
) {

    private val textColor = Color.rgb(0, 235, 255)
    private val backgroundColor = Color.rgb(7, 17, 30)

    init {
        setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )
    }

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {
        return createStyledView(position, convertView, parent, false)
    }

    override fun getDropDownView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {
        return createStyledView(position, convertView, parent, true)
    }

    private fun createStyledView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
        dropdown: Boolean
    ): View {
        val textView = (convertView as? TextView)
            ?: TextView(context)

        textView.text = getItem(position) ?: ""
        textView.setTextColor(textColor)
        textView.textSize = if (dropdown) 16f else 17f
        textView.typeface = Typeface.DEFAULT_BOLD
        textView.gravity = Gravity.CENTER_VERTICAL
        textView.setPadding(24, 14, 24, 14)

        if (dropdown) {
            textView.background = GradientDrawable().apply {
                setColor(backgroundColor)
            }
        } else {
            textView.background = null
        }

        textView.isSingleLine = true

        return textView
    }
}