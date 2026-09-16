# W1-C devlog — 審查 finding 修正 round 5（2026-08-11 UTC+8）

- 分支：`feat/w1c-keyboards`
- 對應：Opus tracer 提出的 4 條 finding（F1/F2/F3/F4），F4 已由 lead 親自查證屬實。本輪只做修正，
  沒有 push、沒有動 `:common`、沒有動 `docs/STATUS.md`。

## 已修正

### F1（medium）password/url「123」鍵誤標，實際目的地是全形符號頁

`password_qwerty.json` / `url_qwerty.json` 控制列的切頁鍵原本標「123」但掛
`KeyAction.SymbolToggle`，本模組唯一的符號頁（`symbol_standard.json`）只有全形標點，一個 ASCII
數字都沒有（`OtherKeyboardsContentTest.symbol keyboard characters are all full-width` 釘死這件
事）。

**改法**：把兩份 JSON 該鍵的 `label` 從 `"123"` 改成 `"符號"`，與 `zhuyin_4x10_portrait.json` /
`zhuyin_4x10_landscape.json` 上同一個 action 的標籤一致，讓標籤與 `symbol_toggle` 的實際目的地
相符。**未**新增半形符號頁（那是新的交付範圍，超出本輪修正）。

改動檔案：
- `keyboards/src/main/resources/keyboards/password_qwerty.json`（控制列第 1 顆鍵 `label`）
- `keyboards/src/main/resources/keyboards/url_qwerty.json`（控制列第 1 顆鍵 `label`）
- `keyboards/src/main/kotlin/com/bopomofobruce/keyboards/Keyboards.kt`：在 `passwordQwerty` 的
  KDoc 補一段「F1 已知落差」，說明按下該鍵在密碼／URL 欄位只會得到全形符號頁，登記為 W2-B
  follow-up。
- `keyboards/src/test/kotlin/com/bopomofobruce/keyboards/OtherKeyboardsContentTest.kt`：更新
  D1 測試上方的 comment，把提到的 `"123"` 改成 `"符號"` 並註記 F1。（round-10 後再次更名為 `"全形"`，該 comment 與本行已一併更正——理由見 `Keyboards.kt` 的 `urlQwerty` KDoc。）

沒有新增測試——原本就沒有測試斷言這顆鍵的 `label` 字面值是 `"123"`（已用
`grep -rn '"123"' keyboards/src/test/kotlin/` 確認），改標籤本身不影響任何既有測試的紅綠。

### F2（medium）numeric-standard 打不出負號

`numeric_standard.json` 原本只有 `0-9` 與 `.`，沒有 `-`，也沒有任何切頁鍵——`TYPE_NUMBER_FLAG_SIGNED`
欄位無法輸入負數。

**改法**：比照 C9 用既有 `LongPressData` 契約，在 `.` 鍵上加 `longPress` 掛 `-`。

改動檔案：
- `keyboards/src/main/resources/keyboards/numeric_standard.json`（`.` 鍵新增 `longPress`）
- `keyboards/src/main/kotlin/com/bopomofobruce/keyboards/Keyboards.kt`：`numericStandard` KDoc
  補充負號 longPress 與「本頁無切頁鍵、是唯一終端頁」的說明。（**round-11 tracer 審查（L1）更正**：終端頁其實有三份——`numeric_standard`、`phone_dialpad`、`datetime_standard`，「唯一」是錯的；見 round13 devlog 與 `Keyboards.numericStandard` 的 KDoc。）
- `keyboards/src/test/kotlin/com/bopomofobruce/keyboards/OtherKeyboardsContentTest.kt`：新增
  `numeric keyboard can type a minus sign for signed values`，斷言 `-` 短按或長按可達。

**已證明會紅**：暫時用 `git checkout HEAD --` 把 `numeric_standard.json` 還原成未修正版本（保留新
測試），重跑 `:keyboards:testDebugUnitTest`：

```
OtherKeyboardsContentTest > numeric keyboard can type a minus sign for signed values() FAILED
    org.opentest4j.AssertionFailedError at OtherKeyboardsContentTest.kt:69
```

還原修正後重跑，該測試轉綠，`git diff` 恢復乾淨。

### F3（medium）phone-dialpad 打不出電話分隔符

`phone_dialpad.json` 原本只有 `0-9 * # +`，沒有 `-`（無法打「02-2712-3456」這類台灣常見號碼格式），
也沒有暫停符號 `,`，全鍵盤沒有任何切頁鍵。

**改法**：在 `+` 鍵加 `longPress` 掛 `-`，在 `#` 鍵加 `longPress` 掛 `,`（撥號暫停）。

改動檔案：
- `keyboards/src/main/resources/keyboards/phone_dialpad.json`（`+` 鍵、`#` 鍵各新增 `longPress`）
- `keyboards/src/main/kotlin/com/bopomofobruce/keyboards/Keyboards.kt`：`phoneDialpad` KDoc
  補充分隔符 longPress 與「本頁無切頁鍵、是唯一終端頁」的說明。（**round-11 tracer 審查（L1）更正**：終端頁其實有三份——`numeric_standard`、`phone_dialpad`、`datetime_standard`，「唯一」是錯的；見 round13 devlog 與 `Keyboards.numericStandard` 的 KDoc。）
- `keyboards/src/test/kotlin/com/bopomofobruce/keyboards/OtherKeyboardsContentTest.kt`：新增
  `phone dialpad can type out common phone number separators`，斷言 `-` 與 `,` 短按或長按可達。

**已證明會紅**：與 F2 同樣手法，暫時還原 `phone_dialpad.json` 到未修正版本、保留新測試，重跑：

```
OtherKeyboardsContentTest > phone dialpad can type out common phone number separators() FAILED
    org.opentest4j.AssertionFailedError at OtherKeyboardsContentTest.kt:196
```

