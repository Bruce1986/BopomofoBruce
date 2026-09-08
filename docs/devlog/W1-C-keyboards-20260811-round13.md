# W1-C devlog — 審查 finding 修正 round 13（2026-08-11 13:15 UTC+8，即 05:15 UTC）

- 分支：`feat/w1c-keyboards`，起點 HEAD `295ab15`。
- 對應：Opus tracer 第十三輪（W1-C 收斂關卡）提出的 3 條 finding（M1 high、M2/M3 medium）。
  本輪只做修正，沒有 push、沒有動 `:common`、沒有動 `docs/STATUS.md`。
- **補記**：`295ab15`（L1/L2/L3，round-11 tracer 審查）新增的兩個測試檔
  （`ToggleFreeKeyboardsTest`、`CharacterKeyLabelMatchesCharTest`）、L1 的終端頁 KDoc 改寫、L3 的
  label/char 不變量，在 round 1–6 六份 devlog 裡一次都沒被記載過（這正是本輪 M3 finding 本身）。
  這份 devlog 一併補齊 L1/L2/L3 與本輪 M1/M2 的記載，見下方。

## M1（high）：J1 的修正造成死路——從 password/url 進符號頁後回不去

`password_qwerty.json` / `url_qwerty.json` 控制列的「全形」鍵（`symbol_toggle`）唯一目的地是
`symbol_standard.json`，但那頁控制列（J1 修正後）只有「注音」→`Custom("switch_to_zhuyin")`（固定切回
注音鍵盤）、「ABC」→`language_toggle`（目的地不存在）、空白、退格、Enter。從密碼/URL 欄位按「全形」
進到符號頁之後，兩個出口都到不了使用者原本在打的密碼/URL 鍵盤——一個把注音鍵盤丟進密碼欄位，一個
沒有落點。資料層完全沒有「回到來源鍵盤」的表達方式。

**選擇 (a)：資料層解**（而非只在 KDoc 登記落差）。理由：

- 這不是「內容不對」（半形符號頁還沒做，那條已知落差維持不動），而是「有去無回」——按下一顆標示明確
  的鍵之後完全困住，屬於資料模型本身可以、也應該補的缺口，不需要等 `:ime`（W2-B）另外設計一套機制。
- `KeyAction.Custom` 本來就是為這類情境保留的 escape hatch（`KeyAction.kt` KDoc：「不需要為每個新功能
  擴 sealed 子型別」），J1 已經用過同一招（`"switch_to_zhuyin"`），本輪只是再加一顆語意不同的 Custom
  鍵，不需要動 `:common` 契約。
- 用「固定目的地」的鍵（像「注音」）沒辦法同時服務全部 4 個來源（zhuyin×2、password、url）——`注音`
  寫死回注音，只服務其中 2 個。改成**通用**返回鍵（不綁定目的地，由 `:ime` 記住「按下 `symbol_toggle`
  之前在哪份鍵盤」）才能一次涵蓋全部來源，且不需要為每個來源各刻一顆專屬返回鍵。

### 改法

- `keyboards/src/main/resources/keyboards/symbol_standard.json`：控制列新增一顆鍵，label
  「返回」、action `Custom("switch_back")`、weight 1.2（放在控制列最前）。
- `keyboards/src/main/kotlin/com/bopomofobruce/keyboards/Keyboards.kt`：
  - `symbolStandard` KDoc 補「返回」鍵的說明、為什麼「注音」不夠、`:ime` 需要維護的「來源鍵盤」狀態
    （至少一層，不需要完整堆疊——本模組沒有巢狀切頁）。
  - `passwordQwerty` KDoc 的 F1 段落補一段區分「內容不對」（F1，未動）vs「回不回得去」（M1，已修），
    避免兩條落差被混為一談。
  - `:ime` 需實作的 `Custom` id 清單補上 `"switch_back"`。
  - 新增 `Keyboards.PAGE_SWITCH_CUSTOM_IDS` / `Keyboards.NON_PAGE_SWITCH_CUSTOM_IDS`（見 M2）。
- 新增測試 `ReturnPathCoverageTest.kt`：對 `Keyboards.all` 建立「切頁鍵 → 目的地鍵盤」的圖，斷言每個
  目的地都有路可回來源（直接切頁鍵，或通用 `switch_back`），否則要求登記進 `KNOWN_GAPS`（目前為空集
  合——這條 finding 修完後沒有已知的「有去無回」缺口）。`LanguageToggle` 的目的地（通用 ABC 鍵盤）不在
  `Keyboards.all` 裡，不屬於這張圖能評估的範圍（本來就是已登記的 W2-B follow-up，不是這條測試要抓的
  問題）。
