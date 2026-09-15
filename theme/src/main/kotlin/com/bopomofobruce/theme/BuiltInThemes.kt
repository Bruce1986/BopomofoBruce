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
                    // B22：M3 primary (#6750A4) 是設計來配 onPrimary（白字）用的，跟 keyText
                    // (#1C1B1F，深色) 疊在一起只有 2.66:1，遠不及 WCAG AA 文字 4.5:1 門檻——功能鍵按下時
                    // 字幾乎看不見。
                    //
                    // B24：第一版改用 M3 primaryContainer (#EADDFF) 讓文字達 13.28:1，卻毀掉強調色
                    // **自己**的邊界——對 keyFill 只剩 1.29:1、對 background 1.12:1（原 primary 是
                    // 6.44 / 5.60），等於按下去看不出按鍵有變色，而 keyAccent 的契約語意正是
                    // 「pressed state / 功能鍵」。淺色主題並不存在深色那種數學互斥：可行亮度區間
                    // L∈[0.226, 0.254] 非空。取中間調 #9179BE，三項同時達標：keyText 4.64、
                    // 對 keyFill 3.70、對 background 3.21（皆有守門，見 BuiltInThemesContrastTest）。
                    keyAccent = 0xFF9179BEu,
                    candidateText = 0xFF1C1B1Fu,
                    // B22：原色 #E8DEF8 直接搬 M3 secondaryContainer，跟 background (#F3EDF7)
                    // 幾乎同色階、只有 1.13:1，不及 WCAG 1.4.11 非文字 UI 元件 3:1 門檻——candidate
                    // 列上的游標高亮幾乎看不出邊界。改成灰紫色 #8A8196：對 background 3.23:1、
                    // candidateText 疊上去 4.62:1（兩者皆有實測，見 BuiltInThemesContrastTest）。
                    candidateHighlight = 0xFF8A8196u,
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
                    // B22：M3 primary dark (#D0BCFF) 是設計來配 onPrimary（深字）用的，跟 keyText
                    // (#E6E1E5，淺色) 疊在一起只有 1.32:1，遠不及 WCAG AA 文字 4.5:1 門檻。
                    //
                    // B24：第一版改用 primaryContainer dark (#4F378B) 讓文字達 7.22:1，但把餘裕全
                    // 浪費掉——對 keyFill 只剩 1.54:1、對 background 1.84:1（原 primary 是 8.42 /
                    // 10.05），按下去看不出按鍵變色。深色主題與 candidateHighlight 同源地無法三全其美
                    // （文字 4.5 為下限時，對 keyFill 的分離度上限只有 2.47），所以**套用與
                    // candidateHighlight 相同的規則**：文字 4.5 當硬下限，在此前提下最大化與周邊的
                    // 分離度。#855196 實測：keyText 4.50、對 keyFill 2.47、對 background 2.95——
                    // 兩項仍不及 1.4.11 的 3:1，但已是此契約下的最佳值（根因同為 KeyboardColors
                    // 缺 on-accent 色，已登記 W2 契約 follow-up）。守門門檻依實測值設定。
                    keyAccent = 0xFF855196u,
                    // **純白，不是 M3 dark 的 onSurface #E6E1E5**（owner 裁決，2026-09-08）。
                    // 這一個 byte 的差別決定了下面那兩條 WCAG 門檻能不能同時成立，推導見
                    // candidateHighlight 的註解。代價是候選列文字比 M3 基準更「硬」一點；
                    // 非高亮候選（畫在 background 上）的對比因此從 13.27 升到 17.13，只變好。
                    candidateText = 0xFFFFFFFFu,
                    // B22：原色 #4A4458 直接搬 M3 secondaryContainer dark，跟 background (#1C1B1F)
                    // 都是深色調、只有 1.84:1，不及 WCAG 1.4.11 非文字 UI 元件 3:1 門檻。
                    //
                    // **這裡曾經被判定為「兩個門檻數學上無法同時滿足」，那個結論只在
                    // candidateText 維持 #E6E1E5 時成立**（2026-09-08 深審實測推翻）：
                    // background 近黑（相對亮度 0.0113），要對它達 3:1 需要亮度 >= 0.1339；
                    // 而 #E6E1E5 當文字時，要同時達 AA 4.5:1 的亮度上限是 0.1307——**只差
                    // 0.0032**，是險些擦身而過，不是根本不可能。把 candidateText 提到純白之後
                    // 上限放寬到 0.1833，可行區間 [0.1339, 0.1833] 非空，光是灰階就有 16 個解。
                    //
                    // 從那 16 個裡選 **#6B6B6B**（白字 5.33、對 background 3.21），而不是區間
                    // 端點附近的 #676767（3.03，餘裕僅 0.03）或 #767676（白字 4.54，餘裕僅 0.04）
                    // ——端點的餘裕薄到任何 RGB ±2 的美術微調都會弄紅 CI，正是舊值 #656471
                    // （對 background 2.9485、餘裕 0.0485）的老問題。#6B6B6B 兩邊都有餘裕。
                    //
                    // 於是兩條門檻**都是真的 3.0 / 4.5**，不再需要「取捨」這個說法，
                    // BuiltInThemesContrastTest 的 Dark 門檻也從 2.9 回到規範值 3.0。
                    // W2 的 `onSecondaryContainer`（高亮候選專用文字色）仍然值得加——那能讓
                    // 非高亮候選留在 M3 的 #E6E1E5、只有高亮那一顆用白字——但已不再是達標的前提。
                    candidateHighlight = 0xFF6B6B6Bu,
                ),
            dimens = StandardDimens.default,
        )

    override val colors: KeyboardColors
        get() = styleSheet.colors

    override val dimens: KeyboardDimens
        get() = styleSheet.dimens
}
