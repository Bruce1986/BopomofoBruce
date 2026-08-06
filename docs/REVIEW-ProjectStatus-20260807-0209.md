# BopomofoBruce 專案狀態審查

> 產生時間：2026-08-07 02:09（UTC+8）
> 審查方式：Claude Code 實查本機 repo + origin + GitHub PR/CI 記錄
> 性質：點時記錄（point-in-time）。讀數會過期，續讀前先對照 git log 與 STATUS.md。

---

## TL;DR

**W0（骨架期）品質紮實且全數收線，但專案自 2026-06-30 起停擺 38 天。** W1 三包於 06-20 標記「認領」後從未真正開工——worktree 不存在、分支沒建、零 commit（幽靈認領）。最高技術風險項（libchewing JNI spike，W1-A）至今沒有任何驗證。三個殘留 W0 worktree 內有未提交殘值待撿。

---

## 一、開發目標評估

目標定義清楚且自洽：clean-room 重寫停更的 Google 注音輸入法，差異化在 **UX + 主題 + 隱私**，解碼直接用 libchewing。v1 減法明確（不做拼音/倉頡/手寫/雲端），以單人維護為前提設計，範圍控制合理。**目標本身不需修改。**

**核心風險：整個技術路線押在一個未驗證的假設上。** ADR-0001 拍板選 libchewing，但對應 PoC（原 M0-4「注音→候選詞 logcat」，現為 W1-A `:decoder-native`）零進度。ADR 是在沒有 spike 的情況下寫的——若 libchewing 的 NDK 交叉編譯或 API 對接踩雷，下游 W2-A（JNI binding）、W2-B（IME service）全部連動。

## 二、進度快照（2026-08-07）

### 已完成 — W0 全數 merge（2026-06-20），main CI 綠

| PR | 內容 | 備註 |
|---|---|---|
| #2 | Gradle 骨架、Version Catalog、8 module 空殼 | Gemini 12 輪收斂 |
| #3 | GitHub Actions CI（build/lint/test + APK artifact） | 運作正常 |
| #4 | `:common` 介面契約 + Fakes + 序列化測試 | tag `contracts-v1`；interface-first 正確 |
| #5 | ADR-0001~0005 | 決策留痕 |

之後僅兩筆文件收尾（#6 styleguide 06-24、#7 DEVPLAN codex 修訂 06-30）。**06-30 後零活動。**

### 卡點 — W1 幽靈認領

STATUS.md 記錄 W1-A/B/C 於 06-20 20:56 認領，實查結果：

- `../BopomofoBruce-w1-*` 三個 worktree **不存在**
- origin **沒有任何** `feat/w1-*` 分支
- 認領後 48 天無 heartbeat；STATUS.md 自訂規則為「>48h 未動即 stale、退回 Backlog」，Lead 每週巡視只執行過初始化那一次

→ 本次審查已依 stale 規則把 W1-A/B/C 退回 🔵 Backlog（見 STATUS.md 巡視紀錄 2026-08-07）。

### 時程對照（outside view）

原 roadmap M0 估 2 週；立項（05-30）至今約 10 週，完成度相當於 M0 骨架部分，spike 未動。以原估算落後 M1 完成點 4 週以上。W0 的 interface-first + ADR 紮實度高於一般 M0 水準，算拿時間換了地基品質。

## 三、殘值清單（分支已 squash merge，但以下改動不在 main）

三個殘留 worktree 位於 `.claude/worktrees/`，**清除前先撿殘值**（stale ≠ 作廢）：

1. **`feat-w0-ci`（最有價值）**：`.github/workflows/ci.yml` 的 `cancel-in-progress: ${{ github.event_name == 'pull_request' }}` — 修「push 到 main 的 CI 被下一個 push 取消、留下無終態 commit」的實質 bug。
2. **`feat-w0-common-contracts`**：`SerializationRoundTripTest.kt` 補 `UIntHexSerializer` 錯誤長度 hex 的負向測試（約 43 行 assertThrows 覆蓋）。
3. **`feat-w0-adr`**：ADR-0005 一處措辭修正（省下的 module 應為 `:tv` 非 `:account`）。

建議各開一支小 PR 撿回，再 `git worktree remove` 清掉四個殘留 worktree。

## 四、文件失真（本次已修）

- README「M0 — 立項中」→ 已更新為 W0 完成、W1 未開工
- project-handbook 任務表停在 M0-1 🟡 → 已改指向 STATUS.md 為單一真相來源
- STATUS.md W1 幽靈認領 → 已退回 Backlog
- 根目錄補建 WORKLOG.md（原僅 docs/devlog/ 一檔）

## 五、反方論證（steelman）

「停擺」也可能是**有意的排序決策**：owner 多線並行，本案為作品集性質副業，暫凍未必是錯——此點無法從 repo 查證，屬 owner 判斷。但若是有意冷凍，目前處於「沒有喚醒條件的模糊凍結」，STATUS.md 掛著 Claimed 假象比明確凍結更糟（本次已修掉假象；喚醒條件仍待 owner 補）。

## 六、建議下一步（依優先序，均待 owner 拍板）

1. **決定續跑或冷凍。** 冷凍：在 STATUS.md 記喚醒條件。續跑：進第 2 步。
2. **W1-A（libchewing JNI spike）單獨先做**——整個技術路線的去風險關鍵，不需等 B/C 一起 fanout。
3. **撿回三筆殘值**（CI concurrency 修正優先），清殘留 worktree。
4. 事前預測（供事後校準）：若續跑且 W1-A 兩週內開工，spike 一次通（NDK 建置 + JNI 回傳候選詞）信心約 6 成；主要不確定性在 libchewing CMake 對 Android NDK 工具鏈的相容性。
