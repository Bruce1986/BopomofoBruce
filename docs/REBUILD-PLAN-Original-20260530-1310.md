# Google 注音輸入法 — 現代化重建開發計畫

- 文件日期：2026-05-30 13:10
- 分析對象：`Google_注音輸入法_com_google_android_apps_inputmethod_zhuyin_2_4_5_164561151.apk`
- 套件名稱：`com.google.android.apps.inputmethod.zhuyin`
- 版本：`2.4.5.164561151-arm64-v8a`
- 建置時間：2016-10-06（`gmscore_v7_RC28` 分支）
- 撰寫人：Bruce（brucex1986@gmail.com）

> 本文件先彙整原 APK 反向分析所得的功能清單與架構，再給出以新版 Android（API 34/35，Jetpack + Kotlin + Compose）為目標的重建藍圖。**僅用於還原已下架輸入法的功能，不會複用 Google 私有的字典、模型、品牌或受版權保護的素材**。

---

## 1. 原 APK 反向分析摘要

### 1.1 套件結構

| 項目 | 內容 |
|---|---|
| 套件大小 | 17 MB（單 arch、arm64-v8a 精簡版） |
| `classes.dex` | 約 3 MB，399+ 個 `com.google.android.apps.inputmethod.*` 類別 |
| `resources.arsc` | 約 3.3 MB，含繁中／簡中／日／韓多語系字串 |
| `lib/arm64-v8a/` | 5 個 `.so`，共約 19 MB（解壓後） |
| `res/xml/` | 約 220 個 IME framework XML（鍵盤、softkeys、processors、settings） |
| `assets/theme/` | 75 個 `.binarypb`（風格表 ProtoBuf） |
| `res/raw/` | 含 `main_en_d3_*.gzip`（英文 dynamic LM）、`zhuyin_pinyin_map`、`sc2tc/tc2sc` 簡繁互轉、`token_character`、`word_explanation` |

### 1.2 原生函式庫（C/C++）

| `.so` | 大小 | 推測用途 |
|---|---|---|
| `libzhuyin_data_bundle.so` | 8.7 MB | 注音語言模型 + 詞典 bundle |
| `libhmm_gesture_hwr_zh.so` | 6.9 MB | 中文 HMM 解碼器 + 手勢解碼 + 手寫辨識 |
| `libhwrword.so` | 2.4 MB | 整詞手寫辨識引擎 |
| `liben_data_bundle.so` | 9 KB | 英文資料 bundle（殼） |
| `libgnustl_shared.so` | 1.1 MB | GNU libstdc++ runtime |

→ 核心 IP 是 **HMM-based decoder + handwriting + gesture**。重建時可改用開源替代。

### 1.3 應用程式元件（取自 AndroidManifest）

- **Application**：`ZhuyinApp`（+ `BackupAgent`）
- **IME Service**：`ZhuyinInputMethodService`（`android.permission.BIND_INPUT_METHOD`）
- **Activities**：
  - `LauncherActivity`、`ZhuyinFirstRunActivity`、`ZhuyinFeatureActivity`
  - `SettingsActivity`、`TVSettingsActivity`、`MiniBrowserActivity`
  - `PermissionsActivity`、`AndroidAccountActivity`、`UserFeedbackActivity`
  - `ThemeSelectorActivity`、`ThemeBuilderActivity`、`ThemeEditorActivity`
- **Services / Providers / Receivers**：`SyncService`（SyncAdapter）、`StubProvider`（user_dictionary ContentProvider）、`LauncherIconVisibilityInitializer`、Firebase IID、GCM JobDispatcher
- **權限**：VIBRATE / INTERNET / READ_EXTERNAL_STORAGE / READ_USER_DICTIONARY / WRITE_USER_DICTIONARY / READ_CONTACTS / ACCESS_NETWORK_STATE / USE_CREDENTIALS / GET_ACCOUNTS / MANAGE_ACCOUNTS / READ_SYNC_SETTINGS / WRITE_SYNC_SETTINGS / RECEIVE_BOOT_COMPLETED / GET_PACKAGE_SIZE / READ_GSERVICES

### 1.4 內部子系統（依 DEX 類別歸納）