- `ToggleKeyLabelActionConsistencyTest.kt`：`expected` 表補一筆 `"返回" -> Custom("switch_back")`。

**證明會紅**：把 `symbol_standard.json` 的「返回」鍵暫時移除，重跑
`./gradlew :keyboards:testDebugUnitTest --tests "com.bopomofobruce.keyboards.ReturnPathCoverageTest"`：

```
ReturnPathCoverageTest > every reachable page-switch destination has a way back or a registered gap() FAILED
org.opentest4j.AssertionFailedError: keyboard 'password-qwerty' can switch to 'symbol-standard-page1'
via SymbolToggle, but 'symbol-standard-page1' has no way back to 'password-qwerty' (no direct edge,
no generic switch_back, and (password-qwerty to symbol-standard-page1) is not in
ReturnPathCoverageTest.KNOWN_GAPS)
keyboard 'url-qwerty' can switch to 'symbol-standard-page1' via SymbolToggle, but
'symbol-standard-page1' has no way back to 'url-qwerty' (no direct edge, no generic switch_back, and
(url-qwerty to symbol-standard-page1) is not in ReturnPathCoverageTest.KNOWN_GAPS)
==> expected: <true> but was: <false>
```

還原「返回」鍵後重跑同一條指令：綠。`git diff` 對 `symbol_standard.json` 確認回到修正後狀態。

## M2（medium）：終端頁判定規則漏掉 `Custom`，而且被寫成「若且唯若」

`numericStandard` KDoc（L1，round-11）給 `:ime` 的終端頁判定規則寫「rows 內所有 action 都不是
`SymbolToggle` 或 `LanguageToggle`」，但 J1（round-11）已經確立 `Custom` 也可能是切頁鍵
（`"switch_to_zhuyin"`）。這條規則沒跟著更新——今天湊巧不出錯是因為 `symbol_standard` 另外掛了
`language_toggle`，一旦出現「只用 `Custom` 切頁」的鍵盤就會被誤判成終端頁。`ToggleFreeKeyboardsTest`
把同一條不完備規則原封不動編碼進測試，抓不到、還把錯誤定義釘死。

### 改法

- `Keyboards.kt` 新增兩個模組層級的登記表：
  - `PAGE_SWITCH_CUSTOM_IDS`：切頁用的 `Custom` id（`"switch_to_zhuyin"`、`"switch_back"`）。
  - `NON_PAGE_SWITCH_CUSTOM_IDS`：非切頁的 `Custom` id（`"url_insert_dot_com"`，純插入文字）。
- `numericStandard` KDoc 的終端頁判定規則改寫，明講「切頁鍵不只 `SymbolToggle`/`LanguageToggle`，也
  包含 id 落在 `PAGE_SWITCH_CUSTOM_IDS` 裡的 `Custom`」。
- `ToggleFreeKeyboardsTest.kt`：`isToggleFree` 改用新的 `isPageSwitchAction`，對兩份登記表都查不到的
  `Custom` id **直接 throw `AssertionError`**（不是回傳 false 靜默放行），訊息要求「登記進
  `PAGE_SWITCH_CUSTOM_IDS` 或 `NON_PAGE_SWITCH_CUSTOM_IDS`」——與 `ToggleKeyLabelActionConsistencyTest`
  對未登記 label 的處理一致（那邊是收集進 failures 清單，這邊因為藏在 `.none{}`/`.any{}` 裡直接 throw
  更直接）。

**證明會紅**：暫時把 `PAGE_SWITCH_CUSTOM_IDS` 與 `NON_PAGE_SWITCH_CUSTOM_IDS` 都清空成 `emptySet()`，
重跑 `./gradlew :keyboards:testDebugUnitTest --tests "com.bopomofobruce.keyboards.ToggleFreeKeyboardsTest"`：

```
ToggleFreeKeyboardsTest > exactly numeric, phone dialpad and datetime keyboards are toggle-free() FAILED
java.lang.AssertionError: unregistered Custom id 'switch_back' -- add it to
Keyboards.PAGE_SWITCH_CUSTOM_IDS or Keyboards.NON_PAGE_SWITCH_CUSTOM_IDS depending on whether it
switches pages, so terminal-page detection can account for it
	at com.bopomofobruce.keyboards.ToggleFreeKeyboardsTest.isPageSwitchAction(...)
	at com.bopomofobruce.keyboards.ToggleFreeKeyboardsTest.isToggleFree(...)
```

還原兩份登記表後重跑同一條指令：綠。

## M3（medium）：round-11（L1/L2/L3）交付完全沒有 devlog 記載——本輪一併補上

