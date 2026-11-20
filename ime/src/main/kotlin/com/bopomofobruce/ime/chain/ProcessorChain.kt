package com.bopomofobruce.ime.chain

import com.bopomofobruce.common.ImeContextProvider
import com.bopomofobruce.common.InputEvent
import com.bopomofobruce.common.InputProcessor
import com.bopomofobruce.common.InputState
import com.bopomofobruce.common.ProcessResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [InputProcessor] chain 的 dispatcher：依序呼叫 [processors]，第一個不回 [ProcessResult.Unhandled] 的結果勝出並被套用。
 * - [ProcessResult.StateChanged]：更新 [state]。
 * - [ProcessResult.TextCommit]：先 [ImeContextProvider.commitText]，再更新 [state]，最後把
 *   [InputEvent.TextCommitted] 餵回 chain 一次（讓學習型 processor 知道哪段字上屏了）。 這次回饋只接受
 *   `StateChanged`／`Unhandled`；回饋時再 commit 會形成無限迴圈，視為 processor 的程式錯誤直接 fail fast。 `text` 為空字串時視同
 *   `StateChanged`：不呼叫 commitText、也不回饋。回饋同樣是「第一個不回 `Unhandled` 的勝出」：排在前面的 processor 回了
 *   `StateChanged`，後面的 processor 就收不到這次 [InputEvent.TextCommitted]，且它的 newState 會覆蓋剛套用的 newState。
 * - 全部 `Unhandled`：什麼都不做，原樣回傳 `Unhandled`，讓上層（service）決定要不要自己處理（例如切換鍵盤）。
 *
 * **例外**：本類別不攔截任何例外。processor（含其呼叫的 decoder）或 [ImeContextProvider] 拋出的例外原樣拋給 [dispatch] 的呼叫端，由
 * service 層統一處理。commitText 成功之前拋出時 [state] 不變；之後（回饋階段）才拋出時文字已上屏、[state] 已是 newState。例外不帶
 * 階段資訊，呼叫端分不出是哪一種，所以**不要重送**該事件（可能重複上屏）。要回到乾淨狀態就送 [InputEvent.Reset]；但本類別不會自己清空 [state]，丟棄
 * composing 要靠 chain 裡處理 Reset 的 processor，全部回 `Unhandled`（或該 processor 本身就是拋例外的那個）時 [state]
 * 不變。本類別自己拋的 fail-fast 例外訊息不含上屏內容；其他來源的例外訊息 不受本類別約束，記 log 前請自行過濾。
 *
 * **執行緒責任邊界**：本類別是同步、無鎖、**非 thread-safe** 的。[dispatch] 會同步呼叫 processor，而 processor 可能同步呼叫
 * [com.bopomofobruce.common.ZhuyinDecoder.input]（契約不保證 thread-safe，真實實作會走 JNI）。 把所有 [dispatch]
 * 序列化到同一個 work dispatcher（且不要在 main thread 上跑真實 decoder）是 **service 層（W2-B2）的責任**；本類別刻意不碰 coroutine
 * dispatcher，才能用純 JVM unit test 驗證。[state] 是 [StateFlow]，UI 可從任何 thread 觀察。
 */
class ProcessorChain(
    private val processors: List<InputProcessor>,
    private val context: ImeContextProvider,
    initialState: InputState = InputState.EMPTY,
) {
    private val _state = MutableStateFlow(initialState)

    /** 目前的輸入狀態（composing／候選列）。UI 只讀，唯一的寫入者是 [dispatch]。 */
    val state: StateFlow<InputState> = _state.asStateFlow()

    /**
     * 把 [event] 送進 chain 並套用結果；回傳勝出 processor 的原始 [ProcessResult]（全部不處理時為 `Unhandled`）。空字串的
     * `TextCommit` 也原樣回傳，即使它實際上沒有上屏，呼叫端不要只憑回傳型別判斷有沒有上屏。
     */
    fun dispatch(event: InputEvent): ProcessResult {
        val result = runChain(event)
        when (result) {
            is ProcessResult.StateChanged -> _state.value = result.newState
            is ProcessResult.TextCommit -> applyCommit(result)
            ProcessResult.Unhandled -> Unit
        }
        return result
    }

    private fun applyCommit(result: ProcessResult.TextCommit) {
        if (result.text.isEmpty()) {
            _state.value = result.newState
            return
        }
        // 先 commitText 再更新 state：commitText 拋例外時 state 保持不變。
        context.commitText(result.text)
        _state.value = result.newState
        when (val feedback = runChain(InputEvent.TextCommitted(result.text))) {
            is ProcessResult.StateChanged -> _state.value = feedback.newState
            is ProcessResult.TextCommit ->
                error(
                    "Processor must not commit in response to TextCommitted (text length ${feedback.text.length})"
                )
            ProcessResult.Unhandled -> Unit
        }
    }

    private fun runChain(event: InputEvent): ProcessResult {
        val current = _state.value
        for (processor in processors) {
            val result = processor.process(event, current)
            if (result !is ProcessResult.Unhandled) return result
        }
        return ProcessResult.Unhandled
    }
}
