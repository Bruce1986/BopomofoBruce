# W1-A devlog — `:decoder-native` libchewing 編譯 spike

- 分支：`feat/w1a-decoder-native`
- 期間：2026-08-10 14:5x – 16:1x (UTC+8)（分兩段：14:5x–15:0x 調查+停工回報，
  15:1x–16:1x owner 裁示後續跑）
- 狀態：**驗收標準全數通過（實機 connectedAndroidTest 綠、assembleDebug 兩
  ABI 皆產出 .so、ktfmtCheck/lint 綠）。細節與已知缺口見下方「第二階段」。**

## 環境確認（動工前）

- JDK 21、`local.properties` 的 `sdk.dir` 已指向
  `/opt/homebrew/share/android-commandlinetools`
- 已裝好：`ndk;27.2.12479018`、`cmake;3.22.1`
- **`platforms;android-35` 尚未裝好**（`platforms/` 下只看到 `android-34`），
  lead 背景安裝可能還沒跑完或裝到別的路徑；`decoder-native/build.gradle.kts`
  目前 `compileSdk = 35`，這點會擋到後續 `assembleDebug`，但不是本次卡關的
  主因，先記錄。
- 實機 `R6AIB700988748X` 有連（`adb devices` 確認），可跑
  `connectedAndroidTest`（尚未跑到這步）。

## 卡關：libchewing 主體已從 C99 改寫成 Rust（ADR-0001 前提不成立）

ADR-0001 的技術判斷建立在「libchewing 是 C99，無 GUI 依賴，NDK r26 + CMake
理論上可直接 cross-compile」。這次 vendor 前照規範去查了 upstream 最新狀態，
發現這個前提在近兩年已經不成立：

