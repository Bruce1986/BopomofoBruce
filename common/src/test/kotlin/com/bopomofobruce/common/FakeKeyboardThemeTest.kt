package com.bopomofobruce.common

import com.bopomofobruce.common.fakes.FakeKeyboardTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakeKeyboardThemeTest {

    @Test
    fun `default theme exposes non-null colors and dimens with id fake-default`() {
        val theme = FakeKeyboardTheme()

        assertEquals("fake-default", theme.id)
        assertNotNull(theme.colors)
        assertNotNull(theme.dimens)
        // 顏色高 8-bit alpha 應為 FF（不透明）。
        assertTrue((theme.colors.background.ushr(24) and 0xFFL) == 0xFFL)
        // dp 都是正值。
        assertTrue(theme.dimens.keyHeightDp > 0f)
        assertTrue(theme.dimens.candidateRowHeightDp > 0f)
    }

    @Test
    fun `can override id and colors for preview variants`() {
        val customColors =
            FakeKeyboardTheme.DEFAULT_COLORS.copy(background = 0xFFFFFFFFL, keyText = 0xFF000000L)
        val theme = FakeKeyboardTheme(id = "fake-light", colors = customColors)

        assertEquals("fake-light", theme.id)
        assertEquals(0xFFFFFFFFL, theme.colors.background)
        assertEquals(0xFF000000L, theme.colors.keyText)
        // dimens 沒覆寫應沿用 default。
        assertEquals(FakeKeyboardTheme.DEFAULT_DIMENS, theme.dimens)
    }
}
