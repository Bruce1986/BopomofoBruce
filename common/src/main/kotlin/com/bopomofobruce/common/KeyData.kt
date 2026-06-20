package com.bopomofobruce.common

import kotlinx.serialization.Serializable

/**
 * 單一按鍵的視覺 + 行為描述。
 *
 * 設計成 value object（[Serializable]）讓自訂鍵盤布局可以存成 JSON：
 * - [label]：按鍵上顯示的主文字（注音符號、英文字母、emoji...）。
 * - [action]：短按時送出的 [KeyAction]。
 * - [weight]：在 row 內水平寬度比例（同 row weight 加總後分母化）。 預設 1f；空白鍵通常設成 4f-6f。
 * - [longPressLabel] / [longPressAction]：長按後送出的內容（可選）。 兩者要嘛同時為 null，要嘛同時非 null；若只設一邊代表 UI 端要顯示 hint
 *   但無動作，由 :keyboards 自己處理（:common 不在這裡 enforce）。
 *
 * **為什麼用 Float weight 不用 Dp 寬度**：寬度是 layout-time 決策（橫/直、不同主題 keyHeight 不同）， KeyData 屬於 model
 * 層不該寫死像素。
 */
@Serializable
data class KeyData(
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val longPressLabel: String? = null,
    val longPressAction: KeyAction? = null,
)
