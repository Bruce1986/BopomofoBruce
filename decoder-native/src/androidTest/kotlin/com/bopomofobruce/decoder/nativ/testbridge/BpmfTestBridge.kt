package com.bopomofobruce.decoder.nativ.testbridge

/**
 * TEST-ONLY JNI bridge to `libbpmf.so` (see `cmake/src/bpmf_test_jni.c` for why this exists and why
 * it is not W2-A's real production binding). `connectedAndroidTest`-only; do not use from
 * `:decoder` or any shipping code — it exists solely so W1-A's own acceptance criteria (a real
 * on-device smoke test of the .so) can run without waiting on W2-A's JNI work.
 *
 * Exposure: `cmake/CMakeLists.txt` compiles `bpmf_test_jni.c` (the native side of these four
 * `nativeTest*` externs) into `libbpmf.so` only when `-DBPMF_BUILD_TEST_BRIDGE=ON` (debug variant
 * only) — the four symbols are absent from release .so output (verified with `nm -D`; see devlog
 * A5/A10). This class itself lives under `androidTest/`, so it is never packaged into a release app
 * either way.
 *
 * The underlying native symbols — in particular `nativeTestFree`, which treats its `Long` argument
 * as a raw pointer and calls `free()` on it — would have been reachable via `dlsym()` on a release
 * `libbpmf.so` before this gate was added, but keeping them out was never actually what stood
 * between a `dlsym()`-capable attacker and a `free()` primitive on release: until a
 * `--version-script` linker map was added to `cmake/CMakeLists.txt` (see R1 in the 20260811 devlog
 * entry), the release `.so` already exported vendored libchewing's own `chewing_free(void*)` — an
 * equally arbitrary-pointer free() — plus ~130 other `chewing_*` functions, regardless of this
 * gate. With the version script in place, release `libbpmf.so` now exports only `bpmf.h`'s 4
 * functions; this gate is what keeps that dynsym table matching the documented public API instead
 * of growing a second, redundant free-like entry point.
 */
internal object BpmfTestBridge {
    init {
        System.loadLibrary("bpmf")
    }

    external fun nativeTestInit(dataPath: String): Long

    external fun nativeTestInput(handle: Long, zhuyin: String): Array<String>

    external fun nativeTestCommit(handle: Long, index: Int)

    external fun nativeTestFree(handle: Long)
}
