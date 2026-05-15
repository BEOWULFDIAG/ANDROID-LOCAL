package com.mjolnir.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private const val FEDORA_ROOTFS_URL =
    "https://github.com/termux/proot-distro/releases/download/v4.31.0/fedora-aarch64-pd-v4.31.0.tar.xz"

private const val PROOT_URL =
    "https://github.com/proot-me/proot-static-build/raw/master/static/proot-arm64"

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
        XZCompressorInputStream(archive.inputStream().buffered()).use { xz ->
            TarArchiveInputStream(xz).use { tar ->
                while (true) {
                    val entry = tar.nextEntry as? TarArchiveEntry ?: break
                    runCatching { writeEntry(entry, dest, tar) }
                }
            }
        }
    }

    private fun writeEntry(entry: TarArchiveEntry, dest: File, tar: TarArchiveInputStream) {
        val rel = entry.name.substringAfter('/', "")
        if (rel.isEmpty()) return
        val outFile = File(dest, rel)
        outFile.parentFile?.mkdirs()
        when {
            entry.isSymbolicLink -> writeSymlink(outFile, entry.linkName)
            entry.isLink -> writeHardlink(outFile, File(dest, entry.linkName.substringAfter('/', "")))
            entry.isDirectory -> outFile.mkdirs()
            else -> writeRegularFile(outFile, tar, entry.mode)
        }
    }

    private fun writeSymlink(target: File, linkName: String) {
        target.delete()
        runCatching { java.nio.file.Files.createSymbolicLink(target.toPath(), java.io.File(linkName).toPath()) }
    }

    private fun writeHardlink(target: File, source: File) {
        if (!source.exists()) return
        target.delete()
        runCatching { java.nio.file.Files.createLink(target.toPath(), source.toPath()) }
            .onFailure { source.copyTo(target, overwrite = true) }
    }

    private fun writeRegularFile(outFile: File, tar: TarArchiveInputStream, mode: Int) {
        outFile.outputStream().use { tar.copyTo(it) }
        if (mode and 0x49 != 0) outFile.setExecutable(true, false)
    }

    private fun configureRootfs() {
        val resolv = File(prootManager.paths.rootfs, "etc/resolv.conf")
        if (!resolv.exists()) resolv.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        File(prootManager.paths.rootfs, "root").mkdirs()
    }
}
