package com.bopomofobruce.common

/**
 * 整個輸入子系統在任一時刻的 immutable 快照。
 *
 * 之所以是 single source of truth：[InputProcessor] chain 要做 reducer 風格（state in → state out），任何一個
 * processor 拿到完整 state 才能做正確決策（例如「有 composing 時 backspace 改刪 buffer 而不是 surrounding text」）。
 * - [composing]：注音 buffer（尚未上屏的 ㄅㄆㄇㄈ 序列）。
 * - [candidates]：當前候選詞 list；空 = 沒有候選列要顯示。
 * - [cursorIndex]：在 [candidates] 中目前 highlight 的索引（方向鍵 / hardware keyboard 用）。 必須 滿足 `0 <=
 *   cursorIndex < candidates.size`，當 candidates 為空時 [cursorIndex] 約定為 0 （不檢查、不丟）。 :ime 套用前應自行
 *   coerce。
 *
 * 用 [EMPTY] 表示「沒在 composing」初始狀態。
 */
data class InputState(
    val composing: String,
    val candidates: List<Candidate>,
    val cursorIndex: Int,
) {
    companion object {
        val EMPTY: InputState =
            InputState(composing = "", candidates = emptyList(), cursorIndex = 0)
    }
}
