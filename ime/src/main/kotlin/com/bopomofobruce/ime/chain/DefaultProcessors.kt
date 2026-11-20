package com.bopomofobruce.ime.chain

import android.view.KeyEvent
import com.bopomofobruce.common.ImeContextProvider
import com.bopomofobruce.common.InputEvent
import com.bopomofobruce.common.InputProcessor
import com.bopomofobruce.common.InputState
import com.bopomofobruce.common.KeyAction
import com.bopomofobruce.common.ProcessResult
import com.bopomofobruce.common.ZhuyinDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 預設的 processor chain，順序即優先序。每種 [KeyAction] 在 composing 空／非空時的行為：
 *
 * |輸入                                      |composing 空                  |composing 非空                           |
 * |----------------------------------------|-----------------------------|---------------------------------------|
 * |`Zhuyin(s)`                             |buffer=`s`，查候選               |buffer 尾端接 `s`，用**完整** buffer 重查候選     |
 * |`Space`                                 |上屏 `" "`                     |有候選→上屏 highlight 候選；無候選→上屏 composing 原文|
 * |`CandidateSelected(i)`                  |無候選，忽略                       |上屏第 i 個候選；i 越界則忽略（不 fall through）      |
 * |`Backspace`                             |`deleteSurroundingText(1, 0)`|只刪 buffer 最後一個符號、不碰 context            |
 * |`Enter`                                 |`sendKey(KEYCODE_ENTER)`     |上屏 composing 原文（不查候選）                  |
 * |`Shift`                                 |切換一次性大寫鎖                     |同左（只影響之後的 `Character`）                 |
 * |`Character(c)`                          |上屏 `c`（Shift 時轉大寫）           |上屏「highlight 候選（無則原文）+ c」              |
 * |`Reset` 事件                              |`decoder.reset()`、清空、放開 Shift|同左，composing **丟棄不上屏**                 |
 * |`SymbolToggle`／`LanguageToggle`／`Custom`|`Unhandled`，交給上層             |同左，composing 保留                        |
 *
 * 選中候選時會先呼叫 [ZhuyinDecoder.commit]；送出原文（Enter／無候選的 Space、Character）不呼叫，因為那不是 decoder 給的候選。 所有上屏都經由
 * [ProcessorChain] 的 `TextCommit` 處理，再由它餵 [InputEvent.TextCommitted] 回 chain。
 *
 * **長按**：processor 一律只看 `KeyPressed.key.action`。UI 層送長按時必須把 `KeyData.longPress.action` 包成新的
 * `KeyData` 再送，不能送原鍵。
 */
fun defaultProcessors(
    decoder: ZhuyinDecoder,
    context: ImeContextProvider,
    shift: ShiftLatch,
): List<InputProcessor> =
    listOf(
        ResetProcessor(decoder, shift),
        ZhuyinProcessor(decoder),
        CandidateSelectionProcessor(decoder),
        BackspaceProcessor(decoder, context),
        EnterProcessor(context),
        ShiftProcessor(shift),
        DirectInputProcessor(decoder, shift),
    )

/** 一次性 Shift：按一下讓下一個 [KeyAction.Character] 轉大寫，用掉即放開；再按一下取消。UI 可觀察 [active] 畫大寫鍵帽。 */
class ShiftLatch {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    internal fun toggle() {
        _active.value = !_active.value
    }

    /** 回傳按下時是否處於 Shift，並放開。 */
    internal fun consume(): Boolean = _active.value.also { _active.value = false }
}

private fun InputEvent.keyAction(): KeyAction? = (this as? InputEvent.KeyPressed)?.key?.action

/** 以完整 [buffer] 查候選；候選依 score 降序（穩定排序，同分保留 decoder 原順序——見 `Candidate` KDoc「:ime 會自行 sort」）。 */
private fun composingState(buffer: String, decoder: ZhuyinDecoder): InputState =
    if (buffer.isEmpty()) InputState.EMPTY
    else InputState(buffer, decoder.input(buffer).sortedByDescending { it.score }, 0)

/** highlight 中的候選文字（並回饋 decoder）；沒有候選時退回 composing 原文。 */
private fun commitHighlightedOrRaw(state: InputState, decoder: ZhuyinDecoder): String {
    val candidate = state.candidates.getOrNull(state.cursorIndex) ?: return state.composing
    decoder.commit(candidate)
    return candidate.text
}

internal class ResetProcessor(private val decoder: ZhuyinDecoder, private val shift: ShiftLatch) :
    InputProcessor {
    override fun process(event: InputEvent, state: InputState): ProcessResult {
        if (event != InputEvent.Reset) return ProcessResult.Unhandled
        decoder.reset()
        shift.consume()
        return ProcessResult.StateChanged(InputState.EMPTY)
    }
}

internal class ZhuyinProcessor(private val decoder: ZhuyinDecoder) : InputProcessor {
    override fun process(event: InputEvent, state: InputState): ProcessResult {
        val action = event.keyAction() as? KeyAction.Zhuyin ?: return ProcessResult.Unhandled
        return ProcessResult.StateChanged(composingState(state.composing + action.symbol, decoder))
    }
}

