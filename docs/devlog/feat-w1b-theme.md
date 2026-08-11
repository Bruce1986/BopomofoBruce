# W1-B devlog — `:theme` 主題引擎

- 分支：`feat/w1b-theme`
- 期間：2026-08-10（單一子代理 session）

## 交付

- 實作 `:common` 契約介面 `KeyboardTheme`（唯讀，未改動 contracts-v1）。
- `StyleSheet` schema（`theme/src/main/kotlin/com/bopomofobruce/theme/style/`）：
  複用 `:common` 已凍結的 `KeyboardColors` / `KeyboardDimens`，新增 `:theme`
  自己擁有的 `KeyboardShapes`（圓角）、`KeyboardTypography`（字級/字重），全部
  kotlinx.serialization `@Serializable`。
- 三個內建主題（`theme/src/main/kotlin/com/bopomofobruce/theme/BuiltInThemes.kt`
  / `MaterialYouTheme.kt`）：
  - `LightTheme` / `DarkTheme`：固定色盤（Material 3 baseline 近似值）。
  - `MaterialYouTheme`：Android 12+（API 31）用
    `androidx.compose.material3.dynamicLightColorScheme` /
    `dynamicDarkColorScheme` 讀系統桌布色；<31 退化為 `LightTheme` /
    `DarkTheme` 色盤，id 仍回報 `"material-you"`。`from()` 拆成兩支 overload，
    讓退化分支可在純 JVM unit test 驗證（見下方「踩雷 / 決定」）。
- 自訂相片背景：`PhotoBackground`（`theme/.../photo/PhotoBackground.kt`，uri /
  blurRadiusDp / opacity / tint）+ `PhotoBackgroundLayer`
  （`photo/PhotoBackgroundLayer.kt`）用 Coil `AsyncImage` 載入、
  `Modifier.blur()` + `.alpha()` + `ColorFilter.tint(..., BlendMode.SrcAtop)`
  套用。
- Compose `@Preview`：`theme/.../preview/ThemePreviews.kt` 四個預覽
  （Light / Dark / Material You 退化路徑 `apiLevel = 30` / Material You 動態取色
  路徑 `apiLevel = 35`）共用一個 `ThemeSwatch` 假鍵盤列 render。**G2 後**：候選列
  只有第一個候選套 `candidateHighlight`、其餘留在 `background`（契約語意是「標示
  cursor 位置」，不是整列都選中）；按鍵列多一顆 `keyAccent` 底色的功能鍵（模擬
  ⌫），是全模組唯一引用 `keyAccent` 的渲染程式碼。
- unit test 全綠（style round-trip、validation、photo round-trip、color
  round-trip、內建主題對比度守門、MaterialYouTheme 退化分支與值語意、動態取色
  選色函式 `pickAccentColor`）。**條數不在此處寫死**（第十二輪審查，I3：曾經在「交付」/「驗收結果」/
  各輪紀錄三處各寫一個數字、每輪都要手動同步卻每輪都漏），實際條數與最新一輪的紅綠驗證結果見本檔
  最新一輪紀錄（目前最新：下方「2026-08-11 第十四輪」）。

## 驗收結果

| 項目 | 結果 |
|---|---|
| 三主題各有 `@Preview` | ✅ 過（`LightThemePreview` / `DarkThemePreview` / `MaterialYouThemeFallbackPreview` + `MaterialYouThemeDynamicPreview`，共 4 個；B14 後 Material You 拆成兩條路徑各一個）。證據等級：函式存在且編譯通過，未實際在 Android Studio 內 render 過。 |
| 主題序列化/反序列化 round-trip test | ✅ 過（`StyleSheetSerializationTest`、`PhotoBackgroundTest`，含巢狀 `UIntHexSerializer`） |
| `./gradlew :theme:assembleDebug` | ✅ 過 |
| `./gradlew :theme:testDebugUnitTest` | ✅ 全綠（條數見各輪紀錄，第十二輪審查 I3 後不在此處寫死絕對條數，避免每輪手動同步漏更新——見下方最新一輪「2026-08-11 第十四輪」） |
| `./gradlew :theme:ktfmtCheck` | ✅ 過（`BUILD SUCCESSFUL`；期間跑過 `:theme:ktfmtFormat` 修過格式後才綠——含 2026-08-10 B12/B14/B15 修正後、最後一次 commit 之後重跑的結果） |
| `./gradlew :theme:lint` | ✅ 過（`BUILD SUCCESSFUL`，`lint-results-debug.txt`：`No issues found.`——2026-08-10 B12/B14/B15 修正後、最後一次 commit 之後重跑的結果） |
| PhotoBackground 實機渲染 < 200 ms | ❌ **沒有量測**——沒有連上 Pixel 6 / 任何實機做這項；本 session 只跑到 JVM unit test 與 AGP 編譯層級，誠實回報未驗證，不編數字。 |

## 踩雷 / 決定

- 第一輪 `StyleSheetSerializationTest` 對 hex wire format 的斷言字串少寫了
  alpha byte（斷言 `"0x1E1E1E`、實際輸出 `"0xFF1E1E1E`），JVM unit test 直接
  跑紅抓到，修正斷言字串後綠。記錄這條是提醒自己：連「順手加的字串斷言」都要
  真的核對過 wire format，不能憑印象。
- 原本擔心 `androidx.compose.ui.graphics.Color` / `toArgb()` 在沒有
  Robolectric 的純 JVM unit test 下會因缺 Android runtime 而炸掉；實測
  `ColorConversionsTest` 直接綠燈通過，Compose UI graphics 這層的 sRGB 轉換是
  純 Kotlin 數學運算，不需要 Android framework stub。
