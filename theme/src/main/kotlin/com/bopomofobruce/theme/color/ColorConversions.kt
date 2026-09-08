package com.bopomofobruce.theme.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 把 [com.bopomofobruce.common.KeyboardColors] 慣例的 ARGB [UInt]（高 8-bit alpha + RGB）轉成 Compose
 * [Color]。`Color(Int)` 建構子本來就是吃 `0xAARRGGBB` packed int，跟 wire format 定義一致，這裡只是補上型別轉換。
 */
fun UInt.toComposeColor(): Color = Color(this.toInt())

/** [toComposeColor] 的反向轉換，供需要把 Compose 產出的顏色（例如 Material You 動態色）存回 [UInt] 的情境用。 */
fun Color.toKeyboardUInt(): UInt = this.toArgb().toUInt()
