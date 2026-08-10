package com.bopomofobruce.keyboards

import com.bopomofobruce.common.KeyAction
import com.bopomofobruce.common.KeyData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * J1（round-11 審查）：pins every non-[KeyAction.Character] key's `label` to the exact [KeyAction] it
 * must carry, across all 8 bundled keyboards ([Keyboards.all]).
 *
 * Why this exists: `symbol_standard.json`'s control row previously had 「注音」→`language_toggle` and
 * 「ABC」→`symbol_toggle` — both backwards relative to [KeyAction]'s own KDoc ("switch to
 * English/digits" / "switch to the symbol keyboard"), so pressing either key sent the user to the
 * keyboard they were already looking at instead of the one the label promised. A presence-only or
 * per-keyboard-content test (see [OtherKeyboardsContentTest]) would not catch this class of bug,
 * because the wrong action is still a *valid*, *known* action — it's just wired to the wrong label.
 * This test instead pins a `label -> expected action` table and walks every reachable non-Character
 * key (short-press and, where present, long-press) on every keyboard against it.
 */
class ToggleKeyLabelActionConsistencyTest {

    /**
     * `label -> expected action` for every non-[KeyAction.Character] key label that appears
     * anywhere in [Keyboards.all]. Zhuyin symbol keys are included (label == the symbol itself,
     * both for short-press and the one long-press case, `ㄜ`'s longPress `ㄦ`) — the labels happen to
     * already be unambiguous per-symbol, but pinning them here means a future copy-paste of a
     * zhuyin action onto a mislabeled key is caught the same way as the control-row bug this test
     * was written for.
     */
    private val expected: Map<String, KeyAction> = buildMap {
        put("⌫", KeyAction.Backspace)
        put("　", KeyAction.Space)
        put("⏎", KeyAction.Enter)
        put("⇧", KeyAction.Shift)
        // password_qwerty / url_qwerty control row: see Keyboards.kt passwordQwerty KDoc (F1) --
        // symbol_toggle's only destination is symbolStandard, which is entirely full-width, so
        // "全形" is the label that matches where this action actually goes.
        put("全形", KeyAction.SymbolToggle)
        // symbol_standard control row (J1): "ABC" reads as "go to English/digits" and
        // language_toggle is documented as exactly that.
        put("ABC", KeyAction.LanguageToggle)
        // symbol_standard control row (J1): KeyAction has no built-in "go back to zhuyin"
        // variant (symbol_toggle/language_toggle only cover "leave symbols"/"leave
        // English"), so this uses the KeyAction.Custom escape hatch. :ime must implement this
        // id -- see Keyboards.kt symbolStandard KDoc for the full Custom-id checklist.
        put("注音", KeyAction.Custom("switch_to_zhuyin"))
        // zhuyin control row: already self-consistent with KeyAction's literal wording (going
        // *from* zhuyin, "符號" = leave to symbols, "英數" = leave to English/digits).
        put("符號", KeyAction.SymbolToggle)
        put("英數", KeyAction.LanguageToggle)
        put(".com", KeyAction.Custom("url_insert_dot_com"))
        for (symbol in
            listOf(
                "ㄅ",
                "ㄉ",
                "ˇ",
                "ˋ",
                "ㄓ",
                "ˊ",
                "˙",
                "ㄚ",
                "ㄞ",
                "ㄢ",
                "ㄆ",
                "ㄊ",
                "ㄍ",
                "ㄐ",
                "ㄔ",
                "ㄗ",
                "ㄧ",
                "ㄛ",
                "ㄟ",
                "ㄣ",
                "ㄇ",
                "ㄋ",
                "ㄎ",
                "ㄑ",
                "ㄕ",
                "ㄘ",
                "ㄨ",
                "ㄜ",
                "ㄠ",
                "ㄤ",
                "ㄈ",
                "ㄌ",
                "ㄏ",
                "ㄒ",
                "ㄖ",
                "ㄙ",
                "ㄩ",
                "ㄝ",
                "ㄡ",
                "ㄥ",
                "ㄦ",
            )) {
            put(symbol, KeyAction.Zhuyin(symbol))
        }
    }

    /** Every (label, action) pair reachable on [keyboard], short-press and long-press alike. */
    private fun labelActionPairs(
        keyboard: com.bopomofobruce.common.KeyboardDef
    ): List<Pair<String, KeyAction>> =
        keyboard.rows.flatten().flatMap { key: KeyData ->
            buildList {
                add(key.label to key.action)
                key.longPress?.let { add(it.label to it.action) }
            }
        }

    @Test
    fun `every non-Character key's label matches its documented action`() {
        val failures = mutableListOf<String>()
        for (keyboard in Keyboards.all) {
            for ((label, action) in labelActionPairs(keyboard)) {
                if (action is KeyAction.Character) continue
                val want = expected[label]
                if (want == null) {
                    failures.add(
                        "keyboard ${keyboard.id}: label '$label' (action=$action) has no entry in " +
                            "the expected label->action table -- add one so this test can pin it"
                    )
                } else if (action != want) {
                    failures.add(
                        "keyboard ${keyboard.id}: label '$label' expected action $want but found $action"
                    )
                }
            }
        }
        assertEquals(emptyList<String>(), failures, failures.joinToString("\n"))
    }
}
