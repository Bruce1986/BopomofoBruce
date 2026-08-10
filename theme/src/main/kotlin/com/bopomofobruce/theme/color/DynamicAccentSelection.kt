package com.bopomofobruce.theme.color

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.x 相對亮度公式：先把 8-bit sRGB channel 轉線性光，再用固定權重加總。與 `BuiltInThemesContrastTest` 私有實作的公式相同（來源：
 * https://www.w3.org/TR/WCAG21/#dfn-relative-luminance）。這裡公開成 internal，讓 [pickAccentColor]
 * 與其單元測試共用同一份實作，避免第三份手抄公式。
 */
internal fun relativeLuminance(argb: UInt): Double {
    val r = ((argb shr 16) and 0xFFu).toInt()
    val g = ((argb shr 8) and 0xFFu).toInt()
    val b = (argb and 0xFFu).toInt()

    fun channel(c: Int): Double {
        val srgb = c / 255.0
        return if (srgb <= 0.03928) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
    }

    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

/** WCAG 對比度公式：(較亮的 +0.05) / (較暗的 +0.05)，恆為 >= 1。 */
internal fun contrastRatio(a: UInt, b: UInt): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val lighter = max(la, lb)
    val darker = min(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * 純函式（不需要 Android runtime，可在 JVM unit test 直接跑）：從一組候選色彩角色裡挑一個，供
 * [com.bopomofobruce.theme.MaterialYouTheme] 的動態取色路徑用來決定 `keyAccent` / `candidateHighlight`。
 *
 * G3 背景：M3 的 `dynamicLightColorScheme` / `dynamicDarkColorScheme` 只依桌布換色相與彩度，**不換
 * tone**——`*Container` 角色的 tone 是固定目標（light 下 container≈90、surface≈98；dark 下
 * container≈30、surface≈10），跟 `surface` 只差固定的色階距離。把 `keyAccent` 寫死映到
 * `scheme.primaryContainer`，等於在**任何**桌布下都重現 `BuiltInThemes` 剛修掉的缺陷——文字達標、 但強調色自己對
 * `keyFill`/`background` 的分離度只有 1–2:1（container 與 surface 幾乎同 tone）。
 * 這個函式不寫死映射到某一個角色，而是從呼叫端給的候選清單中挑：
 * 1. 保留 `contrastRatio(candidate, textPartner) >= textThreshold` 的候選（文字可讀是硬下限， 候選色要能承載
 *    `keyText`/`candidateText`）。
 * 2. 若有候選通過，取「與 [separationReferences] 的最小分離度」最大的那個——讓最弱的那一邊也盡量好， 而不是只顧其中一邊。
 * 3. 若沒有候選通過文字門檻（理論上可能發生：例如某個桌布讓所有候選角色的 tone 都落在中段），退回 「文字對比度最高」的候選，**不偽造一個假裝達標的結果**——呼叫端可以自行用
 *    [contrastRatio] 判斷 退回值是否可用、要不要提示使用者色彩不足。
 *
 * 同分（含 fallback 分支同分）時保留 [candidates] 中排序在前的那個，讓呼叫端能用候選清單的順序表達 「這個角色比較符合語意」的偏好（例如
 * `primaryContainer` 排在 `onSurfaceVariant` 前面）。
 *
 * @param candidates 依優先序排列的候選色（ARGB [UInt]），不可為空。
 * @param textPartner 候選色要承載的文字顏色（`keyText` 或 `candidateText`）。
 * @param separationReferences 候選色需要與之區分開來的底色（`keyAccent` 傳 `listOf(keyFill,
 *   background)`；`candidateHighlight` 依契約只需要對 `background` 有分離度，傳 `listOf(background)` 即可），不可為空。
 * @param textThreshold WCAG AA 一般文字門檻，預設 4.5。
 */
fun pickAccentColor(
    candidates: List<UInt>,
    textPartner: UInt,
    separationReferences: List<UInt>,
    textThreshold: Double = 4.5,
): UInt {
    require(candidates.isNotEmpty()) { "candidates must not be empty" }
    require(separationReferences.isNotEmpty()) { "separationReferences must not be empty" }

    val passingTextThreshold = candidates.filter { contrastRatio(it, textPartner) >= textThreshold }
    val pool = if (passingTextThreshold.isNotEmpty()) passingTextThreshold else candidates
    val scoreBy =
        if (passingTextThreshold.isNotEmpty()) {
            { candidate: UInt -> separationReferences.minOf { contrastRatio(candidate, it) } }
        } else {
            { candidate: UInt -> contrastRatio(candidate, textPartner) }
        }

    return pool.maxWithOrNull(compareBy(scoreBy)) ?: candidates.first()
}
