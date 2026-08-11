package com.bopomofobruce.keyboards

import com.bopomofobruce.common.KeyAction
import com.bopomofobruce.common.KeyboardDef
import org.junit.jupiter.api.Assertions.assertEquals
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
}