- `MaterialYouTheme.from()` 刻意把 SDK 等級開成可注入的參數，而不是內部直接讀
  `Build.VERSION.SDK_INT`，只為了讓 <31 退化分支可測。
  **round-2／round-3 審查後的最終形態**（原本是「單一函式 + `sdkInt` 帶預設值」，
  但那讓正式呼叫端也能傳入與裝置不符的假值、把 <31 裝置推進 `@RequiresApi(S)`
  路徑而崩潰）：拆成兩支 overload —
  - `fun from(context, darkMode)`：正式入口，SDK 等級一律取自 `Build.VERSION.SDK_INT`，無法被覆寫。
  - `internal fun from(context, darkMode, sdkInt)`：測試專用，標 `@VisibleForTesting`。
    用 `internal` 而非只掛註解，是因為 `@VisibleForTesting` 只會產生 lint 警告、
    本專案沒開 `warningsAsErrors`，擋不住模組外部呼叫；`internal` 才是編譯期強制，
    而同 module 的 unit test source set 仍呼叫得到（已實測編譯與全部測試皆過，見上方驗收表）。

  >=31 呼叫
  `dynamicLightColorScheme(Context)` 需要真系統資源，這條分支沒有
  Robolectric、也沒有連實機驗證，留在 `MaterialYouTheme` KDoc 與本檔明說。
- 事後審查抓到 `PhotoBackgroundLayer` 的 KDoc 原本寫「`Modifier.blur()` 在
  API<31 用軟體 fallback，這層不需要自己分支」——**這句是錯的**。
  `Modifier.blur()` 在 API<31 是純粹的 no-op（沒有任何模糊效果），Compose
  沒有軟體 fallback 這種東西，`RenderEffect` 硬體合成只在 API 31+ 存在。本模組
  `minSdk = 28`，代表 28–30 的裝置在修正前會「看起來設了模糊、實際上完全沒
  模糊」且沒人發現。已修正：(1) KDoc 改寫成誠實描述限制；(2) 程式碼加
  `Build.VERSION.SDK_INT >= 31` 判斷，<31 時不套 `.blur()`、只疊
  `.alpha()` / `tint` 當降級效果；(3) `AsyncImage` 加 `onError` 記 log，圖片
  載入失敗時至少可觀測、且不擋住底層主題色。

## 2026-08-10 獨立驗證回合（B12/B14/B15/B19）

- **B12（誠實文件修正，非行為改動）**：`PhotoBackgroundLayer.kt` KDoc 原本寫
  `SrcAtop` tint「保留原圖亮度層次，不是整片蓋純色」——這句在最常見輸入下是
  **錯的**。從合成公式推導：`SrcAtop` 的結果是「用 tint 的 alpha 混合 tint 顏色
  與底圖」，tint 的 alpha 就是疊色強度本身；使用者從一般調色盤挑色時 alpha 常
  是 `0xFF`（不透明），此時整張相片會被蓋成一塊純色矩形，完全看不到底圖。
  `PhotoBackground.tint` 的 `init` 沒有限制 alpha，KDoc 原本也只說「疊加色」沒
  講清楚語意。**決定：不改 `BlendMode`**（換成別的混色模式或加 alpha 上限
  `require` 是產品/視覺決策，超出修文件 bug 的範圍）——已改寫
  `PhotoBackgroundLayer.kt` 與 `PhotoBackground.kt` 兩處 KDoc，誠實記載
  「alpha 就是疊色強度，`0xFF` 會完全蓋掉原圖，呼叫端要自己在 UI 限制低
  alpha」。**給 owner 的建議**：W2-C 設定頁若讓使用者自訂 tint，UI 上應該把
  alpha 滑桿限制在低值（例如 `0x80` 以下），或考慮改用
  `BlendMode.Multiply` / `Modifier.alpha()` 疊一層純色矩形之類「一定保留底圖」
  的混色方式；這兩個都是視覺設計選擇，本次只修文件、未動行為。
- **B14**：`ThemePreviews.kt` 的 `MaterialYouThemePreview` 原本沒有指定
  `@Preview(apiLevel = ...)`（預設 -1，由 tooling 決定），KDoc 卻斷言「Preview
  tooling 跑在 API<31」——這句沒有依據。已拆成兩個預覽：
  `MaterialYouThemeFallbackPreview`（`apiLevel = 30`，明確釘住 <31 退化路徑）與
  `MaterialYouThemeDynamicPreview`（`apiLevel = 35`，釘住 >=31 動態取色路
  徑），KDoc 改成誠實敘述「以 `apiLevel` 釘住渲染環境，實際桌布取色結果仍須
  實機驗證」。**round-5 補充**：四條 preview 全部只驗證到「編譯通過」，沒有任何
  一條在 Android Studio 內實際開啟 render 過。其中 `apiLevel = 35` 那條會走
  `dynamicLightColorScheme` 去讀 `android.R.color.system_accentN_*`，那些資源在
  實機上由 SystemUI 桌布取色服務於執行期寫入，Layoutlib 沙盒是否模擬依版本而異
  ——所以它可能顏色不準，也可能直接 render 失敗，兩種都沒被排除。刻意不在 preview
  裡加 try/catch 掩蓋（那是為推測中的失敗加防禦碼），改為誠實記載未驗證。
- **B15**：`MaterialYouTheme` 原本是普通 class，`from()` 每次 `new`，相同輸入
  兩次呼叫不相等；`LightTheme`/`DarkTheme` 卻是 `object`（天然單例、相等）。已
  改成 `data class`（配 `@ConsistentCopyVisibility` 消除 Kotlin 對「非 public
  建構子暴露在 `copy()`」的警告），equals/hashCode 以 `id` + `styleSheet` 為
  準（`styleSheet` 本身已是 data class，逐欄位比較）。補了一條
  `two calls with the same inputs produce equal instances` 測試；改回普通
  class 重跑會紅（已現場驗證：`assertEquals(first, second)` 因用
  `AssertionFailedError` 失敗），改回 data class 後綠，證明測試有效保護這個
  性質。**W2 follow-up（本包不動）**：驗證者指出光加 equals 不會讓用到
  `MaterialYouTheme` 的 composable 被 Compose 跳過重組——Compose 2.0.20+ 的
  strong skipping 對 unstable 型別是用 `===` 比較，要真的可 skip 必須在
  `:common` 的 `KeyboardTheme` interface 標 `@Stable`/`@Immutable`；那是
  contracts-v1（凍結中），本包不准動，登記給 W2 之後處理。
