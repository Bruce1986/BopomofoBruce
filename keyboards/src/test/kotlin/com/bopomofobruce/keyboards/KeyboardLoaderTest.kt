package com.bopomofobruce.keyboards

import java.io.File
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
    fun `every bundled layout JSON is reachable from the catalog and parses strictly`() {
        // 這個模組每一支測試都是走訪 [Keyboards.all]，也就是說它們檢查的是「目錄裡有的東西」；
        // 但真正被打包進 AAR/APK 的是 `resources/keyboards/` 底下**所有**的檔案。兩者不是同一個
        // 集合，而落差沒有任何測試在看。
        //
        // 實測（2026-09-08，probe worktree）：把一份 schema 不合法（多一個 `bogusField`）的
        // `abc_generic.json` 放進 resources 但**不**登記進 [Keyboards.all]，37 條測試全數通過。
        // 那個檔案會照樣被打包，而 [KeyboardLoader] 那個「刻意嚴格、typo 會在載入時大聲失敗」的
        // 契約對它完全沒有作用——因為從來沒有人載入過它。
        //
        // 這條同時守兩件事：檔案集合與目錄一致（新增配列卻忘了登記會紅），以及每一個被打包的
        // 檔案都真的通得過嚴格 schema（孤兒也逃不掉）。
        val dirUrl =
            requireNotNull(javaClass.classLoader.getResource("keyboards")) {
                "keyboards/ resource directory not found on the test classpath"
            }
        val bundled =
            requireNotNull(File(dirUrl.toURI()).listFiles()) {
                    "keyboards/ resource directory is not readable as a directory"
                }
                .filter { it.isFile && it.name.endsWith(".json") }
                .map { it.name }
                .toSortedSet()

        val expected =
            sortedSetOf(
                "datetime_standard.json",
                "numeric_standard.json",
                "password_qwerty.json",
                "phone_dialpad.json",
                "symbol_standard.json",
                "url_qwerty.json",
                "zhuyin_4x10_landscape.json",
                "zhuyin_4x10_portrait.json",
            )
        assertEquals(
            expected,
            bundled,
            "resources/keyboards/ 的檔案集合變了。新增一份配列時要一起登記進 Keyboards.all " +
                "（否則它會被打包但永遠載入不到，也不會有任何內容測試檢查它），再更新這份清單。",
        )

        // 每一個被打包的檔案都要通得過 KeyboardLoader 的嚴格 schema——包含還沒接進
        // Keyboards.all 的檔案。載入失敗會直接丟例外讓這條測試紅。
        for (name in bundled) {
            KeyboardLoader.loadFromResource("keyboards/$name")
        }
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
        //
        // Goes through KeyboardLoader.decode (the seam KeyboardLoader.loadFromResource itself
        // calls)
        // rather than a test-local Json instance, so this actually pins KeyboardLoader's own
        // ignoreUnknownKeys=false contract instead of a copy that could silently drift from it. See
        // C8 in docs/devlog/W1-C-keyboards-20260810-1531.md for why the previous version (a
        // test-owned `json` val, unrelated to KeyboardLoader) didn't prove anything about the
        // loader.
        val malformed =
            """
            {
              "id": "broken",
              "rows": [[{"label": "a", "action": {"type": "character", "char": "a"}, "bogusField": 1}]]
            }
            """
                .trimIndent()
        assertThrows(SerializationException::class.java) { KeyboardLoader.decode(malformed) }
    }

    @Test
    fun `strict schema validation rejects an unknown KeyAction discriminator`() {
        // Also routed through KeyboardLoader.decode — see comment above. Note: this failure comes
        // from the polymorphic KeyAction serializer failing to resolve an unknown "type"
        // discriminator,
        // which happens regardless of ignoreUnknownKeys; it is NOT itself evidence that
        // ignoreUnknownKeys=false is doing anything (only the "unknown field" test above is).
        val malformed =
            """
            {
              "id": "broken",
              "rows": [[{"label": "a", "action": {"type": "not_a_real_action"}}]]
            }
            """
                .trimIndent()
        assertThrows(SerializationException::class.java) { KeyboardLoader.decode(malformed) }
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
