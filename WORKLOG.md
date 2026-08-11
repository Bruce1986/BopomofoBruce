# WORKLOG — BopomofoBruce

> 每次工作 session 收尾時 append 一節（最新在上）。開工前先讀最近幾節＋[docs/STATUS.md](docs/STATUS.md)。
> 本檔 2026-08-07 補建；06-20 前的細節重建自 git log 與 PR 記錄。

---

## 2026-08-10 ~ 08-11 — W1 三包實作 + gemini-grade-review fix-loop（Claude Code）

### 完成
- **W1 三包全部實作完成**，各自獨立 worktree／分支，**未 push、未開 PR**（等 owner 裁決）：
  - `feat/w1a-decoder-native`（20 commits）— libchewing v0.12.0 → `libbpmf.so`
  - `feat/w1b-theme`（22 commits）— `:theme` 主題引擎
  - `feat/w1c-keyboards`（18 commits）— `:keyboards` 8 份鍵盤定義
- **最大技術風險已去除**：W1-A 在實機（ASUS_AI2302, API 15）跑通 `bpmf_init` + `bpmf_input`，
  輸入「ㄋㄧˇㄏㄠˇ」拿到真實候選 `[好, 郝, 㚼, 㝀]`。
- 三包各跑 10~17 輪 `/gemini-grade-review` fix-loop（Sonnet 廣審 + Opus tracer 深審 + codex 交叉
  檢查 + 獨立驗證者裁決），合計修掉 **60+ 條經獨立驗證的 finding**。

### 關鍵發現（都是「照原驗收標準看完全合格」的東西）
- **W1-A**：測試專用 JNI 符號會進 release APK（含可對任意位址 `free()` 的入口）；字典解壓非原子
  導致壞檔永不自癒；一聲（陰平）會**靜默回傳上一個音節的候選**；`:app` 建置**不會**觸發字典下載
  → APK 打包空 assets（乾淨 checkout 才會現形）；libchewing 內建使用者字典其實從未啟用。
- **W1-B**：`Modifier.blur` 在 minSdk 涵蓋的 API 28–30 完全無效但註解宣稱有 fallback；相片 tint
  用 `SrcAtop` 在 alpha=0xFF（調色盤常態）時會整片蓋掉相片；內建色盤 `keyText` 疊在 `keyAccent`
  上只有 **1.32:1**（AA 要 4.5）；動態取色路徑重現同一缺陷。
- **W1-C**：密碼／URL 鍵盤**打不出任何數字**，唯一出口的符號頁 30 鍵全是全形（誤按送出的是
  U+FF03 之類）；`KeyboardLoader` 的 strict 契約**零測試覆蓋**；符號頁的兩顆切換鍵依契約語意都
  到不了標籤宣稱的目的地；修好之後又發現從 password/url 進符號頁**回不去**。

### 反覆出現的兩個失效型態（值得寫進規範）
1. **「不可能失敗的測試」出現至少五次** —— `weight` 恆真斷言、Light/Dark 只比整個 data class、
   對比度守門只量會過的配對、tie-break 用同一個值放兩次、`assertThrows(IAE)` 卻不知
   `SerializationException` 是其子類。全部都是「補測試」這個動作本身產生的，且都製造了
   「已覆蓋」的錯覺——CI 全綠。
2. **「修正 A 造成缺陷 B」出現至少六次** —— 修對比度→強調色自己消失；解耦 lint→APK 打包空字典；
   排除撞色→分離度倒退；改標籤誠實→把使用者導向更明顯的死路；加返回鍵前→符號頁有去無回。

### 問題 / 決策
- **owner 已裁決**：libchewing 靜態連結造成的 LGPL-2.1 義務**本輪只記案**，已在 ADR-0006 記錄
  「ADR-0001 的動態連結授權前提已失效」並登記為 **W4-D（上架）blocker**。
