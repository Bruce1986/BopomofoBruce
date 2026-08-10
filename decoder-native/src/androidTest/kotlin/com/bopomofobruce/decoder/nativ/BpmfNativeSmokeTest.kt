package com.bopomofobruce.decoder.nativ

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bopomofobruce.decoder.nativ.testbridge.BpmfTestBridge
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

            // Exercise bpmf_commit() too (part of the acceptance criteria's "no leak" spirit:
            // touch every function in the same handle lifecycle the real :decoder will use).
            BpmfTestBridge.nativeTestCommit(handle, 0)
        } finally {
            BpmfTestBridge.nativeTestFree(handle)
        }
    }

    @Test
    fun bpmfFree_isSafeToCallOnFreshHandleAndDoesNotCrash() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dataPath = getDataPath(context)

        val handle = BpmfTestBridge.nativeTestInit(dataPath)
        BpmfTestBridge.nativeTestFree(handle)
        // No assertion beyond "did not crash the process" — bpmf_free() double-free protection
        // is exercised by not calling it twice here; the leak-detection side of "無 leak" is
        // verified externally (see devlog) since neither LeakCanary nor a native memory
        // sanitizer is wired into this module's connectedAndroidTest yet.
    }
}
