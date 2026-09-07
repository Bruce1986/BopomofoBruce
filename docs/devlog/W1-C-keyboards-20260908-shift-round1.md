# W1-C keyboards — 排程深審班 round 1（2026-09-08）

自動 PR 深審 routine 對 #10 跑的一輪：兩個並行 reviewer 視角（配列資料可用性／Kotlin
程式碼與突變測試）＋ parent 自審（契約與文件一致性）。**本輪所有結論都在本機實跑驗證過**
（`ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :keyboards:testDebugUnitTest
--rerun-tasks`；一定要加 `--rerun-tasks`，否則拿到的是 build cache 的舊結果，那不是證據）。

基準：37 tests / 0 failures。本輪後：41 tests / 0 failures。

## 一、🔴 critical：Custom id 分類「登記錯邊」完全沒有守門

`Keyboards.PAGE_SWITCH_CUSTOM_IDS` / `NON_PAGE_SWITCH_CUSTOM_IDS` 的 KDoc 宣稱「這兩份清單
合起來必須涵蓋每一個 `Custom` id，`ToggleFreeKeyboardsTest` 對兩邊都沒登記的 id 直接判定失敗」。
那句話只對**一半**的錯法成立。

**突變實測**：把 `"switch_back"` 從 `PAGE_SWITCH_CUSTOM_IDS` **移到**
`NON_PAGE_SWITCH_CUSTOM_IDS`（＝資料層宣稱「這顆鍵不會帶你去別的鍵盤」）——
**BUILD SUCCESSFUL，37 tests / 0 failures**。

兩邊都剛好看不到它：

- `ToggleFreeKeyboardsTest` 釘的是「哪幾份鍵盤是終端頁」，而 `symbolStandard` 另外還掛著
  `switch_to_zhuyin` 與 `language_toggle`，少算一顆不改變它的終端頁判定。
- `ReturnPathCoverageTest` **根本不看這兩份清單**，自己另外寫死了一次字面字串 `"switch_back"`。

也就是說：資料層說它不切頁、導航圖說它切得回去，兩邊矛盾而沒有任何一條測試會紅。這與
M2（round-11／13）修的是同一種失效——規則本身不完備，靠現有鍵盤組合湊巧遮住——只是換到
分類這一層。風險不是假設性的：`switch_back` 就是設計成給任何鍵盤重用的通用逃生口，一旦
出現「唯一的切頁鍵是 `Custom`」的鍵盤（例如已登記為 W2-B follow-up 的半形符號頁），
誤分類就會讓它被判成終端頁。

**修法**（三處）：

1. 把三個 `Custom` id 抽成 `Keyboards` 的 `const val`，讓資料層與測試共用一個來源。
2. `ReturnPathCoverageTest.hasGenericBack` 改成**同時**要求該 id 登記在
   `PAGE_SWITCH_CUSTOM_IDS` 裡，不再自己比對字面字串。
3. `ToggleFreeKeyboardsTest` 新增兩條：分類本身的守門（兩集合互斥＋三顆鍵各自登記在對的
   一邊），以及一份**合成鍵盤**——「唯一的切頁鍵是 `Custom`」與「只掛非切頁 `Custom`」各一，
   讓規則不再依賴 `Keyboards.all` 目前剛好長什麼樣子。

**修正後同一個突變**：3 條轉紅（`ReturnPathCoverageTest` 的返回路徑那條、新增的兩條）。

## 二、🟠 medium：`language_toggle` 的「暫不檢查」寫成了會靜默失效的形狀

`ReturnPathCoverageTest.destinationsOf()` 對 `LanguageToggle` 走 `else -> emptyList()`，
class KDoc 的 Scope note 解釋理由是「目的地不在 `Keyboards.all` 裡，沒有東西可以比對」。
這個理由**是暫時的**：W2-B 就會把通用 ABC 鍵盤加進來，而 `emptyList()` 到那天不會報錯，
只會**靜靜地跳過** `language_toggle` 這條邊。

**probe 實測**：另開 worktree，把一份完全沒有返回鍵的 `abc-generic` 加進 `Keyboards.all`
（＝W2-B 之後 `language_toggle` 的目的地）——`ReturnPathCoverageTest` **依然 0 failures**。
紅的是 `KeyboardLoaderTest` 兩條（目錄數量 8→9、id 命名清單）與 `ToggleFreeKeyboardsTest`
的終端頁那條。三條訊息都只說「目錄變了」，沒有一條會讓人回頭想到返回路徑。

所以加鍵盤的人**確實會被擋**，但擋他的訊息不會告訴他「有去無回」這一整類缺陷正從
`language_toggle` 這條邊回來。修法是補一條哨兵測試，訊息直接寫明「若新增的是
`LanguageToggle` 的目的地，請先把它接進 `destinationsOf()`」。同一個 probe 突變下，
這條會紅。

## 三、🟠 medium：孤兒配列 JSON 會被打包，而且沒有任何測試看它

七支測試檔都是走訪 `Keyboards.all`，但真正被打包的是 `resources/keyboards/` 底下**所有**
檔案。兩者不是同一個集合。

**probe 實測**：把一份 schema 不合法（多一個 `bogusField`）的 JSON 放進 `resources/keyboards/`
但**不**登記進 `Keyboards.all`——37 條測試**全數通過**。`KeyboardLoader` 那個「刻意嚴格、
typo 會在載入時大聲失敗」的契約對它完全沒有作用，因為從來沒有人載入過它。

修法：`KeyboardLoaderTest` 新增一條，同時守兩件事——檔案集合與目錄一致（新增配列卻忘了
登記會紅），且每一個被打包的檔案都真的通得過嚴格 schema（孤兒也逃不掉）。同一個 probe
突變下，這條會紅。

