# W1-B theme — 排程深審班 round 1（2026-09-08）

> **本檔涵蓋範圍比檔名大**：從 2026-09-08 的 round 1 起，後續各班都接在檔尾追加（一個 `#` 一輪）。
> 各節裡的測試條數是該輪當時的快照；**現況以最後一節為準**。未解決與待裁決事項另有一張總表，就在下面。

## 現況（請隨最後一節更新）

- 測試條數以最後一節為準，寫這段時是 95 條、0 failures（`grep -rn '@Test' theme/src/test | wc -l` 可核對）。
- 指令：`ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :theme:testDebugUnitTest --rerun-tasks`
  （`ANDROID_HOME` 是這台開發機的 SDK 位置，換機請改）。`:theme:ktfmtCheck` 與 `:theme:lintDebug` 各自另跑。
  CI 跑的是根專案的 `assembleDebug`、`ktfmtCheck`、`lint`、`testDebugUnitTest :common:test`（`.github/workflows/ci.yml`，JDK 17）。
- 三個陷阱：(1) 一律加 `--rerun-tasks`，並讀 `theme/build/test-results/testDebugUnitTest/*.xml` 的 `tests=`／`failures=`；只看 BUILD SUCCESSFUL
  拿到的可能是 build cache 的舊結果（見下方 round 1 導言）。(2) 把 worktree 複製到別處做突變實驗時要排除 `.gradle/`，並加
  `--no-configuration-cache`（見歷班第 16 輪的操作備忘）。(3) 開發機是 JDK 21、CI 是 JDK 17，JDK 17 沒有在本機實跑過（見歷班第 24 輪）。
- 下面的總表沒有區分「merge 前要決定」與「可以之後再決定」，那是 owner 的判斷。

## 輪次稱呼對照（引用時請寫出是哪一份檔的哪一輪）

| 稱呼 | 所在檔案與節標題 | 日期 |
| --- | --- | --- |
| 第七～十七輪（中文數字） | `feat-w1b-theme.md` 各節 | 2026-08-10～11 |
| round 1、第 2～4 輪（歷班第 1～4 輪） | 本檔開頭到 `# 第 4 輪` | 2026-09-08 |
| 2C／3B／2D（不算一輪） | 本檔 `# Owner 裁決後的落地（2026-09-08）` | 2026-09-08 |
| 歷班第 5～8 輪 | 本檔 `# 排程深審 2026-09-15 第 1～4 輪` | 2026-09-15 |
| 歷班第 9 輪 | 本檔 `# 排程深審 2026-09-22 第 1 輪` | 2026-09-22 |
| 歷班第 10 輪起 | 本檔 `# 手動 GGR fix-loop … 第 N 輪（歷班第 N+9 輪）` | 2026-10-10 起 |
| 歷班第 30 輪起 | 本檔 `# 排程深審 2026-10-14 …（歷班第 N 輪）` | 2026-10-14 起 |

手動 GGR fix-loop 各節的標題有兩個數字：「第 N 輪」是那次 fix-loop 自己的輪次，「歷班第 M 輪」是接續前面各班的累計輪次，M＝N＋9。
本檔內文與總表引用這幾節時一律用歷班輪次。`feat-w1b-theme.md` 的「第十二輪」（I1）與本檔的「歷班第 12 輪」是不同的兩輪。程式碼註解裡只寫中文數字輪次的
（第十一輪、第十二輪、第十四輪…），指的都是 `feat-w1b-theme.md`。

## 未解決／待 owner 裁決總表（2026-10-12 彙整）

這一節是索引：每項一行，細節與證據在指到的位置；兩邊不一致時以詳述處為準，並回頭修這裡。後續各輪若新增或解掉項目，請改這張表。

**A. 設計裁決（行為未改，等 owner 決定）**

1. DEVPLAN W1-B 寫「Compose `Brush` 套用」，實作沒有用 `Brush`；是否為硬性要求——`feat-w1b-theme.md`「交付」段。
2. 動態路徑 `candidateHighlight` 對 background 只有 light 1.23:1／dark 1.99:1（測試色階 fixture 實跑，非實機），固定色盤守的是 3.0；
   過得了文字門檻的候選角色沒有一個同時對 background 達 3:1，要解得改候選來源或改契約——本檔歷班第 13 輪、`MaterialYouTheme.kt` 的 I1 註解。
3. 動態路徑 `keyAccent` 對 background：fixture 實跑 light 1.62:1／dark 2.88:1（非實機），無守門。早期各輪寫的「1.66／2.66」是拿
   M3 baseline 色票算的（dark surface 用了 tone 10、light 用了約 tone 99；API 31–33 的動態配色是 tone 6／98），以 fixture 的數字為準。
   fixture 模擬的是 API 31–33 的路徑；API 34 以上 light 與 dark 的各角色都讀系統資源，那條路徑上的數字沒有驗證——
   `feat-w1b-theme.md` 第九輪 G3 的範圍更正。
4. 動態路徑在所有候選都不過文字門檻時，回傳「文字對比最高但仍低於 4.5」的色，不通知呼叫端——`MaterialYouTheme.from` 與 `pickAccentColor` 的 KDoc。
5. Dark 的 `keyAccent` 對 background 2.9485，未達 3.0，門檻維持 2.9；解法與代價——本檔「3B」節的「只修好了一半」。
6. `KeyboardColors` 缺 on-accent／on-candidate-highlight 欄位（`:common` 已凍結，屬 W2 契約）——on-candidate-highlight 見本檔
   「TODO（已登記，本次未做）」的 `onSecondaryContainer` 那項；on-accent 只記在 `BuiltInThemes.kt` 註解與 `feat-w1b-theme.md` 第七、八輪。
7. `SrcAtop` 的 tint alpha 為 `0xFF` 時會完全蓋掉底圖（刻意，`init` 不限制），W2-C 設定頁要自己限制——`PhotoBackground.kt` KDoc。

8a. 圓角與字級的防呆上限（`KeyboardShapes.MAX_CORNER_RADIUS_DP`、`KeyboardTypography.MAX_FONT_SIZE_SP`，目前都是 1000）是歷班第 21 輪
   加的，數值沒有設計依據；目的是擋掉會讓 px 變成無限大的輸入，做法是超過 1000 一律拒絕（px 仍有限的也拒）；請 owner 複核。主題 JSON 開始被存下來之後再調低，會讓已存的資料解不出來。
   上限只對「自己建構或從 JSON 解出來的」`KeyboardShapes`／`KeyboardTypography` 有意義；內建主題與 `MaterialYouTheme` 用的都是預設值。
   一個會被這個數值擋掉的寫法：用 `9999` 之類的大數表示「全圓角」（膠囊形）。目前沒有任何資料這樣寫；鍵高 48 dp 時 24 以上就已經是全圓。

**B. 契約與邊界**

8. `PhotoBackground` 只驗 scheme、不驗 authority：`android.resource://其他套件名/…` 會通過白名單。Coil 2.6 的 `ResourceUriFetcher`
   在 authority 不是本套件時會經 `PackageManager.getResourcesForApplication` 去讀（2026-10-12 反組譯所見，未實機驗證）。
   讀到的是別的 app 的 APK 資源、只拿來當圖片畫，沒有外送路徑；要不要限定本套件由 owner 決定。**這一項在本表之前沒有任何記載。**
8b. `:common` 的 `KeyboardDimens` 四個欄位只擋負值與非有限值、沒有上限（contracts-v1 凍結，本 PR 不能動）。太大時壞的是
   Compose `Constraints` 的可表示範圍（沙箱實測約 262142 px 以上 `Constraints.fixedHeight` 丟 `IllegalArgumentException`；換成 dp 隨密度而變，
   密度 4 下約 65535 dp），不是數值溢位。`Modifier.height` 會不會因此在版面配置時丟例外是由 bytecode 推論、沒有在裝置上跑過。
   解析不受信任的主題 JSON 之後、交給 Compose 之前，呼叫端要自己把四個欄位夾住（實測夾到 1000 之後不再丟）——`StyleSheet.kt` KDoc、歷班第 21、27 輪。
8c. `PhotoBackground` 的拒絕訊息不帶完整 uri，但冒號前整段剛好長得像 scheme 的字串（最長 32 字元，例如 `vacation-2024.jpg:1` 的
    `vacation-2024.jpg`）仍會被印出，無法與真的 scheme 區分——`PhotoBackground.kt` KDoc、歷班第 16 輪。
9. `KeyboardColors` 不驗證任何欄位（含 alpha 為 0 的透明色）——`StyleSheet.kt` KDoc、本檔歷班第 11 輪。
10. 反序列化時 `init` 丟的是裸的 `IllegalArgumentException`、不是 `SerializationException`（`StyleSheet` 與 `PhotoBackground` 皆然）；
    收緊 scheme 白名單會讓已存資料解不出來——兩處 KDoc。`PhotoBackground` 那條 `assertFalse(thrown is SerializationException)` 的反向驗證在歷班第 8 輪沒跑，歷班第 16 輪由 reviewer 在沙箱補跑：
    把 `init` 改丟 `SerializationException`，`decoding a theme json with a network uri is rejected` 會紅。
11. 白名單刻意不收 `file`；W2-C 若改成複製進 app 儲存區再用 `file://`，要連路徑範圍限制一起加——`PhotoBackground.kt` KDoc。
12. 「Photo Picker 的 URI 不支援 `takePersistableUriPermission`」未查證，W2-C 選圖前要確認——`PhotoBackground.kt` KDoc。
12b. 接手指引（內容都在 KDoc，這裡只指路）：W2-C 做選圖與相片背景之前先讀 `PhotoBackground.kt` 的 class KDoc 與 `PhotoBackgroundLayer.kt` 的
    KDoc——載入失敗時背景只是不畫、唯一訊號是一行 `Log.w`（只記 scheme 與例外類別名稱）；scheme 只收小寫；模糊在 API 30 以下不套用；
    持久授權與解碼失敗的 log 規則。W2-B 把主題接進 IME 之前先讀 `MaterialYouTheme.from` 的 KDoc——回傳的是呼叫當下的快照、不監聽桌布或深色模式變更、
    不通知 fallback、本身不接例外。`from` 回傳的 dimens／shapes／typography 沿用內建主題的預設值；總表 8b 的「要自己夾住」只針對從 JSON 解出來的 `StyleSheet.dimens`
    （shapes／typography 在 `init` 已有上限，見 8a）。

**C. 驗證缺口**

13. 實機 200 ms 未量測；四個 `@Preview` 未實際 render；動態色在實際桌布下的數字未驗證。
14. 實機 androidTest（覆蓋 `>= 31` 動態取色）：可行性已查、未執行，要 owner 同意在手機安裝測試 APK——本檔「2D」節。
15. （已解，2026-10-12 實跑）「`pickAccentColor` 永遠回傳第一個候選」這個突變，本檔「留給 owner」第 3 項記的是當時 7 條裡有 3 條全綠；
    歷班第 16 輪（當時 87 條）實跑有 7 條會紅（`AccentColorSelectionTest` 5 條、`MaterialYouThemeTest` 2 條）；之後新增的測試沒有再重數。原本那 3 條仍綠：同分取前者與單一候選本來就不該紅，
    `excludes an already-chosen color…` 是預期答案剛好排第一。
16. 已評估、仍存活的突變——本檔歷班第 10 輪「仍存活、未補」、歷班第 13、17、25 輪各自的「已評估、未補」。歷班第 19 輪記的缺口（只在 dark 把 `keyAccent` 的文字門檻放寬）
    已在歷班第 20 輪補上；殘餘是呼叫處把門檻放寬到貼近 4.5 的一小段（只動 dark 時是 4.449 以上、只動 light 或兩邊一起時是 4.468 以上），仍沒有測試會紅。

**D. 屬 W2 範圍（本 PR 不做）**

17. `StyleSheet` → `KeyboardTheme` adapter、`ThemeSwatch` 改 public——`feat-w1b-theme.md`「被判為範圍外、未做（登記給 W2）」。
    `:common` 的 `KeyboardTheme` 標 `@Stable`——`feat-w1b-theme.md` 的 B15 段、`MaterialYouTheme.kt` class KDoc。
    `isDynamic`／`degraded`、匯出 `ThemeJson`／`schemaVersion`、`PhotoBackground` 併進 `StyleSheet`——兩份 devlog 都沒有獨立記載，
    只列在 main 上的 `docs/HANDOVER-W1-fixloop-20260811.md` §4（審查時被判為 W2 範圍、不再重提的清單）。

## round 1（2026-09-08 排程深審班；歷班第 1 輪）

以下到 `# 第 2 輪` 之前（含「一、二、三」「已驗證、沒有問題的部分」「留給 owner」）都屬於這一輪；裡面的條數是當時的數字。

自動 PR 深審 routine 對 #9 跑的一輪：兩個並行 reviewer 視角（色彩／對比數學正確性、測試守門
有效性＋突變）＋ parent 自審。**所有結論都在本機實跑驗證過**
（`ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :theme:testDebugUnitTest
--rerun-tasks`；務必加 `--rerun-tasks`，否則拿到的是 build cache 的舊結果，那不是證據）。

基準：52 tests / 0 failures。本輪後：55 tests / 0 failures，`ktfmtCheck` 綠。

