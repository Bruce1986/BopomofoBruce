package com.bopomofobruce.ime.chain

import com.bopomofobruce.common.ImeContextProvider
import com.bopomofobruce.common.InputEvent
import com.bopomofobruce.common.InputProcessor
import com.bopomofobruce.common.InputState
import com.bopomofobruce.common.ProcessResult
import com.bopomofobruce.common.fakes.FakeImeContextProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Dispatcher 本身的語意：誰先處理誰勝、commit 的副作用與回饋順序。processor 用 lambda 偽造。 */
class ProcessorChainTest {
    private val context = FakeImeContextProvider()
    private val log = mutableListOf<String>()
    private val composingA = InputState("A", emptyList(), 0)
    private val composingB = InputState("B", emptyList(), 0)

    /** 偽造的 processor／context 丟這一個實例；用 assertSame 證明 chain 原樣傳出、沒有包裝或換掉。 */
    private val original = IllegalStateException("original")

    /** 把每次 commitText 連同「當下 chain 的 composing」記進 [log]，用來驗證副作用的先後。 */
    private fun loggingContext(chain: () -> ProcessorChain) =
        object : ImeContextProvider by context {
            override fun commitText(text: String) {
                log += "commit:$text|state=${chain().state.value.composing}"
                context.commitText(text)
            }
        }

    private fun recording(name: String, result: ProcessResult) = InputProcessor { event, _ ->
        log += "$name:${event::class.simpleName}"
        result
    }

    @Test
    fun `Unhandled falls through and the first handler stops the chain`() {
        val chain =
            ProcessorChain(
                listOf(
                    recording("p1", ProcessResult.Unhandled),
                    recording("p2", ProcessResult.StateChanged(composingA)),
                    recording("p3", ProcessResult.StateChanged(InputState.EMPTY)),
                ),
                context,
            )

        assertEquals(ProcessResult.StateChanged(composingA), chain.dispatch(InputEvent.Reset))
        assertEquals(listOf("p1:Reset", "p2:Reset"), log)
        assertEquals(composingA, chain.state.value)
    }

    @Test
    fun `nobody handles - returns Unhandled and touches nothing`() {
        val chain =
            ProcessorChain(listOf(recording("p1", ProcessResult.Unhandled)), context, composingA)

        val result = chain.dispatch(InputEvent.Reset)

        assertEquals(ProcessResult.Unhandled, result)
        assertEquals(composingA, chain.state.value)
        assertEquals("", context.getCommittedText())
    }

    @Test
    fun `TextCommit commits, then updates state, then feeds TextCommitted back once`() {
        lateinit var chain: ProcessorChain
        val committer = InputProcessor { event, state ->
            when (event) {
                InputEvent.Reset -> ProcessResult.TextCommit("你", composingB)
                is InputEvent.TextCommitted -> {
                    log += "feedback:${event.text}|state=${state.composing}"
                    ProcessResult.StateChanged(composingA)
                }
                else -> ProcessResult.Unhandled
            }
        }
        chain = ProcessorChain(listOf(committer), loggingContext { chain }, composingA)

        val result = chain.dispatch(InputEvent.Reset)

        // 回傳的是勝出的原始結果，不是回饋的結果
        assertEquals(ProcessResult.TextCommit("你", composingB), result)
        // commitText 時 state 仍是舊的 A；回饋看到的是 TextCommit 的新 state B
        assertEquals(listOf("commit:你|state=A", "feedback:你|state=B"), log)
        assertEquals("你", context.getCommittedText())
        assertEquals(composingA, chain.state.value) // 回饋的 StateChanged 有被套用
    }

    @Test
    fun `TextCommit newState is kept when nobody handles the feedback`() {
        val committer = InputProcessor { event, _ ->
            if (event == InputEvent.Reset) ProcessResult.TextCommit("你", composingB)
            else ProcessResult.Unhandled
        }
        val chain = ProcessorChain(listOf(committer), context)

        chain.dispatch(InputEvent.Reset)

        assertEquals(composingB, chain.state.value)
    }

    @Test
    fun `whitespace-only TextCommit is still fed back, even when state does not change`() {
        val spacer = InputProcessor { event, _ ->
            if (event !is InputEvent.TextCommitted) ProcessResult.TextCommit(" ", InputState.EMPTY)
            else ProcessResult.Unhandled.also { log += "feedback:[${event.text}]" }
        }
        ProcessorChain(listOf(spacer), context).dispatch(InputEvent.Reset)

        assertEquals(" ", context.getCommittedText()) // 純空白也要上屏
        assertEquals(listOf("feedback:[ ]"), log)
    }

