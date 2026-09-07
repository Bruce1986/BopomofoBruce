package com.bopomofobruce.theme.color

import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 拿**獨立於本專案公式之外的已知答案**驗證 [relativeLuminance] / [contrastRatio] 本身。
 *
 * 為什麼需要這一組：2026-09-08 的深審把 `BuiltInThemesContrastTest` 私有重抄的那份公式刪掉、 改成 import
 * 本檔的正式實作——那修掉了「兩份公式各自漂移」的風險，但換來一個新風險： **現在全模組只有這一份公式，它自己錯了的話，所有使用者會「一起錯」而沒有人會紅**。
 * 在此之前，`theme/src/test/` 底下沒有任何一條測試是拿外部已知答案來驗這份公式的—— 全部都是「這份公式算的值 vs 這份公式算的另一個值」或「算出來的值 vs 門檻常數」。
 *
 * 下面三組刻意涵蓋不同的錯誤型態（各自的鑑別力已用獨立實作交叉驗算過）：
 * - 黑白 = 21:1 是 WCAG 對比度的數學上界，人盡皆知。它對 **gamma 指數錯誤沒有鑑別力** （0 與 1 的任何次方仍是 0 與 1），但對 **`+0.05`
 *   偏移錯誤有**：拿掉偏移會讓它變成無限大。
 * - `#767676` 對白 ≈ 4.5422 對 **gamma 錯誤有鑑別力**：gamma 改成 2.2 會算成 4.0559，跨過 4.5 這條線。⚠️
 *   **這個數字沒有可查證的外部出處**——它是用同一條 WCAG 公式的獨立實作 交叉驗算出來的，所以它證明的是「兩份實作彼此一致」（抓得到謄寫／typo 類錯誤），
 *   **不是**「這條公式忠實反映 WCAG 官方定義」。真正不依賴實作的只有黑白 21:1。
 * - 同色自比恆為 1.0，驗的是公式在 `la == lb` 這個退化點不會產出未定義值（例如 `+0.05` 偏移被拿掉時，黑色自比會變成 `0/0 = NaN`）。 ⚠️ **它抓不到
 *   lighter/darker 取反或除法方向寫反**——`la == lb` 時 `max`/`min` 與除法 方向的任何重排都收斂成同一個值
 *   1.0，結構上就測不到。那兩種錯誤是由上面兩條**跨色** 測試把關的（實測：兩者任一反轉，黑白從 21.0 變成 0.0476、`#767676` 從 4.5422 變成
 *   0.2202，而自比那條在兩次突變下都是綠的）。
 */
class ContrastFormulaKnownValuesTest {

    /** 黑白對比是 WCAG 的數學上界 21:1。這組抓的是 `+0.05` 偏移被動過（會變成無限大）。 */
    @Test
    fun `black on white is exactly 21 to 1`() {
        val ratio = contrastRatio(0xFF000000u, 0xFFFFFFFFu)
        assertTrue(ratio.isFinite(), "對比度不是有限值——`+0.05` 偏移可能被拿掉了：$ratio")
        assertTrue(abs(ratio - 21.0) < 1e-9, "expected 21.0, was $ratio")
    }

    /**
     * `#767676` 在白底上約 4.5422，也就是「剛好達 AA 4.5」的那個灰。這組抓的是 gamma 指數被 動過——用 2.2 會算成 4.0559，直接跨過 4.5 這條線。
     *
     * ⚠️ **無可查證的外部出處**：這個期望值是用同一條 WCAG 公式的獨立實作交叉驗算得出， 不是查證過的外部事實。它是**跨實作一致性檢查**，抓得到謄寫錯誤，但抓不到「這條公式
     * 本身與 WCAG 官方定義不一致」。
     *
     * 容差 `5e-4`：期望值 4.5422 與精確值 4.542224959605253 只差 2.5e-5，所以裕度約 4.75e-4 ——換算下來 gamma 只要偏離 2.4 約
     * ±0.00019（相對誤差 0.008%）就會讓這條紅。它比看起來 緊得多，**不要因為以為它很鬆而隨手放寬**。同時也不會偶發紅：IEEE-754 在同一份程式碼、
     * 同一組輸入下是決定性的，跨平台 `pow()` 差異約 1 ULP（~1e-16），比裕度小了八個數量級。
     */
    @Test
    fun `the canonical AA-threshold grey on white is about 4_5422`() {
        val ratio = contrastRatio(0xFF767676u, 0xFFFFFFFFu)
        assertTrue(abs(ratio - 4.5422) < 5e-4, "expected ~4.5422, was $ratio")
        assertTrue(ratio >= 4.5, "這個灰本來就該剛好達 AA，卻是 $ratio")
    }

