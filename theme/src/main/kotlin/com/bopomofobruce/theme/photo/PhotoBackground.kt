@file:UseSerializers(UIntHexSerializer::class)

package com.bopomofobruce.theme.photo

import com.bopomofobruce.common.serialization.UIntHexSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * 使用者自訂的相片背景設定。
 * - [uri]：使用者從相簿選的圖片 `content://` URI，存 [String] 而非 `android.net.Uri`，讓 `:theme` 的資料模型維持可在無 Android
 *   runtime 情境下序列化/反序列化（跟 [com.bopomofobruce.common.KeyData] 等 contracts-v1 型別同一慣例）。
 * - [blurRadiusDp]：高斯模糊半徑，`0f` 代表不模糊。
 * - [opacity]：疊加不透明度，`0f`（完全透明）..`1f`（完全不透明）。
 * - [tint]：疊加色（ARGB [UInt]，比照 [com.bopomofobruce.common.KeyboardColors] 慣例），`null` 代表不上色。
 */
@Serializable
data class PhotoBackground(
    val uri: String,
    val blurRadiusDp: Float = 0f,
    val opacity: Float = 1f,
    val tint: UInt? = null,
) {
    init {
        require(uri.isNotBlank()) { "PhotoBackground uri must not be blank" }
        require(blurRadiusDp >= 0f && blurRadiusDp.isFinite()) {
            "blurRadiusDp must be >= 0 and finite, but was $blurRadiusDp"
        }
        require(opacity in 0f..1f) { "opacity must be within 0f..1f, but was $opacity" }
    }
}