## 一、🔴 high：12 條對比度守門驗的是測試自己抄的公式，不是產品程式碼

`BuiltInThemesContrastTest` 私有重寫了一份 `relativeLuminance` / `contrastRatio`，與
`color/DynamicAccentSelection.kt` 的正式實作**逐字元相同**，但測試檔完全沒有 import 正式版本。

**突變實測**：把正式 `relativeLuminance` 的 gamma 由 `2.4` 改成 `2.2`（WCAG 公式的核心常數）
→ `AccentColorSelectionTest` 紅 1 條（它真的呼叫 `pickAccentColor` → 正式 `contrastRatio`），
而 `BuiltInThemesContrastTest` **12 條全數維持綠燈**。

> ⚠️ **更正（round 2）**：本節原文還寫了「把 `contrastRatio` 的 `+0.05` 偏移拿掉，結果相同」，
> 並把它與 gamma 突變並列為等效證據。**那是錯的，而且我沒有自己驗算就寫進了永久紀錄。**
> 移除 `+0.05` 偏移在數學上**只會讓比值變大**：`L1 ≥ L2 ≥ 0` 時
> `L1/L2 ≥ (L1+0.05)/(L2+0.05)` 恆成立（分子分母同加正常數會把比值拉向 1）。而
> `BuiltInThemesContrastTest` 的 12 條全是 `ratio >= 門檻` 的下限檢查，所以這個突變**不論修正
> 前後都不可能讓它們變紅**——它從一開始就不是能區分修正前後的突變。實測確認：修正後這個
> 突變下 `BuiltInThemesContrastTest` 0 紅、只有 `AccentColorSelectionTest` 1 紅（後者測的是
> 候選**排序**，非線性重新縮放會改變名次，那是另一種鑑別機制）。
> **能區分修正前後的只有 gamma 突變**，而它已經獨立、充分地證明了本節的論點。
> ⚠️ commit `6807b28` 的 message 裡也有同一句錯誤敘述，已推出、改不掉，以本段為準。

也就是說：這 12 條是本 PR 用來守兩個內建主題全部對比門檻的主力，卻壓根沒在測產品程式碼。
`DynamicAccentSelection.kt` 的 KDoc 自己早就寫著這份重複的存在、目的是「**避免第三份手抄
公式**」——只是沒做完。`internal` 在 Kotlin 是 module 級可見性，同 module 的 test source set
本來就看得到，沒有任何技術障礙。

**修法**：測試改 `import com.bopomofobruce.theme.color.contrastRatio`，刪掉私有複製品。
**修正後同一個突變**：`BuiltInThemesContrastTest` 4 條轉紅（＋`AccentColorSelectionTest` 1 條）。

## 二、🔴 high：兩個內建主題實際吃到的預設值，沒有任何測試鎖住

`KeyboardShapes` / `KeyboardTypography` 的預設值就是 `LightTheme` / `DarkTheme` 實際會用的值
（兩者都沒有明確指定 `shapes = ` / `typography = `，直接沿用 `StyleSheet` 的預設參數）。

**突變實測**：把 `keyLabelSp` 預設值從 `18f` 改成 `99f`（正常鍵盤字級的五倍以上，UI 上是
顯而易見的災難）→ 全套 52 條**零反應、BUILD SUCCESSFUL**。

原因是唯二碰得到這些值的測試都是自我參照：`StyleSheetSerializationTest` 的「省略時套用預設值」
比對 `decoded.typography` 與 `KeyboardTypography()`——**兩邊吃同一份預設值**，一改一起變，
斷言恆真；round-trip 測試則都是顯式傳入自訂數值，沒有在斷言「預設值必須是這個數字」。

**修法**：新增 `StyleDefaultsTest`，把 `KeyboardShapes`（3 個）、`KeyboardTypography`（4 個）與
`StandardDimens.default`（4 個）的字面數字釘成簽入快照。它不「驗算」任何東西——它的用途是讓
「手滑改到預設值」與「合併衝突解錯邊」必須經過一次人工確認。
**修正後同一個突變**：只有新加的那條轉紅。

## 三、🟡 記入文件：兩處餘裕極薄，紅了不一定是真的退步

reviewer 推翻了我原本的假設（「2.9 是為了讓現值通過而調鬆的裝飾門檻」）——**不是**。反推
`#656471` 的亮度幾乎精確等於「白字達 AA 4.5」約束下的理論最大亮度（差在小數點後 6 位），
2.9 是從這個最佳解回推、留一點緩衝。它擋得住舊值 `#4A4458`（1.84，突變實證會紅）。

但餘裕確實很薄，已寫進測試 KDoc 供日後調色的人參考：

- `DarkTheme` 的 `keyAccent` 對 `background` 現值 **2.9485**、門檻 2.9，餘裕 **0.0485**：往背景
  方向線性內插 2%（R−2、G−1）就跌到 2.888。任何看起來無害的 RGB ±2 美術微調都可能弄紅 CI。
- `keyText/keyAccent` = **4.500011**、`candidateText/candidateHighlight` = **4.500039**，都貼著
  4.5 到百萬分之幾。`kotlin.math.pow` 底層的 `java.lang.Math.pow` 在 JLS 上**不保證**跨 JVM
  廠商／版本／架構逐位元一致（`StrictMath` 才保證）。CI 固定的 ubuntu + Temurin 目前綠，
  但在別的 JDK／架構上本機重跑若翻紅，**先確認是不是浮點差異而不是顏色退步**。

## 已驗證、沒有問題的部分

- 突變矩陣共 11 條：WCAG 公式常數、`contrastRatio` 偏移、`candidateHighlight` 退回舊值、
  `keyText` 調到近乎同色、`pickAccentColor` 永遠回第一個候選、`KeyboardShapes` 的 `require`
  反向、`PhotoBackground` 的 blur 上界——除了上面兩條 finding 之外全部如預期轉紅；兩條反向
  突變（換一個同樣達標的近色、合法範圍內調 dp）**沒有誤紅**，測試沒有過度綁死實作。
- 相對亮度／對比度公式本身以獨立 Python 實作交叉驗算：`(L1+0.05)/(L2+0.05)` 沒寫反、沒漏取
  大小，alpha 通道確實沒被算進亮度。
- 兩套內建主題全部欄位的對比度逐一重算皆對得上（Light 最低 3.21、Dark 最低 2.4715），KDoc 引用
  的每個歷史色值（`#4F378B`、`#4A4458`）也對得上。
- `pickAccentColor` 的極端輸入（全灰／全黑／全白／單一色／飽和度 0／候選重複）不會除以零、
  不會空集合取值、不會無限迴圈。
- `<API 31` 降級路徑重用 `LightTheme.colors` / `DarkTheme.colors`，因此**自動繼承**全部對比度
  守門，覆蓋是完整的（間接）。
- `PhotoBackgroundTest` 對 blur 上界／NaN／Infinity／opacity 範圍皆有實質鑑別力。
- 與 `origin/main` 落後的 5 個 commit 只動 `WORKLOG.md` / `STATUS.md` / HANDOVER 三份文件，
  不含程式碼，所以「合併結果」的測試結論與本分支相同。

## 留給 owner（本班未動）

> 這一節是 2026-09-08 當時的快照。第 1 項已由後面「Owner 裁決後的落地」的 2C 處理（`sdkIntProvider` 測試縫，沒有引入 Robolectric）；
> 第 2 項已由 3B 處理（`candidateText` 改 `#FFFFFF`、`candidateHighlight` 改 `#6B6B6B`）。現況請看檔首的總表。

1. 🔴 **正式入口 `MaterialYouTheme.from(context, darkMode)` 零測試覆蓋**。唯二呼叫點是兩個
   **從未實際 render 過**的 `@Preview`。突變實測：把它改成寫死 `from(context, darkMode, 30)`
   ——也就是 Material You 在**任何裝置上永遠不會啟用**，本模組的招牌功能靜默失效——**52 條
   全綠**。JVM 單元測試改不動 `Build.VERSION.SDK_INT`（JDK 21 已封死 final 欄位的反射改寫），
   要覆蓋需要引入 Robolectric（`@Config(sdk = ...)`）。**加測試框架相依屬設計決定，未自行處理。**
   這與已登記的「動態色實際桌布數字未實機驗證」不同：那是值對不對，這是**開關有沒有接上**。
2. HANDOVER §3-3 深色對比取捨——**「數學上互斥」只在 `candidateText` 維持 `#E6E1E5` 時成立**。
   實測：對 `background` 達 3:1 需相對亮度 ≥ 0.1339，白字達 4.5:1 需 ≤ 0.1307，差 0.0032（是
   險些擦身而過，不是根本不可能）。`candidateText` 改純白後上限放寬到 0.1833，可行區間非空
   ——光是灰階就有 16 個解，例如 `#676767`（對 background 3.03、白字 5.66）。
   代價：`KeyboardColors` 只有單一 `candidateText` 欄位（`:common` 已凍結），改色會同時影響
   非高亮候選（白字對 background 約 17:1，無妨），且會偏離 M3 的 onSurface 取值。屬設計裁決。
3. `AccentColorSelectionTest` 的 7 條裡有 3 條在「永遠回傳第一個候選」這個極端突變下巧合全綠
   （那幾條的候選清單剛好把正解放在第一位）。核心邏輯有其他突變證明過有鑑別力，優先度低，
   但之後擴充案例時可以把這個突變單獨當成一條回歸測試。
4. `dynamicColorsFor()` 的 fallback 分支只挑文字對比度最高者，不看 `separationReferences`
   ——已是 devlog G3 登記在案的 W2 契約 follow-up，此處僅重述位置，不重開。

---

# 第 2 輪（對抗式複審 round 1 自己的修正）

round 1 的兩處修正在突變下都如預期轉紅（gamma 突變 → `BuiltInThemesContrastTest` 4 紅、
`keyLabelSp` 預設值突變 → 只有 `StyleDefaultsTest` 1 紅），核心論證站得住。本輪處理四條：

## A. 更正 round 1 寫進永久紀錄的一個錯誤突變宣稱

見上一節的更正框。要點：**「移除 `+0.05` 偏移」這個突變對任何純下限檢查（`ratio >= 門檻`）
都是無效突變**，因為移除偏移只會讓比值變大。我在 round 1 把它與 gamma 突變並列成等效證據，
而且沒有自己驗算就寫了進去——這是本班第三次在「補守門」的當下寫出未驗證的宣稱
（前兩次都在 bruce_bot#304）。這次的教訓更明確：**突變無效不代表被測程式碼沒問題，
只代表這個突變挑錯了**——判斷一個突變有沒有鑑別力，要先問「它會往哪個方向動被測的量」。

## B. 補上「唯一那份公式自己錯了」的偵測能力

round 1 修掉了「兩份公式各自漂移」，但換來新風險：全模組只剩一份 `relativeLuminance` /
`contrastRatio`，它自己錯了的話所有使用者會**一起錯**。而 `theme/src/test/` 底下原本
**沒有任何一條**測試是拿外部已知答案驗這份公式的——全是「這份公式算的值 vs 這份公式算的
另一個值」或「算出來的值 vs 門檻常數」。

新增 `ContrastFormulaKnownValuesTest`，三組刻意涵蓋不同錯誤型態（鑑別力皆以獨立實作交叉驗算）：

| 已知值 | 抓得到 | 抓不到 |
|---|---|---|
| 黑白 = 21:1（WCAG 數學上界） | `+0.05` 偏移被動過（會變成 `Infinity`） | gamma 錯誤（0 與 1 的任何次方仍是 0 與 1） |
| `#767676` 對白 ≈ 4.5422（**無可查證外部出處**，見下方第 3 輪 C 節的撤回） | gamma 錯誤（2.2 會算成 4.0559，跨過 4.5） | — |
| 同色自比 = 1.0 | `la == lb` 退化點產出未定義值（偏移被拿掉時黑色自比變 NaN） | **lighter/darker 取反、除法寫反**（見 round 3 更正） |

另加對稱性與「alpha 不影響亮度」兩條。**這些期望值不可以改成從本專案公式反推的數字**，
否則會退化成自我印證——已寫進 `DynamicAccentSelection.kt` 的 KDoc。

## C. `DynamicAccentSelection.kt` 的 KDoc 講的是已經不存在的東西

原文寫「與 `BuiltInThemesContrastTest` 私有實作的公式相同」——round 1 已經把那份私有複製品
**刪掉**了，現在兩者是**同一份**，不是「相同的另一份」。留著會讓下一個讀者以為還有兩份平行
實作要維護同步，正是 round 1 想根除的認知模型。已改寫，並把 B 節那條「別把已知值改成反推值」
的約束寫進去。

## D. 把 `StyleDefaultsTest` 的防護邊界寫清楚

突變實證：單改預設值 → 轉紅；**預設值與測試期望值一起改（兩檔同動）→ 55 條全綠**。凡是簽入
快照都有這個固有邊界。它的價值是把「改預設值」從零觸點變成一個觸點——PR diff 裡會出現成對
變更讓 reviewer 有機會問「為什麼」。**擋協同修改靠人看 diff，不是靠這條測試。**已寫進 KDoc，
避免日後有人高估它。

60 tests / 0 failures（round 1 後為 55），`ktfmtCheck` 綠。

