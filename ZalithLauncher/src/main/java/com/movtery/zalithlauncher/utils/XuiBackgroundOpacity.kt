package com.movtery.zalithlauncher.utils

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.content.ContextCompat
import com.movtery.zalithlauncher.R

/**
 * Adjusts the fill alpha of XUI dark/red card and button shapes while leaving
 * their stroke/border alpha untouched. Image/video backgrounds are not touched.
 */
object XuiBackgroundOpacity {
    @JvmStatic
    fun apply(root: View, progress: Int, context: Context) {
        val opacity = (progress.coerceIn(0, 100) * 255) / 100
        val targetRgb = setOf(
            ContextCompat.getColor(context, R.color.xui_bg_card) and 0x00FFFFFF,
            ContextCompat.getColor(context, R.color.xui_bg_card_alt) and 0x00FFFFFF,
            ContextCompat.getColor(context, R.color.xui_primary) and 0x00FFFFFF,
            ContextCompat.getColor(context, R.color.xui_primary_dark) and 0x00FFFFFF
        )
        applyToView(root, targetRgb, opacity)
    }

    private fun applyToView(view: View, targetRgb: Set<Int>, opacity: Int) {
        val shape = view.background?.mutate() as? GradientDrawable
        val color = shape?.color?.defaultColor
        if (shape != null && color != null && (color and 0x00FFFFFF) in targetRgb) {
            shape.setColor(ColorUtils.setAlphaComponent(color, opacity))
            view.background = shape
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyToView(view.getChildAt(index), targetRgb, opacity)
            }
        }
    }
}