- W1-B 深色主題下 WCAG 1.4.11（3:1）與 AA 文字（4.5:1）**數學上互斥**（可行亮度區間為空），
  根因是 `KeyboardColors` 缺 on-candidate-highlight 色（contracts-v1 凍結）。目前採**文字優先**，
  兩個候選值與切換方式已寫進 `BuiltInThemes.kt` 註解供 owner 推翻。
- 兩位獨立驗證者對兩條 finding **判斷相反**，未單方裁決、已上呈 owner：
  `:keyboards` 的 `implementation` 是否該改 `api`、空白鍵 label 是否該沿用 U+3000。
- 過程中 lead 兩度誤判 subagent 停擺而接手同一個 worktree，造成一次實際覆寫（W1-B 的
  `candidateHighlight` 色值與測試斷言）。已比對還原、無成果遺失，但停擺判準（22 分鐘無輸出）
  太短，應拉長並在接手前先確認沒有正在跑的建置。

### 下一步
1. owner 決定三包是否開 PR（fix-loop 已收斂，但 `:ime` 不存在使得許多契約無法實測驗證）。
2. 進 W2 前建議先改寫 DEVPLAN 的驗收標準：目前多為「檔案存在／測試綠」，本輪幾乎所有 high
   都是「照驗收標準看完全合格但功能不能用」。建議改成可否證的形式（「在 X 情境下能打出 Y」
   「把 Z 改壞時哪條測試會紅」）。
3. W2-B（`:ime`）開工前需先處理：`language_toggle` 沒有目的地鍵盤、`Custom` id 契約
   （`switch_to_zhuyin`／`switch_back`／`url_insert_dot_com`）、`KeyboardTheme` 是否納入
   shapes/typography。

---

## 2026-08-07 — 全案審查＋文件校正（Claude Code）

### 完成
- 全案審查報告：[docs/REVIEW-ProjectStatus-20260807-0209.md](docs/REVIEW-ProjectStatus-20260807-0209.md)
- STATUS.md：W1-A/B/C 幽靈認領（48 天無 heartbeat、worktree/分支從未建立）依 stale 規則退回 Backlog
- README／project-handbook 進度描述與現實對齊；handbook 廢除第二份任務表、改指 STATUS.md 單一真相來源
- 補建本 WORKLOG

### 問題 / 決策
- 專案自 06-30 停擺 38 天；「續跑 vs 冷凍（＋喚醒條件）」待 owner 拍板
- 三個 W0 殘留 worktree（`.claude/worktrees/`）有未提交殘值：CI cancel-in-progress 修正（最有價值）、UIntHexSerializer 負向測試、ADR-0005 措辭——撿回後才可清 worktree
- 最大技術風險未變：libchewing JNI spike（W1-A）零進度，ADR-0001 的選型假設未經驗證

### 下一步
- owner 拍板方向；若續跑，W1-A 單獨先做去風險

---

## 2026-06-24 ~ 06-30 — 文件收尾

- #6 `.gemini/styleguide.md`（AI review style guide）merge；後續追加 positive-feedback 規則
- #7 DEVPLAN/STATUS retroactive codex review（4 rounds）merge
- 此後無活動

---

## 2026-06-19 ~ 06-20 — W0 骨架期（subagent fanout）

- #1 AI guides/plans 對齊 Android/Kotlin stack
- #2 W0-1 Gradle 骨架、Version Catalog、8 module 空殼（Gemini 12 輪收斂）
- #3 W0-3 GitHub Actions CI（build/lint/test + APK artifact）
- #4 W0-2 `:common` 介面契約 + Fakes + 序列化 round-trip test（tag `contracts-v1`）
- #5 W0-4 ADR-0001~0005 + template
- W1-A/B/C 認領後未實際開工（見 2026-08-07 節）
- 操作教訓已蒸餾至 workspace memory `bopomofobruce-pr-review-loop-lessons`

---

## 2026-05-30 — 立項

- REBUILD-PLAN Original + SoloEdition 兩版計畫；範圍定案：v1 純本地注音 IME，libchewing 後端