## 本輪未改（已評估）

- `StyleDefaultsTest` 改成斷言 `LightTheme.styleSheet.typography == KeyboardTypography()`：
  在**現狀**下不會多抓到任何東西（兩者字面上就是同一個呼叫，`LightTheme`/`DarkTheme` 都沒有
  顯式傳 `typography =`）。它的價值只在未來浮現——若哪天有人給主題顯式傳入一份「字面上與預設
  相同」的 typography，兩者就會開始分岔。記在這裡，等真的出現顯式覆寫時再加。

---

# 第 3 輪（對抗式複審 round 2）

## A. 🔴 我在 round 2 又寫了一句沒驗算的鑑別力宣稱——**同一份 commit 裡**

round 2 的整個動機就是「round 1 寫了沒驗算的突變宣稱」。結果同一份 commit 對同一組新測試，
又寫了一句：「同色自比 1.0（抓 lighter/darker 取反、除法寫反）」。**那句話數學上不可能成立。**

自比時 `la == lb`，於是 `max(la, la) == min(la, la) == la`，`(la+0.05)/(la+0.05) == 1.0`
——`max`/`min` 對調、除法方向反轉，任何重排在 `la == lb` 時都收斂成同一個值。這不是「剛好
沒測到」，是結構上就測不到。

實測兩個突變（`max`/`min` 對調、除法方向反轉）：黑白從 21.0 變成 **0.0476**、`#767676` 從
4.5422 變成 **0.2202**，兩條跨色測試都紅；**自比那條兩次都是綠的**。真正抓到方向反轉的是
那兩條用**兩個不同顏色**的測試。

自比測試實際防的是另一件事：`la == lb` 這個**退化點**不會產出未定義值——例如 `+0.05` 偏移
被拿掉時，黑色自比會變成 `0/0 = NaN`（round 2 的突變矩陣裡它確實因此紅過，但成因是 NaN，
不是方向反轉）。KDoc、devlog 表格都已改成準確描述。
⚠️ commit `41aed3b` 的 message 有同一句錯誤敘述，已推出、改不掉，以本段為準。

**這是本班第四次在「補守門」的當下寫出未驗證的宣稱**（前三次：#304 的 sidecar 子集合比對、
#304 的 keyword_stickers 形狀、#9 round 1 的偏移突變）。四次的共同結構是一樣的：**描述一個
測試「抓得到什麼」時，沒有真的去跑那個突變。**

## B. 🟠 線性分支（`srgb <= 0.03928`）對全模組 60 條測試完全不可見

不只是新測試的盲區——`0.03928` 門檻改成 `0.045`、或 `12.92` 除數改成 `10.0`，**全套 60 條
零反應**（包含 `BuiltInThemesContrastTest`、`AccentColorSelectionTest`）。

原因：所有色票（黑、白、`#767676`、兩個內建主題的實際色、動態取色的候選色）沒有一個 channel
落在 **1..10** 這個「線性分支的非零區間」。黑色剛好是 0，而 `0 / 任何除數 = 0`，連除數寫錯
都看不出來；其他色票的 channel 都遠高於門檻，門檻改多少都不換分支。

補兩個錨點，分工涵蓋門檻的兩側：

| 錨點 | channel | 抓得到 |
|---|---|---|
| `#0A0A0A` | 10（門檻**內**） | 除數寫錯（12.92→10.0／13.0）、門檻被**縮小**（→0.030） |
| `#0B0B0B` | 11（門檻**外**） | 門檻被**放大**（→0.045）、gamma 寫錯 |

突變驗證：兩個修正前「全套零反應」的突變，現在**各紅一條**（都是這條新測試）。

⚠️ 這兩個期望值是**回歸快照，不是外部已知答案**——近黑色的對比度在常見文件裡查不到引用。
它們鎖的是「這份實作在線性分支上的行為別被意外改掉」，已在 KDoc 明確與 `#767676` 那條區隔。

## C. `#767676 ≈ 4.5422` 的出處措辭過度宣稱

我寫的是「WCAG／WebAIM 常引用的參考值」。**repo 內查無出處**，而且這個數字是用同一條 WCAG
公式的獨立實作反算出來的——它證明的是「兩份實作彼此一致」（抓得到謄寫／typo 類錯誤），
**不是**「這條公式忠實反映 WCAG 官方定義」。若整個專案對 WCAG 的理解從一開始就系統性錯了，
兩份實作會一起錯、一起自洽，這條不會紅。

真正不依賴實作的只有**黑白 21:1**——它是公式自身的數學上界（`channel(0)=0`、`channel(255)=1`
對任何 gamma 恆成立，實測 gamma 改 2.2 時黑白仍精確是 21.0）。措辭已改成誠實標注。

## D. 容差 5e-4 的依據補進 KDoc

reviewer 實測：`d(ratio)/d(gamma) ≈ 2.53`，期望值 4.5422 與精確值 4.542224959605253 只差
2.5e-5，裕度約 4.75e-4——換算下來 **gamma 只要偏離 2.4 約 ±0.00019（相對誤差 0.008%）就會
讓這條紅**，而且是全套 60 條裡唯一會紅的（其餘都只做 `>= 門檻` 的粗檢查）。它比看起來緊得多。
也不會偶發紅：IEEE-754 在同一份程式碼與輸入下是決定性的，跨平台 `pow()` 差異約 1 ULP
（~1e-16），比裕度小八個數量級。已寫進 KDoc，避免有人誤以為它很鬆而隨手放寬。

## E. 更正框的數學論證少了一個退化點

round 2 寫「移除偏移在數學上只會讓比值變大，對所有 `L1 ≥ L2 ≥ 0` 恆成立」。`L2 > 0` 時交叉
相乘可證無誤；但 **`L1 = L2 = 0`（黑色自比）時左式是 `0/0`，IEEE-754 下是 `NaN`**，既不是
「變大」也不滿足 `≥`（`NaN >= x` 恆為 false）。不影響該節的結論（那個突變確實無法區分修正
前後），但論證少了一個 case，已補上但書。

## 已驗證、沒有問題

- round 2 對「黑白抓偏移、抓不到 gamma」「`#767676` 抓 gamma（2.2→4.0559）」的具體數字宣稱
  **都準確**。
- `alpha does not affect luminance` 有獨立鑑別力（把 r 通道位移讀到 alpha byte → 17 條紅，
  含這條），不是重言式。
- `StyleDefaultsTest` 的邊界說明沒有過度承諾也沒有不足。
- **仍然抓不到的**：R/B 權重對調（`0.2126`/`0.0722` 互換，和仍為 1.0）——這 6 條新測試全部
  用灰階或黑白，灰階下三個 channel 相同、權重怎麼排都一樣。目前靠 `BuiltInThemesContrastTest`
  的真實非灰階主題色兜底（實測 3 條紅），**但已知值這組本身沒有覆蓋**，記在這裡供日後補一個
  非灰階錨點時參考。

61 tests / 0 failures（round 2 後為 60），`ktfmtCheck` 綠。

---

# 第 4 輪（2026-09-08 當班的最後一輪：Opus tracer 全案最終掃描）

**第 3 輪的四項修正實測全部成立**：M1–M4 各自只紅新測試 ⇒「修正前全套 60 條零反應」屬實；
兩個錨點的分工歸屬四項全中；`max`/`min` 對調時自比那條確實是綠的、拿掉偏移時確實是 NaN
⇒ 第 3 輪對自比測試的重新定性正確。這一輪找到的是**那些修正自己帶進來的新問題**。

## A. 🔴「回歸快照 vs 跨實作檢查」是假的二分法，而真正該做的內在稽核沒做

我把 `#0A0A0A` / `#0B0B0B` 標成「回歸快照」、把 `#767676` 標成「跨實作一致性檢查」，暗示
後者比較可信。**tracer 用完全獨立的實作把那兩個「快照」值逐位元重現了**——它們與 `#767676`
是**完全同一種證據**（同一條公式的第二份實作）。真正的差別只有「有沒有外部出處」，而我自己
早就承認 `#767676` 也沒有。已合併成同一段誠實敘述。

更重要的是，「實作本來就抄錯了會被永久釘死」這個風險**不上網也能大幅降低**——
**分段函式在門檻處必須連續**：

```
0.03928 / 12.92              = 0.003040247678018576
((0.03928+0.055)/1.055)^2.4  = 0.0030394924862258642
相對落差                      = 2.484e-4  (0.025%)
```

五個常數互相印證，動任一個落差就炸開（實算）：`12.92→13.0` 24×、`0.03928→0.045` 21×、
`0.03928→0.030` 84×、`1.055→1.05` 45×、`0.055→0.05` 494×、`12.92→10.0` 911×、`2.4→2.2` 2498×。
這段推導拿紙筆就能重算，已寫進 KDoc——數字因此從「不可稽核的快照」變成**可稽核的推導**。

## B. 🟠 我寫的前置條件斷言對產品程式碼恆真，而且已被證明「在該叫的場合沒叫」

```kotlin
assertTrue(10 / 255.0 <= 0.03928, "前提不成立：#0A0A0A 不在線性分支內")
```

兩邊都是**字面常數對字面常數**，完全沒有觸及 `relativeLuminance`——它比的是測試自己抄的
`0.03928`，不是實作的。突變實證：把實作門檻改成 `0.045` 之後，`#0B0B0B` **真的**掉進線性
分支、「前提不成立」在事實上已經成立，但那條 `assertTrue` **綠燈通過**，紅的是下面的
`assertEquals`。也就是這條前提斷言在它唯一該叫的場合沒有叫——留著比刪掉更糟，因為它讓讀者
以為有守門。

改成問**實作自己**分支在哪（線性支上 L 與 channel 精確成正比）：

```kotlin
assertEquals(10.0, relativeLuminance(#0A0A0A) / relativeLuminance(#010101), 1e-9)
assertTrue(abs(relativeLuminance(#0B0B0B) / relativeLuminance(#010101) - 11.0) > 1e-6)
```

**沒有任何硬編碼期望值、也不需要外部出處。**同一個突變下現在會紅，訊息是
「channel 11 落進了線性分支（門檻被改大？）……實測值 10.999999999999998（正常應約 11.0255）」。

## C. 🟠 灰階盲區補掉了，而且同樣不需要快照

所有錨點都是灰階或黑白，而灰階下三個 channel 相同、**權重怎麼排都算出同一個值**——所以
權重排列錯誤（R↔B、R↔G 對調）對這組完全不可見（實測 R↔B 時這組六條全綠，只有其他兩個檔案
兜底 5 條；R↔G 更薄，只有 3 條）。

補法不需要任何快照：把 channel 打到 0／255 兩端後，`channel(0)=0` 與 `channel(255)=1` 讓
加權和**直接退化成單一權重本身**——期望值就是原始碼裡那三個常數，是從定義推出來的：

```
L(#FF0000) = 0.2126   L(#00FF00) = 0.7152   L(#0000FF) = 0.0722   L(#FFFFFF) = 1.0
```

突變驗證：R↔B 對調 → 這組從 0 紅變成 1 紅（總 6 紅）；R↔G 對調 → 同樣抓到（總 4 紅）。

## D. 🟡「真正不依賴實作的只有黑白 21:1」不準確

黑白 21:1 **一樣依賴實作**——它依賴 `+0.05` 偏移、`channel(0)=0`、以及 `channel(255)=1`
（後者要 `0.055`／`1.055` 這一對配得上）。實測反例：`1.055` 改成 `1.05` → 黑白變 **21.2293**、
這條會紅；而 gamma、門檻、除數、三個權重改掉時它**全都是綠的**（六個常數裡對四個無鑑別力）。

準確說法是「**唯一有外部公認出處**的期望值是黑白 21:1」——那是出處問題，不是鑑別力問題。
原句把兩件事混在一起，混淆方向剛好會讓人**高估** 21:1。已更正。

## E. 🟠 三處只改了一半而互相矛盾的永久紀錄

1. `DynamicAccentSelection.kt` 的 KDoc 明文禁止「把期望值改成從這份實作反推的數字」——而
   第 3 輪加的兩個值正是如此。已改成「已知值組不得反推；線性分支錨點是**刻意的例外**，
   稽核依據是門檻連續性推導」。
2. 同一份 KDoc 的清單只列三項，實際已是四組。已補。
3. **devlog 第 135 行的表格還留著第 3 輪 C 節已經撤回的「WebAIM 常引用」**——我改了第 136 行
   卻漏了第 135 行，而撤回聲明在 60 行之後，讀者從上往下讀會**先吸收被撤回的版本**。已更正。

## F. 🔵 容差與措辭

- `1e-9` 恰當（最小真突變位移 2.8e-4、浮點雜訊上界 2.7e-16，兩側各留五個數量級），但缺推導，
  且**不可為了與 `5e-4` 風格一致而放寬**——放到 1e-4 以上，門檻縮小那一類就靜默失守。已補註。
- `5e-4` 那段的依據引錯：IEEE-754 **並未**要求 `pow` 正確捨入，正確依據是 JLS 對
  `Math.pow` 的「誤差 ≤ 1 ulp」（逐位元一致要 `StrictMath`）——而同 repo 的
  `BuiltInThemesContrastTest` 寫的正是「JLS 不保證跨 JVM 逐位元一致」，兩處方向相反。已統一。
