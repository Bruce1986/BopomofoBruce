package com.bopomofobruce.keyboards

import com.bopomofobruce.common.KeyAction
import com.bopomofobruce.common.KeyData
import com.bopomofobruce.common.KeyboardDef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L1（round-11 tracer 審查）：pins which keyboards in [Keyboards.all] are "terminal" — carry no
 * page-switch key ([KeyAction.SymbolToggle] / [KeyAction.LanguageToggle], or a [KeyAction.Custom]
 * registered in [Keyboards.PAGE_SWITCH_CUSTOM_IDS]) anywhere reachable (short-press or long-press).
 *
 * Why this exists: `Keyboards.kt`'s KDoc for [Keyboards.numericStandard] and
 * [Keyboards.phoneDialpad] each independently claimed "本頁沒有切頁鍵，是唯一終端頁" (this page has no
 * page-switch key, it is *the* terminal page) — two "the only one"s in the same file, contradicting
 * each other — and [Keyboards.datetimeStandard], which is equally toggle-free, wasn't mentioned as
 * a terminal page at all. The KDoc has since been corrected to list all three and to point here so
 * the property doesn't have to be maintained by prose alone.
 *
 * M2（round-13 tracer 審查）：the original rule here only checked the two built-in
 * [KeyAction.SymbolToggle] / [KeyAction.LanguageToggle] variants, even though J1 (round-11) had
 * already established that [KeyAction.Custom] can also be a page-switch key (`"switch_to_zhuyin"`).
 * It happened not to matter before because [Keyboards.symbolStandard] also carries a
 * `language_toggle` key, so it was never at risk of being misclassified as terminal — but the rule
 * itself was silently incomplete. Fixed to consult [Keyboards.PAGE_SWITCH_CUSTOM_IDS] /
 * [Keyboards.NON_PAGE_SWITCH_CUSTOM_IDS], and to fail loudly (not silently pass) on any `Custom` id
 * registered in neither set, mirroring how [ToggleKeyLabelActionConsistencyTest] treats an
 * unregistered label.
 */
class ToggleFreeKeyboardsTest {

    /** Whether [action] switches the user to a different keyboard/page. */
    private fun isPageSwitchAction(action: KeyAction): Boolean =
        when {
            action is KeyAction.SymbolToggle || action is KeyAction.LanguageToggle -> true
            action is KeyAction.Custom && action.id in Keyboards.PAGE_SWITCH_CUSTOM_IDS -> true
            action is KeyAction.Custom && action.id in Keyboards.NON_PAGE_SWITCH_CUSTOM_IDS -> false
            action is KeyAction.Custom ->
                throw AssertionError(
                    "unregistered Custom id '${action.id}' -- add it to " +
                        "Keyboards.PAGE_SWITCH_CUSTOM_IDS or Keyboards.NON_PAGE_SWITCH_CUSTOM_IDS " +
                        "depending on whether it switches pages, so terminal-page detection can " +
                        "account for it"
                )
            else -> false
        }

    private fun isToggleFree(keyboard: KeyboardDef): Boolean =
        keyboard.rows.flatten().none { key ->
            val actions = listOfNotNull(key.action, key.longPress?.action)
            actions.any { isPageSwitchAction(it) }
        }

    @Test
    fun `exactly numeric, phone dialpad and datetime keyboards are toggle-free`() {
        val expectedToggleFreeIds =
            setOf(
                Keyboards.numericStandard.id,
                Keyboards.phoneDialpad.id,
                Keyboards.datetimeStandard.id,
            )

        val actualToggleFreeIds = Keyboards.all.filter { isToggleFree(it) }.map { it.id }.toSet()

        assertEquals(
            expectedToggleFreeIds,
            actualToggleFreeIds,
            "toggle-free (terminal) keyboards changed -- update Keyboards.kt KDoc to match",
        )
    }

    @Test
    fun `the two Custom id sets are a clean partition of the ids actually used`() {
        // 兩份清單的分類**本身**要有守門。原本只有「兩邊都沒登記」會失敗（見
        // isPageSwitchAction 的 throw），但「登記了、卻登記錯邊」完全沒人看：實測
        // （2026-09-08）把 GENERIC_BACK_CUSTOM_ID 從 PAGE_SWITCH 移到 NON_PAGE_SWITCH，
        // 37 條測試全數通過——因為 symbolStandard 另外還掛著 switch_to_zhuyin 與
        // language_toggle，少算一顆不改變它的終端頁判定。
        val overlap =
            Keyboards.PAGE_SWITCH_CUSTOM_IDS intersect Keyboards.NON_PAGE_SWITCH_CUSTOM_IDS
        assertEquals(emptySet<String>(), overlap, "同一個 Custom id 不能同時登記在兩邊")

        // 這三顆的分類是資料層與導航圖共同依賴的事實，不能靠「目前剛好沒出事」維持。
        assertTrue(
            Keyboards.GENERIC_BACK_CUSTOM_ID in Keyboards.PAGE_SWITCH_CUSTOM_IDS,
            "『返回』是切頁鍵：它會把使用者帶回來源鍵盤。登記成非切頁鍵會讓 " + "ReturnPathCoverageTest 與資料層對『這顆鍵會不會換頁』說法相反。",
        )
        assertTrue(
            Keyboards.SWITCH_TO_ZHUYIN_CUSTOM_ID in Keyboards.PAGE_SWITCH_CUSTOM_IDS,
            "『注音』是切頁鍵",
        )
        assertTrue(
            Keyboards.URL_INSERT_DOT_COM_CUSTOM_ID in Keyboards.NON_PAGE_SWITCH_CUSTOM_IDS,
            "『.com』插入文字，不是切頁鍵",
        )
    }

    @Test
    fun `a keyboard whose only page-switch key is a Custom id is not counted as terminal`() {
        // 用合成鍵盤釘住**規則**，不依賴 Keyboards.all 目前剛好長什麼樣子。M2 修的正是
        // 「規則不完備、但被現有鍵盤組合湊巧遮住」——symbolStandard 另外掛了 language_toggle，
        // 所以就算完全不看 Custom 也不會出錯。等到真的出現「唯一的切頁鍵是 Custom」的鍵盤
        // （switch_back 就是設計成給任何鍵盤重用的通用逃生口）才會爆，那時已經太遲。
        val onlyCustomSwitch =
            StaticKeyboardDef(
                id = "synthetic-custom-only",
                rows =
                    listOf(
                        listOf(
                            KeyData("a", KeyAction.Character('a')),
                            KeyData("返回", KeyAction.Custom(Keyboards.GENERIC_BACK_CUSTOM_ID)),
                        )
                    ),
            )
        assertFalse(isToggleFree(onlyCustomSwitch), "唯一的切頁鍵是 Custom 時，這份鍵盤仍然不是終端頁")

        val genuinelyTerminal =
            StaticKeyboardDef(
                id = "synthetic-terminal",
                rows =
                    listOf(
                        listOf(
                            KeyData("a", KeyAction.Character('a')),
                            KeyData(
                                ".com",
                                KeyAction.Custom(Keyboards.URL_INSERT_DOT_COM_CUSTOM_ID),
                            ),
                        )
                    ),
            )
        assertTrue(isToggleFree(genuinelyTerminal), "只掛非切頁的 Custom（插入文字）時，這份鍵盤確實是終端頁")
    }
}
