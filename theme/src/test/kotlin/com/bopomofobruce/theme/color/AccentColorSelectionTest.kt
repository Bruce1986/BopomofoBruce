package com.bopomofobruce.theme.color

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * G3 守門：[pickAccentColor] 是純函式（不需要 Android runtime），所以可以用假造的色彩組合在 JVM 上驗證 `MaterialYouTheme`
 * 動態取色路徑的選色邏輯——即使 `dynamicLightColorScheme`/`dynamicDarkColorScheme` 本身仍然無法在這裡測（需要系統資源，見
 * [com.bopomofobruce.theme.MaterialYouTheme] 的 KDoc）。
 *
 * 色值取自 [com.bopomofobruce.theme.DarkTheme] 附近的真實數字，用 Python 重算過 WCAG 對比度（見 commit 說明），不是憑空編的。
 */
class AccentColorSelectionTest {

    private val keyText = 0xE6E1E5u
    private val keyFill = 0x2B2930u
    private val background = 0x1C1B1Fu

    /**
     * 模擬 G3 描述的真實缺陷：優先序第一的候選（模擬 primaryContainer）文字對比度很高，但因為跟 surface 幾乎同 tone、對 keyFill/background
     * 的分離度很差；優先序其後的候選（模擬跳出 container/surface 這組固定 tone 關係的角色）文字剛好壓線過 4.5，但分離度好得多。函式必須選
     * 分離度更好的那個，而不是照優先序選第一個。
     */
    @Test
    fun `picks the candidate with the best separation among those that pass the text threshold`() {
        val containerLike = 0x4F378Bu // text 7.22 (通過)，keyFill 1.54 / background 1.84（分離度差）
        val distinctHue = 0x855196u // text 4.50 (通過)，keyFill 2.47 / background 2.95（分離度較好）
        val badCandidate = 0x2E2B33u // text 10.78 (通過)，keyFill 1.03 / background 1.23（分離度更差）

        val picked =
            pickAccentColor(
                candidates = listOf(containerLike, distinctHue, badCandidate),
                textPartner = keyText,
                separationReferences = listOf(keyFill, background),
            )

        assertEquals(distinctHue, picked)
    }

    /**
     * 極端桌布情境（G3 明確要求覆蓋）：所有候選色彩角色的 tone 都落在中段，沒有任何一個能同時滿足 文字 AA 4.5 與分離度——container 與 surface 同 tone
     * 的最壞情況。函式必須退回「文字對比度最高」 的候選，而不是丟例外或悄悄回傳一個文字對比度不合格的值當作合格值。
     */
    @Test
    fun `falls back to the highest text contrast candidate when nothing passes the text threshold`() {
        val highSeparationLowText = 0xD0BCFFu // text 1.32（不通過），分離度很好（keyFill 8.42 / bg 10.05）
        val lowSeparationHigherText = 0x9E8FBFu // text 2.28（不通過但比上面高），分離度較差

        val picked =
            pickAccentColor(
                candidates = listOf(highSeparationLowText, lowSeparationHigherText),
                textPartner = keyText,
                separationReferences = listOf(keyFill, background),
            )

        assertEquals(lowSeparationHigherText, picked)
    }

    /**
     * H2（第十一輪審查）：舊版寫法是 `listOf(candidate, candidate)`——同一個 UInt 值放兩次，回傳值不管 `pickAccentColor` 內部
     * tie-break 規則是什麼都必然等於那個值，對 KDoc 明文宣稱的「同分時保留排序在前 的那個」語意零鑑別力。
     *
     * 改用兩個「不同的 UInt」但分離度分數精確相等的候選：`relativeLuminance()`／`contrastRatio()` 只看 `(argb shr 16) and
     * 0xFF` 等低 24 bit（R/G/B），完全不看最上面的 alpha byte（bit 24-31）。 `earlierCandidate` 與 `laterCandidate`
     * 的 RGB 完全相同、只差 alpha byte（0x11 vs 0x99），因此兩者的 `contrastRatio` 是數學上精確相等（同一個 double
     * 值），不是四捨五入湊巧相等——可以真正驗證 `maxWithOrNull(compareBy(...))` 在 compare == 0 時保留先出現的候選，而不是恰好兩個一樣的值。
     */
    @Test
    fun `keeps the earlier candidate on an exact tie`() {
        val earlierCandidate = 0x11855196u // alpha=0x11，RGB 與 laterCandidate 相同
        val laterCandidate = 0x99855196u // alpha=0x99，RGB 與 earlierCandidate 相同 → 分離度分數精確相等

        val picked =
            pickAccentColor(
                candidates = listOf(earlierCandidate, laterCandidate),
                textPartner = keyText,
                separationReferences = listOf(keyFill, background),
            )

        assertEquals(earlierCandidate, picked)
    }