- 測試名 `the linear segment below the gamma threshold is pinned` 只描述了一半（`#0B0B0B`
  釘的是門檻**上方**）。已改成 `the gamma threshold boundary is pinned from both sides`。

## 恆真／重複掃描（全 61 條）

tracer 只找到一處恆真，就是 B 節那兩條前置斷言（已修）。其餘可疑者都用突變證明會失敗：
`contrast ratio is symmetric`、`alpha does not affect luminance`、`a colour against itself`、
以及 `BuiltInThemesTest` 那條看似 `assertEquals(a.b.c, a.c)` 的 delegation 斷言（把
`LightTheme.colors` 的 getter 改成不再 delegate 就會紅）。無重複測試。

62 tests / 0 failures（round 3 後為 61），`ktfmtCheck` 綠。

---

# Owner 裁決後的落地（2026-09-08）

owner 對兩項 W1-B 的待裁決事項給了決定，本節記錄實作與驗證。

## 2C — `MaterialYouTheme` 正式入口的測試縫

**裁決**：加可替換的 `sdkInt` 種子（選項 C），並另外評估實機／androidTest 的可行性（見下節）。

`Build.VERSION.SDK_INT` 的讀取抽成 `internal var sdkIntProvider: () -> Int`，正式入口改成
`from(context, darkMode, sdkIntProvider())`。**未覆蓋範圍因此從「一整行 delegation」縮到
「`{ Build.VERSION.SDK_INT }` 這個單一運算式」**。

實作過程的關鍵發現：`>= 31` 分支在純 JVM 下**不會丟例外**——mockk 的 relaxed `Context` 讓
`dynamicLightColorScheme()` 回傳一組 stub 預設值（多半是 0）。所以兩個分支是**可觀察地不同**的，
測試可以直接斷言「換一個 provider 值，結果就不同」，而**不必**依賴 stub 的具體內容：

| 突變 | 修正前 | 修正後 |
|---|---|---|
| 正式入口寫死 `sdkInt = 30`（Material You 永不啟用） | **52 條全綠** | 紅 1 條（`the production entry point actually routes on the sdk provider`） |
| 預設 provider 寫死 `{ 30 }` | — | 紅 1 條（`the default sdk provider reports the running platform level`） |

第二條測試的鑑別力很窄，已在它的 KDoc 標明：它比對的是 `Build.VERSION.SDK_INT` 與一個本來就是
該運算式的 provider，**唯一抓得到的是「有人把它換成字面值」**，抓不到（也不該被期待抓到）
`SDK_INT` 本身回報錯誤。

## 3B — 深色候選色：兩條 WCAG 門檻現在都真的過了

**裁決**：`candidateText` 改純白 ＋ 換 `candidateHighlight`（選項 B）。

| | 改動前 | 改動後 |
|---|---|---|
| `candidateText` | `#E6E1E5`（M3 dark onSurface） | **`#FFFFFF`** |
| `candidateHighlight` | `#656471` | **`#6B6B6B`** |
| 文字對高亮 | 4.5000（AA 剛好過） | **5.3292**（餘裕 0.83） |
| 高亮對背景 | 2.9485（1.4.11 **不過**） | **3.2143**（餘裕 0.21） |
| 非高亮候選對背景 | 13.27 | 17.13 |
| 測試門檻 | 2.9（規範偏離） | **3.0（規範值）** |

選 `#6B6B6B` 而不是可行區間端點附近的 `#676767`（對背景 3.03、餘裕 0.03）或 `#767676`
（白字 4.54、餘裕 0.04）——端點的餘裕薄到任何 RGB ±2 的美術微調都會弄紅 CI，正是舊值
`#656471`（餘裕 0.0485）的老問題。`#6B6B6B` 兩邊都有餘裕。

突變驗證：把 `candidateHighlight` 退回 `#656471` → 收緊後的門檻紅 1 條
（`expected >= 3.0, was 2.9484939684`）；在舊的 2.9 門檻下它是綠的。

### ⚠️ 只修好了一半——`keyAccent` 那側仍未達標

`keyAccent` 配的是 **`keyText`**（仍是 M3 的 `#E6E1E5`，本次未動），所以它的亮度上限仍是
0.1307、對 background 仍只有 **2.9485**，達不到 1.4.11 的 3.0，門檻維持 2.9。

若日後把 `keyText` 也提到純白，`keyAccent` 同樣會有解（實算）：

| keyAccent 候選 | 白字 | 對 background | 對 keyFill |
|---|---|---|---|
| `#707070` | 4.95 | 3.46 | 2.90 |
| **`#767676`** | **4.54** | **3.77** | **3.16** |
| `#7B7B7B` | 4.23 | 4.05 | 3.39 |

`#767676` 是唯一三條門檻（白字 4.5／對背景 3.0／對 keyFill 3.0）**同時**成立的一個。
代價是所有按鍵文字變純白，視覺影響比候選列大得多，屬另一次設計裁決，本次未動。

## TODO（已登記，本次未做）

- **W2：`:common` 加 `onSecondaryContainer`（高亮候選專用文字色）**——有了它，非高亮候選可以
  留在 M3 的 `#E6E1E5`、只有高亮那一顆用白字。現在已**不是達標的前提**（3B 已達標），
  但仍是更精確的做法。
- **`keyText` 是否也提到純白**（連帶把 `keyAccent` 換成 `#767676`）——見上一節的表。
- **實機／androidTest 覆蓋 `>= 31` 動態取色**：見下節評估。

## 2D — 本機做 androidTest／實機的可行性（已查證，**未執行**）

結論：**門檻比原先評估的低很多，隨時可做**，但有兩個要 owner 拍板的前提。

### 已經備好的部分（不用另外建置）

| 項目 | 現況 |
|---|---|
| 實體裝置 | **已連線**：`ASUS_AI2302`（Zenfone 10），Android 15 / **API 35** |
| Material You 支援 | ✓ API 35 ≥ 31，**動態取色路徑在這台上是真的會走到的** |
| `:theme` 的 instrumentation runner | **已宣告**：`testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` |
| version catalog | 已有 `androidx-test-ext-junit`、`androidx-test-runner`、`androidx-test-espresso-core`，甚至 `de.mannodermaus.junit5:android-test-runner`（androidTest 的 JUnit 5 支援） |
| `adb` | `$ANDROID_HOME/platform-tools/adb`，裝置已授權 |

**沒有** emulator 與 system-image（`$ANDROID_HOME/emulator`、`system-images` 都不存在），
所以只能走實機，不能走模擬器。

### 要做的事（估計 30 分鐘內）

1. `theme/build.gradle.kts` 加 `androidTestImplementation(libs.androidx.test.ext.junit)` 與
   `androidTestUtil`/`androidTestImplementation(libs.androidx.test.runner)`；若要在 androidTest
   也用 JUnit 5，再加 `libs.androidx.test.junit5.runner` 並改 runner。
2. 新增 `theme/src/androidTest/kotlin/.../MaterialYouDynamicColorTest.kt`：拿真實 `Context`
   呼叫 `MaterialYouTheme.from(context, darkMode)`，斷言它**沒有**退化成 `LightTheme.colors`
   （在 API 35 上就該走動態路徑），並把算出來的六個顏色跑一次現有的對比度門檻。
3. `./gradlew :theme:connectedDebugAndroidTest`。

### 兩個要拍板的前提

1. **會在你的手機上安裝測試 APK**（`:theme` 的 androidTest APK ＋ 被測 APK）。這是對你個人
   裝置的實際寫入動作，**我沒有自行執行**，等你點頭。
2. **CI 跑不了**：GitHub Actions 沒有裝置，`connectedAndroidTest` 只能在本機跑。所以它
   **不是回歸守門**，而是「W2-B 接線前後各手動跑一次」的驗收工具。真正的回歸保護仍然是
   2C 那個 provider 種子。

### 我的評估

值得做，但**時機是 W2-B 接線時**，不是現在——因為現在 `:theme` 還沒有任何消費者，實機測到的
只會是「這個函式自己算得對不對」，而不是「IME 畫出來對不對」。現在做，等於在功能還沒接上時
先付一次安裝成本；W2-B 接線時做，同一次可以連 renderer 一起驗。

---

# 排程深審 2026-09-15 第 1 輪（歷班第 5 輪；接在 owner 的 d84a538 之後）

視角換成前四輪沒跑過的兩個：錯誤處理／失敗模式、規格對照＋契約＋接手性；Codex 另跑一次（零 finding）。
全部是 d84a538 落地後**沒跟著更新的敘述**，產品行為零改動，64 tests / 0 failures、`ktfmtCheck` 綠。

- `MaterialYouTheme.kt`：`sdkIntProvider` 上方殘留舊版單行 KDoc「SDK 等級一律取自實際裝置，**無法被覆寫**」，
  跟緊接的新 KDoc 結論相反，已刪；class KDoc 與分支註解還在說「`sdkInt` 建構參數」「兩參數版直接填
  `Build.VERSION.SDK_INT`」，改成經 provider；「測試必須在 `@AfterEach` 還原」與測試實際的 `try`／`finally` 對齊。
- `MaterialYouThemeTest.kt`：class KDoc 寫「只驗證 `<31`、`>=31` 未覆蓋」、`context` 註解寫「分支不會呼叫
  context 上任何方法」——正式入口那條測試會走進 `>=31`、經 `dynamicLightColorScheme` 讀 relaxed mock，兩句都已不成立。
- `feat-w1b-theme.md`：「兩支 overload、無法被覆寫＝最終形態」與「深色兩門檻數學上無法同時滿足、取 `#656471`／2.9」
  兩段加上已被 2026-09-08 裁決取代的指引（保留原文當歷史）。

**評估後未改**：
- reviewer 提的 high「`dynamicLightColorScheme` 沒包 try/catch，OEM 缺系統色資源時會炸穿 IME」——失敗情境是推測、
  沒有證據（`system_accent*` 屬 API 31 平台資源），寬 catch 也違反 styleguide §3；與 2026-08-10 B14「不為推測中的失敗加防禦碼」
  同一取捨。W2-B 接線做實機 androidTest 時可一併觀察。
- `docs/STATUS.md` 的 W1-B 列仍是 2026-08-11 心跳、PR 欄空白、52 tests——那份檔在 main 上，依 DEVPLAN §10.2 由 main 直推更新，
  不在本 PR 分支的範圍，留給 owner。

# 排程深審 2026-09-15 第 2 輪（歷班第 6 輪）

視角：資安／隱私＋跨環境、輸出誠實性（非對比度的 KDoc／註解逐句反查）。64 → 68 tests / 0 failures，`ktfmtCheck` 綠。

- 🟠 **`PhotoBackground.uri` 不限 scheme**：`PhotoBackgroundLayer` 把它原樣交給 Coil，本機 Gradle cache 的
  `coil-base-2.6.0` 內含 `coil/fetch/HttpUriFetcher`（已用 `unzip -l` 實查），`https://…` 會走網路載入；
  目前擋住外連的只有「七個 manifest 都沒申請 `INTERNET`」（coil-base 的 manifest 也沒有）這個外部事實。
  改成 `init` 只接受 `content`／`file`／`android.resource`（不分大小寫），反序列化主題 JSON 時同樣會擋。
  突變 A/B：拿掉這條 `require` → 新測試紅 3 條；大小寫敏感 → 紅 1 條。
- 🟠 **`onError` 把完整 uri 寫進 logcat**（指向使用者相簿裡的特定圖片，會進 bug report）：改成只記 scheme；
  新的 `require` 訊息也只帶 scheme。突變：訊息改帶完整 uri → `rejection message does not leak the full uri` 紅。
  （logcat 那一行在 `@Composable` 裡，純 JVM 測不到，沒有守門。）
- 🟡 `MaterialYouTheme.kt` 的 I1 註解用反引號逐字引用一個**不存在**的測試名稱，改成實際那條
  `does not sacrifice separation from background just to dodge a collision with keyAccent`。
- 🟡 「Photo Picker 的 URI 不支援 `takePersistableUriPermission`」在 KDoc 與 devlog O1 都寫成事實，repo 內查無出處，
  改標 ⚠️ 未查證（排程班禁用網路，無法當場查證；留給 W2-C 實作前確認）。

# 排程深審 2026-09-15 第 3 輪（歷班第 7 輪；對抗式複審第 2 輪自己的修正＋可執行性／反方論證）

68 tests / 0 failures（條數不變、內容改寫），`ktfmtCheck` 綠。**第 2 輪的兩項修正本身都有洞，本輪補上**：

- 🔴 **第 2 輪說「log 不再寫出完整 uri」不成立**：訊息字串清掉了，但 `it.result.throwable` 仍原樣傳給 `Log.w`，
  而 `Log.w` 會印出 throwable 的 `toString()`。`javap` 反組譯 coil-base 2.6.0 的 `ContentUriFetcher`：
  `openInputStream` 回 null 時丟 `IllegalStateException("Unable to open '<uri>'.")`——正是「授權失效」這個最常見的失敗情境。
  改成只記 scheme 與例外型別名稱、不傳 throwable。（`@Composable` 內，純 JVM 仍無守門。）
