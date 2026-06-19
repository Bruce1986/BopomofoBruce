# BopomofoBruce — Solo Edition 開發計畫

- 文件日期：2026-05-30 13:25
- 對照文件：[REBUILD-PLAN-Original-20260530-1310.md](REBUILD-PLAN-Original-20260530-1310.md)（公司級完整版，僅供參考）
- 作者：Bruce（brucex1986@gmail.com）

> 把原計畫從「公司專案」尺度，壓回**單人作品集 + 副業時間（每週 6–10 小時）**的真實尺度。
> 目標讀者：**3 年後的我自己**。讓未來的我能在 1 小時內回想起當下決策。

---

## 0. Reality Check（為什麼要簡化）

| 指標 | 公司版預估 | Solo 實際 |
|---|---|---|
| 工時假設 | 1 FTE × 8 個月 | 每週 6–10 小時 × 12–18 個月 |
| 模組數 | 30+ Gradle module | **8 個** |
| 解碼器 | 自寫 C++ HMM + 自蒐語料 | **JNI 包 libchewing** |
| 手寫 | 自訓 TFLite | ML Kit Digital Ink（或 v1 不做） |
| 同步 | E2EE + 多後端 | v1 純本地 |
| 遙測 | 自架 OpenTelemetry | Play Console 內建即可 |
| TV / 平板 / 折疊 | 第一版就支援 | **v1 只做手機軟鍵盤** |
| 預期 PR/年 | 「社群活躍」 | **0–3 個** |

→ 砍掉所有「為了將來可能會有的人」設計的擴充點。**不留 plugin 介面、不留 abstract factory、不做 multi-arch fat APK。**

---

## 1. v1.0 範圍（MVP）

**做的**
- ✅ 注音 4×10 軟鍵盤（橫直屏）
- ✅ libchewing JNI 後端
- ✅ 候選列、長按符號擴充、自動空白
- ✅ Material You 動態色 + 深淺主題（內建 3 套）
- ✅ 自訂相片背景（單張，可調透明）
- ✅ 個人字典（Room，純本地）
- ✅ Emoji 鍵盤（用 emoji2）
- ✅ 設定頁（Compose）：主題、按鍵音、振動、長按時間
- ✅ FirstRun 引導開啟 IME

**v1 不做**
- ❌ 拼音、倉頡（v1.5 再說）
- ❌ 手寫輸入（v2）
- ❌ 滑動輸入（v2，且看 libchewing 是否支援）
- ❌ 實體鍵盤、TV、平板、折疊裝置
- ❌ 雲端同步、Google 帳號
- ❌ 語音輸入、AI 候選
- ❌ 剪貼簿管理器（Android 13+ 已有不錯的內建）
- ❌ 主題編輯器、主題市集

→ 目標：**3 個月內 v0.1 可在我自己手機上日常使用。**

---

## 2. 技術棧（壓縮版）

| 層 | 選用 | 為什麼 |
|---|---|---|
| 語言 | Kotlin 2.x | Android first-class |
| UI | Jetpack Compose + Material 3 | 學一次受用整個 Android 生態 |
| Min/Target SDK | minSdk 28 / targetSdk 35 | 28 已涵蓋 99%+ 台灣使用者，省 backport 力氣 |
| 架構 | 簡單 MVVM + ViewModel + StateFlow | 不用 Hilt，手動 DI 就好（8 模組撐得住） |
| 持久化 | DataStore（設定）+ Room（字典） | 標準組合 |
| Native | libchewing 預編 + 自寫 thin JNI | NDK r26、CMake |
| ABI | arm64-v8a + armeabi-v7a | 涵蓋率夠，不做 x86 |
| Build | Gradle 8.x + Version Catalog | — |
| CI | GitHub Actions：build / lint / unit | Macrobenchmark 等 v1 之後 |
| 測試 | JUnit5 + Compose UI test，**只測核心** | Solo dev 不追求高覆蓋率 |

**不用的**
- ❌ Hilt（8 模組手動 DI 不痛）
- ❌ KMP（不會跨平台）
- ❌ Detekt（Lint + Ktfmt 夠）
- ❌ Sentry / OpenTelemetry（Play Vitals 即可）
- ❌ Macrobenchmark（v1 後）

---

## 3. Gradle Module（從 30 砍到 8）

```
:app                    Manifest、Application、IME Service entrypoint
:ime                    InputMethodService + Compose IME view + 候選列
:keyboards              注音 4×10、符號、emoji、數字、密碼（合一個）
:decoder                JNI binding + libchewing wrapper + 個人字典合併
:decoder-native (CMake) 預編 libchewing.so，輸出 libbpmf.so
:theme                  Material 3 主題、Style schema、相片背景
:settings               Compose 設定頁、FirstRun
:common                 工具、簡繁、字串、Resource helper
```

> 之後 v1.5 加拼音／倉頡時，只在 `:keyboards` 與 `:decoder` 內擴充，**不另開模組**。

---

## 4. 里程碑（單人時間表）

> 假設每週 8 小時、不請假、不會生病、不會被工作沖掉週末。實際抓 1.4× buffer。

### M0｜立項與 spike（2 週）
- [ ] GitHub repo 公開、README、LICENSE（Apache-2.0）、.gitignore
- [ ] Gradle 骨架 + Version Catalog（先全空 module）
- [ ] CI workflow：Gradle build + Android Lint + `testDebugUnitTest`（首次 push workflow 檔需先 `gh auth refresh -s workflow`）
- [ ] **Spike：把 libchewing 編成 .so，純 JNI 印一個「ㄅㄆㄇㄈ→候選詞」到 logcat**
- [ ] 寫 v0 ADR：為什麼選 libchewing、為什麼不用 Hilt、為什麼不做 TV

