package com.bopomofobruce.common.fakes

import com.bopomofobruce.common.Candidate
import com.bopomofobruce.common.CandidateSource
import com.bopomofobruce.common.ZhuyinDecoder

/**
 * 給 **unit test 與 Compose preview** 用的 in-memory decoder。
 *
 * **不要** 在 production 依賴：
 * - mapping 是硬編碼的少量條目（沒有 libchewing 的 n-gram、tone parsing、tonal disambiguation）。
 * - [commit] 只在 [committedHistory] 累積，沒有真的學習。
 *
 * 提供穩定行為讓 :keyboards 的 preview 可以呈現「ㄋㄧˇ → 你 / 妳」這種真實感，而不需要載入字典 .so。
 *
 * Thread-safe：no — 跟 [ZhuyinDecoder] 介面契約一致，由呼叫端確保 serialise。
 */
class FakeZhuyinDecoder : ZhuyinDecoder {

    /** Test 可以查詢「使用者 commit 了哪些候選詞」確認 chain 行為。 */
    val committedHistory: MutableList<Candidate> = mutableListOf()

    private var resetCount: Int = 0

    /** Test 可以查詢 [reset] 被叫了幾次。 */
    val resetCallCount: Int
        get() = resetCount

    override fun input(buffer: String): List<Candidate> {
        if (buffer.isEmpty()) return emptyList()
        return BUFFER_TO_CANDIDATES[buffer]
            ?: listOf(
                // Unknown buffer fallback：回傳一個低分 placeholder，讓 UI 不會空白。
                Candidate(text = buffer, score = 0.1f, source = CandidateSource.PRIMARY)
            )
    }

    override fun commit(candidate: Candidate) {
        committedHistory.add(candidate)
    }

    override fun reset() {
        committedHistory.clear()
        resetCount += 1
    }

    private companion object {
        // 注意：sequence 用 Bopomofo Unicode block + 聲調符號。
        // 聲調：ˇ = U+02C7、ˋ = U+02CB、ˊ = U+02CA、˙ = U+02D9、（一聲無符號）。
        val BUFFER_TO_CANDIDATES: Map<String, List<Candidate>> =
            mapOf(
                "ㄋㄧˇ" to
                    listOf(
                        Candidate("你", 0.95f, CandidateSource.PRIMARY),
                        Candidate("妳", 0.40f, CandidateSource.PRIMARY),
                    ),
                "ㄏㄠˇ" to
                    listOf(
                        Candidate("好", 0.97f, CandidateSource.PRIMARY),
                        Candidate("号", 0.20f, CandidateSource.PRIMARY),
                    ),
                "ㄕˋ" to
                    listOf(
                        Candidate("是", 0.93f, CandidateSource.PRIMARY),
                        Candidate("事", 0.55f, CandidateSource.PRIMARY),
                        Candidate("示", 0.35f, CandidateSource.PRIMARY),
                    ),
                "ㄅㄨˋ" to
                    listOf(
                        Candidate("不", 0.96f, CandidateSource.PRIMARY),
                        Candidate("部", 0.50f, CandidateSource.PRIMARY),
                    ),
                "ㄇㄚ" to
                    listOf(
                        Candidate("媽", 0.80f, CandidateSource.PRIMARY),
                        Candidate("嗎", 0.78f, CandidateSource.PRIMARY),
                        Candidate("麻", 0.40f, CandidateSource.PRIMARY),
                    ),
            )
    }
}
