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

# 第 4 輪（最後一輪：Opus tracer 全案最終掃描）

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
