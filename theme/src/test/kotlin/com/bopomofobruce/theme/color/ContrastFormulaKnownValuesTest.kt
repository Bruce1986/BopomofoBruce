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
 * **先講清楚這些期望值是什麼等級的證據**：除了黑白 21:1 之外，全部都是**用同一條 WCAG 公式的 第二份實作反算出來的**——包含 `#767676`
 * 與線性分支那兩個。它們證明的是「兩份實作彼此一致」 （抓得到謄寫／typo 類錯誤），**不是**「這條公式忠實反映 WCAG 官方定義」。不要把其中任何一組
 * 說成比另一組更權威；真正的差別只有「有沒有外部公認出處」，而目前**只有黑白 21:1 有**。
 *
 * 那「實作本身就抄錯了會被永久釘死」怎麼辦？靠**分段函式在門檻處必須連續**這個內在條件—— 它不需要任何外部出處就能稽核（推導見 `the linear segment...` 那條測試的
 * KDoc）。
 *
 * 下面幾組刻意涵蓋不同的錯誤型態（各自的鑑別力已用獨立實作交叉驗算過）：
 * - 黑白 = 21:1 是 WCAG 對比度的數學上界，也是本組**唯一有外部公認出處**的數字。但它 **並非「不依賴實作」**：它依賴 `+0.05`
 *   偏移、`channel(0)=0`、以及 `channel(255)=1` （後者要 `0.055`／`1.055` 這一對配得上）。實測：`1.055` 改成 `1.05` → 黑白變
 *   21.2293、 這條會紅；但 gamma、門檻、除數、三個權重改掉時它**全都是綠的**。
 * - `#767676` 對白 ≈ 4.5422 對 **gamma 錯誤有鑑別力**：gamma 改成 2.2 會算成 4.0559，跨過 4.5 這條線。⚠️
 *   **這個數字沒有可查證的外部出處**——它是用同一條 WCAG 公式的獨立實作 交叉驗算出來的，所以它證明的是「兩份實作彼此一致」（抓得到謄寫／typo 類錯誤），
 *   **不是**「這條公式忠實反映 WCAG 官方定義」。（黑白 21:1 是唯一**有外部公認出處**的 期望值，但它同樣依賴實作——見下一條的但書。）
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
     * ±0.00019（相對誤差 0.008%）就會讓這條紅。它比看起來 緊得多，**不要因為以為它很鬆而隨手放寬**。
     *
     * 偶發紅的風險可忽略，但**依據不是 IEEE-754**（它並未要求 `pow` 正確捨入）：正確的依據是 JLS 對 `java.lang.Math.pow` 的「誤差 ≤ 1
     * ulp」規定——逐位元一致要用 `StrictMath`， 這一點 [com.bopomofobruce.theme.BuiltInThemesContrastTest]
     * 的但書講得更完整。這裡 `pow` 結果約 0.0033，其 1 ulp 是 4.3e-19，經 `d(ratio)/dL` 放大後約 1e-16 級， 比 4.75e-4
     * 的裕度小了十二個數量級。
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
     * 釘住**門檻兩側**（`srgb <= 0.03928` 的線性支與其上的 gamma 支）——在此之前這個分岔對 全模組 60 條測試完全不可見。
     *
     * 原因：所有測試用到的色票（黑、白、`#767676`、兩個內建主題的實際色、 `AccentColorSelectionTest` 的候選色）沒有一個 channel 落在 1..10
     * 這個「線性分支的**非零** 區間」。黑色剛好是 0，而 `0 / 任何除數 = 0`，所以連除數寫錯都看不出來。實測（2026-09-08）： 把 `0.03928` 改成
     * `0.045`、或把 `12.92` 改成 `10.0`，**全套 60 條零反應**。
     *
     * ## 為什麼可以相信下面兩個硬編碼數字（不需要外部出處）
     *
     * 它們確實是從本實作反算的。但**分段函式在門檻處必須連續**這個內在條件，讓五個常數 （`0.03928` / `12.92` / `2.4` / `0.055` /
     * `1.055`）互相印證——任何一個抄錯，兩支在門檻處 就對不起來：
     *
     * ```
     * 0.03928 / 12.92              = 0.003040247678018576
     * ((0.03928+0.055)/1.055)^2.4  = 0.0030394924862258642
     * 相對落差                      = 2.484e-4  (0.025%)
     * ```
     *
     * 動任一常數，落差立刻放大（實算）：`12.92→13.0` 24×、`0.03928→0.045` 21×、 `0.03928→0.030` 84×、`1.055→1.05`
     * 45×、`0.055→0.05` 494×、`12.92→10.0` 911×、 `2.4→2.2` 2498×。**這段推導拿紙筆就能重算**，數字因此是可稽核的，不是不可查的快照。
     *
     * ## 容差 `1e-9`
     *
     * 最小的真突變位移是門檻 `0.03928→0.030`（Δ=2.8e-4）；浮點雜訊上界約 2.7e-16 （`#0A0A0A` 走純除法、根本不呼叫 `pow()`；`#0B0B0B`
     * 走 gamma 支，1 ulp 經 `d(ratio)/dL ≈ -369` 放大後仍只有這個量級）。`1e-9` 正好卡在兩者中間、各留五個數量級。 ⚠️ **不要為了「與上面的
     * 5e-4 風格一致」而放寬**：放到 1e-4 以上，門檻縮小那一整類錯誤 （Δ=2.8e-4）就靜默失守。
     */
    @Test
    fun `the gamma threshold boundary is pinned from both sides`() {
        // 前置條件不能寫成「字面常數 <= 字面常數」——那是對產品程式碼恆真的斷言，實作把門檻
        // 改掉時它照樣綠（實測：門檻改成 0.045 後 #0B0B0B 真的掉進線性分支，而那種寫法的
        // assertTrue 仍然通過，紅的是下面的 assertEquals）。改成問**實作自己**分支在哪：
        // 線性支上 L 與 channel 精確成正比，所以 10 倍 channel 必須給出剛好 10 倍的亮度。
        val ratio10 = relativeLuminance(0xFF0A0A0Au) / relativeLuminance(0xFF010101u)
        assertEquals(10.0, ratio10, 1e-9, "channel 10 已經不在線性分支內了（門檻被改小？）")
        val ratio11 = relativeLuminance(0xFF0B0B0Bu) / relativeLuminance(0xFF010101u)
        assertTrue(
            abs(ratio11 - 11.0) > 1e-6,
            "channel 11 落進了線性分支（門檻被改大？）——兩個錨點會跑到同一側，這條測試就失去" + "門檻兩側的分工了。實測值 $ratio11（正常應約 11.0255）",
        )

        assertEquals(19.79814571052481, contrastRatio(0xFF0A0A0Au, 0xFFFFFFFFu), 1e-9)
        assertEquals(19.682627652657427, contrastRatio(0xFF0B0B0Bu, 0xFFFFFFFFu), 1e-9)
    }

    /**
     * 三個權重（`0.2126` / `0.7152` / `0.0722`）各自讀出來——補上灰階錨點的盲區。
     *
     * 上面所有錨點都是灰階或黑白，而灰階下三個 channel 相同、權重怎麼排都算出同一個值， 所以**權重排列錯誤（R↔B、R↔G 對調）這一整類 bug
     * 對它們完全不可見**（實測：R↔B 對調時 這組六條全綠，只有 `BuiltInThemesContrastTest` 3 條 ＋ `AccentColorSelectionTest`
     * 2 條紅； R↔G 對調時兜底更薄，只有 3 條紅）。
     *
     * 這裡**不需要任何快照或外部出處**：把 channel 打到 0／255 兩端之後，`channel(0)=0` 與 `channel(255)=1`
     * 讓加權和直接退化成單一權重本身——期望值就是原始碼裡那三個常數， 是從定義推出來的，不是反算的。最後一條順帶釘住正規化（白必須恰好是 1.0）。
     */
    @Test
    fun `each luminance weight can be read back from a primary colour`() {
        assertEquals(0.2126, relativeLuminance(0xFFFF0000u), 1e-12, "紅的權重")
        assertEquals(0.7152, relativeLuminance(0xFF00FF00u), 1e-12, "綠的權重")
        assertEquals(0.0722, relativeLuminance(0xFF0000FFu), 1e-12, "藍的權重")
        assertEquals(1.0, relativeLuminance(0xFFFFFFFFu), 1e-12, "白必須正規化成 1.0")
    }
}