- 🟠 **第 2 輪的 scheme 比對「不分大小寫」與 Coil 不一致**：`javap` 確認 Coil 2.6 的 `ContentUriFetcher$Factory`／
  `ResourceUriFetcher$Factory`／`FileUriMapper`／`HttpUriFetcher$Factory` 全是 `Intrinsics.areEqual` 精確比對小寫字串，
  `CONTENT://…` 會通過 `init`、渲染時卻載不出來。改成精確比對。突變：加回 `.lowercase()` → 紅 1 條。
- 🟠 **第 2 輪把 `file` 放進白名單**：`file://` 沒有 ContentResolver 那層仲介，等於 IME 行程讀得到的任何路徑。
  目前沒有任何呼叫端需要它，先拿掉；KDoc 寫明若 W2-C 改成複製進 app 儲存區再用 `file://`，要連路徑範圍限制一起加回。
  突變：加回 `"file"` → 紅 1 條。
- 🟡 `MaterialYouTheme.from(context, darkMode)` 補呼叫端契約：回傳的是快照，本模組不監聽桌布／深色模式變更；
  `ThemePreviews` 的 `remember { }` 無 key 寫法只適用 Preview。
- 🟡 `feat-w1b-theme.md` 的 G3「已修」加範圍更正：修掉的是寫死映射，`keyAccent` 對 background 依 H1/I1/I2 的推算
  仍只有 1.66／2.66（非實機），低於固定色盤的 3.0，未解決。

**評估後未改**：reviewer 提「`KeyboardTheme` 契約缺 shapes／typography、devlog 沒登記」——`feat-w1b-theme.md`
「被判為範圍外、未做（登記給 W2）」段已登記 `StyleSheet → KeyboardTheme` adapter 與其根因，reviewer 漏看。

# 排程深審 2026-09-15 第 4 輪（歷班第 8 輪；Opus tracer）

產品程式碼行為零改動（只動註解／KDoc），測試 68 → 70 條 / 0 failures，`ktfmtCheck`、`lintDebug` 綠。
tracer 列了 4 組「預期存活」的突變，**parent 實跑全部存活**（68 條全綠），補守門後逐一重跑轉紅：

| 突變 | 補守門前 | 補守門後 |
| --- | --- | --- |
| `candidateHighlight` 的 `separationReferences` 退回 `listOf(background)`（還原 I1） | 綠 | 紅：light 撞在 `0xFFD0BCFF`（fixture 的 inversePrimary，與 H1 推算一致） |
| `candidateHighlight = keyAccent` | 綠 | 紅 |
| `background`／`keyFill` 對調 surface／surfaceVariant | （未測前態） | 紅 |
| 深色模式也用 `dynamicLightColorScheme` | （未測前態） | 紅：dark background |
| 刪掉 `KeyboardShapes`／`KeyboardTypography` 六個欄位各自的 `isFinite()`（逐一） | 綠 | 各紅 1 |
| `candidateTextSp > 0f` → `>= 0f` | 綠 | 紅 1 |

- 🟠 **`>= 31` 呼叫處零守門，而文件一直寫「JVM 驗不了」**：`javap` 確認 material3 1.3.x 在 `SDK_INT < 34`
  （JVM 下是 0）經 `ColorResourceHelper` → `Resources.getColor(android.R.color.system_*, theme)` 讀色階。新測試
  依 resource id 餵一組 fixture 色階，期望值由 material3 自己的 `dynamic*ColorScheme` 對同一個 mock 算出，
  守角色映射（background／keyFill／keyText／candidateText）與不撞色。**fixture 數值是近似 M3 baseline、未對照官方表**，
  也不代表實際桌布；`keyAccent` 對 background 是否達 3.0 仍無守門（已登記未解決）。三處「JVM 驗不了」的敘述已更正。
- 🟠 **`isFinite()` 名義上有測試、實際沒人守**：既有案例只餵 NaN，而 NaN 在 `>= 0f`／`> 0f` 就已被擋；補 +Infinity。
  `candidateTextSp` 補 0f 邊界。
- 🟡 **第 3 輪寫進註解／commit／devlog 的「授權失效＝`openInputStream` 回 null、最常見的失敗情境」沒有出處**：
  javap 只能證明 Coil 在 null 分支丟含 uri 的 ISE；平台授權失效時實際丟什麼例外未查證。註解改成只寫查證過的部分，
  並註明別拿 errorType 反推授權失效。（e17e5fc 的 commit message 無法修改，以本段為準。）
- 🟡 **`PhotoBackground` 反序列化的例外型別契約**：decode 時丟裸 IAE、不是 `SerializationException`（與 StyleSheet 的 O2
  同一件事，但那段涵蓋不到 `PhotoBackground`）；收緊白名單會讓已存資料解不出來。KDoc 補上，測試比照 O2 加
  `assertFalse(thrown is SerializationException)`（這條的反向驗證沒跑：要讓它紅得改 kotlinx 的包裝行為，本班沒做）。

# 排程深審 2026-09-22 第 1 輪（歷班第 9 輪）

兩個視角並行（測試品質突變複審／產品碼＋文件一致性），產品碼與測試 0 改動，條數維持 **70**
（`:theme:testDebugUnitTest --rerun-tasks`，讀 XML：tests=70、failures=0）。

- 突變複審：8 個針對性突變全數轉紅——還原 I1（candidateHighlight 只避開 background）、background／keyFill
  對調、拿掉 `keyLabelSp` 的 `isFinite()`、`candidateTextSp > 0f` 改 `>= 0f`、scheme 比對改不分大小寫、白名單多收
  `file`、`pickAccentColor` 改挑最差分離度（6 條紅）、dark 也走 light scheme。與上一輪自報的突變矩陣一致（獨立重跑）。
- 🟡 **跨檔「最新一輪」指標過期**：`feat-w1b-theme.md` 兩處仍寫「目前最新：2026-08-11 第十四輪」，但該檔後面還有第十五、
  十七輪，而 08-11 之後的輪次（含 70 條的現況）都記在本檔。兩處改成指向本檔最後一節，並寫明那份檔只記到第十七輪。

# 手動 GGR fix-loop 2026-10-10～11 第 1 輪（歷班第 10 輪；with Codex）

三個視角（產品碼正確性／錯誤處理、測試品質突變、文件全文＋接手性）＋ Codex。產品碼 **0 改動**；
測試 70 → **80** 條（`:theme:testDebugUnitTest --rerun-tasks`，讀 XML：tests=80、failures=0），`ktfmtCheck` 綠。
Codex 與產品碼視角皆零 finding（產品碼視角只做靜態讀碼，沒有實跑）。

- 🟡 **文件全文：六處過期敘述**（已於 `df7ab0d` 落地）：兩條測試 KDoc 仍寫著 devlog 早已撤回的鑑別力宣稱
  （「+0.05 偏移突變」「自比抓得到取反」）、一處引用已改名的測試、一處「已證明會紅」的數字與色碼對不上、
  一處把 H1 修正前的 `listOf(background)` 說成現行契約、本檔檔頭沒說明涵蓋範圍。
- 🟠 **測試品質：23 個新挑的突變，初跑 19 個存活**（70 條全綠）。共同型態是「拒絕端有測、接受端點沒測」
  與「主題對主題比、鎖不住絕對值」。補 10 條後重跑：

| 突變 | 補之前 | 補之後紅的測試 |
| --- | --- | --- |
| 拿掉 `pickAccentColor` 的 `require(candidates.isNotEmpty())`／`require(separationReferences.isNotEmpty())` | 綠 | `rejects empty candidates and empty separation references` |
| `StyleSheet` 的 `id.isNotBlank()` → `isNotEmpty()` | 綠 | `StyleSheet rejects whitespace-only id` |
| 拿掉 `panelCornerRadiusDp >= 0f` | 綠 | `KeyboardShapes rejects negative panel corner radius` |
| `keyCornerRadiusDp >= 0f` → `> 0f` | 綠 | `KeyboardShapes accepts zero for every corner radius` |
| `fontWeight in 1..1000` → `1..999`／`2..1000` | 綠 | `KeyboardTypography accepts both ends of the documented weight range` |
| `opacity` 不接受 `0f` | 綠 | `accepts both ends of the opacity range` |
| scheme 白名單多收 `"data"` | 綠 | `the local scheme allowlist is exactly content and android resource` |
| 內建主題 `styleSheet.id` 與 `theme.id` 對不上；兩個主題的 dimens 一起改掉 | 綠 | `built-in themes use the standard dimens and a styleSheet carrying their own id` |
| Light `background` 的 alpha 改成 `0x00` | 綠 | `built-in theme colors are fully opaque` |
| 動態路徑：`keyAccent`／`candidateHighlight` 的 `textPartner` 改成 `background`；候選拿掉 `inversePrimary` | 綠 | `the dynamic path picks the best separated role among those that carry the text` |

  「補之前」是 reviewer 在沙箱逐一單獨跑的結果；「補之後」另由 parent 在獨立沙箱分三批重跑，每一條新測試都實際紅過，
  且每批紅的只有新測試（舊的 70 條沒有任何一條對這些突變有反應）。

**仍存活、未補（已評估）**：
- `keyAccent` 的 `separationReferences` 只剩 `listOf(background)`：現有色階 fixture 下選出的顏色相同，分不出來；
  要守得換一組 fixture，本輪沒做。（歷班第 13 輪已補，見檔尾。）
- 動態路徑覆寫 `shapes.keyCornerRadiusDp`：「動態路徑只換 colors，其餘沿用退化主題」沒有守門。（歷班第 13 輪已補。）
- Dark `keyFill` 設成與 `background` 相同：規格與 KDoc 都沒要求兩者分離，不算缺口。
- `>= textThreshold` → `>`、`highlightCandidates` 前三項換順序：只在恰好等於門檻／完全同分時有差，現有資料下屬等價突變。

# 手動 GGR fix-loop 2026-10-11 第 2 輪（歷班第 11 輪）

視角：輸出誠實性、規格對照＋契約。產品行為 0 改動（只動 KDoc／註解／devlog），80 tests / 0 failures，`ktfmtCheck` 綠。

- 🟡 `StyleSheet` 的「呼叫端契約」KDoc 把 `KeyboardColors` 列進「在 `init` 用 `require` 驗證」的型別——它沒有 `init`，
  六個欄位是裸 `UInt`。改成只列實際有驗證的三個，並寫明顏色（含 alpha）不驗。
- 🟡 `does not sacrifice separation…` 的 KDoc、行內註解與 `feat-w1b-theme.md` 第十二輪都寫 `keyAccentColor`
  「對 background 分離度最好」。重算：2.1667，而 `weakestFailingCandidate` 是 2.3077。測試鎖得住靠的是
  「只有它過文字門檻」，不是分離度排名；三處敘述已更正。
- 🟡 DEVPLAN W1-B 寫「Compose `Brush` 套用」，實作沒有用 `Brush`，而 devlog 只寫了實際用什麼。
  已在 `feat-w1b-theme.md`「交付」段補記這項偏離，是否為硬性要求留給 owner。

# 手動 GGR fix-loop 2026-10-12 第 3 輪（歷班第 12 輪）

視角：資安／隱私＋跨環境、可執行性（在沙箱照文件實際建置、寫下游用法、重現三個 devlog 記載的突變）。
兩個視角都零 finding，沒有任何檔案變動。可執行性視角重現的三個突變（gamma 2.4→2.2、scheme 比對加 `.lowercase()`、還原 I1）
紅綠與本檔記載一致。

# 手動 GGR fix-loop 2026-10-12 第 4 輪（歷班第 13 輪；Opus tracer）

視角：正確性／錯誤處理（約 170 個對抗式輸入實跑）、反方論證＋測試品質（17 個突變）。產品行為 **0 改動**（只動 KDoc／註解）；
測試 80 → **85** 條 / 0 failures，`ktfmtCheck` 綠。

- 🟠 **動態路徑的 `candidateHighlight` 對 background 幾乎沒有分離度，而註解一直寫成已處理**。以本檔測試的色階 fixture
  經正式入口實跑（非實機）：light 選中 `tertiaryContainer`，對 background 1.23:1；dark 選中 `tertiaryContainer`，1.99:1。
  第十二輪 I1（把 `keyAccent` 併入 `separationReferences`）解掉的是撞色，不是它自己描述的「分離度倒退」——`keyAccent`
  在評分裡對自己恆為 1.0，只要還有別的候選過文字門檻，結果就與硬性排除幾乎相同（light 下新舊寫法選到同一色）。
  過得了文字門檻的只有三個 container 與 `inversePrimary`，沒有一個同時對 background 達 3:1。**行為未改**（要改候選來源或契約，
  屬設計裁決，留給 owner／W2-B）；`MaterialYouTheme.kt` 的 I1 註解、`pickAccentColor` 的 `@param`、`feat-w1b-theme.md`
  的範圍更正段已改成與實況相符。注意歷班第 10 輪補的 `the dynamic path picks the best separated role…` 斷言的是
  「候選裡分離度最大」，這個結果在它眼中是綠的。
- 🟡 `pickAccentColor` KDoc 說 fallback 時「呼叫端可以自行用 `contrastRatio` 判斷」——`contrastRatio` 是 `internal`，
  模組外用不到，模組內唯一的呼叫端也沒檢查；`MaterialYouTheme.from` 的 KDoc 完全沒提這個失敗模式。兩處已據實改寫。
