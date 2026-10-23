package com.android.server.wm;

import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;

public class DisplayPolicy {
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
