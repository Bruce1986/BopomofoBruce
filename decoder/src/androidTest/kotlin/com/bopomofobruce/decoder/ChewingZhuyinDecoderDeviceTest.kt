package com.bopomofobruce.decoder

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 走正式路徑（DecoderModule → libbpmf_jni.so → dlopen libbpmf.so）；期望值取自 BpmfNativeSmokeTest 的實機結果。 */
@RunWith(AndroidJUnit4::class)
class ChewingZhuyinDecoderDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun niHaoReturnsCandidatesOfTheLastSyllable() {
        DecoderModule.create(context).use { decoder ->
            val texts = decoder.input("ㄋㄧˇㄏㄠˇ").map { it.text }
            assertTrue("expected 好 in $texts", "好" in texts)
            // glue 若把結尾 NUL 也複製進 byte array，最後一個候選會變成 "㝀\u0000"；好排第一，上一行抓不到。
            assertTrue("control char in $texts", texts.all { t -> t.none { it.isISOControl() } })
        }
    }

    @Test
    fun incompleteSyllableAndUnmappedInputReturnNothing() {
        DecoderModule.create(context).use { decoder ->
            assertEquals(emptyList<Any>(), decoder.input("ㄋㄧˇㄏ"))
            // 都帶合法前綴：純亂碼本來就湊不出候選，抓不到 fail-closed 被拿掉。
            assertEquals(emptyList<Any>(), decoder.input("ㄏㄠˇx"))
            // U+0000 經 GetStringUTFChars 變 C0 80，要被拒絕而不是在 NUL 處截成 "ㄋㄧˇ"。
            assertEquals(emptyList<Any>(), decoder.input("ㄋㄧˇ\u0000"))
        }
    }

    @Test
    fun commitResetAndCloseDoNotCrash() {
        val decoder = DecoderModule.create(context)
        val first = decoder.input("ㄋㄧˇ").first()
        decoder.commit(first)
        decoder.reset()
        assertTrue(decoder.input("ㄋㄧˇ").isNotEmpty())
        decoder.close()
        decoder.close()
        assertEquals(emptyList<Any>(), decoder.input("ㄋㄧˇ"))
    }
}
