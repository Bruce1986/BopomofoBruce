/*
 * BopomofoBruce native decoder wrapper — thin C API on top of libchewing.
 *
 * This is the ONLY public surface `decoder-native` exposes (DEVPLAN W1-A).
 * `:decoder` (W2-A) is expected to JNI-bind exactly these four functions;
 * nothing else from libchewing is re-exported.
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
 * `data_path` must be a writable, absolute filesystem directory containing
 * the extracted chewing dictionary files (word.dat, tsi.dat — see
 * ChewingDataPath.kt's getDataPath()). It is used as both syspath and
 * userpath, so it must be writable (Android cacheDir, not an APK asset
 * path).
 *
 * Returns NULL on failure (e.g. dictionaries missing/corrupt).
 */
void* bpmf_init(const char* data_path);

/**
 * Feeds a complete zhuyin (bopomofo) buffer, e.g. "ㄋㄧˇㄏㄠˇ", and returns
 * the resulting candidate phrases.
 *
 * `zhuyin` is UTF-8, each syllable made of consonant/vowel bopomofo
 * characters (U+3105-U+3129) followed by an explicit tone mark
 * (U+02CA/U+02C7/U+02CB/U+02D9 for tone 2/3/4/5; first tone has no mark and
 * is not currently supported by this wrapper — see devlog). ASCII spaces in
 * `zhuyin` are treated as syllable separators and ignored (not fed to
 * libchewing as physical keystrokes).
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
