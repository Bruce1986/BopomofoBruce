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

---

# 第 2 輪（對抗式複審 round 1 自己的修正）

沙箱同步到 `fec6621` 後跑了 5 條突變，round 1 的三處修正**都如預期轉紅、且互為備援**——
其中一條突變（把 `hasGenericBack` 退回舊版、同時把 `switch_back` 移到錯的一邊）證實
`ToggleFreeKeyboardsTest` 的兩條新測試是獨立防線，不依賴 `ReturnPathCoverageTest` 那側的修正。

本輪處理兩條：

## A. `javaClass.classLoader` 少一個 `?.`（round 1 自己寫的）

`KeyboardLoaderTest` 新增那條裡寫的是 `javaClass.classLoader.getResource(...)`，而
`classLoader` 的型別是 `ClassLoader?`。編譯器只降級成警告（nullability 來自 JDK 的
enhanced signature，非嚴格模式下不是 error），`ktfmtCheck` 又只管格式不管型別，所以
一路綠燈過關。但 null 時丟的會是**沒有訊息的裸 NPE**，而不是同一行 `requireNotNull`
準備好的那句話；同一個 repo 的 `KeyboardLoader.loadFromResource` 處理同一種情境用的正是
`?.` ＋明確錯誤訊息——等於 round 1 違反了自己檔案裡示範的模式。已補上 `?.`，編譯警告消失。

## B. `isPageSwitchAction` 的「未登記 id 大聲失敗」分支沒有直接測試

那個 `throw` 是 M2 的核心產物（未登記的 `Custom` id 要大聲失敗，而不是靜默當成非切頁鍵），
但在此之前只能被「常數打錯字」之類的意外**間接**踩到。round 1 既然已經在寫合成鍵盤測試，
順手補第三個案例：帶一個完全陌生 id 的合成鍵盤 ＋ `assertThrows`，並斷言錯誤訊息有點名
是哪一個 id。

突變驗證：把該分支改成靜默放行（＝M2 修正前的行為）→ **只有新加的這條轉紅**。

42 tests / 0 failures（round 1 後為 41），`ktfmtCheck` 綠。

## 已查證、疑慮不成立（記錄下來免得下次重查）

- **「resources 被打包成 jar 之後，`File(dirUrl.toURI())` 會爆掉或靜默測不到東西」**——不成立。
  實測 `:keyboards:testDebugUnitTest` 底下 `getResource("keyboards")` 解析到
  `.../build/intermediates/java_res/debug/processDebugJavaRes/out/keyboards`，是真實檔案系統
  目錄（`file:` scheme），`listFiles()` 正常走訪；`processReleaseJavaRes` 的產物也是同樣結構。
  AGP 的 unit test task 本來就是走「解壓後的目錄」而非 jar，CI 跑的 `testDebugUnitTest` 與此一致。

## 本輪未改（已評估，維持原判）

- `ReturnPathCoverageTest` 在分類錯邊時的失敗訊息確實會誤導（說「沒有返回路徑」，實際是分類錯），
  但同一個突變下 `ToggleFreeKeyboardsTest` 的兩條新測試一定同時紅，且訊息精準指出分類問題。
  **殘留風險**：那兩條寫死目前三個常數，若日後新增第四個 `Custom` id 並登記錯邊，就只剩那句
  容易誤導的訊息單獨紅——新增 `Custom` id 時請一併在「clean partition」那條補一行斷言。
- `the language_toggle scope note...` 哨兵的**條件**（`Keyboards.all.size == 8`）確實與
  `KeyboardLoaderTest` 既有兩條重複，價值完全來自訊息文字。但突變實測（加入 `abc-generic`）
  顯示 4 條一起紅時，**只有這一條**提到 `LanguageToggle` 與 `destinationsOf()`，其餘三條都只說
  「目錄變了」。更精準的寫法需要資料層有「這份鍵盤是不是 LanguageToggle 目的地」的標記，
  而 `KeyboardDef` 沒有這種欄位——成本明顯超過這個 trip-wire 的簡單有效，維持現狀。

---

# 第 3 輪（Opus tracer 全案最終掃描）

## 🔴 critical：本班 round 2 補的那道守門，在真實鍵盤上從來不會觸發

round 2 補了「未登記的 `Custom` id 要大聲失敗」的直接測試，並在 KDoc 兩處宣稱
`ToggleFreeKeyboardsTest` 的 `throw` 就是這條守門。**兩句都是假的。**

