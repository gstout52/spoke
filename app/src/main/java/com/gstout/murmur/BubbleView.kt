package com.gstout.murmur

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar

/** The floating mic button. */
class BubbleView(context: Context) : FrameLayout(context) {

    enum class Mode { IDLE, RECORDING, PROCESSING }

    private val background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private val icon = ImageView(context)
    private val spinner = ProgressBar(context).apply {
        isIndeterminate = true
        indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
    }
    private val pulse = ObjectAnimator.ofPropertyValuesHolder(
        this,
        PropertyValuesHolder.ofFloat(SCALE_X, 1f, 1.12f),
        PropertyValuesHolder.ofFloat(SCALE_Y, 1f, 1.12f),
    ).apply {
        duration = 600
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
    }

    init {
        setBackground(background)
        elevation = dp(6).toFloat()
        val iconSize = dp(26)
        addView(icon, LayoutParams(iconSize, iconSize, Gravity.CENTER))
        val spinnerSize = dp(28)
        addView(spinner, LayoutParams(spinnerSize, spinnerSize, Gravity.CENTER))
        contentDescription = "Dictate"
        setMode(Mode.IDLE)
    }

    fun setMode(mode: Mode) {
        when (mode) {
            Mode.IDLE -> {
                background.setColor(Color.parseColor("#E6303F9F"))
                icon.setImageResource(R.drawable.ic_mic)
                icon.visibility = VISIBLE
                spinner.visibility = GONE
                stopPulse()
            }
            Mode.RECORDING -> {
                background.setColor(Color.parseColor("#F2E53935"))
                icon.setImageResource(R.drawable.ic_stop)
                icon.visibility = VISIBLE
                spinner.visibility = GONE
                pulse.start()
            }
            Mode.PROCESSING -> {
                background.setColor(Color.parseColor("#E6303F9F"))
                icon.visibility = GONE
                spinner.visibility = VISIBLE
                stopPulse()
            }
        }
    }

    private fun stopPulse() {
        pulse.cancel()
        scaleX = 1f
        scaleY = 1f
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
