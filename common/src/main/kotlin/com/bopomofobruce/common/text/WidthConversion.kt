package com.bopomofobruce.common.text

/**
 * 全形／半形轉換。
 *
 * 只處理下面兩組一對一對應，其他字元（含中文、注音、emoji、其他全形區字元）一律原樣保留：
 * - ASCII 可見字元 U+0021 `!`–U+007E `~` ↔ 全形 U+FF01 `！`–U+FF5E `～`（固定差 0xFEE0）。
 * - 空白 U+0020 SPACE ↔ U+3000 IDEOGRAPHIC SPACE。
 *
 * 刻意 **不** 轉換的邊界（避免「看起來像全形」就被改掉）：
 * - U+FF5F `｟` 以後的 Halfwidth and Fullwidth Forms（如 U+FFE0 FULLWIDTH CENT SIGN、半形片假名）—— 它們沒有對應的
 *   ASCII 可見字元，NFKC 也不會映到上面那段 ASCII。
 * - U+00A0 NO-BREAK SPACE、Tab、換行等其他空白——不是 U+0020，也不是 U+3000。
 *
 * 兩個方向在對應範圍內互為反函數：對任何字串 `s`，`toFullwidth(toHalfwidth(s))` 與 `toFullwidth(s)`
 * 相同，`toHalfwidth(toFullwidth(s))` 與 `toHalfwidth(s)` 相同。
 *
 * 以 UTF-16 code unit 逐一處理即安全：兩段對應範圍都在 BMP，surrogate（U+D800–U+DFFF）不落在其中，所以補充平面字元（如 emoji）不會被拆壞。
 */
object WidthConversion {
    // 碼位寫成 Int：ktfmt 會把 '\uXXXX' 改寫成字面字元，U+3000 因而看起來像普通空白。
    private val ASCII_VISIBLE = 0x0021..0x007E
    private val FULLWIDTH_VISIBLE = 0xFF01..0xFF5E
    private const val OFFSET = 0xFEE0
    private const val SPACE = 0x0020.toChar()
    private const val IDEOGRAPHIC_SPACE = 0x3000.toChar()

    /** 全形 → 半形：U+FF01–U+FF5E → U+0021–U+007E、U+3000 → U+0020，其他原樣。 */
    fun toHalfwidth(text: CharSequence): String =
        buildString(text.length) {
            for (c in text) {
                append(
                    when {
                        c.code in FULLWIDTH_VISIBLE -> (c.code - OFFSET).toChar()
                        c == IDEOGRAPHIC_SPACE -> SPACE
                        else -> c
                    }
                )
            }
        }

    /** 半形 → 全形：U+0021–U+007E → U+FF01–U+FF5E、U+0020 → U+3000，其他原樣。 */
    fun toFullwidth(text: CharSequence): String =
        buildString(text.length) {
            for (c in text) {
                append(
                    when {
                        c.code in ASCII_VISIBLE -> (c.code + OFFSET).toChar()
                        c == SPACE -> IDEOGRAPHIC_SPACE
                        else -> c
                    }
                )
            }
        }
}
