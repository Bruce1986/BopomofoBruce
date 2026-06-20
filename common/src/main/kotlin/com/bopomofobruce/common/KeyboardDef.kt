package com.bopomofobruce.common

/**
 * 一個鍵盤布局的定義。
 *
 * 不是 data class 因為 row 可能是動態生成（例如「最近 emoji」隨使用者狀態變動）；用 interface 留彈性。
 * - [id]：穩定識別字串，例如 `"zhuyin-4x10-portrait"` / `"symbol-page-1"`， 用於設定持久化與 theme 對應（不同主題針對特定 id
 *   可以給不同 background）。 慣例：`<layout-family>-<shape>-<orientation>`。
 * - [rows]：每個 row 是一個 [KeyData] list，row 之間從上到下排序。 List<List> 而非 2D array，因為每 row 寬度（key
 *   數）可以不同（標準注音 4x10 但 symbol page 不齊）。 採 property 而非 function：對外契約是「狀態未變時穩定且 cheap」，實作層（在
 *   :keyboards）通常會用 `by lazy` 快取，Compose recomposition 反覆讀無 overhead。
 * - [rowCount]：default 實作為 `rows.size`。
 */
interface KeyboardDef {
    val id: String

    val rows: List<List<KeyData>>

    val rowCount: Int
        get() = rows.size
}