- 🟠 **歷班第 10 輪補的守門自己還有洞**，補 5 條後重跑：

| 突變 | 補之前（80 條） | 補之後紅的測試 |
| --- | --- | --- |
| `keyAccent` 的 `separationReferences` 少傳 `keyFill` | 綠 | `keyAccent is scored against keyFill too and neither accent relaxes the text threshold` |
| `accentCandidates` 縮成只剩 `inversePrimary` | 綠 | 同上 |
| `keyAccent`／`candidateHighlight` 呼叫處加 `textThreshold = 3.0` | 綠 | 同上 |
| 動態路徑另外覆寫 `shapes` | 綠 | `the dynamic path replaces only id and colors of the fallback styleSheet` |
| `pickAccentColor` 預設門檻 4.5 → 4.4 | 綠 | `a candidate just below the text threshold cannot win on separation` |
| `opacity in 0f..1f` 改寫成 `!(opacity < 0f \|\| opacity > 1f)`（放行 NaN） | 綠 | `rejects non-finite opacity` |
| `init` 的 `require` 多收 `scheme == "data"`（不動常數） | 綠 | `rejects schemes outside the allowlist that coil or the platform could resolve` |

  「補之前」是 reviewer 在沙箱逐一單獨跑的結果；「補之後」由 parent 在另一個沙箱分三批重跑（表格第 2 列的
  `accentCandidates` 縮減只有 reviewer 跑過，parent 沒重跑；其餘各列 parent 都跑了），五條新測試每條都實際紅過。
  新 fixture 是為了分辨參數而造的色階（把 `surfaceVariant` 設成與 `inversePrimary` 同色、`secondary` 設成對 keyText 約 4.0 的灰），
  不代表任何實際桌布。

**已評估、未補**：`id.isNotBlank()` 改成只放行 tab／換行、`fontWeight` 中段挖洞——刻意造的近等價突變，端點都有守。

# 手動 GGR fix-loop 2026-10-12 第 5 輪（歷班第 14 輪）

視角：文件全文＋輸出誠實性（只審本次 fix-loop 自己寫下的四筆 commit）、執行成本＋歷班第 13 輪新測試複審。
產品行為 0 改動，85 tests / 0 failures，`ktfmtCheck` 綠。執行成本與新測試複審零 finding（8 個突變全數被抓到，含上一輪 parent 沒重跑的
`accentCandidates` 縮減；未突變連跑三次穩定）。

- 🟡 **歷班第 13 輪自己寫的五處敘述又不準**，全部是更正句：
  - `MaterialYouTheme.kt`「新舊寫法唯一有差的情境是…」被同段前一句推翻（dark 下新舊寫法選到不同色，只是對 background 數字相同）。
  - `pickAccentColor` 的 `@param` 把 1.66／2.66（tone 推算）與 1.23／1.99（測試 fixture）說成「同一組」；fixture 下撞色寫法是 1.62／2.88。
  - 新 fixture 測試的 KDoc 寫「每個模式只改一格」，實際改兩格；行內註解「分離度比所有達標的角色都好」只在 light 成立。
  - 本檔歷班第 13 輪「第 2、3 列…只有 reviewer 跑過」指錯列，實際只有第 2 列。
  - `feat-w1b-theme.md` 第十二輪 I1 一節仍把「併入 `separationReferences`」寫成解法、沒有指向新的更正。

# 手動 GGR fix-loop 2026-10-12 第 6 輪（歷班第 15 輪）

視角：接手性、錯誤處理／失敗模式（失敗點逐一列表；反序列化 4 組 `Json` 設定 × 約 90 種壞輸入實跑）。
**產品碼改了一處**（只影響例外訊息文字）；測試 85 → **87** 條 / 0 failures，`ktfmtCheck`、`lintDebug` 綠。

- 🟠 **`PhotoBackground` 的拒絕訊息會帶出路徑片段**。訊息印的是 `uri.substringBefore(':')`，註解說「只帶 scheme」；
  但沒有 scheme 的裸路徑只要含冒號，冒號前就是路徑本身：`/storage/emulated/0/Pictures/secret 10:30.jpg` 的訊息是
  `…but was '/storage/emulated/0/Pictures/secret 10'`。既有的 `rejection message does not leak the full uri` 用的是
  `https://…`，鎖不到。改成冒號前那一段長得像 scheme（RFC 3986 形狀、最長 32 字元）才印，否則印 `not a uri scheme`。
  突變：還原成一律印出 → `rejection message does not echo a path that merely contains a colon` 紅；改成一律不印 →
  `rejection message still names a well-formed scheme` 紅（各自單獨跑，87 條裡只紅那一條）。
- 🟡 **解碼失敗的例外訊息會帶出整個 uri，而 KDoc 的呼叫端契約沒提**：kotlinx 的 `JsonDecodingException` 訊息內嵌
  `JSON input: …` 原文，uri 合法、別的欄位壞掉時 uri 就在裡面。這不是本模組丟的訊息，改不了；`PhotoBackground` 的
  KDoc 補上「記錄解碼失敗時只記例外類別名稱」。
- 🟡 **接手性**：未解決與待裁決事項散在約 15 處、沒有單一入口——在本檔檔首加了總表（當時 17 項；歷班第 21 輪加入 8a、8b，第 25 輪加入 8c，第 26 輪加入指路用的 12b）與輪次稱呼對照；
  2026-09-08 的「留給 owner」一節與 `feat-w1b-theme.md` 第八輪「定案為文字優先」補上已被處理／推翻的標記；
  `BuiltInThemesContrastTest` 一處「第 2 輪 §A」的引用在本檔有三個標題對得上，改成寫出完整節名。
- 反序列化實跑的結論：實際丟出的例外只有 `JsonDecodingException`、`SerializationException`、`MissingFieldException`
  與裸的 `IllegalArgumentException` 四種，全是 IAE 子類，照 KDoc 寫的 catch 接得住；20 萬層巢狀沒有 `StackOverflowError`。

**PR 描述已大幅過期**（條數、「連續兩輪零 finding」、深色候選色的舊方案），不在 repo 內、本輪未動，待 owner 同意後更新。

# 手動 GGR fix-loop 2026-10-12 第 7 輪（歷班第 16 輪）

視角：輸出誠實性（逐項核對上一輪寫的總表與標記）、上一輪產品碼修正的回歸檢查。產品碼 0 改動，87 tests / 0 failures，`ktfmtCheck` 綠。

- 🟡 **上一輪補的測試自己有洞**：`rejection message does not echo a path that merely contains a colon` 只查訊息不含
  `secret`、`Pictures`；實作若印出冒號前的前 8 個字元（`/storage`），它仍綠（reviewer 實跑）。補上「訊息結尾為固定字串」的斷言，
  同一個突變轉紅（reviewer 與 parent 各跑一次）。
- 🟡 **總表三項寫錯**：D17 把只列在 `HANDOVER` 的三樣說成「devlog 已登記」；A6 指到的 TODO 節只有 on-candidate-highlight、
  沒有 on-accent；C15 把「永遠回傳第一個候選」突變列為未解，實跑現在有 7 條會紅。三項已改；輪次對照表補上
  「Owner 裁決後的落地」一列；B10 的反向驗證由 reviewer 補跑後更新。
- 修正本身的回歸檢查沒有發現問題：全新 JVM 第一個動作就反序列化壞輸入，丟的是 IAE、不是初始化錯誤；全 repo 沒有東西依賴舊訊息格式。
  **殘餘**：冒號前整段剛好符合 scheme 形狀的字串（最長 32 字元，例如 `alice-private.diary:page1`）仍會被印出，無法與真的 scheme 區分。
- 操作備忘：沙箱若連 `.gradle/` 一起複製，configuration cache 會把建置導回原 worktree；用過 `--tests` 之後 cache 也會沿用篩選。
  沙箱複製要排除 `.gradle/`，或加 `--no-configuration-cache`。

# 手動 GGR fix-loop 2026-10-12 第 8 輪（歷班第 17 輪；Opus tracer）

視角：全面突變掃描（55 個，專挑先前沒掃過的區域）、全案最終掃描（Compose 層逐行、建置／lint／編譯警告實跑、release 混淆規則、公開 API 表面）。
產品碼 0 改動；測試 87 → **90** 條 / 0 failures，`ktfmtCheck` 綠。全案最終掃描零 finding。

- 🟠 **持久化格式只有一半被釘住**。往返測試都是 encode→decode 自洽，欄位改名時兩邊一起變。逐一加 `@SerialName` 實跑：
  `StyleSheet` 的 `shapes`、三個圓角欄、`keySubLabelSp`、`candidateTextSp`、`fontWeight` 共七個改名後 87 條全綠；`PhotoBackground` 的
  `blurRadiusDp`、`opacity` 改名，以及三個欄位的預設值變動（`opacity` 1f→0.99f 等）也全綠。已存的 JSON 遇到改名，在預設 `Json` 下會丟未知欄位、
  在 `ignoreUnknownKeys = true` 下會靜靜退回預設值。補兩條 golden JSON 測試（固定字面值雙向比對，`PhotoBackground` 另釘省略欄位時的預設值）。
- 🟡 內建主題是否真的沿用預設的 shapes／typography 沒有守門（歷班第 10 輪只接上了 dimens 與 id）；`MaterialYouTheme.ID` 的斷言是常數對常數，
  改值不會紅。各補一條／一行。

| 突變 | 補之前（87 條） | 補之後紅的測試 |
| --- | --- | --- |
| `StyleSheet` 七個欄位各自改名 | 綠 | `StyleSheet wire format is pinned by a golden json in both directions` |
| `PhotoBackground` 的 `blurRadiusDp`／`opacity` 改名、三個預設值變動 | 綠 | `wire format and omitted-field defaults are pinned by golden json` |
| Light／Dark 另外指定 shapes 或 typography | 綠 | `built-in themes use the default shapes and typography` |
| `MaterialYouTheme.ID` 改值 | 綠 | `id is always material-you regardless of fallback` |

  表內各突變由 reviewer 逐一單獨跑；parent 另在獨立沙箱抽 6 個分兩批重跑（`ID`、`opacity` 預設、`fontWeight` 改名、Light 指定 shapes、
  `blurRadiusDp` 改名、`shapes` 改名），四條新斷言都實際紅過。

**已評估、未補**：`MAX_BLUR_RADIUS_DP` 50→49／51（測試以符號引用常數，數值本身沒被釘；調低會讓已存資料解不出來，要不要釘由 owner 決定）；
Light `keyFill` 改成 `#FEFEFE`（色值不設絕對快照是刻意的）；亮度門檻 0.03928→0.04045、`toKeyboardUInt` 改截斷（8-bit 輸入下等價）。
色彩轉換「兩個方向一起把 R、B 對調」只被 `MaterialYouThemeTest` 的 fixture 測試順帶抓到，`ColorConversionsTest` 自己三條全綠。

# 手動 GGR fix-loop 2026-10-12 第 9 輪（歷班第 18 輪）

視角：歷班第 16、17 輪新測試的複審（8 個新突變全數被抓到，零 finding）、產品碼 KDoc／註解逐句讀完（10 個檔）。
產品行為 0 改動（只動 KDoc／註解與一句斷言訊息），90 tests / 0 failures，`ktfmtCheck` 綠。

- 🟠 **dark 的 surface tone 假設錯了，連帶兩個推算數字不對**。`MaterialYouTheme.kt` 與 `DynamicAccentSelection.kt` 寫
  「dark 下 container≈30、surface≈10」；反組譯 material3 1.3.1 的 `dynamicDarkColorScheme31`，background／surface 取的是
  `neutralVariant6`（light 的 `dynamicLightColorScheme31` 是 `neutralVariant98`）。早期各輪的「dark `inversePrimary` 對 background 2.66、container 1.84」
  是拿 M3 baseline 色票（dark surface `#1C1B1F`，tone 10）算的；tone 6 之下約為 2.88／1.99，正好就是色階 fixture 實跑的數字。
  上一輪把兩組數字的落差解釋成「不是同一份資料」，沒說到原因。四處註解、`feat-w1b-theme.md` 的範圍更正段、檔首總表第 3 項已改。
  （這一條本身有三處說得不準，歷班第 19 輪更正，見下一節。）
- 🟡 `pickAccentColor` 的 `@param` 寫「併入 `keyAccent` 只保證不撞色」——`keyAccent` 是唯一過文字門檻的候選時，回傳的就是它自己（撞色），
  同模組的呼叫處註解與測試都這樣寫。拿掉「保證」，補上但書。
- 🟡 `MaterialYouTheme.kt` G3 註解仍寫「`keyAccent` 對 background 是否達 3.0 未經驗證」；fixture 上已知是 1.62／2.88，未驗證的只剩實際桌布。
- 🟡 `StandardDimens` 的 KDoc 與 `StyleDefaultsTest` 的斷言訊息寫「三個內建主題與 MaterialYouTheme 共用」，把 MaterialYouTheme 重複算了一次
  （專案用語裡內建主題就是 Light／Dark／MaterialYou 三個）；改成寫明 Light／Dark 直接引用、MaterialYou 經由退化主題的 `copy` 沿用。

# 手動 GGR fix-loop 2026-10-12 第 10 輪（歷班第 19 輪）

