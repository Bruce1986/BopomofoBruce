package com.bopomofobruce.decoder

import com.bopomofobruce.common.Candidate
import com.bopomofobruce.common.CandidateSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChewingZhuyinDecoderTest {

    private class FakeNative(
        private val initHandle: Long = 42L,
        private val outputs: Map<String, String> = mapOf("ㄋㄧˇ" to "你\n妳\n擬", "ㄏㄠˇ" to "好\n郝"),
    ) : BpmfNative {
        val calls = mutableListOf<String>()

        override fun init(dataPath: String) = initHandle.also { calls += "init($dataPath)" }

        override fun input(handle: Long, zhuyin: String): ByteArray {
            calls += "input($handle,$zhuyin)"
            return (outputs[zhuyin] ?: "").toByteArray(Charsets.UTF_8)
        }

        override fun commit(handle: Long, index: Int) {
            calls += "commit($handle,$index)"
        }

        override fun free(handle: Long) {
            calls += "free($handle)"
        }
    }

    @Test
    fun `init returning 0 throws and never frees`() {
        val native = FakeNative(initHandle = 0L)
        assertThrows<IllegalStateException> { ChewingZhuyinDecoder(native, "/data") }
        assertEquals(listOf("init(/data)"), native.calls)
    }

    @Test
    fun `input keeps native order with strictly descending scores in range`() {
        val native = FakeNative()
        val result = ChewingZhuyinDecoder(native, "/data").input("ㄋㄧˇ")

        assertEquals(listOf("你", "妳", "擬"), result.map { it.text })
        assertEquals(listOf(1f, 0.5f, 1f / 3), result.map { it.score })
        assertTrue(result.all { it.source == CandidateSource.PRIMARY })
        assertEquals("input(42,ㄋㄧˇ)", native.calls.last())
        assertEquals(emptyList<Candidate>(), ChewingZhuyinDecoder(native, "/d").input("ㄏ"))
    }

    @Test
    fun `splitCandidates decodes standard UTF-8 including supplementary characters`() {
        // U+20000 是 4-byte 標準 UTF-8；JNI 的 NewStringUTF（modified UTF-8）會解錯，所以 native 端回 bytes。
        val bytes = "𠀀\n你".toByteArray(Charsets.UTF_8)
        assertEquals(listOf("𠀀", "你"), splitCandidates(bytes))
        assertEquals(emptyList<String>(), splitCandidates(ByteArray(0)))
    }

    @Test
    fun `empty segments are skipped but commit keeps the native index`() {
        val native = FakeNative(outputs = mapOf("ㄋㄧˇ" to "你\n\n妳"))
        val decoder = ChewingZhuyinDecoder(native, "/data")
        val result = decoder.input("ㄋㄧˇ")

        assertEquals(listOf("你", "妳"), result.map { it.text })
        assertEquals(listOf(1f, 0.5f), result.map { it.score })
        decoder.commit(result[1])
        assertEquals("commit(42,2)", native.calls.last())
    }

    @Test
    fun `commit forwards the index of the chosen candidate`() {
        val native = FakeNative()
        val decoder = ChewingZhuyinDecoder(native, "/data")
        decoder.commit(decoder.input("ㄋㄧˇ")[1])
        assertEquals("commit(42,1)", native.calls.last())
    }

    @Test
    fun `commit of a candidate not from the latest input is a no-op`() {
        val native = FakeNative()
        val decoder = ChewingZhuyinDecoder(native, "/data")
        val stale = decoder.input("ㄋㄧˇ")[1]
        decoder.input("ㄏㄠˇ")

        decoder.commit(stale)
        // 與最新結果第 0 個同字同分、只差來源：不是這個 decoder 給的候選，不能被當成它。
        decoder.commit(Candidate("好", 1f, CandidateSource.PERSONAL))
        assertTrue(native.calls.none { it.startsWith("commit") }, native.calls.toString())
    }

    @Test
    fun `reset clears native composition and forgets candidates`() {
        val native = FakeNative()
        val decoder = ChewingZhuyinDecoder(native, "/data")
        val first = decoder.input("ㄋㄧˇ")[0]

        decoder.reset()
        decoder.commit(first)
        assertEquals("input(42,)", native.calls.last())
    }

    @Test
    fun `close frees exactly once and later calls never reach native`() {
        val native = FakeNative()
        val decoder = ChewingZhuyinDecoder(native, "/data")
        val first = decoder.input("ㄋㄧˇ")[0]

        decoder.close()
        decoder.close()
        assertEquals(emptyList<Candidate>(), decoder.input("ㄋㄧˇ"))
        decoder.commit(first)
        decoder.reset()
        assertEquals(listOf("init(/data)", "input(42,ㄋㄧˇ)", "free(42)"), native.calls)
    }
}
