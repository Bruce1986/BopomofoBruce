package com.bopomofobruce.common.fakes

import com.bopomofobruce.common.KeyboardColors
import com.bopomofobruce.common.KeyboardDimens
import com.bopomofobruce.common.KeyboardTheme

/**
 * 給 **unit test 與 Compose preview** 用的固定 [KeyboardTheme]。
 *
 * **不要** 在 production 依賴 — 真實主題在 :theme 模組（W1-B）。
 *
 * 用一組「中性 dark 風格」的顏色與標準 dp，讓 preview 在 light/dark host 下都看得清。
 */
class FakeKeyboardTheme(
    override val id: String = "fake-default",
    override val colors: KeyboardColors = DEFAULT_COLORS,
    override val dimens: KeyboardDimens = DEFAULT_DIMENS,
) : KeyboardTheme {

    companion object {
        /** ARGB UInt 常數；高 8-bit alpha + RGB。 */
        val DEFAULT_COLORS: KeyboardColors =
            KeyboardColors(
                background = 0xFF1E1E1Eu,
                keyFill = 0xFF2E2E2Eu,
                keyText = 0xFFEAEAEAu,
                keyAccent = 0xFF4A90E2u,
                candidateText = 0xFFEAEAEAu,
                candidateHighlight = 0xFF4A90E2u,
            )

        val DEFAULT_DIMENS: KeyboardDimens =
            KeyboardDimens(
                keyHeightDp = 48f,
                rowGapDp = 4f,
                keyGapDp = 4f,
                candidateRowHeightDp = 40f,
            )
    }
}
