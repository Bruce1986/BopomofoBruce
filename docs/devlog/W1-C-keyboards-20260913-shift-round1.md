# W1-C keyboards — 排程深審班（2026-09-13）

自動 PR 深審 routine 對 #10 跑的一輪：一個 reviewer 視角（配列資料正確性／測試強度／
strict 驗證／跨模組介面）＋ parent 複驗。本輪所有結論都在本機實跑驗證過
（`ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew
:keyboards:testDebugUnitTest --rerun-tasks`；一定要加 `--rerun-tasks`，否則拿到的是
build cache 的舊結果，那不是證據）。

基準與本輪後皆為 46 tests / 0 failures——本輪沒有新增測試，改的是兩條既有測試的掃描範圍。

## 一、兩條測試只掃短按，沒掃長按

`reachableChars()` 這個輔助函式的 KDoc 自己就寫明，問「使用者打不打得出這個字」時
必須連 `longPress` 一起算，否則「一旦鍵開始帶 `longPress` 就會漏報」。同一個檔案裡有
四條測試照做了，但底下兩條沒有：

### 1. `symbol keyboard characters are all full-width`（medium）

它只看 `key.action`，不看 `key.longPress?.action`。而**這個 PR 自己就給符號鍵盤加了一個
longPress**（owner 2026-09-08 裁決：破折號 `—` 掛在 `－` 的長按上）。也就是說，這條測試
要擋的「半形字元偷渡進全形標點鍵盤」，現在有一條它看不到的路。

**A/B 實測**（在一個原本沒有 longPress 的鍵上掛半形逗號 `,`）：

| | 結果 |
|---|---|
| 修正後的測試 | **FAILED**（`expected full-width punctuation, got ASCII ','`） |
| 修正前的測試（`git show HEAD:…`） | **46 tests / 0 failures** — 放行 |

目前之所以還沒出事，純粹是既有的那一個 longPress 剛好是非 ASCII 的 `—`。

> 過程中的一個教訓：第一版突變是把現有的 `—` **改成** `-`，結果修正前的測試也紅——
> 但紅的是另一條（`symbol keyboard can type an em dash` 釘死了 `—` 必須可及），不是這一條。
> 換成「新增一個帶 ASCII 的 longPress」才隔離出真正的差異。**突變要打在缺陷會現形的那個
> 形態上，不是任何會讓測試變紅的改法。**

### 2. `url keyboard has slash, dot and a dedicated dot-com custom action`（low，防假紅）

同樣只掃短按。`/` 與 `.` 今天都是短按鍵，所以這條改動**今天是行為不變的**；它防的是
未來把其中一個改成長按可及時的**假紅**——而這個鍵盤的數字本來就已經是長按可及了，
所以「短按才算數」這個隱含前提在這份配列上已經不成立。

**A/B 實測**（把 `/` 改成長按可及、短按換成一個該鍵盤未使用的字元）：

| | 結果 |
|---|---|
| 修正後的測試 | **46 tests / 0 failures** — 正確認得長按 |
| 修正前的測試 | **FAILED**（`url keyboard missing '/' or '.'`）＝假紅 |

> 這裡也踩了一次坑：第一版突變把短按換成 `~`，結果兩邊都紅——因為 `~` 這個鍵盤上已經有了，
> 撞到 `no keyboard has a duplicate Character key`。改用未使用的字元才得到乾淨的 A/B。

## 二、複驗過、**沒有**發現問題的項目

- **注音配列資料**：37 個注音符號（21 聲母＋16 韻母）＋4 個聲調，portrait 與 landscape
  兩份都齊全、無重複、無多餘，位置符合標準大千配列。
- **八份配列無重複鍵**：任一鍵盤內的 `Character`／`Zhuyin`（短按與長按合計）皆無重複——
  已有 `no keyboard has a duplicate Character key` 在守，本輪的第二個突變就是被它抓到的。
- **退化點**：`no keyboard is empty` 已擋住「某份配列整個空掉仍全綠」。
- **恆真／自我印證斷言**：逐條看過，每條「釘住 X」的測試都用寫死的字面期望值，沒有
  拿被測配列自己算出期望值再回頭比對的情形。
- **空白鍵標籤**：六份帶空白鍵的配列全部用「空白」（不是 U+3000），owner 2026-09-08 的
  裁決是**一致**落地的，不是只改了一半。
- **KDoc 裡的列寬總和**：`passwordQwerty`／`urlQwerty` 註解寫的 10/9/9.6/7.6 與
  10/9/9.6/8.8，與 JSON 實際值逐列吻合。
- **跨模組介面（W1-A／W1-B）**：`:decoder` 與 `:theme` 目前都還是單一常數的
  `Placeholder.kt`，`:ime`／`:app`／`:settings` 也還沒有任何程式碼引用 `Keyboards.*`——
  這一項現階段**無從查證**，不是「查過沒問題」。等 `:decoder` 的真實 JNI 綁定落地後
  值得補一次 smoke test。

