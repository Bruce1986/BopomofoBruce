package com.bopomofobruce.common

import kotlinx.serialization.Serializable

/**
 * 鍵盤主題的顏色集合。所有顏色用 [Long] 存 ARGB（不是 Compose Color，避免 :common 依賴 Compose）。
 *
 * 慣例：高 8 bit alpha、再 RGB，例如 `0xFFFF0000L` 是不透明紅。 :keyboards / :theme 端會把 [Long] 轉成
 * `androidx.compose.ui.graphics.Color(value.toULong())`。
 * - [background]：整個 IME panel 背景。
 * - [keyFill]：按鍵填色（normal state）。
 * - [keyText]：按鍵文字色。
 * - [keyAccent]：強調色（pressed state / 功能鍵）。
 * - [candidateText]：候選詞文字色。
 * - [candidateHighlight]：候選列上目前 cursor 位置的底色。
 */
@Serializable
data class KeyboardColors(
    val background: Long,
    val keyFill: Long,
    val keyText: Long,
    val keyAccent: Long,
    val candidateText: Long,
    val candidateHighlight: Long,
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
)

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
