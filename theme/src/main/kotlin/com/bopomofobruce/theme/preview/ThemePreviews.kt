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
 */
@Composable
private fun ThemeSwatch(theme: KeyboardTheme, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(theme.colors.background.toComposeColor()).padding(8.dp)) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(theme.dimens.candidateRowHeightDp.dp)
                    .background(theme.colors.candidateHighlight.toComposeColor()),
            horizontalArrangement = Arrangement.spacedBy(theme.dimens.keyGapDp.dp),
        ) {
            listOf("你", "妳", "擬").forEach { candidate ->
                Text(
                    text = candidate,
                    color = theme.colors.candidateText.toComposeColor(),
                    modifier = Modifier.padding(4.dp),
                )
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
 * Preview tooling 環境跑在 API < 31 的 host 上，所以這個預覽實際展示的是 [MaterialYouTheme] 的 「讀不到動態色時退化為 Light
 * 色盤」路徑，不是真正的桌布取色（那要實機驗證，見 devlog）。
 */
@Preview(name = "Material You theme (fallback path)", showBackground = true)
@Composable
private fun MaterialYouThemePreview() {
    val context = LocalContext.current
    val theme = remember { MaterialYouTheme.from(context, darkMode = false) }
    ThemeSwatch(theme = theme)
}
