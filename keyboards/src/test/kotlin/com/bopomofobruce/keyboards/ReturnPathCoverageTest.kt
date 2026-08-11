package com.bopomofobruce.keyboards

import com.bopomofobruce.common.KeyAction
import com.bopomofobruce.common.KeyboardDef
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
    }

    /** [action] 若是「切到 [Keyboards.all] 裡某份具體鍵盤」的切頁鍵，回傳那些目的地 id；否則回傳空集合。 */
    private fun destinationsOf(action: KeyAction): List<String> =
        when {
            action is KeyAction.SymbolToggle -> listOf(Keyboards.symbolStandard.id)
            action is KeyAction.Custom && action.id == "switch_to_zhuyin" ->
                listOf(Keyboards.zhuyin4x10Portrait.id, Keyboards.zhuyin4x10Landscape.id)
            // language_toggle 的目的地不在 Keyboards.all 裡（見 class KDoc 的 Scope note），
            // switch_back 的目的地是動態的來源鍵盤、不是固定 id -- 兩者都不產生具體目的地。
            else -> emptyList()
        }

    /** [keyboard] 所有可觸及的動作，含短按與長按。 */
    private fun reachableActions(keyboard: KeyboardDef): List<KeyAction> =
        keyboard.rows.flatten().flatMap { key -> listOfNotNull(key.action, key.longPress?.action) }

    /** [keyboard] 是否帶有通用的「返回」逃生鍵（可以回到任何來源，不需要知道具體目的地 id）。 */
    private fun hasGenericBack(keyboard: KeyboardDef): Boolean =
        reachableActions(keyboard).any { it is KeyAction.Custom && it.id == "switch_back" }

    /** [keyboard] 是否有任何一條路（直接切頁鍵，或通用返回鍵）能回到 [targetId]。 */
    private fun hasReturnPathTo(keyboard: KeyboardDef, targetId: String): Boolean =
        hasGenericBack(keyboard) ||
            reachableActions(keyboard).any { targetId in destinationsOf(it) }

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
