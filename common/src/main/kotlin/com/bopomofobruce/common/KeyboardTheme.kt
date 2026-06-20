package com.bopomofobruce.common

import com.bopomofobruce.common.serialization.UIntHexSerializer
import kotlinx.serialization.Serializable

/**
 * 鍵盤主題的顏色集合。所有顏色用 [UInt] 存 ARGB（不是 Compose Color，避免 :common 依賴 Compose）。
 *
 * 為什麼用 `UInt` 不用 `Long`：
 * - 32 位元無符號精確對應 ARGB；不會出現負值或超出 32-bit 的範圍
 * - 避免 sign extension：例如 `0xFFFFFFFF` 在 Int/Long 是 -1，後續 `toULong()` 高位會被填 1， Compose `Color(value:
 *   ULong)` 會誤判 color space。`UInt.toULong()` 高位保證 0
 *
 * 慣例：高 8 bit alpha、再 RGB，例如 `0xFFFF0000u` 是不透明紅。 :keyboards / :theme 端會把 [UInt] 轉成
 * `androidx.compose.ui.graphics.Color(value.toULong() shl 32)` 或 `Color(value.toInt())`（看 Compose
 * 版本）。
 * - [background]：整個 IME panel 背景。
 * - [keyFill]：按鍵填色（normal state）。
 * - [keyText]：按鍵文字色。
 * - [keyAccent]：強調色（pressed state / 功能鍵）。
 * - [candidateText]：候選詞文字色。
 * - [candidateHighlight]：候選列上目前 cursor 位置的底色。
 */
@Serializable
data class KeyboardColors(
    @Serializable(with = UIntHexSerializer::class) val background: UInt,
    @Serializable(with = UIntHexSerializer::class) val keyFill: UInt,
    @Serializable(with = UIntHexSerializer::class) val keyText: UInt,
    @Serializable(with = UIntHexSerializer::class) val keyAccent: UInt,
    @Serializable(with = UIntHexSerializer::class) val candidateText: UInt,
    @Serializable(with = UIntHexSerializer::class) val candidateHighlight: UInt,
)

/**
 * 鍵盤主題的尺寸常數。單位 dp（density-independent pixel），:keyboards 端用 `Dp(value)` 包起來。
 * - [keyHeightDp]：單個按鍵高度。
 * - [rowGapDp]：row 之間的垂直 spacing。
 * - [keyGapDp]：同 row 內按鍵之間的水平 spacing。
 * - [candidateRowHeightDp]：候選詞列高度。
 */
@Serializable
data class KeyboardDimens(
    val keyHeightDp: Float,
    val rowGapDp: Float,
    val keyGapDp: Float,
    val candidateRowHeightDp: Float,
) {
    init {
        require(keyHeightDp >= 0f) { "keyHeightDp must be >= 0, but was $keyHeightDp" }
        require(rowGapDp >= 0f) { "rowGapDp must be >= 0, but was $rowGapDp" }
        require(keyGapDp >= 0f) { "keyGapDp must be >= 0, but was $keyGapDp" }
        require(candidateRowHeightDp >= 0f) {
            "candidateRowHeightDp must be >= 0, but was $candidateRowHeightDp"
        }
    }
}

/**
 * 一個主題。實作者要嘛硬編碼（內建 Light/Dark/MaterialYou），要嘛從 settings DataStore 載入。
 * - [id]：穩定識別字串（例如 `"light"`、`"dark"`、`"material-you"`、`"custom-photo-2026"`）。
 * - [colors] / [dimens]：見上方。
 */
interface KeyboardTheme {
    val id: String
    val colors: KeyboardColors
    val dimens: KeyboardDimens
}
