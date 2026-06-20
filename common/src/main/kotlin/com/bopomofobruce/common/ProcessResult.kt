package com.bopomofobruce.common

/**
 * [InputProcessor.process] 的回傳值。
 *
 * 三種變體對應「chain dispatcher 該怎麼往下走」：
 * - [StateChanged]：processor 處理了事件、僅更新內部狀態（例如剛把一個注音 push 進 buffer）。 Dispatcher 應停止傳遞 event 給 chain
 *   下游，並把 [newState] 廣播給 UI。
 * - [TextCommit]：processor 決定要把 [text] 送上屏。Dispatcher 應呼叫 [ImeContextProvider.commitText] 再廣播
 *   [newState]（通常是 [InputState.EMPTY] 或剩餘 buffer 的狀態）。
 * - [Unhandled]：這個 processor 不處理這個 event，dispatcher 應交給 chain 下一個 processor。
 *
 * 沒有「ConsumedNoChange」變體 — 若 processor 處理了但 state 沒變，回傳 `StateChanged(oldState)` 也 OK（data class
 * 比較會省一次 broadcast）。
 */
sealed interface ProcessResult {
    data class StateChanged(val newState: InputState) : ProcessResult

    data class TextCommit(val text: String, val newState: InputState) : ProcessResult

    data object Unhandled : ProcessResult
}