視角：複審上一輪的更正、測試碼 KDoc／註解／斷言訊息逐句讀完（10 個檔，18 個宣稱實跑）。
產品行為 0 改動（只動 KDoc／註解），90 tests / 0 failures，`ktfmtCheck` 綠。

- 🟡 **上一輪的更正有三處說得不準**：
  - 「dark surface 是 tone 6」只對 **API 31–33**（`dynamicDarkColorScheme31`）成立。API 34 以上走 `dynamicDarkColorScheme34`，
    background／surface 直接讀系統資源 `system_background_dark`／`system_surface_dark`，是哪個 tone 從 repo 查不出來。
    色階 fixture 模擬的是 31–33 那條路徑；34 以上的數字都沒有驗證。這一輪只補了 dark 那一側的範圍，light 同樣在 34 以上改讀系統資源，
    歷班第 20 輪才補上。
  - 「light 不受影響」不對：light 的 1.66 也是對 baseline 色票 `#FFFBFE`（約 tone 99）算的，動態配色的 tone 98 之下是 1.62。
  - 「內建的只有 Light／Dark 兩個」與專案用語不合（DEVPLAN 與 `feat-w1b-theme.md` 都把 MaterialYou 算在內）；原句的毛病是重複計算，上一節已改寫。
    另外「與 `BuiltInThemes` 的 1.84 是同一個數字只是巧合」也不對：選到的色值 `#4A4458` 確實相同，是背景不同才讓數字不同。
- 🟡 **測試碼的 13 處敘述**（都只改文字）：
  - `ContrastFormulaKnownValuesTest`：黑白那條對「三個權重改掉」並非全綠（值改掉會紅，只有互相對調才綠）；class KDoc 的
    「除了黑白之外全部是反算」與後補的權重測試矛盾；容差論證引用的 `pow` 值是另一條測試的；「JLS 規定 `pow` 誤差 ≤ 1 ulp」
    出處寫錯（是 `java.lang.Math` 的 Javadoc，`BuiltInThemesContrastTest` 同一個錯）；R↔B 對調的紅燈條數是舊快照。
  - `BuiltInThemesContrastTest`：把 `candidateHighlight` 的舊色 `#4A4458` 說成 `keyAccent` 的舊值（應是 `#4F378B`）；
    「R−2、G−1」漏了 B−2（照字面改不會翻紅）；「與 `candidateHighlight` 同源地無法三全其美」停在已被推翻的狀態；
    兩條 candidateText 測試的 KDoc 互相指對方、沒有人載明取捨。
  - `AccentColorSelectionTest` 一句行內註解與斷言相反（`midCandidate` 對 refB 是較差的那個）。
  - `MaterialYouThemeTest` class KDoc 只提兩層覆蓋，現在動態路徑有五條。
  - `PhotoBackgroundTest` 白名單兩條測試的 KDoc 用「上面／下面」互指，方向寫反，且留著已失效的「加進 data 不會紅」。
- 🟠 **一個實測確認的守門缺口（未補）**：`keyAccent is scored against keyFill too and neither accent relaxes the text threshold`
  抓不到「只在 dark 把 `keyAccent` 的文字門檻放寬」（reviewer 實跑：全套仍綠）。light 的 `keyAccent`、兩個模式的
  `candidateHighlight` 都抓得到。測試名對 dark 的 `keyAccent` 說過頭，已在行內註解寫明；要補得另造一組 dark 色階。

# 手動 GGR fix-loop 2026-10-12 第 11 輪（歷班第 20 輪）

視角：複審上一輪的更正、補上一輪記下的守門缺口。產品行為 0 改動；測試 90 → **91** 條 / 0 failures，`ktfmtCheck` 綠。

- 🟡 **上一輪只限定了 dark 的 API 範圍，light 沒有**。反組譯 material3 1.3.1：`dynamicLightColorScheme` 與 `dynamicDarkColorScheme`
  的分流條件都是 `SDK_INT >= 34`，34 以上 light 同樣整組改讀系統資源（surface 是 `system_surface_light`）。所以「light surface≈98」
  「桌布不換 tone」在 34 以上一樣沒有查證。產品碼兩處註解改成用一句話限定整段（各 tone 與「不換 tone」都是 API 31–33 的結論）。
- 🟠 **呼叫處放寬文字門檻的守門補齊**。上一輪記的缺口之外，reviewer 另外實跑出兩類也全綠：兩個模式的 `candidateHighlight` 放寬到
  4.0／4.2／4.43，以及 light 的 `keyAccent` 放寬到 4.2／4.43——既有 fixture 的 near-miss 灰（對 keyText 約 3.95／4.00）離 4.5 太遠。
  新增 `neither accent call site lowers the text threshold below the default`：用未動過的色階，把 `secondary` 換成對 keyText
  4.468（light）／4.449（dark）的灰，dark 另把 `inversePrimary` 換掉讓灰在 `candidateHighlight` 的評分裡勝得出；五個前置條件各有斷言。
  reviewer 逐一實跑 15 個放寬突變全數轉紅，既有三個突變（少傳 `keyFill`、候選縮減、門檻 3.0）仍由原本那條抓到；
  parent 另跑兩個（dark `keyAccent` 3.0、`candidateHighlight` 4.2），各只紅新測試。
  **殘餘**：呼叫處把門檻放寬到貼近 4.5 的一小段仍沒有測試會紅——只動 dark 時是 (4.449, 4.5)，只動 light 或兩個模式一起動時是 (4.468, 4.5)
  （歷班第 23 輪實跑：一起動時 4.44、4.45、4.46 紅，4.47 起綠；原本這裡寫「約 4.45 以上」，把 light 還抓得到的一段也算成殘餘了）。

# 手動 GGR fix-loop 2026-10-12 第 12 輪（歷班第 21 輪；Opus tracer）

視角：不分 lens 的 merge 前全案審查（Compose compiler 報告、密度換算、fixture 的 resource id、mock 保真度都有實跑）；Codex 第二次複檢（No findings）。
**產品碼改了兩個檔**（多擋一類輸入）；測試 91 → **92** 條 / 0 failures，`ktfmtCheck`、`lintDebug` 綠。

- 🟠 **有限的 dp 不代表有限的 px**。圓角三欄與字級三欄的 `init` 只擋負值與非有限值。`keyCornerRadiusDp = 2e38f` 過得了 `init`，
  也過得了 `Json.decodeFromString`；交給 `RoundedCornerShape` 後，在密度 2.625 下 px 變成無限大，`createOutline` 丟
  `IllegalArgumentException: Corner size in Px can't be negative(topStart = NaN, …)`（reviewer 沙箱實跑；`1.2e38` 正常、`1.4e38` 起丟）。
  例外發生在畫的時候、不在解碼的時候，照 `StyleSheet` KDoc 的呼叫端契約寫的 catch 接不到，鍵盤每次顯示都會當。字級同理（`2e38f.sp.toPx()`
  得 `Infinity`；之後文字排版會怎樣純 JVM 驗不了）。加上具名的防呆上限（各 1000），`init` 改用閉區間檢查。
  突變：圓角上限拿掉、字級上限拿掉、上限改成不含端點——各自單獨跑，92 條裡只紅 `KeyboardShapes and KeyboardTypography cap every size field`。
  上限數值待 owner 複核（總表 8a）；`:common` 的 `KeyboardDimens` 同樣沒有上限但已凍結（總表 8b）。
- 其餘查過沒有問題、且有實跑的：Compose compiler 報告中 `:theme` 自己的四個資料型別都是 stable、`PhotoBackgroundLayer` 可跳過；
  `android.R.color.system_*` 在 JVM 單元測試下是真常數（五個互不相同），fixture 不會退化成同一格；`Resources` 是嚴格 mock，
  沒餵到的 id 會直接丟錯、不會靜靜回 0；四個 `@Preview` 與 `ThemeSwatch` 都是 `private`，`ui-tooling` 只在 debug。

# 手動 GGR fix-loop 2026-10-12 第 13 輪（歷班第 22 輪）

視角：上一輪防呆上限的回歸檢查、本檔檔首索引區與歷班第 10～21 輪的內部一致性。產品行為 0 改動，92 tests / 0 failures，`ktfmtCheck` 綠。

- 回歸檢查零 finding（全部實跑）：十種邊界輸入逐欄比對新舊寫法，除了「大於 1000 現在會被拒」沒有任何差異（`-0.0f` 的結果相同）；
  六個欄位的檢查各刪一次，上限那條測試每次都會紅；`1000.dp`／`1000.sp` 在密度 4、字型縮放 2 之下仍是有限的 px；
  全新 JVM 第一個動作就解碼超上限的值，丟的是預期的 IAE。順手改了兩處低於 medium 的措辭（`KeyboardDimens` 也擋非有限值；
  `KeyboardTypography` KDoc 的新段落原本插在清單中間）。
- 🟡 **本檔檔首的結構三處**：
  - round 1 的導言（「基準：52 tests…本輪後：55 tests」）在歷班第 15 輪插入總表之後，變成掛在總表那一節底下，讀起來像是現況條數。
    補上 round 1 自己的小節標題。
  - 輪次對照沒有寫明「標題的第 N 輪＝歷班第 N+9 輪」，而內文有幾處只寫「第 10 輪」「第 13 輪」指的是歷班輪次，會和另一節的標題撞號。
    對照表補上規則，內文與總表改成一律寫歷班輪次。
  - 總表的 8b 原本排在 8 前面；移到 8 之後。「加了總表（17 項）」補註現在是 19 項。
- 條數鏈逐 commit 實數（70→80→85→87→90→91→92）與各節宣稱一致；各節引用的測試名都逐字存在；回改後的互相指向都指得到。

# 手動 GGR fix-loop 2026-10-12 第 14 輪（歷班第 23 輪）

視角：產品碼現況的正確性完整複審（約 135 組輸入實跑）、最近三筆 commit 的敘述複審。產品行為 0 改動，92 tests / 0 failures，`ktfmtCheck` 綠。

- 正確性複審零 finding：`git diff 5880980..HEAD -- theme/src/main` 去掉註解後，可執行程式碼的變更只有三處（拒絕訊息的過濾、圓角上限、字級上限）；
  各建構子的輸入組合實跑結果與讀碼預期一致，錯誤訊息的欄位名沒有錯位；JSON 解碼與 `copy()` 都會跑 `init`。
- 🟡 **「殘餘是約 4.45 以上」說得太寬**。實跑：兩個模式一起放寬時 4.45、4.46 仍會紅，4.47 才開始綠（light 的灰是 4.468、dark 是 4.449）。
  測試 KDoc 的「界線」段另外有一句把抓得到與抓不到的區間寫成同一段，並說殘餘「由預設值的單元測試釘住」——那條釘的是預設值，不是呼叫處。
  KDoc、總表 C16、歷班第 20 輪的「殘餘」三處已改成分模式的精確區間。
- 🟡 **上限的 KDoc 寫「只擋掉 px 會變成無限大的輸入」**，實際是超過 1000 一律拒絕，px 仍有限的值（例如 5000）也拒。兩處 KDoc 與總表 8a 改成分開講目的與做法。

# 手動 GGR fix-loop 2026-10-12 第 15 輪（歷班第 24 輪）

視角：資安／隱私＋跨環境（以實跑重做）、上一筆 commit 的敘述複審。**兩個視角都零 finding，沒有任何程式碼或敘述變更**；92 tests / 0 failures。

- 洩漏面：在 uri 埋特徵字串，對 `PhotoBackground` 的建構、`copy`、JSON 解碼各種失敗路徑檢查例外的 message、`toString()`、cause 鏈——
  本模組自己丟的例外沒有任何一條含 uri；含 uri 的只有 kotlinx 的 `JsonDecodingException`（KDoc 已記）。模組內沒有任何地方把
  `PhotoBackground` 物件本身內插進字串或交給 log。
- Coil 2.6.0 反組譯：沒有任何 mapper 會把 `content`／`android.resource` 的 uri 改寫成 `file` 或 `http(s)`；`Uri.parse` 取 scheme 的方式與
  `init` 的 `substringBefore(':')` 一致。
- 跨環境：lint `No issues found`；這支 PR 對建置設定的變更只有 `theme/build.gradle.kts` 一行 mockk。**JDK 17 沒有在本機實跑**（本機是 JDK 21），
  推上去之後以 CI 為準。

# 手動 GGR fix-loop 2026-10-12 第 16 輪（歷班第 25 輪；Opus tracer）

視角：收尾前的突變掃描（47 個新突變）、現行敘述的全量交叉比對（不看 diff，以主題為單位）。
產品行為 0 改動（只動 KDoc／註解）；測試 92 → **95** 條 / 0 failures，`ktfmtCheck` 綠。

- 🟠 **突變掃描**：47 個裡殺掉 26、等價 6、規格沒要求或屬已登記界線 11、真缺口 4（歷班第 17 輪是 55 個裡約 17 個）。四個缺口補三條測試：