## 三、仍開著、本輪無新證據的三項

`contentDescription`、間隔號（U+30FB）的選擇、`url_qwerty` 的空白鍵——都是 2026-09-08
就記錄在案、待 owner 裁決的設計題。本輪沒有取得任何新證據，故不重提也不推翻。

---

# 同日第 2 輪（對抗性複審上一輪的修正）

46 → **47 tests / 0 failures**（新增一條哨兵）。

## 一、第四個「規則本身不完備、靠現有鍵盤組合湊巧遮住」

`ReturnPathCoverageTest.destinationsOf()` 是一個寫死的 `when`，只認得
`KeyAction.SymbolToggle` 與 `Custom(SWITCH_TO_ZHUYIN_CUSTOM_ID)` 兩種切頁動作，其餘一律
`else -> emptyList()`。但「哪些 `Custom` id 算切頁鍵」的**權威來源**是
`Keyboards.PAGE_SWITCH_CUSTOM_IDS`（`ToggleFreeKeyboardsTest`／`CustomIdRegistrationTest`
都照那份清單走）。兩邊沒有任何機制互相核對。

**完整情境實測**（照「正確流程」做：登記新 id ＋ 在符號鍵盤掛一顆指向 `phone_dialpad`
的鍵 ＋ 補上 label 一致性表——`phone_dialpad` 是目前確定沒有任何切頁鍵的終端頁之一，
所以那真的是一條有去無回的死路）：

| | 結果 |
|---|---|
| 本輪新增哨兵後 | **FAILED** |
| 本輪之前（`git show HEAD:…`） | **BUILD SUCCESSFUL** — 整條死路被 `else -> emptyList()` 靜靜吞掉 |

這與 M2、round-11／13、以及 09-08 那輪的 custom id 分類是同一種病，只是換到導航圖這一層。
新增的哨兵 `every registered page-switch custom id has a destination in destinationsOf`
把「登記」與「有目的地」綁在一起，並額外斷言目的地真的存在於 `Keyboards.all`。
`GENERIC_BACK_CUSTOM_ID` 是唯一的例外（目的地是動態的來源鍵盤），在測試裡顯式排除並寫明理由。

> 中途的教訓：第一版突變只在 `PAGE_SWITCH_CUSTOM_IDS` 裡加了一個 id、沒有任何鍵去用它，
> 結果兩版都紅——紅的是 `CustomIdRegistrationTest`（「每個登記的 id 都必須被某份鍵盤用到」）。
> **要驗的缺陷是「登記且使用、但導航圖看不到」，所以突變必須把整條路徑鋪完整**，
> 少一步就會被相鄰的守門攔下，量到的是別人的紅、不是自己的。

## 二、`reachableChars()` 的型別盲區（已補 KDoc，不改行為）

它只認 `KeyAction.Character`。而 `KeyAction` 還有兩種能讓文字上畫面的變體：

- `Custom`：**已經有先例**——`INSERT_AM_CUSTOM_ID`／`INSERT_PM_CUSTOM_ID` 插入的就是
  半形的 `"AM"`／`"PM"`。所以未來若在全形符號頁掛一顆插入半形文字的 `Custom` 鍵，
  上一輪才剛強化的全形檢查照樣看不到它。**但這個模組修不了**：id 到插入文字的對應表
  **將**在 `:ime`（W2-B）而不在這裡——目前 repo 裡沒有任何一行程式碼做這個對應，
  `:ime` 還是 Placeholder，AM/PM 只有 KDoc 層級的意圖記載——所以 AM/PM 那條測試才是改掃 id 的。
- `Zhuyin`：帶的是 `String`，而且輸出要經 `ZhuyinDecoder` 才進緩衝區，不是逐字元直達，
  設計上本來就不在範圍內。

現況查證：`symbol_standard.json` 目前唯二的 `Custom` 是 `switch_back` 與 `switch_to_zhuyin`，
都是控制鍵、不插入文字，且該檔沒有任何 `zhuyin` 型別的鍵——**今天不會誤判，但那又是巧合**。
本輪的處置是在 KDoc 寫明這個函式**不是**「所有可觸及字元」的保證，免得下一個人照名字誤用。

## 三、上一輪宣稱的複驗（全部實跑，無誤）

兩組 A/B 各自重跑一次，結果與 devlog 記載一致。注音符號重數：兩份配列各 **41 個**不重複符號
（21 聲母＋16 韻母＝37，加 4 聲調），兩份集合完全相同。空白鍵：8 份配列中 **6 份**帶空白鍵
（`numeric_standard`／`phone_dialpad` 沒有），6 份全部是「空白」文字標籤。列寬總和
`password_qwerty` 10.0/9.0/9.6/7.6、`url_qwerty` 10.0/9.0/9.6/8.8，與 KDoc 逐列吻合。
跨模組：`grep -rn "Keyboards\." ime app settings decoder theme` 零命中。

