package com.feather.launcher.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.feather.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data class Available(val release: ReleaseInfo) : UpdateUiState
    data class Downloading(val release: ReleaseInfo, val progressPercent: Int?) : UpdateUiState
    data class ReadyToInstall(val release: ReleaseInfo, val apkFile: File) : UpdateUiState
    data class Error(val release: ReleaseInfo?, val message: String) : UpdateUiState
}

/**
 * Gerencia verificação, download e instalação de releases do GitHub
 * usando apenas APIs nativas (HttpURLConnection + org.json + FileProvider),
 * sem adicionar bibliotecas de rede pesadas no APK.
 */
class AppUpdateManager(private val context: Context) {

    private val updatesDir: File
        get() = File(context.cacheDir, "updates").apply { mkdirs() }

    /** Remove APKs antigos do cache para não ocupar espaço no aparelho. */
    fun cleanOldApks() {
        runCatching {
            updatesDir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * Consulta o endpoint /releases/latest do repositório no GitHub.
     * Retorna [ReleaseInfo] apenas se a tag publicada for mais recente
     * que [BuildConfig.VERSION_NAME] e contiver um asset .apk.
     */
    suspend fun checkForUpdate(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val owner = BuildConfig.GITHUB_OWNER
        val repo = BuildConfig.GITHUB_REPO
        val endpoint = "https://api.github.com/repos/$owner/$repo/releases/latest"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "FeatherLauncher/${BuildConfig.VERSION_NAME}")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
                return@withContext null
            }

            val tagName = json.optString("tag_name", "").trim()
            val remoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
            if (remoteVersion.isEmpty()) return@withContext null

            if (!isRemoteNewer(remoteVersion, BuildConfig.VERSION_NAME)) {
                return@withContext null
            }

            val assets = json.optJSONArray("assets") ?: return@withContext null
            var selectedUrl: String? = null
            var selectedName: String? = null
            var selectedSize = -1L

            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    // Prioriza APK release se houver mais de um asset .apk
                    if (selectedUrl == null || name.contains("release", ignoreCase = true)) {
                        selectedUrl = url
                        selectedName = name
                        selectedSize = asset.optLong("size", -1L)
                    }
                }
            }

            if (selectedUrl == null || selectedName == null) return@withContext null

            ReleaseInfo(
                tagName = tagName,
                versionName = remoteVersion,
                releaseNotes = json.optString("body", "").trim(),
                apkDownloadUrl = selectedUrl,
                apkFileName = selectedName,
                apkSizeBytes = selectedSize
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Baixa o APK da release para cacheDir/updates/ reportando progresso (0..100).
     */
    suspend fun downloadApk(
        release: ReleaseInfo,
        onProgress: (Int?) -> Unit
    ): File = withContext(Dispatchers.IO) {
        cleanOldApks()
        val safeName = release.apkFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val targetFile = File(updatesDir, safeName)

        val connection = (URL(release.apkDownloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "FeatherLauncher/${BuildConfig.VERSION_NAME}")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Falha no download (HTTP $code)")
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSizeBytes
            var downloadedBytes = 0L
            var lastReported = -1

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(percent)
                            }
                        } else if (lastReported == -1) {
                            lastReported = 0
                            onProgress(null)
                        }
                    }
                    output.flush()
                }
            }

            if (!targetFile.exists() || targetFile.length() == 0L) {
                throw IllegalStateException("Arquivo APK baixado está vazio.")
            }

            targetFile
        } catch (t: Throwable) {
            targetFile.delete()
            throw t
        } finally {
            connection.disconnect()
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownSourcesSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun buildInstallIntent(apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    companion object {
        /** Compara versões semânticas simples (ex.: "1.7.0" > "1.6.1"). */
        internal fun isRemoteNewer(remote: String, local: String): Boolean {
            val remoteParts = parseNumericParts(remote)
            val localParts = parseNumericParts(local)
            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r != l) return r > l
            }
            return false
        }

        private fun parseNumericParts(version: String): List<Int> {
            val core = version.substringBefore("-").substringBefore("+")
            return core.split(".").map { part ->
                part.filter { it.isDigit() }.toIntOrNull() ?: 0
            }
        }
    }
}
