/*
 * Thin C wrapper around libchewing's C API (capi/include/chewing.h).
 *
 * Converts a zhuyin (bopomofo) unicode string into the DaChen ("standard")
 * keyboard keystroke sequence libchewing expects, feeds it through
 * chewing_handle_Default(), and reads back the candidate list via the
 * "keyboardless" candidate window API (chewing_cand_open / _Enumerate /
 * _hasNext / _String).
 *
 * The DaChen mapping table below is copied 1:1 from libchewing's own
 * canonical implementation (vendored at decoder-native/cmake/libchewing,
 * pinned to v0.12.0) —
 * src/editor/zhuyin_layout/standard.rs `SyllableEditor::key_press` — so it
 * is guaranteed to match what chewing_handle_Default() decodes, rather than
 * being reconstructed from memory.
 */

#include "bpmf.h"

#include <chewing.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/* --- DaChen (standard) bopomofo -> ASCII keystroke table --------------- */
/* clang-format off */
typedef struct {
    uint32_t codepoint;
    int key;
} BopomofoKey;

static const BopomofoKey kBopomofoKeys[] = {
    /* Initials */
    {0x3105, '1'}, /* ㄅ B  */ {0x3106, 'q'}, /* ㄆ P  */
    {0x3107, 'a'}, /* ㄇ M  */ {0x3108, 'z'}, /* ㄈ F  */
    {0x3109, '2'}, /* ㄉ D  */ {0x310A, 'w'}, /* ㄊ T  */
    {0x310B, 's'}, /* ㄋ N  */ {0x310C, 'x'}, /* ㄌ L  */
    {0x310D, 'e'}, /* ㄍ G  */ {0x310E, 'd'}, /* ㄎ K  */
    {0x310F, 'c'}, /* ㄏ H  */ {0x3110, 'r'}, /* ㄐ J  */
    {0x3111, 'f'}, /* ㄑ Q  */ {0x3112, 'v'}, /* ㄒ X  */
    {0x3113, '5'}, /* ㄓ ZH */ {0x3114, 't'}, /* ㄔ CH */
    {0x3115, 'g'}, /* ㄕ SH */ {0x3116, 'b'}, /* ㄖ R  */
    {0x3117, 'y'}, /* ㄗ Z  */ {0x3118, 'h'}, /* ㄘ C  */
    {0x3119, 'n'}, /* ㄙ S  */
    /* Medials */
    {0x3127, 'u'}, /* ㄧ I  */ {0x3128, 'j'}, /* ㄨ U  */
    {0x3129, 'm'}, /* ㄩ IU */
    /* Finals */
    {0x311A, '8'}, /* ㄚ A  */ {0x311B, 'i'}, /* ㄛ O  */
    {0x311C, 'k'}, /* ㄜ E  */ {0x311D, ','}, /* ㄝ EH */
    {0x311E, '9'}, /* ㄞ AI */ {0x311F, 'o'}, /* ㄟ EI */
    {0x3120, 'l'}, /* ㄠ AU */ {0x3121, '.'}, /* ㄡ OU */
    {0x3122, '0'}, /* ㄢ AN */ {0x3123, 'p'}, /* ㄣ EN */
    {0x3124, ';'}, /* ㄤ ANG */ {0x3125, '/'}, /* ㄥ ENG */
    {0x3126, '-'}, /* ㄦ ER */
    /* Tones (tone 1 / no mark is intentionally unsupported — see bpmf.h) */
    {0x02CA, '6'}, /* ˊ tone 2 */
    {0x02C7, '3'}, /* ˇ tone 3 */
    {0x02CB, '4'}, /* ˋ tone 4 */
    {0x02D9, '7'}, /* ˙ tone 5 (neutral) */
};
/* clang-format on */

static const size_t kBopomofoKeyCount = sizeof(kBopomofoKeys) / sizeof(kBopomofoKeys[0]);

/** Decodes one UTF-8 codepoint starting at `s`; advances `*len` past it. Returns 0 on invalid/empty input. */
static uint32_t next_utf8_codepoint(const char* s, size_t* len) {
    const unsigned char* p = (const unsigned char*)s;
    if (p[0] == 0) {
        *len = 0;
        return 0;
    }
    if ((p[0] & 0x80) == 0) {
        *len = 1;
        return p[0];
    }
    if ((p[0] & 0xE0) == 0xC0 && p[1]) {
        *len = 2;
        return ((uint32_t)(p[0] & 0x1F) << 6) | (p[1] & 0x3F);
    }
    if ((p[0] & 0xF0) == 0xE0 && p[1] && p[2]) {
        *len = 3;
        return ((uint32_t)(p[0] & 0x0F) << 12) | ((uint32_t)(p[1] & 0x3F) << 6) | (p[2] & 0x3F);
    }
    if ((p[0] & 0xF8) == 0xF0 && p[1] && p[2] && p[3]) {
        *len = 4;
        return ((uint32_t)(p[0] & 0x07) << 18) | ((uint32_t)(p[1] & 0x3F) << 12) |
               ((uint32_t)(p[2] & 0x3F) << 6) | (p[3] & 0x3F);
    }
    /* Invalid lead byte: skip one byte so the caller makes forward progress. */
    *len = 1;
    return 0xFFFFFFFFu;
}

