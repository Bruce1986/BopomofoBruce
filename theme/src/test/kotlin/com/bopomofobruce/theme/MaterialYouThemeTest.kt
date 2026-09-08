package com.bopomofobruce.theme

import android.content.Context
import android.os.Build
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * 只驗證 `sdkInt < 31` 的退化分支（純 Kotlin 邏輯，不需要 Android runtime）。`>= 31` 分支呼叫真正的
 * `dynamicLightColorScheme(Context)` / `dynamicDarkColorScheme(Context)`，需要系統資源，這裡沒有 Robolectric
 * 可用，未覆蓋 — 見 devlog 與 [MaterialYouTheme] 上的 KDoc 說明。
 */
class MaterialYouThemeTest {

    // 分支不會真的呼叫 context 上任何方法，relaxed mock 只是滿足型別簽章。
    private val context: Context = mockk(relaxed = true)

    @Test
    fun `id is always material-you regardless of fallback`() {
        val lightFallback = MaterialYouTheme.from(context, darkMode = false, sdkInt = 30)
        val darkFallback = MaterialYouTheme.from(context, darkMode = true, sdkInt = 30)

        assertEquals(MaterialYouTheme.ID, lightFallback.id)
        assertEquals(MaterialYouTheme.ID, darkFallback.id)
    }

    @Test
    fun `below API 31 falls back to LightTheme colors for light mode`() {
        val theme =
            MaterialYouTheme.from(context, darkMode = false, sdkInt = Build.VERSION_CODES.S - 1)

        assertEquals(LightTheme.colors, theme.colors)
        assertEquals(LightTheme.dimens, theme.dimens)
    }

    @Test
    fun `below API 31 falls back to DarkTheme colors for dark mode`() {
        val theme =
            MaterialYouTheme.from(context, darkMode = true, sdkInt = Build.VERSION_CODES.S - 1)

        assertEquals(DarkTheme.colors, theme.colors)
        assertEquals(DarkTheme.dimens, theme.dimens)
    }

    @Test
    fun `two calls with the same inputs produce equal instances`() {
        val first =
            MaterialYouTheme.from(context, darkMode = false, sdkInt = Build.VERSION_CODES.S - 1)
        val second =
            MaterialYouTheme.from(context, darkMode = false, sdkInt = Build.VERSION_CODES.S - 1)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    /**
     * **正式入口** `from(context, darkMode)`（兩參數版）真的會依 [MaterialYouTheme.sdkIntProvider]
     * 的值分流——這是本模組招牌功能的開關，在此之前**零測試覆蓋**。
     *
     * 突變實證（2026-09-08 深審）：把兩參數版改成寫死 `from(context, darkMode, 30)`，也就是 Material You
     * 在任何裝置上永遠不會啟用，**當時 52 條測試全綠**。唯二呼叫它的地方是兩個 從未實際 render 過的 `@Preview`，所以沒有任何東西會發現。
     *
     * 這條**不斷言 `>= 31` 那條路算出什麼**（在純 JVM 下它拿到的是 mockk stub 的預設值，沒有 意義），只斷言「換一個 provider
     * 值，結果就不同」——那正好是「開關有沒有接上」這件事， 而且不依賴 stub 的具體行為。
     */
    @Test
    fun `the production entry point actually routes on the sdk provider`() {
        val original = MaterialYouTheme.sdkIntProvider
        try {
            var asked = 0

            MaterialYouTheme.sdkIntProvider = {
                asked++
                Build.VERSION_CODES.S - 1
            }
            val below = MaterialYouTheme.from(context, darkMode = false)

            MaterialYouTheme.sdkIntProvider = {
                asked++
                Build.VERSION_CODES.S
            }
            val atOrAbove = MaterialYouTheme.from(context, darkMode = false)

            assertEquals(2, asked, "兩參數版沒有去問 sdkIntProvider——它可能寫死了某個值")
            assertEquals(
                LightTheme.colors,
                below.colors,
                "provider 回 <31 時應該走退化路徑、直接重用 LightTheme 的色盤",
            )
            assertNotEquals(
                below.colors,
                atOrAbove.colors,
                "provider 回 >=31 與 <31 得到同一組色盤——分流沒有生效（寫死 sdkInt 就會這樣）",
            )
        } finally {
            MaterialYouTheme.sdkIntProvider = original
        }
    }

    /**
     * 預設的 provider 必須是「問這台裝置」，不是某個寫死的值。
     *
     * **這條的鑑別力很窄，寫清楚以免被高估**：它比對的是 `Build.VERSION.SDK_INT` 與一個「本來就 是 `{ Build.VERSION.SDK_INT }`」的
     * provider——正常情況下兩邊是同一個運算式。它唯一抓得到的 是「有人把預設 provider 換成字面值或別的運算式」（突變實測：換成 `{ 30 }` → 本條紅，因為 JVM
     * 單元測試裡 `Build.VERSION.SDK_INT` 是 0）。它**抓不到**、也不該被期待抓到「`SDK_INT` 本身回報錯誤」——那不是這一層能驗的事。
     */
    @Test
    fun `the default sdk provider reports the running platform level`() {
        assertEquals(
            Build.VERSION.SDK_INT,
            MaterialYouTheme.sdkIntProvider(),
            "預設 provider 沒有回報執行環境的 API 等級",
        )
    }
}
