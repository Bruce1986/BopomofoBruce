package com.bopomofobruce.common

import kotlinx.serialization.Serializable

/**
 * 單一按鍵的視覺 + 行為描述。
 *
 * 設計成 value object（[Serializable]）讓自訂鍵盤布局可以存成 JSON：
 * - [label]：按鍵上顯示的主文字（注音符號、英文字母、emoji...）。
 * - [action]：短按時送出的 [KeyAction]。
 * - [weight]：在 row 內水平寬度比例（同 row weight 加總後分母化）。 預設 1f；空白鍵通常設成 4f-6f。
 * - [longPressLabel] / [longPressAction]：長按後送出的內容（可選）。
 *
 *   **契約（W0-2 凍結）**：兩者**必須同時** null **或**同時非 null。只設一邊視為錯誤； :keyboards 反序列化 JSON 時若發現失衡，應
 *   fail-loud（throw IllegalStateException） 而非 silently 顯示空 hint。:common 不在 data class init 加
 *   require 是為了保留 JSON 反序列化的 partial state（先建構再驗證），驗證放在 :keyboards loader。
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
