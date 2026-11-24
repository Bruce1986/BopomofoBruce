package com.bopomofobruce.ime.chain

import com.bopomofobruce.common.Candidate
import com.bopomofobruce.common.CandidateSource.PRIMARY
import com.bopomofobruce.common.InputEvent
import com.bopomofobruce.common.InputState
import com.bopomofobruce.common.KeyAction
import com.bopomofobruce.common.KeyData
import com.bopomofobruce.common.LongPressData
import com.bopomofobruce.common.ProcessResult
import com.bopomofobruce.common.ZhuyinDecoder
import com.bopomofobruce.common.fakes.FakeImeContextProvider
import com.bopomofobruce.common.fakes.FakeZhuyinDecoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** 預設 chain 的端到端情境：按鍵序列 → context 收到什麼、state 剩什麼。 */
class DefaultProcessorsTest {
    private val decoder = FakeZhuyinDecoder()
    private val context = FakeImeContextProvider()
    private val shift = ShiftLatch()
    private val misreadLongPress = LongPressData("長", KeyAction.Character('長')) // 誤讀長按會打出「長」

    private fun chain(d: ZhuyinDecoder = decoder, initial: InputState = InputState.EMPTY) =
        ProcessorChain(defaultProcessors(d, context, shift), context, initial)

    private fun key(action: KeyAction) =
        InputEvent.KeyPressed(KeyData("k", action, longPress = misreadLongPress))

    private fun ProcessorChain.press(vararg actions: KeyAction) =
        actions.forEach { dispatch(key(it)) }

    private fun ProcessorChain.type(zhuyin: String) =
        zhuyin.forEach { press(KeyAction.Zhuyin(it.toString())) }

    private val texts: InputState.() -> List<String> = { candidates.map { it.text } }

    @Test
    fun `typing ni3 then space commits the top candidate and clears composing`() {
        val c = chain()
        c.type("ㄋㄧˇ")
        assertEquals("ㄋㄧˇ", c.state.value.composing)
        assertEquals(listOf("你", "妳"), c.state.value.texts())

        c.press(KeyAction.Space)

        assertEquals("你", context.getCommittedText())
        assertEquals(InputState.EMPTY, c.state.value)
        assertEquals(listOf("你"), decoder.committedHistory.map { it.text })
    }

    @Test
    fun `tapping a candidate commits it, a stale out-of-range tap is swallowed`() {
        val c = chain()
        c.type("ㄕˋ")
        val before = c.state.value
        assertEquals(
            ProcessResult.StateChanged(before),
            c.dispatch(InputEvent.CandidateSelected(3)),
        )
        assertEquals("", context.getCommittedText())

        c.dispatch(InputEvent.CandidateSelected(1))

        assertEquals("事", context.getCommittedText())
        assertEquals(InputState.EMPTY, c.state.value)
        assertEquals(listOf("事"), decoder.committedHistory.map { it.text })
    }

    @Test
    fun `backspace while composing removes only the last symbol and never touches context`() {
        val c = chain()
        c.type("ㄋㄧˇ")
        c.press(KeyAction.Backspace)

        assertEquals("ㄋㄧ", c.state.value.composing)
        assertEquals(listOf("ㄋㄧ"), c.state.value.texts()) // 用新 buffer 重查（fake 的 fallback）
        assertTrue(context.deleteCalls.isEmpty() && context.sentKeys.isEmpty())
        assertEquals("", context.getCommittedText())

        c.press(KeyAction.Backspace, KeyAction.Backspace)
        assertEquals(InputState.EMPTY, c.state.value)
        assertTrue(context.deleteCalls.isEmpty() && context.sentKeys.isEmpty())
    }

    @Test
    fun `backspace while composing removes a whole code point, never half a surrogate pair`() {
        val c = chain()
        c.press(KeyAction.Zhuyin("ㄋ"), KeyAction.Zhuyin("\uD840\uDC00"), KeyAction.Backspace)

        assertEquals("ㄋ", c.state.value.composing)
    }

    @Test
    fun `backspace with empty composing deletes one char before the cursor`() {
        val c = chain()
        c.press(KeyAction.Character('a'), KeyAction.Character('b'))
        // 已處理就回 StateChanged：若回 Unhandled，上層可能當成沒人處理而再補刪一次（W2-B2 的假設，:common 未規定）
        assertEquals(
            ProcessResult.StateChanged(InputState.EMPTY),
            c.dispatch(key(KeyAction.Backspace)),
        )

        assertEquals(listOf(1 to 0), context.deleteCalls)
        assertEquals("a", context.getCommittedText())
    }

