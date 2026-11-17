package tv.withaibuild.customiuizer.mods.utils

import android.graphics.drawable.Drawable
import android.util.SparseIntArray
import android.view.View
import android.widget.ImageView

/** Projects two host icon states onto private controller inputs. */
internal class DualRowSignal(stateClass: Class<*>) {
    private val copy = stateClass.getDeclaredMethod("copy").also {
        require(it.returnType == stateClass)
        it.isAccessible = true
    }
    private val strength = field(stateClass, "strengthId", Int::class.javaPrimitiveType!!)
    private val subscription = field(stateClass, "subId", Int::class.javaPrimitiveType!!)
    private val visible = field(stateClass, "visible", Boolean::class.javaPrimitiveType!!)
    private val airplane = field(stateClass, "airplane", Boolean::class.javaPrimitiveType!!)
    private val roaming = field(stateClass, "roaming", Boolean::class.javaPrimitiveType!!)
    private val dataConnected = field(stateClass, "dataConnected", Boolean::class.javaPrimitiveType!!)
    private val activityIn = field(stateClass, "activityIn", Boolean::class.javaPrimitiveType!!)
    private val activityOut = field(stateClass, "activityOut", Boolean::class.javaPrimitiveType!!)
    private val showName = field(stateClass, "showName", String::class.java)

    private fun canMerge(state: Any): Boolean =
        visible.getBoolean(state) && !airplane.getBoolean(state) && !roaming.getBoolean(state)

    fun project(states: List<*>, nativeLevels: SparseIntArray, shouldHide: (Any) -> Boolean = { false }): List<Any>? {
        if (states.size != 2) return null
        val first = states[0] ?: return null
        val second = states[1] ?: return null
        if (first.javaClass != copy.declaringClass || second.javaClass != copy.declaringClass ||
            !canMerge(first) || !canMerge(second)) return null
        val primarySub = subscription.getInt(first)
        val secondarySub = subscription.getInt(second)
        if (primarySub < 0 || secondarySub < 0 || primarySub == secondarySub) return null
        val firstLevel = nativeLevels.get(strength.getInt(first), -1)
        val secondLevel = nativeLevels.get(strength.getInt(second), -1)
        if (firstLevel < 0 || secondLevel < 0 || shouldHide(first) || shouldHide(second)) return null

        val primary = copy.invoke(first) ?: return null
        val secondary = copy.invoke(second) ?: return null
        if (primary === first || primary === second || secondary === first ||
            secondary === second || primary === secondary) return null
        val pair = if (dataConnected.getBoolean(second)) {
            showName.set(primary, showName.get(second))
            activityIn.setBoolean(primary, activityIn.getBoolean(second))
            activityOut.setBoolean(primary, activityOut.getBoolean(second))
            dataConnected.setBoolean(primary, true)
            rowLevel(secondLevel) * LEVEL_COUNT + rowLevel(firstLevel)
        } else {
            rowLevel(firstLevel) * LEVEL_COUNT + rowLevel(secondLevel)
        }
        visible.setBoolean(secondary, false)
        XposedHelpers.setAdditionalInstanceField(primary, LEVELS, pair)
        // Both xaga controllers reverse the list before handing its entries to holders.
        return arrayListOf(primary, secondary)
    }

    companion object {
        const val LEVEL_COUNT = 6
        private const val LEVELS = "a13DualSignalLevels"

        fun levels(state: Any?): Int? =
            state?.let { XposedHelpers.getAdditionalInstanceField(it, LEVELS) as? Int }

        private fun rowLevel(level: Int): Int = if (level == LEVEL_COUNT) 0 else level

        private fun field(owner: Class<*>, name: String, type: Class<*>) =
            XposedHelpers.findField(owner, name).also { require(it.type == type) }
    }
}

/** Attached to the owning mobile view. Restores the native presentation before host writes. */
internal class DualRowSignalOverlay(
    private val primary: ImageView,
    private val secondary: ImageView,
    private val hd: View
) {
    var levels = 0
    private var nativePrimary: Drawable? = null
    private var nativeSecondary: Drawable? = null
    private var nativeSecondaryVisibility = View.GONE
    private var nativeHdVisibility = View.GONE
    private var primaryId = 0
    private var secondaryId = 0

    fun show(primaryDrawable: Int, secondaryDrawable: Int) {
        if (primaryId == 0) {
            nativePrimary = primary.drawable
            nativeSecondary = secondary.drawable
            nativeSecondaryVisibility = secondary.visibility
            nativeHdVisibility = hd.visibility
        }
        if (primaryId != primaryDrawable) {
            primaryId = primaryDrawable
            primary.setImageResource(primaryDrawable)
        }
        if (secondaryId != secondaryDrawable) {
            secondaryId = secondaryDrawable
            secondary.setImageResource(secondaryDrawable)
        }
        if (secondary.visibility != View.VISIBLE) secondary.visibility = View.VISIBLE
        if (hd.visibility != View.GONE) hd.visibility = View.GONE
    }

    fun restore() {
        if (primaryId == 0) return
        primary.setImageDrawable(nativePrimary)
        secondary.setImageDrawable(nativeSecondary)
        secondary.visibility = nativeSecondaryVisibility
        hd.visibility = nativeHdVisibility
        primaryId = 0
        secondaryId = 0
        nativePrimary = null
        nativeSecondary = null
    }
}
