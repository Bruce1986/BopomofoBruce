package com.bopomofobruce.common.text

/**
 * 注音字串的正規化與結構檢查。
 *
 * **正規形**＝注音鍵盤（`keyboards/` 配列 JSON 裡 [com.bopomofobruce.common.KeyAction.Zhuyin] 的 `symbol`）與
 * native 解碼器（`bpmf.h` 的 `bpmf_input()`）共用的格式：
 * - 注音符號只用 U+3105 ㄅ–U+3129 ㄩ：聲母 U+3105–U+3119（ㄅ…ㄙ）、韻母 U+311A–U+3126（ㄚ…ㄦ）、介音 U+3127–U+3129（ㄧㄨㄩ）。
 * - 聲調放在音節 **後面**：二聲 U+02CA ˊ、三聲 U+02C7 ˇ、四聲 U+02CB ˋ、輕聲 U+02D9 ˙。
 * - 一聲不標，以 ASCII 空白 U+0020 結束音節（大千配列的空白鍵＝一聲鍵）；字串最後一個一聲音節可省略空白。
 *
 * 出處：`bpmf.h` 在 W1-A 分支 `feat/w1a-decoder-native` 的 `decoder-native/cmake/include/bpmf.h`，下文引用的
 * `word.csv` 在同分支 libchewing submodule 的 `data/dict/chewing/word.csv`；W1-A 合併前 main 上都找不到。
 *
 * 刻意未處理、待 owner 裁決（動手前先問）：中點（· ‧ ・）當輕聲、輕聲前置（`˙ㄇㄚ`）、字源相近的漢字（丫／幺／又）、零寬字元（U+200B 等）。這些字元目前 [normalize]
 * 原樣保留；中點、字源相近的漢字、零寬字元讓 [isValidSequence] 回 `false`，但輕聲前置只有 `˙` 在開頭、空白或聲調後才回
 * `false`——接在一聲音節後（前置寫法的「媽媽」`ㄇㄚ˙ㄇㄚ`）與後置寫法 `ㄇㄚ˙ ㄇㄚ` 正規化後相同，會回 `true`。
 */
object ZhuyinText {
    private const val SPACE = ' '

    /** 二、三、四、輕聲（正規形）。 */
    // 碼位一律寫成 Int：ktfmt 會把 '\uXXXX' 改寫成字面字元，組合用附加符號（U+0301 等）因而無法辨識。
    // 寫成函式直接比碼位：逐字呼叫時不裝箱、不做雜湊查找。
    private fun isToneMark(c: Char): Boolean {
        val code = c.code
        return code == 0x02CA || code == 0x02C7 || code == 0x02CB || code == 0x02D9
    }

    /** 易混字元 → 正規形。只收「在注音字串裡只可能是這個意思」的對應；有爭議的（中點當輕聲、輕聲前置、字源相近的漢字如 丫／幺／又）刻意不收。 */
    val CONFUSABLES: Map<Char, Char> =
        mapOf(
            0x4E00.toChar() to
                0x3127.toChar(), // 一 CJK UNIFIED IDEOGRAPH-4E00 → ㄧ BOPOMOFO LETTER I
            0x2F00.toChar() to 0x3127.toChar(), // ⼀ KANGXI RADICAL ONE → ㄧ
            0x02C9.toChar() to 0x0020.toChar(), // ˉ MODIFIER LETTER MACRON（一聲符號）→ 空白（一聲結束音節）
            0x00B4.toChar() to 0x02CA.toChar(), // ´ ACUTE ACCENT → ˊ MODIFIER LETTER ACUTE ACCENT
            0x0301.toChar() to 0x02CA.toChar(), // COMBINING ACUTE ACCENT → ˊ
            0x030C.toChar() to 0x02C7.toChar(), // COMBINING CARON → ˇ CARON
            0x0060.toChar() to 0x02CB.toChar(), // ` GRAVE ACCENT → ˋ MODIFIER LETTER GRAVE ACCENT
            0xFF40.toChar() to 0x02CB.toChar(), // ｀ FULLWIDTH GRAVE ACCENT → ˋ
            0x0300.toChar() to 0x02CB.toChar(), // COMBINING GRAVE ACCENT → ˋ
            0x0307.toChar() to 0x02D9.toChar(), // COMBINING DOT ABOVE → ˙ DOT ABOVE
        )

