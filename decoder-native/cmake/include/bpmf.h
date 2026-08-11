/*
 * BopomofoBruce native decoder wrapper — thin C API on top of libchewing.
 *
 * This is the ONLY public surface `decoder-native` exposes (DEVPLAN W1-A).
 * `:decoder` (W2-A) is expected to JNI-bind exactly these four functions;
 * nothing else from libchewing is re-exported — enforced (not just intended)
 * by a `--version-script` linker map (cmake/src/bpmf.map.in, applied in
 * cmake/CMakeLists.txt): `nm -D --defined-only` on the release `.so`
 * verifies exactly `bpmf_commit`/`bpmf_free`/`bpmf_init`/`bpmf_input` and
 * nothing else — no `chewing_*` symbol, no Rust-mangled dependency symbol —
 * on both shipping ABIs. See the 20260811 devlog entry (R1) for the
 * measured before/after symbol counts; before that fix, `corrosion_import_
 * crate`'s staticlib link had no visibility control and this .so re-exported
 * libchewing's entire ~130-function C API plus ~3400 Rust-mangled symbols
 * from its dependency tree.
 *
 * Thread-safety contract:
 *   - This handle is NOT thread-safe. Every call on a given handle (from
 *     bpmf_init() through bpmf_free()) must be serialised onto a single
 *     dispatcher/thread — mirrors the contract ZhuyinDecoder.kt states at
 *     the Kotlin level (common/src/main/kotlin/com/bopomofobruce/common/
 *     ZhuyinDecoder.kt: "實作不保證 thread-safe, ... serialise"). Do NOT add
 *     a mutex inside BpmfHandle to compensate — that would duplicate the
 *     caller-side serialisation instead of replacing it.
 *
 * Ownership / memory contract:
 *   - bpmf_init() returns an opaque handle owned by the caller. Pass it to
 *     every subsequent call; release it exactly once with bpmf_free().
 *   - bpmf_input() writes a NUL-terminated UTF-8 string into *candidates_out
 *     (candidate phrases joined by '\n', in libchewing's candidate order,
 *     best first). In EVERY case the caller must treat the returned pointer
 *     as READ-ONLY — never write through it, never free() it directly
 *     (mirrors how libchewing itself owns `chewing_cand_String` output;
 *     avoids a 5th "free string" function the DEVPLAN spec doesn't ask
 *     for). Its allocation and lifetime differ by how many candidates were
 *     returned, and both matter to a caller planning any in-place
 *     modification (e.g. splitting the '\n'-joined string into substrings
 *     by writing NUL bytes over the separators):
 *       - Count > 0: the pointer is a genuinely heap-allocated string owned
 *         by the handle. It stays valid until the NEXT bpmf_input() call on
 *         the same handle, or until bpmf_free() on that handle — whichever
 *         comes first.
 *       - Count == 0: *candidates_out is set to an empty string (""), but
 *         that pointer is NOT guaranteed to be heap-allocated or
 *         handle-owned — it may instead point at a `static const`,
 *         process-lifetime string shared across ALL handles and ALL calls
 *         (see bpmf_wrapper.c's kEmptyCandidates). Writing to it (even a
 *         single NUL byte, e.g. an in-place '\n'-splitting routine that
 *         doesn't special-case the empty string) is undefined behaviour —
 *         on most platforms it will SIGSEGV because `.rodata` is
 *         non-writable, but must not be relied upon; its "lifetime" is
 *         effectively the whole process, not "until the next bpmf_input()
 *         call", because it may not belong to this handle at all.
 *     The one guarantee that holds identically in both cases: the caller
 *     must never free() the returned pointer, in either case.
 *   - bpmf_commit() selects candidate `index` (0-based, matching the order
 *     bpmf_input returned) and commits it into libchewing's internal
 *     composition state, then closes the candidate window. See its own doc
 *     comment below for a known limitation on observing the committed
 *     result.
 */

