# W1-B theme — 排程深審班 round 1（2026-09-08）

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
而 `BuiltInThemesContrastTest` **12 條全數維持綠燈**。把 `contrastRatio` 的 `+0.05` 偏移拿掉，
結果相同。

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
