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

/** 三個內建主題與 [MaterialYouTheme][com.bopomofobruce.theme.MaterialYouTheme] 共用的預設尺寸基準。 */
object StandardDimens {
    val default: KeyboardDimens =
        KeyboardDimens(keyHeightDp = 48f, rowGapDp = 6f, keyGapDp = 4f, candidateRowHeightDp = 40f)
}
