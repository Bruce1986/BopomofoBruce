package com.bopomofobruce.theme.photo

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

        val thrown =
            assertThrows(IllegalArgumentException::class.java) {
                json.decodeFromString(PhotoBackground.serializer(), hostile)
            }

        // SerializationException 本身繼承 IllegalArgumentException，只斷言 IAE 對「逃出來的是不是裸 IAE」恆真
        // （同 StyleSheetValidationTest 的 O2）。KDoc 的呼叫端契約靠這條鎖住。
        assertFalse(
            thrown is SerializationException,
            "expected a bare IllegalArgumentException, got ${thrown::class.qualifiedName}",
        )
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

    /**
     * `opacity in 0f..1f` 對 NaN 是 false；改寫成 `!(opacity < 0f || opacity > 1f)` 這類等價外觀的寫法就會放行 NaN。
     */
    @Test
    fun `rejects non-finite opacity`() {
        assertThrows(IllegalArgumentException::class.java) {
            PhotoBackground(uri = "content://x", opacity = Float.NaN)
        }
    }

    /** 白名單常數那條只釘 [PhotoBackground.LOCAL_URI_SCHEMES] 的內容；這條釘 `init` 真的只認那個集合。 */
    @Test
    fun `rejects schemes outside the allowlist that coil or the platform could resolve`() {
        for (uri in listOf("data:image/png;base64,AAAA", "blob:x", "ftp://example.com/a.png")) {
            assertThrows(IllegalArgumentException::class.java, { PhotoBackground(uri = uri) }, uri)
        }
    }

    /** KDoc 寫的範圍是 `0f（完全透明）..1f`；先前只測了兩個拒絕端，`opacity > 0f` 這種突變不會紅。 */
    @Test
    fun `accepts both ends of the opacity range`() {
        assertEquals(0f, PhotoBackground(uri = "content://x", opacity = 0f).opacity)
        assertEquals(1f, PhotoBackground(uri = "content://x", opacity = 1f).opacity)
    }

    /**
     * 白名單整組釘死。上面的拒絕案例只列得出「已經想到要擋的」scheme（http／https／file），多收一個沒列到的 （實測：加進 `"data"`）不會紅。這是 ADR-0005
     * 純本地的邊界，要放寬就該連這條一起改、留下紀錄。這條只釘常數的內容；`init` 是否只認這個集合，由下面拒絕 `data:`／`blob:`／`ftp:` 那條守。
     */
    @Test
    fun `the local scheme allowlist is exactly content and android resource`() {
        assertEquals(setOf("content", "android.resource"), PhotoBackground.LOCAL_URI_SCHEMES)
    }
}
