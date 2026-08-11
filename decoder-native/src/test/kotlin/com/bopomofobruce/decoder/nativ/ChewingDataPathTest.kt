package com.bopomofobruce.decoder.nativ

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChewingDataPathTest {
    private val tempDir = createTempDirectory("chewing-data-test").toFile()

    @AfterEach
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    private fun fakeAssets(files: Map<String, ByteArray>): AssetManager {
        val assets = mockk<AssetManager>()
        every { assets.list("chewing") } returns files.keys.toTypedArray()
        for ((name, bytes) in files) {
            every { assets.open("chewing/$name") } answers { ByteArrayInputStream(bytes) }
        }
        return assets
    }

    @Test
    fun `extracts every asset under chewing into the target dir`() {
        val assets =
            fakeAssets(mapOf("word.dat" to byteArrayOf(1, 2, 3), "tsi.dat" to byteArrayOf(4, 5)))
        val targetDir = File(tempDir, "cache-chewing")

        val result = extractChewingData(assets, targetDir)

        assertEquals(targetDir, result)
        assertTrue(File(targetDir, "word.dat").exists())
        assertEquals(3, File(targetDir, "word.dat").length())
        assertTrue(File(targetDir, "tsi.dat").exists())
        assertEquals(2, File(targetDir, "tsi.dat").length())
    }

    @Test
    fun `creates the target dir if it does not exist yet`() {
        val assets = fakeAssets(mapOf("word.dat" to byteArrayOf(1)))
        val targetDir = File(tempDir, "does/not/exist/yet")

        extractChewingData(assets, targetDir)

        assertTrue(targetDir.isDirectory)
    }

    @Test
    fun `is idempotent - does not re-open already-extracted files`() {
        val assets = fakeAssets(mapOf("word.dat" to byteArrayOf(1, 2, 3)))
        val targetDir = File(tempDir, "cache-chewing")

        extractChewingData(assets, targetDir)
        extractChewingData(assets, targetDir)

        verify(exactly = 1) { assets.open("chewing/word.dat") }
    }

    @Test
    fun `an interrupted copy never leaves a truncated file at the final name`() {
        // Regression test for A6: extraction writes to a sibling `.tmp` file and only renames it
        // into place on success, so a copy that dies partway through (simulating a low-memory
        // process kill) must never leave anything at the final `word.dat` name — only (possibly)
        // at `word.dat.tmp`. Confirmed to go RED on the pre-fix implementation (which wrote
        // straight to the final name): reverting extractChewingData to write directly to `target`
        // makes this test fail with a non-empty `word.dat` left behind. See devlog A6.
        val assets = mockk<AssetManager>()
        every { assets.list("chewing") } returns arrayOf("word.dat")
        every { assets.open("chewing/word.dat") } answers
            {
                object : java.io.InputStream() {
                    private var bytesRead = 0

                    override fun read(): Int {
                        bytesRead++
                        if (bytesRead > 2) {
                            throw IOException("simulated interrupted read (process kill)")
                        }
                        return 'A'.code
                    }
                }
            }
        val targetDir = File(tempDir, "cache-chewing")

        org.junit.jupiter.api.assertThrows<IOException> { extractChewingData(assets, targetDir) }

        assertTrue(
            !File(targetDir, "word.dat").exists(),
            "final file must not exist after an interrupted copy",
        )
    }

    @Test
    fun `does not re-extract a file already present at the final name`() {
        val assets = fakeAssets(mapOf("word.dat" to byteArrayOf(9, 9, 9)))
        val targetDir = File(tempDir, "cache-chewing")
        targetDir.mkdirs()
        File(targetDir, "word.dat").writeBytes(byteArrayOf(1, 2, 3, 4))

        extractChewingData(assets, targetDir)

        // Presence at the final name is trusted as-is (see KDoc: the final name only ever comes
        // into existence via a completed atomic rename), so the pre-existing content is left
        // untouched rather than being re-fetched from assets.
        assertEquals(4, File(targetDir, "word.dat").length())
        verify(exactly = 0) { assets.open("chewing/word.dat") }
    }

    @Test
    fun `returns an empty target dir when there are no chewing assets`() {
        val assets = mockk<AssetManager>()
        every { assets.list("chewing") } returns null
        val targetDir = File(tempDir, "cache-chewing")

        val result = extractChewingData(assets, targetDir)

        val filesInTargetDir: List<File> = result.listFiles()?.toList() ?: emptyList()
        assertEquals(emptyList<File>(), filesInTargetDir)
    }

    private fun fakeContext(assets: AssetManager, cacheDir: File): Context {
        val context = mockk<Context>()
        every { context.assets } returns assets
        every { context.cacheDir } returns cacheDir
        return context
    }

    // Regression tests for K5: extractChewingData only ever asks "does the file already exist at
    // its final name" — it has no way to distinguish "cached from this app version" from "cached
    // from a stale previous app version whose dictionary content has since changed". getDataPath()
    // is what actually closes that gap, by folding the dictionary version into the cache directory
    // name so an app upgrade that ships new dictionary data can never accidentally match a stale
    // directory. The version string itself must be kept in lockstep with
    // decoder-native/scripts/fetch_chewing_data.sh's VERSION — see ChewingDataPath.kt's
    // CHEWING_DATA_VERSION KDoc.

    @Test
    fun `getDataPath scopes the extraction directory to the current dictionary data version`() {
        val assets = fakeAssets(mapOf("word.dat" to byteArrayOf(1, 2, 3)))
        val context = fakeContext(assets, tempDir)

        val path = getDataPath(context)

        // Pinned to the same version string as fetch_chewing_data.sh's VERSION (see
        // CHEWING_DATA_VERSION KDoc) — this assertion is deliberately exact, not just "contains a
        // version", so that a future version bump that forgets to update this test (or the
        // constant) is caught rather than silently passing on a substring match.
        assertEquals(File(tempDir, "chewing-2026.3.22").absolutePath, path)
        assertTrue(File(path, "word.dat").exists())
    }

    @Test
    fun `getDataPath deletes a stale chewing-prefixed cache dir left by a previous app version`() {
        // Simulates the exact scenario K5 describes: cacheDir survives an app upgrade (Android
        // preserves it across upgrades), so a previous app version's now-outdated dictionary
        // directory is still sitting there when the new version's getDataPath() runs.
        val staleDir = File(tempDir, "chewing-2016.1.1")
        staleDir.mkdirs()
        File(staleDir, "word.dat").writeBytes(byteArrayOf(9, 9, 9)) // stale content, must not leak

        val assets = fakeAssets(mapOf("word.dat" to byteArrayOf(1, 2, 3)))
        val context = fakeContext(assets, tempDir)

        val path = getDataPath(context)

        assertFalse(staleDir.exists(), "stale previous-version cache dir must be deleted")
        assertEquals(3, File(path, "word.dat").length()) // freshly extracted, not the stale bytes
    }

    @Test
    fun `getDataPath does not touch unrelated directories under cacheDir`() {
        val unrelatedDir = File(tempDir, "not-chewing-at-all")
        unrelatedDir.mkdirs()
        File(unrelatedDir, "marker").writeText("keep me")

        val assets = fakeAssets(mapOf("word.dat" to byteArrayOf(1)))
        val context = fakeContext(assets, tempDir)

        getDataPath(context)

        assertTrue(unrelatedDir.exists(), "only chewing-* prefixed dirs should be cleaned up")
        assertTrue(File(unrelatedDir, "marker").exists())
    }
}
