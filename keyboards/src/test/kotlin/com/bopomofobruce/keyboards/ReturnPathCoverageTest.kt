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
    fun `every registered page-switch custom id has a destination in destinationsOf`() {
        // destinationsOf() 是一個寫死的 when，但「哪些 Custom id 算切頁鍵」的權威來源是
        // Keyboards.PAGE_SWITCH_CUSTOM_IDS。兩邊沒有任何機制互相核對：在清單裡登記一個新的
        // 切頁 id、卻忘了在 destinationsOf() 加分支，那條邊就會被 `else -> emptyList()` 靜靜
        // 吞掉，回程檢查完全看不到它——即使它指向的是一份真正的死路終端頁。
        //
        // 實測（2026-09-13）：登記一個新的切頁 id、在符號鍵盤掛一顆指向 phone_dialpad
        // （目前確定沒有任何切頁鍵的終端頁之一）的鍵，一切都照「正確流程」做，
        // BUILD SUCCESSFUL、ReturnPathCoverageTest 2 tests / 0 failures——這條真實的導航
        // 死路沒有任何一條測試攔得下來。
        //
        // 這與 M2、round-13、以及 09-08 那輪的 custom id 分類是同一種病：規則本身不完備，
        // 靠現有鍵盤組合湊巧遮住。這條把「登記」與「有目的地」綁在一起，讓它出不了門。
        //
        // GENERIC_BACK 是唯一的例外，理由寫在 destinationsOf() 裡：它的目的地是動態的來源
        // 鍵盤，本來就不是固定 id。例外只有這一個，多一個都要在這裡顯式排除、並說明理由。
        val needsDestination = Keyboards.PAGE_SWITCH_CUSTOM_IDS - Keyboards.GENERIC_BACK_CUSTOM_ID
        for (id in needsDestination) {
            val destinations = destinationsOf(KeyAction.Custom(id))
            assertTrue(
                destinations.isNotEmpty(),
                "`$id` 登記在 PAGE_SWITCH_CUSTOM_IDS 裡，但 destinationsOf() 給不出目的地——" +
                    "它切到的那份鍵盤不會被返回路徑檢查看到。三種可能：" +
                    "①目的地是 Keyboards.all 裡的某份鍵盤 → 在 destinationsOf() 補上對應分支；" +
                    "②目的地是動態的來源鍵盤（像 ${Keyboards.GENERIC_BACK_CUSTOM_ID}）→ " +
                    "在本測試顯式排除並寫明理由；" +
                    "③目的地是固定的、但那份鍵盤還不在 Keyboards.all 裡（像 language_toggle 的通用 " +
                    "ABC 頁、W2-D 的 emoji 頁）→ 比照 class KDoc 的 Scope note 排除並寫明，" +
                    "連同那份鍵盤何時會進 Keyboards.all。",
            )
            for (destinationId in destinations) {
                assertTrue(
                    Keyboards.all.any { it.id == destinationId },
                    "`$id` 的目的地 `$destinationId` 不在 Keyboards.all 裡——" +
                        "destinationsOf() 指向了一份不存在的鍵盤。",
                )
            }
        }
    }

    @Test
    fun `every custom id that destinationsOf resolves is registered as a page switch`() {
        // 上面那條是「清單 → 導航圖」，這條是**反方向**：導航圖認得、卻被登記在
        // NON_PAGE_SWITCH_CUSTOM_IDS（＝資料層宣稱「這顆鍵不會帶你去別的鍵盤」）的 id。
        //
        // 實測（2026-09-13）：新增一個切到 symbolStandard 的 Custom id、在 destinationsOf()
        // 補好分支、在 datetime 掛一顆鍵，但**登記錯邊**——BUILD SUCCESSFUL、47 tests 全綠。
        // 同一顆鍵改登記到對邊才會紅（ToggleFreeKeyboardsTest 的終端頁那條）。也就是說
        // 整套測試的反應完全由「登記在哪一邊」決定，而錯邊那側是靜音的：datetime 真的多了
        // 一顆會換頁的鍵、導航圖也同意，只有資料層說它不是——Keyboards 的「終端頁是這三份」
        // KDoc 就此變成錯的，沒有任何一條測試紅。
        //
        // ToggleFreeKeyboardsTest 對「登記錯邊」的既有防線是三條寫死的 per-id 斷言，
        // 對**新增的** id 結構性無效；這條用走訪取代列舉，才擋得住還沒被寫進去的那些。
        for (keyboard in Keyboards.all) {
            for (action in reachableActions(keyboard)) {
                if (action !is KeyAction.Custom) continue
                if (destinationsOf(action).isEmpty()) continue
                assertTrue(
                    action.id in Keyboards.PAGE_SWITCH_CUSTOM_IDS,
                    "`${action.id}`（用在 ${keyboard.id}）在 destinationsOf() 裡切得到具體鍵盤，" +
                        "卻沒有登記在 PAGE_SWITCH_CUSTOM_IDS。資料層會把它當成不換頁的鍵，" +
                        "於是它所在的鍵盤可能被誤判成終端頁。",
                )
                assertTrue(
                    action.id !in Keyboards.NON_PAGE_SWITCH_CUSTOM_IDS,
                    "`${action.id}`（用在 ${keyboard.id}）切得到具體鍵盤，卻登記在 " +
                        "NON_PAGE_SWITCH_CUSTOM_IDS——資料層與導航圖對「這顆鍵會不會換頁」" +
                        "說法相反。",
                )
            }
        }
    }

    @Test
    fun `a generic back key only sits on keyboards something can switch to`() {
        // 排除 GENERIC_BACK 不做目的地檢查（見上一條）是對的——它的目的地是動態的來源鍵盤，
        // destinationsOf() 只吃 KeyAction、沒有「這顆鍵在哪份鍵盤上」的脈絡，算不出來。
        // 但排除之後就沒有任何一條測試再對它問過任何事，於是一整類缺陷會無聲落地：
        // 把「返回」掛在一份**誰都切不到**的鍵盤上，使用者是靠 InputType 直接進來的，
        // :ime 根本沒有來源狀態可回，那顆鍵在真機上是 no-op。
        //
        // 實測（2026-09-13）：在 url_qwerty 加一顆 switch_back——url_qwerty 不是任何
        // destinationsOf() 的目的地，所以第三條測試根本不會走訪它；它本來也不是終端頁，
        // 所以 ToggleFreeKeyboardsTest 也不受影響。結果 BUILD SUCCESSFUL、47 tests 全綠。
        //
        // 換個問題問，就能在不需要脈絡的情況下守住它：帶返回鍵的鍵盤必須有人切得到。
        val destinationIds =
            Keyboards.all.flatMap { reachableActions(it) }.flatMap { destinationsOf(it) }.toSet()
        for (keyboard in Keyboards.all) {
            val hasBack =
                reachableActions(keyboard).any {
                    it is KeyAction.Custom && it.id == Keyboards.GENERIC_BACK_CUSTOM_ID
                }
            if (!hasBack) continue
            assertTrue(
                keyboard.id in destinationIds,
                "${keyboard.id} 掛了 `${Keyboards.GENERIC_BACK_CUSTOM_ID}`，但沒有任何鍵盤切得到它" +
                    "（目前的目的地集合：$destinationIds）。使用者只能靠 InputType 直接進到這一頁，" +
                    ":ime 沒有來源狀態可回，那顆鍵在真機上會是 no-op。" +
                    "要嘛拿掉它，要嘛補上切進這一頁的路。",
            )
        }
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