| 突變 | 補之前（92 條） | 補之後紅的測試 |
| --- | --- | --- |
| 候選全被 `excluded` 排除時改成直接回第一個候選 | 綠 | `ignoring the exclusion applies the normal rule instead of taking the first candidate` |
| fallback 分支（沒有候選過文字門檻）同分時改取後者 | 綠 | `keeps the earlier candidate on an exact tie in the fallback branch too` |
| `substringBefore(':')` 拿掉 `missingDelimiterValue = ""`（裸字串 `content` 會通過） | 綠 | `scheme must match the allowlist exactly and be followed by a colon` |
| scheme 改成前綴比對（`contentx://…` 會通過） | 綠 | 同上 |

  各突變由 reviewer 逐一單獨跑；parent 另跑第 1、3、4 列，各只紅對應的新測試（第 2 列 parent 沒重跑）。
  **已評估、未補**：候選清單多加或少掉一個在 fixture 上不會被選中的角色（已寫在該測試 KDoc 的界線）；`SCHEME_SHAPE` 的長度與首字元
  （只影響拒絕訊息印不印）；`requireRadius`／`requireFontSize` 傳錯欄位名字串（只影響訊息；驗到別欄的值那種錯位則全數被抓）。
  `keyText` 改讀 `onBackground`、`background` 改讀 `scheme.background` 在 fixture 上同值所以分不出來，是否對任何色階都同值沒有查證。
- 🟡 **現行敘述交叉比對，十個主題裡六個有不一致（都只改文字；下面列的是 reviewer 回報的 10 條，其中一條含兩處）**：
  - 撞色故事線：I1 那條測試的 KDoc 把 `excluded` 寫法稱為「H1 修正前」（它就是 H1 的修正），並說它「重現同一種結構」，讀起來像 I1 解掉了分離度倒退；
    `excluded` 的 `@param` 仍拿已棄用的用法當範例，沒說現在沒有正式呼叫端；`MaterialYouTheme.kt` 兩處「見…KDoc」指過去找不到東西。
  - 守門範圍：`AccentColorSelectionTest` class KDoc 說動態配色函式「無法在這裡測」，`MaterialYouThemeTest` 已有四條直接呼叫；
    `StyleSheetValidationTest` 一段註解還在講產品碼已經沒有的 `isFinite()`；`MaterialYouThemeTest` class KDoc 寫三條、實際四條。
  - 隱私承諾：`PhotoBackground` KDoc 寫「訊息不帶 uri」沒有但書——冒號前剛好像 scheme 的字串（最長 32 字元）仍會被印出。KDoc 補上並舉例，列入總表 8c。
  - 深色色盤：`BuiltInThemes.kt` 仍寫 `keyAccent` 與 `candidateHighlight`「同源地無法三全其美」（後者已解開；測試檔那句歷班第 19 輪改了，這句漏掉）。
  - API 路徑：動態 preview 釘 `apiLevel = 35`，KDoc 說它讀色階資源；material3 在 `SDK_INT >= 34` 讀的是系統角色資源，不是 fixture 模擬的那條路徑
    （Layoutlib 的 `SDK_INT` 是否跟著 `apiLevel` 走沒有驗證，歷班第 26 輪把 KDoc 的一段改成條件句，第 27 輪才把同段第一句與另一條 preview 一起改完）。
  - 同一筆另外順手改了兩處沒列在上面的：`ContrastFormulaKnownValuesTest` 的「見下一條的但書」方向寫反；
    `BuiltInThemesContrastTest` 兩條 KDoc 的「取捨說明見…」改成「門檻的由來見…」（深色候選高亮已經沒有取捨）。
  - fallback：一條測試 KDoc 說函式不會「悄悄回傳不合格的值」，與另外三處的現行說法相反。
  - 其餘四個主題（防呆上限、輪次與日期引用、總表逐項、動態路徑數字）交叉比對後一致。

# 手動 GGR fix-loop 2026-10-12 第 17 輪（歷班第 26 輪）

視角：上一筆改寫的複審、規格對照與接手性對現況重跑。產品行為 0 改動，95 tests / 0 failures，`ktfmtCheck` 綠。

- 規格對照零 finding：DEVPLAN W1-B、REBUILD-PLAN、ADR、AGENTS／handbook 逐條對照現況，全部是符合或已登記的偏離；這次新增的行為
  （防呆上限、拒絕訊息過濾、不收 `file`）與任何規格文字都不牴觸。
- 🟡 **接手性（用五個實際問題考）**：只讀檔首索引與最後三節，「怎麼跑測試」答不完整（指令與 `ANDROID_HOME` 只在 round 1 導言）、
  W2-B／W2-C 接手者必須自己處理的幾件事（載入失敗的表現、快照語意、`from` 不接例外）在索引區找不到路。檔首加「現況」一段與總表 12b（只指路、不複製內容）；
  8a 補上上限只對自己建構或解碼得到的資料有意義。總表沒有區分哪些要在 merge 前決定——那是 owner 的判斷，已在「現況」段寫明。
- 🟡 **上一筆的兩處**：歷班第 25 輪寫「共 10 處」，自己列出來的是 11 處，另有兩個改動沒列；`ThemePreviews.kt` 新寫的
  「`apiLevel = 35` 走的是 `SDK_INT >= 34` 的路徑」把沒驗過的一步（Layoutlib 的 `SDK_INT` 是否跟著 `apiLevel`）寫成了肯定句，改成條件句。
- reviewer 補跑了歷班第 25 輪表格第 2 列（fallback 分支同分取後者），只紅對應的那條；其餘三列也各自重跑確認。

# 手動 GGR fix-loop 2026-10-12 第 18 輪（歷班第 27 輪）

視角：上一筆的複審、錯誤處理／失敗模式對現況重跑（扮演只照 KDoc 寫程式的呼叫端；`StyleSheet` 與 `PhotoBackground` 各數十種壞輸入、各兩種 `Json` 設定——確切數目 reviewer 的回報與它留下的探針對不上，這裡不寫）。
產品行為 0 改動，95 tests / 0 failures，`ktfmtCheck` 綠。

- 🟠 **`StyleSheet` KDoc 對 `KeyboardDimens` 過大的說明，機制寫錯、也沒有給可以照著夾的數字**。原文說「px 溢位」；實測沒有任何數值溢位
  （`roundToPx()` 對 `1e38.dp` 回 `Int.MAX_VALUE`），丟例外的是 Compose `Constraints`（約 262142 px 以上），而且臨界的 dp 隨密度而變。
  照「溢位」的字面去防 Int／Float 範圍的呼叫端，仍會在 `Constraints` 那一步出事。KDoc 改成點名四個欄位、寫出實測臨界值與「夾到 1000 之後不再丟」；總表 8b 同步。
- 🟡 上一筆的三處：兩條 MaterialYou preview 的 KDoc 只有一段改成了條件句，同一段的第一句與另一條 preview 仍把 `apiLevel` 當成已確認會決定
  `SDK_INT`——都改成「意圖是」；總表 12b 把 8a（建構時就拒絕）與 8b（要呼叫端夾）混成一件事；12b 說「內容都在 KDoc」，但「`from` 不接例外」KDoc 沒寫——補進 KDoc。
- 照 KDoc 寫的 catch 接得住全部壞輸入（10 萬層巢狀、1 MB 的 id、各種數值邊界都試過）；`PhotoBackground` 所有失敗路徑的例外**類別名稱**都不含 uri；
  KDoc 提到的 Android API 與旗標名稱在 android.jar 裡都存在；material3 讀色只用 `context.resources.getColor`，不讀 `Configuration`，application context 可用。
- 未列為 finding 的觀察：上限加入前合法、加入後超限的資料，呼叫端接到的是帶欄位名的 `IllegalArgumentException`；`StyleSheet`／`KeyboardShapes` 的 KDoc 沒有像
  `PhotoBackground` 那樣寫「讀取端該怎麼辦」。目前沒有任何已存資料。`:common` 的 `UIntHexSerializer` 的錯誤訊息不帶欄位名或 JSON 位置（凍結，不在本 PR）。

# 手動 GGR fix-loop 2026-10-12 第 19 輪（歷班第 28 輪）

視角：上一筆的複審（每個數字與機制都在沙箱重現）、本檔後半各節與檔首索引的一致性。產品行為 0 改動，沒有動到 `theme/`；95 tests / 0 failures。

- 上一筆的複審零 finding。重現的重點：`Constraints.fixedHeight(262142)` 可、`262143` 丟；這個上限在版面寬度 8000 px 以下都不變
  （寬 32767 時才降到 32766），所以對鍵盤的實際版面成立；三個密度的 dp 換算都對；夾到 1000 dp 在密度 1～4 下都不丟；
  讓 `resources.getColor` 丟 `Resources.NotFoundException` 時，`from` 丟出的是同一個例外物件。
- 🟡 **總表兩項沒跟上後面的輪次**：C16 沒有指到歷班第 25 輪另記的「已評估、未補」；C15 寫「現在整套有 7 條會紅」，那是歷班第 16 輪（87 條時）的實跑數字，
  之後新增的測試沒有重數——改成據實標明。另補一句前向說明（歷班第 25、26 輪說 preview KDoc「已改成條件句」，實際到第 27 輪才改完）。
- 條數鏈逐 commit 實數（70→80→85→87→90→91→92→95）與各節一致；18 節的雙編號都成立；`feat-w1b-theme.md` 這次加的七個更正標記與本檔最新結論一致。

# 手動 GGR fix-loop 2026-10-12 第 20 輪（歷班第 29 輪；Opus tracer＋Codex 第三次複檢）

收尾輪。**零 finding，沒有動到 `theme/`**；Codex 對最終的 `theme/` diff 也是 No findings（三次都是）。

- 推送前的機械檢查（沙箱由 HEAD 複製，照 `.github/workflows/ci.yml` 的寫法跑根專案 task，本機 JDK 21）：`assembleDebug`、`ktfmtCheck`、`lint`、
  `testDebugUnitTest :common:test` 四步都是 EXIT 0；測試 XML 加總 `:theme` 95、`:keyboards` 49、`:common` 26，共 170 tests / 0 failures。
  diff 只動 `theme/**` 與本目錄兩份 devlog；`git diff --check` 無輸出；新增行裡沒有 `println`、`TODO`、被停用的測試、探針或暫存路徑的殘留。
  **JDK 17 沒有在本機實跑**，推上去之後以 CI 為準。
- 最終狀態複審：產品碼逐檔讀完，沒有會在 review 擋下的東西；這次新增的 25 條測試逐條看過，隨機抽 6 條各做一個突變，每個都只紅目標那一條。
- 上限 1000 的一個具體反例記入總表 8a：用 `9999` 表示全圓角的寫法會被拒。

## 這次 fix-loop（歷班第 10～29 輪）的總結

- 測試 70 → 95 條。產品碼的行為變更三處：`PhotoBackground` 的拒絕訊息不再帶出含冒號的裸路徑；圓角三欄與字級三欄加防呆上限 1000。其餘對 `theme/src/main` 的改動都是 KDoc 與註解。
- 20 輪裡完全沒有 finding 的是第 3、15、20 輪（歷班第 12、24、29 輪）；**沒有達到「連續兩輪乾淨」**。後半各輪的 finding 幾乎都在敘述
  （KDoc、註解、本檔），而且多次是前一輪的更正句自己又寫得不準——共同型態是把只在一個分支或一組資料上驗過的結論寫成全稱。
- 跑過的視角：正確性、錯誤處理／失敗模式、資料模型／契約、規格對照、輸出誠實性、可執行性、接手性、資安／隱私、跨環境、執行成本、測試品質（突變）、文件全文、反方論證。
  各自最後一次跑的結果：正確性（第 14 輪）、資安／跨環境（第 15 輪）、規格對照（第 17 輪）零 finding；其餘視角最後一次都還有 finding 並已處理。
- 留給之後的事（不在本 PR 做）：`theme/src/main` 的註解行數與程式碼行數接近 1:1，其中 `pickAccentColor` 的 KDoc 與 `MaterialYouTheme` 呼叫處註解各疊了三、四層更正。
  建議 merge 後另開一支只動註解的 PR，把沿革搬進 devlog、原始碼只留現況與原因。`MaterialYouThemeTest` 的色階測試綁在 material3 1.3.1 讀資源的方式上，
  升 compose BOM 時若轉紅，先讀該檔 class KDoc。`sdkIntProvider` 是全域可變狀態，測試靠循序執行才安全，日後若開 JUnit 平行執行要先處理。

# 排程深審 2026-10-14 afternoon 第 1 輪（歷班第 30 輪）

視角：錯誤處理／契約、註解誠實性、突變抽樣（12 個），另跑 Codex。產品行為 0 改動；95 tests / 0 failures。

- 錯誤處理／契約、Codex：零 finding。突變 12 個殺 10 個，存活的兩個（`SCHEME_SHAPE` 長度上限、`>= textThreshold` 改 `>`）本檔已記為已評估／等價。
- 🟡 註解三處與現況不符，已改：`pickAccentColor` KDoc 說 `excluded` 有「既有的兩條測試」，實際傳入 `excluded` 的測試已不只兩條（改成不寫條數）；
  `ContrastFormulaKnownValuesTest` 權重測試 KDoc 寫 R↔B 對調時「本條以外另有 5 條紅」，在 95 條下重跑是 6 條（本輪實跑，連本條共 7 條紅）；
  `PhotoBackgroundLayer` KDoc「API 31 以下是 no-op」與程式碼（`>= 31` 才套用）字面矛盾，改成「`< 31`」。