- **B19**：本檔驗收表原寫「26/26」，同檔另一處寫「31 條」，實際重新清點是
  32 條（B15 補了一條測試後）。已更新為實際數字；ktfmtCheck / lint 兩格已在
  B12/B14/B15 全部改完、最後一次 commit 之後重新跑過，不是沿用中途快照
  （`ktfmtCheck` 首次重跑抓到本回合新改的 4 個檔案格式跑紅，跑
  `:theme:ktfmtFormat` 修過後再次 `ktfmtCheck` 才綠；`lint` 重跑仍是
  `No issues found.`）。

## 2026-08-10 第七輪（Opus tracer）— 對比度與弱測試

- **B22（high）：三個內建主題的對比度不足，功能鍵按下時字幾乎看不見。**
  用 WCAG 相對亮度公式實算原值：DarkTheme `keyText #E6E1E5` on `keyAccent #D0BCFF`
  = **1.32:1**、LightTheme = **2.66:1**（AA 文字要 4.5:1）；LightTheme
  `candidateHighlight #E8DEF8` on `background #F3EDF7` = **1.13:1**、Dark = **1.84:1**
  （WCAG 1.4.11 非文字元件要 3:1）。根因是兩套色盤都把 M3 的 `primary` 直接搬進
  `keyAccent`，但 M3 的 primary 是設計來配 `onPrimary` 的，而 `KeyboardColors` 契約
  沒有 on-accent 欄位、按鍵文字只有 `keyText` 可用；`MaterialYouTheme` 用
  `scheme.primary` 做同樣映射，所以三個主題全中。

  已改：`keyAccent` 改用 container 色階（Light `#EADDFF` → 13.28:1、Dark `#4F378B`
  → 7.22:1）；`MaterialYouTheme` 改對應 `scheme.primaryContainer`，`candidateHighlight`
  改對應 `scheme.secondaryContainer` 以免與 keyAccent 撞色。Light 的
  `candidateHighlight` 改 `#8A8196`（對 background 3.23:1、候選字 4.62:1）。

  **深色主題的取捨（重要，非疏漏）**：Dark 的兩個門檻**數學上無法同時滿足**——
  background 近黑（相對亮度 0.0113）、candidateText 近白（0.7633），高亮色要對
  background 達 3:1 需要亮度 >= 0.1339，要讓白字達 AA 4.5:1 需要亮度 <= 0.1307，
  可行區間為空。根因是 `KeyboardColors` 沒有「高亮候選專用文字色」（M3 的
  `onSecondaryContainer`），contracts-v1 已凍結。**選擇文字可讀性優先**：高亮的作用
  是指示選中，`:ime` 還能用邊框/底線補強；候選字看不清楚沒有替代方案。最終取
  `#656471`——在「白字達 4.50:1」前提下對 background 分離度最大者（2.95:1）。
  **已登記為 W2 契約 follow-up**：`KeyboardColors` 是否需要 `onCandidateHighlight`。

  守門測試 `BuiltInThemesContrastTest`：純 Kotlin 的 WCAG 計算（不需 Android
  runtime），對兩套色盤斷言 `keyText/keyAccent >= 4.5`、`candidateText/candidateHighlight
  >= 4.5`（Dark 亦然）、`candidateHighlight/background >= 3.0`（Dark 為已論證的 2.9）。
  **已證明會紅**：把 Dark 的 `candidateHighlight` 暫時改回舊值 `#4A4458`，
  `DarkTheme candidateHighlight against background stays at the documented best-effort 2_9`
  立刻 FAILED；還原後全綠。
  動態色路徑（`MaterialYouTheme` 的 >=31 分支）**無法**用 JVM 測試（需系統資源），
  因此它的對比度**未經驗證**，只有兩套固定色盤有守門——誠實記在此。

- **B23（medium）：`LightTheme and DarkTheme use distinct colors` 守門失效。**
  舊寫法只斷言整個 `KeyboardColors` data class 不相等，6 欄位只要 1 欄不同就綠，
  「複製 Light 改 Dark 但漏改欄位」這個它宣稱要防的典型失誤抓不到。已改為逐欄位斷言。

- **被判為範圍外、未做（登記給 W2）**：`ThemeSwatch` 未匯出成 public（DEVPLAN 的 W2-C
  提到「套用 W1-B 的 ThemePreview」，但 W1-B 自己的交付清單只要求「三主題各有 @Preview」）；
  `StyleSheet` → `KeyboardTheme` 的 adapter（根因是凍結契約缺 shapes/typography）。
  兩者都不該由本包單方決定尚未存在的下游 API。

## 2026-08-11 第八輪（Opus tracer）— B22 的修正自己製造了新缺陷

- **B24（high）：`keyAccent` 改用 container 色階後，強調色自己看不見了。**
  B22 只顧「字疊在強調色上」，把 `keyAccent` 換成 M3 container 端點，結果強調色**自身**
  對周邊的分離度崩掉——實算：Light `#EADDFF` 對 `keyFill` 1.29:1、對 `background` 1.12:1
  （原 primary 是 6.44 / 5.60）；Dark `#4F378B` 對 `keyFill` 1.54:1、對 `background`
  1.84:1（原 8.42 / 10.05）。而 `keyAccent` 的契約語意正是「pressed state / 功能鍵」，
  它的首要功能就是要跟一般鍵分得出來——等於按下去看不出按鍵有變色。
  諷刺的是 Dark 的 1.84 正是 B22 拿來當缺陷證據的同一個數字：修掉一個 1.84、又造出另一個。

  更根本的問題是**守門測試只斷言了會過的配對**：WCAG 1.4.11 被套在
  `candidateHighlight/background`（剛好過），卻沒有套在本輪唯一被改動的 `keyAccent` 上
  ——也就是新測試對它會慘敗的欄位保持沉默。

  已修：
  - Light 並不存在深色那種數學互斥（可行亮度區間 L∈[0.226, 0.254] 非空），改用中間調
    `#9179BE`：keyText 4.64、對 keyFill 3.70、對 background 3.21，**三項同時達標**。
    第一版直接跳到 container 端點是偷懶。
  - Dark 套用與 `candidateHighlight` 相同的規則（文字 4.5 當硬下限、在此前提下最大化分離度），
    窮舉紫色系得 `#855196`：keyText 4.50、對 keyFill 2.47、對 background 2.95。後兩項仍
    不及 3:1，但已是此契約下的最佳值。
  - 補 `LightTheme keyAccent stands out from keyFill and background`（3.0/3.0）與
    `DarkTheme keyAccent stays at the documented best-effort separation`（2.4/2.9）兩條守門。
    **已證明會紅**：把兩個 keyAccent 改回第一版的 container 色階，兩條立刻 FAILED，還原後綠。

