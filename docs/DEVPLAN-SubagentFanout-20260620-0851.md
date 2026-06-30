# BopomofoBruce — 子代理分工開發計畫

- 文件日期：2026-06-20 08:51
- 上游：[REBUILD-PLAN-SoloEdition](REBUILD-PLAN-SoloEdition-20260530-1325.md)
- 目的：把 Solo Edition 的 M0–M5 拆成可由 Sonnet 子代理（subagent）平行執行的工作包，每包獨立 worktree、獨立 PR、獨立 review loop。

---

## 1. 為什麼要做 fan-out 規劃

Solo Edition 假設每週 6–10 小時、12–18 個月。實際每個 milestone 內仍有大塊**互不相干**的工作可以平行。

把它拆成「波次（wave）+ 工作包（package）」：

- **Wave**：明確的相依層級（前一波結束才能開始下一波）。
- **Package**：同一 wave 內可由不同子代理同時做的最小單位，**模組邊界 = 衝突邊界**。

設計目標：
1. **零檔案衝突**：每包鎖定獨佔的 Gradle module。共用程式碼（介面、KeyData 等）一律放 `:common`，且只在 Wave 0 / 1 由單一代理寫定。
2. **介面先行**：Wave 0 定義所有公開 API 的型別、契約、Mock。後面波次只實作，不改契約。
3. **每包獨立 PR**：走和你已建立的「codex review → PR → Gemini/CodeRabbit review」流程。
4. **一週可清空**：每包目標約 10–20 小時工作量，子代理在 3–5 個對話 session 內可完成。

---

## 2. 模組依賴圖

```
                    ┌──────────────┐
                    │   :common    │  ◄── 介面、KeyData、Candidate、契約
                    └──────┬───────┘       (所有人都 import 它，沒人改它)
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
   ┌────▼─────┐   ┌────────▼─────┐    ┌──────▼─────────┐
   │  :theme  │   │  :keyboards  │    │ :decoder-native│
   └────┬─────┘   └────────┬─────┘    │  (libchewing)  │
        │                  │          └──────┬─────────┘
        │                  │                 │
        │                  │            ┌────▼─────┐
        │                  │            │ :decoder │
        │                  │            └────┬─────┘
        │                  │                 │
        └────────┬─────────┴────────┬────────┘
                 │                  │
            ┌────▼─────┐       ┌────▼─────┐
            │ :settings│       │  :ime    │
            └────┬─────┘       └────┬─────┘
                 │                  │
                 └────────┬─────────┘
                          │
                     ┌────▼─────┐
                     │  :app    │  ◄── 註冊、組裝
                     └──────────┘
```

---

## 3. Wave 結構（總覽）

| Wave | 名稱 | 平行度 | 代理數 | 預估 wall-clock |
|---|---|---|---|---|
| W0 | 立項與 Foundation | **序列**（一個代理） | 1 | 1–2 週 |
| W1 | 葉節點：原生引擎、介面、主題 | 高 | **3 平行** | 2–3 週 |
| W2 | 中層：decoder、鍵盤、IME 殼、設定 | 中 | **4 平行** | 3–4 週 |
| W3 | 整合與 wiring | 序列 | 1–2 | 2 週 |
| W4 | 打磨、測試、上架 | 部分平行 | 2–3 | 3 週 |

→ 全程約 **11–14 週 wall-clock**（vs. Solo Edition 原估 18 週 sequential），假設子代理之間切換友善。

---

## 4. Wave 詳細工作包

### W0 — Foundation（一個代理，序列）

**為什麼必須一個人做**：所有後續工作都靠這層的決策（Gradle 結構、Version Catalog、介面契約、CI）。多人並做反而會搶資源。

#### W0-1 — Gradle 骨架與工具鏈

- 8 個 module 全部開好空殼（含 `build.gradle.kts`、`AndroidManifest.xml` 或 `module-info.kt`）
- Version Catalog（`gradle/libs.versions.toml`）：列出 Kotlin 2.x、AGP 8.x、Compose BOM、Room、DataStore、JUnit5、Coroutines、Coil 等版本
- `settings.gradle.kts` 註冊所有 module
- Ktfmt + Android Lint baseline 設定
- `gradle.properties`：JDK 17、Compose enabled、useK2

