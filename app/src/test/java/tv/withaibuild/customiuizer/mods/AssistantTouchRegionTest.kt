package tv.withaibuild.customiuizer.mods

import org.junit.Assert.*
import org.junit.Test

class AssistantTouchRegionTest {
    @Test fun nextGestureDiscardsOldLayoutAndAcceptsReplacementBounds() {
        val region = LauncherGestureHooks.AssistantTouchRegion()
        region.reset(100)
        region.update(0, 600, 400, 700)
        assertTrue(region.contains(200, 650))
        region.reset(200)
        assertFalse(region.contains(200, 650))
        region.update(0, 300, 800, 400)
        assertTrue(region.contains(700, 350))
        assertFalse(region.contains(200, 650))
        assertEquals(200, region.downTime)
    }

    @Test fun emptyOrFailedRefreshLeavesNativeInterceptionUntouched() {
        val region = LauncherGestureHooks.AssistantTouchRegion()
        assertFalse(region.contains(0, 0))
        region.update(0, 100, 400, 200)
        region.reset(300)
        assertFalse(region.contains(200, 150))
    }

    @Test fun controllerInstancesAndRightBottomEdgesStayIndependent() {
        val first = LauncherGestureHooks.AssistantTouchRegion()
        val second = LauncherGestureHooks.AssistantTouchRegion()
        first.reset(100); second.reset(100)
        first.update(10, 20, 30, 40)
        assertTrue(first.contains(10, 20))
        assertFalse(first.contains(30, 20))
        assertFalse(first.contains(10, 40))
        assertFalse(second.contains(10, 20))
    }
}