- **深色 `candidateHighlight` 的取捨在本輪定案為「文字優先」**：兩個候選值
  `#6F6A76`（背景 3.26 / 白字 4.07）與 `#656471`（背景 2.95 / 白字 4.50），**選 #656471**。
  理由：候選字看不清楚沒有替代方案，而「這一個被選中」`:ime` 還能用邊框／底線／字重表達。
  兩個選項與切換方式都寫在 `BuiltInThemes.kt` 註解裡，owner 若要反過來以 1.4.11 為硬門檻，
  改色值 + 對調兩條 Dark 門檻即可。
  （過程註記：本輪一度有兩個 session 並行動到同一個 worktree，`#656471` 曾被覆寫回
  `#6F6A76`；已比對還原，最終狀態如上。）

- **動態色路徑的已知結構性限制（tracer 的推理，非實測）**：M3 的 `*Container` 與 `surface`
  是固定 tone 目標（light 下 surface≈tone98 / container≈tone90，dark 下 10 / 30），桌布只換
  色相與彩度、不換 tone，因此 `MaterialYouTheme` 的 `keyAccent`/`candidateHighlight` 對
  `background` 的分離度在**任何**桌布下都落在 1.2～1.9 量級——固定色盤剛修掉的缺陷，在動態
  路徑上原封不動存在。這條無法用 JVM 測試驗證，標為推理；一併掛在既有的 W2 契約 follow-up
  （`KeyboardColors` 缺 on-accent / on-candidate-highlight 色）之下。

## 2026-08-11 第九輪 — G1/G2/G3

- **G1（medium）：守門的嚴格度反了，`BuiltInThemesContrastTest` 逐條核對後發現三個問題。**
  1. `keyText/keyFill`（一般鍵的字疊在一般鍵底色上、鍵盤上被讀最多次的畫素）只鎖 3.0，
     實測 Light 17.13:1、Dark 11.12:1，餘裕巨大卻鬆到讓「keyFill 調到 3.x:1」仍全綠。
  2. 類別 KDoc 明文寫「`candidateText/candidateHighlight` 也用 AA 的 4.5」，但 Light 那條
     實際只斷言 3.0（Dark 已是 4.5），KDoc 與測試自相矛盾。
  3. `candidateText` 對 `background`（候選列上未被選中、也就是大多數候選字畫在背景上的
     組合）完全沒有門檻。
  已改：`keyText/keyFill`（Light/Dark 兩條）、`candidateText/candidateHighlight`（Light 那條，
  Dark 本來就是 4.5）全部提到 4.5；新增 `candidateText` 對 `background` 的兩條 4.5 門檻
  （Light 14.90:1、Dark 13.27:1，Python 重算過的實測值）。類別 KDoc 同步改寫成「keyText/keyFill
  與 candidateText/background 皆為 AA 4.5」的實際狀態。
  **已證明守門有效**：把 Light 的 `keyFill` 暫改成 `#717171`（對 keyText 3.5097:1），重跑
  `BuiltInThemesContrastTest`，`LightTheme keyText on keyFill meets WCAG AA text contrast`
  立刻 FAILED（`org.opentest4j.AssertionFailedError: expected >= 4.5, was 3.509685660644931`）；
  還原後重跑全綠。（連帶讓 `LightTheme keyAccent stands out from keyFill and background`
  也 FAILED，因為改動的 `keyFill` 同時是那條測試的分母——這是這次示範刻意選值造成的副作用，
  不影響 G1 本身要證明的事。）

- **G2（medium）：`keyAccent` 在渲染程式碼中出現次數為零，`ThemeSwatch` 也把候選列語意畫錯。**
  Grep 確認過整個 `theme/src/main` 只有色值定義與 `MaterialYouTheme` 的映射提到
  `keyAccent`，`ThemeSwatch`（四個 `@Preview` 共用的唯一視覺產出）從未畫過它，owner 卻在
  B22→B24 兩輪被要求對它做視覺取捨。另外 `ThemeSwatch` 把整條候選列刷成
  `candidateHighlight`，但 `:common` 契約語意是「候選列上目前 cursor 位置的底色」，等於把
  「一個候選被選中」畫成「整列都被選中」，`background` 在候選列區域完全不出現。
  已改（純預覽層，`ThemeSwatch` 本身，未動任何主題色值）：
  1. 候選列改成整列先鋪 `background`，只有第一個候選（`Modifier.background(...)`）套
     `candidateHighlight`，其餘用 `Color.Transparent` 留在 `background` 上。
  2. 按鍵列加一顆 `keyAccent` 底色的 ⌫ 功能鍵，上面照樣畫 `keyText`——讓 `keyAccent` 對
     `keyFill`、對 `background`，以及 `keyText` 疊在其上這三組關係一次入鏡。
  這是預覽用 Composable，本模組沒有 Compose render test（沿用既有的誠實揭露：四條
  `@Preview` 只驗證到編譯通過），用 `:theme:assembleDebug` 確認編譯通過。