`isToggleFree` 是 `rows.flatten().none { … actions.any { … } }`——`none` 與 `any` **兩層都會短路**。
只要那份鍵盤上較早的某顆鍵已經是切頁鍵，走訪就停了，後面的鍵根本不會被送進
`isPageSwitchAction`，那個 `throw` 自然永遠到不了。攤平後的實際順序：

| 鍵盤 | 第一顆切頁鍵的 index | 總鍵數 | 後果 |
|---|---|---|---|
| `symbol_standard` | 30（`返回`） | 36 | index 30 之後全部不檢查 |
| `url_qwerty` | 28（`全形`） | 34 | `url_insert_dot_com` 在 index 32，**從來沒被檢查過** |

**端到端重現**（本輪實跑）：在 `symbol_standard` 加一顆 `半形` →
`Custom("switch_to_halfwidth")`，插在既有控制鍵之後（最自然的位置）——第一次跑
`ToggleKeyLabelActionConsistencyTest` 會紅；接著**照它的失敗訊息**把 `半形` 加進標籤表，
於是 **42 條全綠**，而那顆真正的切頁鍵被靜默當成「不是切頁鍵」。也就是說：開發者完全照著
測試的指引走，就會走進 M2 存在要防的那個誤判。

round 2 的測試之所以蓋不到，是因為它的合成鍵盤把未登記的 `Custom` 放成**唯一**的切頁鍵
——正是短路唯一咬不到的那種排列。**它可以被突變殺掉（round 2 驗過），卻證明不了真實目錄
的任何事。**這與整個工作包一路在追的失效形狀完全相同（規則本身沒錯、被現有配列的長相遮住），
只是這次出現在「修那個失效」的修正裡面。

## 🟠 high：常數與 JSON 是兩個獨立來源，其中兩個改錯字完全靜默

round 1 抽出的三個 `const val` 與 JSON 裡的字面字串沒有任何測試對照過。兩個方向實測：

| 突變 | 結果 |
|---|---|
| `GENERIC_BACK_CUSTOM_ID` 改錯字（JSON 不動） | 紅（但只是**碰巧**——`返回` 剛好是短路發生前的第一顆） |
| `SWITCH_TO_ZHUYIN_CUSTOM_ID` 改錯字（JSON 不動） | **42 條全綠** |
| `URL_INSERT_DOT_COM_CUSTOM_ID` 改錯字（JSON 不動） | **42 條全綠** |

IDE 的 rename symbol 就會造成這種單邊改動：Kotlin 那側全改了、JSON 字串沒動，而 `:ime` 是依
常數 dispatch 的——那顆鍵在執行期直接變 no-op，測試全綠。

## 修法：一條不短路的專屬測試，同時關掉上面兩個洞

新增 `CustomIdRegistrationTest`，**逐一走訪、不短路、不依賴任何鍵位順序**，兩個方向各一條：

1. `Keyboards.all` 裡用到的每個 `Custom` id 都必須登記在兩份清單其中一邊，且只在一邊。
2. **反向**：登記了的每個 id 都必須真的有鍵盤在用（這條才擋得住常數／JSON 分岔）。

突變驗證：

| 突變 | 修正前 | 修正後 |
|---|---|---|
| 加未登記的切頁鍵＋補標籤表 | 42 全綠 | 紅 1（第 1 條） |
| `SWITCH_TO_ZHUYIN_CUSTOM_ID` 改錯字 | 42 全綠 | 紅 2（兩條都紅） |

並修掉 KDoc 兩處被證偽的宣稱，改成講明「那個 `throw` 現在只是備援，不是守門」。

## 其他

- **刻意不把測試裡的字面字串改成常數**（tracer 提到有四處）：那些字面值正是**獨立錨點**
  ——全部改用常數的話，「常數改了、JSON 沒改」會兩邊一起變而沒人發現，第 2 條測試就沒有
  東西可以對照了。已把這個取捨寫進 `GENERIC_BACK_CUSTOM_ID` 的 KDoc，並更正它原本寫錯的
  使用點列舉（宣稱三處，實際五處）。
- 正式 KDoc 裡寫死的「37 條測試」會隨每輪漂移，已改成不寫死條數（數字留在 devlog）。
- `round5.md` 兩處「唯一終端頁」是 L1 已推翻的舊事實，該檔別處有更正註記的慣例卻漏了這兩處，
  已補上——先讀到 round5 的人本來會拿到過期的結論。
- `.kotlin/`（Kotlin compiler session 檔）未被 `.gitignore` 涵蓋，本班約 20 次 gradle 就多出 5 個
  檔案，`git add -A` 有機會把它們 commit 進去。已加一行。

