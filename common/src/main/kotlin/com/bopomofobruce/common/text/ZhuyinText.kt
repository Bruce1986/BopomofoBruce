package com.bopomofobruce.common.text

/**
 * 注音字串的正規化與結構檢查。
 *
 * **正規形**＝注音鍵盤（`keyboards/` 的 `Zhuyin.symbol`）與 native 解碼器（`decoder-native` 的 `bpmf.h`
 * `bpmf_input()`）共用的格式：
 * - 注音符號只用 U+3105 ㄅ–U+3129 ㄩ：聲母 U+3105–U+3119（ㄅ…ㄙ）、韻母 U+311A–U+3126（ㄚ…ㄦ）、 介音 U+3127–U+3129（ㄧㄨㄩ）。
 * - 聲調放在音節 **後面**：二聲 U+02CA ˊ、三聲 U+02C7 ˇ、四聲 U+02CB ˋ、輕聲 U+02D9 ˙。
 * - 一聲不標，以 ASCII 空白 U+0020 結束音節（大千配列的空白鍵＝一聲鍵）；字串最後一個一聲音節可省略空白。
 */
object ZhuyinText {
    private const val SPACE = ' '

    /** 二、三、四、輕聲（正規形）。 */
    // 碼位一律寫成 Int：ktfmt 會把 '\uXXXX' 改寫成字面字元，組合用附加符號（U+0301 等）因而無法辨識。
    private val TONE_MARKS = setOf(0x02CA, 0x02C7, 0x02CB, 0x02D9).map { it.toChar() }.toSet()

    /** 易混字元 → 正規形。只收「在注音字串裡只可能是這個意思」的對應；有爭議的（中點當輕聲、輕聲前置、字源相近的漢字如 丫／幺／又）刻意不收。 */
    val CONFUSABLES: Map<Char, Char> =
        mapOf(
                0x4E00 to 0x3127, // 一 CJK UNIFIED IDEOGRAPH-4E00 → ㄧ BOPOMOFO LETTER I
                0x2F00 to 0x3127, // ⼀ KANGXI RADICAL ONE → ㄧ
                0x02C9 to 0x0020, // ˉ MODIFIER LETTER MACRON（一聲符號）→ 空白（一聲結束音節）
                0x00B4 to 0x02CA, // ´ ACUTE ACCENT → ˊ MODIFIER LETTER ACUTE ACCENT
                0x0301 to 0x02CA, // COMBINING ACUTE ACCENT → ˊ
                0x030C to 0x02C7, // COMBINING CARON → ˇ CARON
                0x0060 to 0x02CB, // ` GRAVE ACCENT → ˋ MODIFIER LETTER GRAVE ACCENT
                0xFF40 to 0x02CB, // ｀ FULLWIDTH GRAVE ACCENT → ˋ
                0x0300 to 0x02CB, // COMBINING GRAVE ACCENT → ˋ
                0x0307 to 0x02D9, // COMBINING DOT ABOVE → ˙ DOT ABOVE
            )
            .entries
            .associate { (from, to) -> from.toChar() to to.toChar() }

    /**
     * 把注音字串轉成正規形。輸入應是「打算當注音」的字串——例如 `一` 一律被換成 `ㄧ`，不能拿來處理一般中文。
     * 1. 依 [CONFUSABLES] 逐字替換。
     * 2. 所有空白（`Char.isWhitespace()`，含 U+3000、U+00A0、Tab、換行）視為 U+0020。
     * 3. 刪掉對解碼器是 no-op 的空白：開頭、結尾、連續空白的第二個以後、緊接在聲調符號後的空白（見 `bpmf.h`：tone 2–5 之後或尚未打字時的空白是 true
     *    no-op）。一聲音節後的空白會保留，因為它是音節界線。
     *
     * 其他字元（非注音的中文、英文、Bopomofo Extended 等）原樣保留，交給 [isValidSequence] 判斷。
     *
     * 刻意 **不** 做 Unicode NFKC：NFKC 會把正規形的輕聲 U+02D9 DOT ABOVE 拆成 U+0020 + U+0307，把 U+00B4 拆成 U+0020
     * + U+0301（皆以 python `unicodedata` 實測）。
     */
    fun normalize(text: CharSequence): String {
        val out = StringBuilder(text.length)
        for (raw in text) {
            val c = CONFUSABLES[raw] ?: if (raw.isWhitespace()) SPACE else raw
            if (c == SPACE) {
                val prev = out.lastOrNull()
                if (prev == null || prev == SPACE || prev in TONE_MARKS) continue
            }
            out.append(c)
        }
        if (out.lastOrNull() == SPACE) out.setLength(out.length - 1)
        return out.toString()
    }

    /**
     * 檢查 [text] 是否為「由完整注音音節組成」的正規形序列（只看結構，不查字典）。
     *
     * 每個音節＝依序的〔聲母〕〔介音〕〔韻母〕至少一項，後接一個聲調符號、空白或字串結尾。因此會擋下 `bpmf.h` 列為
     * 「偵測不到、會回錯音節候選」的幾種輸入：兩個聲母相連（`ㄏㄋㄧˇ`）、一聲音節漏了空白（`ㄍㄨㄥㄖㄣˊ`）、 重複或開頭的聲調符號（`ㄋㄧˇˇ`、`ˇㄋㄧ`）。
     *
     * 不檢查的：音節是否存在於字典（`ㄏ` 單獨一個仍算合法結構）、聲韻搭配規則（如 ㄦ 不接聲母）、`bpmf.h` 的 39 字元長度上限。 空字串或只有空白回傳
     * `false`。非正規形字元（如 `一`、`´`）一律回傳 `false`，先呼叫 [normalize]。
     */
    fun isValidSequence(text: CharSequence): Boolean {
        var syllables = 0
        var rank = 0 // 0＝音節尚未開始，1＝已有聲母，2＝已有介音，3＝已有韻母
        for (c in text) {
            val r =
                when (c.code) {
                    in 0x3105..0x3119 -> 1 // 聲母 ㄅ…ㄙ
                    in 0x3127..0x3129 -> 2 // 介音 ㄧㄨㄩ
                    in 0x311A..0x3126 -> 3 // 韻母 ㄚ…ㄦ
                    else -> 0
                }
            when {
                r > 0 -> if (r > rank) rank = r else return false
                c in TONE_MARKS || c == SPACE -> {
                    if (rank > 0) syllables++ else if (c != SPACE) return false
                    rank = 0
                }
                else -> return false
            }
        }
        if (rank > 0) syllables++
        return syllables > 0
    }
}