- **G3（P1／high，codex 獨立審查）：動態 Material You 路徑重現了 B22/B24 剛修掉的缺陷。**
  `MaterialYouTheme.dynamicColorsFor()` 原本把 `keyAccent` 寫死映到
  `scheme.primaryContainer`、`candidateHighlight` 寫死映到 `scheme.secondaryContainer`。
  M3 的 `*Container` 與 `surface` 是固定 tone 目標（light 下 surface≈98/container≈90，
  dark 下 10/30），桌布只換色相與彩度、不換 tone，所以這個映射在任何桌布下都只有
  約 1–2:1 的自身分離度——跟固定色盤剛修掉的缺陷同源，且影響每一個 Material You 使用者。
  **已修，不是「做不到」**：新增 `theme/src/main/kotlin/com/bopomofobruce/theme/color/DynamicAccentSelection.kt`，
  公開純函式 `pickAccentColor(candidates, textPartner, separationReferences, textThreshold = 4.5)`：
  先篩出對 `textPartner` 達 4.5 的候選，若有則取「與 `separationReferences` 的最小分離度」
  最大的一個；若沒有候選過文字門檻，退回「文字對比度最高」的候選，不偽造假合格值。
  `MaterialYouTheme.dynamicColorsFor()` 改成從多個候選角色（`primaryContainer` /
  `tertiaryContainer` / `secondaryContainer` / `primary` / `tertiary` / `secondary` /
  `inversePrimary` / `onSurfaceVariant`）中挑，`keyAccent` 的 `separationReferences` 是
  `[keyFill, background]`、`candidateHighlight` 只看 `[background]`（依契約語意）。
  **這個函式是純數學、不需要 Android runtime，已用假造的色彩組合在 JVM 覆蓋**
  （`AccentColorSelectionTest`，4 條）：
  1. 「container 與 surface 幾乎同 tone、分離度差」vs.「文字剛過門檻但分離度好」的候選並存時，
     選分離度好的那個，不是優先序第一個。
  2. 「所有候選都不過文字門檻」的極端情境（模擬 G3 描述的最壞桌布），退回文字對比度最高的候選、
     不崩潰也不偽造合格值。
  3. 同分時保留優先序在前的候選。
  4. `separationReferences` 只看呼叫端傳入的參照色，不會偷用其他色（合成色值驗證，
     不綁在任何真實主題數字上）。
  **已證明測試有效**：把 `pickAccentColor` 暫時改成 `return candidates.first()`（模擬修正前的
  「寫死映射」行為），重跑 `AccentColorSelectionTest`，4 條裡有 3 條 FAILED
  （`picks the candidate with the best separation...`、
  `falls back to the highest text contrast candidate...`、
  `separation is scored only against the references...`，皆為
  `org.opentest4j.AssertionFailedError`）；還原後重跑全綠。
  `dynamicLightColorScheme`/`dynamicDarkColorScheme` 呼叫本身仍然需要系統資源，本檔依然沒有
  Robolectric、也沒有實機驗證——**這件事沒有變**：改動只讓「給定一組桌布色彩，選色函式會不會
  選對」有守門，實際桌布數字（真實 `ColorScheme` 各角色的實際 RGB 值）仍未驗證，誠實記在此。

- **本輪額外查核**：三條 finding 逐條核對後，內容與程式碼現況一致，沒有發現 finding 本身有誤的地方。
  唯一補充：G1 描述「Light 那條實際只斷言 3.0」時舉的實測值（Light 4.61）與本輪重算的 4.62
  略有小數點差異（四捨五入），不影響結論，門檻與現值關係不變。

  **修正（第十一輪審查追加）**：G3 段落原本沒提到 `keyAccent`/`candidateHighlight` 兩次呼叫
  `pickAccentColor` 會撞色——這個風險當時漏了，補記在下方 H1。

## 2026-08-11 第十一輪 — H1 撞色與 H2 又一條不可能失敗的測試

- **H1（medium）：`keyAccent` 與 `candidateHighlight` 會挑到同一個顏色（撞色）。**
  `dynamicColorsFor()` 對 `accentCandidates`／`highlightCandidates` 各呼叫一次
  `pickAccentColor`，但兩份候選清單成員相同（`primaryContainer` / `tertiaryContainer` /
  `secondaryContainer` / `primary` / `tertiary` / `secondary` / `inversePrimary` /
  `onSurfaceVariant` 這 8 個），只是排序不同。用貼近真實 M3 baseline 的 tone 分布實測：
  container 系 tone≈90 與 surface 幾乎同 tone、primary/secondary/tertiary 系文字對比不到
  4.5，最後只剩 `inversePrimary` 同時通過文字門檻且分離度最好——**兩次呼叫都選中
  `0xFFD0BCFF`**。結果是功能鍵按下的底色與候選列選中游標的底色變成同一個顏色，使用者無法用
  顏色區分「這是功能鍵」還是「這是被選中的候選字」。這正是 G3／B22 註解裡明講要避免的事
  （「改對應 `secondaryContainer` 以免與 `keyAccent` 撞色」），繞一圈又回來了。

  **更正（第十二輪審查，I2）**：本段原本在此處寫「兩者各自的對比度都合格，純粹是語意撞色的 UX
  缺陷，不違反 WCAG」——這句沒有查證過，且是錯的。撞色情境下兩者都是 `inversePrimary`：依本檔
  M3 baseline tone 分布推算（**非實機量測**）——light 下對 background 只有 1.66:1、dark 下對
  background 只有 2.66:1，兩者都不及本檔 `BuiltInThemesContrastTest` 對
  `candidateHighlight`/`background` 設的 WCAG 1.4.11 非文字元件 3:1 門檻。撞色當下就已經不合格，
  不只是「兩者都合格、純粹語意」的 UX 問題。

  已修：`pickAccentColor`（`theme/src/main/kotlin/com/bopomofobruce/theme/color/DynamicAccentSelection.kt`）
  新增 `excluded: Set<UInt> = emptySet()` 參數——排除後的候選清單非空就從中選；若排除後變空
  （理論上只有桌布配色高度單調到所有候選角色都撞在一起才會發生），**忽略排除限制、退回原本
  規則選色**，不丟例外也不偽造一個不在候選清單裡的假顏色，這個「退化語意」寫進了 KDoc。
  `MaterialYouTheme.dynamicColorsFor()` 改成先選出 `keyAccent`，再用
  `excluded = setOf(keyAccent)` 選 `candidateHighlight`，並在呼叫處補了說明退化情境的註解。

  **補測試**（`AccentColorSelectionTest.kt`）：
  1. `excludes an already-chosen color and picks the next best candidate`——重用既有的
     `containerLike`/`distinctHue`/`badCandidate` 三個候選，排除掉本來會贏的 `distinctHue`
     後斷言選中次佳的 `containerLike`。
  2. `falls back to ignoring the exclusion when every candidate is excluded`——單一候選且
     該候選同時是 `excluded`，斷言退回原候選（已記載的退化情境，不崩潰）。

  **已證明會紅**：把 `pickAccentColor` 內的排除邏輯暫時改成永遠 `effectiveCandidates =
  candidates`（不管 `excluded`），重跑 `AccentColorSelectionTest`——
  `excludes an already-chosen color and picks the next best candidate()` FAILED：
  `org.opentest4j.AssertionFailedError: expected: <5191563> but was: <8737174>`
  （`5191563` = `containerLike` 0x4F378B，`8737174` = `distinctHue` 0x855196，證明沒排除時
  仍選中本應被排除的顏色）。還原後重跑，6 條全綠。

