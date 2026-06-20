package com.bopomofobruce.common

import com.bopomofobruce.common.fakes.FakeZhuyinDecoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakeZhuyinDecoderTest {

    @Test
    fun `empty buffer returns empty list`() {
        val decoder = FakeZhuyinDecoder()
        assertTrue(decoder.input("").isEmpty())
    }

    @Test
    fun `known buffer returns deterministic candidates sorted by score`() {
        val decoder = FakeZhuyinDecoder()

        val results = decoder.input("ㄋㄧˇ")

        assertEquals(2, results.size)
        assertEquals("你", results[0].text)
        assertEquals("妳", results[1].text)
        assertTrue(results[0].score > results[1].score, "candidates should be score-descending")
        assertTrue(results.all { it.source == CandidateSource.PRIMARY })
    }

    @Test
    fun `unknown buffer returns single low-score fallback`() {
        val decoder = FakeZhuyinDecoder()

        val results = decoder.input("ㄓㄨㄤ")

        assertEquals(1, results.size)
        assertEquals("ㄓㄨㄤ", results[0].text)
        assertTrue(results[0].score < 0.5f)
    }

    @Test
    fun `commit appends to history`() {
        val decoder = FakeZhuyinDecoder()
        val pick = Candidate("你", 0.95f, CandidateSource.PRIMARY)

        decoder.commit(pick)
        decoder.commit(pick)

        assertEquals(2, decoder.committedHistory.size)
        assertEquals(pick, decoder.committedHistory[0])
    }

    @Test
    fun `reset clears history and bumps counter`() {
        val decoder = FakeZhuyinDecoder()
        decoder.commit(Candidate("好", 0.97f, CandidateSource.PRIMARY))
        assertEquals(0, decoder.resetCallCount)

        decoder.reset()

        assertEquals(1, decoder.resetCallCount)
        assertTrue(decoder.committedHistory.isEmpty())
        // 再呼叫一次確認 counter 不會被 history-clear 影響。
        decoder.reset()
        assertEquals(2, decoder.resetCallCount)
        assertFalse(decoder.committedHistory.isNotEmpty())
    }
}