    /**
     * `candidateHighlight` 依契約只需要對 `background` 有分離度，`keyFill` 不在考量範圍內；這裡用
     * 合成色值（不綁在任何真實主題上，純粹驗證機制）確認 [pickAccentColor] 只看呼叫端傳入的
     * `separationReferences`，不會偷用其他色。`refA`（亮）與 `refB`（暗）刻意取得很開，讓「靠近亮 端的候選」與「靠近暗端但仍過文字門檻的候選」在兩個
     * reference 上排名互換。
     */
    @Test
    fun `separation is scored only against the references the caller passes in`() {
        val syntheticText = 0x000000u
        val refA = 0xF5F5F5u // 亮
        val refB = 0x141414u // 暗
        val brightCandidate = 0xF0F0F0u // text 18.4（通過），refA 1.05（差）、refB 16.17（好）
        val midCandidate = 0x7C7C7Cu // text 5.03（通過），refA 3.83（好）、refB 4.41（好，優於 brightCandidate）

        val pickedForRefAOnly =
            pickAccentColor(
                candidates = listOf(brightCandidate, midCandidate),
                textPartner = syntheticText,
                separationReferences = listOf(refA),
            )
        val pickedForRefBOnly =
            pickAccentColor(
                candidates = listOf(brightCandidate, midCandidate),
                textPartner = syntheticText,
                separationReferences = listOf(refB),
            )

        assertEquals(midCandidate, pickedForRefAOnly)
        assertEquals(brightCandidate, pickedForRefBOnly)
    }

    /**
     * H1（第十一輪審查）：`keyAccent` 與 `candidateHighlight` 用同一組候選清單（成員相同、只是排序 不同）各呼叫一次
     * [pickAccentColor]，貼近真實 M3 baseline 時兩次都選中同一個顏色（撞色）。 `excluded`
     * 參數讓第二次呼叫排除第一次已選中的顏色。這裡重用第一條測試的三個候選值： `distinctHue` 分離度最好、原本會被選中；把它排除後，剩下的兩個候選裡
     * `containerLike` 分離度 比 `badCandidate` 好，應該被選中。
     */
    @Test
    fun `excludes an already-chosen color and picks the next best candidate`() {
        val containerLike = 0x4F378Bu // text 7.22（通過），keyFill 1.54 / background 1.84
        val distinctHue = 0x855196u // text 4.50（通過），keyFill 2.47 / background 2.95（分離度最好）
        val badCandidate = 0x2E2B33u // text 10.78（通過），keyFill 1.03 / background 1.23（分離度最差）

        val picked =
            pickAccentColor(
                candidates = listOf(containerLike, distinctHue, badCandidate),
                textPartner = keyText,
                separationReferences = listOf(keyFill, background),
                excluded = setOf(distinctHue),
            )

        assertEquals(containerLike, picked)
    }

    /**
     * H1 退化情境：排除後候選清單變空（所有候選角色的顏色都跟 `excluded` 撞在一起，理論上只有桌布 配色高度單調的極端情境才會發生）。[pickAccentColor] 的
     * KDoc 明文記載此時會忽略排除限制、退回 原本規則選色，不丟例外、也不偽造一個不在候選清單裡的假顏色——回傳值因此仍可能等於 `excluded`
     * 裡的顏色，呼叫端要自己判斷是否要另外提示使用者。
     */
    @Test
    fun `falls back to ignoring the exclusion when every candidate is excluded`() {
        val onlyCandidate = 0x855196u // text 4.50（通過），單一候選，同時也是要被排除的顏色

        val picked =
            pickAccentColor(
                candidates = listOf(onlyCandidate),
                textPartner = keyText,
                separationReferences = listOf(keyFill, background),
                excluded = setOf(onlyCandidate),
            )

        assertEquals(onlyCandidate, picked)
    }