**檔案範圍**（獨占）
```
settings.gradle.kts
build.gradle.kts (root)
gradle/libs.versions.toml
gradle/wrapper/
app/build.gradle.kts (空殼)
ime/build.gradle.kts (空殼)
keyboards/build.gradle.kts (空殼)
decoder/build.gradle.kts (空殼)
decoder-native/build.gradle.kts (空殼 + 預留 externalNativeBuild)
theme/build.gradle.kts (空殼)
settings/build.gradle.kts (空殼)
common/build.gradle.kts (空殼)
```

**驗收**
- [ ] `./gradlew assembleDebug` 全綠（即便每個 module 只有空 Application class）
- [ ] `./gradlew ktfmtCheck` 與 `./gradlew lint` 全綠
- [ ] PR 通過 codex 三輪 + Gemini 一輪

#### W0-2 — 介面契約（`:common`）

**這是整個 fan-out 的核心**。後面 Wave 1/2 的代理都靠這層介面協作；定下來後**禁止改動**，否則所有正在進行中的 PR 都會炸。

需要定義的 Kotlin 型別：

```kotlin
// common/src/main/kotlin/com/bopomofobruce/common/

// 鍵盤模型
sealed interface KeyAction { ... }
data class KeyData(val label: String, val action: KeyAction, ...)
interface KeyboardDef { fun rows(): List<List<KeyData>>; ... }

// 候選詞
data class Candidate(val text: String, val score: Float, val source: CandidateSource)
enum class CandidateSource { PRIMARY, PERSONAL, EMOJI }

// 輸入狀態（不可變）
data class InputState(
  val composing: String,             // 注音 buffer
  val candidates: List<Candidate>,
  val cursorIndex: Int,
)

// Processor chain 介面
interface InputProcessor {
  fun process(event: InputEvent, state: InputState): ProcessResult
}
sealed interface ProcessResult { ... }

// Decoder 介面（讓 :decoder / :decoder-stub 都可實作）
interface ZhuyinDecoder {
  fun input(buffer: String): List<Candidate>
  fun commit(candidate: Candidate)
  fun reset()
}

// Theme 介面
interface KeyboardTheme {
  val colors: KeyboardColors
  val dimens: KeyboardDimens
}

// IME context 抽象（讓 settings/keyboards 可以做 preview 不需 InputMethodService）
interface ImeContextProvider {
  val currentInputType: ImeInputType
  fun commitText(text: String)
}
```

加上 `:common` 應提供 in-memory `FakeZhuyinDecoder` 與 `FakeImeContextProvider` 給其他 module 做 unit test 與 Compose preview。

**驗收**
- [ ] 所有介面有 KDoc
- [ ] 每個介面至少有 1 個 fake 實作
- [ ] PR 後做一次「契約凍結」commit tag `contracts-v1`

#### W0-3 — CI workflow

- `.github/workflows/ci.yml`（Gradle）：build、lint、ktfmt、unit test、上傳 artifact（APK debug）
- 首次 push 前提醒 `gh auth refresh -s workflow`

**驗收**
- [ ] CI 在 PR + main push 都跑得起來
- [ ] APK debug 可下載

#### W0-4 — ADR-0001 ~ 0005

寫五個 ADR（Architectural Decision Record）封存決定：

| ADR | 主題 |
|---|---|
| 0001 | 為什麼 decoder 後端選 libchewing 不自寫 HMM |
| 0002 | 為什麼 8 模組不是 30 模組 |
| 0003 | 為什麼用 Compose-only 不用 XML |
| 0004 | 為什麼不引入 Hilt（手動 DI 即可） |
| 0005 | 為什麼 v1 不做雲端同步 |

**驗收**
- [ ] 五份 ADR 放在 `docs/adr/`，每份含 Context / Decision / Consequences

---

### W1 — 葉節點（3 個子代理可平行）

W0 結束後，介面凍結，三個獨立技術領域可同時下手。

#### W1-A — `:decoder-native` 把 libchewing 編成 .so

**子代理檔案領域**：`decoder-native/` 全部 + `gradle/libs.versions.toml`（只能加 NDK 相關）

**工作內容**
- vendor libchewing 進 `decoder-native/cmake/libchewing/`（git submodule 或固定 tarball；路徑與 [ADR-0001](adr/0001-libchewing-decoder-backend.md)、`AGENTS.md` NDK 段落、`GEMINI.md` 一致）
- CMakeLists.txt：編出 `libbpmf.so`，wrap libchewing 並輸出 C API：
  ```c
  void*  bpmf_init(const char* data_path);
  size_t bpmf_input(void* handle, const char* zhuyin, char** candidates_out);
  void   bpmf_commit(void* handle, size_t index);
  void   bpmf_free(void* handle);
  ```
