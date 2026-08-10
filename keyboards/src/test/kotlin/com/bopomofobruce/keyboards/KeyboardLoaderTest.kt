package com.bopomofobruce.keyboards

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Round-trip + schema-strictness coverage for every bundled [StaticKeyboardDef] JSON asset.
 *
 * Per-keyboard content (exact key mapping) is pinned separately in [ZhuyinLayoutContentTest] and
 * [OtherKeyboardsContentTest] — this file only exercises the loader/serialization machinery.
 */
class KeyboardLoaderTest {

    private val json = Json { prettyPrint = false }

    @Test
    fun `every catalog keyboard loads without throwing`() {
        // Keyboards.all itself invoking KeyboardLoader.loadFromResource for all 8 assets is the
        // assertion: if any resource path is wrong or any JSON fails schema validation, this
        // throws.
        assertEquals(8, Keyboards.all.size)
    }

    @Test
    fun `every catalog keyboard round trips through StaticKeyboardDef serializer`() {
        for (keyboard in Keyboards.all) {
            val original = StaticKeyboardDef(keyboard.id, keyboard.rows)
            val encoded = json.encodeToString(StaticKeyboardDef.serializer(), original)
            val decoded = json.decodeFromString(StaticKeyboardDef.serializer(), encoded)
            assertEquals(original, decoded, "round-trip failed for keyboard id=${keyboard.id}")
        }
    }

    @Test
    fun `all catalog keyboard ids are unique`() {
        val ids = Keyboards.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate keyboard id among: $ids")
    }

    @Test
    fun `ids follow the layout-family-shape-orientation naming convention`() {
        // com.bopomofobruce.common.KeyboardDef's kdoc states the id convention explicitly.
        val expected =
            setOf(
                "zhuyin-4x10-portrait",
                "zhuyin-4x10-landscape",
                "symbol-standard-page1",
                "numeric-standard",
                "password-qwerty",
                "phone-dialpad",
                "url-qwerty",
                "datetime-standard",
            )
        assertEquals(expected, Keyboards.all.map { it.id }.toSet())
    }

    @Test
    fun `loading a missing resource throws IllegalArgumentException`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                KeyboardLoader.loadFromResource("keyboards/does_not_exist.json")
            }
        assertTrue(ex.message.orEmpty().contains("does_not_exist.json"))
    }

    @Test
    fun `strict schema validation actually rejects an unknown field`() {
        // Proves the "strict schema validation" acceptance criterion can fail, not just pass:
        // a typo'd/unexpected field must be rejected, not silently ignored.
        val malformed =
            """
            {
              "id": "broken",
              "rows": [[{"label": "a", "action": {"type": "character", "char": "a"}, "bogusField": 1}]]
            }
            """
                .trimIndent()
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(StaticKeyboardDef.serializer(), malformed)
        }
    }

    @Test
    fun `strict schema validation rejects an unknown KeyAction discriminator`() {
        val malformed =
            """
            {
              "id": "broken",
              "rows": [[{"label": "a", "action": {"type": "not_a_real_action"}}]]
            }
            """
                .trimIndent()
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(StaticKeyboardDef.serializer(), malformed)
        }
    }

    @Test
    fun `every key on every catalog keyboard has a positive finite weight`() {
        // KeyData's own init{} block enforces this at construction time (see :common), and every
        // decode path (including KeyboardLoader.loadFromResource) goes through that constructor, so
        // Keyboards.all can never contain a non-positive/non-finite weight. What we can actually
        // verify is that decoding JSON with a bad weight fails loudly rather than silently — the
        // two cases below.
        for (keyboard in Keyboards.all) {
            for (row in keyboard.rows) {
                for (key in row) {
                    assertTrue(
                        key.weight > 0f && key.weight.isFinite(),
                        "non-positive/non-finite weight on keyboard=${keyboard.id} key=${key.label}",
                    )
                }
            }
        }
    }

    @Test
    fun `decoding a key with zero weight throws`() {
        val malformed =
            """
            {
              "id": "broken",
              "rows": [[{"label": "a", "action": {"type": "character", "char": "a"}, "weight": 0.0}]]
            }
            """
                .trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            json.decodeFromString(StaticKeyboardDef.serializer(), malformed)
        }
    }

    @Test
    fun `decoding a key with negative weight throws`() {
        val malformed =
            """
            {
              "id": "broken",
              "rows": [[{"label": "a", "action": {"type": "character", "char": "a"}, "weight": -1.0}]]
            }
            """
                .trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            json.decodeFromString(StaticKeyboardDef.serializer(), malformed)
        }
    }
}
