# ADR-0002：採用 8 個 Gradle module，不是公司版規劃的 30+

- 日期：2026-06-20
- 狀態：Accepted
- 提議者：Bruce
- 相關：[ADR-0001](0001-libchewing-decoder-backend.md)（decoder 砍掉自寫 LM 後不需要 `:decoder-data` / `:dictionary-tools` 兩 module）、[ADR-0004](0004-no-hilt-manual-di.md)（手動 DI 規模就靠 8 module 撐住）

## Context（背景）

[公司版 plan §4](../REBUILD-PLAN-Original-20260530-1310.md#4-gradle-module-切分) 切了 30+ 個 Gradle module，例如：

```
:framework-core :framework-ui :framework-input
:proc-output :proc-zh-zhuyin :proc-zh-pinyin :proc-zh-cangjie :proc-zh-handwriting
:proc-en-latin :proc-shared
:decoder-jni :decoder-native :decoder-data
:hw-mlkit :hw-tflite
:gesture-decoder
:theme-engine :theme-presets :theme-editor
:kb-zh-zhuyin :kb-zh-pinyin :kb-zh-cangjie :kb-en-qwerty :kb-en-9key
:kb-symbols :kb-emoji :kb-kaomoji :kb-numeric :kb-phone :kb-password :kb-datetime :kb-url
:settings :sync :metrics :tv :common :dictionary-tools
```

這個切法在「1 FTE × 8 個月、有未來社群貢獻者要 plugin」的前提下合理：每個 module 可獨立給不同貢獻者、可獨立發 library、可被其他 IME（粵拼、台羅）重用。

但在 [SoloEdition §0 Reality Check](../REBUILD-PLAN-SoloEdition-20260530-1325.md#0-reality-check為什麼要簡化) 列出的真實前提下完全垮掉：

| 公司版預設 | Solo 現實 |
|---|---|
| 1 FTE × 8 個月 | 每週 6–10 小時 × 12–18 個月 |
| 多輸入法（注音/拼音/倉頡/手寫） | v1 只做注音 |
| 多人貢獻、要 plugin 介面 | 一人維護、預期 PR 0–3 個/年 |
| 需要 module 重用給其他專案 | YAGNI |

30 module 對 solo dev 的具體代價：

- **Gradle config time**：每個 module 一份 `build.gradle.kts`、一輪 plugin apply、一輪 dependency resolution。30 個 module 在 M1 mac 上 config 階段可能 > 15 秒，IDE sync 體感更差。
- **維護成本**：30 份 `build.gradle.kts` 同步 Compose BOM 版本、ktfmt config、Android Lint baseline、minSdk/targetSdk — 升一次 AGP 改 30 個檔。Version Catalog 解決部分但不是全部。
- **YAGNI plugin 邊界**：80% 的 plugin 抽象（`:proc-zh-*`、`:hw-*`、`:kb-zh-*`、`:theme-presets`）是為「未來可能有別的實作」設計的，但 v1 永遠只有一個實作，這些抽象就是死重。
- **fan-out PM 友善度**：[DEVPLAN §2 模組依賴圖](../DEVPLAN-SubagentFanout-20260620-0851.md#2-模組依賴圖) 把 8 module 拆成 W0–W4 五個 wave 還算清晰；30 module 拆 wave 會炸成 12 個 wave + 巨量 cross-package 依賴，子代理協調成本爆表（衝突邊界 = 模組邊界，模組多 = 衝突多）。

關鍵洞察：**模組邊界 = 衝突邊界**。多人協作時邊界多是好事（隔離），單人/少量代理協作時邊界多是 pure overhead（同步、wiring、reasoning 三重）。

## Decision（決定）

**v1 採用 8 個 Gradle module，刻意保持精簡**：

```
:app                  Manifest、Application、IME Service entrypoint、wiring
:ime                  InputMethodService + Compose IME view + 候選列
:keyboards            注音 4×10、符號、emoji、數字、密碼（全合一）
:decoder              JNI binding + libchewing wrapper + 個人字典（Room）
:decoder-native       libchewing .so 編譯（CMake + externalNativeBuild）
:theme                Material 3 主題、Style schema、相片背景
:settings             Compose 設定頁、FirstRun
:common               介面契約、KeyData、Candidate、字串/簡繁工具、Fake 實作
```

對應原則：
- **每個 module 是一個 wave-1/wave-2 工作包的天然單位**（見 [DEVPLAN §4](../DEVPLAN-SubagentFanout-20260620-0851.md#4-wave-詳細工作包)）
- **同類東西合一個 module**：所有鍵盤定義（注音 + 符號 + 數字 + 密碼 + emoji）合在 `:keyboards`，不再拆 `:kb-*` × N 個
- **多輸入法不預先抽 plugin 介面**：v1.5 加拼音/倉頡時直接擴 `:keyboards` + `:decoder`，不另開 module
- **無 `:framework-*` 三件套**：framework concerns 都收在 `:ime`
- **無 `:metrics` / `:sync` / `:tv`**：v1 沒做這些功能，沒對應 module（見 [ADR-0005](0005-no-cloud-sync-v1.md)）

**重要邊界 — 避免 `:ime` → `:settings` 依賴**

`:settings` 同時含 Compose UI（設定頁、FirstRun）與設定資料層（DataStore Schema、`SettingsRepository`）。`:ime` 服務需要讀設定（鍵盤布局選擇、震動強度、候選字大小等），但**不該**為此依賴 `:settings`（不該把 UI 模組塞進核心執行期相依鏈）。

解法：[W2-C 開工時](../DEVPLAN-SubagentFanout-20260620-0851.md#w2-c--settings-compose-設定--firstrun)，把 `SettingsRepository` 介面 + DataStore 實作下沉到 `:common`，`:settings` 只留 UI、`:ime` 直接依賴 `:common`。此事不另開 ADR，但 W2-C reviewer 必檢查。

## Consequences（後果）

**正面**
- Gradle config + IDE sync 時間明顯較公司版方案快（W0-1 實測 `./gradlew help` Configuration Cache hit 後 < 1 秒）
- Fan-out 友善：W1 三包 + W2 四包剛好對到 7 個 leaf/mid module，每包獨占一個 module，零檔案衝突（除介面契約外）— 見 [DEVPLAN §5.4 衝突風險表](../DEVPLAN-SubagentFanout-20260620-0851.md#54-衝突風險與緩解)
- 心智負擔低：1 小時內可在腦中跑完整個依賴圖
- 升版工作量小：升 AGP / Compose BOM 只改 8 份 build script
- Reviewer/codex 看 PR 時 module scope 一目瞭然

**負面**
- `:keyboards` module 內部會堆很多種鍵盤定義；當 v1.5 加拼音/倉頡時，這個 module 可能膨脹到不舒服 — 屆時需重審是否拆出 `:keyboards-zh-input`
- `:decoder` 同時負責 JNI binding + 個人字典 Room schema：兩種完全不同性質的工作；Room 編譯時間會拖慢 `:decoder` 的 incremental build
- 沒有 `:plugin-api`：將來若有人想做別的 decoder（rime、moe-dict），改造成 plugin 架構是大手術
- 失去「library 發佈」可能性：v2 若想把 `:theme` 釋出給其他 IME 用，需要先抽出純介面 module

**開放問題 / 風險**
- v1.5 拼音/倉頡加入後 `:keyboards` 與 `:decoder` 會不會撐爆？若 `:keyboards` 超過 ~3000 LOC 或 `:decoder` 出現多個獨立 decoder backend，重評拆分
- `:common` 同時放介面契約 + emoji 表 + 簡繁工具 + Fake 實作：[DEVPLAN W2-D](../DEVPLAN-SubagentFanout-20260620-0851.md#w2-d--emoji--symbol-擴充與-common-helper) 已預期它會被多包碰；若 W1/W2 後 `:common` 變垃圾桶，v1.5 重審切分
- **重評觸發**：總 module 數因功能擴張需 > 12 個時，重審是否需要中間層 `:framework`

## Alternatives considered（替代方案）

- **30 module 公司版切分**：上面 Context 已詳述否決理由 — overhead 對 solo dev 不划算，且預先 plugin 化是 YAGNI。
- **單 module（all-in-one `:app`）**：另一極端。Test isolation 變差（鍵盤定義測試會把 Compose IME view 也拉進測試 classpath）；無法用 Gradle 平行 build；fan-out 無從拆 worktree（每個 PR 都會碰 `:app/build.gradle.kts`）。否決。
- **3 module（`:app` + `:engine` + `:ui`）**：足夠精簡但失去 fan-out 邊界。3 個 module 對應不到 [DEVPLAN W1/W2 七個工作包](../DEVPLAN-SubagentFanout-20260620-0851.md#3-wave-結構總覽)，子代理無法獨佔分支。8 module 是 fan-out 友善的最小數字。
- **依「特性」切（feature module）而非依「技術層」切**：例如 `:feat-zhuyin-input` / `:feat-emoji` / `:feat-theme` 各自包 UI + logic。Compose IME view 必須單一進入點（`InputMethodService` 子類），feature module 反而難拆。否決。

## References

- [REBUILD-PLAN-SoloEdition §3 Gradle Module](../REBUILD-PLAN-SoloEdition-20260530-1325.md#3-gradle-module從-30-砍到-8)
- [REBUILD-PLAN-Original §4 Gradle Module](../REBUILD-PLAN-Original-20260530-1310.md#4-gradle-module-切分)（被取代的 30 module 版本）
- [DEVPLAN §2 模組依賴圖](../DEVPLAN-SubagentFanout-20260620-0851.md#2-模組依賴圖)
- [DEVPLAN §4 Wave 詳細工作包](../DEVPLAN-SubagentFanout-20260620-0851.md#4-wave-詳細工作包)
- [DEVPLAN §5.4 衝突風險與緩解](../DEVPLAN-SubagentFanout-20260620-0851.md#54-衝突風險與緩解)
- W0-1 merge commit：`6bd6cda` — 8 module 空殼實際結構