    /** 同色對同色恆為 1.0——lighter/darker 取反或除法寫反都會在這裡爆掉。 */
    @Test
    fun `a colour against itself is exactly 1 to 1`() {
        for (colour in listOf(0xFF000000u, 0xFFFFFFFFu, 0xFF855196u, 0xFF1C1B1Fu)) {
            assertEquals(1.0, contrastRatio(colour, colour), 1e-12, "colour=$colour")
        }
    }

    /** 對比度公式對調兩個參數必須得到相同結果（內部有 max/min 排序）。 */
    @Test
    fun `contrast ratio is symmetric`() {
        val a = 0xFF855196u
        val b = 0xFF1C1B1Fu
        assertEquals(contrastRatio(a, b), contrastRatio(b, a), 1e-12)
    }

    /** alpha 通道不參與亮度計算——同樣的 RGB 配上不同 alpha 必須得到相同結果。 */
    @Test
    fun `alpha does not affect luminance`() {
        assertEquals(relativeLuminance(0xFF855196u), relativeLuminance(0x00855196u), 1e-12)
    }

    /**
     * 補上**線性分支**（`srgb <= 0.03928` 那一支）的覆蓋——在此之前它對全模組 60 條測試 完全不可見。
     *
     * 原因：所有測試用到的色票（黑、白、`#767676`、兩個內建主題的實際色、 `AccentColorSelectionTest` 的候選色）沒有一個 channel 落在 1..10
     * 這個「線性分支的**非零** 區間」。黑色剛好是 0，而 `0 / 任何除數 = 0`，所以連除數寫錯都看不出來。實測（2026-09-08）： 把 `0.03928` 改成
     * `0.045`、或把 `12.92` 改成 `10.0`，**全套 60 條零反應**。
     *
     * 兩個錨點分工（實測各自抓得到的突變）：
     * - `#0A0A0A`（channel 10，剛好在門檻**內**）：抓除數寫錯（12.92→10.0 或 13.0）， 以及門檻被**縮小**（0.03928→0.030，會把它推去
     *   gamma 分支）。
     * - `#0B0B0B`（channel 11，剛好在門檻**外**）：抓門檻被**放大**（0.03928→0.045，會把它 拉進線性分支），以及 gamma 寫錯。
     *
     * ⚠️ **這兩個期望值是回歸快照，不是外部已知答案**——這種近黑色的對比度在常見的 WCAG／ 對比度工具文件裡查不到引用（本班禁止上網，也確實沒有可查證的出處）。它們鎖的是「目前
     * 這份實作在線性分支上的行為不要被意外改掉」，**不要**跟上面 `#767676` 那條混為一談，更 不要把它們當成 WCAG 的權威值。
     */
    @Test
    fun `the linear segment below the gamma threshold is pinned`() {
        // 前置條件：這兩個 channel 值必須分別落在門檻的兩側，否則這條測試就失去意義。
        assertTrue(10 / 255.0 <= 0.03928, "前提不成立：#0A0A0A 不在線性分支內")
        assertTrue(11 / 255.0 > 0.03928, "前提不成立：#0B0B0B 不在 gamma 分支內")

        assertEquals(19.79814571052481, contrastRatio(0xFF0A0A0Au, 0xFFFFFFFFu), 1e-9)
        assertEquals(19.682627652657427, contrastRatio(0xFF0B0B0Bu, 0xFFFFFFFFu), 1e-9)
    }
}
