package com.mjolnir.terminal

import android.content.Context
import java.io.File

private const val PROOT_BINARY = "proot-arm64"
private const val ROOTFS_DIR = "fedora-rootfs"

data class ProotPaths(
    val proot: File,
    val rootfs: File,
    val home: File
)

class ProotManager(private val context: Context) {

    val paths: ProotPaths by lazy {
        val files = context.filesDir
        ProotPaths(
            proot = File(files, PROOT_BINARY),
            rootfs = File(files, ROOTFS_DIR),
            home = File(files, "$ROOTFS_DIR/root")
        )
    }

    fun isReady(): Boolean = paths.proot.exists() && paths.rootfs.exists() &&
            File(paths.rootfs, "bin/bash").exists()

    fun buildCommand(): Array<String> = arrayOf(
        paths.proot.absolutePath,
        "--rootfs=${paths.rootfs.absolutePath}",
        "--bind=/dev", "--bind=/proc", "--bind=/sys",
        "--bind=/dev/urandom:/dev/random",
        "-0",
        "--change-id=0:0",
        "--kill-on-exit",
        "/bin/bash", "--login"
    )

    fun buildEnv(): Array<String> = arrayOf(
        "HOME=/root",
        "TERM=xterm-256color",
        "LANG=en_US.UTF-8",
        "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "SHELL=/bin/bash",
        "USER=root",
        "LOGNAME=root"
    )

    fun prepareProotBinary() {
        if (paths.proot.exists() && paths.proot.canExecute()) return
        context.assets.open(PROOT_BINARY).use { input ->
            paths.proot.outputStream().use { output -> input.copyTo(output) }
        }
        paths.proot.setExecutable(true, false)
    }

    fun cleanRootfs() {
        paths.rootfs.deleteRecursively()
    }
}
