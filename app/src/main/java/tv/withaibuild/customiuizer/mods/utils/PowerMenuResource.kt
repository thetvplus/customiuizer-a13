package tv.withaibuild.customiuizer.mods.utils

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** One cold-path attempt. Native menu hooks stay inactive until every prerequisite succeeds. */
internal class PowerMenuResource {
    @Volatile var path: String? = null
        private set
    private var attempted = false

    /** Resource loading and native plugin activation are cold; both must reject a cleared cache. */
    fun pathForLoader(): String? {
        val current = path ?: return null
        val file = File(current)
        if (file.isFile && file.canRead() && file.length() > 0) return current
        path = null
        return null
    }

    @Synchronized
    fun prepare(target: File, source: () -> InputStream, install: (File) -> Boolean): Boolean {
        if (attempted) return false
        attempted = true
        val temporary = File(target.parentFile, target.name + ".tmp")
        try {
            source().use { input -> temporary.outputStream().use { input.copyTo(it) } }
            var hasManifest = false
            var total = 0L
            val buffer = ByteArray(8192)
            ZipInputStream(temporary.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    var size = 0L
                    while (true) {
                        val read = zip.read(buffer)
                        if (read == -1) break
                        size += read
                        total += read
                        check(total <= 2 * 1024 * 1024) { "Power menu resource is too large" }
                    }
                    if (entry.name == "manifest.xml" && !entry.isDirectory && size > 0) hasManifest = true
                    zip.closeEntry()
                }
            }
            check(hasManifest) { "Power menu manifest is missing" }
            check(!target.exists() || target.delete()) { "Cannot replace power menu cache" }
            check(temporary.renameTo(target)) { "Cannot prepare power menu cache" }
            if (!install(target)) return false
            path = target.path
            return true
        } finally {
            temporary.delete()
        }
    }
}
