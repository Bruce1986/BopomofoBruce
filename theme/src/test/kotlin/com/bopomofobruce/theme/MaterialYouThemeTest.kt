package com.bopomofobruce.theme

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.bopomofobruce.theme.color.toKeyboardUInt
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * `sdkInt < 31` 的退化分支驗到色盤內容（純 Kotlin 邏輯，不需要 Android runtime）。`>= 31` 分支有兩層：「正式入口會依 provider
 * 分流」（relaxed mock，stub 色盤），以及「角色映射與不撞色」（依 resource id 餵一組色階 fixture，見 `the dynamic path maps
 * scheme roles and keeps keyAccent and candidateHighlight apart`）。**真實桌布會產生什麼色值、對比度是否達標，仍未覆蓋**（這裡沒有
 * Robolectric、也沒有實機）— 見 devlog 與 [MaterialYouTheme] 上的 KDoc 說明。
 */
class MaterialYouThemeTest {

    // <31 的分支不會呼叫 context 上任何方法；>=31 的分支經由 dynamicLightColorScheme 去讀 context，
    // relaxed mock 讓它回 stub 值（全 0）而不是丟例外。要餵真正的色階用 contextWithSystemPalettes()。
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
     * `>= 31` 路徑的**呼叫處**守門：角色映射，以及 `keyAccent`／`candidateHighlight` 不撞色。
     *
     * 在此之前這條路徑只有「與退化值不同」一條斷言；relaxed mock 下所有系統色都是 0，所以 （2026-09-15 突變實測，68 條全綠）把
     * `candidateHighlight` 的 `separationReferences` 退回 `listOf(background)`（還原 I1 修正）、或直接寫
     * `candidateHighlight = keyAccent` 都零反應。
     *
     * 做法：material3 1.3.x 的 `dynamicLightColorScheme` 在 `SDK_INT < 34` 時走
     * `dynamicTonalPalette(context)`， 經 `ColorResourceHelper` 讀
     * `context.resources.getColor(android.R.color.system_*, theme)`（javap 確認）； JVM 單元測試裡 `SDK_INT`
     * 是 0，一定走這條。所以只要讓 mock 依 resource id 回色值，就能餵進一組色階。
     *
     * 期望值由 material3 自己的 `dynamic*ColorScheme` 對**同一個** mock 算出來當對照，不是抄本模組的映射邏輯。 色階
     * fixture（[TONAL_PALETTES]）只是「tone 由亮到暗單調、五組色相各不相同」的近似 M3 baseline 配色， **數值未對照官方表**，也不代表任何實機桌布。
     */
    @Test
    fun `the dynamic path maps scheme roles and keeps keyAccent and candidateHighlight apart`() {
        val palettedContext = contextWithSystemPalettes()
        val original = MaterialYouTheme.sdkIntProvider
        try {
            MaterialYouTheme.sdkIntProvider = { Build.VERSION_CODES.S }
            for (darkMode in listOf(false, true)) {
                val scheme =
                    if (darkMode) dynamicDarkColorScheme(palettedContext)
                    else dynamicLightColorScheme(palettedContext)
                val colors = MaterialYouTheme.from(palettedContext, darkMode).colors
                val mode = if (darkMode) "dark" else "light"

                assertEquals(scheme.surface.toKeyboardUInt(), colors.background, "$mode background")
                assertEquals(
                    scheme.surfaceVariant.toKeyboardUInt(),
                    colors.keyFill,
                    "$mode keyFill",
                )
                assertEquals(scheme.onSurface.toKeyboardUInt(), colors.keyText, "$mode keyText")
                assertEquals(
                    scheme.onSurface.toKeyboardUInt(),
                    colors.candidateText,
                    "$mode candidateText",
                )
                assertNotEquals(
                    colors.keyAccent,
                    colors.candidateHighlight,
                    "$mode：candidateHighlight 與 keyAccent 撞成同一色（H1）——功能鍵底色與選中候選分不出來",
                )
            }
        } finally {
            MaterialYouTheme.sdkIntProvider = original
        }
    }

    private fun contextWithSystemPalettes(): Context {
        val idToColor = HashMap<Int, Int>()
        for ((ids, tones) in SYSTEM_PALETTE_IDS.zip(TONAL_PALETTES)) {
            ids.zip(tones).forEach { (id, argb) -> idToColor[id] = argb.toInt() }
        }
        val resources = mockk<Resources>()
        every { resources.getColor(any<Int>(), any()) } answers
            {
                val id = firstArg<Int>()
                idToColor[id] ?: error("dynamicTonalPalette 讀了 fixture 沒提供的 resource id $id")
            }
        return mockk(relaxed = true) { every { this@mockk.resources } returns resources }
    }