    @Test
    fun `empty TextCommit only updates state - no commitText and no feedback`() {
        lateinit var chain: ProcessorChain
        val empty = recording("p1", ProcessResult.TextCommit("", composingA))
        chain = ProcessorChain(listOf(empty), loggingContext { chain })

        chain.dispatch(InputEvent.Reset)

        assertEquals(listOf("p1:Reset"), log)
        assertEquals(composingA, chain.state.value)
    }

    @Test
    fun `an exception from a processor propagates, leaves state unchanged and stops the chain`() {
        val boom = InputProcessor { _, _ -> throw original }
        val chain =
            ProcessorChain(
                listOf(boom, recording("p2", ProcessResult.StateChanged(composingB))),
                context,
                composingA,
            )

        assertSame(
            original,
            assertThrows<IllegalStateException> { chain.dispatch(InputEvent.Reset) },
        )
        assertEquals(composingA, chain.state.value)
        assertTrue(log.isEmpty()) // 沒有被吞掉後交給下一個 processor
    }

    @Test
    fun `an exception from a feedback processor propagates after the text landed`() {
        val committer = InputProcessor { event, _ ->
            if (event is InputEvent.TextCommitted) throw original
            ProcessResult.TextCommit("你", composingB)
        }
        val chain = ProcessorChain(listOf(committer), context, composingA)

        assertSame(
            original,
            assertThrows<IllegalStateException> { chain.dispatch(InputEvent.Reset) },
        )
        assertEquals("你", context.getCommittedText())
        assertEquals(composingB, chain.state.value)
    }

    @Test
    fun `an exception from commitText propagates and leaves state unchanged`() {
        val failing =
            object : ImeContextProvider by context {
                override fun commitText(text: String) = throw original
            }
        val committer = recording("p1", ProcessResult.TextCommit("你", InputState.EMPTY))
        val chain = ProcessorChain(listOf(committer), failing, composingA)

        assertSame(
            original,
            assertThrows<IllegalStateException> { chain.dispatch(InputEvent.Reset) },
        )
        assertEquals(composingA, chain.state.value)
        assertEquals(listOf("p1:Reset"), log) // 沒有回饋
    }

    @Test
    fun `processors see the state left by the previous dispatch`() {
        val seenComposing = mutableListOf<String>()
        val appender = InputProcessor { _, state ->
            seenComposing += state.composing
            ProcessResult.StateChanged(InputState(state.composing + "x", emptyList(), 0))
        }
        val chain = ProcessorChain(listOf(appender), context)

        chain.dispatch(InputEvent.Reset)
        chain.dispatch(InputEvent.Reset)

        assertEquals(listOf("", "x"), seenComposing)
        assertEquals("xx", chain.state.value.composing)
    }

    @Test
    fun `committing again in response to TextCommitted fails fast, after the first commit landed`() {
        val looper = InputProcessor { _, _ -> ProcessResult.TextCommit("秘密", composingB) }
        val chain = ProcessorChain(listOf(looper), context, composingA)

        val error = assertThrows<IllegalStateException> { chain.dispatch(InputEvent.Reset) }
        assertTrue(error.message!!.contains("TextCommitted"))
        assertFalse(error.message!!.contains("秘密")) // 使用者輸入不進例外訊息
        assertEquals("秘密", context.getCommittedText()) // 回饋階段才拋：字已上屏
        assertEquals(composingB, chain.state.value) // state 已是 newState
    }

    @Test
    fun `an empty TextCommit in response to TextCommitted also fails fast`() {
        val looper = InputProcessor { event, _ ->
            if (event is InputEvent.TextCommitted) ProcessResult.TextCommit("", composingA)
            else ProcessResult.TextCommit("你", composingB)
        }
        val chain = ProcessorChain(listOf(looper), context, composingA)

        assertThrows<IllegalStateException> { chain.dispatch(InputEvent.Reset) }
        assertEquals("你", context.getCommittedText())
        assertEquals(composingB, chain.state.value)
    }

    @Test
    fun `empty TextCommit is returned as-is`() {
        val chain =
            ProcessorChain(
                listOf(recording("p1", ProcessResult.TextCommit("", composingA))),
                context,
            )

        assertEquals(ProcessResult.TextCommit("", composingA), chain.dispatch(InputEvent.Reset))
    }

    @Test
    fun `TextCommitted feedback - first non-Unhandled wins and later processors are not asked`() {
        val committer = InputProcessor { event, _ ->
            if (event == InputEvent.Reset) ProcessResult.TextCommit("你", InputState.EMPTY)
            else ProcessResult.Unhandled
        }
        val first = InputProcessor { event, _ ->
            if (event is InputEvent.TextCommitted) ProcessResult.StateChanged(composingA)
            else ProcessResult.Unhandled
        }
        val later = recording("later", ProcessResult.StateChanged(composingB))
        val chain = ProcessorChain(listOf(committer, first, later), context)

        chain.dispatch(InputEvent.Reset)

        assertTrue(log.isEmpty()) // later 沒被問到
        assertEquals(composingA, chain.state.value)
    }

