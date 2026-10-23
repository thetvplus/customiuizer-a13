package com.android.server.notification;

/**
 * Test stub for NotificationManagerService used by SelectiveToasts hook tests.
 */
public class NotificationManagerService {
    public android.view.accessibility.AccessibilityManager mAccessibilityManager =
        new android.view.accessibility.AccessibilityManager();
    public java.util.function.BiFunction<Integer, Integer, Integer> timeoutCalculator =
        (base, flags) -> mAccessibilityManager.getRecommendedTimeoutMillis(base, flags);
    public int scheduleCalls;
    public long scheduledDelay;
    public Object scheduledRecord;
    public boolean previousWasText;
    public Runnable duringSchedule;
    public RuntimeException scheduleFailure;

    // The two A13 ROMs use the same base -> accessibility -> animation ordering.
    private void scheduleDurationReachedLocked(com.android.server.notification.toast.ToastRecord record, boolean wasText) {
        scheduleCalls++;
        scheduledRecord = record;
        previousWasText = wasText;
        if (duringSchedule != null) duringSchedule.run();
        if (scheduleFailure != null) throw scheduleFailure;
        int delay = timeoutCalculator.apply(record.getDuration() == 1 ? 3500 : 2000, 2);
        if (wasText) delay += 250;
        if (record instanceof com.android.server.notification.toast.TextToastRecord) delay += 333;
        scheduledDelay = delay;
    }

    public boolean tryShowToast(Object record) {
        return false;
    }
}
