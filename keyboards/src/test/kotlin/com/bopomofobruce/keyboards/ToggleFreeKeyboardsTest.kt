package com.bopomofobruce.keyboards

import com.bopomofobruce.common.KeyAction
import com.bopomofobruce.common.KeyboardDef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * L1（round-11 tracer 審查）：pins which keyboards in [Keyboards.all] are "terminal" — carry no
 * page-switch key ([KeyAction.SymbolToggle] / [KeyAction.LanguageToggle]) anywhere reachable
 * (short-press or long-press).
 *
 * Why this exists: `Keyboards.kt`'s KDoc for [Keyboards.numericStandard] and
 * [Keyboards.phoneDialpad] each independently claimed "本頁沒有切頁鍵，是唯一終端頁" (this page has no
 * page-switch key, it is *the* terminal page) — two "the only one"s in the same file, contradicting
 * each other — and [Keyboards.datetimeStandard], which is equally toggle-free, wasn't mentioned as
 * a terminal page at all. The KDoc has since been corrected to list all three and to point here so
 * the property doesn't have to be maintained by prose alone.
 */
class ToggleFreeKeyboardsTest {

    private fun isToggleFree(keyboard: KeyboardDef): Boolean =
        keyboard.rows.flatten().none { key ->
            val actions = listOfNotNull(key.action, key.longPress?.action)
            actions.any { it is KeyAction.SymbolToggle || it is KeyAction.LanguageToggle }
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
