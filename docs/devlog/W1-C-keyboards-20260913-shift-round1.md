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
  上一輪才剛強化的全形檢查照樣看不到它。**但這個模組修不了**：id 到插入文字的對應表在
  `:ime` 而不在這裡，所以 AM/PM 那條測試才是改掃 id 的。
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
