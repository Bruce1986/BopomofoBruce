package com.bopomofobruce.theme.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bopomofobruce.common.KeyboardTheme
import com.bopomofobruce.theme.DarkTheme
import com.bopomofobruce.theme.LightTheme
import com.bopomofobruce.theme.MaterialYouTheme
import com.bopomofobruce.theme.color.toComposeColor

/**
 * 給 review / 設計檢視用的簡化鍵盤預覽：一列假候選詞 + 一列假按鍵，套上傳入主題的顏色與尺寸。 不吃真的 `KeyboardDef`（那是 `:keyboards` 的責任），純視覺
 * sanity check。
 *
 * G2：候選列的契約語意是「`candidateHighlight` 標示目前 cursor 位置的那一個候選」，其餘候選字畫在 `background` 上——不是整條列都刷成
 * `candidateHighlight`。舊版整列鋪 `candidateHighlight` 會把 `background` 從候選列區域擠掉，讓我們鎖住的
 * highlight/background 分離度在唯一能用眼睛檢查的地方 原理上看不見。這裡改成只有第一個候選套 `candidateHighlight`，其餘留在 `background`
 * 上。
 *
 * 按鍵列另外加一顆 `keyAccent` 底色的功能鍵（模擬 ⌫ 這類 pressed/functional key）——`keyAccent` 在此之前從未被任何渲染程式碼引用過，owner
 * 被要求對一個自己看不到的顏色做視覺取捨（見 devlog G2）。這裡讓 keyAccent 對 keyFill、對 background，以及 keyText 疊在 keyAccent
 * 上，三組關係 一次入鏡。
 */
@Composable
private fun ThemeSwatch(theme: KeyboardTheme, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(theme.colors.background.toComposeColor()).padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(theme.dimens.candidateRowHeightDp.dp),
            horizontalArrangement = Arrangement.spacedBy(theme.dimens.keyGapDp.dp),
        ) {
            listOf("你", "妳", "擬").forEachIndexed { index, candidate ->
                Box(
                    modifier =
                        Modifier.background(
                            if (index == 0) {
                                theme.colors.candidateHighlight.toComposeColor()
                            } else {
                                Color.Transparent
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = candidate,
                        color = theme.colors.candidateText.toComposeColor(),
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(theme.dimens.rowGapDp.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(theme.dimens.keyGapDp.dp)) {
            listOf("ㄅ", "ㄆ", "ㄇ", "ㄈ").forEach { key ->
                Box(
                    modifier =
                        Modifier.height(theme.dimens.keyHeightDp.dp)
                            .width(theme.dimens.keyHeightDp.dp)
                            .background(theme.colors.keyFill.toComposeColor()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = key, color = theme.colors.keyText.toComposeColor())
                }
            }
            // G2：唯一引用 keyAccent 的渲染程式碼——模擬 ⌫ 這類 pressed/functional key。
            Box(
                modifier =
                    Modifier.height(theme.dimens.keyHeightDp.dp)
                        .width(theme.dimens.keyHeightDp.dp)
                        .background(theme.colors.keyAccent.toComposeColor()),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "⌫", color = theme.colors.keyText.toComposeColor())
            }
        }
    }
}

@Preview(name = "Light theme", showBackground = true)
@Composable
private fun LightThemePreview() {
    ThemeSwatch(theme = LightTheme)
}

@Preview(name = "Dark theme", showBackground = true)
@Composable
private fun DarkThemePreview() {
    ThemeSwatch(theme = DarkTheme)
}

/**
 * 用 `@Preview(apiLevel = 30)` 明確釘住 API 30（<31）host，走 [MaterialYouTheme] 「讀不到動態色時退化為 Light
 * 色盤」的路徑；`apiLevel` 不釘的話由 tooling 自行決定實際跑在哪個等級，不能保證這個預覽展示的是 退化路徑。實際桌布取色（>=31 分支）仍須實機驗證，見 devlog。
 */
@Preview(name = "Material You (fallback)", showBackground = true, apiLevel = 30)
@Composable
private fun MaterialYouThemeFallbackPreview() {
    val context = LocalContext.current
    val theme = remember { MaterialYouTheme.from(context, darkMode = false) }
    ThemeSwatch(theme = theme)
}

/**
 * 用 `@Preview(apiLevel = 35)` 釘住 >=31 的 host，走 [MaterialYouTheme]
 * 的動態取色分支（`dynamicLightColorScheme`）。
 *
 * **這條 preview 從未在 Android Studio 內實際開啟驗證過**，只確認過編譯通過。`dynamicLightColorScheme` 會去讀
 * `android.R.color.system_accentN_*`，那些值在實機上是由 SystemUI 的桌布取色服務在執行期寫入的； Layoutlib 沙盒是否模擬該流程依
 * Studio／layoutlib 版本而異。因此這條 preview 的結果可能是 「顏色與實機不同」，也可能是「直接 render 失敗」——兩種都沒有被排除。實際取色結果一律以實機為準。
 */
@Preview(name = "Material You (dynamic)", showBackground = true, apiLevel = 35)
@Composable
private fun MaterialYouThemeDynamicPreview() {
    val context = LocalContext.current
    val theme = remember { MaterialYouTheme.from(context, darkMode = false) }
    ThemeSwatch(theme = theme)
}
