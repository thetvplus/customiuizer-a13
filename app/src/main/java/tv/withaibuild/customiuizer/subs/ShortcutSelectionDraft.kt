package tv.withaibuild.customiuizer.subs

import java.io.File
import java.io.IOException
import java.util.Properties
import java.util.UUID

internal data class ShortcutSelectionResult(
    val component: String,
    val name: String? = null,
    val intentUri: String? = null,
)

/** A provider result that can outlive the process owning its Fragment. */
internal class ShortcutSelectionDraft(cacheDir: File, requestId: UUID) {
    private val resultFile = File(cacheDir, "shortcut-$requestId.properties")
    private val incompleteResult = File(cacheDir, "shortcut-$requestId.properties.part")
    val icon = File(cacheDir, "shortcut-$requestId.png")
    val incompleteIcon = File(cacheDir, "shortcut-$requestId.png.part")

    fun writeResult(result: ShortcutSelectionResult) {
        val properties = Properties()
        properties.setProperty("component", result.component)
        result.name?.let { properties.setProperty("name", it) }
        result.intentUri?.let { properties.setProperty("intent", it) }
        try {
            incompleteResult.outputStream().buffered().use { properties.store(it, null) }
            if (!incompleteResult.renameTo(resultFile)) throw IOException("Cannot promote shortcut result")
        } finally {
            incompleteResult.delete()
        }
    }

    fun readResult(): ShortcutSelectionResult? {
        if (!resultFile.isFile) {
            if (incompleteResult.isFile) throw IOException("Incomplete shortcut result")
            return null
        }
        val properties = Properties()
        resultFile.inputStream().buffered().use { properties.load(it) }
        val component = properties.getProperty("component") ?: throw IOException("Missing shortcut component")
        return ShortcutSelectionResult(component, properties.getProperty("name"), properties.getProperty("intent"))
    }

    fun discard(keepIcon: Boolean = false) {
        resultFile.delete()
        incompleteResult.delete()
        incompleteIcon.delete()
        if (!keepIcon) icon.delete()
    }
}
