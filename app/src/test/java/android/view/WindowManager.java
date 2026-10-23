package android.view;

/** Only the window attributes used by the ROM-proven Toast path are modeled here. */
public interface WindowManager extends ViewManager {
    class LayoutParams extends ViewGroup.LayoutParams {
        public static final int TYPE_APPLICATION = 2;
        public static final int TYPE_TOAST = 2005;
        public static final int TYPE_APPLICATION_OVERLAY = 2038;
        public int type;
        public int flags;
        public long hideTimeoutMilliseconds = -1;

        public LayoutParams() { super(0, 0); }
    }
}
