# W1-A devlog — `:decoder-native` libchewing 編譯 spike

- 分支：`feat/w1a-decoder-native`
- 期間：2026-08-10 14:5x – 15:0x (UTC+8)
- 狀態：**未完工，卡在 ADR-0001 前提失真，停下回報 lead**

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
