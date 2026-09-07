package com.bopomofobruce.theme.style

import com.bopomofobruce.common.KeyboardDimens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 把 [KeyboardShapes] / [KeyboardTypography] / [StandardDimens] 的**預設值字面數字**釘住。
 *
 * 為什麼需要這一條：這些預設值是**兩個內建主題實際會吃到的值**——`BuiltInThemes.kt` 的 `LightTheme` / `DarkTheme` 都沒有明確指定 `shapes
 * = ` / `typography = `，直接沿用 [StyleSheet] 的預設參數。但在此之前沒有任何測試鎖住這些數字：
 * - `StyleSheetSerializationTest` 的「省略時套用預設值」那條，比對的是 `decoded.typography` 與
 *   `KeyboardTypography()`——**兩邊吃的是同一份預設值**，預設值一改 兩邊一起變，那條斷言恆真。
 * - round-trip 測試都是顯式傳入自訂數值，沒有在斷言「預設值必須是這個數字」。
 *
 * 突變實測（2026-09-08）：把 `keyLabelSp` 的預設值從 `18f` 改成 `99f`（正常鍵盤字級的五倍以上， UI 上是顯而易見的災難）→ 全套 52
 * 條**零反應、BUILD SUCCESSFUL**。
 *
 * 這條測試本身沒有「驗算」任何東西，它就是一份簽入的常數快照——目的是讓「手滑改到預設值」 與「合併衝突解錯邊」這類意外必須經過一次人工確認，而不是靜靜地改變兩個內建主題的長相。
 */
class StyleDefaultsTest {

    @Test
    fun `KeyboardShapes defaults are pinned`() {
        val shapes = KeyboardShapes()
        assertEquals(8f, shapes.keyCornerRadiusDp, "按鍵圓角預設值變了")
        assertEquals(0f, shapes.candidateRowCornerRadiusDp, "候選列圓角預設值變了")
        assertEquals(0f, shapes.panelCornerRadiusDp, "面板圓角預設值變了")
    }

    @Test
    fun `KeyboardTypography defaults are pinned`() {
        val typography = KeyboardTypography()
        assertEquals(18f, typography.keyLabelSp, "按鍵主 label 字級預設值變了")
        assertEquals(12f, typography.keySubLabelSp, "按鍵次要 label 字級預設值變了")
        assertEquals(16f, typography.candidateTextSp, "候選列字級預設值變了")
        assertEquals(400, typography.fontWeight, "字重預設值變了")
    }

    @Test
    fun `StandardDimens default is pinned`() {
        assertEquals(
            KeyboardDimens(
                keyHeightDp = 48f,
                rowGapDp = 6f,
                keyGapDp = 4f,
                candidateRowHeightDp = 40f,
            ),
            StandardDimens.default,
            "三個內建主題共用的尺寸基準變了",
        )
    }
}
