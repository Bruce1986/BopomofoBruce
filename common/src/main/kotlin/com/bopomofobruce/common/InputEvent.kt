package com.bopomofobruce.common

/**
 * 進入 [InputProcessor] chain 的事件。
 *
 * 不序列化（pure runtime concept），所以 sealed interface 不掛 @Serializable。
 *
 * 變體說明：
 * - [KeyPressed]：使用者按了一個鍵；payload 是 [KeyData] 而非 [KeyAction] 因為 processor 可能要看 label（例如標點符號特例）。
 * - [CandidateSelected]：使用者點選候選列第 [index] 個；processor 收到後應產生 [TextCommit]。
 * - [TextCommitted]：通常由 :ime 內部 dispatcher 用，告知 chain「某段文字已上屏」， 讓 personal-dictionary processor
 *   有機會學習。
 * - [Reset]：清空 composing 與候選列，例如切換 input field 或長時間 idle。
 */
sealed interface InputEvent {
    data class KeyPressed(val key: KeyData) : InputEvent

    data class CandidateSelected(val index: Int) : InputEvent {
        init {
            require(index >= 0) { "CandidateSelected.index must be non-negative, but was $index" }
        }
    }

    data class TextCommitted(val text: String) : InputEvent

    data object Reset : InputEvent
}
