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
- 字典資料（`word.dat`/`tsi.dat`）**不**在 CMake/cargo 建置流程裡現編（那需要另外跑 `chewing-cli`，等於再多一個 host-side Rust 建置目標）。改用 `decoder-native/scripts/fetch_chewing_data.sh` 下載 upstream 發布的 prebuilt "Generic" 資料包（`chewing/libchewing-data` release `v2026.3.22`），sha256 校驗後解壓進 `decoder-native/src/main/assets/chewing/`，Gradle 把這個 script 掛在 `preBuild`/`mergeAssets` 前。已驗證這個 `v2026.3.22` release 的 commit（`c44e81aef24b06f1509f19e1be54c99812d0c43f`）與我們 vendor 的 `data` submodule commit **完全一致**，不是版本混搭。二進位資料不進 git（見 `.gitignore`），靠腳本可重現下載。

**（2026-08-10 修正輪更新）** 這個 fetch task **不**掛在 `preBuild`：`preBuild` 也在純 JVM 的
`testDebugUnitTest`/`testReleaseUnitTest`（例如 `ChewingDataPathTest`）task graph 裡，這些測試
完全不碰 assets，掛在 `preBuild` 會讓乾淨 checkout 跑一個純 unit test 也要對外連 GitHub，離線環境
（或額度受限時）會無謂失敗。改成只掛在會實際讀 `src/main/assets` 的 task 上：AGP 的
`merge*Assets`/`package*Assets`（資源合併管線）與 `lint*`/`Lint*`（lint 的 model builder 直接讀
source set 的 assets 目錄，不經過 merge/package）。已用 `--dry-run` 核對
`:decoder-native:testDebugUnitTest` 的 task graph 裡沒有 `fetchChewingData`，且
`assembleDebug`/`assembleRelease`/`lint`/`connectedAndroidTest` 都仍會觸發它並成功跑完。詳見
devlog A4。

一句話：**上游已經把「編譯」這件事的重心從 C 編譯器搬到 cargo，我們的建置管線只是如實反映這件事，同時盡量重用上游自己驗證過的 Corrosion 配方，而不是自己發明一套。**

## Consequences（後果）

**正面**
- 建置管線與 upstream 自己的 CI 配方同構（Corrosion + capi crate），未來 upstream 出新版時，我們的 `CMakeLists.txt` 大機率不用大改。
- 只 import `chewing_capi` 一個 crate（不是整個 `libchewing` CMake 專案），避免拉入 upstream 自己的 docs/tests/BUILD_DATA 邏輯，`decoder-native` 的建置範圍維持最小。
- 實測 `assembleDebug`／`assembleRelease` 都成功：debug `libbpmf.so` 未最終最佳化（arm64-v8a 11.4MB／armeabi-v7a 7.7MB，AGP 已剝除偵錯符號後的數字），release profile（workspace `Cargo.toml` 的 `[profile.release]` 已開 `lto = true, opt-level = 3, panic = "abort"`）大幅縮小：arm64-v8a 3.43MB／armeabi-v7a 2.45MB（剝除後）。

**負面**
- 多一層工具鏈依賴：本機需要 rustup 管理的 Rust toolchain + `aarch64-linux-android`／`armv7-linux-androideabi` target（見下方「本機 Homebrew rustc/cargo 衝突」），CI（GitHub Actions）目前**還沒有**裝這些，這是本 ADR 留下的已知缺口，不在 W1-A 範圍內解決。
- `chewing_capi` 依賴（`env_logger`／`der`／`regex` 等一串 crates.io 套件）比純 C 版多一層供應鏈面（crates.io 套件完整性），不像純 C 版只依賴 libc。
- 字典資料改用「下載 prebuilt release」而非「自己跑 chewing-cli 從 `.src` 建」，意味著我們現在信任 upstream release 流程的完整性，而不是自己重新產生一份可完全稽核的建置鏈；若未來要換字典內容（例如加自訂詞），需要另外處理（跑 chewing-cli 或直接編輯 .dat，兩者都超出 W1-A 範圍）。

**開放問題 / 風險**
- **本機 Homebrew rustc/cargo 與 rustup 衝突**：這台機器（M1 MBA，本次 W1-A 施工機）`brew install rustup` 後，`/opt/homebrew/bin/cargo`／`rustc` 仍然指向 Homebrew 自己的 `rust` formula（1.96.1，keg-only 但先佔用了 `bin` 的 symlink），rustup 管理的工具鏈（1.97.1，含 Android target）被安裝到 `/opt/homebrew/opt/rustup/bin/`，不在預設 `PATH` 順位前面。**解法**：不動全域 Homebrew link 狀態（`brew unlink rust` 影響其他 session/專案，超出這包授權），改為每次跑 native build 時把 `/opt/homebrew/opt/rustup/bin` 加到 `PATH` 前面：
  ```bash
  PATH="/opt/homebrew/opt/rustup/bin:$PATH" ./gradlew :decoder-native:assembleDebug
  ```
  這條命令本身沒有寫死進任何 checked-in 檔案（`CMakeLists.txt`／`build.gradle.kts` 都沒有硬編這台機器的路徑），純粹是本機操作註記；CI 或其他機器要用不同 PATH 排法時不受影響。Corrosion 的 `FindRust.cmake` 本身有 `rustup which cargo`/`rustup which rustc` 的偵測邏輯，`rustup` 這個指令本身在這台機器並未被 Homebrew `rust` formula 蓋掉（只有 `cargo`/`rustc` 被蓋），所以只要 `PATH` 順位對，Corrosion 就能正確找到 rustup 管理的工具鏈。
- **CI 尚未跟進**：`.github/workflows/` 目前沒有安裝 Rust Android target 的步驟；下一次碰 CI 設定時要一併補上（`rustup target add aarch64-linux-android armv7-linux-androideabi`），否則 CI 上的 `assembleDebug`/`assembleRelease` 會失敗。這是本 ADR 明確留下的 follow-up，不在 W1-A 範圍內動手。
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
