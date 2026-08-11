# W1-A devlog — `:decoder-native` libchewing 編譯 spike

- 分支：`feat/w1a-decoder-native`
- 期間：2026-08-10 14:5x – 16:1x (UTC+8)（分兩段：14:5x–15:0x 調查+停工回報，
  15:1x–16:1x owner 裁示後續跑）
- 狀態：**驗收標準全數通過（實機 connectedAndroidTest 綠、assembleDebug 兩
  ABI 皆產出 .so、ktfmtCheck/lint 綠）。細節與已知缺口見下方「第二階段」。
  第五階段（2026-08-11）修正下游 `:app` 拿不到字典等 5 條 finding，其中 E1 把
  `lint` 需要連網從「已解決」降級為「已知取捨」，見該節。
  第七階段（2026-08-11）修正 K1–K7 共 7 條 finding：K1（LGPL-2.1 靜態連結授權缺口）
  owner 裁示只記案、登記為 W4-D 上架 blocker；K2/K4/K7 為文件修正；K3 刪除一條
  無鑑別力測試；K5 補上字典版本化快取（含新測試證明會紅）；K6 實際修 CI
  submodule+Rust target。見該節。**

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
- **（2026-08-11 第七階段新增，K1）LGPL-2.1 靜態連結尚未有合規履行**：ADR-0006
  把建置管線換成 Corrosion，把 `chewing_capi` 整份靜態編進 `libbpmf.so`，這與
  ADR-0001 原本核可的「動態連結」前提不同，本輪只補上文件誠實記錄（見
  ADR-0001/ADR-0006），**未**改連結方式、**未**補 LGPL §6(a) 履行文件（wrapper
  原始碼＋可 relink 物件檔，或依賴整個 repo 公開）。**登記為 W4-D（上架）的
  blocker**：上架前必須先解決（改回動態連結，或補齊 LGPL 履行）。
- **（2026-08-11 第七階段新增，K4）`bpmf_commit()` 的效果目前在公開 API 上不可觀測**：
  `bpmf_input()` 每次呼叫開頭都會 `chewing_Reset()`，把 composition 與 commit
  buffer 一併清空，而這 4 個 API 沒有任何讀出 commit buffer 的出口——所以呼叫
  `bpmf_commit()` 之後，唯一能看到「被 commit 了什麼」的方式是呼叫端自己在
  Kotlin/JNI 側累積 `bpmf_input()` 已經回傳過的候選字串，不是靠這個 C API 讀回來。
  W2-A 若需要 libchewing 自己跨呼叫累積組句（例如多字詞的智慧選字），需要另外加
  API（例如導出 `chewing_buffer_String()`），超出 W1-A 範圍。見 `bpmf.h` 的
  `bpmf_commit()` KDoc。

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
  `connected*`/`install*` 額外明列（不假設從 `assembleDebug` 繼承），並用 `--dry-run` 核對過
  `fetchChewingData` 在它們的 task graph 裡。

  **（2026-08-11 第五階段更正，見下方 E1）**：這裡當時只驗證了
  `:decoder-native:assembleDebug`/`assembleRelease`/`connectedAndroidTest` 這幾個
  **decoder-native 自己的** task 本身的 `--dry-run`，沒有驗證下游 `:app`（經 `:decoder`）消費
  `:decoder-native` 時實際排進 task graph 的是 `packageDebugAssets` 這類 artifact task，根本不
  會經過 `:decoder-native:assembleDebug`/`assembleRelease`——`--dry-run` 顯示乾淨 checkout 組出
  的 `:app:assembleDebug`/`:app:assembleRelease` 依賴鏈裡都**沒有** `fetchChewingData`，
  只會 mkdir 出一個空的 `assets/chewing/`。原句「`connectedAndroidTest` 實跑亦綠」也已在
  Opus 級驗證者複查時指出不具鑑別力：`word.dat`/`tsi.dat` 當時早就留在本機磁碟上，「跑得動、
  測試綠」不代表 fetch 真的被排進圖裡，兩者被本輪誤當同一件事。詳見下方「第五階段」E1。

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

---

## 第五階段（Opus 級驗證者查證後的 5 條 finding 修正輪，2026-08-11，分支
`feat/w1a-decoder-native`）

以下 5 條 finding（E1–E5），E1 由 lead 親自實測確認，其餘皆逐條動手查證/重現後才修。

