package com.bopomofobruce.theme.color

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ColorConversionsTest {

    @Test
    fun `UInt to Color to UInt round trips for opaque color`() {
        val original = 0xFF4A90E2u

        val roundTripped = original.toComposeColor().toKeyboardUInt()

        assertEquals(original, roundTripped)
    }

    @Test
    fun `UInt to Color to UInt round trips for translucent color`() {
        val original = 0x804A90E2u

        val roundTripped = original.toComposeColor().toKeyboardUInt()

        assertEquals(original, roundTripped)
    }

    @Test
    fun `UInt to Color to UInt round trips for black and white`() {
        assertEquals(0xFF000000u, 0xFF000000u.toComposeColor().toKeyboardUInt())
        assertEquals(0xFFFFFFFFu, 0xFFFFFFFFu.toComposeColor().toKeyboardUInt())
    }
}