    @Test
    fun `enter while composing commits the raw zhuyin, not a candidate`() {
        val c = chain()
        c.type("ㄋㄧˇ")
        c.press(KeyAction.Enter)

        assertEquals("ㄋㄧˇ", context.getCommittedText())
        assertEquals(InputState.EMPTY, c.state.value)
        assertTrue(decoder.committedHistory.isEmpty())
        assertTrue(context.sentKeys.isEmpty())
    }

    @Test
    fun `enter with empty composing sends KEYCODE_ENTER`() {
        val result = chain().dispatch(key(KeyAction.Enter))

        assertEquals(ProcessResult.StateChanged(InputState.EMPTY), result)
        assertEquals(listOf(66), context.sentKeys) // android.view.KeyEvent.KEYCODE_ENTER
        assertEquals("", context.getCommittedText())
    }

    @Test
    fun `shift uppercases exactly the next character, twice cancels, space types a space`() {
        val c = chain()
        assertEquals(ProcessResult.StateChanged(InputState.EMPTY), c.dispatch(key(KeyAction.Shift)))
        assertTrue(shift.active.value)

        c.press(KeyAction.Character('a'), KeyAction.Character('b'), KeyAction.Space)
        assertEquals("Ab ", context.getCommittedText())
        assertFalse(shift.active.value)

        c.press(KeyAction.Shift, KeyAction.Shift, KeyAction.Character('c'))
        assertEquals("Ab c", context.getCommittedText())

        c.press(KeyAction.Shift, KeyAction.Space, KeyAction.Character('d')) // 空白不消耗 Shift
        assertEquals("Ab c D", context.getCommittedText())
    }

    @Test
    fun `shift while composing keeps composing and uppercases the next character`() {
        val c = chain()
        c.type("ㄋㄧˇ")
        val before = c.state.value
        c.press(KeyAction.Shift)
        assertEquals(before, c.state.value)

        c.press(KeyAction.Character('a'))
        assertEquals("你A", context.getCommittedText())
        assertFalse(shift.active.value)
        assertEquals(InputState.EMPTY, c.state.value)
        assertEquals(listOf("你"), decoder.committedHistory.map { it.text })
    }

    @Test
    fun `space commits the candidate under the cursor, not the first one`() {
        val c = chain(initial = InputState("ㄋㄧˇ", decoder.input("ㄋㄧˇ"), 1))
        c.press(KeyAction.Space)

        assertEquals("妳", context.getCommittedText())
        assertEquals(listOf("妳"), decoder.committedHistory.map { it.text })
    }

    @Test
    fun `character while composing commits the highlighted candidate followed by the character`() {
        val c = chain()
        c.type("ㄋㄧˇ")
        c.press(KeyAction.Character('，'))

        assertEquals("你，", context.getCommittedText())
        assertEquals(InputState.EMPTY, c.state.value)
        assertEquals(listOf("你"), decoder.committedHistory.map { it.text })
    }

    @Test
    fun `reset drops composing without committing and resets the decoder`() {
        val c = chain()
        c.type("ㄋㄧˇ")
        c.press(KeyAction.Shift)
        c.dispatch(InputEvent.Reset)

        assertEquals(InputState.EMPTY, c.state.value)
        assertEquals("", context.getCommittedText())
        assertEquals(1, decoder.resetCallCount)
        assertFalse(shift.active.value)

        c.dispatch(InputEvent.Reset) // composing 空、Shift 沒按：仍要 reset decoder，且不能打開 Shift
        assertFalse(shift.active.value)
        assertEquals(2, decoder.resetCallCount)
        c.press(KeyAction.Shift) // composing 空、Shift 已按：Reset 也要放開
        c.dispatch(InputEvent.Reset)
        assertFalse(shift.active.value)
        assertEquals(3, decoder.resetCallCount)
    }

    @Test
    fun `keyboard switch keys are left to the host and keep composing`() {
        val c = chain()
        val deferred =
            listOf(
                KeyAction.SymbolToggle,
                KeyAction.LanguageToggle,
                KeyAction.Custom("switch_back"),
            )
        val assertDeferred = {
            deferred.forEach { assertEquals(ProcessResult.Unhandled, c.dispatch(key(it)), "$it") }
        }

        assertDeferred() // composing 空
        assertEquals(InputState.EMPTY, c.state.value)
        c.type("ㄋㄧˇ")
        val before = c.state.value
        assertDeferred() // composing 非空

        assertEquals(before, c.state.value)
        assertEquals("", context.getCommittedText())
        assertTrue(context.sentKeys.isEmpty() && context.deleteCalls.isEmpty())
    }

