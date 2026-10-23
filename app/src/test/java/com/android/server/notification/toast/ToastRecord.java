package com.android.server.notification.toast;

public class ToastRecord {
    private final int mDuration;
    public ToastRecord(int duration) { mDuration = duration; }
    public int getDuration() { return mDuration; }
}
