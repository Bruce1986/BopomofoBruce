package com.bopomofobruce.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.bopomofobruce.common.KeyboardColors
import com.bopomofobruce.common.KeyboardDimens
import com.bopomofobruce.common.KeyboardTheme
import com.bopomofobruce.theme.color.toKeyboardUInt
import com.bopomofobruce.theme.style.StyleSheet

/**
 * Material You 動態色主題。Android 12+（API 31，`Build.VERSION_CODES.S`）讀系統桌布色；<31 沒有
 * `dynamicLightColorScheme` / `dynamicDarkColorScheme` 可用，退化為固定的 [LightTheme] / [DarkTheme] 色盤 （id
 * 仍回報 [ID]，讓呼叫端知道「使用者選的是 Material You」，即使實際顏色是退化值）。
 *
 * [sdkInt] 開一個建構參數而非直接讀 `Build.VERSION.SDK_INT`，是為了讓「<31 退化」這條分支可以在純 JVM unit test 下驗證，不需要
 * Robolectric（本專案目前沒有引入）。>=31 分支呼叫真正的 `dynamicLightColorScheme(Context)` 需要 Android runtime
 * 提供的系統資源，只能在 connectedAndroidTest / 實機驗證，這裡沒有量測。
 */
class MaterialYouTheme private constructor(override val id: String, val styleSheet: StyleSheet) :
    KeyboardTheme {

    override val colors: KeyboardColors
        get() = styleSheet.colors

    override val dimens: KeyboardDimens
        get() = styleSheet.dimens

    companion object {
        const val ID: String = "material-you"

        /** 正式呼叫端用這支：SDK 等級一律取自實際裝置，無法被覆寫。 */
        fun from(context: Context, darkMode: Boolean): MaterialYouTheme =
            from(context, darkMode, Build.VERSION.SDK_INT)

        /**
         * 測試用的 SDK 注入版本。**不要在正式程式碼呼叫**——傳入與裝置實際 API 等級不符的 [sdkInt] 會讓 `< 31` 的裝置走進
         * `@RequiresApi(S)` 的動態取色路徑而在執行期崩潰。
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        fun from(context: Context, darkMode: Boolean, sdkInt: Int): MaterialYouTheme {
            val fallback = if (darkMode) DarkTheme.styleSheet else LightTheme.styleSheet
            // Lint 的 NewApi 資料流分析只認得對 `Build.VERSION.SDK_INT` 的直接比較；這裡刻意透過
            // `sdkInt` 參數（正式路徑由上面的兩參數版本填入 `Build.VERSION.SDK_INT`）注入，讓 <31 退化分支能在純 JVM unit
            // test 驗證（見 class KDoc）。實際執行路徑與直接寫 `Build.VERSION.SDK_INT >= S` 等價，
            // 手動抑制這條誤報。
            @Suppress("NewApi")
            val colors =
                if (sdkInt >= Build.VERSION_CODES.S) {
                    dynamicColorsFor(context, darkMode)
                } else {
                    fallback.colors
                }
            return MaterialYouTheme(id = ID, styleSheet = fallback.copy(id = ID, colors = colors))
        }

        @RequiresApi(Build.VERSION_CODES.S)
        private fun dynamicColorsFor(context: Context, darkMode: Boolean): KeyboardColors {
            val scheme =
                if (darkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            return KeyboardColors(
                background = scheme.surface.toKeyboardUInt(),
                keyFill = scheme.surfaceVariant.toKeyboardUInt(),
                keyText = scheme.onSurface.toKeyboardUInt(),
                keyAccent = scheme.primary.toKeyboardUInt(),
                candidateText = scheme.onSurface.toKeyboardUInt(),
                candidateHighlight = scheme.primaryContainer.toKeyboardUInt(),
            )
        }
    }
}
