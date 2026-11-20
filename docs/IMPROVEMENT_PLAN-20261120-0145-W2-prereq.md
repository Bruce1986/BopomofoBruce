# W2 開工前置：待 owner 裁決事項與 W2 拆包計畫

> 產生時間：2026-11-20 01:45（UTC+8）
> 來源：[HANDOVER-W1-fixloop-20260811.md §7](HANDOVER-W1-fixloop-20260811.md#7-進-w2-前的建議給-owner)、
> W1-C devlog（`W1-C-keyboards-20260810-1706.md`、`W1-C-keyboards-20260811-round13.md`）、
> PR #9 分支上的 `theme/.../style/StyleSheet.kt`。
> 本檔只列選項與建議，**沒有任何一項已經決定**；owner 裁決後在各項的「裁決」欄填上結果。

## 目錄

- [0. 現況（2026-11-20 查證）](#0-現況2026-11-20-查證)
- [1. language_toggle 的目的地鍵盤](#1-language_toggle-的目的地鍵盤)
  - [1b. toggle 是單向跳轉還是雙向切換](#1b-toggle-是單向跳轉還是雙向切換)
- [2. Custom id 契約](#2-custom-id-契約)
  - [2a. id 常數與處理器放哪裡](#2a-id-常數與處理器放哪裡)
  - [2b. switch_back 的語意](#2b-switch_back-的語意)
  - [2c. 有 composing 時按插入類 Custom（.com、AM/PM）](#2c-有-composing-時按插入類-customcomampm)
- [3. KeyboardTheme 是否帶 shapes／typography](#3-keyboardtheme-是否帶-shapestypography)
- [4. W2 驗收改寫成可否證形式（交接文件 §7 的建議）](#4-w2-驗收改寫成可否證形式交接文件-7-的建議)
- [5. 其他待裁決（由本輪子工作包回報後補上）](#5-其他待裁決由本輪子工作包回報後補上)
  - [5a. W2-B1 processor chain 回報的契約缺口](#5a-w2-b1-processor-chain-回報的契約缺口)
  - [5b. W2-D1 字串工具回報](#5b-w2-d1-字串工具回報)
  - [5c. W2-A1 decoder JNI 回報](#5c-w2-a1-decoder-jni-回報)
- [6. 本輪拆包（每支目標 ≤ 450 行，含測試）](#6-本輪拆包每支目標--450-行含測試)

---

## 0. 現況（2026-11-20 查證）

| 項目 | 狀態 |
|---|---|
| W1-A `:decoder-native`（PR #8） | 開啟中，head `5aa105a`，CI 綠、MERGEABLE，待 owner merge |
| W1-B `:theme`（PR #9） | 開啟中，head `ea5e0ca`，CI 綠、MERGEABLE，待 owner merge |
| W1-C `:keyboards`（PR #10） | 已於 2026-09-16 合併（`9442d90`） |
| W0 收尾（PR #11） | 開啟中，CI 綠，待 owner merge |
| 實機 | 2026-11-20 `adb devices` 為空；09-08 曾接 ASUS Zenfone 10（Android 15） |

W2 依 DEVPLAN 依賴 W1 全部合併；為了不讓 W2 卡在合併等待，本輪先做**不依賴待裁決事項**的小包（見 §6）。

---

## 1. `language_toggle` 的目的地鍵盤

**問題**：注音鍵盤的「英數」、符號鍵盤的「ABC」都是 `KeyAction.LanguageToggle`，但 `Keyboards.all` 裡沒有通用英文鍵盤
（只有 `password_qwerty`、`url_qwerty` 兩個情境專用的 QWERTY）。W2-B 接線時這顆鍵按下去沒有地方去。

| 選項 | 內容 | 優點 | 缺點 |
|---|---|---|---|
| **A（建議）** | 在 `:keyboards` 新增通用 `qwerty_standard`（直／橫各一）；`LanguageToggle` 的目的地＝這個鍵盤 | 使用者不必離開本 IME 就能打英文 | 多一支 `:keyboards` 小 PR（估 200–300 行，多半是 JSON） |
| B | `LanguageToggle` 改成呼叫系統 `switchToNextInputMethod`（交給 Gboard 等） | 零鍵盤工作 | 只裝本 IME 的使用者按了沒反應；打一個英文字要切兩次 IME |
| C | v1 先借用 `password_qwerty` 當目的地 | 不用新鍵盤 | 密碼鍵盤沒有建議列、語意不符，之後一定要改 |

### 1b. toggle 是單向跳轉還是雙向切換

W1-C devlog（`W1-C-keyboards-20260810-1531.md`）已指出：`KeyAction` 的 KDoc 用「切換到 X」的單向措辭，但
當時 `symbol_standard` 的「注音」掛 `language_toggle`、「ABC」掛 `symbol_toggle`，**只有在雙向語意下才自洽**（現在 main 上已改成「注音」＝`Custom("switch_to_zhuyin")`、「ABC」＝`language_toggle`）；
當時判定這是 `:common` 契約層級的決定，留給 lead／`:ime`（同檔的 J1 更正段已撤回這個結論）。之後 round6（J1）加了
`switch_to_zhuyin`、round13 再加 `Custom("switch_back")` 補回程，但單向或雙向的語意仍沒有定案。

| 選項 | 內容 | 優點 | 缺點 |
|---|---|---|---|
| **A（建議）** | 定為**雙向**：`language_toggle`＝注音系 ⟷ 英文；`symbol_toggle`＝符號頁 ⟷ 進入前的主鍵盤。只改 `KeyAction.kt` 的 KDoc 措辭消歧義（不動型別與 `@SerialName`） | 現有配列不用改；KDoc 改字不影響序列化 | 動到凍結檔案的註解（非程式碼） |
| B | 定為**單向**：新增 `KeyAction` 變體（例如 `zhuyin_toggle`），改 `symbol_standard` 的鍵 | 字面語意最清楚 | 改凍結契約＋JSON schema，`:keyboards` 配列與測試要跟著改 |

**裁決**：1（待填）／1b（待填）

---

## 2. `Custom` id 契約

**問題**：配列裡已用到五個 `Custom` id。`:keyboards` 的 `Keyboards.kt` 已經公開 `GENERIC_BACK_CUSTOM_ID`、
`PAGE_SWITCH_CUSTOM_IDS`、`NON_PAGE_SWITCH_CUSTOM_IDS` 等常數（區分「切頁」與「非切頁」），但**按下去要做什麼**沒有任何一處定義：

| id | 出現在 | 目前的推定語意 |
|---|---|---|
| `switch_to_zhuyin` | `symbol_standard`（「注音」） | 切回注音鍵盤 |
| `switch_back` | `symbol_standard`（「返回」） | 回到按 `symbol_toggle` 之前的鍵盤（動態目的地，W1-C round13 引入） |
| `url_insert_dot_com` | `url_qwerty`（「.com」） | 上屏 `.com` |
| `insert_am`／`insert_pm` | `datetime_standard` | 上屏 `AM`／`PM` |

### 2a. id 常數與處理器放哪裡

| 選項 | 內容 | 優點 | 缺點 |
|---|---|---|---|
| **A（建議）** | 沿用 `Keyboards.kt` 既有常數當唯一來源；`:ime` 用 id → handler 的對照表處理，未知 id 一律 no-op＋log；加一條測試斷言「`Keyboards.all` 出現的每個 id 都有 handler」 | 不動 `:common` 凍結契約；新增 id 時測試會紅 | id 是字串，打錯字只能靠測試抓 |
| B | 常數放 `:common` | 所有模組同一來源 | 動到 contracts-v1，要打新 tag |
| C | 把五個 id 改成新的 `KeyAction` sealed 子型別 | 編譯期保護 | 改凍結契約＋JSON schema，W1-C 配列與測試要跟著改 |

### 2b. `switch_back` 的語意

| 選項 | 內容 | 建議 |
|---|---|---|
| **A（建議）** | `:ime` 維護鍵盤導覽堆疊；`switch_back` 彈出一層；`onStartInput`（換輸入欄）時清空 | 符合 round13 的設計意圖，多層切換也回得去 |
| B | 只記「上一個鍵盤」一格 | 實作最簡單，但「注音→符號→（換頁）→返回」會回到符號第一頁而不是注音 |

### 2c. 有 composing 時按插入類 Custom（`.com`、AM/PM）

| 選項 | 內容 |
|---|---|
| **A（建議）** | 先把 composing 原文送出，再插入文字（與 W2-B1 對 `Character` 的處理一致） |
| B | 先以第一個候選送出，再插入 |

**裁決**：2a（待填）／2b（待填）／2c（待填）

---

## 3. `KeyboardTheme` 是否帶 shapes／typography

**問題**：PR #9 的 `StyleSheet` 有 `shapes`（圓角）與 `typography`（字級、字重），但 `:common` 的 `KeyboardTheme`
只有 `id`、`colors` 與 `dimens`，而且 `StyleSheet` 沒有實作 `KeyboardTheme`。`:ime` 依 DEVPLAN 只能透過 `:common` 介面拿主題，
所以使用者在設定頁調的圓角與字級**到不了鍵盤**。

| 選項 | 內容 | 優點 | 缺點 |
|---|---|---|---|
| A | 契約不動；`:ime` v1 用固定圓角／字級 | 零契約變更，最快看到鍵盤 | 設定頁的圓角／字級調了沒效果，等於做出一個假功能 |
| B | `:common` 新增可選介面（例如 `KeyboardStyleExtras`），`:ime` 用 `as?` 取 | 既有宣告一個字都不改 | `as?` 失敗時靜默退回預設，正是 W1 常見的「看起來合格但功能不能用」型態 |
| **C（建議）** | #9 合併後，把 `KeyboardShapes`／`KeyboardTypography` 搬進 `:common`，在 `KeyboardTheme` 加帶預設值的 `shapes`／`typography` 成員，打 `contracts-v2` tag | 單一來源、沒有靜默退回；repo 內實作者都會自動拿到預設值 | 要動凍結契約（但是加成員、有預設值，既有實作不必改）；要等 #9 合併 |
| D | `:ime` 直接依賴 `:theme` | 最直接 | 違反 DEVPLAN 依賴圖（`:ime` 只透過介面） |

**裁決**：（待填）

---

## 4. W2 驗收改寫成可否證形式（交接文件 §7 的建議）

W1 的 high 幾乎都是「照驗收看完全合格但功能不能用」。建議把 DEVPLAN 的 W2 驗收換成下面的寫法（每條都要指得出「把什麼改壞時哪條測試會紅」）：

| 包 | 原驗收 | 建議改寫 |
|---|---|---|
| W2-A | 1k 條真實注音輸入 unit test | 1k 條輸入的 Top-1 與直接呼叫 libchewing 的結果逐條比對；把候選排序反過來時測試要紅 |
| W2-A | 個人字典加詞/刪詞/查詢 Room test | 加一個詞後同一個注音的 Top-1 變成該詞；刪除後恢復原 Top-1 |
| W2-B | 可被裝在系統 IME 列表 | 實機上「ㄋㄧˇ → 點候選 → 測試 EditText 出現該字」；把 `commitText` 拿掉時 instrumented test 要紅 |
| W2-B | 用 fake decoder 仍可顯示假候選詞 → 點選送字 | （未改寫，沿用原驗收；W2-B2／B3 負責） |
| W2-B | Pixel 4a 上鍵盤展開 < 350 ms | （未改寫，沿用原驗收；W2-B2／B3 負責） |
| W2-B | 退格、空白、Enter、Shift 完整（DEVPLAN 列在工作內容，不在驗收） | 每顆功能鍵在 composing 空／非空兩種狀態各一條情境測試（W2-B1 已照此寫） |
| W2-C | 所有設定變更立即生效 | 改主題後 `KeyboardTheme` Flow 在一次收集內送出新值；把寫入 DataStore 拿掉時測試要紅 |
| W2-D | 簡繁互轉 round-trip 100 條 | （視 §5b D1-5 裁決） |

本表的「原驗收」是 DEVPLAN 原文的摘要，只列有改寫建議或本輪討論到的條目；其餘 W2 驗收照 DEVPLAN 原文。

**裁決**：（待填：全採／部分採／不採）

---

## 5. 其他待裁決（由本輪子工作包回報後補上）

**裁決**：（待填，逐項標選項，例如 B1-1：B）

### 5a. W2-B1 processor chain 回報的契約缺口

| # | 問題 | 選項 | 建議 |
|---|---|---|---|
| B1-1 | 純插字的 `Custom`（`insert_am`／`insert_pm`／`url_insert_dot_com`）在 W2-B2 前按了沒反應 | A：全部留給 W2-B2（現況）／B：chain 先實作這三個插字 id，切頁 id 仍交上層 | B（等 §2 裁決後做） |
| B1-2 | chain 的退格在 composing 空時呼叫 `ImeContextProvider.deleteSurroundingText(1, 0)`（契約另有 `sendKey`，選項 B 不必動契約），composing 空時退格會把 emoji（surrogate pair）切成半個 | A：維持現況／B：改送 `KEYCODE_DEL` 交給輸入框處理字素／C：契約加 `getTextBeforeCursor` | B；若要做 emoji 鍵盤前一定要修好，C 較正確但動契約 |
| B1-3 | 契約沒有 `performEditorAction`，單行欄位的「搜尋／前往」做不到 | A：W2-B2 service 在 Enter 進 chain 前攔下／B：契約加方法 | A |
| B1-4 | composing 中按數字 | A：反白候選＋數字一起上屏（現況）／B：數字鍵選第 N 個候選 | A，B 之後可做成設定 |
| B1-5 | `InputProcessor` KDoc 說 Enter 會選候選，`KeyAction` KDoc 說 Enter 送 composing 原文，兩處矛盾 | A：照 `KeyAction`（現況，送原文）／B：照 `InputProcessor` | A，並修正 `InputProcessor` 的 KDoc |
| B1-6 | `Candidate` 沒有「這個候選吃掉幾個音節」的資訊，選字只能清空整個 composing；子包回報 libchewing wrapper 只回游標所在音節的候選，兩者對不上（**未獨立查證**） | A：v1 限制一次只組一個音節／B：契約 `Candidate` 補欄位（contracts-v2） | 先查證 wrapper 行為再決定；這題會影響 W2-A1 |
| B1-7 | 長按由 UI 層把 `longPress.action` 包成新 `KeyData` 再送進 chain | 確認／否決 | 確認 |
| B1-8 | **沒有一聲鍵**：注音配列只有 ˊˇˋ˙ 四個聲調鍵，大千配列的一聲＝空白鍵；但 `KeyAction.Space` 的 KDoc 與 chain 現況都把空白鍵當「上屏候選」，一聲音節無法在 composing 中途結束（W2-D1 的 `ZhuyinText` 與 `bpmf.h` 也都以空白當一聲） | A：空白鍵在「最後一個音節還沒聲調」時當一聲，已有聲調時才上屏候選（同 libchewing 行為）／B：配列另加一聲鍵／C：維持現況 | A；這會改 `KeyAction.Space` 的 KDoc 語意 |
| B1-9 | decoder 拋例外時 chain 的處置（`reset`／`commit` 失敗時是否先清 composing 與 Shift；學習回饋失敗是否擋住上屏） | A：例外原樣拋給 service，service 送 `Reset` 回乾淨狀態（現況）／B：chain 內 catch 並自行回乾淨狀態 | A，等 W2-B2 service 實作時一起定 |
| B1-10 | composition 不是靠選字結束（Enter 送原文、退格到空）時，decoder 收不到任何訊號 | A：chain 在這兩種情況呼叫 `decoder.reset()`／B：維持現況 | A |

### 5b. W2-D1 字串工具回報

| # | 問題 | 選項 | 建議 |
|---|---|---|---|
| D1-1 | 中點（U+00B7、U+2027、U+30FB）要不要當輕聲 | A：不轉（現況）／B：轉成 ˙ | A（U+2027 在臺灣常當間隔號） |
| D1-2 | 輕聲前置寫法（`˙ㄉㄜ`） | A：判不合法（現況）／B：沒有歧義時搬到音節後面 | B，另開小包 |
| D1-3 | 字源相近的漢字（丫、幺、又、刀、儿…） | A：不收（現況）／B：opt-in 寬鬆模式 | A |
| D1-4 | 零寬字元（U+200B、U+FEFF） | A：保留、判不合法（現況）／B：`normalize` 直接刪 | B，先確認 IME 實際會遇到 |
| D1-5 | 簡繁轉換 | A：引入 OpenCC（Apache-2.0、字典數 MB）／B：字對字對照表（一簡對多繁錯誤多）／C：W2 不做 | C，或先定 APK 大小預算再選 A |

### 5c. W2-A1 decoder JNI 回報

JNI glue 擺放已由子包選定（理由見該分支 `docs/devlog/W2-A1-decoder-jni-20261120.md`）：`:decoder` 自己編
`libbpmf_jni.so`，`JNI_OnLoad` 以 `dlopen("libbpmf.so")`＋`dlsym` 取四個函式，decoder-native 一行不改。
放棄的替代方案：把 glue 編進 `libbpmf.so`（要改 #8 的 version script）、AGP prefab（新外部相依）。
**這個做法在 Android linker namespace 下是否成立只能靠實機確認，目前未驗證。**

| # | 問題 | 選項 | 建議 |
|---|---|---|---|
| A1-1 | 字典初始化失敗 | A：建構時丟例外，`:ime` 接住顯示錯誤（現況）／B：回一個永遠空候選的 decoder | A（B 會安靜地壞掉） |
| A1-2 | `close()` 之後再呼叫 | A：`input` 回空、其餘 no-op（現況）／B：丟例外 | A（避免 service 結束時的競態讓鍵盤 crash） |
| A1-3 | `commit` 怎麼找回候選 index | A：和最近一次 `input` 的結果結構比對（現況）／B：`Candidate` 加 id（動契約） | 先 A，W2-A2 合併個人字典時再評估 |
| A1-4 | `bpmf_input()` 只回游標所在音節的候選，DEVPLAN 的「Top-1 精度 ≥ libchewing baseline 的 95%」做不到 | A：擴 C API 支援整句轉換（另開包）／B：把驗收改成逐音節 | 與 §5a B1-6 一起決定 |

---

## 6. 本輪拆包（每支目標 ≤ 450 行，含測試）

| 包 | 範圍 | 基底 | 依賴的裁決 |
|---|---|---|---|
| W2-A1 | `:decoder` JNI binding＋`ZhuyinDecoder` 真實實作＋`DecoderModule` | 疊在 PR #8 上（stacked） | §5a B1-6、§5c A1-4（影響驗收與 `Candidate` 契約） |
| W2-A2 | `:decoder` Room 個人字典 | main | 無（W2-A1 之後再開，避免同時改 `decoder/build.gradle.kts`） |
| W2-B1a | `:ime` processor chain dispatcher（純 Kotlin） | main | 無 |
| W2-B1b | `:ime` 預設 processor（注音／選字／退格／Enter／Shift／直接輸入） | 疊在 W2-B1a 上 | §5a B1-2、B1-5、B1-8、B1-10 會改它的行為；切換鍵與 `Custom` 先回 `Unhandled`（§5a） |
| W2-B2 | `:ime` `InputMethodService`＋`AbstractComposeView` 掛載 | W2-B1 | §1、§1b、§2、§5a B1-1、B1-3、B1-9 |
| W2-B3 | `:ime` `CandidateRow`／`SoftKeyboardView` Compose 元件 | W2-B2 | §3 |
| W2-C | `:settings` | PR #9 合併後 | §3 |
| W2-D1 | `:common` 全形／半形、注音 normalize | main | 無 |
| W2-D2 | emoji／kaomoji 鍵盤 | main | 無（§5b D1-5 簡繁轉換另議，不擋鍵盤本體） |

合併順序建議：#11 → #8 → #9 → W2-D1 → W2-B1a → W2-B1b → W2-A1（rebase 到合併後的 main）→ 其餘。
