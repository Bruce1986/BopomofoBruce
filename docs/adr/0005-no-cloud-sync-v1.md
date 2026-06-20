# ADR-0005：v1 不做雲端同步、不打網路、不接帳號

- 日期：2026-06-20
- 狀態：Accepted
- 提議者：Bruce
- 相關：[ADR-0002](0002-eight-modules-not-thirty.md)（沒有 `:sync` / `:metrics` module 就是這條決定的具現化）

## Context（背景）

原 Google 注音 APK 的雲同步是它最大的 framework 包袱（[REBUILD-PLAN-Original §1.3, §1.4](../REBUILD-PLAN-Original-20260530-1310.md#13-應用程式元件取自-androidmanifest)）：
- `SyncService`（SyncAdapter）綁 Google 帳號 → 個人字典跨裝置同步
- `libs.dataservice`：帳號登入、Omaha 自動更新、雲端字典下載
- 權限清單包含 `INTERNET` / `READ_CONTACTS` / `USE_CREDENTIALS` / `GET_ACCOUNTS` / `MANAGE_ACCOUNTS` / `READ_SYNC_SETTINGS` / `WRITE_SYNC_SETTINGS`
- Firebase IID、GCM JobDispatcher、Phenotype A/B、Primes 遙測

公司版 plan（[Original §M7](../REBUILD-PLAN-Original-20260530-1310.md#m7--同步與隱私3-週)）想做「個人字典端對端加密 + Drive AppData / WebDAV / Self-host 多後端」，估 3 週、且預設關閉。

Solo Edition 整個劃掉雲端 + 帳號相關（[SoloEdition §1, §5](../REBUILD-PLAN-SoloEdition-20260530-1325.md#5-不做清單solo-dev-的最重要條款)）。理由綜合：

**隱私第一**
- [README](../../README.md) 對使用者的承諾就是「100% 離線；零網路請求」
- [project-handbook.md](../../project-handbook.md) 明列「此專案不做的事 #1：不做雲端同步 / 後端服務（v1 純本地）」
- [AGENTS.md §專案特定注意事項](../../AGENTS.md#專案特定注意事項) 寫死「不打網路、不接 Firebase、不接 Google Sign-In」並指示 review 應拒絕涉及網路/遙測的建議
- 輸入法本質上看得到使用者打的所有字 — 任何網路存取都是 trust 風險。「沒網路權限」是最強的隱私聲明，比任何隱私政策都可信。

**Solo dev 承受不起的後續責任**
- 雲同步要解：conflict resolution（多裝置同時加詞）、E2EE（密鑰管理 / 設備配對 / lost-device recovery）、account model（自建？OAuth？WebDAV？）、伺服器費用、客服回應、GDPR / 個資法的 data deletion API、滲透測試、滲透事件處理
- 任何一塊出包 = 使用者個人字典外洩或遺失。Solo dev 業餘時間玩出資安事件成本不可承受
- [SoloEdition §5](../REBUILD-PLAN-SoloEdition-20260530-1325.md#5-不做清單solo-dev-的最重要條款) 已明列「雲同步：隱私風險、伺服器費用、客服責任」三項否決理由

**v1 沒有也活得下去**
- 個人字典局限本機 = 換手機要重新累積詞頻：可接受。對「我自己 + 5 個 beta 朋友」這個目標群體屬於小痛點，不是 blocker
- v1 沒有「我手機掉了字典就沒了」的場景 — 換手機重建詞頻列為已知 trade-off（個人字典首次幾天累積即可堪用，beta 朋友群可承受）
- F-Droid 上架對「無網路權限」app 有加分（reproducible build + zero-tracking 雙重 badge）

**差異化敘事一致**
- BopomofoBruce 的市場定位（[README §Why another Zhuyin IME](../../README.md#why-another-zhuyin-ime)）：對標 Gboard 的「隱私顧慮」、對標 Google 注音的「無法上 F-Droid」。把網路功能砍乾淨同時解這兩個痛點

## Decision（決定）

**v1 不做雲端同步、不打網路、不接帳號**。具體規範：

- AndroidManifest **不申請** `INTERNET` permission（Android 系統強制 — 沒申請就連 socket 都開不了）
- 不引入任何網路 SDK：Retrofit、OkHttp、Ktor、Firebase 全部不准
- 不接 Google Sign-In、Google Play Services Auth、Drive API
- 不接遙測 / crash reporting SDK：不裝 Sentry、Firebase Crashlytics、自架 OpenTelemetry。**唯一例外**：Play Console 內建的 ANR / Crash 收集（這是 Play Store 平台層、不算 app 主動上傳）
- 不做 OTA self-update（Omaha-like）— 更新走 Play Store / F-Droid 標準管道
- 個人字典純本機 Room；**Android Auto Backup 同樣禁用**（W0-1 已 commit `android:allowBackup="false"`）— 系統層雲端備份也是上傳通道，且使用者無法逐 app 細控；v1 一致拒絕任何資料離開設備
- 主題的相片背景：使用者選的圖讀自本地 `MediaStore`，不上傳、不雲端 sync
- 任何 review 建議（人類或 AI）若涉及網路請求、遙測、雲同步、第三方 SDK 連線 → 直接 reject（[AGENTS.md](../../AGENTS.md#專案特定注意事項) 已明文）

**範圍邊界**：本 ADR 限定 v1。v2 可能評估「使用者主動 opt-in、單一 backend、E2EE、可選自架」的同步功能 — 但開啟那個討論時必須開新 ADR 同時否決本 ADR 對應條款，並更新 [README](../../README.md) 對使用者的承諾。

## Consequences（後果）

**正面**
- AndroidManifest 無 `INTERNET` permission = 使用者看 app info 時直接看到「無網路權限」— 是最強的隱私廣告
- 沒有 `:sync` / `:metrics` / `:account` module 要寫（[ADR-0002](0002-eight-modules-not-thirty.md) 8 module 切分中省下 3 個）
- 不需處理 conflict resolution / key management / account recovery / GDPR data export / 滲透事件
- 沒有伺服器成本：v1 永久零營運成本
- 上架審查簡單：Play Console Data Safety 表格幾乎全填「不收集」；F-Droid 可拿 zero-tracking badge
- 對 reviewer / AI 助理是強約束：[AGENTS.md](../../AGENTS.md#專案特定注意事項) 明文後，任何包含網路代碼的 PR 都會被自動 reject
- 對使用者信任：「輸入法看到我所有打字 + 不打網路」= 信任成本最低

**負面**
- 換手機 = 個人字典詞頻**直接歸零**（v1 連 Auto Backup 都禁用）— 已接受 trade-off；換手機重建詞頻週期是「幾天到幾週」範圍，beta 朋友群可承受
- 無法用「跨裝置同步」當市場賣點 — Gboard 有
- v2 若加同步，是「新增 INTERNET permission」這種**會嚇到既有使用者**的破壞性變更（Android Play Store 會在 update 時警示新增權限）
- 無法收集匿名遙測 → 不知道實際使用者群有多少、平均輸入速度、哪個鍵按錯最多 — 產品決策只能憑直覺 + beta 5 人反饋
- crash report 只能靠 Play Console（覆蓋率不如自家 Sentry）

**開放問題 / 風險**
- 若 v2 評估同步：必須在本 ADR superseded 之前回答「為什麼信任成本上升的代價值得」。Bar 很高
- F-Droid 上架的 reproducible build 仍需獨立 ADR / 工程努力 — 本 ADR 只保證「沒網路權限」這個必要條件成立
- **重評觸發條件**：(1) ≥ 5 個獨立使用者明確要求同步且願意承擔信任成本上升；(2) 出現「個人字典遺失影響日常使用」的具體案例 ≥ 3 次。任一條觸發 → 開新 ADR 評估 v2 同步方案（屆時也可順便重評是否要 opt-in Auto Backup 走系統層）

## Alternatives considered（替代方案）

- **公司版方案：E2EE + Drive AppData + WebDAV + Self-host 多後端**（[Original §M7](../REBUILD-PLAN-Original-20260530-1310.md#m7--同步與隱私3-週)）：3 週工時遠超 [Solo 8 hr/週](../REBUILD-PLAN-SoloEdition-20260530-1325.md#0-reality-check為什麼要簡化) 預算；E2EE key management 一旦出包不可逆；多 backend 是 plugin 化的 YAGNI。**否決**。
- **單一 Google Drive AppData 同步（最小可行）**：仍需 OAuth、Play Services 依賴、Drive API quota、conflict resolution、INTERNET permission。砍掉所有 v1 的隱私敘事換來「跨裝置同步個人字典」這個小痛點 — 不划算。**否決**。
- **「可選 opt-in」同步**：表面上「使用者不用就沒風險」。實際上：(1) 程式碼裡有網路 stack 就有 supply chain 風險（第三方 lib 後門）；(2) AndroidManifest 申請了 `INTERNET` permission 後使用者無法分辨「opt-in 是否真的關著」；(3) 開發 + 維護成本與 mandatory 同步幾乎相同。**否決**。
- **單純加「個人字典本機匯出/匯入（JSON 檔）」**：使用者手動換手機時自己備份。這個**不違反**本 ADR（無網路、無帳號、純本地檔案操作），未列入 v1 範圍但 v1.5 可加。**保留為 v1.5 候選**，不需 ADR 否決。

## References

- [REBUILD-PLAN-SoloEdition §1 v1.0 範圍](../REBUILD-PLAN-SoloEdition-20260530-1325.md#1-v10-範圍mvp) — v1 不做雲端同步
- [REBUILD-PLAN-SoloEdition §5 不做清單](../REBUILD-PLAN-SoloEdition-20260530-1325.md#5-不做清單solo-dev-的最重要條款) — 雲同步否決理由
- [REBUILD-PLAN-Original §M7 同步與隱私](../REBUILD-PLAN-Original-20260530-1310.md#m7--同步與隱私3-週)（被取代的公司版方案）
- [README §Planned features](../../README.md#planned-features-v1) — "100% 離線；零網路請求"
- [project-handbook.md §此專案不做的事](../../project-handbook.md) #1
- [AGENTS.md §專案特定注意事項](../../AGENTS.md#專案特定注意事項) — "不打網路、不接 Firebase、不接 Google Sign-In"
- W0-1 [app/src/main/AndroidManifest.xml](../../app/src/main/AndroidManifest.xml)：`android:allowBackup="false"` 是本 ADR 的具現化
