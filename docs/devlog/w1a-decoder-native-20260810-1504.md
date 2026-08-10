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
