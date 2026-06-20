package com.bopomofobruce.common

/**
 * 注音 → 候選詞 decoder 的抽象。
 *
 * 兩個實作目標：
 * - **Fake**：[com.bopomofobruce.common.fakes.FakeZhuyinDecoder]（in-memory mapping，給 :keyboards /
 *   :theme 做 preview 與 unit test 用）。
 * - **真實**：:decoder 模組（W2-A）會包 libchewing 的 JNI binding。
 *
 * **State 隱含於 implementation**：[input] 多次呼叫之間，decoder 可以累積上下文（n-gram 之類）。 呼叫 [reset] 清乾淨。:ime 在
 * EditText 切換 / 長時間 idle 時應主動 reset。
 *
 * Thread-safety：實作不保證 thread-safe，所有呼叫都應在同一個 dispatcher（通常是 :ime 的 work dispatcher）上
 * serialise。:common 不在這裡 enforce 因為硬綁 dispatcher 會把 Android coroutines main 耦合進來。
 */
interface ZhuyinDecoder {
    /**
     * Push 一段注音 buffer，回傳候選詞列表（按 [Candidate.score] 降序，可能空）。
     *
     * [buffer] 是 **完整** 的當前 composing buffer，不是 incremental delta（讓實作端決定要不要 diff）。
     */
    fun input(buffer: String): List<Candidate>

    /** 使用者選中一個 candidate，回饋給 decoder 學習（例如 personal dictionary 加分）。 */
    fun commit(candidate: Candidate)

    /** 清掉內部狀態（例如切換 input field 時）。`reset()` 後 [input] 從 0 開始計算。 */
    fun reset()
}
