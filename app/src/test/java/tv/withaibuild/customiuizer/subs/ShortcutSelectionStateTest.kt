package tv.withaibuild.customiuizer.subs

import android.content.ContextWrapper
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class ShortcutSelectionStateTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

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
        val result = ShortcutSelectionResult("com.example|ShortcutProvider")
        beforeRecreation.beginRequest(result.component)
        beforeRecreation.prepareResult(contextWithCache(temporaryFolder.newFolder()), result, null)
        assertTrue(beforeRecreation.isBusy)

        val afterRecreation = selection()
        assertSame(beforeRecreation, afterRecreation)
        assertSame(result, afterRecreation.takeResult()?.result)
        assertNull(afterRecreation.takeResult())
        assertFalse(afterRecreation.isBusy)
    }

    @Test fun removingSelectorDiscardsUndeliveredResult() {
        val cache = temporaryFolder.newFolder()
        val pending = selection()
        val result = ShortcutSelectionResult("com.example|Provider")
        pending.beginRequest(result.component)
        pending.prepareResult(contextWithCache(cache), result, null)
        assertEquals(1, cache.listFiles().orEmpty().size)
        store.clear()
        assertNull(pending.ready.value)
        assertNull(pending.takeResult())
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    @Test fun newProcessRecoversAnAcceptedResultFromTheSavedRequest() {
        val cache = temporaryFolder.newFolder()
        val handle = SavedStateHandle()
        val before = ShortcutSelector.SelectionState(handle)
        val result = ShortcutSelectionResult("com.example|Provider", "Shortcut", "intent:#Intent;end")
        before.beginRequest(result.component)
        val beforeCallback = handle.keys().associateWith { handle.get<Any>(it) }

        before.prepareResult(contextWithCache(cache), result, null)
        val after = ShortcutSelector.SelectionState(SavedStateHandle(beforeCallback))
        after.restore(contextWithCache(cache))

        assertNull(after.requestedComponent)
        assertTrue(after.isBusy)
        assertEquals(result, after.takeResult()?.result)
        assertNull(after.takeResult())
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    @Test fun incompleteIconDoesNotPreventRecoveryOfTheResult() {
        val cache = temporaryFolder.newFolder()
        val result = ShortcutSelectionResult("com.example|Provider", "捷徑", "intent:#Intent;end")
        val requestId = UUID.randomUUID()
        val draft = ShortcutSelectionDraft(cache, requestId)
        draft.writeResult(result)
        draft.incompleteIcon.writeText("unfinished")
        val after = recreateSavedSelection(requestId)

        after.restore(contextWithCache(cache))
        val prepared = after.takeResult()
        assertEquals(result, prepared?.result)
        assertNull(prepared?.icon)
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    @Test fun recoveredIconRemainsAvailableUntilTheRecipientTakesOwnership() {
        val cache = temporaryFolder.newFolder()
        val requestId = UUID.randomUUID()
        val draft = ShortcutSelectionDraft(cache, requestId)
        draft.writeResult(ShortcutSelectionResult("com.example|Provider"))
        draft.icon.writeText("finished")
        val after = recreateSavedSelection(requestId)

        after.restore(contextWithCache(cache))
        assertEquals(draft.icon, after.takeResult()?.icon)
        assertEquals(listOf(draft.icon), cache.listFiles().orEmpty().toList())
    }

    @Test fun incompleteResultAllowsANewSelection() {
        val cache = temporaryFolder.newFolder()
        val requestId = UUID.randomUUID()
        File(cache, "shortcut-$requestId.properties.part").writeText("unfinished")
        val after = ShortcutSelector.SelectionState(SavedStateHandle(mapOf(
            "shortcut_request_id" to requestId.toString(),
            "shortcut_component" to "com.example|Provider",
        )))

        after.restore(contextWithCache(cache))
        assertFalse(after.isBusy)
        assertNull(after.takeResult())
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    private fun recreateSavedSelection(requestId: UUID) = ShortcutSelector.SelectionState(
        SavedStateHandle(mapOf("shortcut_request_id" to requestId.toString()))
    )

    private fun selection(): ShortcutSelector.SelectionState =
        ViewModelProvider(store, factory)[ShortcutSelector.SelectionState::class.java]

    private fun contextWithCache(cache: File) = object : ContextWrapper(null) {
        override fun getCacheDir(): File = cache
    }
}