    /**
     * 把注音字串轉成正規形。輸入應是「打算當注音」的字串——例如 `一` 一律被換成 `ㄧ`，不能拿來處理一般中文。
     * 1. 依 [CONFUSABLES] 逐字替換。
     * 2. 所有空白（`Char.isWhitespace()`，含 U+3000、U+00A0、Tab、換行）視為 U+0020。
     * 3. 刪掉多餘空白：開頭、連續空白的第二個以後、緊接在聲調符號後的空白（見 `bpmf.h`：tone 2–5 之後或尚未打字時的空白是 true
     *    no-op）。一聲音節後的空白是音節界線，會保留——**字串最後那一個除外**：`bpmf.h` 允許緩衝最後一個一聲音節省略空白，且 `bpmf_input()` 每次都先
     *    `chewing_Reset()`、只解碼當次送進來的整串，所以刪掉它不改變該次的解碼結果。
     *
     * 但輸出**不能再接續拼接**，也不能存回去當累積用的 composing buffer：`normalize("ㄍㄨㄥ ") + "ㄖㄣˊ"` 是 `ㄍㄨㄥㄖㄣˊ`，解碼器會讀成
     * ㄖㄨㄣˊ。呼叫端應保存原始輸入，每次送 [com.bopomofobruce.common.ZhuyinDecoder.input] 前對原始輸入整串重新呼叫（逐鍵呼叫也可以）。
     *
     * 其他字元（非注音的中文、英文、Bopomofo Extended 等）原樣保留，交給 [isValidSequence] 判斷。
     *
     * 刻意 **不** 做 Unicode NFKC：NFKC 會把正規形的輕聲 U+02D9 DOT ABOVE 拆成 U+0020 加 U+0307，把 U+00B4 拆成 U+0020
     * 加 U+0301（皆以 python `unicodedata` 實測）。
     */
    fun normalize(text: CharSequence): String {
        val out = StringBuilder(text.length)
        for (raw in text) {
            val c = CONFUSABLES[raw] ?: if (raw.isWhitespace()) SPACE else raw
            if (c == SPACE) {
                val prev = out.lastOrNull()
                if (prev == null || prev == SPACE || isToneMark(prev)) continue
            }
            out.append(c)
        }
        if (out.lastOrNull() == SPACE) out.setLength(out.length - 1)
        return out.toString()
    }

    /**
     * 檢查 [text] 是否為「由完整注音音節組成」的正規形序列（只看結構，不查字典）。
     *
     * 每個音節＝依序的〔聲母〕〔介音〕〔韻母〕至少一項，後接一個聲調符號、空白或字串結尾。因此會擋下 `bpmf.h`
     * 列為「偵測不到、會回錯音節候選」的其中幾種輸入：兩個聲母相連（`ㄏㄋㄧˇ`）、一聲音節漏了空白（`ㄍㄨㄥㄖㄣˊ`）、緩衝中段重複或開頭的聲調符號（`bpmf.h` 的例子
     * `ㄋㄧˇˇㄏㄠˇ`）；開頭或結尾的同類輸入（`ˇㄋㄧ`、`ㄋㄧˇˇ`）也回 `false`。
     *
     * **擋不下**的同類輸入：夾在中間、字典裡沒有的單獨聲母（`bpmf.h` 的例子 `ㄏ ㄋㄧˇ`）。`ㄏ` 單獨一個在結構上合法，要查字典才知道它不是音節；也不能改用「只有 ㄓ–ㄙ
     * 能單獨成音節」的規則代替，因為 libchewing 內建字庫（`data/dict/chewing/word.csv`）有 `ㄑ`（胠）、`ㄑ˙`（啐）這類例外。超過 `bpmf.h`
     * 所述 pre-edit 上限的輸入也照樣通過：上限是 libchewing `MAX_CHI_SYMBOL_LEN`＝39，計的是 pre-edit
     * 裡的**中文字數**（約等於音節數，不是注音符號數）；達到上限後被丟掉的音節不會報錯，解碼器回的是別的音節的候選，呼叫端要自己限制長度。
     *
     * 其他不檢查的：聲韻搭配規則（如 ㄦ 不接聲母）。空字串或只有空白回傳 `false`。開頭空白與連續空白（` ㄇㄚ`、`ㄇㄚ ㄇㄚ`）不擋、照樣回
     * `true`——它們不是正規形（[normalize] 會刪掉），但解碼器把多餘空白當 no-op，結構上不影響音節切分。非正規形字元（如 `一`、`´`）一律回傳
     * `false`，先呼叫 [normalize]。
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
                isToneMark(c) || c == SPACE -> {
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