---

# 同日第 3 輪（Opus tracer，本班收尾）

47 → **49 tests / 0 failures**。**產品碼 0 條**；兩條都是測試邏輯。

## 一、第 2 輪的哨兵只做了單向核對

哨兵走的是「清單 → 導航圖」（`PAGE_SWITCH_CUSTOM_IDS` 裡每個 id 都要有目的地）。
**反方向沒人看**：`destinationsOf()` 認得、卻被登記在 `NON_PAGE_SWITCH_CUSTOM_IDS`
（＝資料層宣稱「這顆鍵不會帶你去別的鍵盤」）的 id。

**完整情境實測**（新增一個切到 `symbolStandard` 的 id、在 `destinationsOf()` 補好分支、
在 `datetime_standard` 掛一顆鍵、補 label 表，**但登記在錯邊**）：

| | 結果 |
|---|---|
| 本輪新增反向斷言後 | **FAILED** |
| 本輪之前（同樣補了分支） | **BUILD SUCCESSFUL，47 tests 全綠** |

此時 `datetime_standard` 真的多了一顆會換頁的鍵、導航圖也同意它會換頁，只有資料層說它不是
⇒ `ToggleFreeKeyboardsTest` 把 datetime 當終端頁、`Keyboards.kt` 的「終端頁是這三份」KDoc
就此變成錯的，而**沒有任何一條測試紅**。`ToggleFreeKeyboardsTest` 對「登記錯邊」的既有防線
是三條寫死的 per-id 斷言，對**新增的** id 結構性無效；新的反向斷言用走訪取代列舉。

## 二、排除 `GENERIC_BACK` 之後就沒人再對它問過任何事

排除本身是對的——`destinationsOf()` 只吃 `KeyAction`、沒有「這顆鍵在哪份鍵盤上」的脈絡，
算不出「所有會切到它所在鍵盤的來源」。**但排除之後，一整類缺陷會無聲落地**：把「返回」掛在
一份**誰都切不到**的鍵盤上，使用者是靠 InputType 直接進來的，`:ime` 沒有來源狀態可回，
那顆鍵在真機上是 no-op。

**實測**（在 `url_qwerty` 加一顆 `switch_back`——`url_qwerty` 不是任何 `destinationsOf()`
的目的地）：

| | 結果 |
|---|---|
| 本輪新增後 | **FAILED** |
| 本輪之前 | **BUILD SUCCESSFUL，47 tests 全綠** |

處置是**換一個問題問**而不是完全不問：帶返回鍵的鍵盤，必須是某顆切頁鍵的目的地。

## 三、另修兩處文件精確性

- 哨兵的失敗訊息只給了兩個選項（補分支／宣稱動態），但還有**第三種**情況：目的地是固定的、
  但那份鍵盤還不在 `Keyboards.all` 裡（`language_toggle` 的通用 ABC 頁、W2-D 的 emoji 頁）。
  照原訊息做，只能在排除清單上留一條指不出根據的例外。已把第三種寫進訊息。
- `reachableChars()` 的 KDoc 把「將來會在 `:ime`」寫成現在式。實查：
  `grep -rn` 那幾個 custom id 於 `ime`／`app`／`settings`／`decoder`／`theme`／`common`
  **零命中**，`:ime` 仍是只有一個常數的 Placeholder。AM／PM 插入文字這件事目前**只有
  KDoc 層級的意圖記載、沒有任何一行程式碼**。已改成未來式並寫明現況。
  （`Zhuyin` 要經 `ZhuyinDecoder` 那句**有出處**：介面在 `:common`，不受 `:decoder` 仍是
  Placeholder 影響。）

## 四、merge commit `6b12216` 的衝突解法已複驗

tracer 逐段比對：`git diff origin/main 6b12216 -- WORKLOG.md` **0 行刪除**，
`git diff 6041b45 6b12216 -- WORKLOG.md` 也 **0 行刪除**；`docs/STATUS.md` 與
`docs/HANDOVER-W1-fixloop-20260811.md` 與 `origin/main` 逐位元組相同。兩側內容都在，無遺失。

## 五、停止判斷

**本班到此收手。** 依據是 tracer 的判斷，不是「連續兩輪 clean pass」：本班三輪
（`git diff e6a421d..` 實查）**沒有動到任何一行產品碼**，全部落在測試鷹架與文件；
而 F1／F2 已經是同一個母題（「規則本身不完備、靠現有鍵盤組合湊巧遮住」）的第 5、6 次分身，
再開一輪很可能只是在新補的那條規則上再找一個沒覆蓋到的角落。
