/*
 * BopomofoBruce native decoder wrapper — thin C API on top of libchewing.
 *
 * This is the ONLY public surface `decoder-native` exposes (DEVPLAN W1-A).
 * `:decoder` (W2-A) is expected to JNI-bind exactly these four functions;
 * nothing else from libchewing is re-exported.
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
 *   - bpmf_input() writes a single heap-allocated, NUL-terminated UTF-8
 *     string into *candidates_out: candidate phrases joined by '\n', in
 *     libchewing's candidate order (best first). The returned pointer is
 *     owned by the handle and stays valid until the NEXT bpmf_input() call
 *     on the same handle, or until bpmf_free() — the caller must NOT free()
 *     it directly (mirrors how libchewing itself owns `chewing_cand_String`
 *     output; avoids a 5th "free string" function the DEVPLAN spec doesn't
 *     ask for). If there are zero candidates, *candidates_out is set to an
 *     empty string ("") and 0 is returned.
 *   - bpmf_commit() selects candidate `index` (0-based, matching the order
 *     bpmf_input returned) and commits it into libchewing's internal
 *     composition state, then closes the candidate window.
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
 * Returns NULL on failure (e.g. dictionaries missing/corrupt).
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
 * *candidates_out (see ownership contract above). Returns 0 (with
 * *candidates_out == "") if `handle`/`zhuyin`/`candidates_out` is NULL, if
 * `zhuyin` contains a character outside the mapping table, or if
 * libchewing produced no candidates.
 */
size_t bpmf_input(void* handle, const char* zhuyin, char** candidates_out);

/** Commits the candidate at `index` (0-based, from the last bpmf_input()). */
void bpmf_commit(void* handle, size_t index);

/** Releases a handle created by bpmf_init(). Safe to call with NULL. */
void bpmf_free(void* handle);

#ifdef __cplusplus
}
#endif

#endif  // BOPOMOFOBRUCE_BPMF_H
