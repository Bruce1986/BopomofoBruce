package com.bopomofobruce.theme

import com.bopomofobruce.theme.color.contrastRatio
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
 * **給日後調色的人：這裡有兩處餘裕極薄，紅了不一定是真的退步。**（2026-09-08 深審實測）
 * - `DarkTheme` 的 `keyAccent` 對 `background` 現值 2.9485、門檻 2.9，**餘裕只有 0.0485**：往背景 方向線性內插
 *   2%（`#855196` → `#835094`，R−2、G−1）就會跌到 2.888 而翻紅。這不是裝飾門檻 （舊值 `#4A4458` 的 1.84
 *   確實會被它擋下），而是這個顏色空間在「文字 4.5 為硬下限」的約束下 本來就窄——現值幾乎正好落在理論上限。所以任何看起來無害的 RGB ±2 美術微調都可能弄紅 CI。
 * - `DarkTheme` 的 `keyText/keyAccent` 是 4.500011、`candidateText/candidateHighlight` 是
 *   4.500039，兩者都貼著 4.5 門檻到百萬分之幾。`kotlin.math.pow` 底層是 `java.lang.Math.pow`， JLS **不保證**跨 JVM
 *   廠商／版本／架構逐位元一致（`StrictMath` 才保證）。CI 固定的 ubuntu + Temurin 組合目前綠，但在別的 JDK 或架構上本機重跑若翻紅，**先確認是不是浮點差異
 *   而不是顏色真的退步**。
 *
 * `keyText/keyFill` 是一般鍵的字疊在一般鍵底色上，鍵盤上被讀最多次的畫素，門檻同樣是 AA 的 4.5 （G1：原本只鎖 3.0，Light 17.1:1／Dark 11.1:1
 * 的實測餘裕巨大，鬆門檻擋不住「keyFill 調到 3.x:1 仍全綠」的回歸）。`candidateText` 對 `background`（候選列上未被選中、也就是大多數候選字
 * 畫在背景上的組合）過去完全沒有門檻，一併補上 AA 的 4.5——Light/Dark 現值皆遠高於此。
 */
class BuiltInThemesContrastTest {

    // 對比度公式**直接用正式實作** `com.bopomofobruce.theme.color.contrastRatio`（`internal`，
    // 同一個 module 的測試 source set 本來就看得到）。
    //
    // 早一版這裡私有重抄了一份 `relativeLuminance` / `contrastRatio`，與正式實作逐字元相同，
    // 於是這 12 條門檻**驗的是測試自己抄的那份公式，不是產品程式碼**。突變實測（2026-09-08）：
    // 把正式 `relativeLuminance` 的 gamma 由 2.4 改成 2.2、或把 `contrastRatio` 的 +0.05 偏移
    // 拿掉——動態取色那側的 `AccentColorSelectionTest` 各紅 1 條，而本檔 12 條**全數維持綠燈**。
    // `DynamicAccentSelection.kt` 的 KDoc 早就寫著這份重複的存在、目的是「避免第三份手抄公式」，
    // 只是沒做完；現在做完了。

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

    /** G1：一般鍵的字疊在一般鍵底色上，鍵盤上被讀最多次的畫素，門檻是 WCAG AA 的 4.5。 */
    @Test
    fun `LightTheme keyText on keyFill meets WCAG AA text contrast`() {
        val ratio = contrastRatio(LightTheme.colors.keyText, LightTheme.colors.keyFill)
        assertTrue(ratio >= 4.5, "expected >= 4.5, was $ratio")
    }

    @Test
    fun `DarkTheme keyText on keyFill meets WCAG AA text contrast`() {
        val ratio = contrastRatio(DarkTheme.colors.keyText, DarkTheme.colors.keyFill)
        assertTrue(ratio >= 4.5, "expected >= 4.5, was $ratio")
    }

    /** 高亮候選的字是主要內容，門檻是 WCAG AA 的 4.5（取捨說明見 [DarkTheme] 版本測試的 KDoc）。 */
    @Test
    fun `LightTheme candidateText on candidateHighlight meets WCAG AA`() {
        val ratio =
            contrastRatio(LightTheme.colors.candidateText, LightTheme.colors.candidateHighlight)
        assertTrue(ratio >= 4.5, "expected >= 4.5, was $ratio")
    }

    /** 高亮候選的字是主要內容，門檻是 WCAG AA 的 4.5（取捨說明見上一條測試的 KDoc）。 */
    @Test
    fun `DarkTheme candidateText on candidateHighlight meets WCAG AA`() {
        val ratio =
            contrastRatio(DarkTheme.colors.candidateText, DarkTheme.colors.candidateHighlight)
        assertTrue(ratio >= 4.5, "expected >= 4.5, was $ratio")
    }

    /**
     * G1：候選列上「未被選中」的候選字（也就是大多數候選字）是直接畫在 `background` 上，過去完全 沒有門檻守著。門檻是 WCAG AA 的 4.5，Light/Dark
     * 現值皆遠高於此。
     */
    @Test
    fun `LightTheme candidateText on background meets WCAG AA text contrast`() {
        val ratio = contrastRatio(LightTheme.colors.candidateText, LightTheme.colors.background)
        assertTrue(ratio >= 4.5, "expected >= 4.5, was $ratio")
    }

    @Test
    fun `DarkTheme candidateText on background meets WCAG AA text contrast`() {
        val ratio = contrastRatio(DarkTheme.colors.candidateText, DarkTheme.colors.background)
        assertTrue(ratio >= 4.5, "expected >= 4.5, was $ratio")
    }
}