    private companion object {
        /** 每組 13 個 shade，順序 0、10、50、100…1000（對應 tone 100、99、95、90…0）。 */
        val SYSTEM_PALETTE_IDS: List<List<Int>> =
            listOf(
                listOf(
                    android.R.color.system_accent1_0,
                    android.R.color.system_accent1_10,
                    android.R.color.system_accent1_50,
                    android.R.color.system_accent1_100,
                    android.R.color.system_accent1_200,
                    android.R.color.system_accent1_300,
                    android.R.color.system_accent1_400,
                    android.R.color.system_accent1_500,
                    android.R.color.system_accent1_600,
                    android.R.color.system_accent1_700,
                    android.R.color.system_accent1_800,
                    android.R.color.system_accent1_900,
                    android.R.color.system_accent1_1000,
                ),
                listOf(
                    android.R.color.system_accent2_0,
                    android.R.color.system_accent2_10,
                    android.R.color.system_accent2_50,
                    android.R.color.system_accent2_100,
                    android.R.color.system_accent2_200,
                    android.R.color.system_accent2_300,
                    android.R.color.system_accent2_400,
                    android.R.color.system_accent2_500,
                    android.R.color.system_accent2_600,
                    android.R.color.system_accent2_700,
                    android.R.color.system_accent2_800,
                    android.R.color.system_accent2_900,
                    android.R.color.system_accent2_1000,
                ),
                listOf(
                    android.R.color.system_accent3_0,
                    android.R.color.system_accent3_10,
                    android.R.color.system_accent3_50,
                    android.R.color.system_accent3_100,
                    android.R.color.system_accent3_200,
                    android.R.color.system_accent3_300,
                    android.R.color.system_accent3_400,
                    android.R.color.system_accent3_500,
                    android.R.color.system_accent3_600,
                    android.R.color.system_accent3_700,
                    android.R.color.system_accent3_800,
                    android.R.color.system_accent3_900,
                    android.R.color.system_accent3_1000,
                ),
                listOf(
                    android.R.color.system_neutral1_0,
                    android.R.color.system_neutral1_10,
                    android.R.color.system_neutral1_50,
                    android.R.color.system_neutral1_100,
                    android.R.color.system_neutral1_200,
                    android.R.color.system_neutral1_300,
                    android.R.color.system_neutral1_400,
                    android.R.color.system_neutral1_500,
                    android.R.color.system_neutral1_600,
                    android.R.color.system_neutral1_700,
                    android.R.color.system_neutral1_800,
                    android.R.color.system_neutral1_900,
                    android.R.color.system_neutral1_1000,
                ),
                listOf(
                    android.R.color.system_neutral2_0,
                    android.R.color.system_neutral2_10,
                    android.R.color.system_neutral2_50,
                    android.R.color.system_neutral2_100,
                    android.R.color.system_neutral2_200,
                    android.R.color.system_neutral2_300,
                    android.R.color.system_neutral2_400,
                    android.R.color.system_neutral2_500,
                    android.R.color.system_neutral2_600,
                    android.R.color.system_neutral2_700,
                    android.R.color.system_neutral2_800,
                    android.R.color.system_neutral2_900,
                    android.R.color.system_neutral2_1000,
                ),
            )

        /** 與 [SYSTEM_PALETTE_IDS] 一一對應：primary、secondary、tertiary、neutral、neutral variant。 */
        val TONAL_PALETTES: List<List<UInt>> =
            listOf(
                listOf(
                    0xFFFFFFFFu,
                    0xFFFFFBFEu,
                    0xFFF6EDFFu,
                    0xFFEADDFFu,
                    0xFFD0BCFFu,
                    0xFFB69DF8u,
                    0xFF9A82DBu,
                    0xFF7F67BEu,
                    0xFF6750A4u,
                    0xFF4F378Bu,
                    0xFF381E72u,
                    0xFF21005Du,
                    0xFF000000u,
                ),
                listOf(
                    0xFFFFFFFFu,
                    0xFFFFFBFEu,
                    0xFFF6EDFFu,
                    0xFFE8DEF8u,
                    0xFFCCC2DCu,
                    0xFFB0A7C0u,
                    0xFF958DA5u,
                    0xFF7A7289u,
                    0xFF625B71u,
                    0xFF4A4458u,
                    0xFF332D41u,
                    0xFF1D192Bu,
                    0xFF000000u,
                ),
                listOf(
                    0xFFFFFFFFu,
                    0xFFFFFBFAu,
                    0xFFFFECF1u,
                    0xFFFFD8E4u,
                    0xFFEFB8C8u,
                    0xFFD29DACu,
                    0xFFB58392u,
                    0xFF986977u,
                    0xFF7D5260u,
                    0xFF633B48u,
                    0xFF492532u,
                    0xFF31111Du,
                    0xFF000000u,
                ),
                listOf(
                    0xFFFFFFFFu,
                    0xFFFFFBFEu,
                    0xFFF4EFF4u,
                    0xFFE6E1E5u,
                    0xFFC9C5CAu,
                    0xFFAEAAAEu,
                    0xFF939094u,
                    0xFF787579u,
                    0xFF605D62u,
                    0xFF484649u,
                    0xFF313033u,
                    0xFF1C1B1Fu,
                    0xFF000000u,
                ),
                listOf(
                    0xFFFFFFFFu,
                    0xFFFFFBFEu,
                    0xFFF5EEFAu,
                    0xFFE7E0ECu,
                    0xFFCAC4D0u,
                    0xFFAEA9B4u,
                    0xFF938F99u,
                    0xFF79747Eu,
                    0xFF605D66u,
                    0xFF49454Fu,
                    0xFF322F37u,
                    0xFF1D1A22u,
                    0xFF000000u,
                ),
            )
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
