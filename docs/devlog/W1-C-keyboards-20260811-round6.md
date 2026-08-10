# W1-C devlog — 審查 finding 修正 round 6（2026-08-11 UTC+8）

- 分支：`feat/w1c-keyboards`
- 對應：codex 獨立審查提出的 1 條 P1 finding（J1）。本輪只做修正，沒有 push、沒有動 `:common`、
  沒有動 `docs/STATUS.md`。

## J1（P1）符號鍵盤的兩顆切換鍵，依契約語意都到不了標籤宣稱的目的地

`symbol_standard.json` 控制列原本是：

- 「注音」掛 `language_toggle`（`:common` KDoc 定義：切換到英文/數字鍵盤）
- 「ABC」掛 `symbol_toggle`（`:common` KDoc 定義：切換到符號鍵盤——但符號鍵盤正在顯示中）

兩顆鍵的 action 方向都跟標籤字面語意相反：照 `:ime` 未來會實作的 dispatcher 語意接線，按「注音」
會被導向英文/數字鍵盤，按「ABC」則等於「切到符號鍵盤」但使用者已經在符號鍵盤上——回不去注音、也
到不了英文。

這條 finding 在 round-3（`docs/devlog/W1-C-keyboards-20260810-1531.md`）已被查過一次，當時判斷
「`KeyAction` 沒有任何內建變體字面上代表『切回注音』」，因此**交給 lead／`:ime`（W2-B）裁決**，未
修正。

**該判斷的前提不完整**：`KeyAction.Custom(id)` 是 `:common` 刻意留的擴充逃生口（見
`KeyAction.kt` KDoc：「不需要為每個新功能擴 sealed 子型別」），而且 `url_qwerty.json` 的 `.com`
鍵**當時已經在用它**（`type: "custom"`, id `"url_insert_dot_com"`）——round-3 那輪審查其實已經
看過這個先例（`.com` 鍵是更早修正的），卻沒有把同一招套用到「注音」這顆鍵上。這條可以在不動
`:common` 契約的前提下解掉，不需要交給 lead 裁決。已在 `docs/devlog/W1-C-keyboards-20260810-1531.md`
補一段更正註記，撤回原本的結論。

### 改法

- `symbol_standard.json`：「ABC」改掛 `language_toggle`（符合字面語意：切到英文/數字，與
  `zhuyin_4x10_{portrait,landscape}.json` 的「英數」用法一致）。
- `symbol_standard.json`：「注音」改掛 `KeyAction.Custom("switch_to_zhuyin")`（`:ime` 必須實作的
  契約 id）。
- 檢查了其餘 7 份鍵盤的所有非 `Character` 鍵是否有同型態的錯配：
  - `zhuyin_4x10_portrait.json` / `zhuyin_4x10_landscape.json`：「符號」→`symbol_toggle`、
    「英數」→`language_toggle`——字面語意自洽（round-3 已查證過，本輪重新核對無誤）。
  - `password_qwerty.json` / `url_qwerty.json`：「全形」→`symbol_toggle`——`symbol_toggle` 唯一
    的目的地就是全形符號頁，標籤已在 round-9/round-10（F1）改成「全形」與目的地相符，本輪核對
    無誤，未再變動。
  - `url_qwerty.json` 的「.com」→`Custom("url_insert_dot_com")`：本來就正確，且是本輪採用
    `Custom` 這條路的先例依據。
  - 其餘控制鍵（⌫/　/⏎/⇧）語意固定、不涉及「切到哪個鍵盤」，不在本次錯配檢查範圍內。
  - 結論：只有 `symbol_standard.json` 這兩顆鍵有錯配，其餘 7 份鍵盤的切換鍵語意皆與標籤相符。
- `Keyboards.kt` 的 `symbolStandard` KDoc 補上這次的改法說明，並整理出「`:ime` 必須實作哪些
  `Custom` id」的清單（供 W2-B 接手時對照，不必逐份 JSON 翻找）：
  - `"url_insert_dot_com"`（`urlQwerty`）
  - `"switch_to_zhuyin"`（`symbolStandard`，切回 `zhuyin4x10Portrait` / `zhuyin4x10Landscape`，
    依當時 orientation 擇一）
