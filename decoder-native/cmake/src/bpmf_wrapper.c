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
    /* Tones. Tone 1 (陰平) has no mark and therefore no entry in this table:
     * in the DaChen layout it is KEY_SPACE, committed via the
     * chewing_zuin_Check()-gated space forwarding in
     * bpmf_forward_space_if_pending() below (see also bpmf.h). */
    {0x02CA, '6'}, /* ˊ tone 2 */
    {0x02C7, '3'}, /* ˇ tone 3 */
    {0x02CB, '4'}, /* ˋ tone 4 */
    {0x02D9, '7'}, /* ˙ tone 5 (neutral) */
};
/* clang-format on */

static const size_t kBopomofoKeyCount = sizeof(kBopomofoKeys) / sizeof(kBopomofoKeys[0]);

/*
 * bpmf.h promises *candidates_out == "" (not NULL) whenever bpmf_input()
 * returns 0. Rather than enumerating which branches those are (that list has
 * already drifted once), the rule is simply: EVERY path that returns 0 before
 * a real candidate list exists points *candidates_out at this static,
 * process-lifetime empty string. Today that covers the NULL handle/zhuyin
 * pre-checks, the fail-closed branch for characters outside the mapping
 * table, and strdup("") failing under OOM — but new early-return paths must
 * follow the same rule rather than strdup("") their own copy (a strdup there
 * can itself fail and hand the caller a NULL, breaking the very promise the
 * branch exists to honour). Pointing here instead of NULL is safe because it is
 * NOT stored into handle->last_candidates (so bpmf_free()/the next
 * bpmf_input() never free()s it — only genuinely heap-allocated strings are
 * ever assigned there), and the caller must not free it either (same
 * ownership contract as the heap-backed candidates strings — see bpmf.h).
 */
static const char kEmptyCandidates[] = "";

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

/*
 * Forwards a space keystroke to libchewing IF AND ONLY IF there is currently
 * a non-empty pending phonetic (bopomofo) syllable buffer — gated on
 * chewing_zuin_Check(), which the header (capi/include/chewing.h) documents
 * as "returns 0 when true [there IS a pending phonetic pre-edit string], 1
 * when false".
 *
 * This gate is required, not optional. ASCII space is our
 * syllable-separator convention AND libchewing's DaChen KEY_SPACE, a real
 * keystroke (Bopomofo::TONE1 — see vendored cmake/libchewing/src/editor/
 * zhuyin_layout/standard.rs's SyllableEditor::key_press). But that mapping
 * is only safe to forward while the low-level SyllableEditor is actively
 * composing (editor state EnteringSyllable): there, a pending syllable gets
 * committed with the implied first tone, and an *empty* pending syllable is
 * a harmless KeyError no-op purely at the SyllableEditor level (see
 * standard.rs's own `space` unit test).
 *
 * Once that per-syllable composition is done and the editor has returned to
 * its `Entering` state, though, Space stops being routed to the
 * SyllableEditor at all — editor/mod.rs's `Entering::next()` handles
 * SYM_SPACE itself via `start_selecting_or_input_space()`, which — whenever
 * the overall composition buffer is non-empty and has a symbol under the
 * cursor to select from — transitions the WHOLE EDITOR into its Selecting
 * (candidate-window) state. That collides with bpmf_input()'s own explicit
 * chewing_cand_open() call further down (verified on-device: forwarding an
 * unconditional trailing space made bpmf_input() return 0 candidates for
 * strings whose last syllable already had an explicit tone mark, e.g.
 * "ㄋㄧˇㄏㄠˇ", by opening — and thereby double-opening — the candidate
 * window before this function's own open call runs). Gating on
 * chewing_zuin_Check() means a space here is a genuine "commit the pending
 * syllable" only when there IS one, and a true no-op (nothing forwarded to
 * the editor at all) once composition has already settled back into
 * `Entering` — exactly the separator behaviour the rest of this file
 * assumes.
 */
static void bpmf_forward_space_if_pending(ChewingContext* ctx) {
    if (chewing_zuin_Check(ctx) == 0) {
        chewing_handle_Default(ctx, ' ');
    }
}

size_t bpmf_input(void* opaque_handle, const char* zhuyin, char** candidates_out) {
    if (candidates_out == NULL) {
        return 0;
    }
    /* See bpmf.h: 0 candidates always means *candidates_out == "", never NULL. */
    *candidates_out = (char*)kEmptyCandidates;
    if (opaque_handle == NULL || zhuyin == NULL) {
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
            bpmf_forward_space_if_pending(ctx);
            continue;
        }
        int key = bopomofo_key_for(codepoint);
        if (key < 0) {
            /* Unmapped character (not bopomofo/tone) — spec says fail closed.
             * Point at the static empty string rather than strdup(""): a
             * strdup here could itself fail under OOM and hand the caller a
             * NULL, violating the "returns 0 => *candidates_out is \"\", never
             * NULL" contract this very branch is meant to honour.
             * handle->last_candidates stays NULL (freed above), so there is
             * nothing for bpmf_free/the next call to release. */
            *candidates_out = (char*)kEmptyCandidates;
            return 0;
        }
        chewing_handle_Default(ctx, key);
    }

    /*
     * Flush a still-pending syllable at the very end of the buffer: a
     * trailing syllable with an implied first tone and no following
     * separator (e.g. "ㄍㄨㄥ" with no diacritic and no trailing space) would
     * otherwise never receive its TONE1 commit keystroke. See
     * bpmf_forward_space_if_pending() for why this must stay gated the same
     * way as the mid-string case.
     */
    bpmf_forward_space_if_pending(ctx);

    size_t count = 0;
    char* joined = strdup("");
    if (joined == NULL) {
        /* OOM: *candidates_out is already the static empty string set above. */
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
