package com.mjolnir.terminal

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mjolnir.terminal.databinding.ActivitySetupBinding
import kotlinx.coroutines.launch

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var prootManager: ProotManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prootManager = ProotManager(this)
        startBootstrap()
    }

    private fun startBootstrap() {
        val installer = BootstrapInstaller(prootManager) { state ->
            runOnUiThread { renderState(state) }
        }
        lifecycleScope.launch { installer.install() }
    }

    private fun renderState(state: SetupState) {
        when (state) {
            is SetupState.Downloading -> {
                binding.statusText.text = state.label
                binding.progressBar.progress = state.progress
                binding.progressBar.visibility = android.view.View.VISIBLE
            }
            is SetupState.Extracting -> {
                binding.statusText.text = "EXTRACTING FEDORA ROOTFS..."
                binding.progressBar.isIndeterminate = true
            }
            is SetupState.Ready -> launchMain()
            is SetupState.Error -> {
                binding.statusText.text = "ERROR: ${state.message}"
                binding.retryButton.visibility = android.view.View.VISIBLE
                binding.retryButton.setOnClickListener { startBootstrap() }
            }
            else -> {}
        }
    }

    private fun launchMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
