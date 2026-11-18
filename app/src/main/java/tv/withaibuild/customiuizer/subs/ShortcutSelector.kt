package tv.withaibuild.customiuizer.subs

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.SubFragmentWithSearch
import tv.withaibuild.customiuizer.utils.ResolveInfoAdapter
import tv.withaibuild.customiuizer.utils.SettingsDiagnostics
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList
import java.util.UUID

@Suppress("DEPRECATION")
class ShortcutSelector : SubFragmentWithSearch() {

    private val selection by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(this)[SelectionState::class.java]
    }
    private val deliverResult = Runnable { deliverResultIfReady() }

    override fun onCreate(savedInstanceState: Bundle?) {
        this.padded = false
        super.onCreate(savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val shortcutIntent = Intent(Intent.ACTION_CREATE_SHORTCUT)
        val pm = activity?.packageManager ?: return
        val shortcuts = ArrayList(pm.queryIntentActivities(shortcutIntent, 0))

        val ctx = context ?: return
        listView?.adapter = ResolveInfoAdapter(ctx, shortcuts)
        listView?.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            if (selection.isBusy) return@OnItemClickListener
            val app = parent.getItemAtPosition(position) as? ResolveInfo ?: return@OnItemClickListener
            val cn = ComponentName(app.activityInfo.packageName, app.activityInfo.name)
            val createShortcutIntent = Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(cn)
            selection.beginRequest(cn.packageName + "|" + cn.className)
            try {
                startActivityForResult(createShortcutIntent, 7350)
            } catch (failure: ActivityNotFoundException) {
                selection.cancelRequest()
                SettingsDiagnostics.failure("ShortcutSelector.launchProvider", failure)
            }
        }

        view?.findViewById<View>(R.id.am_progressBar)?.visibility = View.GONE
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selection.restore(requireContext())
        selection.ready.observe(viewLifecycleOwner) { result ->
            if (result != null) view.post(deliverResult)
        }
    }

    override fun onResume() {
        super.onResume()
        // Run after the FragmentManager finishes dispatching the resume transition.
        view?.post(deliverResult)
    }

    override fun onDestroyView() {
        view?.removeCallbacks(deliverResult)
        listView?.onItemClickListener = null
        super.onDestroyView()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 7350) return
        val requestedComponent = selection.requestedComponent ?: return
        if (resultCode != Activity.RESULT_OK) {
            selection.cancelRequest()
            return
        }

        val appContext = context?.applicationContext ?: return
        var name: String? = null
        var intentUri: String? = null
        try {
            name = data?.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
            intentUri = data?.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)?.toUri(0)
        } catch (failure: Throwable) {
            if (failure is OutOfMemoryError || failure is ThreadDeath || failure is VirtualMachineError) throw failure
            SettingsDiagnostics.failure("ShortcutSelector.readResult", failure)
        }
        val result = ShortcutSelectionResult(requestedComponent, name, intentUri)
        val iconData = data.takeIf { arguments?.getString("key") != null }
        selection.prepareResult(appContext, result, iconData)
    }

    private fun deliverResultIfReady() {
        if (!isAdded || !isResumed || view == null || parentFragmentManager.isStateSaved) return
        val target = targetFragment ?: return
        val (shortcut, pendingIcon) = selection.takeResult() ?: return
        val result = Intent()
            .putExtra("shortcut_contents", shortcut.component)
            .putExtra("shortcut_name", shortcut.name)
            .putExtra("shortcut_intent", shortcut.intentUri)
        val filesDir = context?.filesDir
        if (filesDir != null && pendingIcon != null) {
            // MultiAction promotes tmp.png when the user saves the selection.
            val iconFile = File(filesDir, "shortcuts/tmp.png")
            iconFile.parentFile?.mkdirs()
            if (pendingIcon.renameTo(iconFile)) result.putExtra("shortcut_icon", iconFile.absolutePath)
        }
        pendingIcon?.delete()
        target.onActivityResult(targetRequestCode, Activity.RESULT_OK, result)
        finish()
    }

    internal class SelectionState(private val savedState: SavedStateHandle) : ViewModel() {
        data class PreparedShortcut(val result: ShortcutSelectionResult, val icon: File?)

        private val completedResult = MutableLiveData<PreparedShortcut?>()
        private var draft: ShortcutSelectionDraft? = null
        private var iconWorker: Thread? = null
        val ready: LiveData<PreparedShortcut?> get() = completedResult
        val isBusy: Boolean
            get() = requestedComponent != null || iconWorker != null || completedResult.value != null

        var requestedComponent: String?
            get() = savedState["shortcut_component"]
            private set(component) {
                if (component == null) savedState.remove<String>("shortcut_component")
                else savedState["shortcut_component"] = component
            }

        fun beginRequest(component: String) {
            savedState["shortcut_request_id"] = UUID.randomUUID().toString()
            requestedComponent = component
        }

        fun cancelRequest() {
            savedState.remove<String>("shortcut_request_id")
            requestedComponent = null
        }

        private fun findDraft(context: Context): ShortcutSelectionDraft? {
            val requestId = savedState.get<String>("shortcut_request_id") ?: return null
            val id = try {
                UUID.fromString(requestId)
            } catch (_: IllegalArgumentException) {
                return null
            }
            val cacheDir = context.cacheDir ?: return null
            return ShortcutSelectionDraft(cacheDir, id)
        }

        fun restore(context: Context) {
            if (draft != null || completedResult.value != null) return
            val savedDraft = findDraft(context) ?: return
            val result = try {
                savedDraft.readResult()
            } catch (failure: Exception) {
                SettingsDiagnostics.failure("ShortcutSelector.restoreDraft", failure)
                savedDraft.discard()
                cancelRequest()
                return
            }
            if (result == null) {
                if (requestedComponent == null) {
                    savedDraft.discard()
                    cancelRequest()
                }
                return
            }
            draft = savedDraft
            requestedComponent = null
            completedResult.value = PreparedShortcut(result, savedDraft.icon.takeIf { it.isFile })
        }

        fun prepareResult(appContext: Context, result: ShortcutSelectionResult, iconData: Intent?) {
            val currentDraft = findDraft(appContext)
            draft = currentDraft
            try {
                // The previous saved Activity snapshot can already contain this request ID.
                // Publish the result before starting icon work or clearing its request marker.
                currentDraft?.writeResult(result)
            } catch (failure: Exception) {
                SettingsDiagnostics.failure("ShortcutSelector.saveDraft", failure)
            }
            requestedComponent = null

            if (iconData == null || currentDraft == null) {
                completedResult.value = PreparedShortcut(result, null)
                return
            }
            val worker = Thread({
                val pendingIcon = prepareIcon(appContext, iconData, currentDraft)
                val currentWorker = Thread.currentThread()
                appContext.mainExecutor.execute {
                    if (iconWorker === currentWorker) {
                        iconWorker = null
                        completedResult.value = PreparedShortcut(result, pendingIcon)
                    } else {
                        pendingIcon?.delete()
                    }
                }
            }, "A13-shortcut-icon")
            iconWorker = worker
            worker.start()
        }

        fun takeResult(): PreparedShortcut? {
            val result = completedResult.value ?: return null
            completedResult.value = null
            draft?.discard(keepIcon = true)
            draft = null
            cancelRequest()
            return result
        }

        override fun onCleared() {
            iconWorker?.interrupt()
            iconWorker = null
            completedResult.value = null
            draft?.discard()
            draft = null
            cancelRequest()
        }
    }
}