    @Test
    fun `candidates are re-sorted by score (ties keep decoder order) so space picks the best one`() {
        val c =
            chain(
                StubDecoder(
                    Candidate("低", 0.2f, PRIMARY),
                    Candidate("甲", 0.5f, PRIMARY),
                    Candidate("乙", 0.5f, PRIMARY),
                    Candidate("高", 0.9f, PRIMARY),
                )
            )
        c.type("ㄍ")
        assertEquals(listOf("高", "甲", "乙", "低"), c.state.value.texts())

        c.press(KeyAction.Space)
        assertEquals("高", context.getCommittedText())
    }

    @Test
    fun `with no candidates every key acts on the raw composing buffer`() {
        val c = chain(StubDecoder()) // ZhuyinDecoder.input 的 KDoc 允許回 0 個候選（「可能空」）
        c.type("ㄍㄍ") // 下兩行：退格只刪 buffer；Space、Enter、字元各上屏當下的原文（字元接在後）
        c.press(KeyAction.Backspace, KeyAction.Space, KeyAction.Zhuyin("ㄍ"), KeyAction.Enter)
        c.press(KeyAction.Zhuyin("ㄍ"), KeyAction.Shift, KeyAction.Character('a'))

        assertEquals("ㄍㄍㄍA", context.getCommittedText())
        assertEquals(InputState.EMPTY, c.state.value)
        assertTrue(context.deleteCalls.isEmpty() && context.sentKeys.isEmpty())
    }

    @Test
    fun `backspacing to an empty buffer leaves no ghost candidates even if the decoder has some`() {
        val c = chain(StubDecoder(Candidate("高", 0.9f, PRIMARY))) // stub 對 "" 也會回候選
        c.type("ㄍ")
        c.press(KeyAction.Backspace)

        assertEquals(InputState.EMPTY, c.state.value)
    }

    @Test
    fun `only a character consumes shift, with or without composing`() {
        val c = chain()
        c.press(KeyAction.Shift)
        c.press(KeyAction.Backspace, KeyAction.Enter, KeyAction.Space) // composing 空
        c.type("ㄋㄧˇ")
        c.press(KeyAction.Backspace, KeyAction.Space, KeyAction.Zhuyin("ㄚ"))
        c.dispatch(InputEvent.CandidateSelected(99)) // 越界、被吞掉
        c.press(
            KeyAction.SymbolToggle,
            KeyAction.LanguageToggle,
            KeyAction.Custom("x"),
            KeyAction.Enter,
        )
        assertTrue(shift.active.value)
        c.type("ㄋㄧˇ")
        c.dispatch(InputEvent.CandidateSelected(1)) // 有效的候選點擊也不消耗
        assertEquals("妳", context.getCommittedText().takeLast(1))
        assertTrue(shift.active.value)
        c.press(KeyAction.Character('x'))
        assertFalse(shift.active.value)
    }

    @Test
    fun `shift uppercases only the character, not the composing text committed before it`() {
        val c = chain(StubDecoder(Candidate("ab", 0.9f, PRIMARY)))
        c.type("ㄍ")
        c.press(KeyAction.Shift, KeyAction.Character('x'))

        assertEquals("abX", context.getCommittedText())
    }

    @Test
    fun `a decoder commit that throws leaves shift pressed`() {
        val throwing =
            object : ZhuyinDecoder by StubDecoder(Candidate("高", 0.9f, PRIMARY)) {
                override fun commit(candidate: Candidate) = error("decoder down")
            }
        val c = chain(throwing)
        c.type("ㄍ")
        c.press(KeyAction.Shift)

        runCatching { c.press(KeyAction.Character('a')) }

        assertTrue(shift.active.value)
    }

    @Test
    fun `a decoder reset that throws still releases shift but keeps composing`() {
        val throwing =
            object : ZhuyinDecoder by StubDecoder(Candidate("高", 0.9f, PRIMARY)) {
                override fun reset() = error("decoder down")
            }
        val c = chain(throwing)
        c.type("ㄍ")
        val before = c.state.value
        c.press(KeyAction.Shift)

        assertThrows<IllegalStateException> { c.dispatch(InputEvent.Reset) } // 例外要拋給 service

        assertFalse(shift.active.value)
        assertEquals(listOf("高"), before.texts())
        assertEquals(before, c.state.value) // 現況：state 沒清，見 DefaultProcessors 的 TODO
    }

    /** 不論 buffer 都回固定 [candidates]（造 fake 給不出的「亂序」「無候選」）；commit 只收自己給過的候選，原文回饋就炸。 */
    private class StubDecoder(vararg val candidates: Candidate) : ZhuyinDecoder {
        override fun input(buffer: String) = candidates.toList()

        override fun commit(candidate: Candidate) = require(candidate in candidates) { "raw text" }

        override fun reset() = Unit
    }
}
