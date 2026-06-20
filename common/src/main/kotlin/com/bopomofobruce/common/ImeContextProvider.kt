package com.bopomofobruce.common

/**
 * 一個 input field 預期接收什麼類型的內容。
 *
 * 對應 Android `EditorInfo.inputType` 但 :common 不引用 Android API；:ime 端做一次映射 (例如 `TYPE_CLASS_NUMBER` →
 * [NUMBER])。
 *
 * 為什麼需要：:keyboards 要根據 input type 決定要不要切到數字 pad，:ime 也要決定 enter key 行為 （在 PASSWORD field 通常不該觸發
 * newline）。
 */
enum class ImeInputType {
    TEXT,
    NUMBER,
    PHONE,
    URL,
    EMAIL,
    PASSWORD,
    DATE_TIME,
}

/**
 * :ime 對「目前 input field」的薄包裝，讓上層（:keyboards / :settings preview）能在不啟動真 IME 的 情況下 spike 輸入流程。
 *
 * 正式實作在 :ime（包 `InputConnection`），preview / unit test 用
 * [com.bopomofobruce.common.fakes.FakeImeContextProvider]。
 * - [currentInputType]：目前 input field 的類型；UI 可以據此調整 layout。 設成 var 由實作端決定可不可變 — 介面只承諾「可讀」。
 * - [commitText]：把 [text] 送進 input field（取代當前 composing region）。
 * - [deleteSurroundingText]：刪除游標前 [beforeLength] 個與游標後 [afterLength] 個字元。 對應
 *   `InputConnection.deleteSurroundingText`，UTF-16 code unit 計數。
 * - [sendKey]：送一個 Android KeyEvent code（用純 [Int] 而非 KeyEvent class，避免 Android API 滲透進 :common）。實作端應
 *   `Int → KeyEvent → InputConnection.sendKeyEvent`。
 */
interface ImeContextProvider {
    val currentInputType: ImeInputType

    fun commitText(text: String)

    fun deleteSurroundingText(beforeLength: Int, afterLength: Int)

    fun sendKey(keyCode: Int)
}
