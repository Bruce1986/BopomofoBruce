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
import com.bopomofobruce.theme.color.pickAccentColor
import com.bopomofobruce.theme.color.toKeyboardUInt
import com.bopomofobruce.theme.style.StyleSheet
import kotlin.ConsistentCopyVisibility

/**
 * Material You 動態色主題。Android 12+（API 31，`Build.VERSION_CODES.S`）讀系統桌布色；<31 沒有
 * `dynamicLightColorScheme` / `dynamicDarkColorScheme` 可用，退化為固定的 [LightTheme] / [DarkTheme] 色盤 （id
 * 仍回報 [ID]，讓呼叫端知道「使用者選的是 Material You」，即使實際顏色是退化值）。
 *
 * [sdkInt] 開一個建構參數而非直接讀 `Build.VERSION.SDK_INT`，是為了讓「<31 退化」這條分支可以在純 JVM unit test 下驗證，不需要
 * Robolectric（本專案目前沒有引入）。>=31 分支呼叫真正的 `dynamicLightColorScheme(Context)` 需要 Android runtime
 * 提供的系統資源，只能在 connectedAndroidTest / 實機驗證，這裡沒有量測。
 *
 * `data class`：equals/hashCode 以 [id] + [styleSheet] 為準，讓相同輸入兩次呼叫 [from] 得到相等的實例 （[styleSheet] 本身已是
 * data class，逐欄位比較）。這只解決值語意，**不會**讓用到 [MaterialYouTheme] 的 composable 自動被 Compose 跳過重組——2.0.20+ 的
 * strong skipping 對 unstable 型別是用 `===` 比較， 要真的可 skip 必須在 `:common` 的 [KeyboardTheme] interface 標
 * `@Stable`/`@Immutable`，那屬於 contracts-v1（凍結中），本模組不能動，已記在 devlog 當 W2 follow-up。
 */
