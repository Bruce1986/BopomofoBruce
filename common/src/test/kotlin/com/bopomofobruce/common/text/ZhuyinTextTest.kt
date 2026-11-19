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
        // 丁 二 ⼆(KANGXI TWO) ¯ ' U+0302 U+0304 ˆ；待裁決的：中點 · ‧ ・、字源相近的 丫、ZERO WIDTH SPACE
        val untouched =
            listOf(0x4E01, 0x4E8C, 0x2F06, 0x00AF, 0x0027, 0x0302, 0x0304, 0x02C6) +
                listOf(0x00B7, 0x2027, 0x30FB, 0x4E2B, 0x200B)
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
        for (t in listOf(cp(0x02CA), cp(0x02C7), cp(0x02CB), cp(0x02D9))) {
            assertEquals("ㄇㄚ${t}ㄇㄚ", ZhuyinText.normalize("ㄇㄚ$t ㄇㄚ"), t) // 每個聲調後的空白都刪
        }
    }

    @Test
    fun `trailing first-tone space is dropped, so output must not be concatenated`() {
        assertEquals("ㄍㄨㄥ ㄖㄣˊ", ZhuyinText.normalize("ㄍㄨㄥ " + "ㄖㄣˊ")) // 整串一次正規化才對
        assertEquals("ㄍㄨㄥㄖㄣˊ", ZhuyinText.normalize("ㄍㄨㄥ ") + "ㄖㄣˊ") // KDoc 警告的誤用
    }

    @Test
    fun `non-zhuyin characters are preserved for the validator to reject`() {
        assertEquals("ㄋㄧˇ好", ZhuyinText.normalize("ㄋㄧˇ好"))
        assertFalse(ZhuyinText.isValidSequence(ZhuyinText.normalize("ㄋㄧˇ好")))
    }

    @Test
    fun `valid sequences`() {
        val cases =
            listOf(
                "ㄋㄧˇㄏㄠˇ",
                "ㄍㄨㄥ ㄖㄣˊ",
                "ㄇㄚ",
                "ㄇㄚ˙",
                "ㄓ",
                "ㄧ",
                "ㄦˊ",
                "ㄋㄧˇ ㄏㄠˇ ",
                "ㄒㄩㄝˊ",
                "ㄉㄚˋ",
                "ㄉㄚˋ ㄇㄚ",
                "ㄇㄚ ", // 尾端一聲空白也算音節結尾
                "ㄉㄚˋ ㄇㄚ ",
            )
        for (s in cases) assertTrue(ZhuyinText.isValidSequence(s), s)
    }

    @Test
    fun `known blind spots pass because the check is structural only`() {
        // KDoc 記載的已知缺口：bpmf.h 的「ㄏ ㄋㄧˇ」擋不下；ㄑ˙（啐）在 libchewing 字庫裡，不能用聲韻規則硬擋；
        // 輕聲前置的「媽媽」ㄇㄚ˙ㄇㄚ 與後置寫法正規化後相同（待裁決）。
        for (s in listOf("ㄏ ㄋㄧˇ", "ㄑ˙", "ㄇㄚ˙ㄇㄚ")) assertTrue(ZhuyinText.isValidSequence(s), s)
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
                "ㄇㄚㄚ", // 兩個韻母
                "ㄚㄧ", // 介音在韻母後
                "ㄋㄧˇˇ", // 重複聲調
                "ㄋㄧˇˇㄏㄠˇ", // 緩衝中段重複聲調（bpmf.h 的例子）
                "ˇㄋㄧ", // 開頭聲調
                "ㄋㄧ ˇ", // 空白後的聲調
                "˙ㄇㄚ", // 輕聲前置（開頭）
                "ㄇㄚ ˙ㄇㄚ", // 輕聲前置（空白後）
                cp(0x4E00) + "ㄉㄧㄢˇ", // 未正規化的 一
                "ㄋㄧˇa", // 非注音
                cp(0x31A0), // Bopomofo Extended ㆠ U+31A0
                "ㄇㄚ" + cp(0x00B7), // 中點（待裁決）
                "ㄇㄚ" + cp(0x200B) + "ㄇㄚ", // 零寬空白（待裁決）
                "ㄇㄚ" + cp(0x3000) + "ㄇㄚ", // 非 U+0020 的空白要先 normalize
                "ㄇㄚ\tㄇㄚ",
            )
        for (s in cases) assertFalse(ZhuyinText.isValidSequence(s), s)
        // 未正規化的易混字元一律不算聲調／空白
        for (k in ZhuyinText.CONFUSABLES.keys) {
            assertFalse(ZhuyinText.isValidSequence("ㄇㄚ$k"), "U+%04X".format(k.code))
        }
    }

    @Test
    fun `every letter is classified into its own category`() {
        // 三類手寫成字面字串，與實作的碼位區間分開；任一字母歸錯類，某個組合就會翻轉。
        val initials = "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙ"
        val medials = "ㄧㄨㄩ"
        val finals = "ㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦ"
        assertEquals(37, initials.length + medials.length + finals.length)
        for (i in initials) for (m in medials) for (f in finals) {
            assertTrue(ZhuyinText.isValidSequence("$i$m$f"), "$i$m$f")
        }
        for ((later, earlier) in
            listOf(medials to initials, finals to medials, finals to initials)) {
            for (a in later) for (b in earlier) assertFalse(
                ZhuyinText.isValidSequence("$a$b"),
                "$a$b",
            )
        }
    }

    @Test
    fun `only U+3105 to U+3129 count as zhuyin letters`() {
        for (code in 0x3100..0x312F) {
            val want = code in 0x3105..0x3129
            assertEquals(want, ZhuyinText.isValidSequence(cp(code)), "U+%04X".format(code))
        }
    }

    @Test
    fun `normalize repairs inputs the validator would otherwise reject`() {
        val raw = cp(0x4E00, 0x3000) + "ㄉㄧㄢ" + cp(0x00B4)
        assertFalse(ZhuyinText.isValidSequence(raw))
        assertEquals("ㄧ ㄉㄧㄢˊ", ZhuyinText.normalize(raw))
        assertTrue(ZhuyinText.isValidSequence(ZhuyinText.normalize(raw)))
    }
}
