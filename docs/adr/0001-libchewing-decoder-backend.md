# ADR-0001：注音 decoder 後端採用 libchewing，不自寫 HMM

- 日期：2026-06-20
- 狀態：Accepted
- 提議者：Bruce
- 相關：[ADR-0002](0002-eight-modules-not-thirty.md)（8 module 切分把 decoder 限縮在 `:decoder` + `:decoder-native`）

## Context（背景）

BopomofoBruce 是 clean-room 重建 2017 後停更的 Google 注音輸入法。原 APK 的核心 IP 是一顆自家 HMM decoder（`libhmm_gesture_hwr_zh.so`，6.6 MB）配上 8.3 MB 注音語言模型 bundle。整個重建案最大的技術風險就是「decoder 從哪來」。

最初的公司版 plan（[REBUILD-PLAN-Original §3, §7](../REBUILD-PLAN-Original-20260530-1310.md)）規劃自寫 C++ HMM decoder + 自蒐繁中語料、用 KenLM 訓練 N-gram，並列為 M2「注音核心」6 週的主任務。

把這個 plan 從「1 FTE × 8 個月」壓回「solo 每週 6–10 小時 × 12–18 個月」後，這條路徑變得不可行：

- **工時失衡**：HMM decoder 不只是寫 Viterbi（那其實一週可搞定）；難的是訓練資料 — 繁中公開語料的授權審查、清理、normalize、bigram/trigram 統計、平滑、評估，每塊都是論文級工作量。Solo dev 一週 8 小時擠不出來。
- **品質基準難達**：[REBUILD-PLAN-SoloEdition §7](../REBUILD-PLAN-SoloEdition-20260530-1325.md) 的「該不該繼續做」第 5 條是「打字準確度 ≥ libchewing baseline 的 90%」。若自寫 decoder 連 baseline 的 90% 都打不到，整個 app 沒人會用 — 但用 libchewing 當 baseline 後又何必再做一顆。
- **法律風險**：訓練語料若來源不潔（爬到 CC-BY-NC 或無授權），整個 APK 上架被下架的後果不可逆。
- **差異化不在這**：[README](../../README.md) 已明示「BopomofoBruce 的差異化在 UX + 主題 + 隱私，不在解碼引擎」。把 solo 時間花在自家 decoder 等於用稀缺資源做不差異化的事。

