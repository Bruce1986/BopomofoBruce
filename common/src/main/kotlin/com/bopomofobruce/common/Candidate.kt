package com.bopomofobruce.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 一個候選詞的來源。決定 UI 上的標示（顏色/icon）與排序權重的計算方式。
 * - [PRIMARY]：主 decoder 算出來的詞（libchewing / FakeZhuyinDecoder）。
 * - [PERSONAL]：使用者個人詞庫（之前 commit 過的習慣詞）。
 * - [EMOJI]：emoji 補全（輸入特定 keyword 後跳出）。
 * - [SYMBOL]：標點符號或全形/半形轉換候選。
 *
 * 新增來源要同步更新 :ime 的 candidate bar 渲染。
 */
@Serializable
enum class CandidateSource {
    // 釘住 wire format stable id；之後重新命名 Kotlin enum 不會破舊 JSON。
    @SerialName("primary") PRIMARY,
    @SerialName("personal") PERSONAL,
    @SerialName("emoji") EMOJI,
    @SerialName("symbol") SYMBOL,
}

/**
 * 一個候選詞項目。
 * - [text]：實際送進 input field 的字串（通常 1-4 字元，emoji 可能更長）。
 * - [score]：信心分數 `0f..1f`，由 [ZhuyinDecoder] 給。 候選詞 list 慣例按 score 降序，但 :ime 不依賴這點（會自行 sort）。
 * - [source]：見 [CandidateSource]。
 *
 * 設計為 immutable data class 方便在 [InputState] 與 Flow 之間傳遞而不擔心 mutation。
 */
@Serializable
data class Candidate(val text: String, val score: Float, val source: CandidateSource) {
    init {
        require(text.isNotEmpty()) { "Candidate text cannot be empty" }
        require(score in 0f..1f) { "Candidate score must be in 0f..1f, but was $score" }
    }
}
