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
    // 邊界寫成 const、在 when 裡用字面範圍：編譯器才會把 `in` 化成兩次整數比較，不經 IntRange.contains。
    private const val ASCII_VISIBLE_START = 0x0021
    private const val ASCII_VISIBLE_END = 0x007E
    private const val FULLWIDTH_VISIBLE_START = 0xFF01
    private const val FULLWIDTH_VISIBLE_END = 0xFF5E
    private const val OFFSET = 0xFEE0
    private const val SPACE = 0x0020.toChar()
    private const val IDEOGRAPHIC_SPACE = 0x3000.toChar()

    /** 全形 → 半形：U+FF01–U+FF5E → U+0021–U+007E、U+3000 → U+0020，其他原樣。 */
    fun toHalfwidth(text: CharSequence): String =
        buildString(text.length) {
            for (c in text) {
                val code = c.code
                append(
                    when {
                        code in FULLWIDTH_VISIBLE_START..FULLWIDTH_VISIBLE_END ->
                            (code - OFFSET).toChar()
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
                val code = c.code
                append(
                    when {
                        code in ASCII_VISIBLE_START..ASCII_VISIBLE_END -> (code + OFFSET).toChar()
                        c == SPACE -> IDEOGRAPHIC_SPACE
                        else -> c
                    }
                )
            }
        }
}
