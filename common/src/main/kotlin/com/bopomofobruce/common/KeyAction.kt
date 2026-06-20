package com.bopomofobruce.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * 一個按鍵被觸發後要執行的語意動作。
 *
 * 設計刻意 **完全與 Android framework 解耦**（沒有 KeyEvent / Context / View），讓 :keyboards 與 :ime
 * 在不同抽象層共用同一份模型：
 * - :keyboards / :theme 在 Compose preview 端產生 [KeyAction]，不需要啟動 InputMethodService。
 * - :ime 收到 [KeyAction] 後再翻譯成實際的 IME 行為（commitText、sendKey...）。
 *
 * 標記為 [Serializable] 因為 [KeyData] 會被序列化（自訂鍵盤布局存 JSON）。 sealed interface 在 kotlinx.serialization
 * 會自動以 polymorphic + class-discriminator 方式編碼，子型別需各自帶 [SerialName] 以確保 JSON schema
 * 穩定（之後新增變體不會打破舊資料）。
 *
 * **新增變體請務必：**
 * 1. 加上 `@SerialName("<stable_id>")`，且不要再改。
 * 2. 在 :ime 的 dispatcher 補對應分支（when 沒覆蓋會編譯失敗，這是 sealed 的保護）。
 */
@Serializable
@JsonClassDiscriminator("type")
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
sealed interface KeyAction {
    /** 輸入一個一般字元（英文/數字/標點），通常由非注音鍵盤產生。 */
    @Serializable @SerialName("character") data class Character(val char: Char) : KeyAction

    /** 輸入一個注音符號（ㄅㄆㄇㄈ...或聲調），交給 [ZhuyinDecoder] 累積成候選詞。 */
    @Serializable @SerialName("zhuyin") data class Zhuyin(val symbol: String) : KeyAction

    /** 退格。若 composing buffer 非空優先刪 buffer，否則對 IME context 觸發 deleteSurroundingText。 */
    @Serializable @SerialName("backspace") data object Backspace : KeyAction

    /** 空白鍵；在有候選詞時通常被 :ime 解讀成「選第一個候選」。 */
    @Serializable @SerialName("space") data object Space : KeyAction

    /** Enter；在有候選詞時通常被 :ime 解讀成「直接送出 composing buffer」。 */
    @Serializable @SerialName("enter") data object Enter : KeyAction

    /** Shift：切大小寫或注音聲調。 */
    @Serializable @SerialName("shift") data object Shift : KeyAction

    /** 切換到符號鍵盤（vs 注音/字母鍵盤）。 */
    @Serializable @SerialName("symbol_toggle") data object SymbolToggle : KeyAction

    /** 切換到英文/數字鍵盤。 */
    @Serializable @SerialName("language_toggle") data object LanguageToggle : KeyAction

    /**
     * 用戶自訂動作（例如「貼上剪貼簿」、「插入常用語」），由 :ime / :settings 用 [id] 對應到實際行為。 把它做成 escape hatch，讓 :common
     * 不需要為每個新功能擴 sealed 子型別。
     */
    @Serializable @SerialName("custom") data class Custom(val id: String) : KeyAction
}
