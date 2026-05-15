package com.mjolnir.terminal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.mjolnir.terminal.databinding.FragmentTerminalBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TerminalFragment : Fragment() {

    private var _binding: FragmentTerminalBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TerminalViewModel by activityViewModels()
    private lateinit var bridge: TermuxBridge
    private var useFedora = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentTerminalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        super.onViewCreated(view, saved)
        bridge = TermuxBridge(requireContext())
        binding.runButton.setOnClickListener { runCurrentCommand() }
        binding.fedoraToggle.setOnClickListener { useFedora = !useFedora; updateToggleLabel() }
        binding.sendToAi.setOnClickListener { viewModel.sendTerminalContext(viewModel.visibleTerminalText()) }
        binding.commandInput.setOnEditorActionListener { _, _, _ -> runCurrentCommand(); true }
        updateToggleLabel()
        observeLog()
        if (!bridge.isTermuxInstalled()) showSetupHelp()
    }

    private fun updateToggleLabel() { binding.fedoraToggle.text = if (useFedora) "FEDORA" else "TERMUX" }

    private fun showSetupHelp() {
        binding.terminalOutput.text = buildString {
            append("Termux not installed.\n\n")
            append("1. Install Termux from F-Droid:\n")
            append("   https://f-droid.org/packages/com.termux/\n\n")
            append("2. Open Termux and run:\n")
            append("   mkdir -p ~/.termux\n")
            append("   echo 'allow-external-apps = true' >> ~/.termux/termux.properties\n")
            append("   termux-reload-settings\n")
            append("   pkg install proot-distro\n")
            append("   proot-distro install fedora\n\n")
            append("3. Reopen this app.\n")
        }
    }

    private fun runCurrentCommand() {
        val cmd = binding.commandInput.text?.toString()?.trim().orEmpty()
        if (cmd.isEmpty()) return
        binding.commandInput.text?.clear()
        val wrapped = if (useFedora) "proot-distro login fedora -- bash -lc ${shellQuote(cmd)}" else cmd
        val id = viewModel.startCommand(cmd)
        lifecycleScope.launch {
            val r = bridge.execute(wrapped)
            val stderrOrError = if (r.stderr.isNotBlank()) r.stderr else r.error.orEmpty()
            viewModel.finishCommand(id, r.stdout, stderrOrError, r.exitCode)
        }
    }

    private fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun observeLog() {
        lifecycleScope.launch {
            viewModel.terminalLog.collectLatest { entries ->
                binding.terminalOutput.text = entries.joinToString("\n\n", postfix = "\n") { fmt(it) }
                binding.terminalScroll.post { binding.terminalScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private fun fmt(e: TerminalEntry): String = buildString {
        append("$ ").append(e.command).append('\n')
        when {
            e.running -> append("[running...]")
            else -> {
                if (e.stdout.isNotEmpty()) append(e.stdout.trimEnd())
                if (e.stderr.isNotBlank()) { if (!endsWith('\n') && isNotEmpty()) append('\n'); append(e.stderr.trimEnd()) }
                append("\n[exit ").append(e.exitCode).append(']')
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
