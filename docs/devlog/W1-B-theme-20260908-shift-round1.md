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
| `#767676` 對白 ≈ 4.5422（WebAIM 常引用的「剛好達 AA」灰） | gamma 錯誤（2.2 會算成 4.0559，跨過 4.5） | — |
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
