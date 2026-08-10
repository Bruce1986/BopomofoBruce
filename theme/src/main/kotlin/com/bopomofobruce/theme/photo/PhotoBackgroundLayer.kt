package com.bopomofobruce.theme.photo

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

/**
 * 用 Coil 載入 [PhotoBackground.uri]，套上模糊、透明度、色調，鋪成鍵盤面板最底層背景。
 * - 模糊走 `Modifier.blur()`：Compose 內部在 API 31+ 用 `RenderEffect` 硬體加速、<31 用軟體 fallback， 這層不需要自己分支。
 * - [PhotoBackground.tint] 用 `ColorFilter.tint(..., BlendMode.SrcAtop)` 疊加，保留原圖亮度層次， 不是整片蓋純色。
 *
 * 沒有在實機量測「開圖庫選圖 → 渲染」延遲（見 devlog）；DEVPLAN 訂的 `< 200 ms` 驗收項目尚未驗證。
 */
@Composable
fun PhotoBackgroundLayer(background: PhotoBackground, modifier: Modifier = Modifier) {
    val colorFilter =
        background.tint?.let { ColorFilter.tint(it.toComposeColor(), BlendMode.SrcAtop) }
    AsyncImage(
        model = background.uri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = colorFilter,
        modifier = modifier.fillMaxSize().alpha(background.opacity).blur(background.blurRadiusDp.dp),
    )
}