## tracer 已驗證、沒有問題的部分

- 本班新測試（round 1 四條、round 2 一條、round 3 兩條，共**七條**）**全部可被突變殺掉**，原有 37 條抽驗 5 條亦然（唯一例外是
  `every key…has a positive finite weight`，它由 `KeyData.init` 保證、結構上不可能失敗，
  且測試自己的註解已誠實說明）。
- **合成 `StaticKeyboardDef` 與 JSON 載入路徑沒有行為分歧**：10 種形狀（空 rows、空 row、
  空白 id、單獨的 high surrogate、重複 label、`Float.MAX_VALUE` / 次正規 weight 等）全部
  round-trip 一致、都不丟例外。`KeyboardLoader` 的嚴格性只在於 `ignoreUnknownKeys=false`，
  而「未知欄位」在 Kotlin 建構子這側根本無法表達。
- **`Keyboards.all` 一次載入 8 份的成本**：8 份 JSON 共 39,828 bytes，冷啟動全載
  **3.57 ms**（第一份 0.82 ms 含 serializer 初始化，其餘 7 份約 0.39 ms/份），一個 process 一次。
  且每份鍵盤各自 `by lazy`，`:ime` 可以只碰需要的那一個；目前 `Keyboards` 在 `:keyboards` 之外
  **零消費者**，這是純前瞻性的注意事項。（桌面 JVM 數字，非 ART 實機。）
- `ZhuyinLayoutContentTest` 的「37 個注音符號齊全」那條自己把 `ㄦ` 補進集合，所以對 `ㄦ` 不可能
  失敗——但註解誠實說明了，且刪掉 `ㄜ` 的 longPress 會被另外兩條抓到，無需處理。

---

# 第 4 輪（最後一輪：對抗式複審 round 3）

**round 3 的核心主張全部通過獨立重算與突變重現**，沒有 critical/high：`none`/`any` 雙層短路的
Kotlin 語意、index 30／28／32、8 份 JSON 共 39,828 bytes 都逐一核對相符；tracer 另外把未登記的
`Custom` 掛在**短路點之後**的 Enter 鍵 `longPress` 上，`CustomIdRegistrationTest` 正確抓到。
也確認 `ToggleFreeKeyboardsTest` 的「clean partition」與 `CustomIdRegistrationTest` 第一條
**不是重複**——把 id 登記錯邊（仍登記、仍有人用）時只有前者會紅，兩者測的是互補性質。

本輪修四條文件層問題：

1. **round 3 更正的「使用點列舉」自己又漏了一個檔案。**我把「三處」改成「五處」，卻沒把
   `ToggleFreeKeyboardsTest` 算進去——它在三個測試方法裡對這三個常數共 5 次直接引用。
   改法不是再數一次，而是**不寫死數量**（同一份 KDoc 早就對「37 條測試」做過同樣的處理，
   這次卻在隔壁段落又寫了一個會漂移的數字）。
2. **「獨立錨點」的代價沒揭露。**KDoc 只寫了它的好處。實測：一次**合法**的協調式改名
   （常數與 JSON 一起改）會讓 `ToggleKeyLabelActionConsistencyTest` 紅一次，因為它的標籤表
   寫死的還是舊字面值。那不是缺陷，是這個設計刻意要的那一次人工同步——但要寫出來。
3. **`CustomIdRegistrationTest` 第二條會對「W2-B 預先登記」誤紅，錯誤訊息卻只給兩個成因。**
   實測把一個假想的 `switch_to_generic_abc_w2b` 加進清單（模擬先登記、JSON 之後補），
   該條立即紅而訊息叫人去查「分岔」或「遺留鍵」——兩個都不是真正的成因。已補上第三種成因，
   並在 KDoc 說明「本條刻意要求登記與使用同時落地」這個限制。
4. **devlog 的「本班五條新測試」與實際對不上**：逐 commit 數過是 4＋1＋2＝**七條**。
   已更正。（五條是 round 3 tracer 當下的數字，round 3 自己又加了兩條之後就過期了。）

## tracer 標記為「無出處」的兩則（記錄下來，不是問題）

devlog round 3 段落引用的「冷啟動 3.57 ms」與「10 種形狀 round-trip 一致」，在 repo 裡找不到
對應的可重跑腳本——它們是 tracer 在自己的 probe worktree 裡量的，probe 已清掉。與本 repo
既有的「另開 probe worktree」作法一致，但**這兩個數字無法從 repo 重現**，引用時請注意。
