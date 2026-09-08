package com.bopomofobruce.keyboards

import com.bopomofobruce.common.KeyboardDef
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Reads [StaticKeyboardDef] JSON assets bundled under `keyboards/src/main/resources/keyboards/`.
 *
 * Deliberately **strict**: `ignoreUnknownKeys` stays at its kotlinx.serialization default
 * (`false`), so a typo'd field name in a hand-edited layout JSON fails loudly at load time instead
 * of silently dropping a key. Layout JSON is meant to be hand-editable (custom user layouts are a
 * stated project goal), so failing fast here is the whole point of "schema validation".
 */
object KeyboardLoader {
    private val json = Json { prettyPrint = false }

    /**
     * @param resourcePath classpath-relative path, e.g. `"keyboards/zhuyin_4x10_portrait.json"`.
     * @throws IllegalArgumentException if [resourcePath] isn't found on the classpath.
     * @throws SerializationException if the JSON doesn't match the [StaticKeyboardDef] schema.
     * @throws java.io.IOException if the resource stream can't be read (e.g. the classpath jar/apk
     *   is corrupt or truncated). Left as the raw exception rather than wrapped: it's a low-level
     *   I/O failure distinct from the schema/lookup failures above, callers that specifically want
     *   to handle "resource unreadable" shouldn't have to unwrap a custom exception type to get to
     *   it, and it's rare enough in practice (bundled classpath resources, not user-supplied files)
     *   that adding a wrapper type here isn't worth the extra API surface.
     */
    fun loadFromResource(resourcePath: String): KeyboardDef {
        val text =
            KeyboardLoader::class.java.classLoader?.getResourceAsStream(resourcePath)?.use { stream
                ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: throw IllegalArgumentException("Keyboard layout resource not found: $resourcePath")
        return decode(text)
    }

    /**
     * Parses raw JSON text through the same [json] instance [loadFromResource] uses.
     *
     * `internal` (not `private`) specifically so tests in this module can exercise the strict
     * `ignoreUnknownKeys=false` contract *through this class* instead of maintaining their own
     * separate `Json` instance — a separate test-owned `Json` would drift from this one silently
     * and end up testing nothing about [KeyboardLoader] itself.
     */
    internal fun decode(text: String): KeyboardDef =
        json.decodeFromString(StaticKeyboardDef.serializer(), text)
}
