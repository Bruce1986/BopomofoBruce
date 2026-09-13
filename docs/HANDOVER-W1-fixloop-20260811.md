# W1 fix-loop 交接文件

> 產生時間：2026-08-11 14:2x（UTC+8）
> 上一個 session：W1 三包實作 + `/gemini-grade-review` fix-loop（49 輪、70+ 條 finding）
> **owner 指示**：繼續跑 fix-loop 至多 32 輪／包，收斂後才 push 開 PR。

---

## 1. 現況（開工前先自己核對，別只信本檔）

三個 worktree 都在 `/Users/bruce/GitHub/bruce1986/` 底下，**未 push、未開 PR**，主 checkout 全程未動：

| 包 | worktree | 分支 | HEAD | 已跑輪數 | 測試 |
|---|---|---|---|---|---|
| W1-A `:decoder-native` | `BopomofoBruce-w1a-decoder-native` | `feat/w1a-decoder-native` | `17f84c8` | 16 | JVM 9＋實機 6/6 |
| W1-B `:theme` | `BopomofoBruce-w1b-theme` | `feat/w1b-theme` | `312d1d0` | 19 | 52 |
| W1-C `:keyboards` | `BopomofoBruce-w1c-keyboards` | `feat/w1c-keyboards` | `0181388` | 14 | 37 |

三包**都已達成 skill 的收斂條件**（連續兩輪零 finding）。owner 要求再往下跑到 32 輪／包。

另有 `BopomofoBruce-lead`（detached，用來在 main 上做 STATUS/WORKLOG 記帳）。

### 環境
- Android SDK：`/opt/homebrew/share/android-commandlinetools`（platform 34/35、NDK `27.2.12479018`、CMake 3.22.1）
- 跑 W1-A 的 gradle **必須**前綴 `PATH="/opt/homebrew/opt/rustup/bin:$PATH"`（Homebrew rust 沒有 Android target）
- 實機 `R6AIB700988748X`（ASUS_AI2302, API 15）可跑 connectedAndroidTest

---

## 2. 操作規則（上一輪踩過的坑，別再踩）

1. **一個 worktree 同時只能有一個 agent 在動。** 上一輪兩度誤判 subagent 停擺而接手，造成一次實際覆寫。判定停擺前：(a) transcript 至少 30 分鐘無寫入，且 (b) `pgrep -f "gradle|cargo"` 確認沒有正在跑的建置。接手前先發 SendMessage 叫停並要它回報未提交的改動。
2. **只認 subagent 的最終訊息。** 背景 agent 會在暫停時先送通知，中間內容可能長得像結論。上一輪就把中間的 `[]` 當成最終結果、誤宣告 W1-A 收斂。prompt 尾端務必加「最終結論只能出現在最後一則訊息」。
3. **每個 reviewer/verifier prompt 都要帶「已被打回票、不准再提」清單**（見 §4），否則同一條誤報每輪重生、loop 永遠不收斂。
4. **fixer 補測試一律要求「證明會紅」**（改壞→紅→還原→綠，並寫出實際輸出）。上一輪抓到至少五條「不可能失敗的測試」。
5. **`prep` 指令要在正確的 worktree 目錄下跑**——同一個 shell 連續跑兩個 prep 會都跑在第一個目錄（踩過兩次）。
6. **STATUS.md / WORKLOG.md 由 lead 在 main 上維護**（`BopomofoBruce-lead` worktree，`git push origin HEAD:main`），子代理不改。理由：三包同時跑會在相鄰三列衝突。**注意**：因此 branch diff 看不到這些更新，reviewer 會誤報「STATUS 沒更新」——那是 diff 視角造成的假象，已發生兩次。
7. 禁止改 `:common`（contracts-v1 凍結）、禁止改 `app/build.gradle.kts`（W3-1 範圍）、禁止動 vendored `libchewing`。

---

## 3. 待 owner 裁決（四項，勿自行決定）