- externalNativeBuild + ABI filter：arm64-v8a, armeabi-v7a
- 提供 `getDataPath()` Kotlin API，把 chewing 字典 asset 解壓到 cacheDir
- **JNI binding 不在這包**（留給 W2-A `:decoder`）

**驗收**
- [ ] `./gradlew :decoder-native:assembleDebug` 產生 .so
- [ ] 寫一個 connectedAndroidTest，在裝置上：`bpmf_init()` 成功、`bpmf_input("ㄋㄧˇ ㄏㄠˇ")` 拿到非空候選、無 leak
- [ ] APK size 增量 < 4 MB

**禁碰**：任何其他 module、`:common` 的介面

---

#### W1-B — `:theme` 主題引擎

**子代理檔案領域**：`theme/` 全部 + 介面定義（已在 :common，**只讀**）

**工作內容**
- 實作 `KeyboardTheme` 介面
- StyleSheet schema（JSON via kotlinx.serialization）：colors / dimens / shapes / typography
- 3 個內建主題：
  - `MaterialYouTheme`（Android 12+ 動態色，<31 退化為 light/dark）
  - `LightTheme`、`DarkTheme`
- 自訂相片背景：
  - `PhotoBackground` data class（uri、blur、opacity、tint）
  - 用 Coil 載入、Compose `Brush` 套用
- 預覽用 Compose `@Preview` widget

**驗收**
- [ ] 三個主題各有 Compose `@Preview`
- [ ] PhotoBackground 在 Pixel 6 實機上開圖庫選圖→渲染 < 200 ms
- [ ] 主題序列化/反序列化 round-trip test

**禁碰**：任何其他 module

---

#### W1-C — `:keyboards` 鍵盤定義（**僅注音 4×10 + 符號 + 數字 + 密碼**）

**子代理檔案領域**：`keyboards/` 全部 + 介面定義（已在 :common，**只讀**）

**工作內容**
- 注音 4×10 鍵盤（橫直屏各一份 JSON）
- 標準符號鍵盤（含繁中專屬全形標點 ，。、；：「」『』）
- 數字鍵盤、密碼鍵盤、電話、URL、日期時間
- 透過 `KeyboardDef` 介面返回
- Emoji 與 Emoticon 鍵盤**不做**（留 W2-D 一起做）
- 不做 UI 渲染（那是 `:ime` 的事），只負責定義

**驗收**
- [ ] 注音 4×10 layout 與 [原 APK ime_zh_tw_zhuyin_4x10.xml] 鍵位一致（拍照對照）
- [ ] 所有 KeyboardDef 反序列化 round-trip test
- [ ] kotlinx.serialization JSON 通過 schema 驗證

**禁碰**：任何其他 module

---

### W1 同步點：W1 三包全部 merge 後

- 跑一次 `./gradlew assembleDebug` 確認三 module 仍各自獨立可建
- 跑 codex review 整批變更，確保介面契約沒人偷改
- 寫 `docs/devlog/W1-summary.md`

---

### W2 — 中層（4 個子代理可平行）

W1 結束後，可同時開 4 個子代理：

#### W2-A — `:decoder`（JNI + 個人字典）

**依賴**：W1-A（.so）+ W0-2（介面）
**檔案領域**：`decoder/`

**工作內容**
- JNI binding 包 `libbpmf.so` 的 C API（用 `external fun` + System.loadLibrary）
- 實作 `ZhuyinDecoder` 介面（取自 :common）
- 個人字典：Room schema `PersonalDictEntry(word, zhuyin, freq, lastUsedAt)`
- 候選詞合併策略：libchewing 結果 ⊕ 個人字典加權
- **保留 `ZhuyinDecoder.input` 為同步**（contracts-v1 已凍結；見 `common/.../ZhuyinDecoder.kt` 的 thread-safety 註解）。讓 :ime 端負責把呼叫派到 work dispatcher，避免 main thread blocking — `:decoder` 不對外提供 suspend 版本，也不引入 dispatcher 相依
- 提供 `DecoderModule` 物件給 :ime 拿 instance（手動 DI）

**驗收**
- [ ] 1k 條真實注音輸入 unit test（用 `chewing-default-table` 跑 baseline）
- [ ] 個人字典加詞/刪詞/查詢 Room test
- [ ] Top-1 精度 ≥ libchewing baseline 的 95%（驗證 wrap 沒走鐘）

**禁碰**：其他 module；`:common` 介面只讀

---