- **E1〔high〕下游 `:app` 拿不到字典**：`:decoder-native` 是 library module，`:app`（經
  `:decoder`）消費它時，Gradle 排進 task graph 的是 `packageDebugAssets` 這類 artifact
  task，**不會**經過 `:decoder-native:assembleDebug`。第四階段（A12）把 `fetchChewingData`
  從 `package*Assets`/`lint*` 移開、只留在 `assembleDebug`/`assembleRelease`/`connected*`/
  `install*` 上，副作用是下游 `:app` build 再也拿不到真字典——只掛回 `assemble*` 沒用，因為
  `:app` 根本不跑 `:decoder-native` 自己的 `assembleDebug`。本機沒察覺，純粹因為
  `word.dat`/`tsi.dat` 早就留在磁碟上。

  **選擇的作法**：(a)（finding 建議的三選項之一）——把 `fetchChewingData` 掛回
  `package*Assets`/`Lint*`（連 `lintAnalyzeDebug`/`generateDebugLintReportModel` 這兩個不含
  `"Assets"` 字樣、直接讀 assets 目錄因而被 Gradle implicit-dependency 驗證擋下的 lint
  子 task 也一併掛上），接受 `:lint` 因此又需要連網。沒有選 (b)（AGP
  `androidComponents.onVariants` 的 `addGeneratedSourceDirectory`）：這個 API 讓字典成為
  variant 的 generated asset source，一樣會被併進 `mergeDebugAssets` → `packageDebugAssets`
  這條鏈（打包一定要有真內容），所以並不能避開「lint 也連到 package\*Assets」這個 AGP 內建耦合，
  改動幅度卻大得多（要把 `outputs.file` 改到 `layout.buildDirectory` 底下、重寫整個
  fetch task 的路徑假設），對這個具體問題沒有額外好處，故未採用。優先序照 finding 明講：APK 正確性
  優先於 lint 可離線執行，已誠實記進 ADR-0006（把 A12「已解決」降級為「已知取捨」）與本檔第四階段
  A12 段落的更正註記（見上方）。

  驗收證據（五項 `--dry-run`，皆在本輪修正**之後**實測，逐項核對）：

  ```
  $ ./gradlew :app:assembleDebug --dry-run | grep -i fetchChewingData
  :decoder-native:fetchChewingData SKIPPED

  $ ./gradlew :app:assembleRelease --dry-run | grep -i fetchChewingData
  :decoder-native:fetchChewingData SKIPPED

  $ ./gradlew :decoder-native:connectedAndroidTest --dry-run | grep -i fetchChewingData
  :decoder-native:fetchChewingData SKIPPED

  $ ./gradlew :decoder-native:testDebugUnitTest --dry-run | grep -i fetchChewingData
  （空——純 JVM 單元測試維持離線可跑，符合預期）

  $ ./gradlew :decoder-native:lint --dry-run | grep -i fetchChewingData
  :decoder-native:fetchChewingData SKIPPED
  ```

  `:lint` 最後一項**非空**是刻意接受的取捨，不是遺漏——已在 ADR-0006 明文記錄。

  過程中踩了一個新坑：把 `Lint*`-matching 區塊直接刪掉（誤以為 `package*Assets` 依賴
  `fetchChewingData` 後，`lintAnalyzeDebugUnitTest`/`lintAnalyzeDebugAndroidTest` 會透過
  `package*Assets` 間接連到）忽略了 `lintAnalyzeDebug`（無 UnitTest/AndroidTest 後綴）與
  `generateDebugLintReportModel` 這兩個 task 名稱不含 `"Assets"`、直接讀
  `src/main/assets` 卻完全沒有任何宣告依賴——`./gradlew :decoder-native:lint` 因此被 Gradle
  的 implicit-dependency 驗證擋下（`generateDebugLintReportModel`/`lintAnalyzeDebug` 各噴兩個
  "uses this output ... without declaring an explicit or implicit dependency" 錯誤）。修法是
  用 `it.name.contains("Lint", ignoreCase = true)`（注意 `ignoreCase`——`lintAnalyzeDebug`
  的 `l` 是小寫，第一次漏寫 `ignoreCase = true` 導致 matcher 完全沒抓到任何 task，同一個
  validation 錯誤又復現了一次）把這兩個 task 也直接掛上 `fetchChewingData`。

