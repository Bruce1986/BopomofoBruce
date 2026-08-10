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
- Compose `@Preview`：`theme/.../preview/ThemePreviews.kt` 三個預覽
  （Light / Dark / Material You fallback path）共用一個 `ThemeSwatch` 假鍵盤列
  render。
- 26 條 unit test（style round-trip、validation、photo round-trip、color
  round-trip、內建主題、MaterialYouTheme 退化分支）。

## 驗收結果

| 項目 | 結果 |
|---|---|
| 三主題各有 `@Preview` | ✅ 過（`LightThemePreview` / `DarkThemePreview` / `MaterialYouThemePreview`） |
| 主題序列化/反序列化 round-trip test | ✅ 過（`StyleSheetSerializationTest`、`PhotoBackgroundTest`，含巢狀 `UIntHexSerializer`） |
| `./gradlew :theme:assembleDebug` | ✅ 過 |
| `./gradlew :theme:testDebugUnitTest` | ✅ 過（26/26，見下方「踩雷」） |
| `./gradlew :theme:ktfmtCheck` | ✅ 過（`BUILD SUCCESSFUL`；期間跑過 `:theme:ktfmtFormat` 修過兩檔格式後才綠） |
| `./gradlew :theme:lint` | ✅ 過（`BUILD SUCCESSFUL`，`lint-results-debug.txt`：`No issues found.`） |
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
    而同 module 的 unit test source set 仍呼叫得到（已實測編譯與 31 條測試皆過）。

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
