package com.bopomofobruce.keyboards

import com.bopomofobruce.common.KeyAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 「每一個 `Custom` id 都要登記在 [Keyboards.PAGE_SWITCH_CUSTOM_IDS] /
 * [Keyboards.NON_PAGE_SWITCH_CUSTOM_IDS] 其中一邊，而且只登記在一邊」——**這條性質的唯一守門**。
 *
 * ## 為什麼不能靠 `ToggleFreeKeyboardsTest` 的 throw
 *
 * `ToggleFreeKeyboardsTest.isPageSwitchAction` 對未登記的 id 會丟 `AssertionError`，KDoc 也宣稱
 * 那就是守門。**但它在真實鍵盤上幾乎不會被走到**：`isToggleFree` 是 `rows.flatten().none { … actions.any { … } }`，`none` 與
 * `any` **兩層都會短路**——只要那份鍵盤 上較早的某顆鍵已經是切頁鍵，走訪就停了，後面的鍵（包含未登記的 `Custom`）根本不會被送進 `isPageSwitchAction`。
 *
 * 實測（2026-09-08，round 3 tracer）攤平後的順序：
 * - `symbol_standard`：`返回`(index 30) → `注音`(31) → `ABC`(32)，共 36 鍵——**30 之後全部不檢查**
 * - `url_qwerty`：`全形`(28) → `.com`(32)，共 34 鍵——所以 `url_insert_dot_com` **從來沒被檢查過**
 *
 * 端到端重現：在 `symbol_standard` 加一顆 `半形` → `Custom("switch_to_halfwidth")`（排在既有控制鍵 之後，最自然的位置），再照
 * `ToggleKeyLabelActionConsistencyTest` 的失敗訊息把 `半形` 加進 label 表——**42 條全綠**，那顆真正的切頁鍵被靜默當成「不是切頁鍵」。那正是
 * M2 存在要防的誤判， 只是換成由短路造成。
 *
 * ## 為什麼也不能靠常數本身
 *
 * 三個 `const val` 與 JSON 裡的字面字串是**兩個獨立來源**。實測：把 `SWITCH_TO_ZHUYIN_CUSTOM_ID` 或
 * `URL_INSERT_DOT_COM_CUSTOM_ID` 改成錯字而 JSON 不動 → **42 條全綠**（IDE 的 rename symbol 就會造成這種單邊改動，而 `:ime`
 * 是依常數 dispatch 的， 結果是那顆鍵在執行期變成 no-op）。所以下面第二條要反向檢查：登記了的 id 必須真的有人用。
 *
 * 這個檔案刻意**逐一走訪、不短路**，也不依賴任何一份鍵盤的鍵位順序。
 */
class CustomIdRegistrationTest {

    /** [Keyboards.all] 裡所有可觸及（含長按）的 `Custom` id，逐一走訪、不短路。 */
    private fun customIdsInUse(): Map<String, List<String>> {
        val byId = mutableMapOf<String, MutableList<String>>()
        for (keyboard in Keyboards.all) {
            for (key in keyboard.rows.flatten()) {
                for (action in listOfNotNull(key.action, key.longPress?.action)) {
                    if (action is KeyAction.Custom) {
                        byId.getOrPut(action.id) { mutableListOf() }.add(keyboard.id)
                    }
                }
            }
        }
        return byId
    }

    @Test
    fun `every Custom id in use is registered in exactly one of the two sets`() {
        val unregistered = mutableListOf<String>()
        val inBoth = mutableListOf<String>()
        for ((id, keyboards) in customIdsInUse()) {
            val page = id in Keyboards.PAGE_SWITCH_CUSTOM_IDS
            val nonPage = id in Keyboards.NON_PAGE_SWITCH_CUSTOM_IDS
            if (!page && !nonPage) unregistered.add("$id（用於 $keyboards）")
            if (page && nonPage) inBoth.add(id)
        }
        assertTrue(
            unregistered.isEmpty(),
            "這些 Custom id 兩邊都沒登記，終端頁判定會把它們靜默當成『不是切頁鍵』：" +
                "$unregistered。請依它是否會帶使用者跳到另一份鍵盤，加進 " +
                "Keyboards.PAGE_SWITCH_CUSTOM_IDS 或 NON_PAGE_SWITCH_CUSTOM_IDS。",
        )
        assertTrue(inBoth.isEmpty(), "同一個 Custom id 不能同時登記在兩邊：$inBoth")
    }

    @Test
    fun `every registered Custom id is actually used by some keyboard`() {
        // 反向：擋住「常數改了、JSON 沒改」（IDE rename symbol 的典型單邊改動）。
        // 少了這條，把 SWITCH_TO_ZHUYIN_CUSTOM_ID 改成錯字會 42 條全綠，而 :ime 依常數
        // dispatch，那顆鍵在執行期直接變 no-op。
        val inUse = customIdsInUse().keys
        val registered = Keyboards.PAGE_SWITCH_CUSTOM_IDS + Keyboards.NON_PAGE_SWITCH_CUSTOM_IDS
        val orphaned = registered - inUse
        assertEquals(
            emptySet<String>(),
            orphaned,
            "這些 Custom id 登記了卻沒有任何鍵盤在用——常數與 JSON 字面值可能已經分岔" + "（或是這顆鍵被刪了卻沒清登記）。目前實際使用中的是 $inUse。",
        )
    }
}
