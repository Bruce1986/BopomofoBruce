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
 * 對 [LightTheme] / [DarkTheme] 斷言：
 * - `contrastRatio(keyText, keyAccent) >= 4.5`（WCAG AA 一般文字門檻——功能鍵按下時字要看得見）
 * - `contrastRatio(candidateHighlight, background) >= 3.0`（WCAG 1.4.11 非文字 UI 元件門檻——候選列的
 *   游標高亮要跟背景分得出來）
 *
 * `candidateText/candidateHighlight` 也用 AA 的 4.5——高亮候選的字是主要內容。它與「`candidateHighlight` 對
 * `background` ≥3:1」在深色主題下數學上互斥（可行區間為空），取捨是文字優先、非文字門檻退到已論證的 2.9，詳見下方該測試的 KDoc 與 devlog。
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
     * 深色主題下 WCAG 1.4.11 的 3:1 **無法達成**，這是刻意接受的取捨而非疏漏：background 近黑 （相對亮度 0.0113）、candidateText
     * 近白（0.7633），高亮色要對 background 達 3:1 需要亮度 >= 0.1339，要讓白字達 AA 4.5:1 需要亮度 <= 0.1307 — 可行區間為空。根因是
     * KeyboardColors 契約沒有「高亮候選專用的文字色」（M3 的 onSecondaryContainer），contracts-v1 已凍結，已登記 為 W2 契約
     * follow-up。取捨為文字可讀性優先（見 BuiltInThemes.kt 的註解），此處門檻設在 目前值可達到的 2.9，仍能擋住「有人把高亮色改回與背景同色階」這類回歸。
     */
    @Test
    fun `DarkTheme candidateHighlight against background stays at the documented best-effort 2_9`() {
        val ratio = contrastRatio(DarkTheme.colors.candidateHighlight, DarkTheme.colors.background)
        assertTrue(ratio >= 2.9, "expected >= 2.9, was $ratio")
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
    fun `LightTheme candidateText on candidateHighlight meets WCAG AA`() {
        val ratio =
            contrastRatio(LightTheme.colors.candidateText, LightTheme.colors.candidateHighlight)
        assertTrue(ratio >= 4.5, "expected >= 4.5, was $ratio")
    }

    /** 高亮候選的字是主要內容，門檻拉到 WCAG AA 的 4.5（見上一條測試對取捨的說明）。 */
    @Test
    fun `DarkTheme candidateText on candidateHighlight meets WCAG AA`() {
        val ratio =
            contrastRatio(DarkTheme.colors.candidateText, DarkTheme.colors.candidateHighlight)
        assertTrue(ratio >= 4.5, "expected >= 4.5, was $ratio")
    }
}
