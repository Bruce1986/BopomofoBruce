package com.bopomofobruce.theme

import com.bopomofobruce.common.KeyboardColors
import com.bopomofobruce.common.KeyboardDimens
import com.bopomofobruce.common.KeyboardTheme
import com.bopomofobruce.theme.style.StandardDimens
import com.bopomofobruce.theme.style.StyleSheet

/**
 * 固定淺色主題。色盤取自 Material 3 baseline light scheme 的近似值（沒有引入
 * `androidx.compose.material3.lightColorScheme()` 是因為 [KeyboardColors] 只有 6 個欄位， 直接手寫比疊一層轉換更直觀、也不用在
 * model 層拉進整個 Material ColorScheme 依賴）。
 */
object LightTheme : KeyboardTheme {
    override val id: String = "light"

    val styleSheet: StyleSheet =
        StyleSheet(
            id = id,
            colors =
                KeyboardColors(
                    background = 0xFFF3EDF7u,
                    keyFill = 0xFFFFFFFFu,
                    keyText = 0xFF1C1B1Fu,
                    keyAccent = 0xFF6750A4u,
                    candidateText = 0xFF1C1B1Fu,
                    candidateHighlight = 0xFFE8DEF8u,
                ),
            dimens = StandardDimens.default,
        )

    override val colors: KeyboardColors
        get() = styleSheet.colors

    override val dimens: KeyboardDimens
        get() = styleSheet.dimens
}

/** 固定深色主題。色盤取自 Material 3 baseline dark scheme 的近似值，理由同 [LightTheme]。 */
object DarkTheme : KeyboardTheme {
    override val id: String = "dark"

    val styleSheet: StyleSheet =
        StyleSheet(
            id = id,
            colors =
                KeyboardColors(
                    background = 0xFF1C1B1Fu,
                    keyFill = 0xFF2B2930u,
                    keyText = 0xFFE6E1E5u,
                    keyAccent = 0xFFD0BCFFu,
                    candidateText = 0xFFE6E1E5u,
                    candidateHighlight = 0xFF4A4458u,
                ),
            dimens = StandardDimens.default,
        )

    override val colors: KeyboardColors
        get() = styleSheet.colors

    override val dimens: KeyboardDimens
        get() = styleSheet.dimens
}
