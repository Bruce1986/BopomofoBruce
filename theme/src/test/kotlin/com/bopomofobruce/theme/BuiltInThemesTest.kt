package com.bopomofobruce.theme

import com.bopomofobruce.theme.style.KeyboardShapes
import com.bopomofobruce.theme.style.KeyboardTypography
import com.bopomofobruce.theme.style.StandardDimens
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

    /**
     * 上面兩條都是「主題對主題」「主題對自己的 styleSheet」，鎖不住絕對值：兩個主題一起改成別的尺寸、或 `styleSheet.id` 跟 `theme.id`
     * 對不上，都不會紅（2026-10-11 突變實測）。`StyleDefaultsTest` 釘的是 [StandardDimens.default]
     * 的數值，這條補上「內建主題真的用它」。
     */
    @Test
    fun `built-in themes use the standard dimens and a styleSheet carrying their own id`() {
        assertEquals(LightTheme.id, LightTheme.styleSheet.id, "light styleSheet.id")
        assertEquals(DarkTheme.id, DarkTheme.styleSheet.id, "dark styleSheet.id")
        assertEquals(StandardDimens.default, LightTheme.dimens, "light dimens")
        assertEquals(StandardDimens.default, DarkTheme.dimens, "dark dimens")
    }

    /**
     * `StyleDefaultsTest` 釘的是 [KeyboardShapes]／[KeyboardTypography] 預設值的數字，它的 KDoc
     * 說那些就是「兩個內建主題實際會吃到的值」 ——但沒有任何一條驗內建主題真的沿用預設值。在 `BuiltInThemes.kt` 明確指定 `shapes = …`／`typography
     * = …` （單一主題或兩個一起）時 87 條全綠（2026-10-12 突變實測）；`MaterialYouThemeTest` 比的是「與退化主題相同」，會跟著一起變。
     */
    @Test
    fun `built-in themes use the default shapes and typography`() {
        assertEquals(KeyboardShapes(), LightTheme.styleSheet.shapes, "light shapes")
        assertEquals(KeyboardTypography(), LightTheme.styleSheet.typography, "light typography")
        assertEquals(KeyboardShapes(), DarkTheme.styleSheet.shapes, "dark shapes")
        assertEquals(KeyboardTypography(), DarkTheme.styleSheet.typography, "dark typography")
    }

    /** 對比度公式不看 alpha（見 `ContrastFormulaKnownValuesTest`），所以某個色被改成透明時，12 條對比守門全綠。 */
    @Test
    fun `built-in theme colors are fully opaque`() {
        for (theme in listOf(LightTheme, DarkTheme)) {
            val colors = theme.colors
            val byName =
                mapOf(
                    "background" to colors.background,
                    "keyFill" to colors.keyFill,
                    "keyText" to colors.keyText,
                    "keyAccent" to colors.keyAccent,
                    "candidateText" to colors.candidateText,
                    "candidateHighlight" to colors.candidateHighlight,
                )
            for ((name, value) in byName) {
                assertEquals(0xFFu, value shr 24, "${theme.id}.$name alpha")
            }
        }
    }
}
