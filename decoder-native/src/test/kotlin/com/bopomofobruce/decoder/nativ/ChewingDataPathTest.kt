package com.bopomofobruce.decoder.nativ

import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `re-extracts a zero-length (corrupt or interrupted) previous copy`() {
        val assets = fakeAssets(mapOf("word.dat" to byteArrayOf(9, 9, 9)))
        val targetDir = File(tempDir, "cache-chewing")
        targetDir.mkdirs()
        File(targetDir, "word.dat").writeBytes(ByteArray(0))

        extractChewingData(assets, targetDir)

        assertEquals(3, File(targetDir, "word.dat").length())
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
}
