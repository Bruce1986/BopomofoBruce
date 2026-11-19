package com.bopomofobruce.decoder

import android.content.Context
import com.bopomofobruce.decoder.nativ.getDataPath

/**
 * :decoder 的手動 DI 入口（ADR-0004：不用 Hilt）。:app 呼叫 [create] 後把結果交給 :ime。
 *
 * 不做 singleton：[ChewingZhuyinDecoder] 持有要 [ChewingZhuyinDecoder.close] 的 native handle，誰建立誰釋放。
 */
object DecoderModule {
    /**
     * 第一次呼叫會把字典從 APK assets 解到 cacheDir（[getDataPath]），有磁碟 I/O，不要在 main thread 呼叫。
     *
     * @throws IllegalStateException 字典初始化失敗。
     * @throws UnsatisfiedLinkError `libbpmf.so`／`libbpmf_jni.so` 載入失敗或缺符號。
     */
    fun create(context: Context): ChewingZhuyinDecoder =
        ChewingZhuyinDecoder(JniBpmfNative, getDataPath(context.applicationContext))
}
