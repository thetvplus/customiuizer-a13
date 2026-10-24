package tv.withaibuild.customiuizer.mods.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PowerMenuResourceTest {
    @get:Rule val folder = TemporaryFolder()
    private fun target() = File(folder.root, "power-menu")
    private fun archive(name: String = "manifest.xml", data: ByteArray = "<Root/>".toByteArray()): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    @Test fun shortReadsAndZeroAvailableStillCopyTheEntireResourceBeforeActivation() {
        val bytes = archive()
        var closed = false
        val input = object : ByteArrayInputStream(bytes) {
            override fun available() = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int) = super.read(buffer, offset, minOf(length, 7))
            override fun close() { closed = true; super.close() }
        }
        val state = PowerMenuResource()
        val target = target()
        assertTrue(state.prepare(target, { input }) { prepared ->
            assertNull(state.path)
            assertArrayEquals(bytes, prepared.readBytes())
            true
        })
        assertTrue(closed)
        assertEquals(target.path, state.path)
        assertFalse(state.prepare(target, { error("must not retry") }) { error("must not reinstall") })
        assertEquals(target.path, state.path)
    }

    @Test fun missingResourceIsTerminalAndCannotDisableTheNativeMenu() {
        val state = PowerMenuResource()
        try {
            state.prepare(target(), { throw IOException("missing resource") }) { fail("no hook may install"); true }
            fail("missing input must fail")
        } catch (_: IOException) { }
        assertNull(state.path)
        assertFalse(state.prepare(target(), { error("no unbounded retry") }) { true })
    }

    @Test fun corruptMissingOrEmptyManifestNeverActivatesOrInstallsTheOverride() {
        for (bytes in listOf("corrupt".toByteArray(), archive("other.xml"), archive(data = byteArrayOf()),
            archive().copyOf(20))) {
            val state = PowerMenuResource()
            try {
                state.prepare(target(), { ByteArrayInputStream(bytes) }) { fail("invalid resource must not install"); true }
                fail("invalid resource must fail")
            } catch (_: IOException) {
            } catch (_: IllegalStateException) { }
            assertNull(state.path)
            assertFalse(File(folder.root, "power-menu.tmp").exists())
        }
    }

    @Test fun missingConstructorOrFailedHookLeavesTheNativePluginEnabled() {
        for (throwFailure in listOf(false, true)) {
            val state = PowerMenuResource()
            try {
                assertFalse(state.prepare(target(), { ByteArrayInputStream(archive()) }) {
                    if (throwFailure) throw IllegalStateException("hook failed")
                    false
                })
                assertFalse(throwFailure)
            } catch (_: IllegalStateException) { assertTrue(throwFailure) }
            assertNull(state.path)
        }
    }

    @Test fun fatalInputFailuresPropagateAndStillLeaveNativeStateUntouched() {
        val state = PowerMenuResource()
        val fatal = OutOfMemoryError("test-only")
        try {
            state.prepare(target(), { throw fatal }) { true }
            fail("fatal failure must propagate")
        } catch (actual: OutOfMemoryError) { assertSame(fatal, actual) }
        assertNull(state.path)
    }

    @Test fun aClearedCacheFallsBackToNativeWithoutReinstallingOrRetrying() {
        val state = PowerMenuResource()
        val target = target()
        assertTrue(state.prepare(target, { ByteArrayInputStream(archive()) }) { true })
        assertEquals(target.path, state.pathForLoader())
        assertTrue(target.delete())
        assertNull(state.pathForLoader())
        assertNull(state.path)
        assertFalse(state.prepare(target, { error("must not retry after cache eviction") }) { true })
    }

    @Test fun packagedA13ResourcePassesTheSameIntegrityGate() {
        val source = listOf(File("src/main/res/raw/extended_power_menu"),
            File("app/src/main/res/raw/extended_power_menu")).first { it.isFile }
        val state = PowerMenuResource()
        assertTrue(state.prepare(target(), { source.inputStream() }) { true })
    }
}
