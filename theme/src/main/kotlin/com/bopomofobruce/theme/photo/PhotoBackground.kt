@file:UseSerializers(UIntHexSerializer::class)

package com.bopomofobruce.theme.photo

import com.bopomofobruce.common.serialization.UIntHexSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * 使用者自訂的相片背景設定。
 * - [uri]：使用者從相簿選的圖片 `content://` URI，存 [String] 而非 `android.net.Uri`，讓 `:theme` 的資料模型維持可在無 Android
 *   runtime 情境下序列化/反序列化（跟 [com.bopomofobruce.common.KeyData] 等 contracts-v1 型別同一慣例）。 **呼叫端契約（[uri]
 *   這個 `@Serializable` 型別的存在目的就是被寫進 DataStore 長期保存，但 `content://` 授權預設不是持久的）**： 呼叫端在把使用者選的圖片存進
 *   [uri] 之前，必須先透過 SAF `ACTION_OPEN_DOCUMENT` 取得該 URI，並呼叫
 *   `contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`
 *   取得可持久化授權， 授權才會跨重開機存活。`MediaStore.ACTION_PICK_IMAGES`（Android Photo Picker）發出的 URI **不支援**
 *   `takePersistableUriPermission`——授權隨 task／process 結束即失效，把這種 URI 存進 [uri] 會在下次重開機後讓相片背景
 *   悄悄消失（唯一訊號是 [PhotoBackgroundLayer] 的一行 `Log.w`，見該檔 KDoc）。
 * - [blurRadiusDp]：高斯模糊半徑，`0f` 代表不模糊，上限 [MAX_BLUR_RADIUS_DP]（避免呼叫端傳入
 *   離譜大的值——模糊層邊界外擴、在部分渲染路徑上可能造成明顯效能與畫面裁切問題）。
 * - [opacity]：疊加不透明度，`0f`（完全透明）..`1f`（完全不透明）。
 * - [tint]：疊加色（ARGB [UInt]，比照 [com.bopomofobruce.common.KeyboardColors] 慣例），`null` 代表不上色。
 *   [PhotoBackgroundLayer] 用 `BlendMode.SrcAtop` 套用這個顏色：alpha 就是疊色強度本身，`0xFF`（不透明）
 *   會讓整張相片被蓋成純色矩形、完全看不到底圖。這裡的 `init` **不會**驗證或限制 alpha——想要「疊色但仍看得到 相片」，呼叫端（例如設定頁）必須自己在 UI 上把可選的
 *   alpha 限制在低值，這是產品/視覺決策，不是本型別的責任。
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
        require(blurRadiusDp in 0f..MAX_BLUR_RADIUS_DP) {
            "blurRadiusDp must be within 0f..$MAX_BLUR_RADIUS_DP, but was $blurRadiusDp"
        }
        require(opacity in 0f..1f) { "opacity must be within 0f..1f, but was $opacity" }
    }

    companion object {
        /** [blurRadiusDp] 上限，見上方 class KDoc。 */
        const val MAX_BLUR_RADIUS_DP: Float = 50f
    }
}