- **E2〔high〕一聲（陰平）靜默回傳「上一個音節」的候選**：`bpmf_wrapper.c` 原本把 ASCII 空白
  當純分隔符 `continue`，從不餵給 `chewing_handle_Default`。查證 vendored
  `src/editor/zhuyin_layout/standard.rs`（`SyllableEditor::key_press`）確認
  `KEY_SPACE => Bopomofo::TONE1`：syllable 非空時回傳 `KeyBehavior::Commit`（不更新聲調，
  等於用隱含的一聲提交當前音節）；syllable 空時回傳 `KeyBehavior::KeyError`（`standard.rs`
  自己的 `space` unit test 也斷言這點）。finding 指出的因果成立。

  第一版修法（naive）——不分狀態一律把空白餵給 `chewing_handle_Default`——**在裝置上實測後發現
  不安全**，比 finding 本身的建議更深一層：`standard.rs` 只是最底層的 `SyllableEditor`；空白鍵
  在整個 Editor 狀態機（`src/editor/mod.rs`）裡還有另一條路徑——一旦音節已透過顯式聲調鍵提交、
  Editor 回到 `Entering` 狀態，`Entering::next()` 會把 `SYM_SPACE` 導去
  `start_selecting_or_input_space()`，只要組字區（`shared.com`，不是單一音節緩衝區）非空且游標
  下有可選字符，就會把**整個 Editor**切換進 `Selecting`（候選視窗）狀態——跟 `bpmf_input()`
  自己稍後呼叫的 `chewing_cand_open()` 衝突（等於開兩次候選視窗），實測導致
  `bpmf_input(handle, "ㄋㄧˇㄏㄠˇ")`（純既有的、不涉一聲的案例）從原本正確的
  `[好, 郝, 㚼, 㝀]` 退化成 `[]`。修正：改用 `chewing_zuin_Check()`（chewing.h 明文：
  「回傳 0 代表 true〔有待決音節〕、1 代表 false」）閘門——只在真的有待決音節（真的還在
  `EnteringSyllable` composing 狀態）時才把空白轉發給 `chewing_handle_Default`，其餘一律不轉發
  （純分隔符 no-op）。裝置上重新核對 5 組輸入，結果與預期一致：

  ```
  input=[ㄍㄨㄥ]         -> [工, 公, 功, 供, 攻, ...]        （純一聲，靠結尾自動 flush 提交）
  input=[ㄋㄧˇㄍㄨㄥ]    -> [工, 公, 功, 供, 攻, ...]        （前一音節有顯式聲調、正常 commit；
                                                              第二音節一聲，未混進「你/妳」）
  input=[ㄋㄧˇㄏㄠˇ]     -> [好, 郝, 㚼, 㝀]                 （既有案例維持正確，未被閘門修正改壞）
  input=[abc]            -> []                                （unmapped，fail closed）
  ```

  （`input=[ㄍㄨㄥㄏㄠˇ]`〔兩音節之間無任何分隔符/聲調鍵〕回傳 `[]`——查證後確認這不是本輪
  修正造成的迴歸，而是真實 DaChen 輸入法本來就有的行為：兩個初聲/介音/韻母鍵在沒有聲調鍵或空白
  分隔的情況下會直接覆寫同一個待決音節的對應槽位，不會自動斷字，這點在修正前後皆然，只是刻意排除
  在測試設計外。）

  `bpmf_input()` 結尾也補了一次同樣受 `chewing_zuin_Check()` 閘門保護的 flush，讓字串結尾沒有
  分隔符的一聲音節（例如純 `"ㄍㄨㄥ"`）也能被提交，不強制呼叫端加尾隨空白。`bpmf.h`
  同步改寫，移除「一聲不支援」的舊敘述，改成準確描述現行行為與 `chewing_zuin_Check()` 閘門的
  安全保證。

  **測試證明會紅（實機 `R6AIB700988748X`）**：把 `bpmf_forward_space_if_pending()`
  的呼叫點還原成單純 `continue`（不轉發空白）並跑
  `bpmfInput_forFirstToneSyllable_commitsPendingSyllableNotPreviousOne`：

  ```
  FAILED: expected 工 (gong1) among the candidates, got
  [你, 妳, 擬, 禰, 儗, 旎, 昵, 坭, 柅, 薿, 檷, 抳, 苨, 馜, 隬, 譺, 尼, 泥, ...]
  ```

  換回修正版重測，5 個測試（含此測試）全綠。

