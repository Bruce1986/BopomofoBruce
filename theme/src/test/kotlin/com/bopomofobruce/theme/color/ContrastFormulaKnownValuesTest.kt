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
 * - `#767676` 對白 ≈ 4.5422（WCAG／WebAIM 常引用的「灰階在白底上剛好達 AA」參考值）對 **gamma 錯誤有鑑別力**：gamma 改成 2.2 會算成
 *   4.0559，跨過 4.5 這條線。
 * - 同色自比恆為 1.0，驗的是 lighter/darker 的取大小與除法沒有被寫反。
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
     * WCAG／WebAIM 常引用的參考值：`#767676` 在白底上約 4.5422，也就是「剛好達 AA 4.5」的 那個灰。這組抓的是 gamma 指數被動過——用 2.2 會算成
     * 4.0559，直接跨過 4.5 這條線。
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
}
