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
 * H1（第十一輪審查）：`keyAccent` 與 `candidateHighlight` 這兩個用途各自呼叫一次本函式，候選清單 成員相同（只是排序不同），用貼近真實 M3 baseline
 * 的 tone 分布實測會發現兩次呼叫**選中同一個 顏色**（container 系 tone 跟 surface 幾乎同 tone、primary/secondary/tertiary
 * 系文字對比不到 4.5，最後只剩 `inversePrimary` 同時通過文字門檻且分離度最好，兩次都選中它）——功能鍵按下的底色
 * 與候選列選中游標的底色因此變成同一個顏色，使用者無法用顏色區分兩者，是 B22 註解明講要避免、 繞一圈又回來的撞色缺陷。加了 [excluded] 參數讓呼叫端排除已經被另一個用途選中的顏色。
 * **退化語意**：若排除 [excluded] 之後候選清單變成空的（代表所有候選角色的顏色都撞在一起，理論上 只有桌布配色高度單調的極端情境才會發生），本函式**放棄排除限制、忽略
 * [excluded] 走原本規則**選 色，不會丟例外、也不會偽造一個不在 [candidates] 裡的假顏色。這代表撞色在這個退化情境下無法避免 （回傳值可能等於 [excluded]
 * 裡的顏色），呼叫端可以自行比對回傳值與 [excluded] 判斷是否真的撞色、 要不要另外提示使用者。
 *
 * @param candidates 依優先序排列的候選色（ARGB [UInt]），不可為空。
 * @param textPartner 候選色要承載的文字顏色（`keyText` 或 `candidateText`）。
 * @param separationReferences 候選色需要與之區分開來的底色（`keyAccent` 傳 `listOf(keyFill,
 *   background)`；`candidateHighlight` 依契約只需要對 `background` 有分離度，傳 `listOf(background)` 即可），不可為空。
 * @param textThreshold WCAG AA 一般文字門檻，預設 4.5。
 * @param excluded 要排除的顏色集合（例如另一個用途已經選中的顏色，避免撞色）。若排除後候選清單變空， 見上方
 *   KDoc「退化語意」——會忽略這個排除限制。預設空集合，等同不排除任何顏色。
 */
fun pickAccentColor(
    candidates: List<UInt>,
    textPartner: UInt,
    separationReferences: List<UInt>,
    textThreshold: Double = 4.5,
    excluded: Set<UInt> = emptySet(),
): UInt {
    require(candidates.isNotEmpty()) { "candidates must not be empty" }
    require(separationReferences.isNotEmpty()) { "separationReferences must not be empty" }

    val afterExclusion = candidates.filterNot { it in excluded }
    // 排除後沒東西可選：所有候選都撞色到一起，退回忽略排除限制、依原本規則選（見 KDoc「退化語意」）。
    val effectiveCandidates = if (afterExclusion.isNotEmpty()) afterExclusion else candidates

    val passingTextThreshold =
        effectiveCandidates.filter { contrastRatio(it, textPartner) >= textThreshold }
    val pool = if (passingTextThreshold.isNotEmpty()) passingTextThreshold else effectiveCandidates
    val scoreBy =
        if (passingTextThreshold.isNotEmpty()) {
            { candidate: UInt -> separationReferences.minOf { contrastRatio(candidate, it) } }
        } else {
            { candidate: UInt -> contrastRatio(candidate, textPartner) }
        }

    return pool.maxWithOrNull(compareBy(scoreBy)) ?: effectiveCandidates.first()
}