@WorkerThread
private fun prepareIcon(context: Context, data: Intent, draft: ShortcutSelectionDraft): File? {
    if (Thread.currentThread().isInterrupted) return null
    val resourceIcon = try {
        val resource = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource::class.java)
        resource?.let {
            val resources = context.createPackageContext(it.packageName, 0).resources
            val resId = resources.getIdentifier(it.resourceName, "drawable", it.packageName)
            BitmapFactory.decodeResource(resources, resId)
        }
    } catch (failure: Throwable) {
        if (failure is OutOfMemoryError || failure is ThreadDeath || failure is VirtualMachineError) throw failure
        SettingsDiagnostics.failure("ShortcutSelector.loadIconResource", failure)
        null
    }
    try {
        if (Thread.currentThread().isInterrupted) return null
        val icon = resourceIcon ?: data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java) ?: return null
        val complete = FileOutputStream(draft.incompleteIcon).use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return draft.icon.takeIf { complete && draft.incompleteIcon.renameTo(it) }
    } catch (failure: Throwable) {
        if (failure is OutOfMemoryError || failure is ThreadDeath || failure is VirtualMachineError) throw failure
        SettingsDiagnostics.failure("ShortcutSelector.persistIcon", failure)
        return null
    } finally {
        draft.incompleteIcon.delete()
        resourceIcon?.recycle()
    }
}