## 四、🔴 high（資料層）：只存在於長按的功能，沒有任何地方要求 `:ime` 把它畫出來

有幾個功能**只存在於長按**且沒有替代入口：`password_qwerty` / `url_qwerty` 的數字 0–9 與
16 個半形符號（短按那層只有 26 個小寫字母）、`numeric_standard` 的負號、注音的 `ㄦ`。

內容測試（例如「password 鍵盤打得出每一個數字」）驗的是**資料層可不可達**，不是**使用者
知不知道**。若 `:ime` 只把長按做成「會動」而不畫提示，第一次使用的人在密碼欄位看到的就
只有一排小寫字母——測試全綠，功能等於不存在。全 repo grep（`keyboards/`、`docs/devlog/`、
DEVPLAN 的 W2-B 驗收標準）都**沒有**任何地方要求 renderer 顯示 `longPress.label`。

`:common` 的 `LongPressData.label` 本來就是為了被畫出來才存在的，不需要改 schema。已在
`Keyboards` 的 object KDoc 寫成**契約**（不是建議），並列出受影響的鍵盤。

## 五、🟡 low（資料層）：直向注音打不出任何數字，而且跨兩個檔案才看得出來

`zhuyin_4x10_portrait` 沒有數字鍵，而它唯一走得到的逃生口 `symbol_standard` 的 30 個字元裡
也**沒有**數字（全形或半形都沒有）——所以「我今年30歲」這種句子在直向下打不完，
`language_toggle` 的目的地又要等 W2-B。橫向因為多一列數字快捷鍵反而打得出來：**轉個方向
就多一個功能**，而使用者沒有理由想到要轉方向。

單看任何一份 JSON 都不會發現這件事，所以寫進 `zhuyin4x10Portrait` 的 KDoc，並記下最小
止血方案（在 `symbol_standard` 補全形 `０`–`９`——它已經是直向唯一到得了的地方）。
**未實作**：那是配列內容的設計決定，留給 owner。

## 已驗證、沒有問題的部分

- 突變矩陣共 10 條（reviewer 跑的 9 條＋反向 2 條）：schema 嚴格度、`switch_back` 未登記、
  刪掉 `switch_back` 鍵、label≠char、注音符號重複、拿掉負號 longPress、拿掉數字 longPress、
  切換鍵標籤錯配——全部如預期轉紅；兩條「合法改動」（新增一顆合規的鍵、同列換順序）
  正確保持綠，沒有過度綁死實作。
- 注音 36 個 U+3105–U+3129 符號＋4 聲調齊全，`ㄦ` 掛在 `ㄜ` 的 longPress 上（桌機大千放在
  `-` 鍵，40 格塞不下，是已揭露的擺放決定），且有專屬測試。
- 短按／長按無字元衝突、label 與 action 一致、無多碼位 `char`——皆以腳本全掃驗證。
- `KeyboardLoader` 的 `.use{}` 位置正確、無資源洩漏；`getResourceAsStream` 回 null 的分支有測試。
- 與 `origin/main` `merge-tree` 零衝突；落後 main 的 5 個 commit 只動 `WORKLOG.md` /
  `STATUS.md` / HANDOVER 三份文件，不含程式碼，所以「合併結果」的測試結論與本分支相同。
- `./gradlew :keyboards:ktfmtCheck` 綠（本輪新增的程式碼已跑過 `ktfmtFormat`）。

## 留給 owner（本班未動）

1. `implementation(project(":common"))` vs `api`（HANDOVER §3 第 4 項，兩位驗證者判斷相反）
   ——**證據支持維持 `implementation`**：`:keyboards` 的唯二消費者 `:ime` 與 `:app` 都已經
   各自宣告 `implementation(project(":common"))`，所以現況可編譯；repo 內五個模組也都是這個
   慣例。改 `api` 唯一的好處是「未來的消費者不必自己宣告」，但那與現有慣例相反。
2. 空白鍵 label 沿用 U+3000（同上第 4 項）——**空白鍵會渲染成完全空白**，而 `:common` 的
   `KeyData` 只有 `label`、**沒有** contentDescription 之類的無障礙欄位，所以整個鍵盤上最大的
   一顆鍵對 TalkBack 沒有任何可讀內容。兩個方向：改成看得見的 label（`空白` 或 `␣` U+2423），
   或在 W2 於 `:common` 補無障礙欄位（`:common` 是 contracts-v1、已凍結，不在本 PR 範圍）。
3. `symbol_standard` 缺破折號 `—`（U+2014）；目前只有全形連字號 `－`（U+FF0D），兩者語意不同
   （「他忽然停下——像是想起了什麼」用的是前者）。屬配列內容決定。
4. `symbol_standard` 的間隔號用 `・`（U+30FB，片假名中點）而非 `‧`（U+2027）。**無出處**：
   班規禁止上網查證，未能確認臺灣慣例，僅提出待查。
5. `phone_dialpad` 的 `,` / `-`（掛在 `#` / `+` 的 longPress）是否會被目標欄位的
   `InputFilter` 吃掉。**無出處**：同上，需對實際 `inputType` 實測。
6. `url_qwerty` 有一顆 weight 2.6 的空白鍵，但 URI 欄位不接受字面空白。
7. `datetime_standard` 沒有 AM/PM（也沒有任何字母），若要支援 12 小時制自由輸入需補。
8. `.kotlin/` 是 Kotlin build 產生的目錄，未被 `.gitignore` 涵蓋，跑過 build 之後會一直出現在
   `git status` 裡。不在本 PR 範圍，但值得順手處理。