#### W2-B — `:ime`（InputMethodService + Compose IME view）

**依賴**：W0-2（介面）
**檔案領域**：`ime/`

**工作內容**
- `BpmfInputMethodService : InputMethodService`
- 用 `AbstractComposeView` 掛載 Compose IME view
- `CandidateRow` Compose 元件（橫向 LazyRow、點選回 callback）
- `SoftKeyboardView` Compose 元件：吃 `KeyboardDef`、套 `KeyboardTheme`
- Processor chain runtime：`InputProcessor` 串接、`InputState` flow
- Decoder 來源用 `:common` 的 `FakeZhuyinDecoder`（不直接 import `:decoder`，由 `:app` 注入）
- 退格、空白、Enter、Shift 完整

**驗收**
- [ ] `BpmfInputMethodService` 可被裝在系統 IME 列表
- [ ] 用 fake decoder 仍可顯示假候選詞 → 點選送字
- [ ] Pixel 4a 上鍵盤展開 < 350 ms

**禁碰**：`:decoder`、`:keyboards`、`:theme` 的內部（只透過介面用）

---

#### W2-C — `:settings`（Compose 設定 + FirstRun）

**依賴**：W0-2 + W1-B（主題介面）
**檔案領域**：`settings/`

**工作內容**
- `SettingsActivity`：純 Compose、Material 3
- 分頁：主題、輸入、按鍵、字典、關於、回報問題
- 「主題」頁：套用 W1-B 的 ThemePreview 即時預覽
- FirstRun activity：引導三步驟（啟用 IME → 切換 IME → 試打）
- 設定持久化用 DataStore Proto
- 隱私頁：明列「本 app 不打網路 / 不上傳」

**驗收**
- [ ] 所有設定變更立即生效（用 Flow）
- [ ] FirstRun 完整跑過 = 系統 IME 列表有本 IME
- [ ] Compose UI test：每個分頁都進得去

**禁碰**：其他 module（除了透過 :common 介面）

---

#### W2-D — Emoji / Symbol 擴充與 `:common` Helper

**依賴**：W0-2
**檔案領域**：`common/src/main/kotlin/com/bopomofobruce/common/emoji/` + `keyboards/src/main/kotlin/com/bopomofobruce/keyboards/emoji/`

> 唯一一包會碰 `:common` 與 `:keyboards` — 因為涉及 emoji 表，且不影響介面契約。

**工作內容**
- Emoji 鍵盤（emoji2 + EmojiCompat）：8 大類別
- Kaomoji 鍵盤：5 類別（笑、汗、驚、悲、不悅）
- 簡繁轉換工具（OpenCC port 或 Java OpenCC binding）放 `:common`
- 字串工具（半形/全形、注音/拼音 normalize）放 `:common`

**驗收**
- [ ] Emoji 顯示在 Pixel 6 + Pixel 4a 都正常（emoji2 字型 fallback）
- [ ] 簡繁互轉 round-trip 100 條測試
- [ ] 不破壞既有介面（contracts-v1 tag 不動）

**禁碰**：除上述 `:common` emoji 與 string 工具外，其他 `:common` 一切**只讀**

---

### W2 同步點

- 整合測試前的最後一關
- 跑 `./gradlew :app:assembleDebug` — 雖然 `:app` 還沒寫，但所有 module 應該可獨立 build
- W2 四 PR 都 merge 後才開 W3

---

### W3 — 整合（1–2 個代理，序列）

#### W3-1 — `:app` Wiring

**檔案領域**：`app/` + `BopomofoBruce/AndroidManifest.xml` 補完

**工作內容**
- `BpmfApplication`：手動 DI 組裝（提供單例給 IME service）
- 把 `:decoder` 的 `DecoderModule` 注入到 `:ime` 的 service
- Manifest：註冊 `BpmfInputMethodService` + `BIND_INPUT_METHOD`、`SettingsActivity`、`FirstRunActivity`
- 真實 vs fake decoder 切換（debug flag）
- 整合測試：實機上跑「打 ㄋㄧˇ → 看到「你」候選 → 送字到測試 EditText」

**驗收**
- [ ] 端對端：開啟系統設定 → 啟用 IME → 在 Chrome 打字
- [ ] 注音輸入準確度與 libchewing baseline 同等
- [ ] 三個內建主題切換正常
- [ ] APK ≤ 15 MB

---

#### W3-2 — `BopomofoBruce.apk` Beta 5 人試打

