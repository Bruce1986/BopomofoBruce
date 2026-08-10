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
                    candidateText = 0xFFE6E1E5u,
                    // B22：原色 #4A4458 直接搬 M3 secondaryContainer dark，跟 background (#1C1B1F)
                    // 都是深色調、只有 1.84:1，不及 WCAG 1.4.11 非文字 UI 元件 3:1 門檻。
                    //
                    // **取捨（deliberate，非疏漏）**：深色主題下兩個門檻數學上無法同時滿足——
                    // background 近黑（相對亮度 0.0113）、candidateText 近白（0.7633），要對
                    // background 達 3:1 需要亮度 >= 0.1339，要讓白字同時達 AA 4.5:1 需要亮度
                    // <= 0.1307，可行區間是空的。根因是 KeyboardColors 契約沒有「高亮候選專用的
                    // 文字色」（M3 的 onSecondaryContainer），contracts-v1 已凍結，已登記為 W2
                    // 契約 follow-up。
                    //
                    // 兩個候選值：#6F6A76（背景 3.26、白字 4.07）與 #656471（背景 2.95、白字 4.50）。
                    // **選 #656471，文字可讀性優先**：候選字看不清楚沒有替代方案，而「這一個被選中」
                    // 這件事 :ime 還能用邊框／底線／字重表達，不必單靠底色分離度。若 owner 認為
                    // 應反過來以 1.4.11 為硬門檻，改回 #6F6A76 即可（同時要把
                    // BuiltInThemesContrastTest 的兩條 Dark 門檻對調回來）。
                    candidateHighlight = 0xFF656471u,
                ),
            dimens = StandardDimens.default,
        )

    override val colors: KeyboardColors
        get() = styleSheet.colors

    override val dimens: KeyboardDimens
        get() = styleSheet.dimens
}