| 模組 | 主要類別 / 功能 |
|---|---|
| `libs.framework.core` | IME 主框架：`GoogleInputMethodService`、`IIme`、`IKeyboard`、`InputBundleManager`、`KeyboardDefManager`、權限/Backup/啟動器圖示 |
| `libs.chinese.ime.hmm` | `AbstractHmmChineseDecodeProcessor`、`ChineseAutoSpaceProcessor`、`ChineseDoubleSpaceProcessor` |
| `libs.hmm` | `HmmEngineWrapper`、`DictionaryAccessor`、`SettingManager`、`AbstractHmmIme`、`AsyncHmmImeWrapper`、`BasicUserContactsDictionaryImporter`（聯絡人字典） |
| `libs.hmmgesture` | `HmmGestureDecoder`（滑動輸入解碼） |
| `libs.handwriting` | `HandwritingMotionEventHandler`、`HandwritingOverlayView`、`HandwritingPrimeKeyboard`、`FullscreenHandwritingMotionEventHandler` |
| `libs.gestureui` | `GestureOverlayView`、`TrailManager`、`KeyboardLayoutProtoBuilder` |
| `libs.cangjie` | `AbstractCangjieDecodeProcessor` |
| `libs.english` | `EnglishIme`、`English9KeyIme`、`EnglishHard12KeyIme`、`EnglishGestureMotionEventHandler` |
| `libs.latin` | `LatinIme`、`IVoiceImeTranscriptor`、`LatinGestureMotionEventHandler` |
| `libs.theme` | `StyleSheetConverter`、`ThemePackage`、`ThemeBuilder/Selector/Editor`、`StyleSheetProto`、`ThemePackageProto` |
| `libs.tv` | `TVKeyboard`、`TVKeyboardViewController`、`GamePadEventTranslator`、`TVMotionEventHandler`、`FocusPointerUnderlayView` |
| `libs.dataservice` | 帳號登入、雲端字典同步、下載器（`OmahaCheckUpdateTask`）、`StubProvider` |
| `libs.imemetrics` / `libs.metrics` / `libs.logging` | 指標、Primes、UserFeedback |
| `libs.experiments` | Phenotype A/B 與緊急訊號處理 |
| `libs.delight4` | `IPredictionEngine`、`IDynamicLanguageModelProvider`（Gboard 共享神經預測介面） |
| `libs.inputcontext` | 周遭文字／選取追蹤 |
| `libs.omaha` | Google 更新檢查 |

### 1.5 鍵盤／輸入模式清單

從 `res/xml/` 解析：

- **中文輸入法**
  - 注音（4×10 軟鍵盤、實體鍵盤、浮動鍵盤）
  - 繁體拼音（軟 qwerty / 實體 qwerty / 浮動實體）
  - 倉頡（軟 / 實體 / 浮動實體）
  - 手寫（半屏、全屏、實體鍵盤模式）
- **英文輸入法**：qwerty、實體 12-key、實體 qwerty、浮動實體
- **特殊型態**：digit、phone_number、number_password、date_time、password、URL
- **符號／表情**：標準符號、繁中專屬符號、emoji（8 個分類）、kaomoji（笑/汗/驚/悲/不悅 5 類）
- **附屬畫面**：dashboard、access points panel、候選詞列、剪貼簿入口（可加）

### 1.6 設定面（從中／日／繁字串歸納）

- 輸入設定：自動修正、自動加空白、雙擊空白鍵插入句號、句首大寫、預測候選、聯絡人名稱候選、中文預測
- 鍵盤設定：主題、單／雙手模式（左右）、空白鍵滑動移動游標、鍵框顯示、按鍵聲、振動、長按時間、語言切換鍵、emoji 切換鍵、片手鍵盤
- 手寫設定：辨識速度、筆畫粗細、半屏/全屏切換
- 字典：個人字典、同步、匯入/匯出單字表、清除單字表（需輸驗證碼）、聯絡人姓名加入候選
- 主題：內建 11+ 主題（Material 各色、深淺、輪廓版、Holo Blue/White）、相片自訂主題、主題編輯器
- 其他：使用方式提示、回報問題、開源授權、版本資訊、開發者選項（dump、Primes events）
- TV 設定：TVSettingsActivity + GamePad
- 同步：透過 Google 帳號跨裝置同步個人字典

### 1.7 既有架構亮點與包袱

