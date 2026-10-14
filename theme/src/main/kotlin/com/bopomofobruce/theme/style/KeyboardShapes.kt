package com.bopomofobruce.theme.style

import kotlinx.serialization.Serializable

/**
 * 按鍵與面板的形狀（圓角）常數。獨立於 `:common` 的 `KeyboardDimens`——dimens 管「多大」，shapes 管「多圓」， 兩者演進速度不同（圓角是純美術決策、不影響
 * hit-test 幾何），拆開存比較不會互相牽動 wire format。
 * - [keyCornerRadiusDp]：單一按鍵圓角。
 * - [candidateRowCornerRadiusDp]：候選詞列容器圓角。
 * - [panelCornerRadiusDp]：整個鍵盤面板（IME window）圓角，通常只有上緣兩角有效。
 *
 * 三個欄位的合法範圍都是 `0f`..[MAX_CORNER_RADIUS_DP]。
 */
@Serializable
data class KeyboardShapes(
    val keyCornerRadiusDp: Float = 8f,
    val candidateRowCornerRadiusDp: Float = 0f,
    val panelCornerRadiusDp: Float = 0f,
) {
    init {
        requireRadius("keyCornerRadiusDp", keyCornerRadiusDp)
        requireRadius("candidateRowCornerRadiusDp", candidateRowCornerRadiusDp)
        requireRadius("panelCornerRadiusDp", panelCornerRadiusDp)
    }

    companion object {
        /**
         * 三個圓角欄位共用的上限。這是防呆上限，不是美術規範：超過它的值一律拒絕（即使 px 仍然有限）， 目的是擋掉「dp 值有限、乘上螢幕密度後 px 變成無限大」的輸入——
         * 那種值過得了 `isFinite()`，卻會讓 `RoundedCornerShape` 在畫的時候丟 `IllegalArgumentException`（2026-10-12
         * 實測：`2e38f` 在密度 2.625 下如此）， 而且是在解碼成功之後才丟，解析主題 JSON 的 catch 接不到。數值待 owner 複核；**要調請在主題 JSON
         * 開始被存下來之前調**， 之後調低會讓已存的資料解不出來。
         */
        const val MAX_CORNER_RADIUS_DP: Float = 1000f

        private fun requireRadius(name: String, value: Float) {
            // NaN 與 ±Infinity 都不在任何閉區間內，所以這一條同時擋非有限值。
            require(value in 0f..MAX_CORNER_RADIUS_DP) {
                "$name must be within 0f..$MAX_CORNER_RADIUS_DP, but was $value"
            }
        }
    }
}
