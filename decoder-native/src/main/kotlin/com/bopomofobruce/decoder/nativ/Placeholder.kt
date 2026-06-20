package com.bopomofobruce.decoder.nativ

/**
 * Skeleton marker for the `:decoder-native` module.
 *
 * W1-A (`feat/w1-decoder-native`) wires libchewing via CMake, produces `libbpmf.so`, and exposes
 * the C API plus the Kotlin asset-extraction helper. The Gradle skeleton intentionally leaves the
 * `externalNativeBuild { cmake { ... } }` block out so the module is buildable without native
 * sources; W1-A will add it back when the CMakeLists is checked in.
 *
 * Note: the package uses `nativ` (no trailing `e`) because `native` is a reserved Java keyword. AGP
 * 會用套件路徑產生 Java 類別（R.java / BuildConfig.java），含 `native` 會編譯失敗。 Kotlin 自己用 `external`
 * 宣告原生方法，`native` 在 Kotlin 不是保留字。
 */
internal const val MODULE_NAME: String = "decoder-native"