- 5 個朋友裝 debug APK 試 1 週
- 整理 issue → 排優先級
- 修 P0 阻斷性問題

---

### W4 — 打磨與上架（2–3 代理部分平行）

| 包 | 內容 | 平行 |
|---|---|---|
| W4-A | 隱私政策、README 上線版、Play Store 截圖／描述／資料安全表 | 可單獨做 |
| W4-B | 無障礙最低限度（TalkBack 不爆、IME 可朗讀） | 可單獨做 |
| W4-C | Macrobenchmark：冷啟動、打字延遲、電量 | 可單獨做 |
| W4-D | Beta → Internal track | 序列，最後 |

---

## 5. Worktree 機制

### 5.1 目錄佈局

```
~/Projects/GitHub/Bruce1986/
├── BopomofoBruce/                       ← main (clone)
├── BopomofoBruce-w0-foundation/         ← W0 用
├── BopomofoBruce-w1-decoder-native/     ← W1-A
├── BopomofoBruce-w1-theme/              ← W1-B
├── BopomofoBruce-w1-keyboards/          ← W1-C
├── BopomofoBruce-w2-decoder/            ← W2-A
├── BopomofoBruce-w2-ime/                ← W2-B
├── BopomofoBruce-w2-settings/           ← W2-C
└── BopomofoBruce-w2-emoji-common/       ← W2-D
```

### 5.2 命名與建立

```bash
# 從 main 拉出 worktree + 新分支
cd ~/Projects/GitHub/Bruce1986/BopomofoBruce
git fetch origin
git worktree add ../BopomofoBruce-w1-theme -b feat/w1-theme origin/main

# 分支命名規則：<wave>-<package-slug>
# W0-1 Gradle  → feat/w0-gradle-skeleton
# W0-2 介面    → feat/w0-common-contracts
# W1-A 原生   → feat/w1-decoder-native
# W1-B 主題   → feat/w1-theme
# W1-C 鍵盤   → feat/w1-keyboards
# W2-A 解碼   → feat/w2-decoder-jni
# W2-B IME    → feat/w2-ime-service
# W2-C 設定   → feat/w2-settings
# W2-D Emoji  → feat/w2-emoji-common
# W3-1 整合   → feat/w3-app-wiring
```

### 5.3 一個 worktree 的生命週期

1. 子代理收到工作包，cd 到對應 worktree
2. 對照「檔案領域 + 禁碰清單」工作
3. 進度寫進 `docs/devlog/<branch>.md`（每天一句也行）— **可選但鼓勵**：每包獨立 devlog 不是 merge gate；強制要求的是 wave 收尾時的 `docs/devlog/W<N>-summary.md`（見每個 Wave 同步點）
4. 本機跑 `./gradlew :<module>:assembleDebug` + `:<module>:test`
5. 跑 codex review（同你已建立的 3 輪流程）
6. push branch → `gh pr create` → `/gemini review` + `@coderabbitai review`
7. PR review loop → squash & merge
8. 清理：`git worktree remove ../BopomofoBruce-<slug>`，分支自動刪

### 5.4 衝突風險與緩解

| 風險 | 機率 | 緩解 |
|---|---|---|
| 兩包同時改 `:common` 介面 | 低 | Wave 0 凍結後**禁止**；W2-D 例外限定在 emoji/string 工具 |
| 兩包同時改 `libs.versions.toml` | 中 | 集中在 W0；後續若要加版本，先開 micro-PR |
| `gradle.properties` 變更 | 低 | 同上，集中 W0 |
| `:app` Manifest 區塊衝突 | 高 | W3 才動 Manifest；W2 各包不准碰 |
| 多個 module 同時改 build.gradle.kts | 中 | 各包只能改自己的 module 那份 |

### 5.5 子代理交接協議

每個 worktree PR 描述必須附：

```markdown
## Scope owned
- module: :<name>
- files: <list>

## Touches outside (must be empty for W1/W2)
-

## Interfaces consumed (from :common)
- com.bopomofobruce.common.<Type>

## Interfaces produced (for downstream)
- com.bopomofobruce.<module>.<Type>

## Risk to other worktrees
- (none / known interaction with #PR-N)
```

---

## 6. 子代理啟動樣板

每個工作包配一份 prompt 給 Sonnet subagent。模板：

