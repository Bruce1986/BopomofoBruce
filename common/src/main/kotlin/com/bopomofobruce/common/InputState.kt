package com.bopomofobruce.common

/**
 * 整個輸入子系統在任一時刻的 immutable 快照。
 *
 * 之所以是 single source of truth：[InputProcessor] chain 要做 reducer 風格（state in → state out），任何一個
 * processor 拿到完整 state 才能做正確決策（例如「有 composing 時 backspace 改刪 buffer 而不是 surrounding text」）。
 * - [composing]：注音 buffer（尚未上屏的 ㄅㄆㄇㄈ 序列）。
 * - [candidates]：當前候選詞 list；空 = 沒有候選列要顯示。
 * - [cursorIndex]：在 [candidates] 中目前 highlight 的索引（方向鍵 / hardware keyboard 用）。
 *
 *   **不變量（init enforced）**：
 *     - candidates 非空 → `cursorIndex in candidates.indices`
 *     - candidates 為空 → `cursorIndex == 0`
 *
 *   違反就 throw `IllegalArgumentException`，避免不合法狀態在 processor chain 沿路傳遞到 UI 才爆。
 *
 * 用 [EMPTY] 表示「沒在 composing」初始狀態。
 */
data class InputState(
    val composing: String,
    val candidates: List<Candidate>,
    val cursorIndex: Int,
) {
    init {
        if (candidates.isEmpty()) {
            require(cursorIndex == 0) { "cursorIndex must be 0 when candidates is empty" }
        } else {
            require(cursorIndex in candidates.indices) {
                "cursorIndex $cursorIndex out of bounds for candidates of size ${candidates.size}"
            }
        }
    }

    /**
     * 安全地更新 [candidates] 並把 [cursorIndex] coerce 到新範圍。
     *
     * Processor 處理「decoder 返回新候選列表」時用這個，避免直接 `copy(candidates = ...)` 因 init `require` 觸發
     * [IllegalArgumentException]（例如 cursorIndex 還停在舊大小範圍）。
     */
    fun copyWithCandidates(newCandidates: List<Candidate>): InputState {
        val newCursor =
            if (newCandidates.isEmpty()) 0 else cursorIndex.coerceIn(newCandidates.indices)
        return InputState(
            composing = composing,
            candidates = newCandidates,
            cursorIndex = newCursor,
        )
    }

    companion object {
        val EMPTY: InputState =
            InputState(composing = "", candidates = emptyList(), cursorIndex = 0)
    }
}