- **E3〔medium〕`bpmf.h` 的 NULL 契約與實作不符**：`bpmf_wrapper.c:bpmf_input()` 開頭
  `*candidates_out = NULL`，NULL handle/zhuyin/candidates_out 與 `strdup("")` OOM 兩條路徑
  都提早 `return 0` 卻從未把 `*candidates_out` 改回 `""`，與 `bpmf.h`「回傳 0 時
  `*candidates_out == ""`」的承諾不符。選 (a)：新增一個檔案作用域的
  `static const char kEmptyCandidates[] = "";`，**不**存進 `handle->last_candidates`（避免
  下次 `bpmf_input()`/`bpmf_free()` 對這個非堆積指標呼叫 `free()`），NULL 前置檢查與 OOM
  分支都指向它。`bpmf_test_jni.c` 裡原本防禦性的 `joined != NULL` 檢查加了註解，說明現在這是
  defense-in-depth（契約已保證非 NULL），不是繞過某個真實 NULL 路徑的權宜之計。

- **E4〔medium〕devlog 與 ADR/程式碼互相矛盾**：第四階段 A12 段原文「`connectedAndroidTest`
  實跑亦綠（見下方）」與 ADR-0006 記載的「實測顯示只掛 assemble\* 時 connectedAndroidTest
  沒有 fetch」直接互斥，且「實跑亦綠」在字典早已在磁碟上時完全不具鑑別力。已直接改寫上方第四階段
  A12 段落最後兩句，移除這個不具鑑別力的證據，改成如實描述「當時只驗證了 decoder-native 自己的
  task、沒驗證下游 `:app` 實際走的路徑」，並交叉引用本節 E1。ADR-0006 對應段落也同步加了
  「第五階段更正」註記，兩份文件現在對「最終狀態」的敘述一致（`package*Assets`/`Lint*` 依附
  `fetchChewingData`，`lint` 需要連網是刻意取捨）。

- **E5〔medium〕實機測試斷言對本包最高風險項零鑑別力**：`BpmfNativeSmokeTest.kt` 原本只斷言
  「非空 + 每個候選非空白」——E2 那種「靜默回傳錯誤音節的候選」完全不會被抓到，因為錯誤音節的
  候選一樣非空白。改法：
  1. 既有 `bpmfInput_forNiHao_returnsNonEmptyCandidates` 追加斷言 `candidates.contains("好")`
     （裝置實測固定輸出），並在註解說明「非空+非空白」為何不具鑑別力。
  2. 新增 `bpmfInput_forFirstToneSyllable_commitsPendingSyllableNotPreviousOne`（E2 的
     regression test，見上方）。
  3. 新增 `bpmfInput_forUnmappedCharacters_failsClosedWithNoCandidates`（fail-closed 的
     negative case）——**第一版用純 `"abc"` 當輸入，裝置實測發現這個案例本身不具鑑別力**：全
     ASCII 輸入從頭到尾一個鍵都沒按進 `chewing_handle_Default`，composition 全程是空的，就算
     把 `bopomofo_key_for() < 0` 的 fail-closed 分支整段改成 `continue`（不提早 return），
     這條測試依然回傳 0 個候選、照樣綠燈——驗證不出任何東西。改成混合輸入
     `"ㄏㄠˇx"`（先組出真實有候選的 hao3，再接一個 unmapped 字元），這樣 fail-closed 分支必須
     主動丟棄已經組好的候選才能通過。

  **測試證明會紅（實機）**：
  - `candidates.contains("好")` 改成斷言一個不存在的假字串 → `bpmfInput_forNiHao_...`
    立刻紅（`AssertionError: expected 工 ... got [你, 妳, 擬, ...]`已在 E2 段展示同機制；
    此處另外對「好」assertion 本身也單獨跑過一次紅/綠，行為一致）。
  - 把 `bopomofo_key_for(codepoint) < 0` 的 fail-closed 分支改成 `continue`（不 return）→
    `bpmfInput_forUnmappedCharacters_failsClosedWithNoCandidates` 紅：
    ```
    FAILED: expected fail-closed (zero candidates) once an unmapped character appears,
    even with an already-valid hao3 prefix; got [好, 郝, 㚼, 㝀]
    expected:<0> but was:<4>
    ```
  - 兩處都已還原回修正後版本並重新確認 5/5 綠（見下方收尾指令結果）。

