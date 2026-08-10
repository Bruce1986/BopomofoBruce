package com.bopomofobruce.keyboards

import com.bopomofobruce.common.KeyAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the 注音 4×10 layout's key mapping against the "大千注音對應表" sourced verbatim from
 * zh.wikipedia.org's raw wikitext for 注音輸入法 (§鍵盤排列 > 大千注音鍵盤排列), fetched 2026-08-10:
 * ```
 * A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
 * ㄇ ㄖ ㄏ ㄎ ㄍ ㄑ ㄕ ㄘ ㄛ ㄨ ㄜ ㄠ ㄩ ㄙ ㄟ ㄣ ㄆ ㄐ ㄋ ㄔ ㄧ ㄒ ㄊ ㄌ ㄗ ㄈ
 * 1 2 3 4 5 6 7 8 9 0 - ; , . / \ ' [ ] =
 * ㄅ ㄉ ˇ ˋ ㄓ ˊ ˙ ㄚ ㄞ ㄢ ㄦ ㄤ ㄝ ㄡ ㄥ (其餘無)
 * ```
 *
 * **落差聲明（devlog 亦有記載）**：這是桌機 QWERTY 鍵位對照表，不是原 Google 注音輸入法 APK 的
 * `ime_zh_tw_zhuyin_4x10.xml`（拿不到該檔案，無法拍照比對）。40 鍵網格取自上表對應位置的符號本身（不是字母）， 這點忠於來源；`ㄦ`（桌機版在 `-` 鍵）在純 40
 * 鍵網格裡沒有格子，本模組將它放在 `ㄜ` 鍵的 longPress——這一項是 本模組自行決定，並非查證得來，見 [Keyboards.zhuyin4x10Portrait] 與同一 JSON
 * 的 `ㄜ` 鍵。
 */
class ZhuyinLayoutContentTest {

    private fun symbolsOf(rowIndex: Int) =
        Keyboards.zhuyin4x10Portrait.rows[rowIndex].map { key ->
            val action = key.action
            check(action is KeyAction.Zhuyin) { "expected Zhuyin action, got $action" }
            action.symbol
        }

    @Test
    fun `portrait has four zhuyin rows plus one control row`() {
        assertEquals(5, Keyboards.zhuyin4x10Portrait.rowCount)
        for (i in 0..3) {
            assertEquals(
                10,
                Keyboards.zhuyin4x10Portrait.rows[i].size,
                "row $i should have 10 keys",
            )
        }
    }

    @Test
    fun `row 1 matches the number-row of the sourced 大千 table (1-0)`() {
        assertEquals(listOf("ㄅ", "ㄉ", "ˇ", "ˋ", "ㄓ", "ˊ", "˙", "ㄚ", "ㄞ", "ㄢ"), symbolsOf(0))
    }

    @Test
    fun `row 2 matches the QWERTY-row of the sourced 大千 table (Q-P)`() {
        assertEquals(listOf("ㄆ", "ㄊ", "ㄍ", "ㄐ", "ㄔ", "ㄗ", "ㄧ", "ㄛ", "ㄟ", "ㄣ"), symbolsOf(1))
    }

    @Test
    fun `row 3 matches the ASDF-row of the sourced 大千 table (A-L, semicolon)`() {
        assertEquals(listOf("ㄇ", "ㄋ", "ㄎ", "ㄑ", "ㄕ", "ㄘ", "ㄨ", "ㄜ", "ㄠ", "ㄤ"), symbolsOf(2))
    }

    @Test
    fun `row 4 matches the ZXCV-row of the sourced 大千 table (Z-M, comma, period, slash)`() {
        assertEquals(listOf("ㄈ", "ㄌ", "ㄏ", "ㄒ", "ㄖ", "ㄙ", "ㄩ", "ㄝ", "ㄡ", "ㄥ"), symbolsOf(3))
    }

    @Test
    fun `ㄦ is reachable via long-press on the ㄜ key (our own placement decision)`() {
        val keKey = Keyboards.zhuyin4x10Portrait.rows[2][7]
        assertEquals("ㄜ", keKey.label)
        val longPress = requireNotNull(keKey.longPress) { "ㄜ key should carry the ㄦ long-press" }
        assertEquals("ㄦ", longPress.label)
        assertEquals(KeyAction.Zhuyin("ㄦ"), longPress.action)
    }

    @Test
    fun `all 37 zhuyin phonetic symbols plus 4 tone marks are present across the four core rows`() {
        val allSymbols =
            (0..3).flatMap { symbolsOf(it) } + listOf("ㄦ") // long-press only, not a top-level key
        val toneMarks = allSymbols.filter { it in setOf("ˇ", "ˋ", "ˊ", "˙") }
        val phoneticSymbols = allSymbols - toneMarks.toSet()
        assertEquals(4, toneMarks.size)
        assertEquals(37, phoneticSymbols.toSet().size, "expected all 37 distinct zhuyin symbols")
    }

    @Test
    fun `landscape keeps the same 40-key core grid as portrait, plus a leading digit row`() {
        val landscape = Keyboards.zhuyin4x10Landscape
        assertEquals(6, landscape.rowCount)
        // row 0 is the landscape-only digit row: our own addition, not from the sourced table.
        val digitRow =
            landscape.rows[0].map { key ->
                val action = key.action
                check(action is KeyAction.Character) { "expected Character action, got $action" }
                action.char
            }
        assertEquals("1234567890".toList(), digitRow)

        // rows 1..4 must be byte-for-byte identical to portrait's rows 0..3 (same 大千 grid).
        for (i in 0..3) {
            assertEquals(
                Keyboards.zhuyin4x10Portrait.rows[i],
                landscape.rows[i + 1],
                "landscape core row $i diverged from portrait",
            )
        }
    }
}