#ifndef BOPOMOFOBRUCE_BPMF_H
#define BOPOMOFOBRUCE_BPMF_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Creates a decoder instance backed by libchewing.
 *
 * `data_path` must be a readable, absolute filesystem directory containing
 * the extracted chewing dictionary files (word.dat, tsi.dat — see
 * ChewingDataPath.kt's getDataPath()). It is passed to libchewing as
 * syspath only. userpath is passed as NULL: libchewing's userpath is a
 * *file* path (not a directory — see capi/include/chewing.h), and W1-A
 * deliberately does not enable libchewing's built-in user dictionary at
 * all (NULL userpath means "no user dictionary", not "use the default
 * one" — see capi/src/io.rs's chewing_new3() and editor/mod.rs's
 * Editor::chewing()). Personal-phrase learning/storage is W2-A's
 * responsibility (a Room-backed dictionary per DEVPLAN), not libchewing's.
 * `data_path` therefore only needs to be readable, not writable.
 *
 * Returns NULL on failure: `data_path == NULL`, `word.dat`/`tsi.dat` not both
 * present and readable directly under `data_path`, or malloc failure.
 *
 * IMPLEMENTATION NOTE (why this is true, not aspirational): libchewing's own
 * chewing_new3() never returns NULL for missing/corrupt dictionaries — it
 * silently falls back to a tiny built-in "mini" dictionary instead (verified
 * against the vendored capi/src/io.rs and editor/mod.rs's Editor::chewing()).
 * That fallback dictionary is NOT empty (verified on-device: it returns real
 * candidates for common syllables), so a "does this syllable produce any
 * candidates" self-check cannot detect it — bpmf_init() instead checks
 * directly, before calling into libchewing at all, that `word.dat` and
 * `tsi.dat` both exist and are readable under `data_path`. This catches the
 * "missing" half of "dictionaries missing/corrupt" deterministically. It does
 * NOT catch "present but corrupt" — a corrupt word.dat/tsi.dat that exists on
 * disk still passes this check and still falls back to libchewing's silent
 * mini dictionary; there is no public libchewing C API this wrapper can use
 * to detect that case (no dictionary-metadata/introspection function is
 * exported). This residual gap is recorded in the devlog as a known
 * limitation, not silently left undocumented.
 */
void* bpmf_init(const char* data_path);

/**
 * Feeds a complete zhuyin (bopomofo) buffer, e.g. "ㄋㄧˇㄏㄠˇ", and returns
 * the resulting candidate phrases.
 *
 * `zhuyin` is UTF-8, each syllable made of consonant/vowel bopomofo
 * characters (U+3105-U+3129) optionally followed by an explicit tone mark
 * (U+02CA/U+02C7/U+02CB/U+02D9 for tone 2/3/4/5). First tone (陰平) has no
 * diacritic in standard zhuyin orthography and is committed by an ASCII
 * space (U+0020, ' ') immediately after the syllable's bopomofo characters —
 * mirroring libchewing's own DaChen keyboard layout, where the space bar IS
 * the tone-1 key (see vendored cmake/libchewing/src/editor/zhuyin_layout/
 * standard.rs). A trailing first-tone syllable with no following character
 * at all (i.e. it's the last thing in `zhuyin`) does not need an explicit
 * trailing space either — bpmf_input() always checks for one still-pending
 * syllable at the end of the buffer and commits it the same way. A space
 * anywhere else — after a syllable that already carries an explicit tone
 * 2-5 mark, or with nothing typed yet — is a true no-op: it is only ever
 * forwarded to libchewing while a syllable is actually mid-composition, so
 * it is always safe to use purely as a visual/structural separator between
 * syllables too.
 *
 * Returns the candidate count and writes the joined string to
 * *candidates_out (see ownership contract above).
 *
 * Returns 0 with *candidates_out set to "" (never NULL) if `handle` or
 * `zhuyin` is NULL, if `zhuyin` contains a character outside the mapping
 * table, or if libchewing produced no candidates.
 *
 * If `candidates_out` itself is NULL, returns 0 without dereferencing it —
 * there is nothing to write to, so the "*candidates_out == \"\"" guarantee
 * above does not (and cannot) apply to that case.
 */
size_t bpmf_input(void* handle, const char* zhuyin, char** candidates_out);

/**
 * Commits the candidate at `index` (0-based, from the last bpmf_input()).
 *
 * KNOWN LIMITATION (see devlog "已知缺口"): this call's effect is currently
 * unobservable through this public API. bpmf_input() unconditionally calls
 * chewing_Reset() at the top of every invocation, and chewing_Reset() clears
 * both the composition buffer AND libchewing's internal commit buffer
 * (editor.clear()) — so whatever bpmf_commit() just committed is discarded
 * the moment the NEXT bpmf_input() call runs, and there is no function in
 * this 4-function API (bpmf_init/bpmf_input/bpmf_commit/bpmf_free) that
 * reads the commit buffer back out. In other words: calling bpmf_commit()
 * closes the candidate window and advances libchewing's internal state, but
 * nothing the caller can observe today reflects "this candidate was
 * committed" — a caller that wants the committed text itself must
 * accumulate it on the Kotlin/JNI side from the candidate string
 * bpmf_input() already returned, not by reading it back through this C API.
 * If W2-A needs libchewing itself to accumulate committed text across
 * bpmf_input() calls (e.g. for multi-syllable phrase composition), that
 * needs a new API (e.g. exposing chewing_buffer_String()) — out of scope
 * for W1-A.
 */
void bpmf_commit(void* handle, size_t index);

/**
 * Releases a handle created by bpmf_init(). Safe to call with NULL.
 *
 * Does NOT protect against double-free: calling this twice on the same
 * non-NULL handle is undefined behaviour (use-after-free on the second
 * call), same as calling free() twice on the same pointer. The caller must
 * guarantee bpmf_free() is called exactly once per handle returned by a
 * successful bpmf_init().
 */
void bpmf_free(void* handle);

#ifdef __cplusplus
}
#endif

#endif  // BOPOMOFOBRUCE_BPMF_H
