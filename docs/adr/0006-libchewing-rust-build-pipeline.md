# ADR-0006：libchewing 建置管線改為 CMake + Corrosion（cargo cross-compile），不是純 CMake

- 日期：2026-08-10
- 狀態：Accepted
- 提議者：Bruce（透過 W1-A 子代理裁示）
- 相關：[ADR-0001](0001-libchewing-decoder-backend.md)（本 ADR 補充/部分取代其建置管線假設，決策本體「採用 libchewing」不變）

## Context（背景）

[ADR-0001](0001-libchewing-decoder-backend.md) 決定採用 libchewing 當 v1 decoder 後端，假設前提是「C99，無 GUI 依賴，NDK r26 + CMake 理論上可直接 cross-compile」，並明確把「這個假設是否成立」留給 [W1-A spike](DEVPLAN-SubagentFanout-20260620-0851.md#w1-a--decoder-native-把-libchewing-編成-so) 驗證。

W1-A 動工前查證 upstream 現況，發現這個假設在近兩年已經不成立：

- libchewing **v0.9.0（2024-08）起，核心邏輯全面改寫成 Rust**（`src/` 下只剩 `.rs`），公開 C API 由獨立的 `capi/` crate（`chewing_capi`，透過 cbindgen 產生標頭）提供。上游自己的 `CMakeLists.txt` 現在也是靠 [Corrosion](https://github.com/corrosion-rs/corrosion)（一個把 `cargo build` 接進 CMake 的第三方 CMake 模組）驅動編譯，不是直接編 C 原始碼。
- 唯一還符合 ADR-0001 字面「純 C99」的版本是 **v0.5.1（2016-05）**，已停更近 10 年，字典/bug fix 都停在 2016 年，會直接撞上 ADR-0001 自己定義的重評條件（詞典 5 年沒更新導致新詞嚴重缺失）。
- 上游同時把系統字典（`word.dat`/`tsi.dat`/...）拆到獨立的 `chewing/libchewing-data` submodule + release（版本號如 `v2026.3.22`），要編出可用的字典二進位檔需要另一個 Rust 工具 `chewing-cli`，或直接用官方發布的 prebuilt "Generic" 資料包。

owner 已就此裁示（見 devlog 2026-08-10 段）：**採用 v0.12.0（commit `05ae6bcb9309c466a1b32d69c146bc583be04747`，2026-04-06 發布）**，不採用過時的 v0.5.1，本 ADR 記錄配套的建置管線變更；ADR-0001「用 libchewing」的核心決策不變，只有它對建置方式的字面描述需要這份補充。

## Decision（決定）

**`decoder-native` 的建置管線是 CMake + [Corrosion](https://github.com/corrosion-rs/corrosion)（pin `v0.6.1`，commit `1499b14e4906a2890f5cee1547c8848db261753d`），不是純 C CMake 編譯：**

- `decoder-native/cmake/CMakeLists.txt`（AGP `externalNativeBuild.cmake.path` 指到這裡）用 `FetchContent` 拉 Corrosion，`corrosion_import_crate()` 把 vendored 的 `chewing_capi` crate（`decoder-native/cmake/libchewing/capi`，`[lib] crate-type = ["rlib", "staticlib"]`）依 AGP 傳入的 `CMAKE_ANDROID_ARCH_ABI` 交叉編譯成對應 ABI 的 Rust staticlib。Corrosion 原生支援從 `CMAKE_ANDROID_ARCH_ABI` 推導 Rust target triple（`aarch64-linux-android` / `armv7-linux-androideabi`），不需要額外的 cargo-ndk 或手寫 `.cargo/config.toml` target 對照。
- 我們自己寫的一個薄 C wrapper（`decoder-native/cmake/src/bpmf_wrapper.c`，含 DaChen 鍵盤對照表 — 對照表直接照抄 vendored `src/editor/zhuyin_layout/standard.rs`，不是憑記憶重建）`#include` 上游的 `capi/include/chewing.h`，呼叫 `chewing_new3`/`chewing_handle_Default`/`chewing_cand_*`/`chewing_commit_String`/`chewing_delete` 等公開 C API，`target_link_libraries` 連結剛剛的 `chewing_capi` staticlib，一起編成單一 `libbpmf.so`。這條路線完全比照 upstream 自己的 `CMakeLists.txt`（`add_library(libchewing capi/src/chewing.c)` 接 `chewing_capi`），只是我們的 C 檔案換成 `bpmf_wrapper.c` 輸出 DEVPLAN 指定的 4 個簡化 API，而不是把全部 ~60 個 libchewing C API 都轉出去。
- 字典資料（`word.dat`/`tsi.dat`）**不**在 CMake/cargo 建置流程裡現編（那需要另外跑 `chewing-cli`，等於再多一個 host-side Rust 建置目標）。改用 `decoder-native/scripts/fetch_chewing_data.sh` 下載 upstream 發布的 prebuilt "Generic" 資料包（`chewing/libchewing-data` release `v2026.3.22`），sha256 校驗後解壓進 `decoder-native/src/main/assets/chewing/`。已驗證這個 `v2026.3.22` release 的 commit（`c44e81aef24b06f1509f19e1be54c99812d0c43f`）與我們 vendor 的 `data` submodule commit **完全一致**，不是版本混搭。二進位資料不進 git（見 `.gitignore`），靠腳本可重現下載。
  **（2026-08-11 第五階段更正，見 devlog E1）**：Gradle 把這個 script 真正掛在 `package*Assets`
  上——**不是**只掛在 decoder-native 自己的 `assembleDebug`/`assembleRelease`/`connected*`/
  `install*` 上。原因：下游 `:app`（經 `:decoder`）把 `:decoder-native` 當一般 AAR 依賴消費時，
  排進 task graph 的是 `:decoder-native:packageDebugAssets` 這類 artifact task，根本不會經過
  `:decoder-native` 自己的 `assembleDebug`/`assembleRelease`——只掛在後者上，會讓乾淨 checkout
  組出的 `:app` APK 打包一個空的 `assets/chewing/`（`getDataPath()` 解壓不出東西、
  `bpmf_init()` 在真機回 NULL——**2026-08-11 更正**：這句話寫下當時未經查證即為假設，
  已被 Opus 級追蹤者第十二輪指出：查證屬實的部分只到「`getDataPath()` 解壓不出東西」，
  「`bpmf_init()` 在真機回 NULL」在當時的 `bpmf_wrapper.c` 底下並不成立——vendored
  `capi/src/io.rs` 的 `chewing_new3()` 沒有任何回傳 NULL 的路徑，字典目錄缺字典時
  `Editor::chewing()` 會靜默退回內建 mini 字典，`bpmf_init()` 舊版只在
  `data_path == NULL` 或 `malloc` 失敗才回 NULL。這句話現在之所以成立，是因為
  第十二輪同一批修正已在 `bpmf_init()` 內加了字典可用性自我檢測，詳見
  `bpmf.h`／`bpmf_wrapper.c` 與 devlog 對應段落，不是這裡原本假設的理由）。
  `ensureChewingDataDir`（純 `mkdir`、不連網）仍是
  `src/main/assets/chewing` 目錄本身唯一的 `outputs.dir` 擁有者，`package*Assets`/`Lint*`
  兩類 task 現在**同時**依附 `ensureChewingDataDir`（先 mkdir）與 `fetchChewingData`（真下載）。
  這個決定的直接後果是 **`:lint` 又重新透支需要連網**（AGP 把
  `lintAnalyzeDebugUnitTest`/`lintAnalyzeDebugAndroidTest`/`lintAnalyzeDebug`/
  `generateDebugLintReportModel` 都無條件連到 `package*Assets` 或直接讀 assets 目錄，這條
  耦合是 AGP 內建、這份 build script 無法切斷）——這是**刻意接受的取捨，不是疏忽**：APK 正確性
  優先於 lint 可離線執行。lint 需要連網因此從第四階段（A12）的「已解決」明確降級為「已知取捨」。

**（2026-08-10 修正輪的詳細理由）** 這個 fetch task **不**掛在 `preBuild`：`preBuild` 也在純 JVM 的
`testDebugUnitTest`/`testReleaseUnitTest`（例如 `ChewingDataPathTest`）task graph 裡，這些測試
完全不碰 assets，掛在 `preBuild` 會讓乾淨 checkout 跑一個純 unit test 也要對外連 GitHub，離線環境
（或額度受限時）會無謂失敗。

第二輪審查發現，把它改掛在 `merge*Assets`/`package*Assets` 與 `lint*` 上又矯枉過正：lint 是純靜態
分析，只需要 assets 目錄**存在**、不需要字典**內容**，卻因此永遠需要連網（`--dry-run` 實測連
`lintAnalyzeDebugUnitTest` 都被掛住）。最終形態是把兩種需求分開：

- `ensureChewingDataDir`（純 `mkdir`、不連網）← `merge*Assets`/`package*Assets`/`lint*` 依附它；
- `fetchChewingData`（下載＋sha256 校驗）← 只有 `assembleDebug`/`assembleRelease`/`connected*`/
  `install*` 依附它，並以 `mustRunAfter` 保證它排在 assets 合併之前。

`connected*`/`install*` 是**明列**的，不是假設它們會從 `assembleDebug` 傳遞繼承——實測顯示只掛
`assemble*` 時 `:decoder-native:connectedAndroidTest` 的 task graph 裡**沒有** `fetchChewingData`，
乾淨 checkout 下實機測試會打包到空的 assets 目錄（本機之所以會過，只是因為字典剛好已在磁碟上）。

修正後 `--dry-run` 實測：`lint` 無、`testDebugUnitTest` 無、`assembleDebug` 有、`assembleRelease`
有、`connectedAndroidTest` 有。詳見 devlog A4/A12。

**（2026-08-11 第五階段更正，見 devlog E1）**：以上第二輪審查（A12）與其「最終形態」在
2026-08-11 被 Opus 級驗證者指出是不完整的——它只驗證了 `:decoder-native` 自己的
`assembleDebug`/`assembleRelease`/`connectedAndroidTest`，沒有驗證下游 `:app` 實際消費
`:decoder-native` 時走的是 `packageDebugAssets`（不是 `assembleDebug`），導致乾淨 checkout 組
出的 `:app` APK 仍打包空字典。本節「`ensureChewingDataDir` ← `merge*Assets`/`package*Assets`/
`lint*` 依附它；`fetchChewingData` ← 只有 `assembleDebug`/`assembleRelease`/`connected*`/
`install*` 依附它」這個分工方案已不再是現行狀態——見本 ADR 前面 Decision 段落「（2026-08-11
第五階段更正）」，`package*Assets`/`Lint*` 現在都直接依附 `fetchChewingData`（真下載），`lint`
需要連網是刻意接受的取捨。這段歷史敘述保留是為了記錄 A12 當時的推理過程，不代表現行行為。

一句話：**上游已經把「編譯」這件事的重心從 C 編譯器搬到 cargo，我們的建置管線只是如實反映這件事，同時盡量重用上游自己驗證過的 Corrosion 配方，而不是自己發明一套。**

## Consequences（後果）

**正面**
- 建置管線與 upstream 自己的 CI 配方同構（Corrosion + capi crate），未來 upstream 出新版時，我們的 `CMakeLists.txt` 大機率不用大改。
- 只 import `chewing_capi` 一個 crate（不是整個 `libchewing` CMake 專案），避免拉入 upstream 自己的 docs/tests/BUILD_DATA 邏輯，`decoder-native` 的建置範圍維持最小。
- 實測 `assembleDebug`／`assembleRelease` 都成功：debug `libbpmf.so` 未最終最佳化（arm64-v8a 11.4MB／armeabi-v7a 7.7MB，AGP 已剝除偵錯符號後的數字），release profile（workspace `Cargo.toml` 的 `[profile.release]` 已開 `lto = true, opt-level = 3, panic = "abort"`）大幅縮小：arm64-v8a 3.43MB／armeabi-v7a 2.45MB（剝除後）。

**負面**
- 多一層工具鏈依賴：本機需要 rustup 管理的 Rust toolchain + `aarch64-linux-android`／`armv7-linux-androideabi` target（見下方「本機 Homebrew rustc/cargo 衝突」）。CI（GitHub Actions）第七階段已補上 submodule checkout 與 `rustup target add`（見下方「開放問題 / 風險」K6），但 NDK/CMake 版本來源是否需要在 CI 明列尚未驗證，仍是已知缺口。
- `chewing_capi` 依賴（`env_logger`／`der`／`regex` 等一串 crates.io 套件）比純 C 版多一層供應鏈面（crates.io 套件完整性），不像純 C 版只依賴 libc。
- 字典資料改用「下載 prebuilt release」而非「自己跑 chewing-cli 從 `.src` 建」，意味著我們現在信任 upstream release 流程的完整性，而不是自己重新產生一份可完全稽核的建置鏈；若未來要換字典內容（例如加自訂詞），需要另外處理（跑 chewing-cli 或直接編輯 .dat，兩者都超出 W1-A 範圍）。
- **（2026-08-11 第五階段新增）`:lint` 需要連網**：`package*Assets`/`Lint*` 兩類 task 現在都直接依附 `fetchChewingData`，這是修正下游 `:app` 拿不到字典（見 devlog E1）的必然代價——AGP 把 lint 的 model builder 無條件連到 `package*Assets`，這份 build script 無法切斷這條耦合，只能整批接受或整批拒絕。已刻意選擇「整批接受」：APK 正確性優先於 lint 可離線執行。離線環境跑 `:decoder-native:lint` 會失敗；CI 或斷網環境需注意這點。
- **（2026-08-11 第七階段新增，K1）本 ADR 把 ADR-0001 的動態連結前提改成了靜態連結，授權論證沒有跟著重新過關**：`decoder-native/cmake/CMakeLists.txt` 的
  `corrosion_import_crate()` 把 vendored `chewing_capi`（`[lib] crate-type = ["rlib", "staticlib"]`）整份編成 staticlib，`target_link_libraries` 直接靜態連進 `libbpmf.so`——這正是 ADR-0001 Consequences 段明講「未來若想靜態連結需重新評估授權與逆向工程條款」的那個情境，但本 ADR 決定走 Corrosion staticlib 路線時完全沒提 LGPL、也沒重新評估。libchewing 是 **LGPL-2.1**，ADR-0001 的合規論證原文是「License LGPL-2.1：**動態連結**（JNI 載 `.so`）合規」——前提已經被本 ADR 換掉，論證卻沒有跟著換。目前 repo 內也沒有任何 `NOTICE`／授權履行文件。這代表：
  - 若要維持靜態連結（Corrosion staticlib），需要以 LGPL §6(a) 履行——附我方 wrapper（`bpmf_wrapper.c`／`CMakeLists.txt`）原始碼＋足以讓使用者 relink 成別版 libchewing 的物件檔／連結資訊，或者直接公開整個 repo 滿足「原始碼可得」；
  - 或者放棄 Corrosion 的 staticlib 產物，改回 `chewing_capi` 產出 `cdylib`（`.so`）、JNI 動態載入，回到 ADR-0001 原本已核可的合規路徑。
  - 這兩者本輪皆**不動手**——本輪 owner 裁示只記案，不改連結方式（見 devlog 第七階段）。
  - **明確登記為 W4-D（上架）的 blocker**：在正式上架 F-Droid／自行分發 APK 前，必須先完成上述兩者其一，否則靜態連結+無 LGPL 履行文件會讓上架審查（或事後被舉發）直接卡關。

**開放問題 / 風險**
- **本機 Homebrew rustc/cargo 與 rustup 衝突**：這台機器（M1 MBA，本次 W1-A 施工機）`brew install rustup` 後，`/opt/homebrew/bin/cargo`／`rustc` 仍然指向 Homebrew 自己的 `rust` formula（1.96.1，keg-only 但先佔用了 `bin` 的 symlink），rustup 管理的工具鏈（1.97.1，含 Android target）被安裝到 `/opt/homebrew/opt/rustup/bin/`，不在預設 `PATH` 順位前面。**解法**：不動全域 Homebrew link 狀態（`brew unlink rust` 影響其他 session/專案，超出這包授權），改為每次跑 native build 時把 `/opt/homebrew/opt/rustup/bin` 加到 `PATH` 前面：
  ```bash
  PATH="/opt/homebrew/opt/rustup/bin:$PATH" ./gradlew :decoder-native:assembleDebug
  ```
  這條命令本身沒有寫死進任何 checked-in 檔案（`CMakeLists.txt`／`build.gradle.kts` 都沒有硬編這台機器的路徑），純粹是本機操作註記；CI 或其他機器要用不同 PATH 排法時不受影響。Corrosion 的 `FindRust.cmake` 本身有 `rustup which cargo`/`rustup which rustc` 的偵測邏輯，`rustup` 這個指令本身在這台機器並未被 Homebrew `rust` formula 蓋掉（只有 `cargo`/`rustc` 被蓋），所以只要 `PATH` 順位對，Corrosion 就能正確找到 rustup 管理的工具鏈。
- **CI 尚未跟進（2026-08-11 第七階段更正，K6：原清單不完整，照做仍不會綠）**：`.github/workflows/ci.yml` 要補的不只是 Rust target，且**順序很重要**——只補 target 會先卡在更早一步的 submodule 問題，錯誤訊息是 CMake `FATAL_ERROR`，容易被誤讀成「ADR 的診斷錯了」。完整清單（依卡關順序）：
  1. `actions/checkout@v4` 目前**沒有** `with: submodules:`，`decoder-native/cmake/libchewing`（gitlink）在 runner 上會是空目錄；`decoder-native/cmake/libchewing/data`（libchewing 自己的巢狀字典 submodule）也是同樣問題。`CMakeLists.txt` 的 `if(NOT EXISTS .../Cargo.toml)` 會在空目錄上直接 `FATAL_ERROR`，整條 pipeline 連 Rust toolchain 都還沒摸到就先炸——這步不修，後面補了 Rust target 也還是紅在這裡。要用 `submodules: recursive`（不是 `true`），因為要連 `data` 這層巢狀 submodule 一起拉。
  2. `rustup target add aarch64-linux-android armv7-linux-androideabi`：submodule 修好之後才會真正卡到的下一關，Corrosion 的 `corrosion_import_crate()` 找不到對應 target 的 Rust std 會失敗。
  3. 確認 NDK/CMake 版本來源：`decoder-native/build.gradle.kts` 目前 pin `ndkVersion = "27.2.12479018"`；CI 用的 `android-actions/setup-android@v4` 目前只裝 `platforms;android-35 build-tools;35.0.0`，沒有明確裝這個 NDK 版本——要嘛在 `setup-android` 的 `packages` 加上對應 `ndk;27.2.12479018`，要嘛確認 AGP 會用 `sdkmanager` 自動補裝（目前未驗證，是本 ADR 留下的開放問題，不在 W1-A 範圍內解決）。
  第七階段已把前兩步實際補進 `.github/workflows/ci.yml`（`checkout@v4` 加 `submodules: recursive`；新增一個 `rustup target add aarch64-linux-android armv7-linux-androideabi` step，排在 checkout 之後、Android SDK 之後）。第三步（NDK/CMake 版本來源是否需要在 `setup-android` 的 `packages` 明列 `ndk;27.2.12479018`，還是 AGP 會自動用 `sdkmanager` 補裝）**未驗證**——本輪沒有 CI runner 可實跑驗證，是本 ADR 明確留下的 follow-up。
- **重評觸發條件延續 ADR-0001**：若 upstream 停更或詞典 5 年沒更新，重新評估（fork 自維 vs 換方案），與 ADR-0001 原文一致，不因本 ADR 而放寬或收緊。

## Alternatives considered（替代方案）

- **cargo-ndk 直出 `.so`**：`cargo ndk -t arm64-v8a -t armeabi-v7a -o jniLibs build --release` 這條路線更常見於「純 Rust Android 專案」（例如 Mozilla application-services），不需要 CMake。沒選的原因：(1) DEVPLAN §4 W1-A 字面就是要求「CMakeLists.txt 編出 libbpmf.so」，cargo-ndk 直出會整個繞過 CMake，需要另外接一個 Gradle 自訂 task 管理 ABI/輸出路徑/增量建置，等於重造 AGP `externalNativeBuild` 已經處理好的一部分邏輯；(2) 我們的 4 個 API 是用 **C** 寫的薄 wrapper（呼叫 libchewing 的 C API），不是 Rust 寫的 shim crate，cargo-ndk 直出假設整個 cdylib 都是 Rust 原始碼，跟我們選的「C wrapper + Rust staticlib」架構不搭；(3) upstream 自己也是走 Corrosion 這條路，跟隨上游配方能减少未來合併 upstream 變更時的落差。
- **Rust shim crate（cdylib 直接依賴 `chewing_capi`，不寫 C wrapper）**：技術上可行（`chewing_capi` 的函式雖標記 `extern "C"` 但仍是可從 Rust 呼叫的 `pub` 函式），且能省掉 C 檔案。沒選的原因：需要在 `chewing_capi` 的 Cargo.toml 加 `"cdylib"` 到 crate-type（vendored 原始碼不該改，即使只加一行也會讓「vendor 版本可重現」的驗證多一個變因），或另開一個依賴 `chewing_capi` 的新 crate（等於還是要學 `chewing_capi` 內部模組路徑，且新增一整個 Cargo 專案而非一個 C 檔案，維護面比薄 C wrapper 大）。C wrapper 只需要 `#include` 上游已發布的公開標頭（`capi/include/chewing.h`），耦合面更小、更貼近 DEVPLAN 字面要求。
- **接受 v0.5.1（2016 年最後純 C 版）**：完全不用碰 Rust 工具鏈，最貼近 ADR-0001 原始假設。沒選的原因：字典/bug fix 停在 2016 年，直接撞上 ADR-0001 自己定義的重評條件；owner 已就這點明確裁示不採用（見 devlog）。
- **Fork 純 C 版本自維**：ADR-0001 原文的 Plan B（「若 upstream 停更」）講的是「停更」情境，不是「換語言」情境；沿用 2016 年程式碼庫去 fork 一樣繼承詞典過舊問題，且要自己扛住 Rust 版之後 9 年的所有安全修補與 bug fix，solo dev 時間預算撐不住。

## References

- [ADR-0001](0001-libchewing-decoder-backend.md)
- [DEVPLAN W1-A 子代理 spec](DEVPLAN-SubagentFanout-20260620-0851.md#w1-a--decoder-native-把-libchewing-編成-so)
- [W1-A devlog](devlog/w1a-decoder-native-20260810-1504.md) — 完整版本調查與 owner 裁示紀錄
- libchewing 上游：<https://github.com/chewing/libchewing>（`v0.12.0` = commit `05ae6bcb9309c466a1b32d69c146bc583be04747`）
- libchewing-data：<https://github.com/chewing/libchewing-data>（`v2026.3.22` = commit `c44e81aef24b06f1509f19e1be54c99812d0c43f`，與我們的 `data` submodule 一致）
- Corrosion：<https://github.com/corrosion-rs/corrosion>（pin `v0.6.1` = commit `1499b14e4906a2890f5cee1547c8848db261753d`）
