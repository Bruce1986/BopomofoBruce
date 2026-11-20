package com.bopomofobruce.decoder

import android.content.Context
import com.bopomofobruce.decoder.nativ.getDataPath

/**
 * :decoder 的手動 DI 入口（ADR-0004：不用 Hilt）。:app 的 `BpmfApplication` 呼叫 [create] 並持有結果（ADR-0004 的
 * singleton），以 :common 的 `ZhuyinDecoder` 交給 :ime。
 *
 * 本身不快取：每次 [create] 都建立新的 native handle，由呼叫者負責 [ChewingZhuyinDecoder.close]。
 */
object DecoderModule {
    /**
     * 每次呼叫都有磁碟 I/O（確認或解壓字典、`bpmf_init()` 開字典檔），不要在 main thread 呼叫；只有第一次會從 APK assets 解到
     * cacheDir（[getDataPath]）。
     *
     * @throws java.io.IOException 字典從 assets 解到 cacheDir 失敗（讀 asset、寫檔或 rename 失敗）。
     * @throws IllegalStateException 字典目錄建立失敗，或 `bpmf_init()` 失敗。
     * @throws LinkageError `libbpmf.so`／`libbpmf_jni.so` 載入失敗或缺符號：第一次是 [UnsatisfiedLinkError]，同一
     *   process 內之後再呼叫是 [NoClassDefFoundError]（類別已初始化失敗，不會重試載入）。
     */
    fun create(context: Context): ChewingZhuyinDecoder =
        ChewingZhuyinDecoder(JniBpmfNative, getDataPath(context.applicationContext))
}
