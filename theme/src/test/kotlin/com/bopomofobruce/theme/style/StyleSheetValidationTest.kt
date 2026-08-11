package com.bopomofobruce.theme.style

import com.bopomofobruce.common.KeyboardColors
import com.bopomofobruce.common.KeyboardDimens
import kotlinx.serialization.json.Json
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

    // O2（第十四輪 tracer）：反序列化路徑上 require 丟的仍是 IllegalArgumentException，不是
    // SerializationException——呼叫端若只接 SerializationException 會漏接。這條測試直接走
    // decodeFromString，證明「結構合法但欄位超出範圍」的主題 JSON 逃出來的例外型別。
    @Test
    fun `StyleSheet decodeFromString throws IllegalArgumentException for out-of-range nested field`() {
        val jsonWithZeroKeyLabelSp =
            """
            {
                "id": "bad-typography",
                "colors": {
                    "background": "#1E1E1E",
                    "keyFill": "#2E2E2E",
                    "keyText": "#EAEAEA",
                    "keyAccent": "#4A90E2",
                    "candidateText": "#EAEAEA",
                    "candidateHighlight": "#4A90E2"
                },
                "dimens": {
                    "keyHeightDp": 48.0,
                    "rowGapDp": 4.0,
                    "keyGapDp": 4.0,
                    "candidateRowHeightDp": 40.0
                },
                "typography": {
                    "keyLabelSp": 0.0
                }
            }
            """
                .trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            Json.decodeFromString(StyleSheet.serializer(), jsonWithZeroKeyLabelSp)
        }
    }
}
