package com.mjolnir.terminal

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams
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

        val prootManager = ProotManager(this)
        if (!prootManager.isReady()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.terminal_container, TerminalFragment())
                replace(R.id.claude_container, ClaudeFragment())
            }
        }
        setupDividerDrag()
    }

    private fun setupDividerDrag() {
        binding.divider.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartY = event.rawY
                    terminalStartHeight = binding.terminalContainer.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = (event.rawY - dragStartY).toInt()
                    applyDividerDrag(delta)
                    true
                }
                else -> false
            }
        }
    }

    private fun applyDividerDrag(delta: Int) {
        val totalHeight = binding.root.height - binding.divider.height
        val minHeight = (totalHeight * 0.2f).toInt()
        val maxHeight = (totalHeight * 0.8f).toInt()
        val newHeight = (terminalStartHeight + delta).coerceIn(minHeight, maxHeight)
        binding.terminalContainer.layoutParams =
            binding.terminalContainer.layoutParams.apply { height = newHeight }
        binding.terminalContainer.requestLayout()
    }
}
