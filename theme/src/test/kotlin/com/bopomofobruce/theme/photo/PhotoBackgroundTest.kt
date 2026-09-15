package com.bopomofobruce.theme.photo

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PhotoBackgroundTest {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    @Test
    fun `round trips with defaults`() {
        val original = PhotoBackground(uri = "content://media/external/images/42")

        val encoded = json.encodeToString(PhotoBackground.serializer(), original)
        val decoded = json.decodeFromString(PhotoBackground.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `round trips with blur, opacity and tint set`() {
        val original =
            PhotoBackground(
                uri = "content://media/external/images/42",
                blurRadiusDp = 12f,
                opacity = 0.6f,
                tint = 0x804A90E2u,
            )

        val encoded = json.encodeToString(PhotoBackground.serializer(), original)
        val decoded = json.decodeFromString(PhotoBackground.serializer(), encoded)

        assertEquals(original, decoded)
        assert(encoded.contains("\"tint\":\"0x804A90E2\"")) {
            "expected UIntHexSerializer wire format for tint, got: $encoded"
        }
    }

    @Test
    fun `null tint round trips`() {
        val original = PhotoBackground(uri = "content://media/external/images/42", tint = null)

        val decoded =
            json.decodeFromString(
                PhotoBackground.serializer(),
                json.encodeToString(PhotoBackground.serializer(), original),
            )

        assertEquals(original, decoded)
    }

    @Test
    fun `rejects blank uri`() {
        assertThrows(IllegalArgumentException::class.java) { PhotoBackground(uri = "") }
    }

    @Test
    fun `rejects network, file, scheme-less and upper-case scheme uris`() {
        for (uri in
            listOf(
                "https://example.com/a.png",
                "http://example.com/a.png",
                "file:///data/data/com.bopomofobruce/files/a.png",
                "media/42",
                // Coil 只認小寫 scheme：放行的話建構成功、渲染時卻悄悄載不出來。
                "CONTENT://media/external/images/42",
            )) {
            assertThrows(IllegalArgumentException::class.java, { PhotoBackground(uri = uri) }, uri)
        }
    }

    @Test
    fun `accepts the local schemes coil can actually load`() {
        for (uri in
            listOf(
                "content://media/external/images/42",
                "android.resource://com.bopomofobruce/drawable/bg",
            )) {
            assertEquals(uri, PhotoBackground(uri = uri).uri)
        }
    }

    @Test
    fun `decoding a theme json with a network uri is rejected`() {
        val hostile = """{"uri":"https://example.com/pixel.png"}"""

        assertThrows(IllegalArgumentException::class.java) {
            json.decodeFromString(PhotoBackground.serializer(), hostile)
        }
    }

    @Test
    fun `rejection message does not leak the full uri`() {
        val secret = "https://example.com/users/alice/private.png"

        val error =
            assertThrows(IllegalArgumentException::class.java) { PhotoBackground(uri = secret) }

        assert(!error.message.orEmpty().contains("alice")) { "例外訊息帶出了完整 uri：${error.message}" }
    }

    @Test
    fun `rejects negative blur radius`() {
        assertThrows(IllegalArgumentException::class.java) {
            PhotoBackground(uri = "content://x", blurRadiusDp = -1f)
        }
    }

    @Test
    fun `rejects blur radius above max`() {
        assertThrows(IllegalArgumentException::class.java) {
            PhotoBackground(
                uri = "content://x",
                blurRadiusDp = PhotoBackground.MAX_BLUR_RADIUS_DP + 0.1f,
            )
        }
    }

    @Test
    fun `accepts blur radius at max`() {
        val background =
            PhotoBackground(uri = "content://x", blurRadiusDp = PhotoBackground.MAX_BLUR_RADIUS_DP)
        assertEquals(PhotoBackground.MAX_BLUR_RADIUS_DP, background.blurRadiusDp)
    }

    @Test
    fun `rejects non-finite blur radius`() {
        assertThrows(IllegalArgumentException::class.java) {
            PhotoBackground(uri = "content://x", blurRadiusDp = Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoBackground(uri = "content://x", blurRadiusDp = Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `rejects out-of-range opacity`() {
        assertThrows(IllegalArgumentException::class.java) {
            PhotoBackground(uri = "content://x", opacity = 1.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoBackground(uri = "content://x", opacity = -0.1f)
        }
    }
}
