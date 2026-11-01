package com.movtery.zalithlauncher.utils.xui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.drawerlayout.widget.DrawerLayout;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Adds frosted-glass layers to supported XUI panels without modifying tab internals. */
public final class XuiGlassBinder {
    private static final Set<View> BOUND =
            Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private XuiGlassBinder() {
    }

    public static void bind(View root, BlurTarget target) {
        if (root == null || target == null || !(root instanceof ViewGroup)) {
            return;
        }
        bindChildren((ViewGroup) root, target);
    }

    /**
     * TabLayout owns its child hierarchy and enforces TabItem-only insertion.
     * Never traverse into it or add a BlurView to any of its internal views.
     */
    private static boolean isTabLayout(View view) {
        Class<?> type = view.getClass();
        while (type != null) {
            String name = type.getName();
            if ("com.google.android.material.tabs.TabLayout".equals(name)
                    || name.endsWith(".DslTabLayout")
                    || name.endsWith(".TabLayout")) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static void bindChildren(ViewGroup parent, BlurTarget target) {
        if (isTabLayout(parent) || parent instanceof BlurTarget || parent instanceof BlurView) {
            return;
        }

        int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup && !isTabLayout(child)
                    && !(child instanceof BlurTarget) && !(child instanceof BlurView)) {
                bindChildren((ViewGroup) child, target);
            }
        }

        if (BOUND.contains(parent) || parent.getChildCount() == 0) {
            return;
        }

        boolean supported = parent instanceof ConstraintLayout
                || parent instanceof RelativeLayout
                || parent instanceof FrameLayout
                || parent instanceof DrawerLayout;
        if (!supported) {
            return;
        }

        Drawable surface = parent.getBackground();
        if (surface == null || (surface instanceof ColorDrawable
                && ((ColorDrawable) surface).getColor() == Color.TRANSPARENT)) {
            return;
        }

        Drawable glass = surface.getConstantState() != null
                ? surface.getConstantState().newDrawable(parent.getResources()).mutate()
                : surface.mutate();

        BlurView blur = new BlurView(parent.getContext());
        blur.setBackground(glass);
        blur.setClickable(false);
        blur.setFocusable(false);
        blur.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        blur.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        blur.setClipToOutline(true);

        ViewGroup.LayoutParams params;
        if (parent instanceof ConstraintLayout) {
            ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(-1, -1);
            lp.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            params = lp;
        } else if (parent instanceof RelativeLayout) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(-1, -1);
            lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            lp.addRule(RelativeLayout.ALIGN_PARENT_START);
            lp.addRule(RelativeLayout.ALIGN_PARENT_END);
            params = lp;
        } else if (parent instanceof DrawerLayout) {
            DrawerLayout.LayoutParams lp = new DrawerLayout.LayoutParams(-1, -1);
            lp.gravity = Gravity.NO_GRAVITY;
            params = lp;
        } else {
            params = new FrameLayout.LayoutParams(-1, -1, Gravity.TOP | Gravity.START);
        }

        parent.setBackgroundColor(Color.TRANSPARENT);
        parent.addView(blur, 0, params);
        BOUND.add(parent);
        blur.setupWith(target)
                .setBlurRadius(42f)
                .setOverlayColor(Color.argb(12, 255, 255, 255));
    }
}
