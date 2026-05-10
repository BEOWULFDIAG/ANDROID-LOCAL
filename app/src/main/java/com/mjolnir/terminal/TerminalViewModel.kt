package com.mjolnir.terminal

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val role: String,
    val content: String,
    val isStreaming: Boolean = false
)

sealed class SetupState {
    object Idle : SetupState()
    data class Downloading(val progress: Int, val label: String) : SetupState()
    object Extracting : SetupState()
    object Ready : SetupState()
    data class Error(val message: String) : SetupState()
}

class TerminalViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _pendingTerminalContext = MutableStateFlow<String?>(null)
    val pendingTerminalContext: StateFlow<String?> = _pendingTerminalContext.asStateFlow()

    private val _setupState = MutableStateFlow<SetupState>(SetupState.Idle)
    val setupState: StateFlow<SetupState> = _setupState.asStateFlow()

    fun addMessage(message: ChatMessage) = _messages.update { it + message }

    fun updateLastAssistantMessage(content: String, streaming: Boolean = true) =
        _messages.update { list ->
            if (list.isEmpty()) return@update list
            val last = list.last()
            if (last.role != "assistant") return@update list
            list.dropLast(1) + last.copy(content = content, isStreaming = streaming)
        }

    fun appendToLastAssistantMessage(token: String) =
        _messages.update { list ->
            if (list.isEmpty()) return@update list
            val last = list.last()
            if (last.role != "assistant") return@update list
            list.dropLast(1) + last.copy(content = last.content + token)
        }

    fun finaliseAssistantMessage() =
        _messages.update { list ->
            if (list.isEmpty()) return@update list
            val last = list.last()
            list.dropLast(1) + last.copy(isStreaming = false)
        }

    fun sendTerminalContext(text: String) { _pendingTerminalContext.value = text }
    fun clearTerminalContext() { _pendingTerminalContext.value = null }
    fun updateSetupState(state: SetupState) { _setupState.value = state }

    fun conversationHistory(): List<Map<String, String>> =
        _messages.value
            .filter { !it.isStreaming }
            .map { mapOf("role" to it.role, "content" to it.content) }
}