**亮點**
- 「框架 + 處理器鏈」設計：每種輸入法只是一條 `processor` chain（decode → double-space → auto-space → scrub-move → output），擴充非常靈活。
- 鍵盤、按鍵、配色完全以 XML/Proto 宣告，UI 與邏輯解耦。
- HMM decoder 與 UI 切離（JNI 介面 `HmmEngineInterfaceImpl`、`HmmGestureDecoder`）。

**包袱**
- 仍是 Android Support Library（`android-support-multidex`）時代產物，targetSdk 應該 ≤ 24。
- 大量自家 R class 切割、Proto v2 nano、Phenotype、Primes、Omaha 等 Google 內部依賴。
- 大量 XML 鍵盤定義冗長且重複（光 keymapping XML 就上百個）。
- 雲端同步綁 Google 帳號 + GMS，無法直接搬到開源版。
- `READ/WRITE_USER_DICTIONARY` 在 Android 10+ 已不再對第三方提供。

---

## 2. 新版重建專案 — 願景

> **「BPMF Reborn」**：一套以注音為主、現代 Android stack 構築的開源／私人輸入法，沿用原 APK 的優點（處理器鏈架構、可宣告鍵盤、豐富主題），補上現代化的 UX（Material 3、可調式工具列、剪貼簿、AI 候選）與隱私（離線優先、可選同步）。

### 2.1 設計原則

1. **離線優先**：核心輸入完全不需網路；雲同步為 opt-in。
2. **隱私可驗證**：任何外送（崩潰、統計）皆需明確同意，且支援 DP 噪聲；提供本機 raw log。
3. **模組化**：framework / decoder / ui / theme / sync 各自獨立 Gradle module，可被其他 IME（粵拼、台羅）共用。
4. **可宣告鍵盤**：保留「XML/Proto 描述鍵盤」概念，但改用 Kotlin DSL + JSON schema。
5. **Compose 優先**：Settings、Theme Editor、First-run 全部 Compose；IME 候選列亦 Compose（Glance/AndroidView 混合）。
6. **Android 14+ best practice**：scoped storage、predictive back、edge-to-edge、per-app language、Material You 動態取色。

---

## 3. 目標技術棧

| 層級 | 選用 |
|---|---|
| 語言 | Kotlin 2.x（IME 服務）、Rust 或 C++20（解碼核心） |
| UI | Jetpack Compose + Material 3，IME View 採 Compose + `AbstractComposeView` |
| Min/Target SDK | minSdk 26（Android 8.0）、targetSdk 35（Android 15） |
| 架構 | MVI + UDF，Hilt 或 Koin DI |
| 持久化 | Room（字典、設定、同步元資料）、Proto DataStore（鍵盤主題、用戶偏好） |
| 序列化 | kotlinx.serialization、Protobuf（鍵盤定義、樣式表） |
| 多進程 | IME service 主進程；下載/同步用 WorkManager 後台 |
| 非同步 | Coroutines + Flow（取代原 `ITaskRunner` / `ITaskScheduler`） |
| Native | NDK r26、CMake、ABI：arm64-v8a + x86_64（emulator）+ armeabi-v7a（可選） |
| 解碼核心 | C++ 或 Rust，編成 `libbpmf_core.so`，JNI binding 經 `kotlinx-cinterop`/JNI |
| 語言模型 | 自製繁中 N-gram（KenLM）＋ 可選的離線 Transformer（TFLite/onnxruntime-mobile） |
| 手寫辨識 | 開源替代：Google ML Kit Digital Ink（線上 model download）作 v1；v2 接 TFLite Stroke Transformer |
| 滑動輸入 | 自製 HMM decoder（key trajectory → token） |
| 主題引擎 | 重新設計：JSON/Proto 樣式表 + Material 動態色 + 使用者圖片底圖 |
| Build | Gradle 8.x + Version Catalogs、Detekt、Spotless、Renovate |
| CI | GitHub Actions：build / lint / unit / instrumented (Gradle managed device)、發佈 Play Internal track |
| 測試 | JUnit5、Turbine、Robolectric、Compose UI test、Macrobenchmark、ime-test-app（自製） |
| 監測 | 自架 OpenTelemetry，不接 Firebase；崩潰用 Sentry 或 Acra（自架） |

---

## 4. Gradle Module 切分