libchewing 的 source-level 可行性檢查通過（尚未做完整 NDK build）：
- 已有 20 年生產驗證（從 2003 年釋出至今），主流 Linux IME（gcin、hime、ibus-chewing、fcitx-chewing）皆採用，繁中辭典完整、polysyllabic 詞組成熟
- License **LGPL-2.1**：動態連結（JNI 載 `.so`）合規，不污染本 app 的 Apache-2.0
- C99，無 GUI 依賴 — **理論上** NDK r26 + CMake 可 cross-compile arm64-v8a / armeabi-v7a，但「能跑」是 [W1-A spike](../DEVPLAN-SubagentFanout-20260620-0851.md#w1-a--decoder-native-把-libchewing-編成-so) 的事；本 ADR 不預先宣告通過
- 主動維護中（GitHub 上仍有 release），有 active 社群可回報 bug

## Decision（決定）

**採用 libchewing 作為 v1 注音 decoder 後端，透過 JNI 包裝**。

- 把 libchewing 以 git submodule 形式 vendor 進 `decoder-native/src/main/cpp/libchewing/`
- 在 `decoder-native` 編出 `libbpmf.so`（thin C wrapper exposing 4 個 C API：init/input/commit/free）
- `:decoder` 用 JNI binding 包這四個 API，並實作 `:common` 定義的 `ZhuyinDecoder` 介面
- 個人字典（`PersonalDictEntry`）以 Room 儲存於 `:decoder`，候選詞時與 libchewing 結果加權合併

一句話：**libchewing 是 20 年成熟的免費 baseline，solo dev 沒有理由花 6 週去打不贏它**。

## Consequences（後果）

**正面**
- M2「注音核心」工時從原本「自寫 HMM + 蒐語料 6 週」壓到「JNI binding + 個人字典 4 週」
- 注音準確度 day-1 就等同 libchewing baseline，不需訓練資料、不需評估期
- 法律乾淨：LGPL-2.1 動態連結 + 公開 APK 上架，符合 F-Droid 政策
- 詞典更新可直接 sync upstream libchewing release
- 差異化資源可全押 UX + 主題 + 隱私

**負面**
- 失去對 decoder 行為的完全控制：libchewing 的選詞偏好、片語切分、特殊符號處理皆是 upstream 決定，本專案只能透過個人字典加權扭轉
- libchewing 是 C99，每個 `chewing_context_t` 帶 global state：執行緒安全（thread-safety）需在 JNI 層自己保護，coroutine wrapper（[DEVPLAN W2-A](../DEVPLAN-SubagentFanout-20260620-0851.md#w2-a--decoderjni--個人字典)）要小心
- LGPL-2.1 動態連結雖合規，未來若想靜態連結（statically link，為了 APK size）需重新評估授權與逆向工程（reverse-engineering）條款
- 失去自家 LM 的差異化敘事（無法宣稱「我們的 decoder 更聰明」）— 但本 app 本來就不靠這個賣點

**開放問題 / 風險**
- libchewing 的 NDK build 在 AGP 8.7 + CMake 3.22 上是否一次過？— 留給 [W1-A spike](../DEVPLAN-SubagentFanout-20260620-0851.md#w1-a--decoder-native-把-libchewing-編成-so) 驗證
- libchewing 對「滑動輸入 / gesture decode」**不支援**，若 v2 想做需另尋方案（見 [REBUILD-PLAN-SoloEdition §1](../REBUILD-PLAN-SoloEdition-20260530-1325.md#1-v10-範圍mvp) — v1 本來就不做）
- 若 upstream libchewing 停更（unlikely 但須備案），Plan B 是 fork + 自維最小修補；不重新走自寫路線
- **重評觸發條件**：libchewing 在實機上 P95 打字延遲 > 60 ms（DEVPLAN DoD），或詞典 5 年沒更新導致新詞嚴重缺失

## Alternatives considered（替代方案）

- **自寫 C++ HMM decoder + 自蒐語料**（公司版原案）：上面 Context 已詳述否決理由 — 工時、品質、法律三重風險超過 solo 承受度。
- **採用 RIME (librime)**：功能更強（支援多輸入法引擎），但體積龐大（核心 + 引擎 + 詞庫 > 10 MB）、配置複雜（Lua scripting）、Android NDK build 紀錄較少。本 app v1 只做注音一種輸入法，殺雞用牛刀。
- **包裝 Rust crate `chewing-rs` 或其他社群 wrapper**：較新、生態不成熟、與 Android NDK 整合需先驗 Rust toolchain，多一層風險。直接用 libchewing 上游 C99 source 最短路徑。
- **採用 Gboard 內建注音 via SpeechRecognizer / 第三方 SDK**：違反「100% 離線、零網路請求」的 [README](../../README.md) 承諾，直接否決。

## References

- [REBUILD-PLAN-SoloEdition §0 Reality Check](../REBUILD-PLAN-SoloEdition-20260530-1325.md#0-reality-check為什麼要簡化)
- [REBUILD-PLAN-SoloEdition §5「不做」清單](../REBUILD-PLAN-SoloEdition-20260530-1325.md#5-不做清單solo-dev-的最重要條款) — 第一條「自寫 HMM decoder」
- [REBUILD-PLAN-Original §1.2 原 APK 原生函式庫](../REBUILD-PLAN-Original-20260530-1310.md#12-原生函式庫cc)
- [DEVPLAN W1-A 子代理 spec](../DEVPLAN-SubagentFanout-20260620-0851.md#w1-a--decoder-native-把-libchewing-編成-so)
- [DEVPLAN W2-A 子代理 spec](../DEVPLAN-SubagentFanout-20260620-0851.md#w2-a--decoderjni--個人字典)
- libchewing 上游：<https://github.com/chewing/libchewing>
- libchewing License (LGPL-2.1)：<https://github.com/chewing/libchewing/blob/master/COPYING>
