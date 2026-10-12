package com.bopomofobruce.theme.style

import com.bopomofobruce.common.KeyboardColors
import com.bopomofobruce.common.KeyboardDimens
import kotlinx.serialization.Serializable

/**
 * 主題完整規格：contracts-v1 凍結的 [KeyboardColors] / [KeyboardDimens]（`:common`），加上 `:theme` 自己擁有、可自由演進的
 * [KeyboardShapes] / [KeyboardTypography]。
 *
 * 這是自訂主題 JSON 檔的 top-level schema。[KeyboardColors] 與 [KeyboardDimens] 已各自在 `:common` 用
 * `@Serializable` 定義好序列化器（含 [com.bopomofobruce.common.serialization.UIntHexSerializer]），
 * 這裡直接複用，不重新定義一套。
 * - [id]：穩定識別字串，對應 [com.bopomofobruce.common.KeyboardTheme.id]。
 * - [shapes] / [typography]：預設值即內建主題共用的基準，自訂主題可整包覆寫或省略沿用預設。
 *
 * **呼叫端契約（解析不受信任的主題 JSON 時）**：[StyleSheet] 本身與巢狀的 [KeyboardShapes] / [KeyboardTypography] /
 * [KeyboardDimens] 都在 `init` 用 `require` 驗證欄位範圍（[KeyboardColors] **沒有**任何驗證：六個欄位是裸的 `UInt`， 連 alpha
 * 為 0 的透明色也照收，要擋得由呼叫端自己檢查），這些 `require` 在 `Json.decodeFromString` 的反序列化路徑上一樣會執行——kotlinx
 * **不會**把它們包成 `SerializationException`，丟出來的仍是 `IllegalArgumentException`。呼叫端若只 `catch (e:
 * SerializationException)`，遇到結構合法但欄位超出範圍的 JSON（例如 `"id": ""`、`"keyLabelSp": 0`、`"keyHeightDp":
 * -1`）會讓 `IllegalArgumentException` 沒被接住，從主題載入路徑竄出去；解析 主題 JSON 時必須同時接 `SerializationException` 與
 * `IllegalArgumentException`（或接兩者共同的上層 `RuntimeException`）。
 *
 * **解碼成功不代表可以直接交給 Compose**：[KeyboardShapes] 與 [KeyboardTypography] 的尺寸欄位有防呆上限，但 [KeyboardDimens]
 * （`:common`，已凍結）只擋負值與非有限值、沒有上限。它的四個欄位（`keyHeightDp`、`rowGapDp`、`keyGapDp`、`candidateRowHeightDp`）太大時，壞的不是數值溢位
 * （`roundToPx()` 只會回一個很大的 Int），而是 Compose `Constraints` 表示不了：2026-10-12
 * 沙箱實測，`Constraints.fixedHeight` 在約 262142 px 以上丟 `IllegalArgumentException`，換算成 dp 隨密度而變（密度 1 約
 * 262142、2.625 約 99863、4 約 65535）。`Modifier.height` 會不會因此在版面配置時丟例外是由 bytecode 推論、沒有在裝置上跑過。解析不受信任的主題
 * JSON 之後，呼叫端在包成 `Dp` 之前要自己把這四個欄位夾到合理範圍（實測夾到 1000 之後不再丟）。
 */
@Serializable
data class StyleSheet(
    val id: String,
    val colors: KeyboardColors,
    val dimens: KeyboardDimens,
    val shapes: KeyboardShapes = KeyboardShapes(),
    val typography: KeyboardTypography = KeyboardTypography(),
) {
    init {
        require(id.isNotBlank()) { "StyleSheet id must not be blank" }
    }
}

/**
 * `LightTheme`／`DarkTheme` 共用的預設尺寸基準；[MaterialYouTheme][com.bopomofobruce.theme.MaterialYouTheme]
 * 經由退化主題的 `copy` 沿用同一份。
 */
object StandardDimens {
    val default: KeyboardDimens =
        KeyboardDimens(keyHeightDp = 48f, rowGapDp = 6f, keyGapDp = 4f, candidateRowHeightDp = 40f)
}