```
你正在為 BopomofoBruce 專案的 Wave <N> 工作包 <ID> 工作。

【背景】
- Repo: https://github.com/Bruce1986/BopomofoBruce
- Worktree: ~/Projects/GitHub/Bruce1986/BopomofoBruce-<slug>
- 分支: feat/<slug>
- 先讀: README.md、project-handbook.md、docs/REBUILD-PLAN-SoloEdition-*.md、
       docs/DEVPLAN-SubagentFanout-*.md（本文件）的 §4.<ID>、§10（協調規則）

【Pre-flight — 開工前必跑，順序不可調】
1. cd ~/Projects/GitHub/Bruce1986/BopomofoBruce
2. git fetch origin && git pull --ff-only origin main
3. cat docs/STATUS.md         # 找你的包，確認狀態仍是「🔵 Backlog」
4. gh pr list --state open --json number,title,headRefName,baseRefName
5. git branch -r | grep feat/  # 看遠端有沒有同名分支
6. 若你的包已被認領 (狀態非 Backlog)：stop，回報 lead，不要繼續

【認領（claim）— Pre-flight 通過後執行】
7. 編輯 docs/STATUS.md 該包那一列：
   - 狀態 🔵 Backlog → 🟡 Claimed
   - 認領者 填你的 session id 或 agent 名稱
   - Worktree 填 ../BopomofoBruce-<slug>
   - 分支 填 feat/<slug>
   - 更新時間 填 YYYY-MM-DD HH:MM
8. git add docs/STATUS.md && git commit -m "chore(status): claim <ID> by <agent>"
9. git push origin main
   - 若被 reject (有人剛 push)：git pull --rebase origin main，回到步驟 3 重做
   - 若 main protected 拒絕直 push：改開 micro-PR claim/<ID>，請 lead 立即 squash merge
10. 認領成功才能 cd 進 worktree 開工

【你的範圍】
獨占模組: :<module>
可讀但禁改: <list>
完全禁碰: 其他所有 module

【契約】
從 :common 取以下介面: <list>
你產出給下游的介面: <list>

【接受標準】
1. <測試 1>
2. <測試 2>
3. PR 通過 codex 3 輪 + Gemini 1 輪、無 blocking
4. devlog 寫好（個別包 devlog 為可選；wave summary devlog 為強制）
5. STATUS.md 走完狀態流（Claimed → In progress → In review → Merged）

【工作流】
1. 開工前把 STATUS.md 該列狀態改 🟠 In progress（順手一個 commit 推 main）
2. cd worktree、實作、本機 build/test、codex review、修
3. push branch → gh pr create → /gemini review + @coderabbitai review
4. PR 一開好，立刻把 STATUS.md 狀態改 🟣 In review、補上 PR 號（推 main）
5. 走 PR review loop（參考 .claude/skills/pr-review-loop）
6. squash & merge 後：
   - 把 STATUS.md 狀態改 ✅ Merged、把這列搬到「Recently merged」段
   - git worktree remove ../BopomofoBruce-<slug>

【不要做的事】
- 不要改 :common 公開介面（W2-D emoji/string 工具除外）
- 不要動其他 module 的 build.gradle.kts
- 不要 force push
- 不要 skip 失敗 test 來 push 過 CI
- 不要動 STATUS.md 不屬於你的列

完成後回報: PR URL + 一句話 changelog + 你在 STATUS.md 留下的最新狀態。
```

實際使用：把這段貼到新 Claude Code session 的開頭，後面接「請開始 Wave 1-B 主題引擎」之類。

---

## 7. 並行排程示意

```
Week ╲ Agent       Lead         Agent A          Agent B          Agent C          Agent D
─────────────────────────────────────────────────────────────────────────────────
W1   W0-1 Gradle
W2   W0-2 契約    
W3   W0-3 CI + 0-4 ADR
─── W0 freeze（合併 main）─────────────────────────────────────────
W4                 ……idle……    W1-A native      W1-B theme       W1-C kbd
W5                 ……idle……    W1-A native      W1-B theme       W1-C kbd
W6   (review &)
     W1 sync
─── W1 freeze（介面契約已凍結，模組可獨立建）────────────────────
W7                 ……idle……    W2-A decoder     W2-B ime         W2-C settings    W2-D emoji
W8                 ……idle……    W2-A decoder     W2-B ime         W2-C settings    W2-D emoji
W9                 ……idle……    W2-A decoder     W2-B ime         W2-C settings    W2-D emoji
W10  W2 sync
─── W3 開始 ─────────────────────────────────────────────────────
W11  W3-1 wiring
W12  W3-1 wiring
W13  W3-2 beta
W14  W4 polish     W4-A README   W4-B a11y         W4-C perf
                                                                W4-D ship
```

