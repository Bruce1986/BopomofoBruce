package com.bopomofobruce.theme.style

import kotlinx.serialization.Serializable

/**
 * 按鍵與面板的形狀（圓角）常數。獨立於 `:common` 的 `KeyboardDimens`——dimens 管「多大」，shapes 管「多圓」， 兩者演進速度不同（圓角是純美術決策、不影響
 * hit-test 幾何），拆開存比較不會互相牽動 wire format。
 * - [keyCornerRadiusDp]：單一按鍵圓角。
 * - [candidateRowCornerRadiusDp]：候選詞列容器圓角。
 * - [panelCornerRadiusDp]：整個鍵盤面板（IME window）圓角，通常只有上緣兩角有效。
 */
@Serializable
data class KeyboardShapes(
    val keyCornerRadiusDp: Float = 8f,
    val candidateRowCornerRadiusDp: Float = 0f,
    val panelCornerRadiusDp: Float = 0f,
) {
    init {
        require(keyCornerRadiusDp >= 0f && keyCornerRadiusDp.isFinite()) {
            "keyCornerRadiusDp must be >= 0 and finite, but was $keyCornerRadiusDp"
        }
        require(candidateRowCornerRadiusDp >= 0f && candidateRowCornerRadiusDp.isFinite()) {
            "candidateRowCornerRadiusDp must be >= 0 and finite, but was $candidateRowCornerRadiusDp"
        }
        require(panelCornerRadiusDp >= 0f && panelCornerRadiusDp.isFinite()) {
            "panelCornerRadiusDp must be >= 0 and finite, but was $panelCornerRadiusDp"
        }
    }
}
