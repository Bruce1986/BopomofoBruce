package com.bopomofobruce.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class BuiltInThemesTest {

    @Test
    fun `LightTheme and DarkTheme report stable ids`() {
        assertEquals("light", LightTheme.id)
        assertEquals("dark", DarkTheme.id)
    }

    @Test
    fun `LightTheme and DarkTheme use distinct colors`() {
        // B23：舊寫法只斷言整個 data class 不相等，6 個欄位只要 1 個不同就綠——「複製 Light 改 Dark
        // 但漏改某個欄位」這個典型失誤抓不到。改成逐欄位斷言，缺一不可。
        assertNotEquals(LightTheme.colors.background, DarkTheme.colors.background, "background")
        assertNotEquals(LightTheme.colors.keyFill, DarkTheme.colors.keyFill, "keyFill")
        assertNotEquals(LightTheme.colors.keyText, DarkTheme.colors.keyText, "keyText")
        assertNotEquals(LightTheme.colors.keyAccent, DarkTheme.colors.keyAccent, "keyAccent")
        assertNotEquals(
            LightTheme.colors.candidateText,
            DarkTheme.colors.candidateText,
            "candidateText",
        )
        assertNotEquals(
            LightTheme.colors.candidateHighlight,
            DarkTheme.colors.candidateHighlight,
            "candidateHighlight",
        )
    }

    @Test
    fun `LightTheme and DarkTheme share the standard dimens baseline`() {
        assertEquals(LightTheme.dimens, DarkTheme.dimens)
    }

    @Test
    fun `theme colors and dimens are consistent with the exposed styleSheet`() {
        assertEquals(LightTheme.styleSheet.colors, LightTheme.colors)
        assertEquals(LightTheme.styleSheet.dimens, LightTheme.dimens)
        assertEquals(DarkTheme.styleSheet.colors, DarkTheme.colors)
        assertEquals(DarkTheme.styleSheet.dimens, DarkTheme.dimens)
    }
}
