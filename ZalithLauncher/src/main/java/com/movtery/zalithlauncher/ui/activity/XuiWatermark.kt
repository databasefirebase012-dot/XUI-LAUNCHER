package com.movtery.zalithlauncher.ui.activity

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/** Adds a subtle, non-interactive XUI community-build watermark to launcher screens. */
object XuiWatermark {
    private const val TAG = "xui_launcher_watermark"

    @JvmStatic
    fun attach(activity: Activity) {
        val root = activity.findViewById<View>(android.R.id.content) as? FrameLayout ?: return
        for (index in root.childCount - 1 downTo 0) {
            if (root.getChildAt(index).tag == TAG) root.removeViewAt(index)
        }

        val label = TextView(activity).apply {
            tag = TAG
            text = "XUI LAUNCHER BY XRANS OFFICIAL"
            textSize = 10f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.04f
            setTextColor(Color.argb(64, 255, 255, 255))
            setPadding(dp(activity, 8), dp(activity, 3), dp(activity, 8), dp(activity, 3))
            rotation = -15f
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            elevation = dp(activity, 2).toFloat()
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = dp(activity, 90)
        }
        root.addView(label, params)
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density + 0.5f).toInt()
}