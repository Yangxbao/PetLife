package com.petlife.app.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel

/**
 * 一次性 UI 事件总线（Snackbar 等提示用）。
 *
 * 用 Channel 而非 StateFlow：
 *  - 每条消息只被消费一次，页面重组/返回不会重复弹出旧消息
 *  - 页面不在前台时消息不堆积（DROP_OLDEST），避免"延迟出现/常驻不消失"
 */
class MessageBus {

    private val _events = Channel<String>(capacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val events: Flow<String> = _events.receiveAsFlow()

    fun post(message: String) {
        _events.trySend(message)
    }
}
