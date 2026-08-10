package com.bopomofobruce.keyboards

import com.bopomofobruce.common.KeyAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Content sanity checks for the non-zhuyin keyboards (symbol / numeric / password / phone / url /
 * datetime).
 */
class OtherKeyboardsContentTest {

    private fun labelsOf(rows: List<List<com.bopomofobruce.common.KeyData>>): Set<String> =
        rows.flatten().map { it.label }.toSet()

    @Test
    fun `symbol keyboard contains the required 繁中全形標點`() {
        val required = setOf("，", "。", "、", "；", "：", "「", "」", "『", "』")
        val labels = labelsOf(Keyboards.symbolStandard.rows)
        for (punct in required) {
            assertTrue(punct in labels, "symbol keyboard missing required punctuation: $punct")
        }
    }

    @Test
    fun `symbol keyboard characters are all full-width (not ASCII half-width duplicates)`() {
        // A half-width "," slipping in instead of "，" would defeat the point of a 全形標點 keyboard.
        // Walks *all* rows (not just the first 3) so a future page-2 / 半形切換 row inserted anywhere
        // in the layout can't silently smuggle an ASCII half-width character past this check.
        for (row in Keyboards.symbolStandard.rows) {
            for (key in row) {
                val action = key.action
                if (action is KeyAction.Character) {
                    assertTrue(
                        action.char.code > 127,
                        "expected full-width punctuation, got ASCII '${action.char}'",
                    )
                }
            }
        }
    }

    @Test
    fun `numeric keyboard has digits 0-9 exactly once each plus a decimal point`() {
        val digitActions =
            Keyboards.numericStandard.rows.flatten().mapNotNull { key ->
                (key.action as? KeyAction.Character)?.char
            }
        for (d in '0'..'9') {
            assertEquals(1, digitActions.count { it == d }, "digit '$d' should appear exactly once")
        }
        assertTrue('.' in digitActions, "numeric keyboard should have a decimal point")
    }

    /**
     * Every [KeyAction.Character] reachable from a keyboard, counting both the short-press [action]
     * and (if present) the long-press [com.bopomofobruce.common.KeyData.longPress] action. Used to
     * check "can a user actually type this character on this keyboard", which a short-press-only
     * scan would under-report once keys start carrying a `longPress`.
     */
    private fun reachableChars(rows: List<List<com.bopomofobruce.common.KeyData>>): List<Char> =
        rows.flatten().flatMap { key ->
            listOfNotNull(
                (key.action as? KeyAction.Character)?.char,
                (key.longPress?.action as? KeyAction.Character)?.char,
            )
        }

    @Test
    fun `password and url keyboards can type every digit 0-9 and stay pure ASCII`() {
        // C9: password_qwerty and url_qwerty previously had zero digits reachable (short-press or
        // long-press), so a numeric password or a numeric URL segment couldn't be typed at all.
        // Both
        // keyboards now carry a longPress digit (Gboard convention: q-p -> 1234567890) on their top
        // row. This also guards against a stray full-width character sneaking in and being sent to
        // a
        // server expecting ASCII.
        for (keyboard in listOf(Keyboards.passwordQwerty, Keyboards.urlQwerty)) {
            val chars = reachableChars(keyboard.rows)
            for (d in '0'..'9') {
                assertTrue(
                    d in chars,
                    "keyboard ${keyboard.id} cannot type digit '$d' (short or long press)",
                )
            }
            for (c in chars) {
                assertTrue(
                    c.code <= 127,
                    "keyboard ${keyboard.id} has non-ASCII reachable character '$c'",
                )
            }
        }
    }

    @Test
    fun `password keyboard covers all 26 lowercase letters`() {
        val letters =
            Keyboards.passwordQwerty.rows.flatten().mapNotNull { key ->
                (key.action as? KeyAction.Character)?.char
            }
        for (c in 'a'..'z') {
            assertTrue(c in letters, "password keyboard missing letter '$c'")
        }
    }

    @Test
    fun `password and url keyboards share identical qwerty letter rows`() {
        // password_qwerty.json and url_qwerty.json's first three rows (q-p, a-l, z-m plus
        // shift/backspace) are copy-pasted verbatim from one another. Nothing else pins them
        // together, so a future edit to one without the other would silently diverge. Compare the
        // full per-key (label, action, weight) tuple, not just the letters, so a weight/shift/
        // backspace edit on only one side is also caught.
        // take(3) is intentional here (unlike the full-width check above): rows 0-2 are defined as
        // *the* letter rows shared between these two keyboards; row 3 (the control row)
        // legitimately
        // differs between password and url layouts and must stay excluded from this comparison.
        val passwordLetterRows = Keyboards.passwordQwerty.rows.take(3)
        val urlLetterRows = Keyboards.urlQwerty.rows.take(3)
        assertEquals(
            passwordLetterRows,
            urlLetterRows,
            "password_qwerty and url_qwerty letter rows have diverged",
        )
    }

    @Test
    fun `phone dialpad has 0-9, star and pound`() {
        val chars =
            Keyboards.phoneDialpad.rows.flatten().mapNotNull { key ->
                (key.action as? KeyAction.Character)?.char
            }
        for (d in '0'..'9') {
            assertTrue(d in chars, "phone dialpad missing digit '$d'")
        }
        assertTrue('*' in chars && '#' in chars, "phone dialpad missing * or #")
    }

    @Test
    fun `url keyboard has slash, dot and a dedicated dot-com custom action`() {
        val allKeys = Keyboards.urlQwerty.rows.flatten()
        val chars = allKeys.mapNotNull { (it.action as? KeyAction.Character)?.char }
        assertTrue('/' in chars && '.' in chars, "url keyboard missing '/' or '.'")

        val customActions = allKeys.mapNotNull { it.action as? KeyAction.Custom }
        assertTrue(
            customActions.any { it.id == "url_insert_dot_com" },
            "url keyboard missing the documented url_insert_dot_com custom action",
        )
    }

    @Test
    fun `datetime keyboard has digits and the date-time separators`() {
        val chars =
            Keyboards.datetimeStandard.rows.flatten().mapNotNull { key ->
                (key.action as? KeyAction.Character)?.char
            }
        for (d in '0'..'9') {
            assertTrue(d in chars, "datetime keyboard missing digit '$d'")
        }
        for (sep in listOf('/', ':', '-')) {
            assertTrue(sep in chars, "datetime keyboard missing separator '$sep'")
        }
    }

    @Test
    fun `no keyboard has a duplicate Character key`() {
        // Catches copy-paste mistakes (e.g. password_qwerty / url_qwerty share their first three
        // rows verbatim) that a presence-only assertion (`c in chars`) would miss: a duplicated key
        // still contains every required character, just also contains an extra copy of one.
        for (keyboard in Keyboards.all) {
            val charActions =
                keyboard.rows.flatten().mapNotNull { key ->
                    (key.action as? KeyAction.Character)?.char
                }
            val duplicates = charActions.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertTrue(
                duplicates.isEmpty(),
                "keyboard ${keyboard.id} has duplicate Character key(s): $duplicates",
            )
        }
    }

    @Test
    fun `no keyboard is empty`() {
        for (keyboard in Keyboards.all) {
            assertTrue(keyboard.rows.isNotEmpty(), "keyboard ${keyboard.id} has zero rows")
            assertTrue(
                keyboard.rows.all { it.isNotEmpty() },
                "keyboard ${keyboard.id} has an empty row",
            )
        }
    }
}
