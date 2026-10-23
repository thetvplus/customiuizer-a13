package android.view.accessibility;

/** JVM model of the ROM's cached non-interactive timeout minimum; no Binder or device claims. */
public class AccessibilityManager {
    public static final int FLAG_CONTENT_TEXT = 2;
    public int minimum;
    public int calls;
    public int lastBase;
    public int lastFlags;

    public int getRecommendedTimeoutMillis(int originalTimeout, int flags) {
        calls++;
        lastBase = originalTimeout;
        lastFlags = flags;
        return (flags & 3) != 0 ? Math.max(originalTimeout, minimum) : originalTimeout;
    }
}
