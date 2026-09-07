package com.bopomofobruce.keyboards

import com.bopomofobruce.common.KeyAction
import com.bopomofobruce.common.KeyboardDef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * M1（round-13 tracer 審查）：for every keyboard in [Keyboards.all], every page-switch key whose
 * destination is itself one of [Keyboards.all] must have *some* way back to the keyboard that sent
 * the user there — either a direct return edge, the keyboard-agnostic "go back to wherever I came
 * from" action ([KeyAction.Custom] id `"switch_back"`), or an explicitly documented gap in
 * [KNOWN_GAPS].
 *
 * Why this exists: J1 (round-11) fixed `symbol_standard`'s control-row label/action mismatch, but
 * didn't check the navigation graph one hop further. `password_qwerty` / `url_qwerty`'s only
 * page-switch key ("全形" -> [KeyAction.SymbolToggle] -> `symbol_standard`) landed the user on
 * `symbol_standard` with no way back: "注音" there is hardcoded to zhuyin (the wrong destination for
 * a password field) and "ABC" targets a keyboard that doesn't exist in [Keyboards.all] at all.
 * Every existing test in this module checks *content* (is the right character/label/action present)
 * — none of them would have caught "this destination is a dead end", because every individual key
 * on every individual keyboard was, in isolation, perfectly valid. M1 added a keyboard-agnostic
 * `"switch_back"` key to `symbol_standard` to close that dead end (see [Keyboards.symbolStandard]
 * KDoc); this test pins the *shape* of the fix so a future page-switch key edit can't silently
 * reopen this class of bug without either wiring a real return path or registering the gap here.
 *
 * **Scope note**: [KeyAction.LanguageToggle]'s destination (a generic ABC keyboard) isn't in
 * [Keyboards.all] at all — it's an already-registered W2-B follow-up (see
 * [Keyboards.symbolStandard] / zhuyin KDocs), not something this graph can evaluate a return path
 * for, so it's simply not tracked here (there is nothing in [Keyboards.all] to check a return path
 * against).
 */
class ReturnPathCoverageTest {

    companion object {
        /**
         * 已知、已登記的「有去無回」缺口：`來源鍵盤 id to 目的地鍵盤 id`。只有當這條邊確實無法修、 且已在 `Keyboards.kt` KDoc 登記為 follow-up
         * 時才可以加進來 —— 加一筆等於允許這條邊繼續 斷開，不要拿來當偷懶的手段。目前沒有任何一條登記在案（M1 已把已知的那條修掉了）。
         */
        val KNOWN_GAPS: Set<Pair<String, String>> = emptySet()

        /**
         * [Keyboards.all] 目前的鍵盤份數。只被下面那條哨兵測試使用——它的用途不是「數數」， 而是讓 class KDoc 的 Scope note
         * 在前提失效時發出聲音，見該測試的說明。
         */
        const val EXPECTED_CATALOG_SIZE = 8
    }

    /** [action] 若是「切到 [Keyboards.all] 裡某份具體鍵盤」的切頁鍵，回傳那些目的地 id；否則回傳空集合。 */
    private fun destinationsOf(action: KeyAction): List<String> =
        when {
            action is KeyAction.SymbolToggle -> listOf(Keyboards.symbolStandard.id)
            action is KeyAction.Custom && action.id == Keyboards.SWITCH_TO_ZHUYIN_CUSTOM_ID ->
                listOf(Keyboards.zhuyin4x10Portrait.id, Keyboards.zhuyin4x10Landscape.id)
            // language_toggle 的目的地不在 Keyboards.all 裡（見 class KDoc 的 Scope note），
            // switch_back 的目的地是動態的來源鍵盤、不是固定 id -- 兩者都不產生具體目的地。
            else -> emptyList()
        }

    /** [keyboard] 所有可觸及的動作，含短按與長按。 */
    private fun reachableActions(keyboard: KeyboardDef): List<KeyAction> =
        keyboard.rows.flatten().flatMap { key -> listOfNotNull(key.action, key.longPress?.action) }

