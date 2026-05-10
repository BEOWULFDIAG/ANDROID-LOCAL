package com.mjolnir.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private const val FEDORA_ROOTFS_URL =
    "https://github.com/termux/proot-distro/releases/download/v4.22.1/fedora-aarch64-pd-v4.22.1.tar.xz"

private const val PROOT_URL =
    "https://github.com/proot-me/proot/releases/download/v5.4.0/proot-v5.4.0-aarch64-static"

class BootstrapInstaller(
    private val prootManager: ProotManager,
    private val onProgress: (SetupState) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    suspend fun install() = withContext(Dispatchers.IO) {
        runCatching {
            downloadProot()
            downloadAndExtractRootfs()
            configureRootfs()
            withContext(Dispatchers.Main) { onProgress(SetupState.Ready) }
        }.onFailure { e ->
            withContext(Dispatchers.Main) { onProgress(SetupState.Error(e.message ?: "Install failed")) }
        }
    }

    private fun downloadProot() {
        if (prootManager.paths.proot.exists()) return
        onProgress(SetupState.Downloading(0, "Downloading proot binary..."))
        downloadFile(PROOT_URL, prootManager.paths.proot) { _, _ -> }
        prootManager.paths.proot.setExecutable(true, false)
    }

    private fun downloadAndExtractRootfs() {
        val tarFile = File(prootManager.paths.rootfs.parentFile, "fedora.tar.xz")
        onProgress(SetupState.Downloading(0, "Downloading Fedora rootfs..."))
        downloadFile(FEDORA_ROOTFS_URL, tarFile) { downloaded, total ->
            if (total > 0) onProgress(SetupState.Downloading((downloaded * 100 / total).toInt(), "Downloading Fedora..."))
        }
        onProgress(SetupState.Extracting)
        prootManager.paths.rootfs.mkdirs()
        extractTarXz(tarFile, prootManager.paths.rootfs)
        tarFile.delete()
    }

    private fun downloadFile(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
        val total = response.body?.contentLength() ?: -1L
        var downloaded = 0L
        response.body?.byteStream()?.use { input ->
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, total)
                }
            }
        }
    }

    private fun extractTarXz(archive: File, dest: File) {
        val pb = ProcessBuilder("tar", "-xJf", archive.absolutePath, "-C", dest.absolutePath,
            "--strip-components=1")
            .redirectErrorStream(true)
            .start()
        check(pb.waitFor() == 0) { "Extraction failed:\n${pb.inputStream.bufferedReader().readText()}" }
    }

    private fun configureRootfs() {
        val resolv = File(prootManager.paths.rootfs, "etc/resolv.conf")
        if (!resolv.exists()) resolv.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        File(prootManager.paths.rootfs, "root").mkdirs()
    }
}
