package com.bopomofobruce.common.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 把 [UInt] 序列化成 8-digit hex string，例如 `"0xFFFFFFFF"`。
 *
 * 用於 [com.bopomofobruce.common.KeyboardColors] 的 ARGB 欄位，讓自訂主題 JSON 人類可讀／可手編。Wire 範例：
 *
 * ```json
 * { "background": "0xFF1E1E1E", "keyFill": "0xFF2E2E2E", ... }
 * ```
 *
 * Decode 寬容兩種前綴：`0x` 與 `#`（CSS-style），方便人類手寫。6-digit hex（無 alpha） 視為完全不透明（自動補 `FF` 高位），符合 CSS 顏色慣例。
 */
object UIntHexSerializer : KSerializer<UInt> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.bopomofobruce.common.UIntHex", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UInt) {
        encoder.encodeString("0x${value.toString(16).padStart(8, '0').uppercase()}")
    }

    override fun deserialize(decoder: Decoder): UInt {
        val raw = decoder.decodeString().trim()
        val hex =
            when {
                raw.startsWith("0x", ignoreCase = true) -> raw.substring(2)
                raw.startsWith("#") -> raw.substring(1)
                else -> raw
            }
        require(hex.length == 6 || hex.length == 8) {
            "Hex value must be either 6 digits (RGB) or 8 digits (ARGB), but was: $raw"
        }
        val parsed = hex.toUInt(16)
        // 6-digit hex 視為 RGB（無 alpha），自動補 FF 高位完全不透明。
        return if (hex.length == 6) parsed or 0xFF000000u else parsed
    }
}
