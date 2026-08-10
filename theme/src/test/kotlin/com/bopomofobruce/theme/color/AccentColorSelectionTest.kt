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

    @Test
    fun `keeps the earlier candidate on an exact tie`() {
        val candidate = 0x855196u

        val picked =
            pickAccentColor(
                candidates = listOf(candidate, candidate),
                textPartner = keyText,
                separationReferences = listOf(keyFill, background),
            )

        assertEquals(candidate, picked)
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
}