### 收尾指令結果

- `:decoder-native:assembleDebug` → `BUILD SUCCESSFUL`
- `:decoder-native:assembleRelease` → `BUILD SUCCESSFUL`
- `:decoder-native:testDebugUnitTest` → `BUILD SUCCESSFUL`
- `:decoder-native:ktfmtCheck` → `BUILD SUCCESSFUL`
- `:decoder-native:lint` → `BUILD SUCCESSFUL`（需連網，見 E1 取捨）
- 實機 `R6AIB700988748X`（ASUS_AI2302, API 15）`:decoder-native:connectedAndroidTest` →
  `BUILD SUCCESSFUL`，`BpmfNativeSmokeTest` **5/5 綠**（新增 2 個：E2 的一聲 regression test、
  E5 的 fail-closed negative test；原 3 個維持綠，其中 `bpmfInput_forNiHao_...`
  追加了 E5 的「好」斷言）。

### 對 finding 本身的補充意見

- E1、E3、E4、E5 皆屬實，沒有需要 push back 之處。
- E2 的因果查證（`standard.rs` 的 `KEY_SPACE => Bopomofo::TONE1`）finding 本身完全正確，但
  finding 建議的「把空白原樣餵給 `chewing_handle_Default`」若不加任何閘門，會在裝置上引入一個
  finding 沒有預見的新迴歸（`Entering::next()` 的 `start_selecting_or_input_space()` 把整個
  Editor 切進 Selecting 狀態、與 `bpmf_input()` 自己的 `chewing_cand_open()` 衝突）。這不是
  finding 判斷錯誤——finding 本來就要求「你自行評估、若不可行則 fail closed」——只是記錄下這個
  額外查證步驟（讀 `editor/mod.rs` 的 `Entering`/`EnteringSyllable` 兩個 state 的完整
  `next()` 實作，不只是 `standard.rs` 一層）供之後回頭查證參考。
- E5 建議的 negative case 範例（`"abc"`）本身經查證不具鑑別力（見上方 E5 段），已換成
  `"ㄏㄠˇx"` 這種「先有效、後 unmapped」的混合輸入；這不影響 finding 判斷本身（fail-closed
  斷言確實需要，且原本完全缺這類測試），只是 finding 給的範例字串需要替換才能真正驗證到目標。

## 第六階段（round-5 審查，2026-08-11）

- **[high] E3 的 `kEmptyCandidates` fallback 漏了一條路徑。** 上一輪把 `bpmf_input()` 開頭的
  NULL 前置檢查與結尾 `strdup("")` 的 OOM 分支都改指向 static 空字串，但**迴圈中段「未對照到
  表格字元」的 fail-closed 分支**仍是舊寫法（`handle->last_candidates = strdup(""); *candidates_out
  = handle->last_candidates;`）。那個 `strdup("")` 一旦 OOM 就會讓 `*candidates_out` 變成 NULL，
  違反 `bpmf.h` 與本檔案開頭註解重申的「回傳 0 時 `*candidates_out` 必為 `""`、絕不是 NULL」
  契約——而這正是 E3 要修的那一類 bug，只是漏了這一條分支。
  連帶地，`bpmf_test_jni.c` 的註解與本 devlog E3 段當時宣稱「所有回傳 0 的路徑都已涵蓋」
  **在修正前並不成立**；本階段修掉該分支後才真正成立。

  已修：該分支改為 `*candidates_out = (char*)kEmptyCandidates;`，不再 `strdup`。
  `handle->last_candidates` 維持在稍早已被 free 並設回 NULL 的狀態，沒有東西需要之後釋放。
  全檔現在只剩結尾 `joined = strdup("")` 一處會配置空字串，且其 OOM 分支已指向 static 空字串。

  驗證：`assembleDebug`/`assembleRelease`/`testDebugUnitTest`/`ktfmtCheck`/`lint` 全綠；
  實機 `R6AIB700988748X` `connectedAndroidTest` 5/5 綠（含 E2 一聲迴歸測試與 fail-closed
  negative test，後者走的正是本次修改的這條分支）。

## 第七階段（Opus 級追蹤者第八輪 7 條 finding 修正輪，2026-08-11）