/** Looks up the DaChen keystroke for a bopomofo/tone codepoint. Returns -1 if unmapped. */
static int bopomofo_key_for(uint32_t codepoint) {
    for (size_t i = 0; i < kBopomofoKeyCount; i++) {
        if (kBopomofoKeys[i].codepoint == codepoint) {
            return kBopomofoKeys[i].key;
        }
    }
    return -1;
}

/*
 * ChewingContext is an opaque Rust struct (defined in libchewing, not under
 * our control), so there is nowhere inside it to stash the "last candidates
 * buffer" required by bpmf.h's ownership contract. Wrap it in our own
 * handle struct instead.
 */
typedef struct {
    ChewingContext* ctx;
    char* last_candidates;
} BpmfHandle;

/* --- public API ---------------------------------------------------------- */

void* bpmf_init(const char* data_path) {
    if (data_path == NULL) {
        return NULL;
    }
    /*
     * userpath (2nd arg) is intentionally NULL, not data_path: libchewing's
     * userpath is a FILE path (capi/include/chewing.h), not a directory —
     * passing our directory there silently fails to enable a user
     * dictionary at all (loader.rs dispatches on file extension; a
     * directory never matches, and editor/mod.rs swallows the resulting
     * error with `.ok()`, so nothing is logged either). Verified against
     * the vendored capi/src/io.rs: chewing_new3() treats a NULL userpath as
     * "no custom user dictionary", and editor/mod.rs's Editor::chewing()
     * then skips creating/loading one entirely — a clean, well-defined
     * no-op, not a silent failure. W1-A deliberately does not enable
     * libchewing's built-in user dictionary; personal-phrase learning is
     * W2-A's responsibility (Room-backed, per DEVPLAN). See bpmf.h.
     */
    ChewingContext* ctx = chewing_new3(data_path, NULL, "word.dat,tsi.dat", NULL, NULL);
    if (ctx == NULL) {
        return NULL;
    }
    chewing_set_ChiEngMode(ctx, CHINESE_MODE);
    chewing_set_ShapeMode(ctx, HALFSHAPE_MODE);
    chewing_set_candPerPage(ctx, MAX_SELKEY);
    chewing_set_maxChiSymbolLen(ctx, MAX_CHI_SYMBOL_LEN);

    BpmfHandle* handle = (BpmfHandle*)malloc(sizeof(BpmfHandle));
    if (handle == NULL) {
        chewing_delete(ctx);
        return NULL;
    }
    handle->ctx = ctx;
    handle->last_candidates = NULL;
    return handle;
}

size_t bpmf_input(void* opaque_handle, const char* zhuyin, char** candidates_out) {
    if (candidates_out != NULL) {
        *candidates_out = NULL;
    }
    if (opaque_handle == NULL || zhuyin == NULL || candidates_out == NULL) {
        return 0;
    }
    BpmfHandle* handle = (BpmfHandle*)opaque_handle;
    ChewingContext* ctx = handle->ctx;

    free(handle->last_candidates);
    handle->last_candidates = NULL;

    chewing_Reset(ctx);

    const char* cursor = zhuyin;
    for (;;) {
        size_t consumed = 0;
        uint32_t codepoint = next_utf8_codepoint(cursor, &consumed);
        if (consumed == 0) {
            break; /* end of string */
        }
        cursor += consumed;
        if (codepoint == ' ') {
            continue; /* syllable separator, not a real keystroke */
        }
        int key = bopomofo_key_for(codepoint);
        if (key < 0) {
            /* Unmapped character (not bopomofo/tone) — spec says fail closed. */
            handle->last_candidates = strdup("");
            *candidates_out = handle->last_candidates;
            return 0;
        }
        chewing_handle_Default(ctx, key);
    }

    size_t count = 0;
    char* joined = strdup("");
    if (joined == NULL) {
        return 0;
    }

    if (chewing_cand_open(ctx) == 0) {
        chewing_cand_Enumerate(ctx);
        while (chewing_cand_hasNext(ctx)) {
            char* candidate = chewing_cand_String(ctx);
            if (candidate == NULL) {
                break;
            }
            size_t old_len = strlen(joined);
            size_t cand_len = strlen(candidate);
            size_t sep_len = (count > 0) ? 1 : 0;
            char* grown = (char*)realloc(joined, old_len + sep_len + cand_len + 1);
            if (grown == NULL) {
                chewing_free(candidate);
                break;
            }
            joined = grown;
            if (sep_len) {
                joined[old_len] = '\n';
            }
            memcpy(joined + old_len + sep_len, candidate, cand_len + 1);
            chewing_free(candidate);
            count++;
        }
        chewing_cand_close(ctx);
    }

    handle->last_candidates = joined;
    *candidates_out = joined;
    return count;
}

void bpmf_commit(void* opaque_handle, size_t index) {
    if (opaque_handle == NULL) {
        return;
    }
    ChewingContext* ctx = ((BpmfHandle*)opaque_handle)->ctx;
    if (chewing_cand_open(ctx) == 0) {
        chewing_cand_choose_by_index(ctx, (int)index);
        chewing_cand_close(ctx);
    }
}

void bpmf_free(void* opaque_handle) {
    if (opaque_handle == NULL) {
        return;
    }
    BpmfHandle* handle = (BpmfHandle*)opaque_handle;
    chewing_delete(handle->ctx);
    free(handle->last_candidates);
    free(handle);
}
