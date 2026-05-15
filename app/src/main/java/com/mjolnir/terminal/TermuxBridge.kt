package com.mjolnir.terminal

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class CommandResult(val stdout: String, val stderr: String, val exitCode: Int, val error: String? = null)

class TermuxBridge(private val context: Context) {

    fun isTermuxInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo("com.termux", 0)
    }.isSuccess

    suspend fun execute(command: String, workdir: String = TERMUX_HOME): CommandResult =
        suspendCancellableCoroutine { cont ->
            val action = "com.mjolnir.terminal.CMD_RESULT_${System.nanoTime()}"
            lateinit var receiver: BroadcastReceiver
            receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    runCatching { context.unregisterReceiver(receiver) }
                    val b = intent.getBundleExtra("result")
                    cont.resume(CommandResult(
                        stdout = b?.getString("stdout").orEmpty(),
                        stderr = b?.getString("stderr").orEmpty(),
                        exitCode = b?.getInt("exitCode", -1) ?: -1,
                        error = b?.getString("errmsg")
                    ))
                }
            }
            registerReceiver(receiver, action)
            cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
            launch(command, workdir, action) { error ->
                runCatching { context.unregisterReceiver(receiver) }
                cont.resume(CommandResult("", error, -1, "launch_error"))
            }
        }

    private fun registerReceiver(receiver: BroadcastReceiver, action: String) {
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
    }

    private fun launch(command: String, workdir: String, resultAction: String, onError: (String) -> Unit) {
        val pi = PendingIntent.getBroadcast(
            context, 0,
            Intent(resultAction).setPackage(context.packageName),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val intent = Intent().apply {
            component = ComponentName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", workdir)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi)
        }
        runCatching { context.startService(intent) }
            .onFailure { onError(it.message ?: "Failed to launch Termux service") }
    }

    companion object {
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
    }
}
