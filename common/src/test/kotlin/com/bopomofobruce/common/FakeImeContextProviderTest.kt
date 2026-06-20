package com.bopomofobruce.common

import com.bopomofobruce.common.fakes.FakeImeContextProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakeImeContextProviderTest {

    @Test
    fun `commitText accumulates and getCommittedText reflects all calls`() {
        val ime = FakeImeContextProvider()

        ime.commitText("你")
        ime.commitText("好")
        ime.commitText("世界")

        assertEquals("你好世界", ime.getCommittedText())
    }

    @Test
    fun `deleteSurroundingText trims the tail of committed buffer and records the call`() {
        val ime = FakeImeContextProvider()
        ime.commitText("你好世界")

        ime.deleteSurroundingText(beforeLength = 2, afterLength = 0)

        assertEquals("你好", ime.getCommittedText())
        assertEquals(listOf(2 to 0), ime.deleteCalls)
    }

    @Test
    fun `sendKey records all keycodes in order`() {
        val ime = FakeImeContextProvider()

        ime.sendKey(66) // KEYCODE_ENTER
        ime.sendKey(67) // KEYCODE_DEL

        assertEquals(listOf(66, 67), ime.sentKeys)
    }

    @Test
    fun `currentInputType is mutable for switching field simulation`() {
        val ime = FakeImeContextProvider(initialInputType = ImeInputType.TEXT)
        assertEquals(ImeInputType.TEXT, ime.currentInputType)

        ime.currentInputType = ImeInputType.PASSWORD

        assertEquals(ImeInputType.PASSWORD, ime.currentInputType)
    }

    @Test
    fun `clearAll resets every recording surface and currentInputType`() {
        val ime = FakeImeContextProvider(initialInputType = ImeInputType.TEXT)
        ime.currentInputType = ImeInputType.PASSWORD
        ime.commitText("abc")
        ime.deleteSurroundingText(1, 0)
        ime.sendKey(66)

        ime.clearAll()

        assertEquals(ImeInputType.TEXT, ime.currentInputType)
        assertTrue(ime.getCommittedText().isEmpty())
        assertTrue(ime.deleteCalls.isEmpty())
        assertTrue(ime.sentKeys.isEmpty())
    }
}