- **H2（medium）：又一條不可能失敗的測試。**
  `keeps the earlier candidate on an exact tie` 舊寫法是 `listOf(candidate, candidate)`——
  同一個 UInt 值放兩次，不管 `pickAccentColor` 內部 tie-break 規則是保留先出現的、後出現的、
  還是隨機挑，回傳值都必然等於 `candidate`，對 KDoc 明文宣稱的 tie-break 語意零鑑別力。

  已修：改用兩個**不同的 UInt**——`earlierCandidate = 0x11855196u`、
  `laterCandidate = 0x99855196u`。關鍵是 `relativeLuminance()`/`contrastRatio()` 的實作
  只看 `(argb shr 16) and 0xFF` 這類低 24 bit（R/G/B），完全不讀最上面的 alpha byte
  （bit 24-31），所以這兩個「不同的 UInt」的分離度分數是**數學上精確相等**（同一個
  double 值），不是湊巧或四捨五入後相等，可以真正驗證 `maxWithOrNull(compareBy(...))` 在
  `compare == 0` 時保留先出現的候選。

  **已證明會紅**：把 `pickAccentColor` 最後一行暫時改成
  `pool.reversed().maxWithOrNull(compareBy(scoreBy))`（反轉 tie-break 方向），重跑
  `AccentColorSelectionTest`——`keeps the earlier candidate on an exact tie()` FAILED：
  `org.opentest4j.AssertionFailedError: expected: <293949846> but was: <2575651222>`
  （`293949846` = `earlierCandidate` 0x11855196，`2575651222` = `laterCandidate`
  0x99855196，證明反轉後真的選到後面那個）。還原後重跑，6 條全綠。

- **本輪驗收**：`AccentColorSelectionTest` 從 4 條增至 6 條，`:theme` 模組總測試數從 48 增至 50。
  `./gradlew :theme:assembleDebug :theme:testDebugUnitTest :theme:ktfmtCheck :theme:lint`
  全部 `BUILD SUCCESSFUL`；`ktfmtCheck` 第一次因新加的 KDoc 換行未套用 ktfmt 而 FAILED，跑
  `:theme:ktfmtFormat` 後重新完整跑一次四項確認全綠（`lint-results-debug.txt`：
  `No issues found.`）。
- **本輪額外查核**：兩條 finding 逐條核對後，內容與程式碼現況一致，沒有發現 finding 本身有誤的地方。

## 2026-08-11 第十二輪（Opus tracer）— I1 撞色排除的解法本身讓分離度倒退

- **I1（high）：H1 的硬性排除讓分離度倒退，重現了 B22 修掉的缺陷。**
  H1（第十一輪）用 `pickAccentColor(excluded = setOf(keyAccent))` 解決撞色，這是「先硬性過濾、
  再評分」——排除後剩下的候選不管有多差都不會回頭選。用本檔 KDoc 假設的 M3 baseline tone 分布實算：
  light 下 `keyAccent` 選中 `inversePrimary #D0BCFF`（對 background 1.66:1），排除後
  `candidateHighlight` 落到 `tertiaryContainer #FFD8E4`（對 background只剩 1.26:1，**比排除前更
  差**）；dark 下落到 `secondaryContainer #4A4458`（對 background 1.84:1）——正是 `BuiltInThemes.kt`
  註解裡寫著「原色 #4A4458…只有 1.84:1，不及 3:1」的那個色值與數字，被動態路徑原封不動撿回來。三個
  container 候選彼此 tone 相同，所以只要 `keyAccent` 選中 `inversePrimary`，第二次呼叫幾乎必然落進
  container 群——這是 baseline 下的預設結果，不是罕見路徑。

  **已採用審查者建議的第二個方案**：完全不用 `excluded`，改成 `candidateHighlight` 呼叫時傳
  `separationReferences = listOf(background, keyAccent)`——讓「與 keyAccent 分得開」變成與
  background 同級的評分項而非硬約束。這同時解掉「只排除完全相同 UInt、視覺上幾乎相同的顏色排不掉」
  的問題。改動：`theme/src/main/kotlin/com/bopomofobruce/theme/MaterialYouTheme.kt`
  （`candidateHighlight` 呼叫處，第 128–134 行一帶，移除 `excluded` 參數、`separationReferences`
  改傳 `listOf(background, keyAccent)`）。

  **`excluded` 參數是否保留**：`pickAccentColor` 的 `excluded` 參數本身**予以保留**（
  `theme/src/main/kotlin/com/bopomofobruce/theme/color/DynamicAccentSelection.kt`），只是
  `dynamicColorsFor()` 這個呼叫處不再使用它。理由：`excluded` 是描述明確、已有兩條既有測試覆蓋（
  `excludes an already-chosen color and picks the next best candidate`／
  `falls back to ignoring the exclusion when every candidate is excluded`）的通用純函式功能，硬性
  排除（「絕不可以是這個顏色」）跟軟性評分（「盡量分得開，但分離度優先」）是兩種不同語意，各自有合理
  使用情境；拿掉整個參數只是為了消化這次呼叫端不用它，會連帶刪掉這兩條測試、縮小已測試過的公開 API，
  沒有相稱的好處。

  **必加測試**：`theme/src/test/kotlin/com/bopomofobruce/theme/color/AccentColorSelectionTest.kt`
  新增 `does not sacrifice separation from background just to dodge a collision with keyAccent`。
  構造三個候選：`keyAccentColor`（唯一過文字門檻、對 background 分離度最好 2.17:1）、
  `weakerFailingCandidate`（不過文字門檻但最接近門檻，分離度反而最差 1.69:1）、
  `weakestFailingCandidate`（不過文字門檻、離門檻更遠，分離度較好 2.31:1）——三個色值用 Python 重算過
  WCAG 相對亮度與對比度，不是隨手編的。測試內同時保留一條 sanity check：直接呼叫舊式
  `excluded = setOf(keyAccentColor)` 寫法，斷言它會選到分離度較差的 `weakerFailingCandidate`（用來
  證明新舊兩種呼叫方式的結果確實不同），再斷言新式 `separationReferences = listOf(background,
  keyAccentColor)`（不排除）寫法選中 `keyAccentColor` 本身，分離度 2.17:1，沒有為了避開撞色而選到
  分離度更差的顏色。

  **已證明會紅**：把測試裡「新呼叫方式」暫時改回舊式（`excluded = setOf(keyAccentColor)`，
  `separationReferences = listOf(background)`），重跑
  `AccentColorSelectionTest`——`does not sacrifice separation from background just to dodge a
  collision with keyAccent()` FAILED：
  `org.opentest4j.AssertionFailedError: expected: <1578042> but was: <14158996>`
  （`1578042` = `keyAccentColor` 0x18143A，`14158996` = `weakerFailingCandidate` 0xD80C94，證明
  舊式呼叫方式選到分離度更差的候選）；改回新式呼叫方式後，7 條全綠。