以下 7 條 finding（K1–K7）逐條記錄處理方式與證據；本輪不動 `:common`、不動
`docs/STATUS.md`、不動 vendored libchewing、不 push。

- **K1〔high → owner 裁示降為只記案〕LGPL-2.1 靜態連結**：ADR-0006 把建置管線換成
  Corrosion `corrosion_import_crate()`，把 vendored `chewing_capi`（LGPL-2.1，
  `crate-type = ["rlib", "staticlib"]`）整份靜態編進 `libbpmf.so`，但 ADR-0001 的合規
  論證前提原文是「動態連結（JNI 載 `.so`）」，且 ADR-0001 自己明講「未來若想靜態連結需
  重新評估授權與逆向工程條款」——ADR-0006 翻掉了這個前提卻沒接手評估，全文未提 LGPL，
  repo 也沒有 `NOTICE`。**owner 裁示本輪只記案、不改連結方式**：
  - ADR-0006 Consequences 補了一段，明講 (a) 前提已被本 ADR 換成靜態連結、(b) 因此
    ADR-0001 的授權論證在靜態連結下不再成立、需要 LGPL §6(a) 履行或改回動態連結、
    (c) 明確登記為 **W4-D（上架）的 blocker**。
  - ADR-0001 底部的 supersede 註記補上「授權前提部分亦受影響，詳見 ADR-0006」。
  - devlog「已知缺口」補一條（見上方，第二階段那節）。
  - 沒有動 `decoder-native/cmake/CMakeLists.txt` 或任何連結方式相關程式碼。

- **K2〔medium〕`ChewingDataPath.kt` KDoc 與 `bpmf.h` 權限要求相反**：`bpmf.h:56`
  已在第三階段（A2）改成「`data_path` 只需可讀」，但 `ChewingDataPath.kt` 頂部 KDoc
  仍寫「needs a writable filesystem directory」——已改成「needs a readable filesystem
  directory」，並補一句：`cacheDir` 之所以要可寫，是為了 [extractChewingData] 自己的
  解壓縮（寫 `.tmp` + rename），不是 `bpmf_init()` 的要求（後者只讀不寫）。純文件修正，
  不影響任何簽章或行為。

- **K3〔medium〕不可能失敗的實機測試**：`bpmfFree_isSafeToCallOnFreshHandleAndDoesNotCrash`
  沒有任何 assert，字典缺失時 `bpmf_init()` 回 NULL、`nativeTestFree(0)` 直接 no-op 也照樣
  綠；它唯一可能紅的情境（原生層 abort）已被 `bpmfInit_withExtractedDictionaryData_succeeds`
  完全涵蓋（那條測試的 `finally` 區塊本來就會呼叫 `nativeTestFree`）。已**刪除**這條測試
  （finding 給的兩個選項之一：它是第一條的嚴格子集）。原本「double-free protection is
  exercised by not calling it twice here」這句未查證的說法（`bpmf_free()` 其實完全沒有
  double-free 保護）隨測試一起消失，但這個事實不能就此無人知曉——改為寫進 `bpmf.h` 的
  `bpmf_free()` KDoc：明講不提供 double-free 保護、呼叫端必須自行保證只呼叫一次。

- **K4〔medium〕`bpmf_commit()` 的效果一定會被下一次 `bpmf_input()` 丟棄，header 沒寫**：
  查證屬實——`bpmf_wrapper.c` 的 `bpmf_input()` 每次開頭都無條件 `chewing_Reset(ctx)`，
  而 `chewing_Reset` 會清空 composition 與 commit buffer；這 4 個 API 沒有任何讀出
  commit buffer 的出口，所以 `bpmf_commit()` 的效果在公開 API 上永遠不可觀測。已在
  `bpmf.h` 的 `bpmf_commit()` KDoc 明講這個限制，並在devlog「已知缺口」補一條
  （W2-A 若需要跨呼叫累積組句，需要另加 API，例如導出 `chewing_buffer_String()`，
  超出 W1-A 範圍）。純文件修正，不動 `bpmf_wrapper.c` 的行為。

