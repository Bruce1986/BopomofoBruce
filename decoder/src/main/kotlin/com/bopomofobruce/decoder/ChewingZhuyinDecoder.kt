package com.bopomofobruce.decoder

import com.bopomofobruce.common.Candidate
import com.bopomofobruce.common.CandidateSource
import com.bopomofobruce.common.ZhuyinDecoder
import java.io.Closeable

/**
 * 以 libchewing（經 `libbpmf.so`）實作的 [ZhuyinDecoder]。由 [DecoderModule.create] 建立。
 *
 * 生命週期：建構時取得一個 native handle，[close] 釋放它。由建立者（:app）持有並負責 [close]；照 ADR-0004 由 `BpmfApplication` 當
 * singleton 持有時，process 存活期間不 close。:ime 只透過 [ZhuyinDecoder] 使用、不要 close（service
 * 重建時拿回的是同一個實例）。[close] 可重複呼叫，只會釋放一次。關閉後 [input] 回空 list、[commit]／[reset] 是 no-op，不會碰 native。
 *
 * Thread-safety：同 [ZhuyinDecoder]，不 thread-safe、不加鎖；所有呼叫（含 [close]）要在同一個 dispatcher 上
 * serialise。實例會跨 service 重建存活，若 dispatcher 隨 service 建立／取消，換用新的之前要確定舊的上面已沒有進行中的呼叫（取消 coroutine
 * 不會中斷正在跑的 native 呼叫）。
 *
 * 候選分數：libchewing 只給順序、不給分數，第 i 個（0 起算）給 `1 / (i + 1)`，保持嚴格遞減且落在 `(0, 1]`。這是排序用的名次，不是機率。
 *
 * @throws IllegalStateException `bpmf_init()` 失敗（[dataPath] 下缺 `word.dat`／`tsi.dat` 等，見 `bpmf.h`）。
 */
class ChewingZhuyinDecoder internal constructor(private val native: BpmfNative, dataPath: String) :
    ZhuyinDecoder, Closeable {

    private var handle: Long = native.init(dataPath)

    /** 最近一次 [input] 的候選，與各自在 `bpmf_input()` 結果裡的 index。 */
    private var lastCandidates: List<Pair<Candidate, Int>> = emptyList()

    init {
        check(handle != 0L) { "bpmf_init() failed for chewing data path: $dataPath" }
    }

    override fun input(buffer: String): List<Candidate> {
        if (handle == 0L) return emptyList()
        val texts = splitCandidates(native.input(handle, buffer))
        lastCandidates =
            texts
                .withIndex()
                .filter { it.value.isNotEmpty() }
                .mapIndexed { rank, (nativeIndex, text) ->
                    Candidate(text, 1f / (rank + 1), CandidateSource.PRIMARY) to nativeIndex
                }
        return lastCandidates.map { it.first }
    }

    /**
     * 把 [candidate] 對應的 index 交給 `bpmf_commit()`。不是最近一次 [input] 回傳的候選（例如個人字典來的、或已被下一次 [input] 取代）時是
     * no-op。`bpmf.h` 記載 commit 目前在 C API 上觀察不到效果，這裡照樣呼叫，讓 libchewing 的狀態與使用者的選擇一致。
     */
    override fun commit(candidate: Candidate) {
        if (handle == 0L) return
        val nativeIndex = lastCandidates.firstOrNull { it.first == candidate }?.second ?: return
        native.commit(handle, nativeIndex)
    }

    /** 清掉候選與 native 端的 composition（以空 buffer 呼叫 `bpmf_input()`，它一開始就會 reset）。 */
    override fun reset() {
        lastCandidates = emptyList()
        if (handle == 0L) return
        native.input(handle, "")
    }

    override fun close() {
        if (handle == 0L) return
        val toFree = handle
        handle = 0L
        lastCandidates = emptyList()
        native.free(toFree)
    }
}

/** `bpmf_input()` 的輸出是以 `'\n'` 串接的標準 UTF-8；空陣列代表 0 個候選。 */
internal fun splitCandidates(bytes: ByteArray): List<String> =
    if (bytes.isEmpty()) emptyList() else String(bytes, Charsets.UTF_8).split('\n')