### M1｜IME 跑起來（4 週）
- [ ] `BpmfInputMethodService` 註冊、可在系統 IME 列表中選到
- [ ] Compose IME view 雛形（純 QWERTY 英文先打字）
- [ ] 鍵盤 JSON 描述格式 v0（不做 DSL，直接 kotlinx.serialization）
- [ ] 退格、空白、Enter、shift 動作完整
- [ ] FirstRun activity（引導開啟 IME）

### M2｜注音核心（4 週）
- [ ] `:decoder` 接 libchewing：輸入注音 → 取得候選詞陣列
- [ ] 候選列 Compose 元件（橫向 scroll、點選送字）
- [ ] 注音 4×10 鍵盤 layout
- [ ] 個人字典 Room schema + 加入/刪除/詞頻
- [ ] 用我自己手機日常打字測試 ≥ 1 週

### M3｜美觀（3 週）
- [ ] 內建主題 3 套：深、淺、Material You 動態
- [ ] 相片背景（單張，可裁切、調透明、模糊）
- [ ] 鍵框顯示開關、字體大小調整
- [ ] 按鍵聲、振動、長按時間設定

### M4｜符號與表情（2 週）
- [ ] 符號鍵盤（含繁中專屬全形標點）
- [ ] Emoji（emoji2 + EmojiCompat）
- [ ] 數字、密碼、URL、電話鍵盤

### M5｜打磨與上架（3 週）
- [ ] 設定頁完整：主題、輸入、按鍵、關於、回報問題
- [ ] 隱私政策（純本地、無網路）
- [ ] APK ≤ 12 MB（不含 split data）
- [ ] Play Store internal track → closed beta
- [ ] **以家人朋友 5 人為 beta 測試者**

**累計：18 週實作 + 緩衝 = 約 6 個月。**

之後 v1.5/v2 看反饋與心情，**不預先承諾時程**。

---

## 5. 「不做」清單（Solo dev 的最重要條款）

| 拒絕做的事 | 原因 |
|---|---|
| 自寫 HMM decoder | libchewing 已經 20 年成熟，我打不贏 |
| 自蒐語料訓練 LM | 法律 + 工時雙重風險 |
| TV / 平板 / 折疊 | 受眾 < 5%，測試成本 > 開發成本 |
| 雲同步 | 隱私風險、伺服器費用、客服責任 |
| 多 IME（拼音 / 倉頡） | 留 v1.5。先把注音做好，免得樣樣半吊子 |
| 主題市集、社群分享 | 流量沒到不做 |
| 自訂鍵盤布局 UI | 用設定切換就好 |
| AI 候選 / LLM | 模型授權太麻煩 |
| 抽 plugin 介面 | YAGNI |
| 完整 i18n | 繁中為主，英文 fallback 即可 |
| 完整無障礙朗讀（v1） | 留 v1.5，先確保 TalkBack 不爆掉就好 |

---

## 6. 我會用到的時間（誠實版）

| 活動 | 預估 |
|---|---|
| 寫 code | 50% |
| 看 libchewing 源碼／找文件 | 15% |
| Debug 與真機測試 | 20% |
| README／docs／截圖 | 5% |
| 處理 GitHub issue／回信 | 5%（若公開後） |
| 重構 v0 程式 | 5% |

→ 每週若擠出 8 小時，**實際產出約 4 小時的乾貨**。

---

## 7. 怎麼判斷「該不該繼續做」

每 3 個月檢查一次：

- [ ] **我自己還在用嗎？** 沒在用 → 砍掉
- [ ] **每週有花 ≥ 4 小時嗎？** 沒有 → 公開告知 hiatus
- [ ] **有家人/朋友每天在用嗎？** 沒有 → 不算成功，但仍可作為作品集
- [ ] **GitHub stars 突破 100 了嗎？** 沒有 → 不影響繼續，僅作參考
- [ ] **打字準確度 ≥ libchewing baseline 的 90%？** 沒有 → 排第一優先修

---

## 8. 立即下一步（本週）

1. ✅ 開公開 repo `Bruce1986/BopomofoBruce`
2. ✅ 把這份 + 公司版 plan 都放進 `docs/`
3. [ ] Spike：把 libchewing 編出 arm64 .so，能在我手機跑 JNI
4. [ ] Gradle module 骨架建好（8 個都先空著、有 BUILD.kts.kt 即可）
5. [ ] CI 跑得起來
6. [ ] M0 結束時寫第一篇 Dev Log（README 連去 docs/devlog/）

---

## 附錄：與公司版的對應

| 公司版章節 | Solo 對應 |
|---|---|
| §1 反向分析摘要 | 保留在公司版，不重複 |
| §3 技術棧 | 砍 60%（見本文 §2） |
| §4 30+ 模組 | 砍到 8（見本文 §3） |
| §5 功能對應表 | v1 只實作其中 30% |
| §7 M0–M9 8 個月 | 砍成 M0–M5 6 個月，且 v1.5/v2 不承諾 |
| §8 風險清單 | 重抓「無人接手」「我自己沒空」兩條 |
| §9 OSS 授權 | 沿用 |
| §10 DoD | 砍到「自己每天用得下去」一條 |

---

_這份文件是 Solo Edition 的工作母本。公司版留作未來若擴大、或捐給組織時的參考藍圖。_
