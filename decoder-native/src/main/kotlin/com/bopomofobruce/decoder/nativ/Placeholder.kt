package com.bopomofobruce.decoder.nativ

/**
 * Marker for the `:decoder-native` module.
 *
 * W1-A (`feat/w1a-decoder-native`) wires libchewing via CMake+Corrosion (see `cmake/CMakeLists.txt`
 * and [ADR-0006](../../../../../../../docs/adr/0006-libchewing-rust-build-pipeline.md)), produces
 * `libbpmf.so`, and exposes the C API (`cmake/include/bpmf.h`) plus the Kotlin asset-extraction
 * helper ([ChewingDataPath]). JNI binding of `libbpmf.so` itself is out of scope here — that's
 * W2-A's `:decoder`.
 *
 * Note: the package uses `nativ` (no trailing `e`) because `native` is a reserved Java keyword. AGP
 * 會用套件路徑產生 Java 類別（R.java / BuildConfig.java），含 `native` 會編譯失敗。 Kotlin 自己用 `external`
 * 宣告原生方法，`native` 在 Kotlin 不是保留字。
 */
internal const val MODULE_NAME: String = "decoder-native"
