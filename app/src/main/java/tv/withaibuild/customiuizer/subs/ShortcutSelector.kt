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
            selection.requestedComponent = cn.packageName + "|" + cn.className
            try {
                startActivityForResult(createShortcutIntent, 7350)
            } catch (failure: ActivityNotFoundException) {
                selection.requestedComponent = null
                SettingsDiagnostics.failure("ShortcutSelector.launchProvider", failure)
            }
        }

        view?.findViewById<View>(R.id.am_progressBar)?.visibility = View.GONE
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
        selection.requestedComponent = null
        if (resultCode != Activity.RESULT_OK) return

        val appContext = context?.applicationContext ?: return
        val result = Intent(appContext, this.javaClass).putExtra("shortcut_contents", requestedComponent)
        try {
            result.putExtra("shortcut_name", data?.getStringExtra(Intent.EXTRA_SHORTCUT_NAME))
            result.putExtra("shortcut_intent", data?.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java))
        } catch (failure: Throwable) {
            if (failure is OutOfMemoryError || failure is ThreadDeath || failure is VirtualMachineError) throw failure
            SettingsDiagnostics.failure("ShortcutSelector.readResult", failure)
        }
        selection.prepareResult(appContext, result, data.takeIf { arguments?.getString("key") != null })
    }

    private fun deliverResultIfReady() {
        if (!isAdded || !isResumed || view == null || parentFragmentManager.isStateSaved) return
        val target = targetFragment ?: return
        val (result, pendingIcon) = selection.takeResult() ?: return
        if (pendingIcon != null) {
            // MultiAction promotes tmp.png when the user saves the selection.
            val iconFile = File(pendingIcon.parentFile, "tmp.png")
            if (pendingIcon.renameTo(iconFile)) result.putExtra("shortcut_icon", iconFile.absolutePath)
            else pendingIcon.delete()
        }
        target.onActivityResult(targetRequestCode, Activity.RESULT_OK, result)
        finish()
    }

    internal class SelectionState(private val savedState: SavedStateHandle) : ViewModel() {
        data class PreparedShortcut(val result: Intent, val icon: File?)

        private val completedResult = MutableLiveData<PreparedShortcut?>()
        private var iconWorker: Thread? = null
        val ready: LiveData<PreparedShortcut?> get() = completedResult
        val isBusy: Boolean
            get() = requestedComponent != null || iconWorker != null || completedResult.value != null

        var requestedComponent: String?
            get() = savedState["shortcut_component"]
            set(component) {
                savedState["shortcut_component"] = component
            }

        fun prepareResult(appContext: Context, result: Intent, iconData: Intent?) {
            if (iconData == null) {
                completedResult.value = PreparedShortcut(result, null)
                return
            }
            val worker = Thread({
                val pendingIcon = prepareIcon(appContext, iconData)
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
            return result
        }

        override fun onCleared() {
            iconWorker?.interrupt()
            iconWorker = null
            takeResult()?.icon?.delete()
        }
    }
}

@WorkerThread
private fun prepareIcon(context: Context, data: Intent): File? {
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
    var pending: File? = null
    var complete = false
    try {
        if (Thread.currentThread().isInterrupted) return null
        val icon = resourceIcon ?: data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java) ?: return null
        val filesDir = context.filesDir ?: return null
        val dir = File(filesDir, "shortcuts")
        dir.mkdirs()
        pending = File.createTempFile("pending-", ".png", dir)
        complete = FileOutputStream(pending).use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return pending.takeIf { complete }
    } catch (failure: Throwable) {
        if (failure is OutOfMemoryError || failure is ThreadDeath || failure is VirtualMachineError) throw failure
        SettingsDiagnostics.failure("ShortcutSelector.persistIcon", failure)
        return null
    } finally {
        if (!complete) pending?.delete()
        resourceIcon?.recycle()
    }
}
