package com.bopomofobruce.theme.style

import com.bopomofobruce.common.KeyboardColors
import com.bopomofobruce.common.KeyboardDimens
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class StyleSheetValidationTest {

    private val validColors =
        KeyboardColors(
            background = 0xFF1E1E1Eu,
            keyFill = 0xFF2E2E2Eu,
            keyText = 0xFFEAEAEAu,
            keyAccent = 0xFF4A90E2u,
            candidateText = 0xFFEAEAEAu,
            candidateHighlight = 0xFF4A90E2u,
        )

    private val validDimens =
        KeyboardDimens(keyHeightDp = 48f, rowGapDp = 4f, keyGapDp = 4f, candidateRowHeightDp = 40f)

    @Test
    fun `StyleSheet rejects blank id`() {
        assertThrows(IllegalArgumentException::class.java) {
            StyleSheet(id = "", colors = validColors, dimens = validDimens)
        }
    }

    @Test
    fun `KeyboardShapes rejects negative corner radius`() {
        assertThrows(IllegalArgumentException::class.java) {
            KeyboardShapes(keyCornerRadiusDp = -1f)
        }
    }

    @Test
    fun `KeyboardShapes rejects non-finite corner radius`() {
        assertThrows(IllegalArgumentException::class.java) {
            KeyboardShapes(panelCornerRadiusDp = Float.NaN)
        }
    }

    @Test
    fun `KeyboardShapes rejects negative or non-finite candidate row corner radius`() {
        assertThrows(IllegalArgumentException::class.java) {
            KeyboardShapes(candidateRowCornerRadiusDp = -1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KeyboardShapes(candidateRowCornerRadiusDp = Float.NaN)
        }
    }

    @Test
    fun `KeyboardTypography rejects zero or negative font sizes`() {
        assertThrows(IllegalArgumentException::class.java) { KeyboardTypography(keyLabelSp = 0f) }
        assertThrows(IllegalArgumentException::class.java) {
            KeyboardTypography(candidateTextSp = -5f)
        }
    }

    @Test
    fun `KeyboardTypography rejects zero, negative or non-finite sub label size`() {
        assertThrows(IllegalArgumentException::class.java) {
            KeyboardTypography(keySubLabelSp = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KeyboardTypography(keySubLabelSp = -1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KeyboardTypography(keySubLabelSp = Float.NaN)
        }
    }

    @Test
    fun `KeyboardTypography rejects out-of-range font weight`() {
        assertThrows(IllegalArgumentException::class.java) { KeyboardTypography(fontWeight = 0) }
        assertThrows(IllegalArgumentException::class.java) { KeyboardTypography(fontWeight = 1001) }
    }
}
