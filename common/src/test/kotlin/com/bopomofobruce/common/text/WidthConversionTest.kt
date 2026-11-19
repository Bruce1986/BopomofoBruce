package com.bopomofobruce.common.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WidthConversionTest {

    private val asciiVisible = (0x21..0x7E).map { it.toChar() }.joinToString("")
    private val fullwidthVisible = (0xFF01..0xFF5E).map { it.toChar() }.joinToString("")

    @Test
    fun `every ASCII visible char maps to its fullwidth counterpart and back`() {
        assertEquals(94, asciiVisible.length)
        assertEquals(fullwidthVisible, WidthConversion.toFullwidth(asciiVisible))
        assertEquals(asciiVisible, WidthConversion.toHalfwidth(fullwidthVisible))
    }

    @Test
    fun `range endpoints are converted`() {
        assertEquals("！", WidthConversion.toFullwidth("!")) // U+0021 → U+FF01
        assertEquals("～", WidthConversion.toFullwidth("~")) // U+007E → U+FF5E
        assertEquals("!", WidthConversion.toHalfwidth("！"))
        assertEquals("~", WidthConversion.toHalfwidth("～"))
    }

    @Test
    fun `space maps to ideographic space and back`() {
        assertEquals(cp(0x3000), WidthConversion.toFullwidth(" "))
        assertEquals(" ", WidthConversion.toHalfwidth(cp(0x3000)))
    }

    @Test
    fun `round trip fullwidth to halfwidth to fullwidth`() {
        val full = "ＡＢＣ" + cp(0x3000) + "１２３！？～"
        assertEquals("ABC 123!?~", WidthConversion.toHalfwidth(full))
        assertEquals(full, WidthConversion.toFullwidth(WidthConversion.toHalfwidth(full)))
        val all = fullwidthVisible + cp(0x3000)
        assertEquals(all, WidthConversion.toFullwidth(WidthConversion.toHalfwidth(all)))
    }

    @Test
    fun `round trip halfwidth to fullwidth to halfwidth`() {
        val all = "$asciiVisible "
        assertEquals(all, WidthConversion.toHalfwidth(WidthConversion.toFullwidth(all)))
    }

    @Test
    fun `chars just outside the ranges are left untouched`() {
        // U+0020 以下的控制字元、U+007F DEL、U+FF00（未指派）、U+FF5F ｟、U+FFE0 ￠
        val outside = cp(0x001F, 0x007F, 0xFF00, 0xFF5F, 0xFFE0)
        assertEquals(outside, WidthConversion.toHalfwidth(outside))
        assertEquals(outside, WidthConversion.toFullwidth(outside))
    }

    @Test
    fun `other whitespace is not treated as space`() {
        val ws = cp(0x00A0, 0x0009, 0x000A, 0x2003) // NO-BREAK SPACE、Tab、LF、EM SPACE
        assertEquals(ws, WidthConversion.toFullwidth(ws))
        assertEquals(ws, WidthConversion.toHalfwidth(ws))
    }

    @Test
    fun `CJK, bopomofo and surrogate pairs are preserved`() {
        val mixed = "你好ㄋㄧˇ😀" // 😀 U+1F600 是 surrogate pair
        assertEquals(mixed, WidthConversion.toFullwidth(mixed))
        assertEquals(mixed, WidthConversion.toHalfwidth(mixed))
        assertEquals("你好!A😀", WidthConversion.toHalfwidth("你好！Ａ😀"))
        assertEquals("你好！Ａ😀", WidthConversion.toFullwidth("你好!A😀"))
    }
}
