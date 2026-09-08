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
#include <unistd.h>

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
 * returns 0. What is NOT promised is which "" this is — this static buffer,
 * or a heap-allocated strdup("")/realloc() chain owned by the handle. Do not
 * try to enumerate here which code paths produce which (this comment has
 * already drifted out of sync with the code four times); read bpmf_input()
 * itself for that. The one fact that matters, and that stays true no matter
 * how the branches inside bpmf_input() change, is this:
 *
 *   *candidates_out points at this static buffer if and only if it was NOT
 *   also stored into handle->last_candidates. Check that assignment, not
 *   the return value or any inferred "reason", to know which one you have.
 *
 * Ownership consequence, same for both: the caller must never write to or
 * free() what it receives (see bpmf.h). Only the difference is who is
 * responsible for eventually free()ing it — bpmf_free()/the next
 * bpmf_input() call free() handle->last_candidates when it is heap-backed,
 * and never touch this static buffer.
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
    /* How many candidates the last bpmf_input() on this handle returned.
     * bpmf_commit() uses it as the upper bound for `index`; without it the
     * only thing standing between a caller-supplied index and libchewing is
     * libchewing's own internal bounds check, and a caller could not tell a
     * rejected index apart from an accepted one. */
    size_t last_candidate_count;
} BpmfHandle;

/* --- public API ---------------------------------------------------------- */

/*
 * Checks that `dir`/`filename` exists and is readable, without assuming any
 * bound on `dir`'s length (the caller-supplied data_path is not under this
 * file's control). Returns 0 (not readable, or OOM building the path — fails
 * closed) or 1.
 */
static int file_is_readable(const char* dir, const char* filename) {
    size_t dir_len = strlen(dir);
    size_t name_len = strlen(filename);
    /* dir + '/' + filename + '\0' */
    char* path = (char*)malloc(dir_len + 1 + name_len + 1);
    if (path == NULL) {
        return 0;
    }
    memcpy(path, dir, dir_len);
    path[dir_len] = '/';
    memcpy(path + dir_len + 1, filename, name_len + 1);
    int ok = (access(path, R_OK) == 0);
    free(path);
    return ok;
}

void* bpmf_init(const char* data_path) {
    if (data_path == NULL) {
        return NULL;
    }
    /*
     * Self-check (see bpmf.h "Returns NULL on failure (e.g. dictionaries
     * missing/corrupt)"): chewing_new3() itself NEVER returns NULL for that
     * case — verified against the vendored capi/src/io.rs, whose only return
     * statement is Box::into_raw(context), and against editor/mod.rs's
     * Editor::chewing(), which returns a plain Editor (not a Result) and, if
     * AssetLoader::load() can't find/parse any system dictionary, silently
     * falls back to a built-in "mini" dictionary rather than failing. Left
     * unchecked, a missing/corrupt data_path would hand the caller a "valid"
     * handle whose candidates come from that tiny fallback dictionary instead
     * of the real word.dat/tsi.dat — exactly the W2-A failure mode this
     * finding flagged (no way to tell "no candidates for this input" apart
     * from "the dictionary never loaded").
     *
     * IMPORTANT / do not "simplify" this back to a candidate-count probe: an
     * earlier version of this fix tried exactly that (feed a well-known
     * syllable through chewing_cand_open()/_Enumerate() and require >=1
     * candidate). It was WRONG and caught by this file's own on-device test
     * (bpmfInit_withMissingDictionaryData_returnsNullHandle): libchewing's
     * built-in "mini" fallback dictionary is not empty — verified on-device
     * it returns real candidates for common syllables (e.g. 2 candidates
     * [好, 郝] for hao3, 14 for gong1, vs. the real dictionary's 4 and more),
     * so a "does hasNext() return true" check cannot tell "real dictionary
     * loaded" apart from "silently fell back to mini". A count-threshold
     * probe was considered and rejected too: the real/mini candidate-count
     * gap is real but data-version-dependent (varies from +2 to +12 across
     * several probe syllables measured on-device), so any fixed threshold
     * would be fragile against future word.dat/tsi.dat updates. A direct,
     * deterministic file-existence check on the two files `bpmf.h` already
     * documents `data_path` as requiring (word.dat, tsi.dat) has none of
     * these problems. This only detects "missing", not "present but
     * corrupt" — see the known-gap note in the devlog; chewing_new3() falls
     * back to the same mini dictionary for corrupt files, which this check
     * does not distinguish from a real one either.
     */
    if (!file_is_readable(data_path, "word.dat") || !file_is_readable(data_path, "tsi.dat")) {
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
    handle->last_candidate_count = 0;
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
    handle->last_candidate_count = 0;

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
    handle->last_candidate_count = count;
    *candidates_out = joined;
    return count;
}

void bpmf_commit(void* opaque_handle, size_t index) {
    if (opaque_handle == NULL) {
        return;
    }
    BpmfHandle* handle = (BpmfHandle*)opaque_handle;
    /* Reject out-of-range indices here rather than forwarding them to
     * libchewing. Two reasons this check is not redundant with libchewing's
     * own: (a) `index` is size_t but chewing_cand_choose_by_index() takes an
     * int, so a caller that sends a negative value through a signed JNI jint
     * arrives here as a huge size_t and the (int) cast wraps it straight back
     * to a negative number — the cast, not the value, is what libchewing
     * would see; (b) the enumeration bpmf_input() performed is the only place
     * that knows how many candidates the caller was actually offered, and
     * that number is not recoverable from the context alone. Out of range is
     * a no-op, matching the "invalid input fails closed" behaviour the rest
     * of this API already has (see bpmf_input()'s unmapped-character path). */
    if (index >= handle->last_candidate_count) {
        return;
    }
    ChewingContext* ctx = handle->ctx;
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
