package com.bopomofobruce.common.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 碼位寫成 Int（ktfmt 會把 `\uXXXX` 改成字面字元，組合用附加符號會看不見）。本 package 測試共用。 */
internal fun cp(vararg codes: Int): String = codes.joinToString("") { it.toChar().toString() }

class ZhuyinTextTest {

    /** 期望表獨立於 [ZhuyinText.CONFUSABLES] 手寫，刪掉或改錯任一條對應都會紅。 */
    private val expected =
        listOf(
            cp(0x4E00) + "ㄉㄧㄢ" + cp(0x02C7) to "ㄧㄉㄧㄢ" + cp(0x02C7), // 一 → ㄧ
            "ㄉ" + cp(0x2F00) + "ㄢ" to "ㄉㄧㄢ", // ⼀ KANGXI RADICAL ONE → ㄧ
            "ㄇㄚ" + cp(0x02C9) + "ㄇㄚ" to "ㄇㄚ ㄇㄚ", // ˉ MACRON → 一聲空白
            "ㄇㄚ" + cp(0x00B4) to "ㄇㄚ" + cp(0x02CA),
            "ㄇㄚ" + cp(0x0301) to "ㄇㄚ" + cp(0x02CA),
            "ㄇㄚ" + cp(0x030C) to "ㄇㄚ" + cp(0x02C7),
            "ㄇㄚ" + cp(0x0060) to "ㄇㄚ" + cp(0x02CB),
            "ㄇㄚ" + cp(0xFF40) to "ㄇㄚ" + cp(0x02CB),
            "ㄇㄚ" + cp(0x0300) to "ㄇㄚ" + cp(0x02CB),
            "ㄇㄚ" + cp(0x0307) to "ㄇㄚ" + cp(0x02D9),
        )

    @Test
    fun `each confusable maps to its canonical form`() {
        assertEquals(expected.size, ZhuyinText.CONFUSABLES.size) // 新增對應時要補期望
        for ((input, want) in expected) assertEquals(want, ZhuyinText.normalize(input), input)
    }

    @Test
    fun `near-miss characters are not mapped`() {
        // 丁 二 ⼆(KANGXI TWO) ¯ ' U+0302 U+0304 ˆ、中點 · ‧ ・（待裁決，暫不當輕聲）、字源相近的 丫
        val untouched =
            listOf(0x4E01, 0x4E8C, 0x2F06, 0x00AF, 0x0027, 0x0302, 0x0304, 0x02C6) +
                listOf(0x00B7, 0x2027, 0x30FB, 0x4E2B)
        for (code in untouched) {
            assertEquals(
                "ㄇ" + cp(code),
                ZhuyinText.normalize("ㄇ" + cp(code)),
                "U+%04X".format(code),
            )
        }
    }

    @Test
    fun `canonical zhuyin passes through unchanged`() {
        val keyboardSymbols =
            cp(*(0x3105..0x3129).toList().toIntArray()) + cp(0x02CA, 0x02C7, 0x02CB, 0x02D9)
        assertEquals(41, keyboardSymbols.length) // 37 個注音＋4 個聲調，與注音鍵盤 JSON 相同
        assertEquals(keyboardSymbols, ZhuyinText.normalize(keyboardSymbols))
        assertEquals("ㄋㄧˇㄏㄠˇ", ZhuyinText.normalize("ㄋㄧˇㄏㄠˇ"))
        // 未做 NFKC：NFKC 會把 U+02D9 拆成 U+0020 U+0307
        assertEquals("ㄇㄚ" + cp(0x02D9), ZhuyinText.normalize("ㄇㄚ" + cp(0x02D9)))
    }

    @Test
    fun `all whitespace kinds become a single space between first-tone syllables`() {
        // IDEOGRAPHIC SPACE、NO-BREAK SPACE、EM SPACE、Tab＋LF 混合
        for (ws in listOf(cp(0x3000), cp(0x00A0), cp(0x2003), " \t\n ")) {
            assertEquals("ㄍㄨㄥ ㄖㄣˊ", ZhuyinText.normalize("ㄍㄨㄥ" + ws + "ㄖㄣˊ"), ws)
        }
    }

    @Test
    fun `redundant spaces are dropped but the first-tone boundary is kept`() {
        assertEquals("ㄋㄧˇㄏㄠˇ", ZhuyinText.normalize("  ㄋㄧˇ  ㄏㄠˇ  ")) // 開頭、聲調後、結尾
        assertEquals("ㄇㄚ ㄇㄚ˙", ZhuyinText.normalize("ㄇㄚ ㄇㄚ˙"))
        assertEquals("ㄇㄚ", ZhuyinText.normalize("ㄇㄚ "))
        assertEquals("", ZhuyinText.normalize(" " + cp(0x3000) + " "))
    }

    @Test
    fun `non-zhuyin characters are preserved for the validator to reject`() {
        assertEquals("ㄋㄧˇ好", ZhuyinText.normalize("ㄋㄧˇ好"))
        assertFalse(ZhuyinText.isValidSequence(ZhuyinText.normalize("ㄋㄧˇ好")))
    }

    @Test
    fun `valid sequences`() {
        val cases = listOf("ㄋㄧˇㄏㄠˇ", "ㄍㄨㄥ ㄖㄣˊ", "ㄇㄚ", "ㄇㄚ˙", "ㄓ", "ㄧ", "ㄦˊ", "ㄋㄧˇ ㄏㄠˇ ", "ㄒㄩㄝˊ")
        for (s in cases) assertTrue(ZhuyinText.isValidSequence(s), s)
    }

    @Test
    fun `invalid sequences`() {
        val cases =
            listOf(
                "", // 沒有音節
                "  ", // 只有空白
                "ㄏㄋㄧˇ", // 兩個聲母相連
                "ㄍㄨㄥㄖㄣˊ", // 一聲漏空白 → 韻母後接聲母
                "ㄋㄧㄨˇ", // 兩個介音
                "ㄚㄧ", // 介音在韻母後
                "ㄋㄧˇˇ", // 重複聲調
                "ˇㄋㄧ", // 開頭聲調
                "ㄋㄧ ˇ", // 空白後的聲調
                cp(0x4E00) + "ㄉㄧㄢˇ", // 未正規化的 一
                "ㄋㄧˇa", // 非注音
                cp(0x31A0), // Bopomofo Extended ㆠ U+31A0
            )
        for (s in cases) assertFalse(ZhuyinText.isValidSequence(s), s)
    }

    @Test
    fun `normalize repairs inputs the validator would otherwise reject`() {
        val raw = cp(0x4E00, 0x3000) + "ㄉㄧㄢ" + cp(0x00B4)
        assertFalse(ZhuyinText.isValidSequence(raw))
        assertEquals("ㄧ ㄉㄧㄢˊ", ZhuyinText.normalize(raw))
        assertTrue(ZhuyinText.isValidSequence(ZhuyinText.normalize(raw)))
    }
}
