package com.bopomofobruce.theme.style

import com.bopomofobruce.common.KeyboardColors
import com.bopomofobruce.common.KeyboardDimens
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 對 `:theme` 自己擁有的 `@Serializable` 型別做 serialize → string → deserialize → assertEquals， 比照
 * `:common` 的 `SerializationRoundTripTest` 寫法（W1-B 驗收項：主題序列化/反序列化 round-trip test）。
 */
class StyleSheetSerializationTest {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    @Test
    fun `KeyboardShapes round trips with defaults`() {
        val original = KeyboardShapes()

        val encoded = json.encodeToString(KeyboardShapes.serializer(), original)
        val decoded = json.decodeFromString(KeyboardShapes.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `KeyboardShapes round trips with custom values`() {
        val original =
            KeyboardShapes(
                keyCornerRadiusDp = 12f,
                candidateRowCornerRadiusDp = 4f,
                panelCornerRadiusDp = 16f,
            )

        val encoded = json.encodeToString(KeyboardShapes.serializer(), original)
        val decoded = json.decodeFromString(KeyboardShapes.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `KeyboardTypography round trips`() {
        val original =
            KeyboardTypography(
                keyLabelSp = 20f,
                keySubLabelSp = 11f,
                candidateTextSp = 15f,
                fontWeight = 600,
            )

        val encoded = json.encodeToString(KeyboardTypography.serializer(), original)
        val decoded = json.decodeFromString(KeyboardTypography.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `StyleSheet round trips full object graph including nested UIntHex colors`() {
        val original =
            StyleSheet(
                id = "custom-photo-2026",
                colors =
                    KeyboardColors(
                        background = 0xFF1E1E1Eu,
                        keyFill = 0xFF2E2E2Eu,
                        keyText = 0xFFEAEAEAu,
                        keyAccent = 0xFF4A90E2u,
                        candidateText = 0xFFEAEAEAu,
                        candidateHighlight = 0xFF4A90E2u,
                    ),
                dimens =
                    KeyboardDimens(
                        keyHeightDp = 48f,
                        rowGapDp = 4f,
                        keyGapDp = 4f,
                        candidateRowHeightDp = 40f,
                    ),
                shapes = KeyboardShapes(keyCornerRadiusDp = 10f),
                typography = KeyboardTypography(keyLabelSp = 19f),
            )

        val encoded = json.encodeToString(StyleSheet.serializer(), original)
        val decoded = json.decodeFromString(StyleSheet.serializer(), encoded)

        assertEquals(original, decoded)
        // wire format 應該重用 :common 的 hex 慣例，而不是另立一套數字格式。
        assert(encoded.contains("\"background\":\"0xFF1E1E1E")) {
            "expected hex-string ARGB in encoded StyleSheet, got: $encoded"
        }
    }

    @Test
    fun `StyleSheet applies shapes and typography defaults when omitted from JSON`() {
        val minimalJson =
            """
            {
                "id": "minimal",
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
                }
            }
            """
                .trimIndent()

        val decoded = json.decodeFromString(StyleSheet.serializer(), minimalJson)

        assertEquals(KeyboardShapes(), decoded.shapes)
        assertEquals(KeyboardTypography(), decoded.typography)
    }
}
