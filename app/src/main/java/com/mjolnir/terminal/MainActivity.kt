package com.mjolnir.terminal

import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.mjolnir.terminal.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var dragStartY = 0f
    private var terminalStartHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.terminal_container, TerminalFragment())
                replace(R.id.claude_container, ClaudeFragment())
            }
        }
        setupDividerDrag()
    }

    private fun setupDividerDrag() {
        binding.divider.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartY = event.rawY
                    terminalStartHeight = binding.terminalContainer.height
                    true
                }
                MotionEvent.ACTION_MOVE -> { applyDividerDrag((event.rawY - dragStartY).toInt()); true }
                MotionEvent.ACTION_UP -> { v.performClick(); true }
                else -> false
            }
        }
    }

    private fun applyDividerDrag(delta: Int) {
        val totalHeight = binding.root.height - binding.divider.height
        val newHeight = (terminalStartHeight + delta).coerceIn(
            (totalHeight * 0.2f).toInt(), (totalHeight * 0.8f).toInt()
        )
        binding.terminalContainer.layoutParams =
            binding.terminalContainer.layoutParams.apply { height = newHeight }
        binding.terminalContainer.requestLayout()
    }
}
