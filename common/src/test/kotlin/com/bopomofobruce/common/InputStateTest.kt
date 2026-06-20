package com.bopomofobruce.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class InputStateTest {

    private val a = Candidate("a", 0.9f, CandidateSource.PRIMARY)
    private val b = Candidate("b", 0.5f, CandidateSource.PRIMARY)
    private val c = Candidate("c", 0.3f, CandidateSource.PRIMARY)

    @Test
    fun `EMPTY is the canonical zero-composing state`() {
        val s = InputState.EMPTY
        assertEquals("", s.composing)
        assertEquals(emptyList<Candidate>(), s.candidates)
        assertEquals(0, s.cursorIndex)
    }

    @Test
    fun `init rejects non-zero cursorIndex when candidates is empty`() {
        assertThrows(IllegalArgumentException::class.java) {
            InputState(composing = "ㄋ", candidates = emptyList(), cursorIndex = 1)
        }
    }

    @Test
    fun `init rejects out-of-bounds cursorIndex when candidates is non-empty`() {
        assertThrows(IllegalArgumentException::class.java) {
            InputState(composing = "ㄋㄧˇ", candidates = listOf(a, b), cursorIndex = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InputState(composing = "ㄋㄧˇ", candidates = listOf(a), cursorIndex = -1)
        }
    }

    @Test
    fun `copyWithCandidates clamps cursor to 0 when newCandidates is empty`() {
        val s = InputState(composing = "ㄋㄧˇ", candidates = listOf(a, b, c), cursorIndex = 2)
        val updated = s.copyWithCandidates(emptyList())
        assertEquals(0, updated.cursorIndex)
        assertEquals(emptyList<Candidate>(), updated.candidates)
        assertEquals("ㄋㄧˇ", updated.composing)
    }

    @Test
    fun `copyWithCandidates keeps cursor unchanged when still in range`() {
        val s = InputState(composing = "ㄋㄧˇ", candidates = listOf(a, b, c), cursorIndex = 1)
        val updated = s.copyWithCandidates(listOf(a, b))
        assertEquals(1, updated.cursorIndex)
        assertEquals(listOf(a, b), updated.candidates)
    }

    @Test
    fun `copyWithCandidates coerces cursor down when out of range`() {
        // 原本 cursor=2，但 newCandidates 只剩 2 個（indices 0..1），coerce 到 1
        val s = InputState(composing = "ㄋㄧˇ", candidates = listOf(a, b, c), cursorIndex = 2)
        val updated = s.copyWithCandidates(listOf(a, b))
        assertEquals(1, updated.cursorIndex)
    }
}