```
:app                       — Manifest 註冊、Application、Hilt 組裝
:ime-service               — InputMethodService、生命週期
:framework
  :framework-core          — IIme、IProcessor、Keyboard、KeyboardDef、InputBundle
  :framework-ui            — Compose IME view、SoftKey、CandidateRow、Toolbar
  :framework-input         — InputContext、SurroundingTextTracker、HardKeyTracker
:processors
  :proc-output             — OutputProcessor、Compose-back commit
  :proc-zh-zhuyin          — ZhuyinHmmDecodeProcessor
  :proc-zh-pinyin          — TaiwanPinyinHmmDecodeProcessor
  :proc-zh-cangjie         — CangjieDecodeProcessor
  :proc-zh-handwriting     — ChineseHandwritingDecodeProcessor
  :proc-en-latin           — LatinIme + gesture
  :proc-shared             — auto-space, double-space, scrub-move
:decoder
  :decoder-jni             — JNI 介面 + Kotlin 包裝
  :decoder-native (CMake)  — C++ HMM / Trie / N-gram，輸出 libbpmf_core.so
  :decoder-data            — 詞典產生（離線工具）+ 資產打包
:handwriting
  :hw-mlkit                — ML Kit Digital Ink Recognition
  :hw-tflite               — TFLite stroke recognizer（v2）
:gesture
  :gesture-decoder         — 滑動輸入（重寫 hmmgesture）
:theme
  :theme-engine            — StyleSheet schema、ColorRole、Drawable factory
  :theme-presets           — 內建主題（dark/light/material-you/blue/...）
  :theme-editor            — Compose Theme Builder/Editor activity
:keyboards
  :kb-zh-zhuyin            — 4x10、實體鍵盤、浮動鍵盤定義
  :kb-zh-pinyin
  :kb-zh-cangjie
  :kb-en-qwerty / kb-en-9key
  :kb-symbols / :kb-emoji / :kb-kaomoji
  :kb-numeric / :kb-phone / :kb-password / :kb-datetime / :kb-url
:settings (Compose)        — Settings、FirstRun、Permissions、About、Feedback
:sync                      — opt-in 端對端加密同步（自架或 WebDAV / Drive 適配）
:metrics                   — 本地統計、可選遙測（OpenTelemetry）
:tv                        — Android TV / Leanback 鍵盤
:common                    — 工具、I/O、字串處理、簡繁轉換（讀取 sc2tc 表）
:dictionary-tools (JVM)    — 離線詞典編譯、語料清理 CLI
```

---

## 5. 功能對應表（舊 → 新）

| 原 APK 功能 | 新版做法 |
|---|---|
| HMM 解碼器（`libhmm_gesture_hwr_zh.so`） | 自寫 C++/Rust HMM trie + N-gram；資料用 g2pW / OpenCC / 公開語料訓練 |
| 注音 4×10 鍵盤 | Compose 自繪，定義在 `:kb-zh-zhuyin` JSON |
| 拼音／倉頡 | 處理器 chain 注入 cangjie/pinyin 解碼器 |
| 手寫（半屏/全屏） | v1 用 ML Kit Digital Ink；v2 接自訓 TFLite |
| 滑動輸入 | 自寫 gesture decoder（trajectory → key seq → token） |
| 11+ 主題 | 重新繪製 Material 3 主題；新增動態色（Android 12+） |
| 主題編輯器 | Compose-only Theme Builder：選背景圖、調透明、配色預覽即時生效 |
| 個人字典 + Google 帳號同步 | Room 儲存；同步改為 opt-in，預設關閉；可選 Google Drive AppData、WebDAV、E2EE blob |
| 聯絡人名稱候選 | 用 ContactsContract + 拼音/注音轉換，使用者明確同意 |
| Phenotype A/B | 內建簡易 Feature Flag（DataStore），不外送 |
| Omaha 自動更新 | 改走 Play Store；側載版可選 self-update Channel |
| Primes/UserFeedback | 改為「本機 dump → 使用者主動分享 zip」 |
| BackupAgent | 接 Android Auto Backup + 自訂 BackupAgent 過濾敏感資料 |
| TV / GamePad | `:tv` module，沿用 D-pad navigation + Compose-for-TV |
| 軟鍵盤上的剪貼簿 | 新增：Clipboard manager、置頂、敏感資訊過濾 |
| 候選列 emoji 切換 | 新增：可調工具列（剪貼簿/翻譯/設定/語音/手寫快捷） |
| `READ/WRITE_USER_DICTIONARY` | 廢棄；改為自家字典 + 標準 Android UserDictionary 介面僅讀取（Android 8-9） |
| 語音輸入 | 接 Android 13 `RecognitionService` + 可選離線 Whisper.cpp |
| AI 候選（新） | 可選串接本機 LLM（gemma 2B / phi-3 mini）做下一句預測，預設關閉 |

