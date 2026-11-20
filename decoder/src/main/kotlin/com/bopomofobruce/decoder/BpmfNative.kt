package com.bopomofobruce.decoder

/**
 * `libbpmf.so` 四個 C 函式（`decoder-native/cmake/include/bpmf.h`）的 Kotlin 端 seam。
 *
 * 正式實作是 [JniBpmfNative]；JVM 單元測試注入 fake，讓 [ChewingZhuyinDecoder] 的候選轉換、handle 生命週期與錯誤處理不必載入 native
 * library 就能測。語意照 `bpmf.h`：
 * - [init] 失敗回 `0L`。
 * - [input] 回傳 `bpmf_input()` 的 `'\n'` 串接字串原始 bytes（標準 UTF-8）；0 個候選時為空陣列。
 * - [commit] 的 index 對應最近一次 [input] 的順序，越界是 no-op。
 * - [free] 每個非 0 handle 只能呼叫一次（`bpmf_free` 不防 double free）。
 */
internal interface BpmfNative {
    fun init(dataPath: String): Long

    fun input(handle: Long, zhuyin: String): ByteArray

    fun commit(handle: Long, index: Int)

    fun free(handle: Long)
}

/** 正式 JNI binding；native 端是 `decoder/cmake/src/bpmf_jni.c`。 */
internal object JniBpmfNative : BpmfNative {
    init {
        // 先載入 libbpmf.so（decoder-native），再載入 libbpmf_jni.so：後者的 JNI_OnLoad 以
        // dlopen 取前者的符號，任何一個找不到都會在這裡丟 UnsatisfiedLinkError。
        System.loadLibrary("bpmf")
        System.loadLibrary("bpmf_jni")
    }

    external override fun init(dataPath: String): Long

    external override fun input(handle: Long, zhuyin: String): ByteArray

    external override fun commit(handle: Long, index: Int)

    external override fun free(handle: Long)
}
