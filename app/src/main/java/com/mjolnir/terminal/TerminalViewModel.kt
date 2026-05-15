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

data class TerminalEntry(
    val id: Long = System.nanoTime(),
    val command: String,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int? = null,
    val running: Boolean = true
)

class TerminalViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _terminalLog = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val terminalLog: StateFlow<List<TerminalEntry>> = _terminalLog.asStateFlow()

    private val _pendingTerminalContext = MutableStateFlow<String?>(null)
    val pendingTerminalContext: StateFlow<String?> = _pendingTerminalContext.asStateFlow()

    fun addMessage(m: ChatMessage) = _messages.update { it + m }

    fun appendToLastAssistantMessage(token: String) = _messages.update { list ->
        if (list.isEmpty() || list.last().role != "assistant") return@update list
        list.dropLast(1) + list.last().copy(content = list.last().content + token)
    }

    fun updateLastAssistantMessage(content: String, streaming: Boolean = true) = _messages.update { list ->
        if (list.isEmpty() || list.last().role != "assistant") return@update list
        list.dropLast(1) + list.last().copy(content = content, isStreaming = streaming)
    }

    fun finaliseAssistantMessage() = _messages.update { list ->
        if (list.isEmpty()) return@update list
        list.dropLast(1) + list.last().copy(isStreaming = false)
    }

    fun startCommand(cmd: String): Long {
        val entry = TerminalEntry(command = cmd)
        _terminalLog.update { it + entry }
        return entry.id
    }

    fun finishCommand(id: Long, stdout: String, stderr: String, exitCode: Int) =
        _terminalLog.update { list ->
            list.map { if (it.id == id) it.copy(stdout = stdout, stderr = stderr, exitCode = exitCode, running = false) else it }
        }

    fun sendTerminalContext(text: String) { _pendingTerminalContext.value = text }
    fun clearTerminalContext() { _pendingTerminalContext.value = null }

    fun conversationHistory(): List<Map<String, String>> = _messages.value
        .filter { !it.isStreaming }
        .map { mapOf("role" to it.role, "content" to it.content) }

    fun visibleTerminalText(): String = _terminalLog.value.takeLast(20).joinToString("\n\n") { e ->
        buildString {
            append("$ ").append(e.command).append('\n')
            if (e.stdout.isNotEmpty()) append(e.stdout.trimEnd())
            if (e.stderr.isNotBlank()) { if (isNotEmpty() && !endsWith('\n')) append('\n'); append(e.stderr.trimEnd()) }
        }
    }
}
