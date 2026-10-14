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

    /**
     * 持久化 schema 的字面值快照：固定的 JSON 字串 ↔ 固定的物件，兩個方向都驗。
     *
     * 上面的 round-trip 都是 encode → decode 自洽，欄位改名時兩邊一起變、照樣綠（2026-10-12 突變實測：`shapes`、
     * `keyCornerRadiusDp`、`candidateRowCornerRadiusDp`、`panelCornerRadiusDp`、`keySubLabelSp`、`candidateTextSp`、
     * `fontWeight` 各自加上不同的 `@SerialName`，87 條全綠）。改名的後果是已經存下的主題 JSON 解不出來 （預設的 `Json`
     * 會因未知欄位丟例外；`ignoreUnknownKeys = true` 的讀取端則靜靜退回預設值）。
     *
     * 每個欄位都刻意用**非預設值**，這樣「欄位被忽略而退回預設值」不會剛好等於期望值。 encode 方向依賴本檔的 `encodeDefaults = true` 與欄位宣告順序； 要改
     * schema 就連這條一起改，並決定舊資料怎麼遷移。
     */
    @Test
    fun `StyleSheet wire format is pinned by a golden json in both directions`() {
        val golden =
            """
            {
                "id": "golden",
                "colors": {
                    "background": "0xFF1E1E1E",
                    "keyFill": "0xFF2E2E2E",
                    "keyText": "0xFFEAEAEA",
                    "keyAccent": "0xFF4A90E2",
                    "candidateText": "0xFFF0F0F0",
                    "candidateHighlight": "0xFF3A6EA5"
                },
                "dimens": {
                    "keyHeightDp": 50.0,
                    "rowGapDp": 5.0,
                    "keyGapDp": 3.0,
                    "candidateRowHeightDp": 42.0
                },
                "shapes": {
                    "keyCornerRadiusDp": 10.0,
                    "candidateRowCornerRadiusDp": 2.0,
                    "panelCornerRadiusDp": 16.0
                },
                "typography": {
                    "keyLabelSp": 19.0,
                    "keySubLabelSp": 11.0,
                    "candidateTextSp": 15.0,
                    "fontWeight": 600
                }
            }
            """
                .trimIndent()
        val expected =
            StyleSheet(
                id = "golden",
                colors =
                    KeyboardColors(
                        background = 0xFF1E1E1Eu,
                        keyFill = 0xFF2E2E2Eu,
                        keyText = 0xFFEAEAEAu,
                        keyAccent = 0xFF4A90E2u,
                        candidateText = 0xFFF0F0F0u,
                        candidateHighlight = 0xFF3A6EA5u,
                    ),
                dimens =
                    KeyboardDimens(
                        keyHeightDp = 50f,
                        rowGapDp = 5f,
                        keyGapDp = 3f,
                        candidateRowHeightDp = 42f,
                    ),
                shapes =
                    KeyboardShapes(
                        keyCornerRadiusDp = 10f,
                        candidateRowCornerRadiusDp = 2f,
                        panelCornerRadiusDp = 16f,
                    ),
                typography =
                    KeyboardTypography(
                        keyLabelSp = 19f,
                        keySubLabelSp = 11f,
                        candidateTextSp = 15f,
                        fontWeight = 600,
                    ),
            )

        assertEquals(expected, json.decodeFromString(StyleSheet.serializer(), golden))
        // golden 的值裡沒有任何空白字元，所以拿掉排版用的空白就是 prettyPrint = false 的輸出。
        assertEquals(
            golden.filterNot { it.isWhitespace() },
            json.encodeToString(StyleSheet.serializer(), expected),
        )
    }
}
