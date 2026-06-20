package com.bopomofobruce.common

/**
 * Chain of Responsibility 的一個節點。
 *
 * :ime 會持有一條 ordered list of [InputProcessor]，依序呼叫 [process]：
 * - 回傳 [ProcessResult.Unhandled] → 試下一個 processor。
 * - 回傳 [ProcessResult.StateChanged] / [ProcessResult.TextCommit] → 停止 chain、套用結果。
 *
 * 典型的 chain 順序：
 * 1. ZhuyinProcessor（[KeyAction.Zhuyin] → 餵給 [ZhuyinDecoder]）
 * 2. CandidateSelectionProcessor（空白/Enter/數字鍵選候選）
 * 3. BackspaceProcessor（先吃 composing buffer、再吃 surrounding text）
 * 4. DirectInputProcessor（沒 buffer 時的英文/符號直接上屏）
 *
 * fun interface 讓測試可以用 lambda 偽造一個 processor，不用每次 new 一個 class。
 */
fun interface InputProcessor {
    fun process(event: InputEvent, state: InputState): ProcessResult
}
