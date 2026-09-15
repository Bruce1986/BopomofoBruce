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
 *   取得可持久化授權， 授權才會跨重開機存活。⚠️ **未查證**：本段原本斷言 `MediaStore.ACTION_PICK_IMAGES`（Android Photo Picker）發出的
 *   URI 不支援 `takePersistableUriPermission`，但 repo 內沒有任何出處；W2-C 實作選圖流程前要先對照官方文件確認，
 *   不要把它當成已驗證的平台行為。無論走哪條路，沒取得持久授權的 URI 存進 [uri]，都會在授權失效後讓相片背景悄悄消失 （唯一訊號是 [PhotoBackgroundLayer] 的一行
 *   `Log.w`，見該檔 KDoc）。
 *
 *   **只接受本機 scheme**（[LOCAL_URI_SCHEMES]）：[PhotoBackgroundLayer] 把 [uri] 原樣交給 Coil，而 Coil 2 對
 *   `http(s)` 字串會走網路載入。IME 看得到使用者輸入的所有文字，ADR-0005 承諾純本地；這個型別會被寫進設定、將來也可能從主題 JSON 反序列化，
 *   所以「相片背景永遠是本機資源」由 `init` 自己強制，不寄託在「沒有任何模組申請 `INTERNET` 權限」這個外部事實上。
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
        // 訊息只帶 scheme、不帶完整 uri：它指向使用者相簿裡的特定圖片，不該跟著例外進 log／錯誤報告。
        // 刻意分大小寫：Coil 2.6 的 ContentUriFetcher／ResourceUriFetcher 用精確字串比對小寫 scheme，
        // `CONTENT://…` 過了這裡也載不出來，只會變成一張悄悄消失的背景。
        val scheme = uri.substringBefore(':', missingDelimiterValue = "")
        require(scheme in LOCAL_URI_SCHEMES) {
            "PhotoBackground uri must use a local scheme $LOCAL_URI_SCHEMES, but was '$scheme'"
        }
        require(blurRadiusDp in 0f..MAX_BLUR_RADIUS_DP) {
            "blurRadiusDp must be within 0f..$MAX_BLUR_RADIUS_DP, but was $blurRadiusDp"
        }
        require(opacity in 0f..1f) { "opacity must be within 0f..1f, but was $opacity" }
    }

    companion object {
        /** [blurRadiusDp] 上限，見上方 class KDoc。 */
        const val MAX_BLUR_RADIUS_DP: Float = 50f

        /**
         * [uri] 允許的 scheme（精確比對、只收小寫，與 Coil 一致）。見上方 class KDoc 的 [uri] 條目：`http`／`https` 等網路 scheme
         * 在 `init` 就被拒絕。
         *
         * **刻意不收 `file`**：`file://` 沒有 ContentResolver／資源系統那層存取仲介，等於「IME 行程讀得到的任何路徑」，
         * 不是「使用者選的那張圖」。若 W2-C 決定把選到的圖複製進 app 自己的儲存區再用 `file://` 參照，要連同路徑範圍限制 （例如限定在 `filesDir`
         * 底下）一起加回來。
         */
        val LOCAL_URI_SCHEMES: Set<String> = setOf("content", "android.resource")
    }
}
