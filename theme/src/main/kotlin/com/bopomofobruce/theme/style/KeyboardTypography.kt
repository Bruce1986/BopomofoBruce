package com.bopomofobruce.theme.style

import kotlinx.serialization.Serializable

/**
 * 鍵盤文字排版常數。單位 sp（scale-independent pixel），供 `:keyboards` / `:ime` 端包成
 * `androidx.compose.ui.unit.TextUnit`。
 * - [keyLabelSp]：按鍵主要 label（注音符號 / 字母）字級。
 * - [keySubLabelSp]：按鍵右上角次要 label（例如注音鍵位的英數 shift 標示）字級。
 * - [candidateTextSp]：候選詞列文字字級。
 * - [fontWeight]：對應 `androidx.compose.ui.text.font.FontWeight.weight`，合法範圍 1..1000（CSS font-weight
 *   慣例），400 為 normal、700 為 bold。存 Int 而非直接依賴 Compose 型別，讓 `:theme` 的 model 層維持可在無 Android runtime
 *   情境下序列化。
 */
@Serializable
data class KeyboardTypography(
    val keyLabelSp: Float = 18f,
    val keySubLabelSp: Float = 12f,
    val candidateTextSp: Float = 16f,
    val fontWeight: Int = 400,
) {
    init {
        require(keyLabelSp > 0f && keyLabelSp.isFinite()) {
            "keyLabelSp must be > 0 and finite, but was $keyLabelSp"
        }
        require(keySubLabelSp > 0f && keySubLabelSp.isFinite()) {
            "keySubLabelSp must be > 0 and finite, but was $keySubLabelSp"
        }
        require(candidateTextSp > 0f && candidateTextSp.isFinite()) {
            "candidateTextSp must be > 0 and finite, but was $candidateTextSp"
        }
        require(fontWeight in 1..1000) { "fontWeight must be within 1..1000, but was $fontWeight" }
    }
}