> Lead = 你本人（PM + reviewer）。Agent A/B/C/D = Sonnet subagents。
> Lead 在 Wave 1/2 時主要工作是 review PR、回答 agent 提問、處理 merge。

---

## 8. 失敗模式與止損

| 失敗 | 徵兆 | 處置 |
|---|---|---|
| 介面契約被改 | W1/W2 中有 PR 編輯 `:common` 公開類 | 拒絕 merge，要求拆 micro-PR 走 W0 流程 |
| Worktree 撞 main | merge conflict | 該 agent 先 `git pull --rebase origin main`，自行解或回報 lead |
| 一包持續延宕 > 2× 估時 | agent 卡 > 2 週 | lead 介入：拆更小、換 agent、或刪掉重來 |
| Codex 一輪一輪 nitpick 不收斂 | 同類意見來回 > 3 輪 | lead 判定下車，agent 把剩下 non-blocking 寫成 issue 後 merge |
| 多包同時 ready，review 不過來 | PR queue > 4 | lead 暫停開新 worktree，先清完現有 |

---

## 9. 度量

| 指標 | 目標 |
|---|---|
| 每包 wall-clock | ≤ 2.5 週（含 review loop） |
| 每包 PR 數 | 1（squash） |
| Codex 平均輪數 | ≤ 3 |
| Gemini 平均輪數 | ≤ 2 |
| 介面契約變更次數（W0 後） | 0（容忍 1 次經 lead 簽核） |
| Worktree merge conflict 次數 | ≤ 1 / 包 |

---

## 10. 協調與 visibility — STATUS.md live 表格

### 10.1 為什麼需要

§5 的 worktree 機制只解決「檔案不衝突」。但**「誰已經在做什麼、進度到哪」**是另一個維度的問題：

| 場景 | 不處理會發生什麼 |
|---|---|
| 兩個子代理同時想做 W1-B | 一個做完開 PR 才發現另一個也快做完 — 白工 |
| 一個子代理斷在中途（context lost、token 用完） | 後來的代理不知道這包還在做、以為 backlog |
| 你剛 spawn 完新代理，想知道目前整體進度 | 沒地方一眼看完 |

解法：**用一份檔案當作 live 表格**，加上 git push 當天然 lock。

### 10.2 機制

**單一事實來源**：`docs/STATUS.md`（在 main 上）

每個工作包一列，狀態欄走以下流轉：

```
🔵 Backlog  →  🟡 Claimed  →  🟠 In progress  →  🟣 In review  →  ✅ Merged
                                                                        │
                                             ⛔ Blocked  (可從任一狀態進入) │
                                                                        ▼
                                                              移到 Recently merged
```

**鎖機制**：誰先 `git push origin main` 改該列誰得手。push 衝突時，`git pull --rebase` 後**重檢主表該列的狀態欄**；若已被搶走則放棄、回報 lead。

> Race condition 的範圍：`git push` 序列化只保證「commit 不會交錯」，**不保證 STATUS.md 主表 + Active worktrees + Recently merged + Blockers + Lead 巡視紀錄五處欄位永遠一致**。每次改動務必相關段落一起改、單一 commit，並依靠 §10.3 列出的人工 sanity checklist 兜底（自動化 lint script 待補）。一秒內兩個人搶同一包是稀有事件，但別假設它不會發生 — rebase 後重檢主表是真正的防線。

