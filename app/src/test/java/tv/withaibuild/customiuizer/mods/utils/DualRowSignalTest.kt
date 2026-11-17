package tv.withaibuild.customiuizer.mods.utils

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.SparseIntArray
import android.view.View
import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.FakeContext
import java.util.Collections

class DualRowSignalTest {
    private val levels = SparseIntArray().apply {
        put(ZERO, 0)
        put(TWO, 2)
        put(FOUR, 4)
        put(NO_SERVICE, 6)
    }
    private val signal = DualRowSignal(Icon::class.java)

    @Test
    fun controllerReversesOnlyItsCopiesAndNativeResourceIdsSurvive() {
        val first = Icon(1, TWO, showName = "first")
        val second = Icon(2, FOUR, dataConnected = true, activityIn = true, showName = "data SIM")
        val input = listOf(first, second)
        val projected = signal.project(input, levels) as MutableList<*>
        val main = projected[0] as Icon
        val hidden = projected[1] as Icon

        assertNotSame(first, main)
        assertNotSame(second, hidden)
        assertEquals(TWO, main.strengthId)
        assertEquals(FOUR, hidden.strengthId)
        assertEquals(4 * 6 + 2, DualRowSignal.levels(main))
        assertEquals("data SIM", main.showName)
        assertTrue(main.activityIn)
        assertFalse(hidden.visible)
        Collections.reverse(projected)
        assertSame(main, projected[1])
        assertSame(first, input[0])
        assertEquals("first", first.showName)
        assertFalse(first.activityIn)
        assertTrue(second.visible)
        assertNull(DualRowSignal.levels(first))
        assertNull(DualRowSignal.levels(main.copy()))
    }

    @Test
    fun zeroBarsAreValidAndNoServiceUsesTheZeroBarAsset() {
        val input = listOf(Icon(1, ZERO), Icon(2, NO_SERVICE))
        val first = signal.project(input, levels)!![0] as Icon
        assertEquals(0, DualRowSignal.levels(first))
        assertEquals(ZERO, first.strengthId)
        val changed = listOf(Icon(1, ZERO), Icon(2, FOUR))
        assertEquals(4, DualRowSignal.levels(signal.project(changed, levels)!![0]))
    }

    @Test
    fun unavailableOrNativeOnlyStatesDoNotHideEitherSlot() {
        val first = Icon(1, TWO)
        for (second in listOf(
            Icon(2, FOUR, visible = false),
            Icon(2, FOUR, airplane = true),
            Icon(2, FOUR, roaming = true),
            Icon(2, 0),
            Icon(2, 123),
            Icon(1, FOUR),
            Icon(-1, FOUR)
        )) {
            val visible = second.visible
            assertNull(signal.project(listOf(first, second), levels))
            assertEquals(visible, second.visible)
        }
        assertNull(signal.project(listOf(first), levels))
        first.airplane = true
        assertNull(signal.project(listOf(first, Icon(2, FOUR)), levels))
        assertTrue(first.visible)
    }

    @Test
    fun repeatedDrawsSkipImageWritesAndRestoreNativePresentationAcrossUpdates() {
        val primary = Image()
        val roaming = Image()
        val hd = Image()
        val nativePrimary = ColorDrawable(1)
        val nativeRoaming = ColorDrawable(2)
        primary.setImageDrawable(nativePrimary)
        roaming.setImageDrawable(nativeRoaming)
        roaming.visibility = View.GONE
        hd.visibility = View.VISIBLE
        val overlay = DualRowSignalOverlay(primary, roaming, hd)

        overlay.show(11, 21)
        overlay.show(11, 21)
        overlay.show(11, 22)
        assertEquals(listOf(11), primary.writes)
        assertEquals(listOf(21, 22), roaming.writes)
        assertEquals(View.VISIBLE, roaming.visibility)
        assertEquals(View.GONE, hd.visibility)

        overlay.restore()
        assertSame(nativePrimary, primary.drawable)
        assertSame(nativeRoaming, roaming.drawable)
        assertEquals(View.GONE, roaming.visibility)
        assertEquals(View.VISIBLE, hd.visibility)

        val recolored = ColorDrawable(3)
        roaming.setImageDrawable(recolored)
        roaming.visibility = View.VISIBLE
        overlay.show(13, 23)
        overlay.restore()
        assertSame(recolored, roaming.drawable)
        assertEquals(View.VISIBLE, roaming.visibility)
    }

    private class Image : ImageView(FakeContext()) {
        val writes = mutableListOf<Int>()
        private var current: Drawable? = null
        private var visibilityState = View.VISIBLE

        override fun getDrawable() = current
        override fun getVisibility() = visibilityState
        override fun setVisibility(visibility: Int) { visibilityState = visibility }
        override fun setImageDrawable(drawable: Drawable?) { current = drawable }
        override fun setImageResource(resId: Int) {
            writes.add(resId)
            current = ColorDrawable(resId)
        }
    }

    private class Icon(
        @JvmField val subId: Int,
        @JvmField var strengthId: Int,
        @JvmField var visible: Boolean = true,
        @JvmField var airplane: Boolean = false,
        @JvmField var roaming: Boolean = false,
        @JvmField var dataConnected: Boolean = false,
        @JvmField var activityIn: Boolean = false,
        @JvmField var activityOut: Boolean = false,
        @JvmField var showName: String? = null
    ) {
        fun copy() = Icon(subId, strengthId, visible, airplane, roaming, dataConnected, activityIn, activityOut, showName)
    }

    companion object {
        private const val ZERO = 0x7f080001
        private const val TWO = 0x7f080002
        private const val FOUR = 0x7f080004
        private const val NO_SERVICE = 0x7f080006
    }
}
