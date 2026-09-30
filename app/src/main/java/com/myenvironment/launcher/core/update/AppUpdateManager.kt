package com.myenvironment.launcher.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.myenvironment.launcher.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GitHub Releases 上の最新リリース情報
 */
data class ReleaseUpdateInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val publishedAt: String
)

/**
 * アプリ内アップデート確認・ダウンロード・インストールの状態
 */
sealed interface AppUpdateState {
    data object Idle : AppUpdateState

    data object Checking : AppUpdateState

    data class UpToDate(
        val currentVersion: String,
        val latestRelease: ReleaseUpdateInfo?,
        val checkedAtText: String
    ) : AppUpdateState

    data class UpdateAvailable(
        val currentVersion: String,
        val latestRelease: ReleaseUpdateInfo,
        val checkedAtText: String
    ) : AppUpdateState

    data class Downloading(
        val latestRelease: ReleaseUpdateInfo,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppUpdateState

    data class ReadyToInstall(
        val latestRelease: ReleaseUpdateInfo,
        val apkFilePath: String,
        val requiresInstallPermission: Boolean = false
    ) : AppUpdateState

    data class Error(
        val message: String,
        val fallbackUrl: String = GITHUB_RELEASES_PAGE_URL
    ) : AppUpdateState

    companion object {
        const val GITHUB_REPO_OWNER = "tnk962"
        const val GITHUB_REPO_NAME = "ChimeLauncher"
        const val GITHUB_LATEST_RELEASE_API =
            "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
        const val GITHUB_RELEASES_PAGE_URL =
            "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases"
    }
}

/**
 * バージョン比較・GitHub Releases JSON パースの純粋ロジック（Unit Test 可能）
 */
object AppUpdateParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * "v1.2.0" や "1.0.0-beta1" から数値配列 [1, 2, 0] を抽出する
     */
    fun parseVersionParts(rawVersion: String): List<Int> {
        val cleaned = rawVersion
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")
            .trim()
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split(".")
            .mapNotNull { it.trim().toIntOrNull() }
    }