---

## 6. 系統架構（高階）

```
┌──────────────────────────────────────────────────────────────────┐
│                       BpmfInputMethodService                     │
│  (InputMethodService 子類；只負責 framework 接點與 Compose 容器) │
└──────────────┬──────────────────────────────────────┬────────────┘
               │ KeyEvent / IME callbacks             │ View hosting
               ▼                                       ▼
  ┌─────────────────────┐                  ┌────────────────────────┐
  │  InputController    │                  │  Compose IME View       │
  │  - 當前 InputBundle │◄────State Flow──►│  - CandidateRow         │
  │  - Processor chain  │                  │  - SoftKeyboard         │
  │  - SurroundingText  │                  │  - Toolbar / Clipboard  │
  └─────────┬───────────┘                  └────────────────────────┘
            │ KeyEvent (Kotlin)
            ▼
  ┌─────────────────────────────────────────────────────────────────┐
  │             Processor Chain（依語言／模式組裝）                  │
  │   Decode → AutoSpace → DoubleSpace → ScrubMove → Output         │
  └─────────┬───────────────────────────────────────────────────────┘
            │ JNI
            ▼
  ┌───────────────────────────────┐  ┌─────────────────────────────┐
  │  libbpmf_core.so (C++/Rust)   │  │  Handwriting / Gesture      │
  │  - Zhuyin/Pinyin/Cangjie HMM  │  │  - ML Kit Digital Ink (v1)  │
  │  - N-gram + Trie dictionary   │  │  - TFLite Stroke Net (v2)   │
  │  - Personal LM merge          │  │  - Gesture decoder          │
  └─────────┬─────────────────────┘  └─────────────────────────────┘
            │
            ▼
  ┌───────────────────────────────┐
  │  Room (user_dict, settings)   │
  │  Proto DataStore (theme, ui)  │
  └───────────────────────────────┘
```

---

## 7. 開發里程碑

### M0 — 立項與基礎建設（2 週）

- [ ] GitHub 專案、CI、Detekt/Spotless、Renovate、Conventional Commits
- [ ] 版本目錄與 Gradle 模組骨架（上面 §4 全部建空 module）
- [ ] Hilt + Coroutines + DataStore baseline
- [ ] 文件：CONTRIBUTING、ARCHITECTURE、PRIVACY、LICENSE（Apache-2.0）
- [ ] 風險評估：詞典／語料授權審查（必須使用 CC0 / CC-BY 公開語料）

### M1 — IME 框架可跑（4 週）

- [ ] `BpmfInputMethodService` + Compose IME view 雛形
- [ ] 鍵盤 JSON DSL（仿原 XML，但用 Kotlin serializer 反序列化）
- [ ] 一個英文 QWERTY 鍵盤可打字、可備刪、可送空白
- [ ] `InputContext` / `SurroundingTextTracker`（取代原 `libs.inputcontext`）
- [ ] Settings 入口（FirstRun → 開啟系統 IME → 切換 IME）
- [ ] 整合測試：自製 ime-test-app + Espresso

### M2 — 注音核心（6 週）

- [ ] `libbpmf_core` v0.1：純 C++ HMM decoder + 注音→字 候選
- [ ] 開源詞典：moedict + Wikipedia 詞頻 + CC-CEDICT（許可允許者）
- [ ] JNI binding（`decoder-jni`）：byte buffer-based、可序列化的查詢/學習介面
- [ ] `proc-zh-zhuyin` + `kb-zh-zhuyin` 4x10 上線
- [ ] AutoSpace / DoubleSpace 處理器
- [ ] 候選列翻頁、長按字、刪除候選
- [ ] 基準：與 chewing/libchewing 並列做精度迴歸

### M3 — 拼音、倉頡、英文滑動（4 週）

- [ ] `proc-zh-pinyin`、`kb-zh-pinyin`（含 ㄓ→zh / ㄔ→ch 等對應，沿用 `zhuyin_pinyin_map` 概念但自建）
- [ ] `proc-zh-cangjie`（資料來自開源倉頡碼表）
- [ ] 英文 gesture（Glide）：實作 `:gesture-decoder`
- [ ] 個人字典：使用次數加權、可手動加入

