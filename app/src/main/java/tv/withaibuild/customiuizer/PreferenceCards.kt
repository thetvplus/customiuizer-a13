package tv.withaibuild.customiuizer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import tv.withaibuild.customiuizer.prefs.PreferenceCategoryEx

/** Visible adapter rows are authoritative, including dynamically hidden preferences. */
internal object PreferenceCards {
    data class Row(val header: Boolean = false, val hidden: Boolean = false)
    data class Shape(val header: Boolean, val hidden: Boolean, val first: Boolean, val last: Boolean)

    fun layout(rows: List<Row>): List<Shape> = rows.mapIndexed { index, row ->
        Shape(row.header, row.hidden,
            !row.header && (index == 0 || rows[index - 1].header),
            !row.header && (index == rows.lastIndex || rows[index + 1].header))
    }
}

/** One decoration owned by the fragment's view; no extra row containers or runtime hooks. */
internal class PreferenceCardDecoration(private val screen: () -> PreferenceScreen?) : RecyclerView.ItemDecoration() {
    private var adapter: RecyclerView.Adapter<*>? = null
    private var cachedScreen: PreferenceScreen? = null
    private var shapes: List<PreferenceCards.Shape>? = null
    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = invalidate()
        override fun onItemRangeChanged(start: Int, count: Int) = invalidate()
        override fun onItemRangeChanged(start: Int, count: Int, payload: Any?) = invalidate()
        override fun onItemRangeInserted(start: Int, count: Int) = invalidate()
        override fun onItemRangeRemoved(start: Int, count: Int) = invalidate()
        override fun onItemRangeMoved(from: Int, to: Int, count: Int) = invalidate()
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val corners = FloatArray(8)
    private var inset = 0
    private var gap = 0
    private var radius = 0f
    private var fill = 0
    private var divider = 0
    private var ready = false

    private fun invalidate() { shapes = null; cachedScreen = null }
    fun dispose() {
        adapter?.unregisterAdapterDataObserver(observer)
        adapter = null
        invalidate()
    }

    private fun rows(parent: RecyclerView): List<PreferenceCards.Shape> {
        val current = parent.adapter
        val positions = current as? PreferenceGroup.PreferencePositionCallback
        val root = screen()
        if (current == null || positions == null || root == null) { dispose(); return emptyList() }
        if (adapter !== current) {
            dispose()
            adapter = current
            current.registerAdapterDataObserver(observer)
        }
        shapes?.let { if (cachedScreen === root && it.size == current.itemCount) return it }
        val inputs = MutableList(current.itemCount) { PreferenceCards.Row() }
        fun collect(group: PreferenceGroup) {
            for (i in 0 until group.preferenceCount) {
                val pref = group.getPreference(i)
                if (pref is PreferenceCategory) {
                    val position = positions.getPreferenceAdapterPosition(pref)
                    if (position in inputs.indices) inputs[position] = PreferenceCards.Row(
                        header = true, hidden = (pref as? PreferenceCategoryEx)?.isTitleVisible() == false)
                }
                if (pref is PreferenceGroup && pref !is PreferenceScreen) collect(pref)
            }
        }
        collect(root)
        cachedScreen = root
        return PreferenceCards.layout(inputs).also { shapes = it }
    }

    private fun prepare(parent: RecyclerView) {
        if (ready) return
        val res = parent.resources
        inset = res.getDimensionPixelSize(R.dimen.preference_card_inset)
        gap = res.getDimensionPixelSize(R.dimen.preference_card_gap)
        radius = res.getDimension(R.dimen.preference_card_radius)
        fill = parent.context.getColor(R.color.preference_card_background)
        divider = parent.context.getColor(R.color.preference_card_divider)
        ready = true
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        prepare(parent)
        val position = parent.getChildAdapterPosition(view)
        val snapshot = rows(parent)
        val row = snapshot.getOrNull(position)
        if (row == null || row.hidden) { outRect.setEmpty(); return }
        val top = if (row.header || position == 0 || row.first && snapshot.getOrNull(position - 1)?.hidden == true) gap else 0
        outRect.set(inset, top, inset, if (position == snapshot.lastIndex) gap else 0)
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        prepare(parent)
        val snapshot = rows(parent)
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val row = snapshot.getOrNull(parent.getChildAdapterPosition(child)) ?: continue
            if (row.header) continue
            rect.set(child.left.toFloat(), child.top + child.translationY, child.right.toFloat(), child.bottom + child.translationY)
            val top = if (row.first) radius else 0f
            val bottom = if (row.last) radius else 0f
            corners[0] = top; corners[1] = top; corners[2] = top; corners[3] = top
            corners[4] = bottom; corners[5] = bottom; corners[6] = bottom; corners[7] = bottom
            path.reset()
            path.addRoundRect(rect, corners, Path.Direction.CW)
            paint.color = fill
            canvas.drawPath(path, paint)
            if (!row.last) {
                paint.color = divider
                canvas.drawRect(rect.left + inset, rect.bottom - parent.resources.displayMetrics.density,
                    rect.right - inset, rect.bottom, paint)
            }
        }
    }
}