- **I2（medium）：devlog 未查證地宣稱「不違反 WCAG」。**
  第十一輪 H1 段落原寫「兩者各自的對比度都合格，純粹是語意撞色的 UX 缺陷，不違反 WCAG」——這句沒有
  查證過，且是錯的：撞色情境下兩者都是 `inversePrimary`，依本檔 M3 baseline tone 分布推算（**非實機
  量測**），light 下對 background 只有 1.66:1、dark 下對 background 只有 2.66:1，兩者都不及
  `BuiltInThemesContrastTest` 對 `candidateHighlight`/`background` 設的 WCAG 1.4.11 非文字元件
  3:1 門檻——撞色當下就已經不合格。已在原段落原地更正（見上方第十一輪 H1 段落），標明數字為推算、
  非實機量測，不另寫一份。

- **I3（medium）：測試條數三處不一致——B19 抓過的同一類缺陷重演。**
  「交付」段原寫「48 條 unit test」、驗收表原寫「✅ 過（48/48）」，但第十一輪紀錄寫「從 48 增至
  50」，同一份檔案三處兩個答案；驗收表原本交叉指引到「第九輪紀錄」也已過期（最後一次四項完整重跑在
  第十一輪）。已從結構上斷開這個每輪都要手動同步、每輪都漏的來源：「交付」段與驗收表都改成不寫死絕對
  條數，改寫成「條數見各輪紀錄」並指向本檔**最新一輪**（目前即本輪，第十二輪），之後每輪只要在自己
  的段落記實際條數即可，不用回頭改「交付」/驗收表這兩處共用欄位。

- **本輪驗收**：`AccentColorSelectionTest` 從 6 條增至 7 條，`:theme` 模組總測試數從 50 增至 51。
  `./gradlew :theme:assembleDebug :theme:testDebugUnitTest :theme:ktfmtCheck :theme:lint`
  第一次跑，`ktfmtCheckMain`／`ktfmtCheckTest` 因新加的 KDoc 換行未套用 ktfmt 而 FAILED（分別是
  `DynamicAccentSelection.kt`／`AccentColorSelectionTest.kt`），跑 `:theme:ktfmtFormat` 後重新
  完整跑一次四項，全部 `BUILD SUCCESSFUL`（`lint-results-debug.txt`：`No issues found.`）。
- **本輪額外查核**：三條 finding 逐條核對後，內容與程式碼現況一致，沒有發現 finding 本身有誤的地方。

## 2026-08-11 第十四輪（Opus tracer）— 兩處呼叫端契約補文件

- **O1（medium）：`PhotoBackground.uri` 沒說明「必須先取得可持久化授權」這個呼叫端契約。**
  `uri` 原本的 KDoc 只解釋「為什麼存 `String` 而非 `android.net.Uri`」（序列化考量），沒提到 Android
  的 `content://` 授權預設不是持久的：`MediaStore.ACTION_PICK_IMAGES`（Photo Picker）發出的 URI
  **不支援** `takePersistableUriPermission`，授權隨 task／process 結束即失效；只有走 SAF
  `ACTION_OPEN_DOCUMENT` 並呼叫 `contentResolver.takePersistableUriPermission(uri,
  FLAG_GRANT_READ_URI_PERMISSION)` 才會跨重開機存活。照原 KDoc 實作 W2-C 的選圖流程，會得到一個
  當下測試完全正常、重開機後相片背景整個消失的功能，唯一訊號是 `PhotoBackgroundLayer` 的一行
  `Log.w`（把它寫成「來源 App 移除授權」這種外部偶發因素，沒提到呼叫端本來就要履行的前置契約）。

  **純文件修正**：`theme/src/main/kotlin/com/bopomofobruce/theme/photo/PhotoBackground.kt`，
  `uri` 欄位 KDoc（class KDoc 內第一個條列項）補上呼叫端契約：要用 SAF `ACTION_OPEN_DOCUMENT` +
  `takePersistableUriPermission` 取得可持久化授權後才能存進 `uri`；Photo Picker 的 URI 存下來會在
  重開機後失效。**未改行為**，`PhotoBackgroundLayer.kt` 與其他程式碼皆未動。給 W2-C 接手者的提醒：
  選圖流程要走 SAF `ACTION_OPEN_DOCUMENT`，不能用 Photo Picker，選完要呼叫
  `takePersistableUriPermission`，這件事目前全 repo grep 不到任何一處提及，是 W2-C 實作前必須補上的
  前置動作。

