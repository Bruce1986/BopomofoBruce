package com.bopomofobruce.decoder.nativ

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
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
    fun `fails with a clear message when the target path is an existing plain file`() {
        val assets = fakeAssets(mapOf("word.dat" to byteArrayOf(1)))
        val targetDir = File(tempDir, "chewing-collides-with-a-file")
        targetDir.writeText("not a directory")

        val error =
            assertThrows(IllegalStateException::class.java) {
                extractChewingData(assets, targetDir)
            }

        assertTrue(error.message!!.contains("Could not create chewing data dir"))
        verify(exactly = 0) { assets.open(any()) }
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
    fun `recovers from an orphaned tmp file left by an interrupted copy`() {
        // The KDoc on extractChewingData promises that a process killed mid-copy leaves at most an
        // orphaned `<name>.tmp`, and that "the next call harmlessly overwrites that `.tmp` and
        // retries". The interrupted-copy test above only proves the first half (nothing lands at
        // the final name); nothing proved the recovery actually produces CORRECT content.
        //
        // Mutation this kills: switching the copy to append mode
        // (`FileOutputStream(tmp, true)`) instead of truncating. Every other test starts from an
        // empty directory, where append and truncate behave identically, so that mutation survives
        // the whole suite — while in production it would concatenate the dead process's partial
        // bytes onto the retry and publish the mixture under the final name.
        val expected = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val assets = fakeAssets(mapOf("word.dat" to expected))
        val targetDir = File(tempDir, "cache-chewing")
        targetDir.mkdirs()
        // Longer than `expected` on purpose: with append-mode the final file would be the leftovers
        // followed by `expected`, so both the length and the content check below would fail.
        File(targetDir, "word.dat.tmp").writeBytes(ByteArray(64) { 0x7F })

        extractChewingData(assets, targetDir)

        val extracted = File(targetDir, "word.dat")
        assertTrue(extracted.exists(), "retry after an orphaned .tmp must produce the final file")
        assertArrayEquals(
            expected,
            extracted.readBytes(),
            "retry must overwrite the orphaned .tmp, not append to it",
        )
        assertFalse(
            File(targetDir, "word.dat.tmp").exists(),
            "the .tmp must have been renamed into place, not left behind",
        )
    }

    @Test
    fun `concurrent first-time extraction never overlaps two copies of the same file`() {
        // extractChewingData's KDoc justifies `synchronized(extractionLock)` by the two-thread race
        // it prevents: both threads see the final name missing, then interleave writes into the
        // same `.tmp` path. Every other test in this class is single-threaded and sequential, so
        // deleting the `synchronized` wrapper entirely leaves the whole suite green — the lock had
        // no guard at all.
        //
        // Rather than hoping a race shows up, this observes the invariant the lock exists to
        // provide: at no point may two threads be inside the asset copy at the same time. The
        // first thread to start reading holds still inside the copy until the outcome is decided
        // one way or the other — either the second thread also starts reading (the lock is not
        // holding, `overlapSeen` is set), or the second thread is seen parked on the monitor (the
        // lock is holding). Nothing here depends on how fast the copy runs: an earlier version
        // slept on every byte of a 4096-byte payload instead, which took over 8 s on an idle
        // machine and ran past the 30 s await below on a loaded one, failing with the lock
        // intact.
        val concurrentReaders = AtomicInteger(0)
        val overlapSeen = AtomicBoolean(false)
        val workers = CopyOnWriteArrayList<Thread>()
        val payload = ByteArray(16) { it.toByte() }

        fun holdUntilOutcomeIsDecided() {
            val self = Thread.currentThread()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var blockedPolls = 0
            while (!overlapSeen.get() && System.nanoTime() < deadline) {
                val others = workers.filter { it !== self }
                // The other worker has already finished. With the lock intact this is never
                // reached: the second thread finds the final file in place and does not open the
                // asset at all. It is reached only when the lock is not holding and the first
                // reader stopped waiting (deadline, or a false BLOCKED streak) and completed
                // before this thread started reading. Nobody is left to overlap with, so return
                // rather than idle until the deadline.
                if (others.all { it.state == Thread.State.TERMINATED }) {
                    return
                }
                // Several consecutive BLOCKED observations, so a worker that is only passing
                // through some unrelated monitor on its way in is not mistaken for one parked on
                // extractionLock.
                val otherIsParked = others.any { it.state == Thread.State.BLOCKED }
                blockedPolls = if (otherIsParked) blockedPolls + 1 else 0
                if (blockedPolls >= 20) {
                    return
                }
                Thread.sleep(5)
            }
        }

        val assets = mockk<AssetManager>()
        every { assets.list("chewing") } returns arrayOf("word.dat")
        every { assets.open("chewing/word.dat") } answers
            {
                object : java.io.InputStream() {
                    private val backing = ByteArrayInputStream(payload)
                    private var entered = false

                    override fun read(): Int {
                        if (!entered) {
                            entered = true
                            if (concurrentReaders.incrementAndGet() > 1) {
                                overlapSeen.set(true)
                            }
                            holdUntilOutcomeIsDecided()
                        }
                        return backing.read()
                    }

                    override fun close() {
                        if (entered) {
                            concurrentReaders.decrementAndGet()
                        }
                        backing.close()
                    }
                }
            }

        val targetDir = File(tempDir, "cache-chewing")
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val failure = AtomicReference<Throwable?>(null)
        repeat(2) {
            workers += Thread {
                try {
                    start.await()
                    extractChewingData(assets, targetDir)
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                } finally {
                    done.countDown()
                }
            }
        }
        workers.forEach { it.start() }
        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS), "both extraction threads must finish")

        assertEquals(null, failure.get(), "neither thread may fail: ${failure.get()}")
        assertFalse(
            overlapSeen.get(),
            "two threads were inside the asset copy at once — extractionLock is not holding",
        )
        assertArrayEquals(
            payload,
            File(targetDir, "word.dat").readBytes(),
            "the published file must be exactly one complete copy of the asset",
        )
    }

    @Test
    fun `extracts the files still missing when an earlier one is already in place`() {
        // The state a process kill leaves behind when it lands between two files: word.dat has
        // been renamed into place, tsi.dat has not been copied yet. The next call must skip the
        // first and still go on to the second — stopping at the first file already present
        // would leave the directory without tsi.dat for good, since nothing re-checks it later.
        val assets =
            fakeAssets(mapOf("word.dat" to byteArrayOf(1, 2, 3), "tsi.dat" to byteArrayOf(4, 5)))
        val targetDir = File(tempDir, "cache-chewing")
        targetDir.mkdirs()
        File(targetDir, "word.dat").writeBytes(byteArrayOf(9, 9, 9, 9))

        extractChewingData(assets, targetDir)

        assertArrayEquals(byteArrayOf(4, 5), File(targetDir, "tsi.dat").readBytes())
        assertArrayEquals(byteArrayOf(9, 9, 9, 9), File(targetDir, "word.dat").readBytes())
        verify(exactly = 0) { assets.open("chewing/word.dat") }
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

    @Test
    fun `getDataPath does not wipe and re-extract its own current-version dir on a second call`() {
        // The stale-dir sweep must skip the current version's dir; without that exclusion every
        // app start deletes the freshly extracted dictionary and copies it all over again.
        val assets = fakeAssets(mapOf("word.dat" to byteArrayOf(1, 2, 3)))
        val context = fakeContext(assets, tempDir)

        getDataPath(context)
        getDataPath(context)

        verify(exactly = 1) { assets.open("chewing/word.dat") }
    }

    @Test
    fun `getDataPath still cleans up a stale dir even when extraction itself fails`() {
        val staleDir = File(tempDir, "chewing-2016.1.1")
        staleDir.mkdirs()
        File(staleDir, "word.dat").writeBytes(byteArrayOf(9, 9, 9))

        val assets = mockk<AssetManager>()
        every { assets.list("chewing") } returns arrayOf("word.dat")
        every { assets.open("chewing/word.dat") } throws IOException("simulated asset read failure")
        val context = fakeContext(assets, tempDir)

        assertThrows(IOException::class.java) { getDataPath(context) }

        assertFalse(staleDir.exists(), "stale dir must be cleaned up even when extraction fails")
    }

    @Test
    fun `a failed rename is not silently swallowed - it throws, removes the tmp and publishes nothing`() {
        // Forces File.renameTo() to return false by putting a non-empty directory at the final
        // name right after the tmp copy is written (when the asset stream is closed, which is
        // after the "final name already exists" check has run). Renaming a regular file onto a
        // non-empty directory fails regardless of who runs the test; the earlier version of this
        // test made the target dir read-only instead, which root ignores and which also made
        // tmp.delete() fail, so the "the .tmp is deleted" half of the contract was unobservable.
        val targetDir = File(tempDir, "cache-chewing")
        val blocker = File(targetDir, "word.dat")
        val blockerMarker = File(blocker, "marker")
        val payload = byteArrayOf(1, 2, 3)
        val assets = mockk<AssetManager>()
        every { assets.list("chewing") } returns arrayOf("word.dat")
        every { assets.open("chewing/word.dat") } answers
            {
                object : java.io.InputStream() {
                    private val backing = ByteArrayInputStream(payload)

                    override fun read(): Int = backing.read()

                    override fun close() {
                        backing.close()
                        blocker.mkdirs()
                        blockerMarker.writeBytes(byteArrayOf(9))
                    }
                }
            }

        assertThrows(IOException::class.java) { extractChewingData(assets, targetDir) }

        assertTrue(
            blockerMarker.isFile,
            "test setup: the blocking directory must have been created",
        )
        assertFalse(
            blocker.isFile,
            "a failed rename must not publish the payload at the final name",
        )
        assertFalse(
            File(targetDir, "word.dat.tmp").exists(),
            "a failed rename must delete its tmp file",
        )
    }
}