### M4 — 手寫（4 週）

- [ ] v1：ML Kit Digital Ink Recognition（zh-Hant、zh-Hans、en）
- [ ] 半屏 / 全屏手寫 overlay（Compose Canvas）
- [ ] 候選機制與 HMM decoder 共用 candidate UI
- [ ] 設定：筆畫粗細、辨識速度、識別語言
- [ ] v2 stretch：TFLite 自訓筆畫 transformer

### M5 — 主題系統（3 週）

- [ ] StyleSheet schema（JSON/Proto，色票 + 圓角 + 鍵框 + 字體尺度）
- [ ] 內建：Material You、Dark、Light、5 個彩色預設、Holo Retro
- [ ] Theme Selector / Builder / Editor（Compose Activity）
- [ ] 使用者圖片背景：可裁切、模糊、調明度
- [ ] 即時預覽（同 process IME view）

### M6 — 進階輸入體驗（3 週）

- [ ] 浮動鍵盤、單／雙手模式（左/右）
- [ ] 空白鍵滑動移動游標、長按符號擴充
- [ ] 可自訂工具列（剪貼簿、設定、語音、手寫、翻譯、語言切換）
- [ ] 剪貼簿：歷史、釘選、敏感資料偵測（電話/卡號 regex）
- [ ] Emoji（用 emoji2 + EmojiCompat）+ kaomoji + 符號 8 大類

### M7 — 同步與隱私（3 週）

- [ ] 個人字典端對端加密（libsodium）
- [ ] 同步適配器：Drive AppData / WebDAV / Self-host（pluggable）
- [ ] Per-app language（Android 13+）
- [ ] 隱私 dashboard：可下載/刪除所有本機資料
- [ ] Backup 規則 + Auto Backup 過濾

### M8 — TV / 平板 / 折疊（2 週）

- [ ] Leanback TV 鍵盤、D-pad navigation、Game pad
- [ ] sw600dp / sw768dp Compose 適配
- [ ] 折疊裝置：摺疊／展開狀態切換

### M9 — 上線（2 週）

- [ ] Play Store 隱私問卷、Data safety 表單
- [ ] Internal Track → Closed Beta（200 人）→ Open Beta → Production
- [ ] APK 大小目標：基礎包 < 15 MB；資料 split AAB
- [ ] 文件：使用者手冊、F-Droid metadata（可選同時上 F-Droid）

**總計：33 週（約 8 個月）一人份估算。實際依人力可平行壓縮。**

---

## 8. 風險與緩解

| 風險 | 影響 | 緩解 |
|---|---|---|
| 詞典／語料授權不清 | 法律風險 | 僅用 CC0/CC-BY/MIT；建立資料來源追溯表 |
| HMM decoder 精度不及 Google 原版 | 使用者流失 | 同時提供 libchewing 後端做 fallback；做 A/B 評測 |
| 手寫辨識依賴 ML Kit | 需網路下載模型；商用上架限制 | v2 改用自訓 TFLite；提供「不安裝手寫」選項 |
| Compose IME 在低階機效能 | 卡頓 | Macrobenchmark 守底線；關鍵 path 用 AndroidView fallback |
| Android 14+ FGS / 權限政策變動 | 上架被拒 | 嚴格只用 `BIND_INPUT_METHOD` + 明確 runtime 權限請求 |
| 雲端同步資安事件 | 私訊外洩 | E2EE、預設關閉、自架選項、第三方安全稽核 |
| 注音／拼音／倉頡資料表錯誤 | 體驗差 | 對拍 libchewing/rime 測試集，CI 跑回歸 |
| AI 候選（LLM）模型過大 | 安裝包暴增 | 模型獨立 AAB delivery + on-demand 下載 |
| 與系統剪貼簿/IME API 互動相容性 | 在部分 ROM 不可用 | 設備兼容性矩陣（小米/三星/OPPO/Pixel）每版本 smoke |

---

## 9. 開源／第三方授權清單（草案）

