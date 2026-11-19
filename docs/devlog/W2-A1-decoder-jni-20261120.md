# W2-A1 — `:decoder` JNI binding + `ChewingZhuyinDecoder`（2026-11-20）

> 產生時間：2026-11-20（UTC+8）。疊在 `feat/w1a-decoder-native`（PR #8）上的 `feat/w2-decoder-jni`。
> 只含 JNI 與 `ZhuyinDecoder` 實作；Room 個人字典與候選合併是另一包（W2-A2）。

## 做了什麼

- `decoder/cmake/`：新的 `libbpmf_jni.so`（純 C）。`JNI_OnLoad` 以 `dlopen("libbpmf.so")`＋`dlsym` 取 `bpmf.h` 的四個函式，
  缺任何一個就回 `JNI_ERR`，`System.loadLibrary` 當場丟 `UnsatisfiedLinkError`。
- `BpmfNative`（internal seam）＋`JniBpmfNative`；`ChewingZhuyinDecoder`（＋`Closeable`）；`DecoderModule.create(context)`（不做 singleton）。
  候選以 bytes 回 Kotlin 用標準 UTF-8 解碼（`NewStringUTF` 吃 modified UTF-8，BMP 以外會解錯）。

## JNI glue 放哪裡

| 方案 | 結果 |
|---|---|
| **A. `:decoder` 自己的 CMake，執行期 dlopen（採用）** | decoder-native 一個檔案都不改；release `libbpmf.so` 仍只匯出四個 `bpmf_*`；無新相依。指標型別用 `__typeof__(bpmf_init)` 取自 `bpmf.h`。代價：符號是否存在只能在 `JNI_OnLoad` 檢查，多一顆 `.so`（arm64 27 KB）。 |
| B. glue 編進 `libbpmf.so` | 要改 decoder-native 的 CMakeLists、version script 與 `bpmf.h` 裡「只匯出四個函式」的敘述；decoder-native 的 C 要寫死 `:decoder` 的類別名，改名只會在裝置上 `UnsatisfiedLinkError`。 |
| C. AGP prefab | 連結期就能檢查符號，但消費端要下載 `com.google.prefab:cli`（本機 gradle cache 沒有，屬新外部相依），兩邊 STL 要一致，也仍要改 decoder-native 的 build.gradle.kts。 |

## 驗證（實測）

- 根目錄 `assembleDebug` 成功；APK 的兩個 ABI 各有 `libbpmf.so`、`libbpmf_jni.so` 一份，arm64 兩顆的 PT_LOAD `p_align`
  都是 `0x4000`。`llvm-nm -D` 看 `libbpmf_jni.so`：只定義 `JNI_OnLoad` 與四個 `Java_..._JniBpmfNative_*`，沒有未定義的 `bpmf_*`。
- `:decoder` JVM 單元測試 8 條（fake `BpmfNative`）。對 `ChewingZhuyinDecoder.kt` 做 10 個單點突變（分數用錯 index、commit 固定
  index 0、close 不歸零、拿掉 init 檢查、reset 不碰 native／不清候選、拿掉空字串過濾、用錯 charset、commit 只比字、close 後
  仍呼叫 native），每個都有測試紅。
- 主機 ASan＋UBSan（host 版 `libchewing_capi.a`、假 `JNIEnv`，腳本不進 repo）：真字典 50 輪 init／input／commit／free，
  `detect_leaks=1` 無錯；`ㄋㄧˇㄏㄠˇ` → `好 郝 㚼 㝀`；`ㄏㄠˇx`、modified UTF-8 的 U+0000（`C0 80`）與 U+20000 都回 0 bytes；
  `libbpmf.so` 不存在或缺符號時 `JNI_OnLoad` 回 `JNI_ERR`。對 glue 的 3 個突變都被抓到。

## 未驗證／已知缺口

- **未在實機驗證**：`ChewingZhuyinDecoderDeviceTest`（3 條）只編譯過。Android linker namespace 下 `dlopen("libbpmf.so")`
  找得到同 APK 的 lib 是依文件推論，要靠這組測試確認。
- DEVPLAN W2-A 的「1k 條真實注音」「Top-1 ≥ baseline 95%」不在本包；`bpmf_input()` 只回游標所在音節的候選。
- `commit()` 以 `Candidate` 結構相等比對最近一次 `input()`；W2-A2 若重新計分，要拿原本的 candidate 回呼。分數是名次 `1/(i+1)`。
