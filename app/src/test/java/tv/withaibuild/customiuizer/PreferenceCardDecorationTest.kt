package tv.withaibuild.customiuizer

import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe
import tv.withaibuild.customiuizer.prefs.PreferenceCategoryEx

/** Production row cache and observer ownership, without pretending to render Android Views. */
class PreferenceCardDecorationTest {
    private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
        isAccessible = true; get(null) as Unsafe
    }
    private fun <T> allocate(type: Class<T>): T = type.cast(unsafe.allocateInstance(type))
    private fun group(group: PreferenceGroup, children: List<Preference>) {
        PreferenceGroup::class.java.getDeclaredField("mPreferences").apply { isAccessible = true }
            .set(group, ArrayList(children))
    }
    private class Adapter(var rows: List<Preference>) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        PreferenceGroup.PreferencePositionCallback {
        val observers = ArrayList<RecyclerView.AdapterDataObserver>()
        override fun registerAdapterDataObserver(observer: RecyclerView.AdapterDataObserver) { observers.add(observer) }
        override fun unregisterAdapterDataObserver(observer: RecyclerView.AdapterDataObserver) { assertTrue(observers.remove(observer)) }
        override fun getItemCount() = rows.size
        override fun getPreferenceAdapterPosition(preference: Preference) = rows.indexOfFirst { it === preference }
        override fun getPreferenceAdapterPosition(key: String) = -1
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder = error("not a rendering test")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    }
    private fun setAdapter(list: RecyclerView, adapter: Adapter) {
        RecyclerView::class.java.getDeclaredField("mAdapter").apply { isAccessible = true }.set(list, adapter)
    }
    @Suppress("UNCHECKED_CAST")
    private fun rows(decoration: PreferenceCardDecoration, list: RecyclerView): List<PreferenceCards.Shape> =
        PreferenceCardDecoration::class.java.getDeclaredMethod("rows", RecyclerView::class.java)
            .apply { isAccessible = true }.invoke(decoration, list) as List<PreferenceCards.Shape>

    @Test fun dynamicRemovalAndHiddenHeadersRefreshTheActualAdapterSnapshot() {
        val root = allocate(PreferenceScreen::class.java)
        val header = allocate(PreferenceCategoryEx::class.java)
        val first = allocate(Preference::class.java)
        val last = allocate(Preference::class.java)
        group(header, listOf(first, last)); group(root, listOf(header))
        val adapter = Adapter(listOf(header, first, last))
        val list = allocate(RecyclerView::class.java)
        setAdapter(list, adapter)
        val decoration = PreferenceCardDecoration { root }
        val original = rows(decoration, list)
        assertSame(original, rows(decoration, list))
        assertEquals(1, adapter.observers.size)
        assertTrue(original[1].first && !original[1].last && original[2].last)
        header.hide()
        adapter.rows = listOf(header, last)
        adapter.observers.single().onItemRangeRemoved(1, 1)
        val changed = rows(decoration, list)
        assertTrue(changed[0].hidden)
        assertTrue(changed[1].first && changed[1].last)
        adapter.rows = listOf(last) // A filtered adapter can omit the hidden category itself.
        adapter.observers.single().onChanged()
        assertTrue(rows(decoration, list).single().let { it.first && it.last && !it.hidden })
        adapter.rows = listOf(header, first, last)
        adapter.observers.single().onChanged()
        assertEquals(3, rows(decoration, list).size)
        decoration.dispose()
        assertTrue(adapter.observers.isEmpty())
    }

    @Test fun adapterReplacementAndViewDisposalReleaseEachObserverExactlyOnce() {
        val root = allocate(PreferenceScreen::class.java)
        group(root, emptyList())
        val list = allocate(RecyclerView::class.java)
        val first = Adapter(emptyList()); val second = Adapter(emptyList())
        val decoration = PreferenceCardDecoration { root }
        setAdapter(list, first); rows(decoration, list)
        assertEquals(1, first.observers.size)
        setAdapter(list, second); rows(decoration, list)
        assertTrue(first.observers.isEmpty())
        assertEquals(1, second.observers.size)
        decoration.dispose(); decoration.dispose()
        assertTrue(second.observers.isEmpty())
    }
}
