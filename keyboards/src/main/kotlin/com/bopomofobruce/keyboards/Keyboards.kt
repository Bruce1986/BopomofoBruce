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
    /**
     * [com.bopomofobruce.common.KeyAction.Custom] id 集合，依「是否代表切頁（帶使用者跳去另一份
     * 鍵盤，而不是插入文字或其他行為）」分類（M2，round-13 tracer 審查）。
     *
     * 背景：`:keyboards` 對「終端頁」（見 [numericStandard] KDoc）與工具測試（[ToggleFreeKeyboardsTest]） 原本只看
     * [com.bopomofobruce.common.KeyAction.SymbolToggle] /
     * [com.bopomofobruce.common.KeyAction.LanguageToggle] 這兩個內建型別，但 J1（round-11）已經確立 `Custom`
     * 也可能是切頁鍵（`"switch_to_zhuyin"` 就是），這條規則沒有跟著更新——今天湊巧不出錯是因為 `symbolStandard` 另外還掛了
     * `language_toggle`，一旦出現「只用 `Custom` 切頁」的鍵盤就會被誤判成 終端頁。
     *
     * 這兩份清單合起來必須涵蓋 [Keyboards.all] 目前用到的每一個 `Custom` id—— [ToggleFreeKeyboardsTest] 對「出現一個兩邊都沒登記的
     * id」直接判定失敗，不會靜默放行成「不是切頁鍵」。
     */
    val PAGE_SWITCH_CUSTOM_IDS: Set<String> = setOf("switch_to_zhuyin", "switch_back")

    /** 不代表切頁、單純插入文字/其他行為的 `Custom` id（同上，供終端頁判定排除）。 */
    val NON_PAGE_SWITCH_CUSTOM_IDS: Set<String> = setOf("url_insert_dot_com")

    /** 注音 4×10，直向。大千式配列，見 [KeyboardLoader] 所讀 JSON 內的來源附註。 */
    val zhuyin4x10Portrait: KeyboardDef by lazy {
        KeyboardLoader.loadFromResource("keyboards/zhuyin_4x10_portrait.json")
    }

    /** 注音 4×10，橫向。核心 40 鍵與直向相同，另加一列數字快捷鍵（橫向多出的水平空間）。 */
    val zhuyin4x10Landscape: KeyboardDef by lazy {
        KeyboardLoader.loadFromResource("keyboards/zhuyin_4x10_landscape.json")
    }

    /**
     * 標準符號鍵盤（含全形標點 ，。、；：「」『』）。目前僅一頁；多頁／半形切換留待後續。
     *
     * **控制列三顆切換鍵**：
     * - 「ABC」掛 [com.bopomofobruce.common.KeyAction.LanguageToggle]——字面語意「切換到英文/數字鍵盤」，
     *   跟標籤一致。**落差**：`Keyboards.all` 目前沒有通用英文鍵盤可當它的目的地（不在 W1-C 交付範圍）， 已登記為 W2-B follow-up，不在本輪新增。
     * - 「注音」掛 [com.bopomofobruce.common.KeyAction.Custom]（id `"switch_to_zhuyin"`）——`KeyAction`
     *   sealed 型別裡沒有任何內建變體字面上代表「切回注音鍵盤」（`symbol_toggle`/`language_toggle` 只覆蓋 「去符號」「去英數」兩個方向），改用
     *   `Custom` 這個 escape hatch（見 [com.bopomofobruce.common.KeyAction] KDoc：「不需要為每個新功能擴 sealed
     *   子型別」），不動 `:common` 契約。`:ime` 端需對應實作。**固定**目的地是注音鍵盤，只適合「從注音鍵盤按
     *   [com.bopomofobruce.common.KeyAction.SymbolToggle] 進來」這條路徑。
     * - 「返回」掛 [com.bopomofobruce.common.KeyAction.Custom]（id `"switch_back"`，M1，round-13 tracer
     *   審查新增）——**通用**的「回到剛剛送我來這裡的那份鍵盤」，不綁定特定目的地。
     *
     * **為什麼「注音」不夠、需要另外一顆「返回」（M1 落地說明）**： [com.bopomofobruce.common.KeyAction.SymbolToggle]
     * 唯一的目的地就是這頁，但**來源**有四份鍵盤——
     * [zhuyin4x10Portrait]、[zhuyin4x10Landscape]、[passwordQwerty]、[urlQwerty]——而「注音」這顆鍵的目的地
     * 寫死是注音鍵盤，只服務前兩個來源。從 [passwordQwerty] / [urlQwerty] 按「全形」進來的使用者，原本兩個
     * 出口（「注音」把注音鍵盤丟進密碼欄位、「ABC」目的地不存在）都到不了自己原本在打的密碼/URL 鍵盤——
     * 資料層完全沒有「回到來源」的表達方式，這是死路，不是「半形符號頁還沒做」（那是另一條已登記的落差， 見下方 F1 段落）。「返回」補上這個通用逃生口：`:ime` 必須維護「使用者按下
     * `symbol_toggle` 之前正在哪份 鍵盤」這個來源狀態（至少一層，不需要完整堆疊——本模組沒有巢狀切頁），按下「返回」時切回那份鍵盤。
     * [ReturnPathCoverageTest] 釘住「[Keyboards.all] 裡每一顆切頁鍵的目的地都有路可回」這個性質，涵蓋這個 案例。
     *
     * `:ime` 目前需要實作的 `Custom` id 清單（供 W2-B 對照；另見 [PAGE_SWITCH_CUSTOM_IDS] /
     * [NON_PAGE_SWITCH_CUSTOM_IDS]）：
     * - `"url_insert_dot_com"`（見 [urlQwerty]）——不是切頁鍵，插入文字。
     * - `"switch_to_zhuyin"`（本鍵盤，切回 [zhuyin4x10Portrait] / [zhuyin4x10Landscape]，依當時 orientation
     *   擇一）——切頁鍵，固定目的地。
     * - `"switch_back"`（本鍵盤，M1 新增）——切頁鍵，目的地是 `:ime` 記住的來源鍵盤，不是固定值。
     */
    val symbolStandard: KeyboardDef by lazy {
        KeyboardLoader.loadFromResource("keyboards/symbol_standard.json")
    }

    /**
     * 純數字（計算機式 3 欄）。`.` 鍵的 longPress 掛 `-`（F2），供 `TYPE_NUMBER_FLAG_SIGNED`
     * 欄位（溫度、價差、偏移量）輸入負數；本頁沒有切頁鍵（終端頁）。本模組的終端頁為 [numericStandard]、[phoneDialpad]、[datetimeStandard]
     * 這三份（L1，round-11 審查——之前 `numericStandard` 與 `phoneDialpad` 的 KDoc 各自寫「是唯一終端頁」，兩個「唯一」互相矛盾，
     * 且漏了同樣不含切頁鍵的 `datetimeStandard`）。`:ime` 可直接從資料本身判定終端頁，不必依賴這份 KDoc：一份鍵盤是終端頁，若且唯若它的 rows 內所有
     * [com.bopomofobruce.common.KeyData.action]（含 `longPress`）都不是切頁鍵——**切頁鍵不只
     * [com.bopomofobruce.common.KeyAction.SymbolToggle] /
     * [com.bopomofobruce.common.KeyAction.LanguageToggle] 這兩個內建型別，也包含 id 落在
     * [PAGE_SWITCH_CUSTOM_IDS] 裡的 [com.bopomofobruce.common.KeyAction.Custom]**（M2，round-13 tracer
     * 審查——原本這句話漏了 `Custom` 也可能是切頁鍵，`symbolStandard` 的 `"switch_to_zhuyin"`/`"switch_back"`
     * 都是；當時湊巧沒出錯是因為 `symbolStandard` 另外掛了 `language_toggle`，一旦出現「只用 `Custom` 切頁」的鍵盤就會被這條不完備規則
     * 誤判成終端頁）。[ToggleFreeKeyboardsTest] 釘住「這三份且僅這三份」這個性質，且對任何未登記進 [PAGE_SWITCH_CUSTOM_IDS] /
     * [NON_PAGE_SWITCH_CUSTOM_IDS] 的 `Custom` id 直接判定失敗，不會靜默放行。
     */
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
     *
     * **F1 已知落差（內容，非回程）**：控制列的切頁鍵標籤是「全形」（原本誤標「123」，一度改為「符號」，`symbol_toggle` 這個 action 唯一的目的地是
     * [symbolStandard]，那頁一個 ASCII 數字都沒有——見 [symbolStandard] 的 KDoc 與 `OtherKeyboardsContentTest`
     * 釘死的「全形、非 ASCII」測試）。 也就是說**在密碼／URL 欄位按下這顆鍵，使用者得到的是全形標點頁，不是半形數字或半形符號
     * 頁**——本模組目前沒有半形符號頁可切，這是已知落差，登記為 W2-B（`:ime`）follow-up： 若要讓使用者在密碼／URL
     * 情境切到「真正半形」的符號/數字頁，需要新增一份半形符號頁（新的 交付範圍，不在本輪處理）。**這條落差是「目的地內容不對」**， 跟下面這條「回不回得去」的問題是兩回事，不要混為一談。
     *
     * **M1 已修（round-13 tracer 審查，回程）**：F1 只講了「全形」按下去之後的內容不對，沒講「按下去之後回不回得去」—— 而 round-11（J1）把
     * [symbolStandard] 的「注音」鍵改成固定切回注音鍵盤之後，[symbolStandard] 的控制列已經沒有 任何一顆鍵能帶使用者「回到密碼/URL
     * 鍵盤」：「注音」把注音鍵盤丟進密碼欄位（錯誤目的地），「ABC」的目的地 （通用英文鍵盤）根本不存在——**兩個出口都到不了使用者原本在打的這份鍵盤**，是死路，不是內容不對。
     * [symbolStandard] 現已新增通用的「返回」鍵（`Custom("switch_back")`）解掉這條死路，細節見 [symbolStandard]
     * KDoc；[ReturnPathCoverageTest] 釘住「[Keyboards.all] 裡每一顆切頁鍵的目的地都有路可回」，防止未來的切頁鍵 改動再次靜默斷開回程。
     *
     * 標籤之所以是「全形」而非「符號」：round-9 審查指出，C9/D1/D2 已經把密碼／URL 需要的 0-9 與 常用半形符號全部用 longPress
     * 掛在主鍵列上，使用者其實不需要離開這份鍵盤；此時把這顆通往 全形頁的鍵標成「符號」，等於用更精確、更吸引人的字眼把使用者導向一個對他無用的頁面——比
     * 原本明顯文不對題的「123」更容易誤觸。標成「全形」讓使用者在按下之前就知道那不是 ASCII 符號。
     */
    val passwordQwerty: KeyboardDef by lazy {
        KeyboardLoader.loadFromResource("keyboards/password_qwerty.json")
    }

    /**
     * 電話撥號鍵盤。`+` 鍵的 longPress 掛 `-`、`#` 鍵的 longPress 掛 `,`（撥號暫停）（F3），供台灣常見
     * 「02-2712-3456」「0912-345-678」電話號碼分隔符輸入；本頁沒有切頁鍵（終端頁）。終端頁清單與判定 方式見 [numericStandard]
     * KDoc（L1，round-11 審查）。
     */
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

    /**
     * 日期／時間輸入：數字 + 常用分隔符（`/` `:` `-`）。本頁沒有切頁鍵（終端頁）。終端頁清單與判定方式見 [numericStandard] KDoc（L1，round-11
     * 審查——本頁先前完全沒被記載為終端頁）。
     */
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
