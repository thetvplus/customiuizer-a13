package com.android.server.wm;

import android.content.Context;
import android.view.DisplayCutout;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;

public class DisplayPolicy {
    public Context mUiContext;
    public final DisplayContent mDisplayContent = new DisplayContent();

    public int getStatusBarHeightForRotation(int rotation) {
        return 75;
    }

    public static class DisplayContent {
        public int lastRotation = -1;

        public WmDisplayCutout calculateDisplayCutoutForRotation(int rotation) {
            lastRotation = rotation;
            return new WmDisplayCutout();
        }
    }

    public static class WmDisplayCutout {
        public DisplayCutout getDisplayCutout() {
            return null;
        }
    }

    public AccessibilityManager mAccessibilityManager = new AccessibilityManager();
    public java.util.function.BiFunction<Integer, Integer, Integer> timeoutCalculator =
        (base, flags) -> mAccessibilityManager.getRecommendedTimeoutMillis(base, flags);
    public int adjustCalls;
    public WindowState lastWindow;
    public boolean remainingNativePolicyRan;

    // Reproduces the toast branch only. Other window policy is represented by the final marker.
    public void adjustWindowParamsLw(WindowState window, WindowManager.LayoutParams attributes) {
        adjustCalls++;
        lastWindow = window;
        if (attributes.type == WindowManager.LayoutParams.TYPE_TOAST) {
            if (attributes.hideTimeoutMilliseconds < 0 || attributes.hideTimeoutMilliseconds > 4100) {
                attributes.hideTimeoutMilliseconds = 4100;
            }
            attributes.hideTimeoutMilliseconds = timeoutCalculator.apply((int) attributes.hideTimeoutMilliseconds, 2);
            attributes.flags |= 0x10;
        }
        remainingNativePolicyRan = true;
    }
}
