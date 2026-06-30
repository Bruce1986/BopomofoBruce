# BopomofoBruce 開發協作手冊

## 專案身份

| 項目 | 說明 |
|------|------|
| **專案名稱** | BopomofoBruce |
| **在生態系統中的角色** | 開源繁體中文（注音）Android 輸入法。Clean-room 重寫已停更的 Google 注音輸入法。 |
| **開發優先順序** | 個人作品集 / 副業專案 |
| **目前階段** | M0 — 立項中 |
| **架構說明** | [README.md](README.md)、[docs/REBUILD-PLAN-SoloEdition-20260530-1325.md](docs/REBUILD-PLAN-SoloEdition-20260530-1325.md) |

> ⚠️ **此專案不做的事：**
> 1. 不做雲端同步 / 後端服務（v1 純本地）
> 2. 不做手寫 / 滑動輸入 / 語音輸入（留 v2）
> 3. 不做 TV / 平板 / 折疊裝置專屬 UI（v1 只手機軟鍵盤）

---

## 任務清單

| 任務編號 | 說明 | 狀態 | 依賴 / 備註 |
|----------|------|------|-------------|
| M0-1 | GitHub repo 公開、LICENSE、.gitignore | 🟡 | — |
| M0-2 | Gradle 骨架 + Version Catalog | ⏳ | — |
| M0-3 | CI workflow（Gradle build + Android Lint + unit test）；首次 push workflow 檔需先 `gh auth refresh -s workflow` | ⏳ | M0-2 |
| M0-4 | libchewing JNI spike：注音→候選詞 logcat | ⏳ | M0-2 |
| M0-5 | ADR-0001 為何選 libchewing | ⏳ | M0-4 |

**狀態圖例：** ⏳ 待開始 ｜ 🟡 進行中 ｜ 🔍 審核中 ｜ ✅ 完成 ｜ ⛔ 阻塞

---

## 開發流程

### 1. 開始一個任務

```bash
git checkout main && git pull
git checkout -b {{類型}}/{{編號}}-{{簡短說明}}
# 類型建議：feature / fix / design / refactor / docs
```

### 2. 完成後發 PR

PR 標題格式：`[{{類型}}] {{說明}} (#{{編號}})`

PR 說明應包含：
- 做了什麼
- 對外介面是否有異動
- 如何驗證（在哪台手機測過、是否有 unit test）

### 3. Review

Solo dev 階段：自己 review、留 24 小時冷卻再 merge。
若公開後有貢獻者：留言 `/Gemini review` 觸發 AI review。

直接 push 到 `main` 僅限文件小修正（typo 等級），功能性變更一律走 PR。

**例外**：`docs/STATUS.md` 的下列 live bookkeeping 動作被視為 typo 級，允許直推 main（commit 必須**只**動 `docs/STATUS.md`、type 用 `chore(status):`）：

- 主表任意列的「狀態 / 認領者 / Worktree / 分支 / PR / 更新時間」欄位更新
- 「Active worktrees」段落 append / delete 一行
- 「Recently merged」段落搬入新列（並順手把超過 5 筆的舊列移到 `docs/devlog/status-archive.md`，依時間序 append；若該檔不存在則建立）
- 「Blockers」段落新增 / 移除 / 更新原因連結
- 「Lead 巡視紀錄」段落 append 一列

任何 schema 變動、章節結構、圖例文字、範例格式變更仍走 PR。詳見 [DEVPLAN §10.2](docs/DEVPLAN-SubagentFanout-20260620-0851.md#102-機制)。

<!-- Doc-naming: DEVPLAN 檔名含時間戳 (YYYYMMDD-HHMM) 是專案強制慣例。重命名時須更新所有引用點（本檔、docs/STATUS.md、AGENTS.md、GEMINI.md、docs/adr/）。連結 rot 是接受的 trade-off。 -->

---

## 程式碼規範

- Kotlin 命名：函式以動詞開頭、class 名詞、不縮寫
- 用 `Result<T>` / sealed class 表達錯誤，**禁止 swallow exception**
- 不留死 code、不留 TODO 在 main 分支（改用 GitHub issue）
- 每個 decoder/keyboard 邏輯都要對應 unit test
- 不寫註解描述「做了什麼」；只有「為何這樣做」才寫
- IME view 任何 layout 變動必須在 Pixel 4a + Pixel 8 兩支實機測過

### PR Review 確認清單

- [ ] 功能符合預期？
- [ ] 命名清晰？
- [ ] 有無潛在 Bug？
- [ ] 是否新增 IME 對外行為（鍵盤布局、候選順序）？→ 需錄影 demo
- [ ] APK 大小變化？→ 若 > 100 KB 需在 PR 說明

---

## 工作日誌

每週五更新一次（短到一句話也 OK）。

```markdown
## YYYY-MM-DD

### 完成
- 具體描述

### 問題 / 決策
- 為什麼這樣選

### 下週打算
- ...
```