    /**
     * I1（第十二輪審查）：H1 用 `excluded` 硬性把 `keyAccent` 已選中的顏色從候選清單移除、再重新評分—— 這個「先過濾、再評分」的順序本身會讓分離度倒退，重現
     * B22 修掉的缺陷（見 [com.bopomofobruce.theme.MaterialYouTheme] 呼叫處註解的 M3 baseline 實算數字：light 下從
     * 1.66:1 倒退到 1.26:1，dark 下倒退到 B22 明講「不及 3:1」的 1.84:1 那個色值）。
     *
     * 這裡用專門構造的候選集合重現同一種結構：`keyAccentColor` 是唯一通過文字門檻、且對 background
     * 分離度最好（2.17:1）的候選；`weakerFailingCandidate`／`weakestFailingCandidate` 兩個都不過文字
     * 門檻，但**越接近文字門檻的那個（`weakerFailingCandidate`）分離度反而越差**（1.69:1，比 `weakestFailingCandidate` 的
     * 2.31:1 還差）——這種「文字對比與背景分離度方向相反」的關係， 用 Python 對這三個色值重算過 WCAG 相對亮度與對比度確認存在（不是隨手編的巧合）。
     *
     * 舊呼叫方式（`excluded = setOf(keyAccentColor)`，`separationReferences = listOf(background)`， 對應 H1
     * 修正前的 `dynamicColorsFor()`）：`keyAccentColor` 被排除後，剩下兩個候選都不過文字門檻， 退回「文字對比度最高」的 fallback 分支，選中
     * `weakerFailingCandidate`——分離度只有 1.69:1， 明顯比 `keyAccentColor` 自己的 2.17:1 差。
     *
     * 新呼叫方式（I1 修正後：不排除，`separationReferences = listOf(background, keyAccentColor)`）： 三個候選都不排除，只有
     * `keyAccentColor` 過文字門檻，函式選中它自己（分離度 2.17:1）—— 沒有為了避開撞色（`candidateHighlight` 與 `keyAccent`
     * 撞成同一個值）而選到分離度更差的顏色。
     *
     * **已證明會紅**：把下面「新呼叫方式」暫時改成舊呼叫方式（`excluded = setOf(keyAccentColor)`， `separationReferences =
     * listOf(background)`），重跑本測試， `org.opentest4j.AssertionFailedError: expected: <8721210> but
     * was: <14156436>` FAILED （`8721210` = `keyAccentColor` 0x18143A，`14156436` =
     * `weakerFailingCandidate` 0xD80C94， 證明舊呼叫方式選到分離度更差的候選）；改回新呼叫方式後綠。
     */
    @Test
    fun `does not sacrifice separation from background just to dodge a collision with keyAccent`() {
        val keyAccentTextPartner = 0xF5F5F5u
        val backgroundRef = 0x683E82u
        val keyAccentColor = 0x18143Au // text 16.05（通過），對 background 2.17:1（三者中最好）
        val weakerFailingCandidate = 0xD80C94u // text 4.38（不通過，最接近門檻），對 background 1.69:1（三者中最差）
        val weakestFailingCandidate = 0xE06244u // text 3.21（不通過，離門檻更遠），對 background 2.31:1（比上面好）

        val pickedWithOldExcludedStyle =
            pickAccentColor(
                candidates =
                    listOf(keyAccentColor, weakerFailingCandidate, weakestFailingCandidate),
                textPartner = keyAccentTextPartner,
                separationReferences = listOf(backgroundRef),
                excluded = setOf(keyAccentColor),
            )
        assertEquals(
            weakerFailingCandidate,
            pickedWithOldExcludedStyle,
            "sanity check：H1 修正前的舊呼叫方式應該選到分離度較差的 weakerFailingCandidate，" +
                "藉此證明下面的新呼叫方式確實選到不同、更好的結果",
        )

        val pickedWithNewSeparationReferenceStyle =
            pickAccentColor(
                candidates =
                    listOf(keyAccentColor, weakerFailingCandidate, weakestFailingCandidate),
                textPartner = keyAccentTextPartner,
                separationReferences = listOf(backgroundRef, keyAccentColor),
            )

        assertEquals(keyAccentColor, pickedWithNewSeparationReferenceStyle)
    }
}