`295ab15`（commit message：「L1/L2/L3 round-11 tracer findings（終端頁 KDoc、devlog 更正、
label/char 不變量）」）做了三件事，round 1–6 的六份 devlog 一次都沒提到：

- **L1**：`numericStandard`/`phoneDialpad` 的 KDoc 各自誤稱「唯一終端頁」且互相矛盾，也漏了同樣不含
  切頁鍵的 `datetimeStandard`。改寫成三處互相參照的一致敘述，新增 `ToggleFreeKeyboardsTest.kt` 釘住
  「`numeric-standard`、`phone-dialpad`、`datetime-standard` 且僅此三份不含 `SymbolToggle`/
  `LanguageToggle`」。
- **L2**：round 5 devlog 的「F1 label 改動範圍核實」小節自稱已用 grep 核實過，但漏了
  `symbol_standard.json` 當時也掛 `symbol_toggle`，「全模組同一 action 標籤一致」的結論從未成立。
- **L3**：C9/D1/D2 手寫的 52 組 `longPress`（10 位數字 + 16 符號 × 2 檔）從未有測試斷言 label 與
  `Character` action 的 `char` 相符——遮罩密碼欄位裡的錯字使用者永遠不會發現。新增
  `CharacterKeyLabelMatchesCharTest.kt` 逐一比對 `Keyboards.all` 內每顆 `Character` 鍵（含
  `longPress`）的 label 是否等於 `action.char`。

`295ab15` 的 commit message 記載三條均以「暫改資料觸發紅燈、還原後綠燈」驗證過；本輪未重新驗證 L1/L3
的紅燈（那是上一輪的交付，不是本輪改動），只讀了 diff 與現有測試檔內容確認測試邏輯與 KDoc 敘述吻合。

**round 6 devlog 的「34 個 test case」已標註為「round 6 當時」**（見
`docs/devlog/W1-C-keyboards-20260811-round6.md` 的 Gradle 收尾段落更正）——HEAD `295ab15` 實際是
36 個（round 6 的 34 + L1 新增 1 + L3 新增 1），本輪（M1/M2）再加 1（`ReturnPathCoverageTest`）成 37。

## 改動檔案總覽（本輪 M1/M2/M3）

- `keyboards/src/main/resources/keyboards/symbol_standard.json`：控制列新增「返回」鍵。
- `keyboards/src/main/kotlin/com/bopomofobruce/keyboards/Keyboards.kt`：新增
  `PAGE_SWITCH_CUSTOM_IDS`/`NON_PAGE_SWITCH_CUSTOM_IDS`；`symbolStandard`/`passwordQwerty`/
  `numericStandard` KDoc 更新。
- `keyboards/src/test/kotlin/com/bopomofobruce/keyboards/ReturnPathCoverageTest.kt`（新檔，M1）。
- `keyboards/src/test/kotlin/com/bopomofobruce/keyboards/ToggleFreeKeyboardsTest.kt`（M2：
  `isPageSwitchAction` 改寫）。
- `keyboards/src/test/kotlin/com/bopomofobruce/keyboards/ToggleKeyLabelActionConsistencyTest.kt`
  （M1：`expected` 表補「返回」）。
- `docs/devlog/W1-C-keyboards-20260811-round6.md`（M3：「34 個 test case」標註為 round 6 當時）。
- `docs/devlog/W1-C-keyboards-20260811-round13.md`（本檔，M3）。

## 現況（本輪收尾）

- 測試檔數：7（`KeyboardLoaderTest`、`ZhuyinLayoutContentTest`、`OtherKeyboardsContentTest`、
  `ToggleFreeKeyboardsTest`、`ToggleKeyLabelActionConsistencyTest`、`CharacterKeyLabelMatchesCharTest`、
  `ReturnPathCoverageTest`）。
- test case 數：37（HEAD `295ab15` 的 36 + 本輪新增 1）。

## Gradle 收尾結果

- `:keyboards:assembleDebug` — 綠
- `:keyboards:testDebugUnitTest` — 綠，37 個 test case
- `:keyboards:ktfmtCheck` — 綠（首次跑即綠，未觸發 ktfmtFormat）
- `:keyboards:lint` — 綠

## 認為 finding 有誤或需要留意的地方

- 三條 finding 本身判斷皆正確，未發現需要反駁之處。
- 唯一需要留意：M1 選 (a) 之後，`ReturnPathCoverageTest.KNOWN_GAPS` 目前是空集合——這是「修完之後沒有
  已知缺口」的正確狀態，不是「還沒填」；未來若真的出現無法修的切頁死路，才把那對 `(來源 id, 目的地
  id)` 加進去，且要在 `Keyboards.kt` KDoc 同步登記，不要只加測試那邊的 allowlist。
