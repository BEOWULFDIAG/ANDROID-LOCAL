package com.mjolnir.terminal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.mjolnir.terminal.databinding.FragmentTerminalBinding
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalViewClient

class TerminalFragment : Fragment() {

    private var _binding: FragmentTerminalBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TerminalViewModel by activityViewModels()
    private var session: TerminalSession? = null
    private lateinit var prootManager: ProotManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentTerminalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prootManager = ProotManager(requireContext())
        setupTerminalView()
        setupKeyboardBar()
        if (prootManager.isReady()) startSession() else showWaiting()
    }

    private fun setupTerminalView() {
        binding.terminalView.setTerminalViewClient(buildViewClient())
        binding.terminalView.setOnClickListener { showKeyboard() }
    }

    private fun startSession() {
        prootManager.prepareProotBinary()
        val cmd = prootManager.buildCommand()
        session = TerminalSession(
            cmd[0], "/root", cmd.drop(1).toTypedArray(),
            prootManager.buildEnv(), 24, 80, buildSessionClient()
        ).also { binding.terminalView.attachSession(it) }
        binding.waitingText.visibility = View.GONE
    }

    private fun showWaiting() {
        binding.waitingText.visibility = View.VISIBLE
        binding.waitingText.text = "WAITING FOR BOOTSTRAP..."
    }

    fun onBootstrapComplete() {
        binding.waitingText.visibility = View.GONE
        startSession()
    }

    fun captureVisibleOutput(): String =
        session?.emulator?.let { emulator ->
            val sb = StringBuilder()
            val screen = emulator.screen
            for (row in 0 until screen.activeRows) {
                sb.appendLine(screen.getSelectedText(0, row, emulator.mColumns, row).trimEnd())
            }
            sb.toString().trimEnd()
        } ?: ""

    private fun setupKeyboardBar() {
        val keys = listOf("ESC", "TAB", "CTRL", "ALT", "↑", "↓", "←", "→", "→ AI")
        keys.forEach { key ->
            val btn = layoutInflater.inflate(R.layout.key_button, binding.keyboardBar, false)
            (btn as android.widget.Button).apply {
                text = key
                setOnClickListener { handleSpecialKey(key) }
            }
            binding.keyboardBar.addView(btn)
        }
    }

    private fun handleSpecialKey(key: String) {
        when (key) {
            "→ AI" -> viewModel.sendTerminalContext(captureVisibleOutput())
            "ESC"  -> session?.write(byteArrayOf(27))
            "TAB"  -> session?.write(byteArrayOf(9))
            "CTRL" -> binding.terminalView.toggleControlKey()
            "ALT"  -> binding.terminalView.toggleAltKey()
            "↑"    -> session?.write(byteArrayOf(27, '['.code.toByte(), 'A'.code.toByte()))
            "↓"    -> session?.write(byteArrayOf(27, '['.code.toByte(), 'B'.code.toByte()))
            "←"    -> session?.write(byteArrayOf(27, '['.code.toByte(), 'D'.code.toByte()))
            "→"    -> session?.write(byteArrayOf(27, '['.code.toByte(), 'C'.code.toByte()))
        }
    }

    private fun showKeyboard() {
        binding.terminalView.requestFocus()
        requireContext().getSystemService<InputMethodManager>()
            ?.showSoftInput(binding.terminalView, 0)
    }

    private fun buildSessionClient() = object : TerminalSessionClient {
        override fun onTextChanged(s: TerminalSession) = binding.terminalView.onScreenUpdated()
        override fun onTitleChanged(s: TerminalSession) {}
        override fun onSessionFinished(s: TerminalSession) { showWaiting() }
        override fun onCopyTextToClipboard(s: TerminalSession, t: String) {}
        override fun onPasteTextFromClipboard(s: TerminalSession?) {}
        override fun onBell(s: TerminalSession) {}
        override fun onColorsChanged(s: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun setTerminalShellPid(s: TerminalSession, pid: Int) {}
    }

    private fun buildViewClient() = object : TerminalViewClient {
        override fun onScale(scale: Float) = scale
        override fun onSingleTapUp(e: android.view.MotionEvent?) { showKeyboard() }
        override fun shouldBackButtonBeSentToTerminal() = true
        override fun copyModeChanged(copyMode: Boolean) {}
        override fun onKeyDown(k: Int, e: android.view.KeyEvent?, s: TerminalSession?) = false
        override fun onKeyUp(k: Int, e: android.view.KeyEvent?) = false
        override fun onLongPress(e: android.view.MotionEvent?) = false
        override fun readControlKey() = false
        override fun readAltKey() = false
        override fun readFnKey() = false
        override fun readShiftKey() = false
        override fun onCodePoint(cp: Int, ctrl: Boolean, alt: Boolean) = false
        override fun onEmulatorSet() {}
        override fun logError(t: String, m: String) {}
        override fun logWarn(t: String, m: String) {}
        override fun logInfo(t: String, m: String) {}
        override fun logDebug(t: String, m: String) {}
        override fun logVerbose(t: String, m: String) {}
        override fun logStackTraceWithMessage(t: String, m: String, e: Exception?) {}
        override fun logStackTrace(t: String, e: Exception?) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        session?.finishIfRunning()
        _binding = null
    }
}
