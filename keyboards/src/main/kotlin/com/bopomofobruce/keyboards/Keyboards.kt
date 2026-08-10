package com.bopomofobruce.keyboards

import com.bopomofobruce.common.KeyboardDef

/**
 * Catalog of all [KeyboardDef]s this module ships. `:ime` should reach layouts through here rather
 * than calling [KeyboardLoader] with a hand-typed resource path — one fewer place for a typo'd
 * filename to go unnoticed until runtime.
 *
 * Each property is `by lazy`: [KeyboardDef.rows] is documented (see `:common`) as "stable and cheap
 * once read", so we parse the backing JSON once per process and reuse the same instance across
 * Compose recompositions.
 *
 * Explicitly **not** here: emoji / kaomoji keyboards (W2-D) and any UI rendering (`:ime`'s job) —
 * see the W1-C work-package scope in `docs/DEVPLAN-SubagentFanout-20260620-0851.md`.
 */
object Keyboards {
    /** 注音 4×10，直向。大千式配列，見 [KeyboardLoader] 所讀 JSON 內的來源附註。 */
    val zhuyin4x10Portrait: KeyboardDef by lazy {
        KeyboardLoader.loadFromResource("keyboards/zhuyin_4x10_portrait.json")
    }

    /** 注音 4×10，橫向。核心 40 鍵與直向相同，另加一列數字快捷鍵（橫向多出的水平空間）。 */
    val zhuyin4x10Landscape: KeyboardDef by lazy {
        KeyboardLoader.loadFromResource("keyboards/zhuyin_4x10_landscape.json")
    }

    /** 標準符號鍵盤（含全形標點 ，。、；：「」『』）。目前僅一頁；多頁／半形切換留待後續。 */
    val symbolStandard: KeyboardDef by lazy {
        KeyboardLoader.loadFromResource("keyboards/symbol_standard.json")
    }

    /** 純數字（計算機式 3 欄）。 */
    val numericStandard: KeyboardDef by lazy {
        KeyboardLoader.loadFromResource("keyboards/numeric_standard.json")
    }

    /**
     * 密碼輸入。結構上等同一般英文 QWERTY——遮罩顯示與停用建議是 `:ime` 依 InputType 決定的行為，不屬於 [KeyboardDef] 這層模型。
     *
     * **各列 weight 總和不對齊，這是刻意的、留給 renderer 處理**：字母三列（q-p / a-l / z-m）加總分別是 10 / 9 / 9.6，第四列（控制列）另外是
     * 7.6。[KeyData] 目前的模型只有「同 row 內互相比例」的語意（見 [com.bopomofobruce.common.KeyData] KDoc），沒有
     * spacer/gap 這種「跨 row 對齊留白」的概念——硬塞一個 假 [com.bopomofobruce.common.KeyAction.Custom] spacer 鍵會讓
     * `:ime` 有機會把它誤 render 成一顆可按 的鍵，比「列與列鍵柱沒對齊」更糟。因此各列**左邊界對齊、右邊界依各列 weight 總和自然收尾**，跨列的水平
     * 留白（讓鍵柱視覺對齊）留給 `:ime`（W2-B）渲染時自行處理，例如用容器 padding 或依最大列寬正規化，而不是 在 `:common`/`:keyboards`
     * 這層資料模型硬湊。`url_qwerty` 也是同樣狀況（10 / 9 / 9.6 / 8.8）。
     */
    val passwordQwerty: KeyboardDef by lazy {
        KeyboardLoader.loadFromResource("keyboards/password_qwerty.json")
    }

    /** 電話撥號鍵盤。 */
    val phoneDialpad: KeyboardDef by lazy {
        KeyboardLoader.loadFromResource("keyboards/phone_dialpad.json")
    }

    /**
     * URL 輸入。額外的 `.com` 鍵透過 [com.bopomofobruce.common.KeyAction.Custom] 表達（id
     * `"url_insert_dot_com"`），`:ime` 端需對應實作，否則會落入未知 custom id 的預設處理。
     *
     * 各列 weight 總和同樣不對齊（10 / 9 / 9.6 / 8.8）——理由與跨列對齊留給 renderer 的決定，見 [passwordQwerty] KDoc。
     */
    val urlQwerty: KeyboardDef by lazy {
        KeyboardLoader.loadFromResource("keyboards/url_qwerty.json")
    }

    /** 日期／時間輸入：數字 + 常用分隔符（`/` `:` `-`）。 */
    val datetimeStandard: KeyboardDef by lazy {
        KeyboardLoader.loadFromResource("keyboards/datetime_standard.json")
    }

    /** 所有內建鍵盤，供批次驗證（round-trip / schema test）與 `:ime` 端列舉使用。 */
    val all: List<KeyboardDef> by lazy {
        listOf(
            zhuyin4x10Portrait,
            zhuyin4x10Landscape,
            symbolStandard,
            numericStandard,
            passwordQwerty,
            phoneDialpad,
            urlQwerty,
            datetimeStandard,
        )
    }
}
