# BopomofoBruce — Work Package Status

**這是 live 檔。** 開工前必讀；認領時必改；merge 後也要改。

> 規則與認領協議：[DEVPLAN-SubagentFanout §10](DEVPLAN-SubagentFanout-20260620-0851.md#10-協調與-visibility--statusmd-live-表格)
> 啟動 prompt 模板：[DEVPLAN-SubagentFanout §6](DEVPLAN-SubagentFanout-20260620-0851.md#6-子代理啟動樣板)
<!-- Doc-naming pushback (Gemini #3496050473): DEVPLAN 檔名含時間戳 (YYYYMMDD-HHMM) 是專案強制 doc-naming 慣例（CLAUDE.md「檔名必須加上日期時間」），不接受改為無時間戳檔名。link rot 是已知 trade-off；重命名時須同步更新本檔所有指向 DEVPLAN 的引用，以及 project-handbook.md / AGENTS.md / GEMINI.md / docs/adr/。 -->

最後一次 lead 巡視：2026-11-20 01:55

---

## 狀態圖例

- 🔵 **Backlog** — 未認領，可被任何人 pre-flight 後認領
- 🟡 **Claimed** — 已認領但尚未開工（pre-flight 通過、改完 STATUS、cd 進 worktree 前）
- 🟠 **In progress** — 開工中（已 cd 進 worktree、在改 code）
- 🟣 **In review** — PR 已開、正在跑 codex / Gemini / CodeRabbit
- ✅ **Merged** — 已 squash merge 進 main（merge 後 24 h 內搬到 Recently merged）
- ⛔ **Blocked** — 阻塞中（必附原因連結；解除後改回 In progress）

**「更新時間」是 heartbeat 欄**：認領者每次 commit / push / 開 PR / 走 review loop 一輪都要順手刷新此欄；超過 48 h 未動視為 stale，Lead 巡視時改回 🔵 Backlog（見 [DEVPLAN §10.4](DEVPLAN-SubagentFanout-20260620-0851.md#104-子代理-vs-lead-的責任分配)）。即使只是「review loop 還在等 Gemini」也要更新。
<!-- Same doc-naming pushback applies to the §10.4 link above. -->


---

## 主表

| ID | Wave | 範圍（獨占模組／檔案） | 狀態 | 認領者 | Worktree | 分支 | PR | 更新時間 |
|----|------|----------------------|------|--------|----------|------|----|---------|
| W1-A | W1 | `:decoder-native` libchewing → libbpmf.so | 🟣 In review | claude-lead | ../BopomofoBruce-w1a-decoder-native | feat/w1a-decoder-native | [#8](https://github.com/Bruce1986/BopomofoBruce/pull/8)（CI 綠，待 owner merge） | 2026-11-20 01:55 |
| W1-B | W1 | `:theme` Material You + 自訂相片背景 | 🟣 In review | claude-lead | ../BopomofoBruce-w1b-theme | feat/w1b-theme | [#9](https://github.com/Bruce1986/BopomofoBruce/pull/9)（CI 綠，待 owner merge） | 2026-11-20 01:55 |
| W1-C | W1 | `:keyboards` 注音 4×10、符號、數字、密碼 | ✅ Merged | sonnet-C | — | feat/w1c-keyboards | [#10](https://github.com/Bruce1986/BopomofoBruce/pull/10) | 2026-09-16 |
| W2-A1 | W2 | `:decoder` JNI binding + `ZhuyinDecoder` 真實實作（疊在 #8 上） | 🟣 In review | claude-lead | ../../worktrees/BopomofoBruce-w2a-decoder-jni | feat/w2-decoder-jni | [#16](https://github.com/Bruce1986/BopomofoBruce/pull/16)（base＝#8 分支） | 2026-11-20 17:23 |
| W2-A2 | W2 | `:decoder` 個人字典（Room） | 🔵 Backlog | — | — | — | — | — |
| W2-B1a | W2 | `:ime` processor chain dispatcher（純 Kotlin） | 🟣 In review | claude-lead | ../../worktrees/BopomofoBruce-w2b1a-chain | feat/w2-ime-processor-chain-core | [#13](https://github.com/Bruce1986/BopomofoBruce/pull/13) | 2026-11-20 10:40 |
| W2-B1b | W2 | `:ime` 預設 processor（疊在 #13 上） | 🟣 In review | claude-lead | ../../worktrees/BopomofoBruce-w2b1b-defaults | feat/w2-ime-default-processors | [#14](https://github.com/Bruce1986/BopomofoBruce/pull/14) | 2026-11-20 10:40 |
| W2-B2/B3 | W2 | `:ime` InputMethodService＋Compose 元件 | ⛔ Blocked | — | — | — | — | 待 owner 裁決 [W2 前置 §1–§3、§5a](IMPROVEMENT_PLAN-20261120-0145-W2-prereq.md) |
| W2-C | W2 | `:settings` 設定頁 + FirstRun | 🔵 Backlog | — | — | — | — | — |
| W2-D1 | W2 | `:common` 全形／半形、注音 normalize | 🟣 In review | claude-lead | ../../worktrees/BopomofoBruce-w2d-text-utils | feat/w2-common-text-utils | [#12](https://github.com/Bruce1986/BopomofoBruce/pull/12) | 2026-11-20 10:40 |
| W2-D2 | W2 | emoji／kaomoji 鍵盤（簡繁轉換待裁決） | 🔵 Backlog | — | — | — | — | — |
| W3-1 | W3 | `:app` wiring（手動 DI、Manifest 補完、整合） | 🔵 Backlog | — | — | — | — | — |
| W3-2 | W3 | Beta 5 人試打 + P0 修 | 🔵 Backlog | — | — | — | — | — |
| W4-A | W4 | Privacy policy、README 上線版、Play 截圖／資料安全表 | 🔵 Backlog | — | — | — | — | — |
| W4-B | W4 | 無障礙最低限度（TalkBack） | 🔵 Backlog | — | — | — | — | — |
| W4-C | W4 | Macrobenchmark：冷啟動、打字延遲、電量 | 🔵 Backlog | — | — | — | — | — |
| W4-D | W4 | Internal track 上架 | 🔵 Backlog | — | — | — | — | — |

---

## Active worktrees

> 認領 / 開工時 append；merge 後刪。

- W1-A  ../BopomofoBruce-w1a-decoder-native  feat/w1a-decoder-native  PR #8  agent=claude-lead  since=2026-08-10 15:03  heartbeat=2026-11-20 01:55（待 owner merge）
- W1-B  ../BopomofoBruce-w1b-theme  feat/w1b-theme  PR #9  agent=claude-lead  since=2026-08-10 15:03  heartbeat=2026-11-20 01:55（待 owner merge）
- W2-A1  ../../worktrees/BopomofoBruce-w2a-decoder-jni  feat/w2-decoder-jni  PR #16  agent=claude-lead  since=2026-11-20 01:40
- W2-B1a  ../../worktrees/BopomofoBruce-w2b1a-chain  feat/w2-ime-processor-chain-core  PR #13  agent=claude-lead  since=2026-11-20 01:40
- W2-B1b  ../../worktrees/BopomofoBruce-w2b1b-defaults  feat/w2-ime-default-processors  PR #14  agent=claude-lead  since=2026-11-20 01:40
- W2-D1  ../../worktrees/BopomofoBruce-w2d-text-utils  feat/w2-common-text-utils  PR #12  agent=claude-lead  since=2026-11-20 01:40

> 註：本波三包的 STATUS 記帳由 lead 統一維護（三個子代理同時開工，避免同一檔案三方衝突）；子代理不改本檔。
> `.claude/worktrees/` 下四個 W0 殘留 worktree 仍在，其中三個含未提交殘值，見 [REVIEW-ProjectStatus-20260807-0209.md §三](REVIEW-ProjectStatus-20260807-0209.md)，撿完殘值後清除。

範例格式：
```
- W1-B  ../BopomofoBruce-w1-theme  feat/w1-theme  PR #12  agent=sonnet-A  since=2026-07-03 09:15
```

---

## Recently merged

> 最近 5 筆。再往前的搬 `docs/devlog/` 歸檔。

- W1-C  feat/w1c-keyboards  PR [#10](https://github.com/Bruce1986/BopomofoBruce/pull/10)  merged 2026-09-16 by Bruce1986 (squash)  commit=9442d90
- W0-1  feat/w0-gradle-skeleton  PR [#2](https://github.com/Bruce1986/BopomofoBruce/pull/2)  merged 2026-06-20 by claude-lead (squash)  commit=6bd6cda
- W0-3  feat/w0-ci  PR [#3](https://github.com/Bruce1986/BopomofoBruce/pull/3)  merged 2026-06-20 by claude-lead (squash)  commit=eade2b0
- W0-4  feat/w0-adr  PR [#5](https://github.com/Bruce1986/BopomofoBruce/pull/5)  merged 2026-06-20 by claude-lead (squash)  commit=ac0e9a2
- W0-2  feat/w0-common-contracts  PR [#4](https://github.com/Bruce1986/BopomofoBruce/pull/4)  merged 2026-06-20 by claude-lead (squash)  commit=4c75782  tag=contracts-v1

範例（格式對齊現有列）：
```
- <ID>  <branch>  PR [#<N>](url)  merged YYYY-MM-DD by <merger> (squash)  commit=<sha7>
```

---

## Blockers

> ⛔ 狀態的包必在這裡留條目（為什麼卡、誰要解、ETA）。

- W2-B2/B3：`language_toggle` 目的地、toggle 單／雙向、`Custom` id 語意、主題 shapes/typography，以及 §5a B1-1／B1-3／B1-9 待 owner 裁決（[W2 前置](IMPROVEMENT_PLAN-20261120-0145-W2-prereq.md)）。

---

## Lead 巡視紀錄

> 每週一次。檢查：stale Claimed (> 48 h)、無進度 In progress (> 7 d)、Blockers ETA。

| 日期 | 巡視人 | 動作 |
|------|--------|------|
| 2026-06-20 | Bruce | 初始化 STATUS.md，17 包全部 Backlog |
| 2026-08-07 | claude-lead（審查） | W1-A/B/C 幽靈認領（06-20 認領後 48 天無 heartbeat、worktree/分支從未建立）依 stale 規則退回 Backlog；全案審查報告見 [REVIEW-ProjectStatus-20260807-0209.md](REVIEW-ProjectStatus-20260807-0209.md)；續跑 vs 冷凍待 owner 拍板 |
| 2026-11-20 | claude-lead | 主表對齊現況（#8／#9 待 merge、#10 已合併）；W2 拆成 A1／A2／B1／B2-B3／D1／D2，A1、B1、D1 開工；B2/B3 等 [W2 前置裁決](IMPROVEMENT_PLAN-20261120-0145-W2-prereq.md) |
