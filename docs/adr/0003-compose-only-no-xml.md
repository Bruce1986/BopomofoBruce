# ADR-0003：IME view、設定、首次啟動全部 Compose，不寫 XML layout

- 日期：2026-06-20
- 狀態：Accepted
- 提議者：Bruce
- 相關：[ADR-0002](0002-eight-modules-not-thirty.md)（`:ime` / `:settings` / `:theme` 三 module 都假設 Compose-only）

## Context（背景）

原 Google 注音 APK（[REBUILD-PLAN-Original §1.1](../REBUILD-PLAN-Original-20260530-1310.md#11-套件結構)）有：
- 約 220 個 `res/xml/` IME framework 檔（鍵盤定義、softkeys、processors、settings 全部宣告式 XML）
- 大量 Android Support Library / View-based UI（targetSdk ≤ 24 時代產物）

公司版 plan 一度想沿用「鍵盤用 XML/Proto 描述」這個亮點（[原 §1.7](../REBUILD-PLAN-Original-20260530-1310.md#17-既有架構亮點與包袱)）。Solo Edition 已經決定改用 Kotlin DSL + JSON（[SoloEdition §2](../REBUILD-PLAN-SoloEdition-20260530-1325.md#2-技術棧壓縮版)）。

剩下要決定的是 **UI 渲染層**：Compose、傳統 View（XML inflate）、或混用？實務上 Android IME 開發很多老專案還在用 View 的原因：

- `InputMethodService` 的 contract 跟 Activity 不同；`onCreateInputView()` 必須回傳一個 `View`
- 早期 Compose 對「非 Activity 宿主」支援不完整；需要 `AbstractComposeView` 加上 `ViewTreeLifecycleOwner` / `ViewTreeSavedStateRegistryOwner` 手動掛
- IME view 頻繁被 destroy/recreate（切應用、轉螢幕、切輸入法），Compose 的 composition 重建成本可能比 View inflate 高

但 2026 年的條件已經完全不同：
- Compose 1.7+ 對 `AbstractComposeView` 在 `InputMethodService` 內掛載已有官方文件與社群驗證範例
- Kotlin 2.0 自帶 Compose compiler（W0-1 已套用 `org.jetbrains.kotlin.plugin.compose`，見 commit `6bd6cda`）— 不再需要綁 compose-compiler 版本
- Material You（Android 12+）的動態色僅在 Compose 有 first-class API（XML 主題還要手刻 `MaterialComponents` overlay）
- IDE / `@Preview` 在 Compose 的迭代速度遠勝 XML（XML preview 經常 invalidate、需要 rebuild）
- 沒有 XML 後省掉 `viewBinding` / `findViewById` / `R.layout.xxx` 整套維護
- [SoloEdition §2 不用的](../REBUILD-PLAN-SoloEdition-20260530-1325.md#2-技術棧壓縮版) 已決定不用 Hilt（[ADR-0004](0004-no-hilt-manual-di.md)）；Compose 的 `LocalXxx` + `remember` 對「沒有 DI 框架」的場景特別友善（manual provide 自然落地）
- [AGENTS.md §專案特定注意事項](../../AGENTS.md#專案特定注意事項) 已明文 "Jetpack Compose only. 不要在 IME view 引入 XML layout"

對「IME view 重建成本」的擔憂：Compose state 用 `viewModelScope` / `rememberSaveable` 配置好後，IME view 在切應用時的重建**理論上**主要是 composition 而非 inflate。Solo Edition 設定的目標是「Pixel 4a 上鍵盤展開 < 350 ms」（[SoloEdition DoD](../REBUILD-PLAN-SoloEdition-20260530-1325.md#4-里程碑單人時間表)），但**尚未在 W0-1 階段做實機測量** — 這是 [W2-B 驗收條件](../DEVPLAN-SubagentFanout-20260620-0851.md#w2-b--imeinputmethodservice--compose-ime-view)，若 W2-B 實測超標、且找不到 root cause，需重評 Compose IME 路線（針對熱 path 引入 `AndroidView` fallback，或退回 XML — 後者觸發本 ADR superseded）。

## Decision（決定）

**`:ime` IME view、`:settings` 設定頁、`:theme` 預覽、FirstRun 引導，全部 Jetpack Compose，禁止 XML layout**。

具體規範：

- IME view 透過 `AbstractComposeView` 子類掛到 `InputMethodService.onCreateInputView()`
- 主題、字型、dimens 全走 Compose Material 3 `ColorScheme` / `Typography` / `Shapes`
- 鍵盤定義（[`:keyboards`](../../keyboards/)）走 kotlinx.serialization JSON，**不寫 `res/xml/` 鍵盤**
- 候選列、SoftKey、Toolbar 一律 Compose composable
- `:settings` 採 Compose-only（含 FirstRun Activity）
- 仍允許用 `res/values/` 放字串資源（`strings.xml`）與 `themes.xml`（host Activity 的 Material 3 base theme，這是 Android 系統層要求，不算「UI layout」）
- 允許 `AndroidView` 作為**最後手段**包裝必要的 platform view（例如 emoji2 的 `EmojiTextView` 若 Compose API 不足，或 Material You wallpaper picker），須在 PR 說明原因

延伸：[GEMINI.md / AGENTS.md 專案特定注意事項](../../AGENTS.md#專案特定注意事項) 已寫入這條規則，code review（人類或 AI）看到 IME view 引入 XML layout 應 reject。

## Consequences（後果）

**正面**
- 開發迭代速度：`@Preview` 比 XML preview 穩、Compose hot reload（Live Edit）可用
- 主題系統乾淨：Material 3 動態色 + 使用者相片背景全在同一個 `KeyboardTheme` 介面下（[DEVPLAN W1-B](../DEVPLAN-SubagentFanout-20260620-0851.md#w1-b--theme-主題引擎)）
- 少一套技術：不需學 XML attribute 語法、`styles.xml` cascade、`?attr/colorPrimary` 解析規則
- 沒有 `viewBinding` 產出物，build 速度 + APK size 雙贏
- 設定頁與 IME view 共用同一套 Compose 元件（候選列預覽即時生效，[DEVPLAN W2-C](../DEVPLAN-SubagentFanout-20260620-0851.md#w2-c--settings-compose-設定--firstrun)）

**負面**
- `AbstractComposeView` + IME service 掛載 boilerplate 比 `View.inflate(R.layout.xxx)` 多（需手動 set `ViewTreeLifecycleOwner` / `ViewTreeSavedStateRegistryOwner` / `ViewTreeViewModelStoreOwner`）— 第一次踩會痛
- 在極低階機（Pixel 4a 等級或更低）IME view 首次 composition 可能慢於 XML inflate；需 [W2-B 驗收](../DEVPLAN-SubagentFanout-20260620-0851.md#w2-b--imeinputmethodservice--compose-ime-view) 實測守住 350 ms 門檻
- TalkBack / 無障礙在 Compose 上仍有偶發 case 比 XML 弱（[SoloEdition §5](../REBUILD-PLAN-SoloEdition-20260530-1325.md#5-不做清單solo-dev-的最重要條款) 已將完整 a11y 延到 v1.5）
- IME 在 system_server crash 時的錯誤訊息較難 debug — Compose stack trace 比 XML inflate exception 抽象

**開放問題 / 風險**
- `InputMethodService` 不是 `LifecycleOwner`：必須自己實作 `Lifecycle` + 在 service `onCreate` / `onDestroy` 同步狀態。社群有範例但 edge case（split screen、IME visibility 切換）不少 — 留 [W2-B](../DEVPLAN-SubagentFanout-20260620-0851.md#w2-b--imeinputmethodservice--compose-ime-view) 處理
- 切換螢幕方向、深淺主題、語言時 IME view 需正確 `recompose`：靠 `CompositionLocalProvider(LocalConfiguration)` 串好；測試覆蓋見 [W2-B 驗收](../DEVPLAN-SubagentFanout-20260620-0851.md#w2-b--imeinputmethodservice--compose-ime-view)
- **重評觸發條件**：M2 實機測試鍵盤展開 > 350 ms 且無法用 Compose 本身優化（baseline profile、`derivedStateOf` 等）解決時，重審是否引入 `AndroidView` 包熱 path
- emoji2 / EmojiCompat 與 Compose 整合在 [W2-D](../DEVPLAN-SubagentFanout-20260620-0851.md#w2-d--emoji--symbol-擴充與-common-helper) 實作時可能踩到 `AndroidView` 需求

## Alternatives considered（替代方案）

- **XML layout + traditional View**：成熟、IME 場景文件多、低階機效能可預測。但 Material You 動態色、`@Preview` 體驗、主題即時切換、單人維護成本全都吃虧。否決。
- **Compose + 局部 XML 混用**（IME view 用 XML、設定頁用 Compose）：兩套技術都要懂；主題系統需各維護一份；fan-out 時 `:ime` 與 `:settings` 沒法共用 `:theme` 的 Compose preview。比純 Compose 更糟。否決。
- **採用第三方 IME framework**（如 fcitx5-android base）：節省 service 接線工，但綁定 framework 演進，且 fcitx5 偏 Linux IME 生態，Android 上社群小。Solo dev 押第三方 framework 風險過高。否決。
- **Compose Multiplatform（KMP）**：[SoloEdition §2 不用的](../REBUILD-PLAN-SoloEdition-20260530-1325.md#2-技術棧壓縮版) 已明列「不會跨平台」。Android-only Compose 即可。否決。

## References

- [REBUILD-PLAN-SoloEdition §2 技術棧壓縮版](../REBUILD-PLAN-SoloEdition-20260530-1325.md#2-技術棧壓縮版)
- [AGENTS.md §專案特定注意事項](../../AGENTS.md#專案特定注意事項) — "Jetpack Compose only. 不要在 IME view 引入 XML layout"
- [DEVPLAN W2-B `:ime` 工作包](../DEVPLAN-SubagentFanout-20260620-0851.md#w2-b--imeinputmethodservice--compose-ime-view)
- W0-1 merge commit `6bd6cda` — Kotlin 2.0 Compose plugin（`org.jetbrains.kotlin.plugin.compose`）已套用
- Compose in `InputMethodService` 範例：<https://github.com/android/snippets>（AbstractComposeView pattern）
- Material 3 動態色 API：<https://developer.android.com/develop/ui/compose/designsystems/material3>
