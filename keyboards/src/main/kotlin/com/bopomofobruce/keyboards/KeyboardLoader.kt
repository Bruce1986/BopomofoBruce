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
     */
    fun loadFromResource(resourcePath: String): KeyboardDef {
        val text =
            KeyboardLoader::class.java.classLoader?.getResourceAsStream(resourcePath)?.use { stream
                ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: throw IllegalArgumentException("Keyboard layout resource not found: $resourcePath")
        return json.decodeFromString(StaticKeyboardDef.serializer(), text)
    }
}
