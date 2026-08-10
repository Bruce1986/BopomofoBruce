package com.bopomofobruce.decoder.nativ.testbridge

/**
 * TEST-ONLY JNI bridge to `libbpmf.so` (see `cmake/src/bpmf_test_jni.c` for why this exists and why
 * it is not W2-A's real production binding). `connectedAndroidTest`-only; do not use from
 * `:decoder` or any shipping code — it exists solely so W1-A's own acceptance criteria (a real
 * on-device smoke test of the .so) can run without waiting on W2-A's JNI work.
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