- 查 [chewing/libchewing](https://github.com/chewing/libchewing) 的 release
  列表：最新穩定版 **v0.12.0**（2026-04-06 發布，commit
  `05ae6bcb9309c466a1b32d69c146bc583be04747`）。release note 裡明寫
  `rust: breaking! renamed SystemDictionaryLoader to AssetLoader` 等項目，
  且註明「主 repo 已搬到 Codeberg」。
- 拉 v0.12.0 的檔案樹確認：`src/` 底下全部是 `.rs`（`lib.rs`、
  `conversion/`、`dictionary/`、`editor/`、`zhuyin/` 等），沒有任何 `.c`；
  C API 是獨立的 `capi/`（`Cargo.toml` + `cbindgen.toml` + `include/`），
  透過 `cbindgen` 從 Rust 產生標頭、Cargo 編譯出 `.so`/`.a`，CMake 檔
  （根目錄仍有 `CMakeLists.txt`）現在扮演的角色是「呼叫 cargo 再包裝」而
  非直接編 C 原始碼。
- 往回查每個 tag 的 `src/` 副檔名分布，確認轉型時間點：

  | tag | 發布日 | `src/` 副檔名 |
  |---|---|---|
  | v0.5.1 | 2016-05 | `c` `h` `am`（純 C，Autotools） |
  | v0.6.0 | 2024-01 | `c` `h` `rs` `map`（開始混入 Rust） |
  | v0.7.0 / v0.8.x | 2024 | `c` `h` `rs` `map`（C/Rust 並存過渡期） |
  | v0.9.0 起（含 v0.10/v0.11/v0.12） | 2024-08 起 | 只剩 `rs`（核心邏輯全部改寫成 Rust，`Cargo.toml`／`Cargo.lock` 進 repo 根目錄） |

  也就是說：**唯一還是「純 C99、CMake 直接編」的版本是 2016 年的 v0.5.1**，
  距今近 10 年沒更新，字典與 bug fix 都停在 2016 年的狀態——這正好會直接
  踩到 ADR-0001 自己列的「重評觸發條件」（詞典 5 年沒更新導致新詞嚴重缺失）。
  v0.9.0（2024-08）之後的所有現行版本，編譯都需要完整 Rust toolchain
  （`cargo` + `cbindgen` + Android cross target `aarch64-linux-android` /
  `armv7-linux-androideabi`，通常搭 `cargo-ndk`），不是單純 NDK + CMake 就能
  過。

- 本機環境確認：`cargo`／`rustc` 已裝（Homebrew，1.96.1），但
  **`cargo-ndk` 未裝、Android cross-compile target 也未裝**（Homebrew 版
  rustc 不是走 `rustup`，沒有標準的 target 管理指令）；且照子代理任務書「只
  執行 CMake/NDK 建置流程」的紀律，安裝 Rust cross toolchain、改變整個編譯
  管線已經超出這包被授權的範圍。

## 為什麼在這裡停下、不自行決定

任務書明寫：「遇到會改變設計的岔路（例如 libchewing 對 NDK 不相容），停下來
回報 lead，不要自行改 ADR。」這正是那種岔路——不是「NDK 完全不相容」，而是
「ADR 假設的建置模型（C99 + CMake）在近 2 年的 upstream 已經被換掉」，兩條
路線影響完全不同：

1. **選一個舊的純 C 版本（v0.5.1）**：符合 ADR 原字面（CMake 直接編），但
   詞典/bug fix 停在 2016 年，且很可能撞上 ADR 自己定義的重評條件；長期看
   是技術債。
2. **改採現行 Rust 版本（建議 v0.12.0，`05ae6bcb9309c466a1b32d69c146bc583be04747`）**：
   拿到 10 年份的維護與詞典更新，但建置管線要換成
   `cargo` + `cargo-ndk`（或等價方案）產生 Android cross-compile 的
   `.so`，CMakeLists.txt 的角色從「編譯」退化成「呼叫 cargo + 打包」，
   ADR-0001 的「Alternatives considered」與部分 Consequences（例如純 CMake
   build 的假設）需要一併修訂，且需要在 CI（GitHub Actions）也裝 Rust
   Android target，這是本 wave 之外的變更面。
3. **Fork 純 C 分支自己維護**：ADR-0001 本來就有寫「若 upstream 停更，Plan B
   是 fork + 自維最小修補」，但那條 Plan B 講的是「upstream 停更」，不是
   「upstream 換語言」；沿用純 C 版本去 fork 一樣繼承 2016 年詞典過舊的問題。

三條路線都改變 ADR-0001 的技術判斷或後果分析，不是 W1-A 這包子代理該自己
拍板的範圍，所以在 vendor 任何原始碼之前先停下回報。

## 尚未進行的動作（刻意不做，等 lead 裁示後才做）

- 未 vendor 任何 libchewing 原始碼（git submodule 或 tarball 都還沒拉）
- 未寫 `decoder-native/cmake/` 下任何 CMakeLists.txt
- 未動 `decoder-native/build.gradle.kts` 的 `externalNativeBuild` 區塊
- 未動 `gradle/libs.versions.toml`
- 未寫 `getDataPath()` Kotlin API 或任何 unit / connectedAndroidTest

## 建議 lead 裁示的問題

1. 要走 Rust 版（建議 v0.12.0）還是接受 v0.5.1 純 C 版？（個人判斷：Rust
   版對「BopomofoBruce 差異化在 UX 不在解碼引擎」這個定位更務實，v0.5.1
   的詞典陳舊風險偏高，但 Rust cross-compile toolchain 的維運成本要一併算進
   solo dev 的時間預算——這是 ADR 層級的取捨，不是我該替 Bruce 決定的。）
2. 若走 Rust 版，ADR-0001 需要開一版修訂（或新開 ADR-0006）記錄建置管線變
   更，這個文件工作要不要也劃進 W1-A，還是留給 lead／另開一包？
3. `platforms;android-35` 目前還沒裝好（只看到 android-34），是否要我等，
   還是回報後暫停在這裡一併交給 lead 處理？

## 版本資訊（供 lead 決策參考）

- 查證時間：2026-08-10 14:5x (UTC+8)
- libchewing 最新 release：v0.12.0，commit
  `05ae6bcb9309c466a1b32d69c146bc583be04747`，2026-04-06 發布
- 最後一個純 C99 版本：v0.5.1，2016-05-18 發布
- 上游主 repo 已搬到 Codeberg（GitHub repo 仍鏡像更新，release note 內有註記）

---

## 第二階段（owner 裁示後續跑，2026-08-10 15:1x–16:1x UTC+8）

Owner 裁示：走 A（v0.12.0 Rust 版），ADR-0006 另開記錄建置管線，不改
ADR-0001 本體。詳細裁示內容見 lead 轉達訊息（本檔不重抄，決策細節見
[ADR-0006](../adr/0006-libchewing-rust-build-pipeline.md)）。

### 交付項目

1. **libchewing vendored**：`decoder-native/cmake/libchewing/`，git submodule，
   `https://github.com/chewing/libchewing.git`，pin 到 tag `v0.12.0`
   = commit `05ae6bcb9309c466a1b32d69c146bc583be04747`（已用
   `git rev-parse HEAD` 核對）。內含的 `data` submodule（
   `https://github.com/chewing/libchewing-data`）pin 到
   `c44e81aef24b06f1509f19e1be54c99812d0c43f`（同樣核對過）。
2. **CMakeLists.txt**（`decoder-native/cmake/CMakeLists.txt`）：用 Corrosion
   （FetchContent pin `v0.6.1` = commit
   `1499b14e4906a2890f5cee1547c8848db261753d`）把 `chewing_capi` crate 編成
   對應 ABI 的 Rust staticlib，再用一個薄 C wrapper
   （`decoder-native/cmake/src/bpmf_wrapper.c`，`#include` 上游的
   `capi/include/chewing.h`）連結成單一 `libbpmf.so`，輸出 DEVPLAN 指定的
   四個 C API（`bpmf_init`/`bpmf_input`/`bpmf_commit`/`bpmf_free`，介面定義
   在 `decoder-native/cmake/include/bpmf.h`）。架構細節、為何選這條路線而
   非 cargo-ndk 直出、為何不改 vendored `chewing_capi` 的 crate-type，見
   [ADR-0006](../adr/0006-libchewing-rust-build-pipeline.md)。
3. `decoder-native/build.gradle.kts`：加了 `ndkVersion`、
   `externalNativeBuild { cmake { path / version } }`、abiFilters 沿用既有
   的 `arm64-v8a` + `armeabi-v7a`。
4. **字典資料**：`decoder-native/scripts/fetch_chewing_data.sh` 下載 upstream
   `chewing/libchewing-data` 的 prebuilt "Generic" release
   （`v2026.3.22`，sha256
   `db8248f7a46be17beda41aedd94e7e846d01e3b2cfa3b45fcfae453acf9c62be`，已
   用 `shasum -a 256` 核對且與 GitHub Releases API 回報的 digest 一致），
   解壓 `word.dat`（280 KB）與 `tsi.dat`（4.3 MB）進
   `decoder-native/src/main/assets/chewing/`（`.gitignore` 排除，不進
   git）。Gradle 掛了一個 `fetchChewingData` task 在 `preBuild`/
   `mergeAssets` 前，`assembleDebug` 從乾淨 checkout 可重現重跑這一步。
   這個版本（`v2026.3.22`）的 commit 已核對與我們 vendor 的 `data`
   submodule commit **完全一致**，不是版本混搭。
5. **`getDataPath()` Kotlin API**：
   `decoder-native/src/main/kotlin/.../ChewingDataPath.kt`。設計成
   `extractChewingData(assets: AssetManager, targetDir: File): File`（不依賴
   `Context`），讓它能用 mockk 純 JVM 單元測試（不用 Robolectric）；
   `getDataPath(context: Context): String` 是給正式呼叫方用的
   convenience wrapper。5 個 unit test 覆蓋：完整解壓、目錄自動建立、
   idempotent（不重複開檔）、零長度殘檔會重新解壓、無 assets 時回傳空
   目錄。
6. **JNI binding**：main sourceset **沒有**（符合「不做 JNI binding」）。
   但 W1-A 自己的驗收標準要求 connectedAndroidTest 能在裝置上呼叫
   `bpmf_init`/`bpmf_input`，這件事離開 Kotlin/JVM 就不可能不經過任何 JNI
   glue。取捨：寫了一個**明確標示 TEST-ONLY** 的最小 JNI 橋接
   （`decoder-native/cmake/src/bpmf_test_jni.c` + androidTest sourceset 的
   `testbridge/BpmfTestBridge.kt`），函式名前綴 `nativeTest*`、package 叫
   `testbridge`，跟 W2-A 之後會寫的正式 `ZhuyinDecoder` JNI binding 在命名
   上不會混淆；W2-A 可以直接刪掉這個檔案换上自己的正式 binding。這個判斷
   沒有先跟 lead 對過，如果 lead 認為連這個都算「做了 JNI binding」，可以
   要求砍掉——但砍掉之後 connectedAndroidTest 這條驗收標準在 W1-A 這包就
   無法達成（會需要改用 native `add_executable` + `adb shell` 執行的方式，
   工程量更大且脫離 Gradle 標準 connectedAndroidTest 流程）。
7. **devlog**（本檔）。

### 環境問題與解法

- **Homebrew rustc/cargo 與 rustup 衝突**：`brew install rustup` 後，
  `/opt/homebrew/bin/cargo`／`rustc` 仍指向 Homebrew 自己的 `rust` formula
  （1.96.1，之前就裝在這台機器上），不是 rustup 管理的工具鏈（1.97.1，含
  `aarch64-linux-android`/`armv7-linux-androideabi` target）。**沒有**動全
  域 Homebrew link 狀態（`brew unlink rust` 會影響這台機器上其他 session/
  專案，超出這包授權範圍）；解法是每次跑 native build 時把
  `/opt/homebrew/opt/rustup/bin` 加到 `PATH` 最前面：
  ```bash
  PATH="/opt/homebrew/opt/rustup/bin:$PATH" ./gradlew :decoder-native:assembleDebug
  ```
  這個 workaround 純粹是本機操作，沒有寫死進任何 checked-in 檔案。完整原理
  見 [ADR-0006](../adr/0006-libchewing-rust-build-pipeline.md) 的「開放
  問題」段。
- `platforms;android-35`：lead 確認已裝好（`ls` 核對過 `platforms/android-35`
  存在），無需再等。

### 驗收標準結果（逐條，附證據）

- ✅ **`./gradlew :decoder-native:assembleDebug` 產生 .so（arm64-v8a、
  armeabi-v7a 都要有）**：`BUILD SUCCESSFUL`。兩個 ABI 都在
  `decoder-native/build/intermediates/stripped_native_libs/debug/.../lib/{arm64-v8a,armeabi-v7a}/libbpmf.so`
  且被打進 `decoder-native-debug.aar` 的 `jni/` 目錄下（`unzip -l` 核對
  過）。`nm -D` 核對過 arm64-v8a 版本確實匯出
  `bpmf_init`/`bpmf_input`/`bpmf_commit`/`bpmf_free`（加上 test-only 的
  `Java_..._BpmfTestBridge_nativeTest*` 四個）。
- ✅ **connectedAndroidTest 在實機（`R6AIB700988748X`）上：bpmf_init 成功、
  bpmf_input 拿到非空候選**：`./gradlew :decoder-native:connectedDebugAndroidTest`
  → `BUILD SUCCESSFUL`，3/3 測試通過（`BpmfNativeSmokeTest`：
  `bpmfInit_withExtractedDictionaryData_succeeds`、
  `bpmfInput_forNiHao_returnsNonEmptyCandidates`、
  `bpmfFree_isSafeToCallOnFreshHandleAndDoesNotCrash`），XML 結果
  `tests="3" failures="0" errors="0"`。
  - **第一次跑有一個誤判、修正後才綠**：第一版測試斷言候選詞要包含「你好」
    整詞，實機上實際拿到 `[好, 郝, 㚼, 㝀]`（都是注音 ㄏㄠˇ 的候選字）。
    這不是 bug，是我對 `chewing_cand_open()` 語意的誤解——它重選的是**游標
    所在音節**的候選（打完 ㄋㄧˇㄏㄠˇ 後游標在最後一個音節），不是整句智
    慧選字後的結果（那個要透過 `chewing_buffer_String()` 拿，`bpmf_input()`
    目前沒有暴露這個）。修正成斷言「非空 + 全部非空字串」，符合 DEVPLAN
    字面的驗收標準（沒有要求「候選要包含指定詞」）。這個語意差異記在
    `bpmf.h`／測試檔註解裡，供 W2-A 設計 `ZhuyinDecoder.input()` 時參考：
    如果 W2-A 要整句候選，要另外走 `chewing_buffer_String()`，`bpmf_input()`
    現在只回傳單一音節的重選候選。
  - **「無 leak」**：`BpmfNativeSmokeTest` 每個測試都在 `finally` 呼叫
    `bpmf_free()`，`bpmf_wrapper.c` 的 `BpmfHandle` 結構會在 `bpmf_free()`
    釋放 `ctx` 與 `last_candidates`；`bpmf_input()` 每次呼叫前會先
    `free(handle->last_candidates)` 再產生新的，避免重複呼叫累積洩漏。
    **老實說明局限**：沒有接 LeakCanary 或 native memory sanitizer
    （ASan/Valgrind）進 connectedAndroidTest，所以「無 leak」目前只是程式
    碼審查層級的保證（每個 malloc/strdup/realloc 都有對應 free 路徑），
    不是工具驗證過的保證。若要工具化驗證，需要另外把 ASan 接進
    `externalNativeBuild`（`-fsanitize=address` + `wrap.sh` 之類），這超出
    這輪的時間範圍，記為 follow-up。
- ✅ **`ktfmtCheck` 綠**：`./gradlew :decoder-native:ktfmtCheck` →
  `BUILD SUCCESSFUL`（第一輪有 1 個檔案 `BpmfTestBridge.kt` 格式不符，
  跑 `ktfmtFormat` 修正後綠）。
- ✅ **`lint` 綠**：`./gradlew :decoder-native:lint` → `BUILD SUCCESSFUL`，
  lint report 顯示 1 warning（`Missing x86_64 ABI support for ChromeOS`，
  在 `build.gradle.kts:18` 的 `abiFilters` 那行——這是刻意的：AGENTS.md
  「不打 x86」，這個 warning 是預期中、不打算處理的雜訊）、0 error。
- **DEVPLAN 額外列的「APK size 增量 < 4 MB」**（lead 這次轉達的驗收清單沒
  重複列這條，但 DEVPLAN §4 W1-A 本文有）：debug 版 `libbpmf.so` 未最佳化
  （arm64-v8a 11.4 MB／armeabi-v7a 7.7 MB，這是 AGP 剝除偵錯符號後的數字，
  Rust 依賴鏈 + debug profile 沒開 LTO 所以偏大）；試跑 `assembleRelease`
  後，release profile（workspace `Cargo.toml` 已開
  `lto = true, opt-level = 3, panic = "abort"`）縮到 arm64-v8a 3.43 MB／
  armeabi-v7a 2.45 MB（剝除後）。兩個都在 4 MB 上下，實際打包進 APK 時只會
  裝一個 ABI（除非用 universal APK），所以正式驗證這條要等 `:app` 真的組
  APK 才能量到準確數字——這件事目前超出 `decoder-native` 一個 module 能
  單獨驗證的範圍，記為 follow-up 讓 lead 或 `:app` 相關工作包接手驗證。

### 已知缺口（誠實列出，非驗收標準內但值得記錄）

- CI（`.github/workflows/`）還沒裝 Rust Android target，目前只在本機驗證
  過；下次碰 CI 設定要補上（見 ADR-0006）。
- `bpmf_input()` 目前只回傳游標所在音節的重選候選，不是整句智慧選字結果；
  W2-A 設計 `ZhuyinDecoder` 時需要知道這個語意（見上）。
- 「無 leak」只有程式碼審查層級保證，沒有 ASan/LeakCanary 工具驗證。
- 字典僅用 `word.dat` + `tsi.dat`（`chewing_new3` 預設的
  `enabled_dicts` 還包含 `chewing.dat`/`chewing-deleted.dat`，這兩個檔案
  不在 upstream 的 prebuilt "Generic" 資料包裡，`bpmf_init()` 已明確只傳
  `"word.dat,tsi.dat"` 避免載入不存在的檔案失敗）。

### Commit

見本次 PR/分支的完整 commit 列表（子代理最終回報裡有列 sha）。

---

## 第三階段（Opus 級驗證者查證後的 7 條 finding 修正輪，2026-08-10）

以下 7 條 finding 皆經獨立驗證者逐條查證屬實才動手修，逐條記錄處理方式與證據。

- **A5〔high〕測試專用 JNI 進了 release .so**：`decoder-native/cmake/CMakeLists.txt`
  改成只在 `CMAKE_BUILD_TYPE STREQUAL "Debug"` 時才把 `bpmf_test_jni.c` 加進
  `add_library(bpmf SHARED ...)` 的來源清單（AGP 對 debug variant 傳
  `CMAKE_BUILD_TYPE=Debug`，release variant 傳 `RelWithDebInfo`，已用實跑
  `assembleDebug`/`assembleRelease` 的 log 核對）。用 NDK 的 `llvm-nm -D` 對兩個 ABI 的
  stripped release `.so` 核對：`bpmf_commit`/`bpmf_free`/`bpmf_init`/`bpmf_input` 四個公開 API
  在，`nativeTest*` 四個符號**完全不在**；debug `.so` 兩者都在（見下方指令輸出，本輪跑過兩次，
  格式化前後都核對一致）。另外補了 `bpmf_test_jni.c:30`（`nativeTestInit` 的
  `GetStringUTFChars`）與原 `:40`（`nativeTestInput` 的）兩處 NULL 檢查：`jstring` 本身為
  NULL、以及 `GetStringUTFChars` 因 OOM 回傳 NULL 兩種情況都提早 return，不再把 NULL
  指標往下傳。`bpmf_test_jni.c` 檔頭與 `BpmfTestBridge.kt` 的 KDoc 都補了「這四個符號在
  release .so 裡缺席，已用 nm -D 驗證」的明文說明，取代原本含糊的 TEST-ONLY 措辭。

  ```
  === RELEASE arm64-v8a ===
  bpmf_commit / bpmf_free / bpmf_init / bpmf_input   （僅此四個，nativeTest* 不存在）
  === RELEASE armeabi-v7a ===
  bpmf_commit / bpmf_free / bpmf_init / bpmf_input   （同上）
  === DEBUG arm64-v8a ===
  bpmf_commit / bpmf_free / bpmf_init / bpmf_input
  Java_..._BpmfTestBridge_nativeTestCommit/nativeTestFree/nativeTestInit/nativeTestInput
  ```

- **A6〔high〕字典解壓非原子、壞檔永不自癒**：`ChewingDataPath.kt` 改成寫入
  `<name>.tmp` 再 `File.renameTo` 原子換名到最終檔名；skip 判斷只看最終檔名是否存在
  （不再看 `length() > 0`——原檢查對「非零長度截斷檔」完全無效，這正是 finding 描述的
  bug）；rename 失敗會刪 tmp 並拋 `IOException`。KDoc 的「Idempotent: 非零長度視為已完成」
  改成準確描述新的原子寫入不變量。**測試證明會失敗**：先把
  `ChewingDataPathTest.kt` 新增的「中斷複製不留下最終檔名的截斷檔」測試跑在**修正前**的舊版
  `extractChewingData`（`git show HEAD:...` 取出舊版蓋掉，跑
  `./gradlew :decoder-native:testDebugUnitTest --rerun`）——RED，1 個測試失敗、斷言最終檔案
  不該存在但確實存在；換回修正後版本，跑 `--rerun` 重測全 GREEN。另外把舊測試「零長度殘檔會
  重新解壓」換成「已存在最終檔名時完全信任、不重新讀 assets」，因為新的原子寫入不變量下，
  「最終檔名存在」本身就是「已完整寫入」的證明，舊測試斷言的情境（半殘檔案卡在最終檔名）
  在新程式碼下不該再自然發生。

- **A7〔medium〕解壓無同步**：`extractChewingData` 整段包進
  `synchronized(extractionLock)`（檔案層級的 `private val extractionLock = Any()`），
  防兩條 thread 同時通過「最終檔名不存在」判斷後交錯寫入同一個 `.tmp`。A6 的 tmp+rename
  緩解了「半殘檔留在最終名」，但 rename 前的寫入順序本身仍需要序列化，這條鎖補的正是這段。

- **A2〔medium〕userpath 傳了目錄，使用者字典其實從未啟用**：查證 vendored
  `capi/src/io.rs` 的 `chewing_new3()`（"All parameters will be default if set to NULL"，
  userpath 為 NULL 時轉成 `Option::None`）與 `src/editor/mod.rs` 的
  `Editor::chewing()`（`userpath` 為 `None` 時 `custom_userpath` 為 false，
  完全跳過 `user_dict_mgr.userphrase_path()`/`.init()`，`user_dict` 直接是 `None`，是一個
  乾淨、有明確語意的 no-op，不是靜默失敗）——確認 `chewing_new3` 接受 NULL userpath 且行為
  可預期，選 **(b)**：`bpmf_wrapper.c` 的 `chewing_new3()` 呼叫把 userpath 從
  `data_path` 改成 `NULL`，並在呼叫點與 `bpmf.h` 都寫清楚「W1-A 刻意不啟用 libchewing 內建
  使用者字典，個人字典由 W2-A 的 Room 負責」。同步修正 `bpmf.h:39-41` 「用作 userpath 所以
  必須可寫」的失準說明，改成準確描述 `data_path` 只需可讀（syspath-only）。

- **A1〔medium〕C header 沒有執行緒契約**：`bpmf.h` 補了一段「Thread-safety
  contract」，交叉引用 `ZhuyinDecoder.kt` 的既有措辭（不保證 thread-safe、呼叫方需在同一
  dispatcher 序列化），並明寫「不要在 BpmfHandle 內加 mutex 補償」——照驗證者的意見，沒有動
  `bpmf_wrapper.c` 的 `BpmfHandle` 結構本身，純文件修正。

- **A3〔medium〕fetch 腳本 idempotency 名不副實**：`fetch_chewing_data.sh` 的
  fast-path 判斷從單純 `[[ -f ... ]]` 改成對已存在的 `word.dat`/`tsi.dat` 各自算
  `shasum -a 256` 比對已知期望值（用當下 repo 裡已存在的檔案跑 `shasum -a 256` 取得，記在腳本
  常數 `EXPECTED_WORD_DAT_SHA256`/`EXPECTED_TSI_DAT_SHA256`）；下載+解壓後的 `cp` 也改成
  「先寫 `.tmp` 再 `mv`」的同資料夾原子換名，不再直接 `cp` 到最終檔名。註解同步改成準確描述
  這個行為，不再宣稱舊版沒做到的「大小正確才跳過」。

- **A4〔medium〕preBuild 綁死網路**：`decoder-native/build.gradle.kts` 拿掉
  `tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchChewingData) }`
  這行，只留（且擴充）「會實際讀 `src/main/assets` 的 task」依賴：`merge*Assets`/
  `package*Assets`（AGP 資源合併管線分兩段，`merge` 產生中介檔、`package` 又直接讀一次
  source set，兩段都要掛，只掛 `merge` 會被 Gradle 的 task 輸入驗證擋下）與
  `lint*`/`Lint*`（lint 的 model builder 直接讀 source set 的 assets 目錄，不經過資源合併
  管線）。**已驗證**：`./gradlew :decoder-native:testDebugUnitTest --dry-run` 印出的完整
  task graph裡沒有 `fetchChewingData`（`grep -c fetchChewingData` 回 0），且這個 dry-run
  結果在最終版 `build.gradle.kts` 上重新核對過一次；`assembleDebug`/`assembleRelease`/
  `lint`/`connectedAndroidTest` 四項都仍會觸發 `fetchChewingData`（因為 assets 已存在於
  worktree，實際不會真的打網路，只用 log 確認 task 有出現在圖上）並全部成功。

### 修正輪四項 gradle 指令 + 實機測試結果

- `:decoder-native:assembleDebug` → `BUILD SUCCESSFUL`（兩 ABI `.so` 皆產出）
- `:decoder-native:testDebugUnitTest` → `BUILD SUCCESSFUL`（含新增/修改後的
  `ChewingDataPathTest` 全數 6 個測試綠）
- `:decoder-native:ktfmtCheck` → `BUILD SUCCESSFUL`（第一輪 3 個檔案格式不符，跑
  `ktfmtFormat` 修正後綠）
- `:decoder-native:lint` → `BUILD SUCCESSFUL`
- 四項合併在同一次 `./gradlew` 呼叫裡也核對過一次全綠（排除「個別跑綠、合跑因 task 順序
  觸發 A4 的 Gradle 驗證錯誤」的可能性——事實上第一次合跑真的踩到這個錯誤，`lintAnalyzeDebug`
  也直接讀 assets 卻沒宣告依賴，這才發現只掛 `merge*Assets` 不夠，追加了 `lint*` 這段）
- `:decoder-native:connectedAndroidTest`（實機 `R6AIB700988748X`）→ `BUILD SUCCESSFUL`，
  `BpmfNativeSmokeTest` 3/3 綠（`bpmfInit_withExtractedDictionaryData_succeeds`、
  `bpmfInput_forNiHao_returnsNonEmptyCandidates`、
  `bpmfFree_isSafeToCallOnFreshHandleAndDoesNotCrash`），修正輪跑了兩次（CMake 改動後一次、
  build.gradle.kts 追加 lint 依賴後再一次）確認沒改壞。

### 對 finding 本身的補充意見

- 沒有發現 7 條 finding 裡有誤判或需要 push back 的地方；A2 要求「選 (b) 前先查證
  `chewing_new3` 是否接受 NULL userpath」這條查證確實非顯而易見（需要讀
  `capi/src/io.rs` **和** `src/editor/mod.rs` 兩層才能確認 NULL userpath 是乾淨 no-op
  而非某種降級到 default 路徑的行為），花了額外時間但查證結果明確支持 (b)。
- A6 舊測試「零長度殘檔會重新解壓」與 A6 修正後的新不變量（最終檔名存在即信任）在語意上
  互斥，這點 finding 本身沒有明講但邏輯上必然如此；已在上面 A6 段落記錄取捨，供之後回頭
  查證時參考，不是我自己新發現的額外缺陷。

---

## 第四階段（獨立驗證者第二輪查證的 4 條 finding 修正輪，2026-08-10～11，commit `f01fce3`）

以下 4 條 finding（A9–A12）皆經獨立驗證者查證屬實（含查 AOSP 原始碼、`--dry-run` 實測）才動手修，逐條記錄處理方式與證據。

- **A9〔medium〕ADR-0006 自相矛盾**：Decision 本體原文「Gradle 把這個 script 掛在
  `preBuild`/`mergeAssets` 前」與「（2026-08-10 修正輪更新）」追記段落「這個 fetch task
  **不**掛在 `preBuild`」直接矛盾。已改寫 Decision 本體第一段，直接敘述最終狀態（只掛在真正
  需要字典內容的 task 上），並把追記段落的標題從「更新」改成「詳細理由」，內容同步改成描述
  A12 完成後的最終形態（`ensureChewingDataDir` vs `fetchChewingData` 兩個 task 的分工），
  消除了原本的矛盾。

- **A10〔medium〕CMake 閘門綁的是 AGP 的 isDebuggable fallback**：查證 AOSP
  `CreateCxxVariantModel.kt` 確認 AGP 先用變體名稱字串比對（含 debug/release/
  relwithdebinfo/minsizerel 關鍵字），只有名稱不含這些關鍵字時才 fallback 到
  `if (isDebuggable) "Debug" else "RelWithDebInfo"`——採首選改法：
  `decoder-native/build.gradle.kts` 的 `buildTypes { debug {} / release {} }` 各自用
  `externalNativeBuild.cmake.arguments` 顯式傳 `-DBPMF_BUILD_TEST_BRIDGE=ON`/`OFF`；
  `cmake/CMakeLists.txt` 的閘門條件從 `CMAKE_BUILD_TYPE STREQUAL "Debug"` 改成
  `if(BPMF_BUILD_TEST_BRIDGE)`，不再依賴 AGP 的隱含變體名稱推導。用 NDK 的
  `llvm-nm -D --defined-only` 對 release 產物重新核對（兩個 ABI 皆只有 4 個 `bpmf_*`、
  0 個 `Java_*`/`nativeTest*`；debug 兩個 ABI 皆有 4 個 `Java_..._nativeTest*`），輸出見下方。

- **A11〔medium〕KDoc 語氣超出鎖的保證範圍**：`ChewingDataPath.kt` 的
  `synchronized(extractionLock)` 只擋同 process，跨 process 兩個寫入者仍可能交錯寫壞
  `.tmp` 後各自原子 rename。KDoc 補了一段明寫「保證僅限單一 process」，並記錄若 IME 與其他
  元件日後分成不同 process，需改用 `FileChannel.lock()` 或約定單一元件觸發（不現在就實作
  檔案鎖，屬過度設計）。已查證目前 7 個模組的 AndroidManifest 皆未宣告 `android:process`。

- **A12〔medium〕lint 被綁上網路**：原本 `it.name.contains("Lint", ignoreCase = true)` 讓
  `lintAnalyzeDebugUnitTest`/`lintAnalyzeDebugAndroidTest` 都依賴 `fetchChewingData`，
  使純靜態分析的 `:lint` 永遠需要連網。改法：拆成兩個 task——`ensureChewingDataDir`（純
  `mkdir`、不連網，是 `src/main/assets/chewing` 目錄本身唯一的擁有者）與 `fetchChewingData`
  （下載＋sha256 校驗，`outputs.file` 宣告 `word.dat`/`tsi.dat` 兩個具體檔案，不再宣告整個
  目錄，避免與 `ensureChewingDataDir` 的 `outputs.dir` 重疊）。`merge*Assets`/
  `package*Assets`/`lint*` 只依附 `ensureChewingDataDir`（+ `mustRunAfter(fetchChewingData)`
  純排序，不拉依賴）；只有 `assembleDebug`/`assembleRelease` 直接依附 `fetchChewingData`。
  過程中踩了兩個坑，都已修正並重新驗證：(1) 若把「建目錄」task 命名為
  `ensureChewingAssetsDir`，名字裡含 "Assets" 會被自己的 `contains("Assets")` matcher
  抓到，形成循環依賴——改名 `ensureChewingDataDir` 避開；(2) `lintAnalyzeDebugUnitTest`/
  `lintAnalyzeDebugAndroidTest` 是 AGP 內建就依賴 `package*Assets`（非本次改動造成），若
  `package*Assets` 依附 `fetchChewingData`，這兩個 lint 子 task 會透過它間接連網——改成
  `package*Assets` 也只依附 `ensureChewingDataDir`，`fetchChewingData` 改為只掛在
  `assembleDebug`/`assembleRelease` 上，並用 `mustRunAfter` 讓「若兩者都被排進同一次
  Gradle 呼叫」時保持正確順序（例如 `./gradlew lint assembleDebug` 合跑一次也核對過綠）。
  `connected*`/`install*` 未額外明列——已用 `--dry-run` 確認 `assembleDebug`
  依賴鏈已涵蓋，`connectedAndroidTest` 實跑亦綠（見下方）。

### A10/A12 驗證指令與輸出

`--dry-run` 三項核對（修正後）：

```
$ ./gradlew :decoder-native:lint --dry-run | grep -iE "fetchChewingData|ensureChewingDataDir"
:decoder-native:ensureChewingDataDir SKIPPED
（fetchChewingData 不在圖上）

$ ./gradlew :decoder-native:testDebugUnitTest --dry-run | grep -iE "fetchChewingData|ensureChewingDataDir"
（都不在圖上）

$ ./gradlew :decoder-native:assembleDebug --dry-run | grep -iE "fetchChewingData|ensureChewingDataDir"
:decoder-native:ensureChewingDataDir SKIPPED
:decoder-native:fetchChewingData SKIPPED
```

另外針對 A12 提到的兩個具名 lint 子 task 也各自 `--dry-run` 核對過，`fetchChewingData`
均不在圖上：`lintAnalyzeDebugUnitTest`、`lintAnalyzeDebugAndroidTest`。

`llvm-nm -D --defined-only` 對 release stripped `.so` 核對（A10）：

```
arm64-v8a release: bpmf_commit / bpmf_free / bpmf_init / bpmf_input（4 個，僅此 4 個）
armeabi-v7a release: bpmf_commit / bpmf_free / bpmf_init / bpmf_input（4 個，僅此 4 個）
```

`nativeTest*`/`Java_*` 在兩個 release ABI 上皆為 0；debug arm64-v8a 核對出 4 個：
`Java_com_bopomofobruce_decoder_nativ_testbridge_BpmfTestBridge_nativeTestCommit`、
`nativeTestFree`、`nativeTestInit`、`nativeTestInput`。

### 收尾指令結果

- `:decoder-native:assembleDebug` → `BUILD SUCCESSFUL`
- `:decoder-native:assembleRelease` → `BUILD SUCCESSFUL`
- `:decoder-native:testDebugUnitTest` → `BUILD SUCCESSFUL`
- `:decoder-native:ktfmtCheck` → `BUILD SUCCESSFUL`
- `:decoder-native:lint` → `BUILD SUCCESSFUL`
- `:decoder-native:lint :decoder-native:assembleDebug`（合跑，驗證 A12 排序修正）→
  `BUILD SUCCESSFUL`
- 實機 `R6AIB700988748X`（ASUS_AI2302, API 15）`:decoder-native:connectedAndroidTest` →
  `BUILD SUCCESSFUL`，`BpmfNativeSmokeTest` 3/3 綠，與第三階段的結果一致，沒有改壞。

### 對 finding 本身的補充意見

- 4 條 finding 皆屬實，沒有需要 push back 之處。
- A12 修正過程中發現的兩個坑（task 命名撞上自己的 matcher、AGP 內建的
  `lintAnalyzeDebug{UnitTest,AndroidTest}→package*Assets` 依賴）finding 本身沒有點名，
  是動手驗證時才浮現，記錄在上面供之後回頭查證參考。
