package android.animation

// Functional duration storage for callback tests; this does not simulate rendering.
class ObjectAnimator : ValueAnimator() {
    private var millis = 0L
    override fun setDuration(duration: Long): ObjectAnimator { millis = duration; return this }
    override fun getDuration(): Long = millis

    companion object {
        @JvmStatic fun ofInt(target: Any?, propertyName: String?, vararg values: Int): ObjectAnimator = ObjectAnimator()
        @JvmStatic fun ofArgb(target: Any?, propertyName: String?, vararg values: Int): ObjectAnimator = ObjectAnimator()
    }
}
