package com.bopomofobruce.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * B22 守門：WCAG 2.x 對比度公式的純 Kotlin 實作（sRGB 相對亮度，不需要 Android runtime，跑法比照
 * [com.bopomofobruce.theme.color.ColorConversionsTest]）。
 *
 * 對 [LightTheme] / [DarkTheme] 斷言 finding 明訂的兩條必修門檻：
 * - `contrastRatio(keyText, keyAccent) >= 4.5`（WCAG AA 一般文字門檻——功能鍵按下時字要看得見）
 * - `contrastRatio(candidateHighlight, background) >= 3.0`（WCAG 1.4.11 非文字 UI 元件門檻——候選列的
 *   游標高亮要跟背景分得出來）
 *
 * `candidateText/candidateHighlight` 也用 AA 的 4.5——高亮候選的字是主要內容。它與「`candidateHighlight` 對
 * `background` ≥3:1」在深色主題下數學上互斥（可行亮度區間為空，推導見 [DarkTheme] 的註解），取捨是 文字優先、非文字門檻退到已論證的 2.9。
 *
 * `keyText/keyFill` 沒有被回報過問題，只用寬鬆的 3.0 當回歸警戒。
 */
class BuiltInThemesContrastTest {

    /**
     * WCAG 2.x 相對亮度公式：先把 8-bit sRGB channel 轉線性光，再用固定權重加總。
     * 公式來源：https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
     */
    private fun relativeLuminance(argb: UInt): Double {
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
    private fun contrastRatio(a: UInt, b: UInt): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = max(la, lb)
        val darker = min(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun `LightTheme keyText on keyAccent meets WCAG AA text contrast`() {
        val ratio = contrastRatio(LightTheme.colors.keyText, LightTheme.colors.keyAccent)
        assertTrue(ratio >= 4.5, "expected >= 4.5, was $ratio")
    }

    @Test
    fun `DarkTheme keyText on keyAccent meets WCAG AA text contrast`() {
        val ratio = contrastRatio(DarkTheme.colors.keyText, DarkTheme.colors.keyAccent)
        assertTrue(ratio >= 4.5, "expected >= 4.5, was $ratio")
    }

    @Test
    fun `LightTheme candidateHighlight against background meets WCAG 1_4_11 non-text contrast`() {
        val ratio =
            contrastRatio(LightTheme.colors.candidateHighlight, LightTheme.colors.background)
        assertTrue(ratio >= 3.0, "expected >= 3.0, was $ratio")
    }

    /**
     * 深色主題下 WCAG 1.4.11 的 3:1 與「白字對高亮達 AA 4.5」數學上互斥（可行亮度區間為空，推導見 [DarkTheme]
     * 的註解）。取捨是文字可讀性優先，因此這條退到現值可達的 2.9——仍能擋住「有人把高亮 色改回與背景同色階」的回歸（原值 #4A4458 是 1.84，會被擋下）。
     */
    @Test
    fun `DarkTheme candidateHighlight against background stays at the documented best-effort 2_9`() {
        val ratio = contrastRatio(DarkTheme.colors.candidateHighlight, DarkTheme.colors.background)
        assertTrue(ratio >= 2.9, "expected >= 2.9, was $ratio")
    }

    /**
     * B24：`keyAccent` 同樣受 WCAG 1.4.11 管轄（它是「pressed state / 功能鍵」的視覺指示），第一版 B22 修正只顧到「字疊在它上面」而讓它自己對
     * keyFill/background 掉到 1.29/1.12，測試卻沒有任何 一條量到——1.4.11 被精準地只套在會過的欄位上。這兩條補起來，讓守門覆蓋所有被改動過的欄位。
     */
    @Test
    fun `LightTheme keyAccent stands out from keyFill and background`() {
        assertTrue(
            contrastRatio(LightTheme.colors.keyAccent, LightTheme.colors.keyFill) >= 3.0,
            "keyAccent vs keyFill: ${contrastRatio(LightTheme.colors.keyAccent, LightTheme.colors.keyFill)}",
        )
        assertTrue(
            contrastRatio(LightTheme.colors.keyAccent, LightTheme.colors.background) >= 3.0,
            "keyAccent vs background: ${contrastRatio(LightTheme.colors.keyAccent, LightTheme.colors.background)}",
        )
    }

    /**
     * 深色主題與 `candidateHighlight` 同源地無法三全其美：`keyText` 4.5 當硬下限時，`keyAccent` 對 `keyFill` 的分離度上限只有
     * 2.47（窮舉紫色系求得）。門檻依實測最佳值設定，仍能擋住「有人把 強調色改回與按鍵同色階」的回歸（第一版 #4F378B 是 1.54 / 1.84，會被這兩條擋下）。
     */
    @Test
    fun `DarkTheme keyAccent stays at the documented best-effort separation`() {
        assertTrue(
            contrastRatio(DarkTheme.colors.keyAccent, DarkTheme.colors.keyFill) >= 2.4,
            "keyAccent vs keyFill: ${contrastRatio(DarkTheme.colors.keyAccent, DarkTheme.colors.keyFill)}",
        )
        assertTrue(
            contrastRatio(DarkTheme.colors.keyAccent, DarkTheme.colors.background) >= 2.9,
            "keyAccent vs background: ${contrastRatio(DarkTheme.colors.keyAccent, DarkTheme.colors.background)}",
        )
    }

    @Test
    fun `LightTheme keyText on keyFill has healthy contrast`() {
        val ratio = contrastRatio(LightTheme.colors.keyText, LightTheme.colors.keyFill)
        assertTrue(ratio >= 3.0, "expected >= 3.0, was $ratio")
    }

    @Test
    fun `DarkTheme keyText on keyFill has healthy contrast`() {
        val ratio = contrastRatio(DarkTheme.colors.keyText, DarkTheme.colors.keyFill)
        assertTrue(ratio >= 3.0, "expected >= 3.0, was $ratio")
    }

    @Test
    fun `LightTheme candidateText on candidateHighlight has healthy contrast`() {
        val ratio =
            contrastRatio(LightTheme.colors.candidateText, LightTheme.colors.candidateHighlight)
        assertTrue(ratio >= 3.0, "expected >= 3.0, was $ratio")
    }

    /** 高亮候選的字是主要內容，門檻是 WCAG AA 的 4.5（取捨說明見上一條測試的 KDoc）。 */
    @Test
    fun `DarkTheme candidateText on candidateHighlight meets WCAG AA`() {
        val ratio =
            contrastRatio(DarkTheme.colors.candidateText, DarkTheme.colors.candidateHighlight)
        assertTrue(ratio >= 4.5, "expected >= 4.5, was $ratio")
    }
}
