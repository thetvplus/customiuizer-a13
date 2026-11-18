package tv.withaibuild.customiuizer.subs

import android.content.ContextWrapper
import android.content.Intent
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShortcutSelectionStateTest {
    private val store = ViewModelStore()
    private val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            modelClass.cast(ShortcutSelector.SelectionState(SavedStateHandle()))
    }

    @Before fun useCallingThreadForLiveData() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun isMainThread(): Boolean = true
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
        })
    }

    @After fun clearState() {
        store.clear()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test fun retainedSelectionCanBeConsumedExactlyOnce() {
        val beforeRecreation = selection()
        val result = Intent().putExtra("shortcut_contents", "com.example|ShortcutProvider")
        beforeRecreation.prepareResult(ContextWrapper(null), result, null)
        assertTrue(beforeRecreation.isBusy)

        val afterRecreation = selection()
        assertSame(beforeRecreation, afterRecreation)
        assertSame(result, afterRecreation.takeResult()?.result)
        assertNull(afterRecreation.takeResult())
        assertFalse(afterRecreation.isBusy)
    }

    @Test fun removingSelectorDiscardsUndeliveredResult() {
        val pending = selection()
        pending.prepareResult(ContextWrapper(null), Intent(), null)
        store.clear()
        assertNull(pending.ready.value)
        assertNull(pending.takeResult())
    }

    private fun selection(): ShortcutSelector.SelectionState =
        ViewModelProvider(store, factory)[ShortcutSelector.SelectionState::class.java]
}
