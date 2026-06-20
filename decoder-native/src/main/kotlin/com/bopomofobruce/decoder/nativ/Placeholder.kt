package com.bopomofobruce.decoder.nativ

/**
 * Skeleton marker for the `:decoder-native` module.
 *
 * W1-A (`feat/w1-decoder-native`) wires libchewing via CMake, produces `libbpmf.so`, and exposes
 * the C API plus the Kotlin asset-extraction helper. The Gradle skeleton intentionally leaves the
 * `externalNativeBuild { cmake { ... } }` block out so the module is buildable without native
 * sources; W1-A will add it back when the CMakeLists is checked in.
 *
 * Note: the package uses `nativ` (no trailing `e`) because `native` is a reserved Kotlin keyword.
 */
internal const val MODULE_NAME: String = "decoder-native"
