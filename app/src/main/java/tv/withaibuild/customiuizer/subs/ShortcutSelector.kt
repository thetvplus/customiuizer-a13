package tv.withaibuild.customiuizer.subs

import android.app.Activity
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
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.SubFragmentWithSearch
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.ResolveInfoAdapter
import tv.withaibuild.customiuizer.utils.SettingsDiagnostics
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList

@Suppress("DEPRECATION")
class ShortcutSelector : SubFragmentWithSearch() {

    private var key: String? = null
    private var keyContents: String? = null
    private var shortcutRequest = 0
    private lateinit var shortcuts: ArrayList<ResolveInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        this.padded = false
        super.onCreate(savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val args = arguments ?: return
        key = args.getString("key")
        key?.let { keyContents = AppHelper.getStringOfAppPrefs(it, null) }

        val shortcutIntent = Intent(Intent.ACTION_CREATE_SHORTCUT)
        val pm = activity?.packageManager ?: return
        shortcuts = ArrayList(pm.queryIntentActivities(shortcutIntent, 0))

        val ctx = context ?: return
        listView?.adapter = ResolveInfoAdapter(ctx, shortcuts)
        listView?.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val createShortcutIntent = Intent(Intent.ACTION_CREATE_SHORTCUT)
            val app = parent.getItemAtPosition(position) as? ResolveInfo ?: return@OnItemClickListener
            val cn = ComponentName(app.activityInfo.packageName, app.activityInfo.name)
            createShortcutIntent.component = cn
            keyContents = app.activityInfo.packageName + "|" + app.activityInfo.name
            shortcutRequest++
            startActivityForResult(createShortcutIntent, 7350)
        }

        view?.findViewById<View>(R.id.am_progressBar)?.visibility = View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 7350 && resultCode == Activity.RESULT_OK) {
            val ctx = context
            if (ctx == null) {
                super.onActivityResult(requestCode, resultCode, data)
                return
            }

            val target = targetFragment
            if (target != null && isAdded) {
                val result = Intent(ctx, this.javaClass)
                    .putExtra("shortcut_contents", keyContents)
                try {
                    result.putExtra("shortcut_name", data?.getStringExtra(Intent.EXTRA_SHORTCUT_NAME))
                    result.putExtra("shortcut_intent", data?.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java))
                } catch (failure: Throwable) {
                    if (failure is OutOfMemoryError || failure is ThreadDeath || failure is VirtualMachineError) throw failure
                    SettingsDiagnostics.failure("ShortcutSelector.readResult", failure)
                }
                val targetRequest = targetRequestCode
                val request = ++shortcutRequest
                val sourceView = view
                fun deliver(pendingIcon: File?) {
                    if (request != shortcutRequest || view !== sourceView || targetFragment !== target || !isAdded) {
                        pendingIcon?.delete()
                        return
                    }
                    if (pendingIcon != null) {
                        // MultiAction promotes tmp.png when the user saves the selection.
                        val iconFile = File(pendingIcon.parentFile, "tmp.png")
                        if (pendingIcon.renameTo(iconFile)) result.putExtra("shortcut_icon", iconFile.absolutePath)
                        else pendingIcon.delete()
                    }
                    target.onActivityResult(targetRequest, Activity.RESULT_OK, result)
                    finish()
                }

                if (key == null || data == null) {
                    deliver(null)
                } else {
                    val appContext = ctx.applicationContext
                    Thread({
                        val pendingIcon = prepareIcon(appContext, data)
                        appContext.mainExecutor.execute { deliver(pendingIcon) }
                    }, "A13-shortcut-icon").start()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    @WorkerThread
    private fun prepareIcon(context: Context, data: Intent): File? {
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
}