    /**
     * latestTagName が currentVersionName より新しいバージョンかどうかを判定する
     */
    fun isNewerVersion(currentVersionName: String, latestTagName: String): Boolean {
        val currentParts = parseVersionParts(currentVersionName)
        val latestParts = parseVersionParts(latestTagName)
        if (latestParts.isEmpty()) return false
        if (currentParts.isEmpty()) return true

        val maxLen = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLen) {
            val cur = currentParts.getOrElse(i) { 0 }
            val lat = latestParts.getOrElse(i) { 0 }
            if (lat > cur) return true
            if (lat < cur) return false
        }
        return false
    }

    /**
     * GitHub Releases API (`/releases/latest`) の JSON 文字列をパースする
     */
    fun parseLatestReleaseJson(rawJson: String): ReleaseUpdateInfo? {
        return runCatching {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (tagName.isEmpty()) return null

            val versionName = tagName.removePrefix("v").removePrefix("V")
            val title = root["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: "Chime Launcher $tagName"
            val body = root["body"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: AppUpdateState.GITHUB_RELEASES_PAGE_URL
            val publishedAt = root["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty()

            val assetsArray: JsonArray = root["assets"]?.jsonArray ?: JsonArray(emptyList())
            val assetObjects: List<JsonObject> = assetsArray.mapNotNull { it as? JsonObject }

            // Release APK ("debug" を含まない .apk) を最優先し、なければ最初の .apk アセットを選択
            val preferredAsset = assetObjects.firstOrNull { obj ->
                val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                name.endsWith(".apk", ignoreCase = true) && !name.contains("debug", ignoreCase = true)
            } ?: assetObjects.firstOrNull { obj ->
                val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                name.endsWith(".apk", ignoreCase = true)
            }

            val apkDownloadUrl = preferredAsset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
            val apkFileName = preferredAsset?.get("name")?.jsonPrimitive?.contentOrNull
            val apkSizeBytes = preferredAsset?.get("size")?.jsonPrimitive?.longOrNull ?: 0L

            ReleaseUpdateInfo(
                tagName = tagName,
                versionName = versionName,
                title = title,
                releaseNotes = body,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkDownloadUrl,
                apkFileName = apkFileName,
                apkSizeBytes = apkSizeBytes,
                publishedAt = publishedAt
            )
        }.getOrNull()
    }
}

/**
 * GitHub Releases からの新バージョン検知・APKダウンロード・PackageInstaller起動を管理するマネージャー
 */
class AppUpdateManager(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    @Volatile
    private var lastCheckedAtMillis: Long = 0L

    /**
     * ホーム画面復帰時などにバックグラウンドで自動チェックする（3時間に1回までスロットリング）
     */
    fun checkForUpdatesAutoIfNeeded(nowMillis: Long = System.currentTimeMillis()) {
        val currentState = _updateState.value
        if (currentState is AppUpdateState.Checking || currentState is AppUpdateState.Downloading) {
            return
        }
        if (nowMillis - lastCheckedAtMillis < AUTO_CHECK_INTERVAL_MS && currentState !is AppUpdateState.Idle) {
            return
        }
        checkForUpdates(manual = false)
    }

    /**
     * GitHub Releases の最新バージョンを確認する
     */
    fun checkForUpdates(manual: Boolean = true) {
        val currentState = _updateState.value
        if (currentState is AppUpdateState.Downloading) return

        _updateState.value = AppUpdateState.Checking
        scope.launch {
            val result = fetchLatestReleaseFromGitHub()
            val checkedTime = SimpleDateFormat("HH:mm", Locale.JAPAN).format(Date())
            lastCheckedAtMillis = System.currentTimeMillis()

            result.fold(
                onSuccess = { release ->
                    val currentVer = BuildConfig.VERSION_NAME
                    if (AppUpdateParser.isNewerVersion(currentVer, release.tagName)) {
                        _updateState.value = AppUpdateState.UpdateAvailable(
                            currentVersion = currentVer,
                            latestRelease = release,
                            checkedAtText = checkedTime
                        )
                    } else {
                        _updateState.value = AppUpdateState.UpToDate(
                            currentVersion = currentVer,
                            latestRelease = release,
                            checkedAtText = checkedTime
                        )
                    }
                },
                onFailure = { err ->
                    if (manual || _updateState.value is AppUpdateState.Checking) {
                        _updateState.value = AppUpdateState.Error(
                            message = "アップデート確認に失敗しました: ${err.localizedMessage ?: "通信エラー"}"
                        )
                    }
                }
            )
        }
    }

    /**
     * 最新リリースのAPKをキャッシュディレクトリへダウンロードし、完了後にインストーラーを起動する
     */
    fun downloadAndInstallRelease(release: ReleaseUpdateInfo) {
        val downloadUrl = release.apkDownloadUrl
        if (downloadUrl.isNullOrBlank()) {
            openUrlInBrowser(release.htmlUrl)
            return
        }

        if (_updateState.value is AppUpdateState.Downloading) return

        scope.launch {
            _updateState.value = AppUpdateState.Downloading(
                latestRelease = release,
                progressPercent = 0,
                downloadedBytes = 0L,
                totalBytes = release.apkSizeBytes
            )

            runCatching {
                val updatesDir = File(context.cacheDir, "updates").apply {
                    if (!exists()) mkdirs()
                }
                val targetFile = File(updatesDir, "ChimeLauncher-${release.tagName}.apk")
                val tempFile = File(updatesDir, "ChimeLauncher-${release.tagName}.apk.part")
                if (tempFile.exists()) tempFile.delete()

                downloadFileWithRedirects(
                    urlStr = downloadUrl,
                    destination = tempFile,
                    expectedBytes = release.apkSizeBytes
                ) { downloaded, total ->
                    val percent = if (total > 0L) {
                        ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                    } else {
                        -1
                    }
                    _updateState.value = AppUpdateState.Downloading(
                        latestRelease = release,
                        progressPercent = percent,
                        downloadedBytes = downloaded,
                        totalBytes = total
                    )
                }

                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                targetFile
            }.fold(
                onSuccess = { apkFile ->
                    val canInstall = canRequestPackageInstalls()
                    _updateState.value = AppUpdateState.ReadyToInstall(
                        latestRelease = release,
                        apkFilePath = apkFile.absolutePath,
                        requiresInstallPermission = !canInstall
                    )
                    withContext(Dispatchers.Main) {
                        triggerPackageInstaller(apkFile)
                    }
                },
                onFailure = { err ->
                    _updateState.value = AppUpdateState.Error(
                        message = "APKのダウンロードに失敗しました: ${err.localizedMessage ?: "通信エラー"}",
                        fallbackUrl = release.htmlUrl
                    )
                }
            )
        }
    }

    /**
     * ダウンロード済みAPKのパッケージインストーラーを起動する。
     * 「不明なアプリのインストール」権限が未許可の場合は設定画面へ誘導する。
     */
    fun triggerPackageInstaller(apkFile: File) {
        if (!apkFile.exists()) {
            _updateState.value = AppUpdateState.Error("ダウンロード済みのAPKファイルが見つかりません。再度ダウンロードしてください。")
            return
        }

        if (!canRequestPackageInstalls()) {
            val current = _updateState.value
            if (current is AppUpdateState.ReadyToInstall) {
                _updateState.value = current.copy(requiresInstallPermission = true)
            }
            openUnknownSourcesSettings()
            return
        }

        val current = _updateState.value
        if (current is AppUpdateState.ReadyToInstall && current.requiresInstallPermission) {
            _updateState.value = current.copy(requiresInstallPermission = false)
        }

        runCatching {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        }.onFailure { err ->
            _updateState.value = AppUpdateState.Error(
                message = "インストーラーを起動できませんでした: ${err.localizedMessage ?: "エラー"}"
            )
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return runCatching {
            context.packageManager.canRequestPackageInstalls()
        }.getOrDefault(true)
    }

    fun openUnknownSourcesSettings() {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openUrlInBrowser(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun fetchLatestReleaseFromGitHub(): Result<ReleaseUpdateInfo> {
        return runCatching {
            val connection = (URL(AppUpdateState.GITHUB_LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ChimeLauncher/${BuildConfig.VERSION_NAME}")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("GitHub API HTTP $code")
                }
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                AppUpdateParser.parseLatestReleaseJson(body)
                    ?: throw IllegalStateException("リリース情報の解析に失敗しました")
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun downloadFileWithRedirects(
        urlStr: String,
        destination: File,
        expectedBytes: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        var currentUrl = urlStr
        var redirects = 0
        while (redirects < 5) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "ChimeLauncher/${BuildConfig.VERSION_NAME}")
            }
            val code = connection.responseCode
            if (code in listOf(
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307,
                    308
                )
            ) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("リダイレクト先URLが取得できませんでした")
                }
                currentUrl = location
                redirects++
                continue
            }

            try {
                if (code !in 200..299) {
                    throw IllegalStateException("ダウンロード HTTP $code")
                }
                val contentLength = connection.contentLengthLong.takeIf { it > 0L } ?: expectedBytes
                connection.inputStream.use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastReportedPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val percent = if (contentLength > 0L) {
                                ((downloaded * 100L) / contentLength).toInt()
                            } else {
                                -1
                            }
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(downloaded, contentLength)
                            }
                        }
                        output.flush()
                        onProgress(downloaded, contentLength)
                    }
                }
                return
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalStateException("リダイレクト回数が上限を超えました")
    }

    companion object {
        private const val AUTO_CHECK_INTERVAL_MS = 3 * 60 * 60 * 1000L // 3時間
    }
}
