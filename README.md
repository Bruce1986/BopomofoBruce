# BopomofoBruce

> 一套以注音為核心、為現代 Android 重寫的開源輸入法。
> A modern, offline-first Bopomofo (Zhuyin) IME for Android.

Google 注音輸入法（`com.google.android.apps.inputmethod.zhuyin`）自 2017 年起未再更新，2024 年後在新版 Android 上的相容性與體驗逐漸惡化。本專案以 clean-room 方式從零重建，並補上 2026 年該有的：Material You、Jetpack Compose、隱私可驗證、完全離線的核心解碼。

> ⚠️ **狀態：M0 — 立項中。** 還沒有可用的 APK。Roadmap 見 [docs/](./docs)。

---

## Planned features (v1)

- 注音 4×10 軟鍵盤（橫直屏）
- libchewing JNI 後端、個人字典
- Material You 動態色 + 自訂相片背景
- Emoji、符號、繁中專屬全形標點
- 100% 離線；零網路請求

**v1 不做**：拼音、倉頡、手寫、滑動輸入、雲端同步、TV／平板／折疊裝置、AI 候選。
這些之後（v1.5 / v2）視時間與心情再說。

詳細範圍見 [docs/REBUILD-PLAN-SoloEdition-20260530-1325.md](./docs/REBUILD-PLAN-SoloEdition-20260530-1325.md)。

---

## Stack

Kotlin 2 · Jetpack Compose · NDK (libchewing JNI) · Room · DataStore · GitHub Actions

- minSdk 28 / targetSdk 35
- ABI: arm64-v8a + armeabi-v7a
- 8 Gradle modules（刻意保持精簡）

---

## Why another Zhuyin IME?

| 既有方案 | 痛點 |
|---|---|
| Google 注音輸入法 | 2017 起未更新；缺 Material You；無法上 F-Droid |
| Gboard 注音 | 國際版繁中體驗較差；隱私顧慮 |
| 微軟新注音（Android） | 無官方 Android 版 |
| 其他 libchewing-based IME | UI 老舊、主題少、缺乏現代 Android 體驗 |

BopomofoBruce 的差異化在 **UX + 主題 + 隱私**，不在解碼引擎（直接站在 libchewing 巨人肩膀上）。

---

## Building

(尚未可建置，待 M0 spike 完成後補。)

---

## Roadmap

| 里程碑 | 內容 | 預估 |
|---|---|---|
| M0 | 立項、libchewing JNI spike | 2 週 |
| M1 | IME 框架可跑 | 4 週 |
| M2 | 注音核心 + 個人字典 | 4 週 |
| M3 | 主題系統 + 相片背景 | 3 週 |
| M4 | 符號與表情 | 2 週 |
| M5 | 打磨與上架 internal track | 3 週 |

詳見 [docs/REBUILD-PLAN-SoloEdition-20260530-1325.md](./docs/REBUILD-PLAN-SoloEdition-20260530-1325.md)。

---

## Contributing

歡迎開 issue 回報問題、提出功能建議、貢獻翻譯與主題。

PR 之前請開 issue 討論，避免做白工。注音 IME 的小眾性使得本專案以**單人維護**為前提設計，所以對「新增大型功能」會比較保守。

---

## License

Apache-2.0 © Bruce Jhang

第三方元件：
- [libchewing](https://github.com/chewing/libchewing)（LGPL-2.1，動態連結）
- Jetpack Compose、emoji2 等 AndroidX 元件（Apache-2.0）

---

## Acknowledgements

- 反向分析參考：Google 注音輸入法 2.4.5（2016 年 Android APK）— 僅用於還原已下架輸入法的功能與架構研究，未複用任何 Google 私有資料、模型或品牌素材。
- 解碼引擎：[chewing/libchewing](https://github.com/chewing/libchewing)。
- 命名靈感：作者本人。
