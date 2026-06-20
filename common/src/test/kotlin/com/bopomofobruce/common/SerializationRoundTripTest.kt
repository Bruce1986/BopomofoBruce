package com.bopomofobruce.common

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 對所有 @Serializable 的 contract type 做 serialize → string → deserialize → assertEquals。
 *
 * 目的：W0-2 訂下來的 wire format 之後不能無聲打破。任何 serial name 改動會在這層先炸。
 */
class SerializationRoundTripTest {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    @Test
    fun `Candidate round trips`() {
        val original = Candidate(text = "你", score = 0.95f, source = CandidateSource.PRIMARY)

        val encoded = json.encodeToString(Candidate.serializer(), original)
        val decoded = json.decodeFromString(Candidate.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `KeyData with simple action round trips`() {
        val original =
            KeyData(label = "ㄅ", action = KeyAction.Zhuyin("ㄅ"), weight = 1f, longPressLabel = null)

        val encoded = json.encodeToString(KeyData.serializer(), original)
        val decoded = json.decodeFromString(KeyData.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `KeyData with long-press round trips`() {
        val original =
            KeyData(
                label = " ",
                action = KeyAction.Space,
                weight = 5f,
                longPressLabel = "符號",
                longPressAction = KeyAction.SymbolToggle,
            )

        val encoded = json.encodeToString(KeyData.serializer(), original)
        val decoded = json.decodeFromString(KeyData.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `KeyAction polymorphic encoding uses stable SerialName discriminator`() {
        val actions: List<KeyAction> =
            listOf(
                KeyAction.Character('a'),
                KeyAction.Zhuyin("ㄋ"),
                KeyAction.Backspace,
                KeyAction.Space,
                KeyAction.Enter,
                KeyAction.Shift,
                KeyAction.SymbolToggle,
                KeyAction.LanguageToggle,
                KeyAction.Custom("paste"),
            )

        for (action in actions) {
            val encoded = json.encodeToString(KeyAction.serializer(), action)
            val decoded = json.decodeFromString(KeyAction.serializer(), encoded)
            assertEquals(action, decoded, "round-trip failed for $action (json=$encoded)")
        }
    }

    @Test
    fun `KeyAction SerialName discriminator strings are the contracted stable ids`() {
        // 釘住 wire format：之後任何人改 @SerialName 都會在這裡炸。
        val expectedDiscriminators =
            mapOf(
                KeyAction.Character('x') to "character",
                KeyAction.Zhuyin("ㄋ") to "zhuyin",
                KeyAction.Backspace to "backspace",
                KeyAction.Space to "space",
                KeyAction.Enter to "enter",
                KeyAction.Shift to "shift",
                KeyAction.SymbolToggle to "symbol_toggle",
                KeyAction.LanguageToggle to "language_toggle",
                KeyAction.Custom("foo") to "custom",
            )

        for ((action, expected) in expectedDiscriminators) {
            val encoded = json.encodeToString(KeyAction.serializer(), action)
            assertTrue(
                encoded.contains("\"type\":\"$expected\""),
                "expected discriminator '$expected' in json=$encoded",
            )
        }
    }

    @Test
    fun `KeyboardColors and KeyboardDimens round trip`() {
        val colors =
            KeyboardColors(
                background = 0xFF1E1E1EL,
                keyFill = 0xFF2E2E2EL,
                keyText = 0xFFEAEAEAL,
                keyAccent = 0xFF4A90E2L,
                candidateText = 0xFFEAEAEAL,
                candidateHighlight = 0xFF4A90E2L,
            )
        val dimens =
            KeyboardDimens(
                keyHeightDp = 48f,
                rowGapDp = 4f,
                keyGapDp = 4f,
                candidateRowHeightDp = 40f,
            )

        val colorsRt =
            json.decodeFromString(
                KeyboardColors.serializer(),
                json.encodeToString(KeyboardColors.serializer(), colors),
            )
        val dimensRt =
            json.decodeFromString(
                KeyboardDimens.serializer(),
                json.encodeToString(KeyboardDimens.serializer(), dimens),
            )

        assertEquals(colors, colorsRt)
        assertEquals(dimens, dimensRt)
    }

    @Test
    fun `CandidateSource enum round trips for every value`() {
        for (source in CandidateSource.entries) {
            val candidate = Candidate("x", 0.5f, source)
            val rt =
                json.decodeFromString(
                    Candidate.serializer(),
                    json.encodeToString(Candidate.serializer(), candidate),
                )
            assertEquals(source, rt.source)
        }
    }
}
