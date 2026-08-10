package com.bopomofobruce.theme.photo

import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bopomofobruce.theme.color.toComposeColor

private const val TAG = "PhotoBackgroundLayer"

/**
 * 用 Coil 載入 [PhotoBackground.uri]，套上模糊、透明度、色調，鋪成鍵盤面板最底層背景。
 * - 模糊走 `Modifier.blur()`：這個 API 在 Android 12（API 31）以下是 **no-op**——Compose 沒有軟體
 *   fallback，`RenderEffect` 是硬體合成才有的機制。本模組 `minSdk = 28`，所以只在 `Build.VERSION.SDK_INT >=
 *   Build.VERSION_CODES.S`（API 31）時才套用 `.blur()`；`< 31` 一律跳過模糊， 只疊 `.alpha()` 與
 *   [PhotoBackground.tint]（見下）當降級效果——不假裝有模糊。
 * - [PhotoBackground.tint] 用 `ColorFilter.tint(..., BlendMode.SrcAtop)` 疊加，保留原圖亮度層次， 不是整片蓋純色。
 * - 圖片載入失敗（例如使用者曾選過的 `content://` URI 因來源 App 移除授權而失效）時， [AsyncImage] 的 `onError` 只記一行
 *   `Log.w`；不擋住底層主題色，讓鍵盤仍可用。
 *
 * 沒有在實機量測「開圖庫選圖 → 渲染」延遲（見 devlog）；DEVPLAN 訂的 `< 200 ms` 驗收項目尚未驗證。
 */
@Composable
fun PhotoBackgroundLayer(background: PhotoBackground, modifier: Modifier = Modifier) {
    val colorFilter =
        background.tint?.let { ColorFilter.tint(it.toComposeColor(), BlendMode.SrcAtop) }
    val blurModifier =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(background.blurRadiusDp.dp)
        } else {
            Modifier
        }
    AsyncImage(
        model = background.uri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = colorFilter,
        onError = {
            Log.w(TAG, "Failed to load photo background: ${background.uri}", it.result.throwable)
        },
        modifier = modifier.fillMaxSize().alpha(background.opacity).then(blurModifier),
    )
}
