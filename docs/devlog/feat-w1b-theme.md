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
  路徑 `apiLevel = 35`）共用一個 `ThemeSwatch` 假鍵盤列 render。
- 32 條 unit test（style round-trip、validation、photo round-trip、color
  round-trip、內建主題、MaterialYouTheme 退化分支與值語意）。

## 驗收結果

| 項目 | 結果 |
|---|---|
| 三主題各有 `@Preview` | ✅ 過（`LightThemePreview` / `DarkThemePreview` / `MaterialYouThemeFallbackPreview` + `MaterialYouThemeDynamicPreview`，共 4 個；B14 後 Material You 拆成兩條路徑各一個）。證據等級：函式存在且編譯通過，未實際在 Android Studio 內 render 過。 |
| 主題序列化/反序列化 round-trip test | ✅ 過（`StyleSheetSerializationTest`、`PhotoBackgroundTest`，含巢狀 `UIntHexSerializer`） |
| `./gradlew :theme:assembleDebug` | ✅ 過 |
| `./gradlew :theme:testDebugUnitTest` | ✅ 過（32/32，見下方「踩雷」） |
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
  實機驗證」。
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