@ConsistentCopyVisibility
data class MaterialYouTheme
private constructor(override val id: String, val styleSheet: StyleSheet) : KeyboardTheme {

    override val colors: KeyboardColors
        get() = styleSheet.colors

    override val dimens: KeyboardDimens
        get() = styleSheet.dimens

    companion object {
        const val ID: String = "material-you"

        /** 正式呼叫端用這支：SDK 等級一律取自實際裝置，無法被覆寫。 */
        /**
         * 正式路徑讀取執行裝置 API 等級的**唯一**位置。抽成可替換的 provider 是為了讓下面那個 兩參數版本（真正的正式入口）能在純 JVM 被測到。
         *
         * 背景（2026-09-08 深審）：在此之前，兩參數版本直接寫 `from(context, darkMode,
         * Build.VERSION.SDK_INT)`，而**唯二呼叫它的地方是兩個從未實際 render 過的 `@Preview`**。突變實測：把它改成寫死
         * `from(context, darkMode, 30)`——也就是 Material You 在任何裝置上永遠不會啟用、本模組的招牌功能靜默失效——**52 條測試全綠**。
         * JVM 單元測試改不動 `Build.VERSION.SDK_INT`（JDK 21 已封死 final 欄位的反射改寫）， 所以改用這個縫。
         *
         * **未覆蓋的部分縮到只剩 `{ Build.VERSION.SDK_INT }` 這個單一運算式**（無法在 JVM 驗證）， 而「兩參數版本會依 provider
         * 的值分流」現在有守門（見 `MaterialYouThemeTest`）。
         *
         * 正式程式碼**不得寫入**這個變數——它只有測試會換掉，且測試必須在 `@AfterEach` 還原。
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal var sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }

        fun from(context: Context, darkMode: Boolean): MaterialYouTheme =
            from(context, darkMode, sdkIntProvider())

        /**
         * 測試用的 SDK 注入版本。**不要在正式程式碼呼叫**——傳入與裝置實際 API 等級不符的 [sdkInt] 會讓 `< 31` 的裝置走進
         * `@RequiresApi(S)` 的動態取色路徑而在執行期崩潰。
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal fun from(context: Context, darkMode: Boolean, sdkInt: Int): MaterialYouTheme {
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
            val background = scheme.surface.toKeyboardUInt()
            val keyFill = scheme.surfaceVariant.toKeyboardUInt()
            val keyText = scheme.onSurface.toKeyboardUInt()

            // G3（codex 獨立審查 + 第八輪 Opus tracer 的推理）：B22 把 keyAccent 寫死映到
            // scheme.primaryContainer、candidateHighlight 寫死映到 scheme.secondaryContainer，跟
            // BuiltInThemes 修掉的缺陷同源——M3 的 *Container 與 surface 是固定 tone 目標（light 下
            // surface≈98/container≈90，dark 下 10/30），桌布只換色相與彩度、不換 tone，所以這個映射
            // 在任何桌布下都只有約 1–2:1 的自身分離度。改成用 pickAccentColor 從多個候選角色中選：
            // 保留文字達 AA 4.5 的候選，其中挑與 keyFill/background（keyAccent）或 background
            // （candidateHighlight）分離度最大的一個。這個選色函式本身是純數學，已用假造的色彩組合在
            // JVM 覆蓋（見 AccentColorSelectionTest，含「container 與 surface 同 tone」的極端情境）。
            // 注意：dynamicDarkColorScheme/dynamicLightColorScheme 呼叫本身仍需要系統資源，本檔仍然
            // 沒有 Robolectric、也沒有實機驗證，只有「給定一組桌布色彩，選色函式會不會選對」是有守門的
            // ——實際桌布數字未經實機驗證（見本檔 class KDoc 與 devlog 的誠實揭露）。
            val accentCandidates =
                listOf(
                        scheme.primaryContainer,
                        scheme.tertiaryContainer,
                        scheme.secondaryContainer,
                        scheme.primary,
                        scheme.tertiary,
                        scheme.secondary,
                        scheme.inversePrimary,
                        scheme.onSurfaceVariant,
                    )
                    .map { it.toKeyboardUInt() }
            val highlightCandidates =
                listOf(
                        scheme.secondaryContainer,
                        scheme.tertiaryContainer,
                        scheme.primaryContainer,
                        scheme.secondary,
                        scheme.tertiary,
                        scheme.primary,
                        scheme.inversePrimary,
                        scheme.onSurfaceVariant,
                    )
                    .map { it.toKeyboardUInt() }

            val keyAccent =
                pickAccentColor(
                    candidates = accentCandidates,
                    textPartner = keyText,
                    separationReferences = listOf(keyFill, background),
                )

            // H1（第十一輪審查）：accentCandidates 與 highlightCandidates 成員相同（只是排序不同），
            // 用貼近真實 M3 baseline 的 tone 分布實測，兩次呼叫最後都只剩 inversePrimary 同時通過
            // 文字門檻且分離度最好，於是 keyAccent 與 candidateHighlight 撞成同一個顏色——功能鍵按下
            // 的底色跟候選列選中游標的底色分不出來，正是 B22 註解要避免、繞一圈又回來的缺陷。
            //
            // I1（第十二輪審查）：H1 當時用 excluded = setOf(keyAccent) 硬性把 keyAccent 選中的顏色
            // 從候選清單移除、再重新評分，結果**先過濾、再評分**這個順序本身會讓分離度倒退：用本檔
            // KDoc 假設的 M3 baseline tone 分布實算，light 下 keyAccent 選中 inversePrimary（對
            // background 1.66:1），排除後 candidateHighlight 落到 tertiaryContainer（對 background
            // 只剩 1.26:1，比排除前更差）；dark 下落到 secondaryContainer（1.84:1，正是 B22 註解裡
            // `BuiltInThemes.kt` 記載「只有 1.84:1，不及 3:1」的同一個色值/數字，被動態路徑撿回來）。
            // 根因：excluded 是硬性排除，不管排除之後剩下的候選有多差都不會回頭；而三個 container
            // 候選彼此 tone 相近，只要 keyAccent 選中 inversePrimary，第二次呼叫幾乎必然落進這個分離
            // 度差的 container 群，不是罕見路徑。
            //
            // 已改：不再用 excluded 硬性過濾，改成把 keyAccent 併入 separationReferences——讓「與
            // keyAccent 分得開」變成跟「與 background 分得開」同級的評分項，而不是二元的
            // 排除／不排除。這同時解掉「excluded 只比對完全相同的 UInt，視覺上幾乎相同但不是同一個
            // UInt 的顏色排不掉」的問題（見 pickAccentColor 的 KDoc）。若排除後只剩 keyAccent
            // 自己一個候選通過文字門檻（見 DynamicAccentSelection.kt 的 AccentColorSelectionTest
            // 案例），candidateHighlight 會等於 keyAccent（撞色），但這是候選清單本身別無選擇下最好
            // 的結果，不是本函式為了避開撞色硬推去一個分離度更差的顏色——已用
            // `keyAccent excluded from candidates causes a worse pick than scoring against it as a
            // separation reference` 測試證明：舊的 excluded 呼叫方式在這個候選集合下會選到分離度
            // 明顯更差的顏色，新的呼叫方式不會。
            val candidateHighlight =
                pickAccentColor(
                    candidates = highlightCandidates,
                    textPartner = keyText,
                    separationReferences = listOf(background, keyAccent),
                )

            return KeyboardColors(
                background = background,
                keyFill = keyFill,
                keyText = keyText,
                keyAccent = keyAccent,
                candidateText = keyText,
                candidateHighlight = candidateHighlight,
            )
        }
    }
}