| 元件 | 授權 | 用途 |
|---|---|---|
| libchewing | LGPL-2.1 | 注音 fallback decoder（動態連結） |
| OpenCC | Apache-2.0 | 簡繁轉換（替代 `sc2tc/tc2sc`） |
| KenLM | LGPL-2.1 | N-gram 訓練（離線工具，不打包進 APK） |
| ML Kit Digital Ink | Google ML Kit 條款 | 手寫 v1 |
| TFLite / onnxruntime-mobile | Apache-2.0 / MIT | 手寫 v2 / AI 候選 |
| libsodium | ISC | 同步 E2EE |
| Jetpack / Compose | Apache-2.0 | UI 核心 |
| emoji2 | Apache-2.0 | Emoji |
| Sentry SDK（自架） | BSL/Apache | 崩潰收集 |
| Apache-2.0（本專案） | — | 程式碼授權 |

---

## 10. 度量指標（DoD）

- [ ] **冷啟動 IME**：低階機（Pixel 4a 等級）≤ 350 ms 顯示鍵盤
- [ ] **打字延遲**：按鍵 → 候選列更新 ≤ 60 ms（P95）
- [ ] **詞典精度**：對 1k 句測試集，Top-1 ≥ libchewing ＋ 10%
- [ ] **APK 大小**：基礎包 < 15 MB（資料 / 主題 / 手寫模型走 split AAB）
- [ ] **電量**：每小時連續輸入 < 4% 電池消耗（Pixel 6）
- [ ] **崩潰率**：< 0.2%（Play Vitals）
- [ ] **A11y**：TalkBack 鍵盤可完整朗讀；contrast AA
- [ ] **隱私**：靜默網路請求 = 0；網路存取必經設定明確同意

---

## 11. 立即下一步（本週可做）

1. **建立 Repo**：`bpmf-reborn`（Apache-2.0）
2. **抓 baseline 樣本**：在實機把原 APK 鍵盤體驗錄影，建立比較基準
3. **撰 keyboard schema v0**：拿 `ime_zh_tw_zhuyin_4x10.xml` 對照產出 Kotlin DSL spike
4. **詞典來源評估**：列出 moedict / CC-CEDICT / wiki-zh-Hant 詞頻 / 國教院新詞語料庫 的可用範圍與授權
5. **POC 模組**：跑通最小 IME（純 QWERTY 英文 + Compose 候選列）能在 Android 14 模擬器掛載
6. **法務 checklist**：套件名稱、UI 設計避免抄襲 Gboard（即便分享同框架，新版需重新繪製 icon／字型／配色）

---

## 附錄 A：原 APK 完整原生函式庫與資料表

```
lib/arm64-v8a/
  liben_data_bundle.so            9.2 KB
  libgnustl_shared.so             1.0 MB   GNU libstdc++
  libhmm_gesture_hwr_zh.so        6.6 MB   HMM + 手勢 + 手寫
  libhwrword.so                   2.3 MB   手寫整詞
  libzhuyin_data_bundle.so        8.3 MB   注音語言模型

res/raw/
  main_en_d3_20160715.gzip                 英文 dynamic LM（2016 訓練）
  zhuyin_pinyin_map                        注音⇄拼音 對應表
  sc2tc_unigram.jpg / sc2tc_bigram.jpg     簡→繁 unigram/bigram（副檔名是偽裝）
  tc2sc_unigram_index.jpg / tc2sc_bigram_index.jpg  繁→簡 索引
  token_character                          token→字 對應
  word_explanation                         詞義
  lb_voice_*.ogg                           Leanback 語音提示音
```

## 附錄 B：原 APK 完整模組依賴圖（推測）

```
zhuyin (app)
 ├── libs.framework (core IME)
 ├── libs.chinese
 │    ├── libs.hmm (decoder engine)
 │    │    └── libs.hmmgesture (gesture)
 │    └── libs.handwriting
 ├── libs.cangjie
 ├── libs.english ─── libs.latin (voice transcription)
 ├── libs.theme
 ├── libs.tv (Leanback)
 ├── libs.dataservice (account, sync, download, omaha)
 ├── libs.delight4 (神經預測介面，僅佔位)
 ├── libs.experiments (Phenotype)
 ├── libs.metrics / libs.imemetrics / libs.logging (Primes)
 ├── libs.inputcontext (surrounding text)
 └── libs.omaha (self-update)
```

---

_本計畫為起點藍圖；M0 結束前須與目標社群（注音愛用者 / 台灣 OSS 圈 / 無障礙群體）做一次需求驗證再凍結。_
