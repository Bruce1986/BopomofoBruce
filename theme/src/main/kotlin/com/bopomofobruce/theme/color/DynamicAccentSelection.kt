package com.bopomofobruce.theme.color

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.x 相對亮度公式：先把 8-bit sRGB channel 轉線性光，再用固定權重加總
 * （來源：https://www.w3.org/TR/WCAG21/#dfn-relative-luminance）。
 *
 * `internal` 是為了讓 [pickAccentColor]、`BuiltInThemesContrastTest` 與其單元測試**共用這唯一
 * 一份實作**。`BuiltInThemesContrastTest` 一度自己私有重抄了一份逐字元相同的公式，於是它那 12
 * 條對比門檻驗的是測試自己抄的版本、對正式實作的任何迴歸完全免疫（2026-09-08 深審用突變 實證：把下方 gamma 由 2.4 改成 2.2，那 12 條全綠）；那份複製品已刪除。
 *
 * 只剩一份實作的代價是「它自己錯了就沒有第二份能對照」，所以另有 `ContrastFormulaKnownValuesTest` 從幾個角度驗它：黑白 21:1（唯一有外部公認出處的數字）、
 * `#767676` 對白 ≈4.5422、同色自比 1.0（釘 `la == lb` 的退化點）、三個權重各自從原色讀回、 以及門檻兩側的錨點。
 *
 * **不要把已知值組（黑白／`#767676`／自比／權重）的期望值改成從這份實作反推出來的數字**—— 那會讓它退化成自我印證。**唯一刻意的例外**是門檻兩側那兩個錨點：它們確實是反算的，
 * 因為近黑色的對比度查不到外部引用；它們的稽核依據改成「分段函式在門檻處必須連續」 （五個常數互相印證，落差 0.025%，動任一個放大 21–2500 倍），推導寫在該測試的 KDoc 裡。
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
 * 與候選列選中游標的底色因此變成同一個顏色，使用者無法用顏色區分兩者，是 B22 註解明講要避免、 繞一圈又回來的撞色缺陷。當時加了 [excluded]
 * 參數讓呼叫端排除已經被另一個用途選中的顏色。
 *
 * **I1（第十二輪審查，修正 H1 的解法）**：[excluded] 是「先硬性過濾、再評分」，排除後剩下的候選 有多差都不會回頭選，實測會讓分離度倒退到比排除前更差（見
 * [com.bopomofobruce.theme.MaterialYouTheme] 的呼叫處註解，附 M3 baseline 實算數字）。
 * [com.bopomofobruce.theme.MaterialYouTheme.dynamicColorsFor] 已改成**不用** [excluded]，改為把 `keyAccent`
 * 併入 [separationReferences]，讓「與 keyAccent 分得開」變成跟「與 background 分得開」同級的評分項，而不是二元的硬性排除／不排除。[excluded]
 * 參數本身**保留在本函式的公開 API**（見下方 KDoc 與 `AccentColorSelectionTest` 既有的兩條測試），因為它是描述明確、已有測試覆蓋
 * 的通用純函式功能——之後若有呼叫端需要「絕對不可以跟某個顏色相同」這種硬性排除語意（跟 `candidateHighlight` 這種「盡量分得開但分離度優先」的語意不同），仍可以直接重用；只是
 * `dynamicColorsFor()` 這個呼叫處不再使用它。
 *
 * **退化語意**（[excluded] 仍適用此段）：若排除 [excluded] 之後候選清單變成空的（代表所有候選角色的顏色都撞在一起，理論上
 * 只有桌布配色高度單調的極端情境才會發生），本函式**放棄排除限制、忽略 [excluded] 走原本規則**選 色，不會丟例外、也不會偽造一個不在 [candidates]
 * 裡的假顏色。這代表撞色在這個退化情境下無法避免 （回傳值可能等於 [excluded] 裡的顏色），呼叫端可以自行比對回傳值與 [excluded] 判斷是否真的撞色、 要不要另外提示使用者。
 *
 * @param candidates 依優先序排列的候選色（ARGB [UInt]），不可為空。
 * @param textPartner 候選色要承載的文字顏色（`keyText` 或 `candidateText`）。
 * @param separationReferences 候選色需要與之區分開來的底色，不可為空。現行呼叫端（見
 *   `MaterialYouTheme.dynamicColorsFor()`）：`keyAccent` 傳 `listOf(keyFill, background)`；
 *   **`candidateHighlight` 傳 `listOf(background, keyAccent)`——把已選定的 `keyAccent` 一併列為參照色，
 *   正是本函式避免兩個用途撞色的方式**（`keyAccent` 也在候選清單內，它對自己的對比度恆為 1.0，因此會被 壓到最低分而讓出位置；見上方 I1 段落）。**不要**只傳
 *   `listOf(background)`——那是 H1 修正前的舊寫法， 在 M3 baseline 的 tone 分布下兩次呼叫會雙雙選中 `inversePrimary`，light 對
 *   background 僅 1.66:1、 dark 2.66:1，皆不及本模組自訂的 WCAG 1.4.11 門檻。
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
