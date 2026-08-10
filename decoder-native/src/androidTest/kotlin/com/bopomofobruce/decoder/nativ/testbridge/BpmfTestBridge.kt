package com.bopomofobruce.decoder.nativ.testbridge

/**
 * TEST-ONLY JNI bridge to `libbpmf.so` (see `cmake/src/bpmf_test_jni.c` for why this exists and why
 * it is not W2-A's real production binding). `connectedAndroidTest`-only; do not use from
 * `:decoder` or any shipping code — it exists solely so W1-A's own acceptance criteria (a real
 * on-device smoke test of the .so) can run without waiting on W2-A's JNI work.
 *
 * Exposure: `cmake/CMakeLists.txt` compiles `bpmf_test_jni.c` (the native side of these four
 * `nativeTest*` externs) into `libbpmf.so` only for Debug builds — the four symbols are absent from
 * release .so output (verified with `nm -D`; see devlog A5). This class itself lives under
 * `androidTest/`, so it is never packaged into a release app either way, but the underlying native
 * symbols would have been reachable via `dlsym()` on a release `libbpmf.so` before that gate was
 * added — in particular `nativeTestFree`, which treats its `Long` argument as a raw pointer and
 * calls `free()` on it.
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
