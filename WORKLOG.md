# WORKLOG — BopomofoBruce

> 每次工作 session 收尾時 append 一節（最新在上）。開工前先讀最近幾節＋[docs/STATUS.md](docs/STATUS.md)。
> 本檔 2026-08-07 補建；06-20 前的細節重建自 git log 與 PR 記錄。

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
