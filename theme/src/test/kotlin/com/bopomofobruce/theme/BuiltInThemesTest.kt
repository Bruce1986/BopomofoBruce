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
        assertNotEquals(LightTheme.colors, DarkTheme.colors)
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