- **K5〔medium〕字典資料改版後永遠不會更新**：查證屬實——`extractChewingData` 只看
  「最終檔名是否存在」，cacheDir 在 app 升級後會保留，`fetch_chewing_data.sh` 的
  `VERSION` 之後必然會 bump，使用者升級後 cacheDir 仍是舊字典且沒有任何訊號。
  **改法**：把快取目錄名綁上資料版本——新增 `CHEWING_DATA_VERSION = "2026.3.22"`
  常數（KDoc 明講必須與 `fetch_chewing_data.sh` 的 `VERSION` 手動同步，兩者是不同語言、
  沒有機制強制一致，這是人工不變量）；`getDataPath()` 改成解壓進
  `cacheDir/chewing-$CHEWING_DATA_VERSION`，並在解壓前呼叫新增的
  `deleteStaleChewingCacheDirs()`（比對 `chewing-` 前綴、刪掉除了當前版本以外的所有
  同前綴目錄，best-effort、刪不掉就留著，不丟例外）。`extractChewingData()` 本身
  （單元測試涵蓋的核心邏輯）未改，改的是它的生產呼叫端 `getDataPath()`；同步把
  `extractChewingData` KDoc 的「Self-healing」限定清楚：只對「同一個 targetDir、
  同樣的 asset 內容、process 被砍重試」成立，不對「asset 內容換了」成立。

  **測試證明會紅**：在 `ChewingDataPathTest.kt` 新增 3 個測試（`getDataPath` 依版本
  分流、刪除舊版本殘留目錄、不誤刪不相干目錄）。把 `ChewingDataPath.kt` 換回
  `git show HEAD:...` 取出的修正前版本（`CHEWING_CACHE_DIR_NAME = "chewing"`，
  無版本、無清理邏輯）跑 `:decoder-native:testDebugUnitTest --tests
  ChewingDataPathTest --rerun`：

  ```
  ChewingDataPathTest > getDataPath deletes a stale chewing-* cache dir left by a previous app version() FAILED
      org.opentest4j.AssertionFailedError at ChewingDataPathTest.kt:178
  ChewingDataPathTest > getDataPath scopes the extraction directory to the current dictionary data version() FAILED
      org.opentest4j.AssertionFailedError at ChewingDataPathTest.kt:160
  9 tests completed, 2 failed
  BUILD FAILED
  ```

  換回修正後版本，同一條指令 `--rerun`：`9 tests completed, 0 failed`，`BUILD
  SUCCESSFUL`。（測試名稱裡原本含 `*` 字元觸發 ktfmt 的 Windows 檔名警告，已改名
  避開，不影響斷言內容。）

- **K6〔medium〕ADR 的 CI follow-up 清單不完整，照做仍不會綠**：查證屬實——`ci.yml`
  的 `actions/checkout@v4` 沒有 `submodules:`，`decoder-native/cmake/libchewing`
  （gitlink）與它自己巢狀的 `data` submodule 在 runner 上都會是空目錄，
  `CMakeLists.txt` 的 `if(NOT EXISTS .../Cargo.toml)` 會先 `FATAL_ERROR`，根本走不到
  Rust toolchain——只補 `rustup target add` 不會讓 CI 變綠，還會讓人誤以為 ADR 診斷錯了
  （因為錯誤訊息是 CMake FATAL_ERROR，不是 Rust 相關訊息）。已實際修 `ci.yml`：
  1. `checkout@v4` 加 `submodules: recursive`（recursive 是因為要連 `data` 這層巢狀
     submodule 一起拉，不是只拉第一層）。
  2. 新增一個獨立 step `rustup target add aarch64-linux-android
     armv7-linux-androideabi`，排在 checkout 與 Android SDK 之後、`assembleDebug`
     之前。
  3. NDK/CMake 版本來源（`decoder-native/build.gradle.kts` pin
     `ndkVersion = "27.2.12479018"`，但 CI 的 `android-actions/setup-android@v4`
     目前 `packages` 只列 `platforms;android-35 build-tools;35.0.0`，沒有明確裝這個
     NDK 版本）**沒有 CI runner 可實跑驗證**，仍留為開放問題，寫進 ADR-0006。
  已用 `python3 -c "import yaml; yaml.safe_load(...)"` 驗證 `ci.yml` 是合法 YAML；
  沒有 GitHub Actions runner 可實際跑一次驗證 submodule+target 修正後真的會綠，這點
  誠實記錄為未驗證。ADR-0006 的「開放問題 / 風險」與 Consequences 負面段都已同步
  改寫，不再宣稱「還沒有」（已補上前兩步，第三步待驗證）。

