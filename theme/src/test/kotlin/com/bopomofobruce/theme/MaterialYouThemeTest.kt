package com.bopomofobruce.theme

import android.content.Context
import android.os.Build
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
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
}