**與專案 push policy 的關係**：`project-handbook.md` 規定 main 直推只限 typo 級小修。**`docs/STATUS.md` 的 live bookkeeping 動作被列為例外、比照 typo-class 直推允許**（同一 commit 必須只動 `docs/STATUS.md`、commit type 用 `chore(status):`）。允許的動作清單與 [`project-handbook.md` Code Review 流程段](../project-handbook.md#3-review)的例外條款字面**一字不差**地一致：

- 主表任意列的「狀態 / 認領者 / Worktree / 分支 / PR / 更新時間」欄位更新
- 「Active worktrees」段落 append / delete 一行
- 「Recently merged」段落搬入新列（並順手把超過 5 筆的舊列移到 `docs/devlog/`）
- 「Blockers」段落新增 / 移除 / 更新原因連結
- 「Lead 巡視紀錄」段落 append 一列

清單之外（schema 變動、章節結構、圖例文字、範例格式變更）一律走 PR。若未來開啟 main branch protection，bookkeeping 改走 micro-PR + auto-squash；屆時「git push 序列化當 lock」會降級為「open PR 的 head SHA 序列化當 lock」，需要 lead 配合快速 merge。

### 10.3 STATUS.md 的格式（schema）

詳見 [`docs/STATUS.md`](STATUS.md)。要點：

- 主表格：`ID / Wave / 範圍 / 狀態 / 認領者 / Worktree / 分支 / PR / 更新時間`
- **「更新時間」是 heartbeat 欄**：認領者每次 commit / push / 開 PR / 走 review loop 一輪都要順手更新；空白超過 24 h 視為可疑、48 h 視為 stale（見 §10.4）。
- 副區塊：
  - **Active worktrees**：認領者開工時 append 一行；merge 後刪
  - **Recently merged**（最近 5 筆）：merge 完搬到這
  - **Blockers**：被 ⛔ 卡住的包，附原因連結

每個 sub-agent 啟動時的 pre-flight 第 0 步必須做 **人工** sanity check（自動化 lint script `scripts/status-lint.sh` 待補；issue 追在 [W4-C 之前的 tooling backlog]）：

- 主表狀態必為 6 圖例其中之一
- Claimed/In progress/In review 列必有對應的 Worktree 與分支欄
- 「Active worktrees」實際列數 == 主表 Claimed + In progress + In review 列數
- 「Recently merged」筆數 ≤ 5（超過要搬 `docs/devlog/`）

### 10.4 子代理 vs lead 的責任分配

| 動作 | 由誰做 |
|---|---|
| 認領（Backlog → Claimed） | 子代理（pre-flight 後第一個 commit） |
| 開工（Claimed → In progress） | 子代理 |
| 開 PR（In progress → In review） | 子代理（同一 commit 補 PR 號） |
| Merge（In review → Merged） | Lead（或子代理 squash merge 後）順手把列搬到 Recently merged |
| 標 ⛔ Blocked | 子代理發現阻塞時自己標 + 留原因 |
| 解 ⛔ Blocked | Lead 處理後改回 In progress |
| 清掉 stale Claimed（更新時間 > 48 h 無進度） | Lead — 改回 Backlog，附 devlog 說明 |
| 心跳 / 進度回報（每天工作前後刷新「更新時間」欄） | 子代理 — 即使只是「還在 review loop 中」也要更新 |

### 10.5 失敗模式對應

| 失敗 | 對應 |
|---|---|
| 子代理斷線、STATUS 卡在 Claimed | Lead 48 h 後巡視；強制改 Backlog，可被別人重認 |
| 兩人同時 push 認領，後者被 reject | Git 機制天然處理；後者 rebase 後發現非 Backlog 自動放棄 |
| 子代理忘記改 STATUS | Lead PR review 時看到沒同步就 block merge |
| STATUS.md 自己被 merge 衝突弄壞 | Lead 手動修；表格保持「人類友善」格式不要過度自動化 |

### 10.6 與 GitHub Issue 的關係

目前**不**用 GitHub Issue 當 work package tracker（理由：避免提早建出 17 個空 issue 變雜訊）。

未來若團隊變大 / 想自動化（例如串 Linear、GitHub Projects），STATUS.md 可降級為「人類視角」、由腳本同步上游。

---

## 11. 立即下一步

1. **本週**：自己跑 W0-1（Gradle 骨架）+ W0-2（介面契約）。**不外包**這兩個，因為決策密度高。
2. **W0 結束後**：第一次嘗試 fan-out — 同時開 W1-A / W1-B / W1-C 三個 worktree，丟給三個獨立 Claude session。
3. **第一個 W1 PR merge 後**：寫一篇短 devlog 紀錄「子代理協作了哪些事順利、哪些事踩雷」，回饋回本計畫。

---

## 附錄 A — 一鍵建立全部 W1 worktree

```bash
cd ~/Projects/GitHub/Bruce1986/BopomofoBruce
git fetch origin
for slug in w1-decoder-native w1-theme w1-keyboards; do
  git worktree add ../BopomofoBruce-$slug -b feat/$slug origin/main
done
git worktree list
```

## 附錄 B — Wave 完成後清理

```bash
cd ~/Projects/GitHub/Bruce1986/BopomofoBruce
for slug in w1-decoder-native w1-theme w1-keyboards; do
  git worktree remove ../BopomofoBruce-$slug 2>/dev/null
  git branch -d feat/$slug 2>/dev/null
done
git worktree prune
```

---

_本文件是執行計畫，不是憲法。第一輪 fan-out（W1）跑完後，根據實際經驗回頭修。_
