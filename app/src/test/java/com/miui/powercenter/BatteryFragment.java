package com.miui.powercenter;

/** Present so D1 can hide BatteryFragment without affecting later features. */
public class BatteryFragment {
    public static class NonHandler extends java.lang.ref.WeakReference<Object> {
        public NonHandler() { super(null); }
    }
    /** ROM-derived handler shape only; no Android rendering simulation. */
    public static class BatteryHandler extends android.os.Handler {
        public final java.lang.ref.WeakReference<Object> owner = new java.lang.ref.WeakReference<>(null);
        public int calls;
        public RuntimeException failure;
        @Override public void handleMessage(android.os.Message msg) {
            calls++;
            if (failure != null) throw failure;
        }
    }
}
