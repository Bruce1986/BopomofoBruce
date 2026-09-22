package com.bopomofobruce.theme.style

import com.bopomofobruce.common.KeyboardColors
import com.bopomofobruce.common.KeyboardDimens
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertFalse
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
        // 0f 要單獨測：`> 0f` 誤寫成 `>= 0f` 時 -5f 照樣被擋，只有 0f 分得出來。
        assertThrows(IllegalArgumentException::class.java) {
            KeyboardTypography(candidateTextSp = 0f)
        }
    }

    // NaN 在前面的 `>= 0f`／`> 0f` 就已經是 false、走不到 isFinite()，所以上面那幾條 NaN 案例守不住
    // isFinite()；真正只靠 isFinite() 擋下的是 +Infinity（2026-09-15 突變實測：刪掉六個欄位的 isFinite() 全套仍綠）。
    @Test
    fun `KeyboardShapes and KeyboardTypography reject positive infinity in every size field`() {
        val inf = Float.POSITIVE_INFINITY
        val builders: Map<String, () -> Any> =
            mapOf(
                "keyCornerRadiusDp" to { KeyboardShapes(keyCornerRadiusDp = inf) },
                "candidateRowCornerRadiusDp" to
                    {
                        KeyboardShapes(candidateRowCornerRadiusDp = inf)
                    },
                "panelCornerRadiusDp" to { KeyboardShapes(panelCornerRadiusDp = inf) },
                "keyLabelSp" to { KeyboardTypography(keyLabelSp = inf) },
                "keySubLabelSp" to { KeyboardTypography(keySubLabelSp = inf) },
                "candidateTextSp" to { KeyboardTypography(candidateTextSp = inf) },
            )
        for ((field, build) in builders) {
            assertThrows(IllegalArgumentException::class.java, { build() }, field)
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

        val thrown =
            assertThrows(IllegalArgumentException::class.java) {
                Json.decodeFromString(StyleSheet.serializer(), jsonWithZeroKeyLabelSp)
            }

        // 光斷言 IllegalArgumentException 不夠：kotlinx-serialization 1.7.3 的
        // SerializationException 本身就繼承 IllegalArgumentException，所以即使未來這條路徑改成
        // 把 require 包成 SerializationException（＝呼叫端只 catch SerializationException 就接得
        // 住，本測試想證明的問題也就不存在了），上面那條斷言仍會綠。必須額外排除掉子類，這條
        // 測試才真的鎖得住「逃出來的是裸的 IAE、接 SerializationException 會漏接」這個性質。
        assertFalse(
            thrown is SerializationException,
            "expected a bare IllegalArgumentException that `catch (e: SerializationException)` " +
                "would NOT catch, but got ${thrown::class.qualifiedName}",
        )
    }
}
