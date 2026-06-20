# ADR-0004：採用手動 DI，不引入 Hilt（或其他 DI 框架）

- 日期：2026-06-20
- 狀態：Accepted
- 提議者：Bruce
- 相關：[ADR-0002](0002-eight-modules-not-thirty.md)（8 module 規模是手動 DI 還撐得住的關鍵前提）、[ADR-0003](0003-compose-only-no-xml.md)（Compose 的 `CompositionLocal` 補上 UI 層的注入需求）

## Context（背景）

公司版 plan（[REBUILD-PLAN-Original §3](../REBUILD-PLAN-Original-20260530-1310.md#3-目標技術棧)）寫的是「Hilt 或 Koin DI」。30 module + 多輸入法 + 多 processor chain + 多 decoder backend + 同步/遙測子系統，這個規模引入 Hilt 合理。

但 Solo Edition 把這些功能砍掉後（[SoloEdition §0](../REBUILD-PLAN-SoloEdition-20260530-1325.md#0-reality-check為什麼要簡化)），實際物件圖剩下：

```
BpmfApplication (Android Application 子類)
  ├─ DecoderModule (singleton; wraps libchewing + Room PersonalDict)
  ├─ ThemeRepository (singleton; reads/writes DataStore)
  ├─ KeyboardRegistry (singleton; static layouts from :keyboards)
  └─ SettingsRepository (singleton; DataStore Proto)

BpmfInputMethodService (Android Service)
  └─ 從 Application 取上述四個 singleton
```

整個 wire 圖**少於 10 個 binding**。對這個規模而言：

| 評估維度 | Hilt | 手動 DI |
|---|---|---|
| Wire boilerplate | 註解 `@Inject` / `@Module` / `@InstallIn` | 一個 `BpmfApplication.kt` 約 30 行 |
| 編譯時間 | KSP 處理 Hilt annotations（W0-1 build 增量約 +3-5 秒/incremental，full build 更多）| 0 |
| APK size 增量 | Hilt runtime + Dagger generated code（典型 +200 KB） | 0 |
| 學習成本 | 需懂 Component / Subcomponent / Scope / `@AssistedInject` / Hilt EntryPoint | Kotlin object + lazy + ServiceLocator |
| `InputMethodService` 整合 | Hilt 對 service 支援需 `@AndroidEntryPoint` + 自訂 component；IME service 不是常規 Android entry point，文件少 | 直接 `(application as BpmfApplication).decoderModule` |
| Compose 整合 | 仍需 `hiltViewModel()` 或手動 provide | `CompositionLocal` 原生 fit |
| Test 替換 | `@TestInstallIn` + Hilt test rule | 構造子注入 fake；無 framework |

W0-1 已確認 `:common` 走純 Kotlin JVM module（commit `6bd6cda`），這意味著 Hilt 在 `:common` 不適用（KSP 配置複雜化），DI 邊界天然就被切成「Android module（接 Hilt）+ pure Kotlin module（接不到）」兩半，這種混合反而比全手動更難理解。

[SoloEdition §2 不用的](../REBUILD-PLAN-SoloEdition-20260530-1325.md#2-技術棧壓縮版) 已明列「不用 Hilt（8 模組手動 DI 不痛）」、[SoloEdition §3](../REBUILD-PLAN-SoloEdition-20260530-1325.md#3-gradle-module從-30-砍到-8) 也注「之後 v1.5 加拼音/倉頡時不另開模組」— **在目前 v1/v1.5 範圍內，物件圖不預期會大幅膨脹**（v2 若加雲端 / 多 backend / 多語言 decoder，再重評）。

## Decision（決定）

**v1 採手動 DI（ServiceLocator pattern）。所有 singleton 由 `BpmfApplication` 持有並 lazily 構造**。

實作規範：

- `:app` 內定義 `BpmfApplication : Application`，內含 `val decoderModule by lazy { ... }` 等 properties
- **在 `:common` 定義 provider 介面**避免 `:ime → :app` 循環依賴：
  ```kotlin
  // :common
  interface BpmfDependencyProvider {
      val decoderModule: DecoderModule
      val settingsRepository: SettingsRepository
      // 其他 singleton
  }
  ```
  `:app` 的 `BpmfApplication` 實作此介面（`:app` → `:common`，順向）。
  `:ime` 的 `BpmfInputMethodService` 在 `onCreate` 取 `application as BpmfDependencyProvider`
  即可拿 singleton（`:ime` → `:common`，順向；**不**反向相依 `:app`）。
- Compose 層用 `CompositionLocalProvider` 把需要的 repository / state holder 向下傳
- ViewModel 用 `ViewModelProvider.Factory` 手動構造（不用 `by viewModels()` 的 Hilt 變形）
- 測試替換：每個物件接受 fake 透過建構子或 setter；`:common` 已預備 `FakeZhuyinDecoder` / `FakeImeContextProvider`（[DEVPLAN W0-2](../DEVPLAN-SubagentFanout-20260620-0851.md#w0-2--介面契約common)）
- [DEVPLAN W3-1 wiring](../DEVPLAN-SubagentFanout-20260620-0851.md#w3-1--app-wiring) 為實際 wiring 範例之單一指定 owner

**禁用**：Hilt、Dagger、Koin、Kodein、Anvil。例外需開新 ADR 否決本 ADR。

## Consequences（後果）

**正面**
- 零 annotation processing overhead：build 時間少一輪 KSP
- 物件構造順序可讀 — 整個 wire graph 在 `BpmfApplication.kt` 一個檔案看完
- `:common` 的純 Kotlin module 性質保留：testable on JVM only，不需 Android instrumentation
- 對 Compose 友善：`CompositionLocal` 是 Compose 設計時就考慮 manual provide 的 idiom
- 升 Compose / AGP / Kotlin 時少一個第三方 framework 要追相容性（Hilt 跟 KSP 版本綁定常需 unblock）
- 子代理 fan-out 時：W2 各包不用學 Hilt 慣例，只需照介面實作

**負面**
- 構造順序錯誤要在 runtime 才會炸（沒有編譯期 graph 驗證）— Hilt 會在 build 時抓 missing binding
- 添加新 singleton 時要記得在 `BpmfApplication` 手動 instantiate（沒有 `@Inject` 自動 wire）
- 若未來 ViewModel 數量爆增，自己寫 `ViewModelFactory` 會比 Hilt 的 `@HiltViewModel` 囉嗦
- ServiceLocator 模式被部分 Android 社群視為 anti-pattern；招人時可能被問

**開放問題 / 風險**
- 物件圖規模膨脹門檻：當 `BpmfApplication` 內的 `by lazy` properties 超過 ~15 個、或出現「constructor 參數 > 5 個的物件」時，重審是否引入 Hilt
- 並行初始化風險：`by lazy` 預設 `LazyThreadSafetyMode.SYNCHRONIZED`，多執行緒同時觸發 wire 時不會產生競態（race）；但若改 `PUBLICATION` 為效能優化需小心
- 測試切換成本：若整合測試需替換多個 module，手動構造會比 Hilt `@TestInstallIn` 囉嗦 — 接受這個 trade-off
- **重評觸發條件**：(1) v1.5 加拼音/倉頡後 ViewModel 超過 8 個；(2) 整合測試的 setup boilerplate > 一頁；(3) 出現需要 scope 管理（不是單純 singleton vs activity-scope）的物件

## Alternatives considered（替代方案）

- **Hilt**：公司版預設。優點：編譯期驗證、社群熟、test rule 完整。缺點對 solo dev 都是 overhead（見 Context 表格）。**否決**。
- **Koin**：Kotlin-first DSL、reflection-based、無 annotation processing。優點：學習曲線比 Hilt 平。缺點：仍多一個依賴、startup time 增加（reflection lookup）、type-safety 弱於 Hilt — 拿了 Koin 的缺點卻沒拿 Hilt 的優點。**否決**。
- **kotlin-inject / Anvil**：annotation-based 但體積較小。較新、社群不大、文件少；Solo dev 不押第三方 framework 賭注。**否決**。
- **完全不分 module、所有東西放 companion object**：是手動 DI 的退化形式。8 module 切分（[ADR-0002](0002-eight-modules-not-thirty.md)）就無法依賴反向（`:decoder` 無法 reach `:app` 的 companion）。**否決**。
- **延後決定，v1 先手動 DI、預留 Hilt 抽象**：YAGNI。預留抽象本身就是引入 framework 的成本但沒拿到好處。當前 ADR 已開放重評條件，未來真有需要再切。**否決**。

## References

- [REBUILD-PLAN-SoloEdition §2 技術棧壓縮版](../REBUILD-PLAN-SoloEdition-20260530-1325.md#2-技術棧壓縮版) — "不用 Hilt（8 模組手動 DI 不痛）"
- [REBUILD-PLAN-Original §3 目標技術棧](../REBUILD-PLAN-Original-20260530-1310.md#3-目標技術棧)（公司版原案：Hilt 或 Koin）
- [DEVPLAN W0-2 介面契約](../DEVPLAN-SubagentFanout-20260620-0851.md#w0-2--介面契約common) — Fake 實作 by 構造子注入
- [DEVPLAN W3-1 `:app` Wiring](../DEVPLAN-SubagentFanout-20260620-0851.md#w3-1--app-wiring) — 手動 DI wiring 由單一 owner 集中處理
- W0-1 merge commit `6bd6cda` — `:common` 為純 Kotlin JVM module
- Hilt + InputMethodService 整合的非官方範例：<https://github.com/florisboard/florisboard>（floris IME 早期版本案例）
