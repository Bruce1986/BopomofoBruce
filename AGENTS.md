# AI 助理協作指南

本專案使用 AI 助理（Claude、GitHub Copilot、Codex 等）進行程式碼審查與開發協作。

---

## 開始工作前：先讀懂這個專案

**在寫任何程式碼或發起任何任務之前，請先閱讀 [`project-handbook.md`](project-handbook.md)。**

重點確認：
1. **這個專案是什麼**（專案身份表格）
2. **這個專案不做的事**（⚠️ 警告列）
3. **目前任務清單**（只做清單裡有的事）

若任務清單是空的，代表這個專案尚未定義任何工作，**請停下來，告知使用者需要先填寫任務清單。**

---

## PR Review 流程

請依以下流程循環執行直到所有 comment 解決：

1. 抓取 PR comment
2. 確認目前遠端分支的最新程式碼
3. 參考 comment 修正程式碼
4. 執行測試
5. 提交並推送程式碼
6. 留言 `/Gemini review`
7. 等待 Gemini 審查完成（通常約 210 秒）後，回到步驟 1

---

## 程式碼審查原則

審查時著重以下幾點，有問題就提出，不要視而不見：

- **正確性**：邏輯是否正確？有無邊界案例未處理？
- **命名**：變數、函式命名是否清晰有語意？
- **錯誤處理**：失敗路徑是否有妥善處理與 log？
- **測試**：新增的函式是否有對應的 unit test？
- **文件一致性**：若有對外介面異動，是否已更新 `../INTERFACES.md`？

---

## 撰寫程式碼的原則

- 以可驗證的事實和嚴謹邏輯為基礎，不捏造資料
- 遇到不確定的地方，直接說明而非自行假設
- 複雜任務先在 `IMPROVEMENT_PLAN.md` 制定計畫再動手
- 重要操作記錄到 `WORKLOG.md`
- 撰寫新 function 時，為其撰寫對應 unit test；若不可行，在 PR 說明原因


---

## PR Review 自動化循環

收到「抓取 review 意見」或 `/Gemini review` 相關指令後，執行以下循環：

### 步驟

1. **抓取 review comments**
   - `gh api repos/{owner}/{repo}/pulls/{pr}/comments` 取得 inline comments
   - `gh api repos/{owner}/{repo}/pulls/{pr}/reviews` 取得 review summary
   - 篩選 `user.login == "gemini-code-assist[bot]"`
   - 用時間戳過濾只處理新的 comments

2. **修改程式碼**
   - 合理建議直接實作
   - 已被 owner 明確拒絕的建議跳過（查看先前對話紀錄）
   - 純文件/風格建議也要處理

3. **驗證**
   - 跑 `ruff check src/ tests/`
   - 跑 `mypy src/autonomous_agent/`
   - 跑 `pytest -v`
   - 三項全部通過才能繼續

4. **Commit & Push**
   - 寫清楚的 commit message（見下方慣例）
   - 推到 origin 和 homee 兩個 remote

5. **觸發下一輪 review**
   - 在 PR 留言 `/Gemini review`

6. **等待**
   - `sleep 210` 秒（約 3.5 分鐘）等待 Gemini 回應
   - 若時間到了沒有新 review，再等 60-120 秒重試

7. **檢查終止條件後回到步驟 1**

### 終止條件

- Gemini 回覆包含 **"I have no feedback"** 或 **"no review comments were submitted"**
- Gemini bot 達到每日額度限制（"daily quota"）
- 使用者手動要求停止

---

## Commit 慣例

```
<type>: <簡述>

<詳細說明>

Co-Authored-By: <agent-name> <noreply@anthropic.com>
```

Type: `fix`, `feat`, `refactor`, `chore`, `docs`, `test`
