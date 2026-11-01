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

import com.movtery.zalithlauncher.R;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Applies clear frosted-glass surfaces to launcher pages without changing their behavior. */
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

    private static boolean isSupportedParent(ViewGroup parent) {
        return parent instanceof ConstraintLayout
                || parent instanceof RelativeLayout
                || parent instanceof FrameLayout
                || parent instanceof DrawerLayout;
    }

    private static boolean hasVisibleBackground(View view) {
        Drawable background = view.getBackground();
        return background != null && (!(background instanceof ColorDrawable)
                || ((ColorDrawable) background).getColor() != Color.TRANSPARENT);
    }

    private static void bindChildren(ViewGroup parent, BlurTarget target) {
        if (isTabLayout(parent) || parent instanceof BlurTarget || parent instanceof BlurView) {
            return;
        }

        int count = parent.getChildCount();
        View[] children = new View[count];
        for (int i = 0; i < count; i++) {
            children[i] = parent.getChildAt(i);
        }

        for (View child : children) {
            if (child instanceof ViewGroup && !isTabLayout(child)
                    && !(child instanceof BlurTarget) && !(child instanceof BlurView)) {
                bindChildren((ViewGroup) child, target);
            }
        }

        // Add a separate glass layer behind clickable leaf controls on safe parent types.
        // This does not replace listeners, IDs, layout constraints, or ripple handling.
        if (isSupportedParent(parent)) {
            bindClickableChildren(parent, children, target);
        }

        if (BOUND.contains(parent) || parent.getChildCount() == 0 || !isSupportedParent(parent)) {
            return;
        }

        if (!hasVisibleBackground(parent)) {
            return;
        }

        BlurView blur = createGlassView(parent, target);
        ViewGroup.LayoutParams params = createFullSizeParams(parent);
        parent.setBackgroundColor(Color.TRANSPARENT);
        parent.addView(blur, 0, params);
        BOUND.add(parent);
    }

    private static void bindClickableChildren(ViewGroup parent, View[] children, BlurTarget target) {
        for (View child : children) {
            if (child == null || child instanceof ViewGroup || child instanceof BlurView
                    || !child.isClickable() || BOUND.contains(child)
                    || !hasVisibleBackground(child)) {
                continue;
            }

            ViewGroup.LayoutParams original = child.getLayoutParams();
            ViewGroup.LayoutParams params = copyLayoutParams(parent, original);
            if (params == null) {
                continue;
            }

            int index = parent.indexOfChild(child);
            if (index < 0) {
                continue;
            }

            BlurView blur = createGlassView(parent, target);
            parent.addView(blur, index, params);
            BOUND.add(child);
        }
    }

    private static BlurView createGlassView(View view, BlurTarget target) {
        BlurView blur = new BlurView(view.getContext());
        // Always use the clear XUI glass surface; never copy an opaque/dark card layer.
        blur.setBackgroundResource(R.drawable.xui_bg_glass);
        blur.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        blur.setClipToOutline(true);
        blur.setClickable(false);
        blur.setFocusable(false);
        blur.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        blur.setupWith(target)
                .setBlurRadius(24f)
                .setOverlayColor(Color.argb(8, 255, 255, 255));
        return blur;
    }

    private static ViewGroup.LayoutParams createFullSizeParams(ViewGroup parent) {
        if (parent instanceof ConstraintLayout) {
            ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(-1, -1);
            lp.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            return lp;
        } else if (parent instanceof RelativeLayout) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(-1, -1);
            lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            lp.addRule(RelativeLayout.ALIGN_PARENT_START);
            lp.addRule(RelativeLayout.ALIGN_PARENT_END);
            return lp;
        } else if (parent instanceof DrawerLayout) {
            DrawerLayout.LayoutParams lp = new DrawerLayout.LayoutParams(-1, -1);
            lp.gravity = Gravity.NO_GRAVITY;
            return lp;
        }
        return new FrameLayout.LayoutParams(-1, -1, Gravity.TOP | Gravity.START);
    }

    private static ViewGroup.LayoutParams copyLayoutParams(
            ViewGroup parent, ViewGroup.LayoutParams original) {
        if (original == null) {
            return null;
        }
        if (parent instanceof ConstraintLayout && original instanceof ConstraintLayout.LayoutParams) {
            return new ConstraintLayout.LayoutParams((ConstraintLayout.LayoutParams) original);
        }
        if (parent instanceof RelativeLayout && original instanceof RelativeLayout.LayoutParams) {
            return new RelativeLayout.LayoutParams((RelativeLayout.LayoutParams) original);
        }
        if (parent instanceof FrameLayout && original instanceof FrameLayout.LayoutParams) {
            return new FrameLayout.LayoutParams((FrameLayout.LayoutParams) original);
        }
        if (parent instanceof DrawerLayout && original instanceof DrawerLayout.LayoutParams) {
            return new DrawerLayout.LayoutParams((DrawerLayout.LayoutParams) original);
        }
        return null;
    }
}