（此次連同 F2 一起還原，同一次跑輸出 `33 tests completed, 2 failed`。）還原修正後重跑，兩條皆轉綠。

### F4（medium）0714 devlog 的未查證推測從未被更正——lead 已查證屬實

`docs/devlog/W1-C-keyboards-20260810-0714.md` 第 53 行原文「兩者若把
`Json { ignoreUnknownKeys = true }` 打開就會轉綠變紅」是未查證的推測，round 3（1644 devlog）的
C10 段落只在 `KeyboardLoaderTest.kt` 補了一行 comment，**沒有回頭改寫 0714 原文那句話**——`git log
--follow -p -- docs/devlog/W1-C-keyboards-20260810-0714.md` 顯示該檔案自 `0ee454d`（首次
commit）之後沒有再被修改過，直到本輪。

**改法**：
1. 直接改寫 `docs/devlog/W1-C-keyboards-20260810-0714.md` 第 53 行那句推測，換成 C8 實測的結論
   （只有 `unknown field` 那條會紅、`unknown discriminator` 那條維持綠，失敗來源是 polymorphic
   serializer 找不到 discriminator，與 `ignoreUnknownKeys` 無關），並指回 1644 devlog 的
   C8/C10 段落。
2. 在 `docs/devlog/W1-C-keyboards-20260810-1644.md` 的 C10 段落補一段「F4 補正」，明講 round 3
   當時只改了 `KeyboardLoaderTest.kt` 的 comment、沒有改 0714 原文，本輪（round 5）已直接改寫
   0714 devlog 本文，不再只靠另開新 devlog 說明了事。

改動檔案：
- `docs/devlog/W1-C-keyboards-20260810-0714.md`（第 53 行附近，改寫為實測結論並加註更正說明）
- `docs/devlog/W1-C-keyboards-20260810-1644.md`（C10 段落補「F4 補正」小節）

未改動 `KeyboardLoaderTest.kt`——該檔的 comment 在 round 3 已經正確，這次落差只在 0714 devlog
本文沒有跟著更正。

## Gradle 收尾結果（`:keyboards:assembleDebug` 全重跑；`ktfmtCheck` 第一輪抓到 1 個檔案格式不符）

- `:keyboards:ktfmtCheck`（第一輪）— **紅**：`Keyboards.kt` 格式不符（`[ktfmt] Invalid formatting
  for: .../Keyboards.kt`），跑 `:keyboards:ktfmtFormat` 修正後重新完整跑一次全部四項。
- `:keyboards:assembleDebug` — 綠（`BUILD SUCCESSFUL`）
- `:keyboards:testDebugUnitTest` — 綠，33 個 test case（round 4 結束時 31 條 + 本輪新增 2 條：F2
  `numeric keyboard can type a minus sign for signed values`、F3 `phone dialpad can type out
  common phone number separators`）
- `:keyboards:ktfmtCheck`（複驗）— 綠
- `:keyboards:lint` — 綠

## F1 label 改動範圍核實

**round-11 tracer 審查（L2）更正**：本節原文自稱「已用 grep 核實」，但內容有兩處錯誤——(1)
`symbol_standard.json` 當時被列為「頁內無此鍵」，但實際上 round 5 當下 `symbol_standard.json`
控制列的「ABC」鍵掛的正是 `symbol_toggle`（J1，round-11 之後才改成 `language_toggle`），所以當時
的正確檔案數是 5 個、不是「4 份 + 1 個目的地」；(2) 「改動後全模組同一個 action 的標籤一致」這句
結論從未成立過——`url_qwerty.json`／`password_qwerty.json` 標成「符號」，`zhuyin_4x10_*.json`
標成「符號」沒錯，但這只是巧合；`symbol_toggle` 的標籤此後又被改為「全形」（見 `Keyboards.kt`
`passwordQwerty` 的 F1 落差說明），標籤從來不是靠這個 action 本身固定的。**這是「把未查證推測寫進永久紀錄」
在本專案的第三次**（前兩次見 `W1-C-keyboards-20260810-1644.md` C10 段落與其 F4 補正）——原因同樣
是寫下當下觀察到的字面事實，卻用了一句更強的、沒有實際驗證過的結論句去總結它。

以下改以 J1 修正後的現況（HEAD `b952131`）重新 grep 核對：

`grep -rn '"type": "symbol_toggle"'` 確認 `symbol_toggle` action 目前出現在 4 個檔案，各自的 label：
- `password_qwerty.json` — label「全形」
- `url_qwerty.json` — label「全形」
- `zhuyin_4x10_portrait.json` — label「符號」
- `zhuyin_4x10_landscape.json` — label「符號」

`symbol_standard.json` 已不含 `symbol_toggle`（J1 之後改掛 `language_toggle`，label「ABC」）。

`grep -rn '"type": "language_toggle"'` 確認 `language_toggle` action 出現在 3 個檔案：
`symbol_standard.json`（label「ABC」）、`zhuyin_4x10_portrait.json`（label「英數」）、
`zhuyin_4x10_landscape.json`（label「英數」）。

結論改寫：**同一個 action 依所在頁面帶不同標籤是常態，不是需要收斂成一致的目標**——
`symbol_toggle` 在密碼／URL 頁標「全形」、在注音頁標「符號」；`language_toggle` 在符號頁標
「ABC」、在注音頁標「英數」。標籤要傳達的是「按下去去哪、對使用者有什麼意義」，語意由 `:ime`
依當前鍵盤決定，不是 action 本身的固定屬性。`ToggleKeyLabelActionConsistencyTest`（J1）已經逐一
釘住這些 (label, action) 配對，比這裡的散文更能防止標籤漂移。
