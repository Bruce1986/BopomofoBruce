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
 * Thread-safe：no（同 [ZhuyinDecoder]）。
 */
class FakeImeContextProvider(initialInputType: ImeInputType = ImeInputType.TEXT) :
    ImeContextProvider {

    override var currentInputType: ImeInputType = initialInputType

    private val committed: StringBuilder = StringBuilder()

    /** Test 查詢「目前 input field 累積了什麼字」。 */
    fun getCommittedText(): String = committed.toString()

    /** [deleteSurroundingText] 每次呼叫記錄 `(beforeLength, afterLength)`。 */
    val deleteCalls: MutableList<Pair<Int, Int>> = mutableListOf()

    /** [sendKey] 收到的 keyCode 序列。 */
    val sentKeys: MutableList<Int> = mutableListOf()

    override fun commitText(text: String) {
        committed.append(text)
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) {
        deleteCalls.add(beforeLength to afterLength)
        // 模擬刪除 committed buffer 的 beforeLength 個字元（只支援 BMP，preview/test 足夠）。
        // afterLength 在 fake 裡沒語意（沒有真實 cursor 概念）。
        val drop = beforeLength.coerceAtLeast(0).coerceAtMost(committed.length)
        committed.delete(committed.length - drop, committed.length)
    }

    override fun sendKey(keyCode: Int) {
        sentKeys.add(keyCode)
    }

    /** 清空所有狀態，方便 test 之間共用同一個 instance。 */
    fun clearAll() {
        committed.clear()
        deleteCalls.clear()
        sentKeys.clear()
    }
}