- **K7〔medium〕所有權契約說「一律 heap-allocated」，但 `kEmptyCandidates` 是
  `.rodata`**：查證屬實——`bpmf.h` 寫「writes a single heap-allocated ... string」
  「stays valid until the NEXT bpmf_input() call」，但零候選路徑（`opaque_handle`/
  `zhuyin` 為 NULL、字元不在映射表、以及結尾 `strdup("")` OOM 三處）交出去的都是
  `bpmf_wrapper.c` 的 `static const char kEmptyCandidates[] = ""`：不是 heap、寫入會
  SIGSEGV，有效期是整個 process 而非「到下一次呼叫」。**選擇文件修正（而非讓零候選
  也回傳 handle 擁有的 heap `""`）**：理由是 `opaque_handle == NULL` 這條路徑本來就沒有
  handle 可以擁有任何字串——呼叫端傳 NULL handle 進來時，函式在觸碰 `handle` 之前就要
  回傳，所以「handle-owned 空字串」這個方案在這條路徑上原理上就不成立，勢必還是要有一個
  不屬於任何 handle 的 process-lifetime 空字串存在；與其只解決「count==0 且 handle 非
  NULL」這一種子情況、留下 handle==NULL 這條路徑繼續用 static 字串（文件反而要拆更細的
  三種情形），不如把兩種情形講清楚更簡單、也更誠實。已把 `bpmf.h` 的 ownership 段落
  改寫成：回傳值一律唯讀（不得寫入、不得 free，兩種情形共同成立）；候選數 > 0 時是
  handle 擁有的 heap 字串、有效期到下一次呼叫或 `bpmf_free()`；候選數 == 0 時可能是
  process 生命週期的 `static const` 字串，不保證屬於該 handle，寫入是未定義行為
  （多數平台會 SIGSEGV，但不可依賴這個現象本身）。沒有動 `bpmf_wrapper.c` 的行為。

### 收尾指令與實機結果

- `:decoder-native:assembleDebug` → `BUILD SUCCESSFUL`
- `:decoder-native:assembleRelease` → `BUILD SUCCESSFUL`
- `:decoder-native:testDebugUnitTest` → `BUILD SUCCESSFUL`（`ChewingDataPathTest`
  9/9 綠，含新增的 3 個 K5 測試）
- `:decoder-native:ktfmtCheck` → 第一輪抓到 `ChewingDataPath.kt` 格式不符，跑
  `ktfmtFormat` 後 `BUILD SUCCESSFUL`
- `:decoder-native:lint` → `BUILD SUCCESSFUL`（需連網，見 E1 取捨，本輪未變）
- 上述四項＋`assembleRelease` 合併在同一次 `./gradlew` 呼叫也核對過一次全綠
- 實機 `R6AIB700988748X`（ASUS_AI2302, API 15）`:decoder-native:connectedAndroidTest`
  → `BUILD SUCCESSFUL`，`BpmfNativeSmokeTest` **4/4 綠**（K3 刪掉一條後從 5 條變 4
  條：`bpmfInit_withExtractedDictionaryData_succeeds`、
  `bpmfInput_forNiHao_returnsNonEmptyCandidates`、
  `bpmfInput_forUnmappedCharacters_failsClosedWithNoCandidates`、
  `bpmfInput_forFirstToneSyllable_commitsPendingSyllableNotPreviousOne`）。

### 對 finding 本身的補充意見

- 沒有發現 K1–K7 有判斷錯誤之處。
- K1 是本輪唯一一條「查證屬實但不動程式碼」的 finding——owner 明確裁示只記案，這不代表
  finding 的技術判斷有誤，只是修法（改回動態連結 vs. 補 LGPL 履行文件）需要 owner 對
  上架時程與履行成本做取捨，不是這輪 fix-loop 該自行決定的範圍。
- K6 提醒「順序很重要」這點在實作時確實驗證到：`ci.yml` 現有的
  `android-actions/setup-android@v4` 只管 SDK/build-tools，不管 submodule；若沒注意
  順序，容易誤以為「補了 target 就夠」，事實上 submodule 沒修對，target 修了也沒用。
- K7 的兩個選項都合理，選文件修正主要是「handle==NULL 這條路徑本來就不可能有
  handle-owned 字串」這個結構性理由，不是嫌 heap-owned 空字串方案技術上做不到。
