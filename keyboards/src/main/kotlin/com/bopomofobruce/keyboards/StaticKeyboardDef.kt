package com.bopomofobruce.keyboards

import com.bopomofobruce.common.KeyData
import com.bopomofobruce.common.KeyboardDef
import kotlinx.serialization.Serializable

/**
 * JSON-backed implementation of [KeyboardDef].
 *
 * `:common` freezes the [KeyboardDef] interface itself (contracts-v1); this data class lives in
 * `:keyboards` because a plain interface can't be deserialized directly by kotlinx.serialization —
 * we need one concrete `@Serializable` shape to decode into. [KeyData] (and everything it
 * transitively references — [com.bopomofobruce.common.KeyAction],
 * [com.bopomofobruce.common.LongPressData]) is already `@Serializable` in `:common`, so this class
 * only has to describe its own two fields.
 */
@Serializable
data class StaticKeyboardDef(override val id: String, override val rows: List<List<KeyData>>) :
    KeyboardDef