- **O2（medium）：反序列化時丟的是 `IllegalArgumentException`，呼叫端最自然的 catch 會漏接。**
  `StyleSheet`（以及巢狀的 `KeyboardShapes` / `KeyboardTypography` / `PhotoBackground` /
  `:common` 的 `KeyboardDimens`）的 `init` require 在反序列化路徑上照樣執行，丟的是
  `IllegalArgumentException`——kotlinx 不會把它包成 `SerializationException`。呼叫端若寫
  `try { Json.decodeFromString<StyleSheet>(text) } catch (e: SerializationException) { 用預設
  主題 }`，遇到結構合法但欄位超出範圍的主題 JSON（例如 `"keyLabelSp": 0`）就會漏接，`IllegalArgumentException`
  未被捕捉地從 IME 主題載入路徑竄出去。這與 `:common` 的 `UIntHexSerializer` 刻意把
  `NumberFormatException` 轉成 `SerializationException`（註解寫明理由是讓 kotlinx 能附帶 JSON
  位置上下文）在同一份 wire format 上自相矛盾。`StyleSheetValidationTest` 原本 7 條全部只呼叫建構子，
  沒有一條走 `decodeFromString`，這個實際使用情境下的例外型別完全沒有守門。

  **已補 KDoc 契約**：`theme/src/main/kotlin/com/bopomofobruce/theme/style/StyleSheet.kt`，class
  KDoc 補一段——本 schema（含巢狀型別）的 `require` 在反序列化時一樣會執行並丟
  `IllegalArgumentException`，解析不受信任的主題 JSON 除了 `SerializationException` 也必須接
  `IllegalArgumentException`（或兩者共同的上層 `RuntimeException`）。

  **已補測試**：`theme/src/test/kotlin/com/bopomofobruce/theme/style/StyleSheetValidationTest.kt`
  新增 `StyleSheet decodeFromString throws IllegalArgumentException for out-of-range nested
  field`——用一段結構合法、但巢狀 `typography.keyLabelSp` 為 `0.0`（超出 `KeyboardTypography` 的
  `> 0f` 範圍）的原始 JSON 字串走 `Json.decodeFromString(StyleSheet.serializer(), ...)`，斷言逃出來
  的是 `IllegalArgumentException`。

  **已證明會紅**：把 `KeyboardTypography.kt` 的 `keyLabelSp` require 暫時改成一行註解拿掉，重跑
  `StyleSheetValidationTest`——2 條 FAILED：新加的
  `StyleSheet decodeFromString throws IllegalArgumentException for out-of-range nested
  field()`（`org.opentest4j.AssertionFailedError`，因為拿掉 require 後 `decodeFromString` 不再丟
  例外）與既有的 `KeyboardTypography rejects zero or negative font sizes()` 一併紅掉（同一個
  require 也守著這條舊測試，紅得符合預期）。還原 require 後重新完整跑 `:theme:testDebugUnitTest`，8
  條全綠（`git diff` 確認 `KeyboardTypography.kt` 已完整還原、無殘留）。

  **未採用「改丟 `SerializationException`」的替代方案**：那會改變直接建構子呼叫端（例如
  `StyleSheetValidationTest` 現有 7 條、`BuiltInThemes.kt` 等）目前依賴 `IllegalArgumentException`
  的既有語意，屬於較大的行為決策，本輪不動。記在此處給 owner：若未來要統一例外型別，`StyleSheet` 及其
  巢狀型別的 `init` 可考慮改成先驗證、`decodeFromString` 路徑改包一層轉型，但這需要重新檢視所有既有
  呼叫端與測試對 `IllegalArgumentException` 的依賴，不是本輪範圍。

- **本輪驗收**：`StyleSheetValidationTest` 從 7 條增至 8 條，`:theme` 模組總測試數從 51 增至 52。
  `./gradlew :theme:assembleDebug :theme:testDebugUnitTest :theme:ktfmtCheck :theme:lint`
  第一次跑，`ktfmtCheckMain` 因新加的 KDoc 換行未套用 ktfmt 而 FAILED（`PhotoBackground.kt` /
  `StyleSheet.kt`），跑 `:theme:ktfmtFormat` 後重新完整跑一次四項，全部 `BUILD SUCCESSFUL`
  （`lint-results-debug.txt`：`No issues found.`）。
- **本輪額外查核**：兩條 finding 逐條核對後，內容與程式碼現況一致，沒有發現 finding 本身有誤的地方。
  O1 的「全 repo grep 不到 `takePersistableUriPermission`」與 O2 的「`StyleSheetValidationTest` 7
  條全部只呼叫建構子、沒有一條走 `decodeFromString`」兩個具體斷言皆已重新查證屬實。

## 2026-08-11 第十五輪 — O2 的測試自己也分不出兩種例外

- **[medium]** O2 新增的 `StyleSheet decodeFromString throws IllegalArgumentException...` 只斷言
  `assertThrows(IllegalArgumentException)`，但 **kotlinx-serialization 1.7.3 的
  `SerializationException` 本身就繼承 `IllegalArgumentException`**（這件事在 W1-C 的 C6 那輪已被
  反編譯 jar 證實過）。所以就算未來這條路徑改成把 require 包成 `SerializationException`——也就是
  「呼叫端只 catch `SerializationException` 會漏接」這個問題根本不存在了——這條測試**仍然會綠**。
  它區分不了自己要證明的兩種情況。

  已修：取回 `assertThrows` 的回傳值，額外斷言 `thrown !is SerializationException`，失敗訊息寫明
  「expected a bare IllegalArgumentException that `catch (e: SerializationException)` would NOT
  catch」。**已證明會紅**：把測試 JSON 換成會丟真 `SerializationException` 的輸入（未知欄位）後
  測試立刻 FAILED，還原後綠。

  教訓與本包 B23／H2 同型：**斷言的型別範圍比它要證明的性質寬時，測試就會對「問題已消失」與
  「問題仍在」給出相同的綠燈。** 這是本包第三次踩到同一類問題。
