package com.movtery.zalithlauncher.ui.activity

import android.app.Activity
import android.graphics.Color
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
            text = "XUI • COMMUNITY BUILD"
            textSize = 10f
            letterSpacing = 0.06f
            setTextColor(Color.argb(105, 255, 48, 72))
            setPadding(dp(activity, 7), dp(activity, 3), dp(activity, 7), dp(activity, 3))
            rotation = -12f
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            elevation = dp(activity, 2).toFloat()
        }
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = dp(activity, 12)
            bottomMargin = dp(activity, 8)
        }
        root.addView(label, params)
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density + 0.5f).toInt()
}
