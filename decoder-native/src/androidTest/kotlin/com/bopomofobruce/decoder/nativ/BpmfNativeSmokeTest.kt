package com.bopomofobruce.decoder.nativ

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bopomofobruce.decoder.nativ.testbridge.BpmfTestBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEVPLAN W1-A on-device acceptance test: `libbpmf.so` initializes against the real chewing
 * dictionary data and returns non-empty candidates for a real bopomofo input, on a real device —
 * not the JVM unit test host.
 */
@RunWith(AndroidJUnit4::class)
class BpmfNativeSmokeTest {
    @Test
    fun bpmfInit_withExtractedDictionaryData_succeeds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dataPath = getDataPath(context)

        val handle = BpmfTestBridge.nativeTestInit(dataPath)
        try {
            assertNotEquals("bpmf_init() returned a null handle (0)", 0L, handle)
        } finally {
            BpmfTestBridge.nativeTestFree(handle)
        }
    }

    @Test
    fun bpmfInput_forNiHao_returnsNonEmptyCandidates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dataPath = getDataPath(context)

        val handle = BpmfTestBridge.nativeTestInit(dataPath)
        assertNotEquals(0L, handle)
        try {
            // "ㄋㄧˇㄏㄠˇ" = "ni3 hao3" = 你好 ("hello"). libchewing's candidate window
            // (chewing_cand_open, which bpmf_input() uses) reselects the syllable at the
            // *cursor position* — i.e. the last one typed ("hao3") — not the whole
            // auto-converted phrase; that matches how a real Zhuyin IME's candidate popup
            // behaves while still typing. The full intelligently-converted phrase ("你好")
            // is available separately via chewing_buffer_String(), which bpmf_input() does not
            // currently expose (see devlog). So this asserts DEVPLAN's literal bar — non-empty,
            // real Chinese candidates for the "hao" syllable — rather than the two-character
            // word, which this wrapper doesn't surface yet.
            val candidates = BpmfTestBridge.nativeTestInput(handle, "ㄋㄧˇㄏㄠˇ")

            assertTrue(
                "expected at least one candidate for 你好, got ${candidates.toList()}",
                candidates.isNotEmpty(),
            )
            assertTrue(
                "expected every candidate to be non-blank, got ${candidates.toList()}",
                candidates.all { it.isNotBlank() },
            )
            // A wrong-syllable regression (E2: e.g. tone-1 handling silently returning the
            // *previous* syllable's candidates) would still pass the two assertions above —
            // every candidate for a wrong syllable is still non-blank. Verified on-device: the
            // real output for this exact input is [好, 郝, 㚼, 㝀] (see devlog); pin that down so
            // a mis-wired DaChen table entry or tone regression actually turns this test red.
            assertTrue(
                "expected 好 among the candidates for ㄏㄠˇ, got ${candidates.toList()}",
                candidates.contains("好"),
            )

            // Exercise bpmf_commit() too (part of the acceptance criteria's "no leak" spirit:
            // touch every function in the same handle lifecycle the real :decoder will use).
            BpmfTestBridge.nativeTestCommit(handle, 0)
        } finally {
            BpmfTestBridge.nativeTestFree(handle)
        }
    }

    @Test
    fun bpmfInput_forUnmappedCharacters_failsClosedWithNoCandidates() {
        // Negative case for the "fail closed" contract (bpmf.h): a string containing characters
        // outside the bopomofo/tone mapping table must return zero candidates, not silently
        // fall back to whatever was previously composed. Proves the fail-closed path in
        // bpmf_wrapper.c's bopomofo_key_for() < 0 branch actually fires and actually surfaces as
        // "no candidates" through the full JNI round-trip, not just NULL-vs-"" at the C level.
        //
        // Deliberately a *mixed* valid-prefix + unmapped-suffix input ("ㄏㄠˇ" + "x"), not pure
        // garbage: an all-ASCII input like "abc" never presses a single mapped key at all, so the
        // composition stays empty and returns 0 candidates regardless of whether the fail-closed
        // check even runs — that would not actually catch a regression that deleted the
        // fail-closed branch (verified: disabling the `key < 0` early-return locally still left
        // this suite green for "abc"). "ㄏㄠˇx" has already-committed real candidates (hao3) by
        // the time the unmapped 'x' is hit, so fail-closed has to actively discard them.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dataPath = getDataPath(context)

        val handle = BpmfTestBridge.nativeTestInit(dataPath)
        assertNotEquals(0L, handle)
        try {
            val candidates = BpmfTestBridge.nativeTestInput(handle, "ㄏㄠˇx")
            assertEquals(
                "expected fail-closed (zero candidates) once an unmapped character appears, " +
                    "even with an already-valid hao3 prefix; got ${candidates.toList()}",
                0,
                candidates.size,
            )
        } finally {
            BpmfTestBridge.nativeTestFree(handle)
        }
    }

    @Test
    fun bpmfInput_forFirstToneSyllable_commitsPendingSyllableNotPreviousOne() {
        // E2 regression test: DaChen KEY_SPACE == Bopomofo::TONE1 (see vendored
        // cmake/libchewing/src/editor/zhuyin_layout/standard.rs). "ㄍㄨㄥ" (gong, first tone/陰平)
        // carries no diacritic mark at all, so it must be committed by bpmf_input()'s own
        // end-of-buffer flush rather than an explicit tone keystroke. Preceding it with an
        // already tone-marked syllable ("ㄋㄧˇ" = nǐ, third tone) that would auto-commit on its
        // own tone key reproduces the exact failure mode from the finding: before the fix, the
        // trailing "ㄍㄨㄥ" stayed an uncommitted pending buffer and the candidate window opened
        // on nǐ instead, returning nǐ's candidates (你/妳/...) with no error signal at all — every
        // candidate still non-blank, so a naive "non-empty + non-blank" assertion would not have
        // caught it.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dataPath = getDataPath(context)

        val handle = BpmfTestBridge.nativeTestInit(dataPath)
        assertNotEquals(0L, handle)
        try {
            val candidates = BpmfTestBridge.nativeTestInput(handle, "ㄋㄧˇㄍㄨㄥ")

            assertTrue(
                "expected at least one candidate for gong1, got ${candidates.toList()}",
                candidates.isNotEmpty(),
            )
            // Verified on-device: 工 is libchewing's top candidate for gong1 in this dictionary.
            assertTrue(
                "expected 工 (gong1) among the candidates, got ${candidates.toList()}",
                candidates.contains("工"),
            )
            // The silent-failure mode this test guards against returns nǐ's candidates instead —
            // assert none of the well-known nǐ characters leaked through.
            assertFalse(
                "candidates should be for gong1, not the previous syllable (nǐ); got " +
                    candidates.toList().toString(),
                candidates.any { it == "你" || it == "妳" },
            )
        } finally {
            BpmfTestBridge.nativeTestFree(handle)
        }
    }
}
