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
 *
 * 三個字級欄位的合法範圍都是大於 `0f`、不超過 [MAX_FONT_SIZE_SP]。
 */
@Serializable
data class KeyboardTypography(
    val keyLabelSp: Float = 18f,
    val keySubLabelSp: Float = 12f,
    val candidateTextSp: Float = 16f,
    val fontWeight: Int = 400,
) {
    init {
        requireFontSize("keyLabelSp", keyLabelSp)
        requireFontSize("keySubLabelSp", keySubLabelSp)
        requireFontSize("candidateTextSp", candidateTextSp)
        require(fontWeight in 1..1000) { "fontWeight must be within 1..1000, but was $fontWeight" }
    }

    companion object {
        /**
         * 三個字級欄位共用的上限。防呆上限，不是排版規範：理由與 [KeyboardShapes.MAX_CORNER_RADIUS_DP] 相同——有限的 sp
         * 乘上密度與字型縮放後可能變成無限大的 px（實測 `2e38f.sp.toPx()` 得 `Infinity`；無限大字級進到文字排版後會怎樣，純 JVM 驗不了）。數值待
         * owner 複核， 要調請在主題 JSON 開始被存下來之前調。
         */
        const val MAX_FONT_SIZE_SP: Float = 1000f

        private fun requireFontSize(name: String, value: Float) {
            // `value > 0f` 對 NaN 為 false；上限同時擋 +Infinity。
            require(value > 0f && value <= MAX_FONT_SIZE_SP) {
                "$name must be > 0 and <= $MAX_FONT_SIZE_SP, but was $value"
            }
        }
    }
}