- **未動**：`language_toggle` 的目的地鍵盤（通用 ABC 鍵盤）本來就不在 W1-C 交付範圍、`Keyboards.all`
  裡沒有這一項——這是既有的、已登記過的 W2-B follow-up（見
  `docs/devlog/W1-C-keyboards-20260810-1706.md`），本輪只是讓「ABC」這顆鍵的 action 字面正確，
  **沒有**為此新增一份 ABC 鍵盤。

### 改動檔案

- `keyboards/src/main/resources/keyboards/symbol_standard.json`：控制列「注音」/「ABC」兩顆鍵的
  `action`。
- `keyboards/src/main/kotlin/com/bopomofobruce/keyboards/Keyboards.kt`：`symbolStandard` KDoc 補
  J1 改法說明＋`:ime` 需實作的 `Custom` id 清單。
- `keyboards/src/test/kotlin/com/bopomofobruce/keyboards/ToggleKeyLabelActionConsistencyTest.kt`
  （新檔）：見下方測試段落。
- `docs/devlog/W1-C-keyboards-20260810-1531.md`：補更正註記，撤回原本「交給 lead 裁決」的結論。

### 新增測試

`ToggleKeyLabelActionConsistencyTest.kt`：以一張 `label -> 預期 action` 對照表，逐一走過
`Keyboards.all`（8 份鍵盤）裡**所有非 `Character` 鍵**（含短按與長按，例如
`zhuyin_4x10_{portrait,landscape}` 的 `ㄜ` 長按 `ㄦ`），斷言 label 與 action 一致；若走到一個
表裡沒有登記的 label，測試本身直接報「請補一筆對照表項目」而非靜默跳過。

**證明會紅**：暫時把 `symbol_standard.json` 的「注音」鍵從 `Custom("switch_to_zhuyin")` 改回
`language_toggle`（round-3 修正前的錯誤狀態），重跑
`./gradlew :keyboards:testDebugUnitTest --tests "com.bopomofobruce.keyboards.ToggleKeyLabelActionConsistencyTest"`：

```
ToggleKeyLabelActionConsistencyTest > every non-Character key's label matches its documented action() FAILED
org.opentest4j.AssertionFailedError: keyboard symbol-standard-page1: label '注音' expected action
Custom(id=switch_to_zhuyin) but found LanguageToggle ==> expected: <[]> but was:
<[keyboard symbol-standard-page1: label '注音' expected action Custom(id=switch_to_zhuyin) but found LanguageToggle]>
```

還原後重跑 `:keyboards:testDebugUnitTest`：34 個測試（原 33 + 本輪新增 1）全綠，`git diff` 對
`symbol_standard.json` 確認回到修正後狀態（未殘留 debug 改動）。

## Gradle 收尾結果

- `:keyboards:assembleDebug` — 綠
- `:keyboards:testDebugUnitTest` — 綠，34 個 test case
- `:keyboards:ktfmtCheck` — 首次跑紅（`Keyboards.kt` 與新測試檔格式未過），跑
  `:keyboards:ktfmtFormat` 後重新完整跑一次 `:keyboards:assembleDebug :keyboards:testDebugUnitTest
  :keyboards:ktfmtCheck :keyboards:lint` 四項全綠
- `:keyboards:lint` — 綠

## `:ime`（W2-B）需實作的 `Custom` id 清單（本輪整理，另見 `Keyboards.kt` KDoc）

| id | 來源鍵盤 | 目的地 |
| --- | --- | --- |
| `url_insert_dot_com` | `urlQwerty` | 在游標處插入 `.com`（既有，非本輪新增） |
| `switch_to_zhuyin` | `symbolStandard` | 切回 `zhuyin4x10Portrait` / `zhuyin4x10Landscape`（依當時 orientation 擇一）（J1 新增） |

## 認為 finding 有誤或需要留意的地方

- finding 原文的判斷本身是對的（兩顆鍵確實回不去注音、也開不了英文），round-3 那輪的「留給 lead
  裁決」才是判斷錯誤的一方（漏看 `Custom` 這個逃生口，且忽略了自己同輪已經用過的 `.com` 先例）。
  沒有發現本輪 finding 有誤植或需要反駁的地方。