    @Test
    fun `re-entrant dispatch fails fast, leaves state unchanged and the chain stays usable`() {
        lateinit var chain: ProcessorChain
        var reenter = true
        val processor = InputProcessor { _, _ ->
            if (reenter) chain.dispatch(InputEvent.Reset)
            ProcessResult.StateChanged(composingB)
        }
        chain = ProcessorChain(listOf(processor), context, composingA)

        val error = assertThrows<IllegalStateException> { chain.dispatch(InputEvent.Reset) }
        assertTrue(error.message!!.contains("re-entrantly"))
        assertEquals(composingA, chain.state.value)

        reenter = false
        chain.dispatch(InputEvent.Reset)
        assertEquals(composingB, chain.state.value)
    }

    @Test
    fun `re-entrant dispatch from commitText fails fast with the text sent and state not updated`() {
        lateinit var chain: ProcessorChain
        var reenter = true
        val reentrantContext =
            object : ImeContextProvider by context {
                override fun commitText(text: String) {
                    context.commitText(text)
                    if (reenter) chain.dispatch(InputEvent.Reset)
                }
            }
        val committer = InputProcessor { event, _ ->
            if (event == InputEvent.Reset) ProcessResult.TextCommit("你", composingB)
            else ProcessResult.Unhandled
        }
        chain = ProcessorChain(listOf(committer), reentrantContext, composingA)

        assertThrows<IllegalStateException> { chain.dispatch(InputEvent.Reset) }
        assertEquals("你", context.getCommittedText())
        assertEquals(composingA, chain.state.value)

        reenter = false
        chain.dispatch(InputEvent.Reset) // 例外後旗標已復原
        assertEquals("你你", context.getCommittedText())
        assertEquals(composingB, chain.state.value)
    }

    @Test
    fun `re-entrant dispatch from the feedback phase fails fast after the text landed`() {
        lateinit var chain: ProcessorChain
        var reenter = true
        val committer = InputProcessor { event, _ ->
            if (event is InputEvent.TextCommitted) {
                if (reenter) chain.dispatch(InputEvent.Reset)
                ProcessResult.Unhandled
            } else ProcessResult.TextCommit("你", composingB)
        }
        chain = ProcessorChain(listOf(committer), context, composingA)

        val error = assertThrows<IllegalStateException> { chain.dispatch(InputEvent.Reset) }
        assertTrue(error.message!!.contains("re-entrantly"))
        assertEquals("你", context.getCommittedText()) // 內層沒有再上屏一次
        assertEquals(composingB, chain.state.value)

        reenter = false
        chain.dispatch(InputEvent.Reset) // 例外後旗標已復原
        assertEquals("你你", context.getCommittedText())
    }

    @Test
    fun `a swallowed re-entrancy error does not let a second re-entrant dispatch through`() {
        lateinit var chain: ProcessorChain
        val attempts = mutableListOf<Throwable?>()
        val processor = InputProcessor { _, _ ->
            if (attempts.isEmpty()) {
                repeat(2) {
                    attempts += runCatching { chain.dispatch(InputEvent.Reset) }.exceptionOrNull()
                }
            }
            ProcessResult.StateChanged(composingB)
        }
        chain = ProcessorChain(listOf(processor), context, composingA)

        chain.dispatch(InputEvent.Reset)

        // 第一次重入被拒之後，外層還在跑，第二次也要被拒
        assertEquals(2, attempts.size)
        assertTrue(attempts.all { it is IllegalStateException })
        assertEquals(composingB, chain.state.value)
    }

    @Test
    fun `TextCommitted feedback reaches a later processor when earlier ones return Unhandled`() {
        val committer = InputProcessor { event, _ ->
            if (event == InputEvent.Reset) ProcessResult.TextCommit("你", composingB)
            else ProcessResult.Unhandled
        }
        val learner = InputProcessor { event, _ ->
            if (event is InputEvent.TextCommitted) {
                log += "learn:${event.text}"
                ProcessResult.StateChanged(composingA)
            } else ProcessResult.Unhandled
        }
        val chain = ProcessorChain(listOf(committer, learner), context)

        chain.dispatch(InputEvent.Reset)

        assertEquals(listOf("learn:你"), log)
        assertEquals(composingA, chain.state.value)
    }
}
