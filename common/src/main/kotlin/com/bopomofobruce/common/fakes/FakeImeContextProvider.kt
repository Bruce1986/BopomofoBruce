package com.bopomofobruce.common.fakes

import com.bopomofobruce.common.ImeContextProvider
import com.bopomofobruce.common.ImeInputType

/**
 * 給 **unit test 與 Compose preview** 用的 [ImeContextProvider]。
 *
 * **不要** 在 production 依賴 — 沒有真的接 Android InputConnection，commitText 只是塞進 internal StringBuilder。
 *
 * 行為：
 * - [commitText] 累加到 [getCommittedText] 回傳的 buffer。
 * - [deleteSurroundingText] / [sendKey] 也記錄到對應 list 供 test assert。
 * - [currentInputType] 設為 `var` 讓 test 可以模擬切 input field。
 *
 * Thread-safe：no（同 [com.bopomofobruce.common.ZhuyinDecoder]）。
 */
class FakeImeContextProvider(initialInputType: ImeInputType = ImeInputType.TEXT) :
    ImeContextProvider {

    override var currentInputType: ImeInputType = initialInputType

    private val committed: StringBuilder = StringBuilder()

    /** Test 查詢「目前 input field 累積了什麼字」。 */
    fun getCommittedText(): String = committed.toString()

    private val _deleteCalls: MutableList<Pair<Int, Int>> = mutableListOf()

    /** [deleteSurroundingText] 每次呼叫記錄 `(beforeLength, afterLength)`。對外唯讀。 */
    val deleteCalls: List<Pair<Int, Int>>
        get() = _deleteCalls

    private val _sentKeys: MutableList<Int> = mutableListOf()

    /** [sendKey] 收到的 keyCode 序列。對外唯讀。 */
    val sentKeys: List<Int>
        get() = _sentKeys

    override fun commitText(text: String) {
        committed.append(text)
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) {
        // 與 Android InputConnection.deleteSurroundingText 契約一致：non-negative。
        require(beforeLength >= 0) { "beforeLength must be non-negative, but was $beforeLength" }
        require(afterLength >= 0) { "afterLength must be non-negative, but was $afterLength" }
        _deleteCalls.add(beforeLength to afterLength)
        // 模擬刪除 committed buffer 的 beforeLength 個字元（只支援 BMP，preview/test 足夠）。
        // afterLength 在 fake 裡沒語意（沒有真實 cursor 概念）。
        val drop = beforeLength.coerceAtMost(committed.length)
        committed.delete(committed.length - drop, committed.length)
    }

    override fun sendKey(keyCode: Int) {
        _sentKeys.add(keyCode)
    }

    /** 清空所有狀態，方便 test 之間共用同一個 instance。 */
    fun clearAll() {
        committed.clear()
        _deleteCalls.clear()
        _sentKeys.clear()
    }
}
