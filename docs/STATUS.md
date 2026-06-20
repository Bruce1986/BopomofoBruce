# BopomofoBruce — Work Package Status

**這是 live 檔。** 開工前必讀；認領時必改；merge 後也要改。

> 規則與認領協議：[DEVPLAN-SubagentFanout §10](DEVPLAN-SubagentFanout-20260620-0851.md#10-協調與-visibility--statusmd-live-表格)
> 啟動 prompt 模板：[DEVPLAN-SubagentFanout §6](DEVPLAN-SubagentFanout-20260620-0851.md#6-子代理啟動樣板)

最後一次 lead 巡視：2026-06-20 08:51

---

## 狀態圖例

- 🔵 **Backlog** — 未認領，可被任何人 pre-flight 後認領
- 🟡 **Claimed** — 已認領但尚未開工（pre-flight 通過、改完 STATUS、cd 進 worktree 前）
- 🟠 **In progress** — 開工中（已 cd 進 worktree、在改 code）
- 🟣 **In review** — PR 已開、正在跑 codex / Gemini / CodeRabbit
- ✅ **Merged** — 已 squash merge 進 main（merge 後 24 h 內搬到 Recently merged）
- ⛔ **Blocked** — 阻塞中（必附原因連結；解除後改回 In progress）

---

## 主表

| ID | Wave | 範圍（獨占模組／檔案） | 狀態 | 認領者 | Worktree | 分支 | PR | 更新時間 |
|----|------|----------------------|------|--------|----------|------|----|---------|
| W0-2 | W0 | `:common` 介面契約（凍結） | 🟣 In review | claude-subagent-w0-2 | ../BopomofoBruce-w0-common-contracts | feat/w0-common-contracts | [#4](https://github.com/Bruce1986/BopomofoBruce/pull/4) | 2026-06-20 19:45 |
| W0-3 | W0 | CI workflow（Gradle build/lint/test） | 🟣 In review | claude-subagent-w0-3 | ../BopomofoBruce-w0-ci | feat/w0-ci | [#3](https://github.com/Bruce1986/BopomofoBruce/pull/3) | 2026-06-20 19:45 |
| W0-4 | W0 | ADR-0001 ～ 0005 | 🟣 In review | claude-subagent-w0-4 | ../BopomofoBruce-w0-adr | feat/w0-adr | [#5](https://github.com/Bruce1986/BopomofoBruce/pull/5) | 2026-06-20 19:45 |
| W1-A | W1 | `:decoder-native` libchewing → libbpmf.so | 🔵 Backlog | — | — | — | — | — |
| W1-B | W1 | `:theme` Material You + 自訂相片背景 | 🔵 Backlog | — | — | — | — | — |
| W1-C | W1 | `:keyboards` 注音 4×10、符號、數字、密碼 | 🔵 Backlog | — | — | — | — | — |
| W2-A | W2 | `:decoder` JNI binding + 個人字典（Room） | 🔵 Backlog | — | — | — | — | — |
| W2-B | W2 | `:ime` InputMethodService + Compose IME view | 🔵 Backlog | — | — | — | — | — |
| W2-C | W2 | `:settings` 設定頁 + FirstRun | 🔵 Backlog | — | — | — | — | — |
| W2-D | W2 | emoji 鍵盤 + 簡繁／字串工具（`:common` 例外擴充） | 🔵 Backlog | — | — | — | — | — |
| W3-1 | W3 | `:app` wiring（手動 DI、Manifest 補完、整合） | 🔵 Backlog | — | — | — | — | — |
| W3-2 | W3 | Beta 5 人試打 + P0 修 | 🔵 Backlog | — | — | — | — | — |
| W4-A | W4 | Privacy policy、README 上線版、Play 截圖／資料安全表 | 🔵 Backlog | — | — | — | — | — |
| W4-B | W4 | 無障礙最低限度（TalkBack） | 🔵 Backlog | — | — | — | — | — |
| W4-C | W4 | Macrobenchmark：冷啟動、打字延遲、電量 | 🔵 Backlog | — | — | — | — | — |
| W4-D | W4 | Internal track 上架 | 🔵 Backlog | — | — | — | — | — |

---

## Active worktrees

> 認領 / 開工時 append；merge 後刪。

- W0-2  ../BopomofoBruce-w0-common-contracts  feat/w0-common-contracts  PR #4  agent=claude-subagent-w0-2  since=2026-06-20 19:25
- W0-3  ../BopomofoBruce-w0-ci  feat/w0-ci  PR #3  agent=claude-subagent-w0-3  since=2026-06-20 19:25
- W0-4  ../BopomofoBruce-w0-adr  feat/w0-adr  PR #5  agent=claude-subagent-w0-4  since=2026-06-20 19:25

範例格式：
```
- W1-B  ../BopomofoBruce-w1-theme  feat/w1-theme  PR #12  agent=sonnet-A  since=2026-07-03 09:15
```

---

## Recently merged

> 最近 5 筆。再往前的搬 `docs/devlog/` 歸檔。

- W0-1  feat/w0-gradle-skeleton  PR [#2](https://github.com/Bruce1986/BopomofoBruce/pull/2)  merged 2026-06-20 by claude-lead (squash)  commit=6bd6cda

範例：
```
- W0-1  feat/w0-gradle-skeleton  PR #5   merged 2026-06-30 by lead   commit=abc1234
```

---

## Blockers

> ⛔ 狀態的包必在這裡留條目（為什麼卡、誰要解、ETA）。

_(空)_

---

## Lead 巡視紀錄

> 每週一次。檢查：stale Claimed (> 48 h)、無進度 In progress (> 7 d)、Blockers ETA。

| 日期 | 巡視人 | 動作 |
|------|--------|------|
| 2026-06-20 | Bruce | 初始化 STATUS.md，17 包全部 Backlog |
