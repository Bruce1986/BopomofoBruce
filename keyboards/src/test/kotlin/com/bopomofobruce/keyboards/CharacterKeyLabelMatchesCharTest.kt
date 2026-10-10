package com.bopomofobruce.keyboards

import com.bopomofobruce.common.KeyAction
import com.bopomofobruce.common.KeyData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * L3（round-11 tracer 審查）：for every [KeyAction.Character] reachable on any keyboard in
 * [Keyboards.all] — short-press or [KeyData.longPress] alike — the visible [KeyData.label] (or
 * [com.bopomofobruce.common.LongPressData.label]) must equal the character actually sent.
 *
 * Why this exists: C9/D1/D2 hand-wrote 52 `longPress: { label, action.char }` pairs (10 digits + 16
 * symbols x 2 files, on `password_qwerty.json` / `url_qwerty.json`) and no test asserted label/char
 * agreement. Every other test in this module only checks "is this char reachable" — none of them
 * would catch a typo like `{"label":"5","action":{"char":"6"}}`: the reachable-char test still
 * finds '6' present, the pure-ASCII test still passes, the duplicate-char test still passes (a
 * different key just lost its own '6'), and even the password/url letter-row-equality test passes
 * because the same typo is copy-pasted into both files. In a masked password field a user pressing
 * the key labelled "5" and silently sending "6" would never notice. This test closes that gap
 * directly.
 */
class CharacterKeyLabelMatchesCharTest {

    @Test
    fun `every Character key's label matches the character it sends`() {
        val failures = mutableListOf<String>()
        for (keyboard in Keyboards.all) {
            for (row in keyboard.rows) {
                for (key: KeyData in row) {
                    val action = key.action
                    if (action is KeyAction.Character && key.label != action.char.toString()) {
                        failures.add(
                            "keyboard ${keyboard.id}: short-press label '${key.label}' does not match " +
                                "action char '${action.char}'"
                        )
                    }
                    val longPress = key.longPress
                    val longAction = longPress?.action
                    if (
                        longAction is KeyAction.Character &&
                            longPress.label != longAction.char.toString()
                    ) {
                        failures.add(
                            "keyboard ${keyboard.id}: longPress label '${longPress.label}' does not " +
                                "match action char '${longAction.char}'"
                        )
                    }
                }
            }
        }
        assertEquals(emptyList<String>(), failures, failures.joinToString("\n"))
    }
}