1. **LGPL-2.1**：libchewing 靜態連結，ADR-0001 的動態連結授權前提已失效。owner 已裁示「只記案」，登記為 **W4-D（上架）blocker**。
2. **APK size**：DEVPLAN 門檻「增量 < 4 MB」。加 version script 後 `.so` 從 3.43 MB 降到 1.59 MB，加字典 assets 約 2.08 MB → 單 ABI 約 **3.67 MB，回到門檻內**。但 `:app` 未宣告 `ndkVersion`，`stripReleaseDebugSymbols` 沒跑，實際打包的是 33.8 MB 未剝除 `.so`——**屬 W3-1 範圍，本波未修**。
3. **W1-B 深色主題**：WCAG 1.4.11（3:1）與 AA 文字（4.5:1）在深色下**數學上互斥**（可行亮度區間為空），根因是 `KeyboardColors` 缺 on-candidate-highlight 色。目前採**文字優先**（`#656471`：白字 4.50／背景 2.95）。另一選項 `#6F6A76`（背景 3.26／白字 4.07）與切換方式已寫在 `BuiltInThemes.kt` 註解。
4. **兩位獨立驗證者判斷相反**：`:keyboards` 的 `implementation(project(":common"))` 是否該改 `api`；空白鍵 label 是否沿用 U+3000。

---

## 4. 已被獨立驗證者打回票、**不准再提**（每個 prompt 都要帶）

**W1-A**：無（所有 finding 皆成立）
**W1-B**：`MAX_BLUR_RADIUS_DP = 50f` 缺量測依據；「不擋住底層主題色」的 KDoc；「反序列化會繞過 init 的 require」（已實驗證明會執行）；加 `isDynamic`/`degraded`、匯出 `ThemeJson`/`schemaVersion`、`PhotoBackground` 併進 `StyleSheet`、`ThemeSwatch` 改 public、加 `StyleSheet`→`KeyboardTheme` adapter（皆 W2 範圍）；`:common` 標 `@Stable`；preview 加 try/catch；把 init 改丟 `SerializationException`（已記為 owner 可選）
**W1-C**：`assertThrows(IllegalArgumentException)` 的斷言型別（已實測證明是唯一正確寫法）；加 `schemaVersion`

## 5. 已知且已誠實記載、**不算 finding**

**W1-A**：無 leak 未用 ASan 驗證；CI 未明列 NDK 版本；`bpmf_input` 只回游標所在音節候選；libchewing 內建使用者字典刻意不啟用；跨 process 時 `synchronized` 不夠；`:lint` 需要連網（取捨）；`bpmf_init` 偵測不到「檔案在但內容損毀」；LGPL／APK size／`:app` strip 見 §3
**W1-B**：實機 200 ms 未量測；動態色實際桌布數字未實機驗證；`SrcAtop` alpha=0xFF 會蓋掉底圖（刻意維持）；四個 `@Preview` 未實際 render 驗證
**W1-C**：跨列鍵柱對齊由 renderer 負責；`language_toggle` 的目的地（通用 ABC 鍵盤）不在 W1-C 範圍（W2-B follow-up）；password/url 沒有半形符號頁；注音配列未與原 APK 拍照對照（原 APK 不可得，已用標準大千並經獨立查證）

---

## 6. 這輪反覆出現的兩個失效型態（審查時優先找這兩類）

1. **「不可能失敗的測試」至少五次**——全都是「補測試」這個動作本身產生的，且都製造「已覆蓋」的錯覺。典型：只斷言整個 data class 不相等、守門只量會過的配對、tie-break 用同一個值放兩次、`assertThrows(IAE)` 卻不知 `SerializationException` 是其子類、`nm -D` 只 grep 預期會看到的前綴。
2. **「修正 A 造成缺陷 B」至少六次**——修對比度→強調色自己消失；解耦 lint→APK 打包空字典；排除撞色→分離度倒退；改標籤誠實→把使用者導向更明顯的死路；加返回鍵前→符號頁有去無回。

**每輪 fixer 收工後，下一輪 reviewer 都要明確被問「這些修正有沒有製造新問題」。**

---

## 7. 進 W2 前的建議（給 owner）

DEVPLAN 的驗收標準多為「檔案存在／測試綠」，而本波幾乎所有 high 都是「照驗收標準看完全合格但功能不能用」（密碼鍵盤打不出數字、APK 打包空字典、一聲回錯音節的候選、功能鍵按下看不見字）。建議在 W2 開工前把驗收改寫成**可否證的形式**：「在 X 情境下能打出 Y」「把 Z 改壞時哪條測試會紅」。

W2-B（`:ime`）開工前需先處理：`language_toggle` 沒有目的地鍵盤、`Custom` id 契約（`switch_to_zhuyin`／`switch_back`／`url_insert_dot_com`）、`KeyboardTheme` 是否納入 shapes/typography。