internal class CandidateSelectionProcessor(private val decoder: ZhuyinDecoder) : InputProcessor {
    override fun process(event: InputEvent, state: InputState): ProcessResult {
        if (event is InputEvent.CandidateSelected) {
            val candidate =
                state.candidates.getOrNull(event.index)
                    ?: return ProcessResult.StateChanged(state) // 過時的點擊：吃掉、不動
            decoder.commit(candidate)
            return ProcessResult.TextCommit(candidate.text, InputState.EMPTY)
        }
        if (event.keyAction() == KeyAction.Space && state.composing.isNotEmpty()) {
            return ProcessResult.TextCommit(
                commitHighlightedOrRaw(state, decoder),
                InputState.EMPTY,
            )
        }
        return ProcessResult.Unhandled
    }
}

internal class BackspaceProcessor(
    private val decoder: ZhuyinDecoder,
    private val context: ImeContextProvider,
) : InputProcessor {
    override fun process(event: InputEvent, state: InputState): ProcessResult {
        if (event.keyAction() != KeyAction.Backspace) return ProcessResult.Unhandled
        val buffer = state.composing
        if (buffer.isEmpty()) {
            // TODO(owner 裁決)：deleteSurroundingText 以 UTF-16 code unit 計數，會把 emoji（surrogate
            // pair）切半；
            // 有選取範圍時也只刪選取起點前一個字、不刪選取區。替代方案 sendKey(KEYCODE_DEL) 交給編輯器處理兩者。
            context.deleteSurroundingText(1, 0)
            return ProcessResult.StateChanged(state)
        }
        val shorter = buffer.substring(0, buffer.offsetByCodePoints(buffer.length, -1))
        return ProcessResult.StateChanged(composingState(shorter, decoder))
    }
}

internal class EnterProcessor(private val context: ImeContextProvider) : InputProcessor {
    override fun process(event: InputEvent, state: InputState): ProcessResult {
        if (event.keyAction() != KeyAction.Enter) return ProcessResult.Unhandled
        if (state.composing.isNotEmpty()) {
            return ProcessResult.TextCommit(state.composing, InputState.EMPTY)
        }
        // TODO(owner 裁決)：單行欄位的 IME action（搜尋／前往）需要 performEditorAction，:common 契約沒有；留給 W2-B2。
        context.sendKey(KeyEvent.KEYCODE_ENTER)
        return ProcessResult.StateChanged(state)
    }
}

internal class ShiftProcessor(private val shift: ShiftLatch) : InputProcessor {
    override fun process(event: InputEvent, state: InputState): ProcessResult {
        if (event.keyAction() != KeyAction.Shift) return ProcessResult.Unhandled
        shift.toggle()
        return ProcessResult.StateChanged(state)
    }
}

/**
 * 鏈尾。對 [KeyAction] 窮舉（不寫 `else`），新增變體時這個**實際會執行**的 processor 編譯失敗，強迫決定歸屬。
 *
 * TODO(owner 裁決)：`SymbolToggle`／`LanguageToggle` 的目的地鍵盤（`language_toggle` 目前沒有通用英數鍵盤可去）、`Custom` 的
 *   id 契約（`switch_to_zhuyin`／`switch_back`／`url_insert_dot_com` 見
 *   `docs/HANDOVER-W1-fixloop-20260811.md` §7； `insert_am`／`insert_pm` 見 `:keyboards` 的
 *   `datetime_standard.json`）。裁決前本包不定義任何目的地，一律回 `Unhandled` 交給上層。
 */
internal class DirectInputProcessor(
    private val decoder: ZhuyinDecoder,
    private val shift: ShiftLatch,
) : InputProcessor {
    override fun process(event: InputEvent, state: InputState): ProcessResult {
        val action = event.keyAction()
        val text =
            when (action) {
                is KeyAction.Character -> action.char.toString()
                KeyAction.Space -> " " // composing 非空的 Space 已由 CandidateSelectionProcessor 吃掉
                KeyAction.SymbolToggle,
                KeyAction.LanguageToggle,
                is KeyAction.Custom -> return ProcessResult.Unhandled // 交給上層，見 KDoc TODO
                is KeyAction.Zhuyin,
                KeyAction.Backspace,
                KeyAction.Enter,
                KeyAction.Shift,
                null -> return ProcessResult.Unhandled // 前面的 processor 已處理／非按鍵事件
            }
        val prefix = if (state.composing.isEmpty()) "" else commitHighlightedOrRaw(state, decoder)
        // Shift 放到 decoder.commit 之後才用掉：commit 拋例外時 Shift 仍保留，重按同一鍵還是大寫。
        val shifted = action is KeyAction.Character && shift.consume()
        return ProcessResult.TextCommit(
            prefix + (if (shifted) text.uppercase() else text),
            InputState.EMPTY,
        )
    }
}
