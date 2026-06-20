package com.bopomofobruce.common

import kotlinx.serialization.Serializable

/**
 * 長按時要送出的內容。封裝成獨立型別，比起讓 [KeyData] 同時持有兩個可空欄位， 用型別系統一次解掉「label 與 action 必須同生同滅」的不變量。
 *
 * JSON 表現會多一層 `longPress: { label: ..., action: ... }`，但讀寫端皆乾淨。
 */
@Serializable data class LongPressData(val label: String, val action: KeyAction)

/**
 * 單一按鍵的視覺 + 行為描述。
 *
 * 設計成 value object（[Serializable]）讓自訂鍵盤布局可以存成 JSON：
 * - [label]：按鍵上顯示的主文字（注音符號、英文字母、emoji...）。
 * - [action]：短按時送出的 [KeyAction]。
 * - [weight]：在 row 內水平寬度比例（同 row weight 加總後分母化）。 預設 1f；空白鍵通常設成 4f-6f。
 * - [longPress]：長按後送出的內容（可選）。封裝成 [LongPressData]，型別系統保證「要嘛沒有， 要嘛 label/action 兩個都齊」 — 不再需要 runtime
 *   require/驗證。
 *
 * **為什麼用 Float weight 不用 Dp 寬度**：寬度是 layout-time 決策（橫/直、不同主題 keyHeight 不同）， KeyData 屬於 model
 * 層不該寫死像素。
 */
@Serializable
data class KeyData(
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val longPress: LongPressData? = null,
) {
    init {
        require(weight > 0f && weight.isFinite()) {
            "KeyData weight must be > 0 and finite, but was $weight"
        }
    }
}
