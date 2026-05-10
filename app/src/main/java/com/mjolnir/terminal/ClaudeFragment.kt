package com.mjolnir.terminal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mjolnir.terminal.databinding.FragmentClaudeBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ClaudeFragment : Fragment() {

    private var _binding: FragmentClaudeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TerminalViewModel by activityViewModels()
    private val adapter = MessageAdapter()
    private var claudeService: ClaudeApiService? = null
    private lateinit var keyStore: ApiKeyStore

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentClaudeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        keyStore = ApiKeyStore(requireContext())
        setupRecyclerView()
        setupInput()
        observeMessages()
        observeTerminalContext()
        initService()
    }

    private fun setupRecyclerView() {
        binding.messageList.adapter = adapter
        binding.messageList.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }
    }

    private fun setupInput() {
        binding.sendButton.setOnClickListener { sendMessage() }
        binding.inputField.setOnEditorActionListener { _, _, _ -> sendMessage(); true }
        binding.apiKeyButton.setOnClickListener { showApiKeyDialog() }
    }

    private fun sendMessage() {
        val text = binding.inputField.text?.toString()?.trim() ?: return
        if (text.isEmpty() || claudeService == null) return
        binding.inputField.text?.clear()
        viewModel.addMessage(ChatMessage(role = "user", content = text))
        viewModel.addMessage(ChatMessage(role = "assistant", content = "", isStreaming = true))
        streamResponse()
    }

    private fun streamResponse() {
        val service = claudeService ?: return
        lifecycleScope.launch {
            service.stream(
                history = viewModel.conversationHistory().dropLast(1),
                onToken = { token -> viewModel.appendToLastAssistantMessage(token); scrollToBottom() },
                onError = { err ->
                    viewModel.updateLastAssistantMessage("Error: $err", streaming = false)
                    scrollToBottom()
                }
            )
            viewModel.finaliseAssistantMessage()
            scrollToBottom()
        }
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            viewModel.messages.collectLatest { messages ->
                adapter.submitList(messages.toList())
                scrollToBottom()
            }
        }
    }

    private fun observeTerminalContext() {
        lifecycleScope.launch {
            viewModel.pendingTerminalContext.collectLatest { context ->
                context ?: return@collectLatest
                binding.inputField.setText("Terminal output:\n```\n${context.take(1500)}\n```\n")
                binding.inputField.setSelection(binding.inputField.text?.length ?: 0)
                viewModel.clearTerminalContext()
            }
        }
    }

    private fun initService() {
        val key = keyStore.load()
        if (key != null) {
            claudeService = ClaudeApiService(key)
            binding.apiKeyButton.text = "KEY ✓"
        }
    }

    private fun showApiKeyDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "sk-ant-..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("ANTHROPIC API KEY")
            .setView(input)
            .setPositiveButton("SAVE") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotEmpty()) {
                    keyStore.save(key)
                    claudeService = ClaudeApiService(key)
                    binding.apiKeyButton.text = "KEY ✓"
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun scrollToBottom() {
        val count = adapter.itemCount
        if (count > 0) binding.messageList.scrollToPosition(count - 1)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