    /**
     * [keyboard] 是否帶有通用的「返回」逃生鍵（可以回到任何來源，不需要知道具體目的地 id）。
     *
     * **同時要求它在 [Keyboards.PAGE_SWITCH_CUSTOM_IDS] 裡登記為切頁鍵。**早一版只比對 字面字串 `"switch_back"`，於是這個檔案與
     * `Keyboards.kt` 的分類清單是兩個各說各話的 來源：實測把它從 `PAGE_SWITCH_CUSTOM_IDS` 移到
     * `NON_PAGE_SWITCH_CUSTOM_IDS` （＝宣稱「這顆鍵不會帶你去別的鍵盤」），這裡照樣把它算成返回路徑，37 條測試全綠。
     * 那等於：資料層說它不切頁、導航圖說它切得回去，兩邊矛盾而沒有任何一條測試會紅。
     */
    private fun hasGenericBack(keyboard: KeyboardDef): Boolean =
        Keyboards.GENERIC_BACK_CUSTOM_ID in Keyboards.PAGE_SWITCH_CUSTOM_IDS &&
            reachableActions(keyboard).any {
                it is KeyAction.Custom && it.id == Keyboards.GENERIC_BACK_CUSTOM_ID
            }

    /** [keyboard] 是否有任何一條路（直接切頁鍵，或通用返回鍵）能回到 [targetId]。 */
    private fun hasReturnPathTo(keyboard: KeyboardDef, targetId: String): Boolean =
        hasGenericBack(keyboard) ||
            reachableActions(keyboard).any { targetId in destinationsOf(it) }

    @Test
    fun `the language_toggle scope note still has a premise to stand on`() {
        // class KDoc 的 Scope note 說 language_toggle 不必檢查，理由是「它的目的地不在
        // Keyboards.all 裡，沒有東西可以拿來比對」。這個理由**是暫時的**：W2-B 就會把通用 ABC
        // 鍵盤加進來。而 destinationsOf() 把這個暫時狀態寫成 `else -> emptyList()`——前提失效時
        // 它不會報錯，只會**靜靜地跳過** language_toggle 這條邊。
        //
        // 實測（2026-09-08，另開 probe worktree）：把一份完全沒有返回鍵的 `abc-generic` 加進
        // Keyboards.all，上面那條返回路徑測試**依然 0 failures**；紅的是 KeyboardLoaderTest 的
        // 兩條（目錄數量 8→9、id 命名清單）與 ToggleFreeKeyboardsTest 的終端頁那條——三條訊息
        // 都只說「目錄變了」，沒有一條會讓人回頭想到返回路徑。也就是說：加鍵盤的人確實會被擋，
        // 但擋他的訊息不會告訴他「有去無回」這一整類缺陷（round-13 才修掉的那個）正從
        // language_toggle 這條邊悄悄回來。這條哨兵就是為了補上那句話。
        assertEquals(
            EXPECTED_CATALOG_SIZE,
            Keyboards.all.size,
            "Keyboards.all 變動了。若新增的鍵盤是 KeyAction.LanguageToggle 的目的地（通用 ABC " +
                "鍵盤），請先把 LanguageToggle 接進本檔的 destinationsOf()，讓它的返回路徑真的被 " +
                "檢查，再更新 EXPECTED_CATALOG_SIZE；否則本檔的 Scope note 就變成一句沒有前提的話。",
        )
    }

    @Test
    fun `every reachable page-switch destination has a way back or a registered gap`() {
        val failures = mutableListOf<String>()
        val byId = Keyboards.all.associateBy { it.id }

        for (source in Keyboards.all) {
            for (action in reachableActions(source)) {
                for (destId in destinationsOf(action)) {
                    if (destId == source.id) continue // switching to yourself is a no-op
                    val gap = source.id to destId
                    if (gap in KNOWN_GAPS) continue
                    val dest = byId.getValue(destId)
                    if (!hasReturnPathTo(dest, source.id)) {
                        failures.add(
                            "keyboard '${source.id}' can switch to '${dest.id}' via $action, but " +
                                "'${dest.id}' has no way back to '${source.id}' (no direct edge, " +
                                "no generic switch_back, and (${source.id} to ${dest.id}) is not in " +
                                "ReturnPathCoverageTest.KNOWN_GAPS)"
                        )
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
