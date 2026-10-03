package com.myenvironment.launcher.core.update

import android.content.Context
import android.content.pm.PackageManager
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
import kotlinx.serialization.json.booleanOrNull
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
    val publishedAt: String,
    val companionDownloadUrl: String? = null,
    val companionFileName: String? = null,
    val companionSizeBytes: Long = 0L
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

    data class InstalledAhead(
        val currentVersion: String,
        val latestRelease: ReleaseUpdateInfo,
        val checkedAtText: String
    ) : AppUpdateState

    data class ReleaseUnavailable(
        val currentVersion: String,
        val latestRelease: ReleaseUpdateInfo,
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
        val requiresInstallPermission: Boolean = false,
        val companionFilePath: String? = null
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

    private data class Version(val parts: List<Int>, val preview: List<String>)
    private val versionPattern = Regex("""^[vV]?(\d+(?:\.\d+)*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$""")

    private fun version(raw: String): Version? {
        val match = versionPattern.matchEntire(raw.trim()) ?: return null
        val parts = match.groupValues[1].split(".").map { it.toIntOrNull() ?: return null }
        val preview = match.groupValues[2].takeIf { it.isNotEmpty() }?.split(".").orEmpty()
        return Version(parts, preview)
    }

    fun parseVersionParts(rawVersion: String): List<Int> = version(rawVersion)?.parts.orEmpty()

    /** Positive means left is newer; null means either version is invalid. Build metadata is ignored. */
    fun compareVersions(left: String, right: String): Int? {
        val a = version(left) ?: return null
        val b = version(right) ?: return null
        for (i in 0 until maxOf(a.parts.size, b.parts.size)) {
            val result = a.parts.getOrElse(i) { 0 }.compareTo(b.parts.getOrElse(i) { 0 })
            if (result != 0) return result
        }
        if (a.preview.isEmpty() && b.preview.isEmpty()) return 0
        if (a.preview.isEmpty()) return 1
        if (b.preview.isEmpty()) return -1
        for (i in 0 until minOf(a.preview.size, b.preview.size)) {
            val x = a.preview[i]
            val y = b.preview[i]
            val xNumeric = x.all { it.isDigit() }
            val yNumeric = y.all { it.isDigit() }
            val result = when {
                xNumeric && yNumeric -> {
                    val nx = x.trimStart('0').ifEmpty { "0" }
                    val ny = y.trimStart('0').ifEmpty { "0" }
                    nx.length.compareTo(ny.length).takeIf { it != 0 } ?: nx.compareTo(ny)
                }
                xNumeric -> -1
                yNumeric -> 1
                else -> x.compareTo(y)
            }
            if (result != 0) return result
        }
        return a.preview.size.compareTo(b.preview.size)
    }

    fun isNewerVersion(currentVersionName: String, latestTagName: String): Boolean =
        compareVersions(latestTagName, currentVersionName)?.let { it > 0 } == true

    fun canInstallRelease(currentVersion: String, release: ReleaseUpdateInfo): Boolean =
        !release.apkDownloadUrl.isNullOrBlank() && compareVersions(release.tagName, release.versionName) == 0 &&
            compareVersions(release.versionName, currentVersion)?.let { it >= 0 } == true

    fun canInstallApk(installedVersion: String?, installedCode: Long?, apkVersion: String?, apkCode: Long): Boolean {
        if (apkVersion == null || version(apkVersion) == null || apkCode < 0) return false
        if (installedCode != null && apkCode < installedCode) return false
        if (installedVersion == null) return true
        return compareVersions(apkVersion, installedVersion)?.let { it >= 0 } == true
    }

    fun stateForRelease(currentVersion: String, release: ReleaseUpdateInfo, checkedAt: String): AppUpdateState {
        if (compareVersions(release.tagName, release.versionName) != 0) {
            return AppUpdateState.Error("リリースタグとバージョン情報が一致しません", release.htmlUrl)
        }
        val order = compareVersions(release.versionName, currentVersion)
            ?: return AppUpdateState.Error("バージョン情報を認識できません", release.htmlUrl)
        return when {
            order < 0 -> AppUpdateState.InstalledAhead(currentVersion, release, checkedAt)
            order == 0 -> AppUpdateState.UpToDate(currentVersion, release, checkedAt)
            release.apkDownloadUrl.isNullOrBlank() -> AppUpdateState.ReleaseUnavailable(currentVersion, release, checkedAt)
            else -> AppUpdateState.UpdateAvailable(currentVersion, release, checkedAt)
        }
    }

    private fun matchesVersionedApk(name: String, prefix: String, releaseVersion: String): Boolean {
        if (!name.startsWith(prefix, ignoreCase = true) || !name.endsWith(".apk", ignoreCase = true)) return false
        val assetVersion = name.substring(prefix.length).dropLast(4).removeSuffix("-debug")
        return compareVersions(assetVersion, releaseVersion) == 0
    }

    /**
     * GitHub Releases API (`/releases/latest`) の JSON 文字列をパースする
     */
    fun parseLatestReleaseJson(rawJson: String): ReleaseUpdateInfo? {
        return runCatching {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (version(tagName) == null || root["draft"]?.jsonPrimitive?.booleanOrNull == true ||
                root["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null

            val versionName = tagName.removePrefix("v").removePrefix("V")
            val title = root["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: "Chime Launcher $tagName"
            val body = root["body"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: AppUpdateState.GITHUB_RELEASES_PAGE_URL
            val publishedAt = root["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty()

            val assetsArray: JsonArray = root["assets"]?.jsonArray ?: JsonArray(emptyList())
            val assetObjects: List<JsonObject> = assetsArray.mapNotNull { it as? JsonObject }

            // Never install the separate Companion as an update to the launcher.
            val launcherAssets = assetObjects.filter { obj ->
                val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                matchesVersionedApk(name, "ChimeLauncher-", versionName) &&
                    !obj["browser_download_url"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
            }
            val preferredAsset = launcherAssets.firstOrNull { obj ->
                val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                !name.contains("debug", ignoreCase = true)
            } ?: launcherAssets.firstOrNull()

            val companionAsset = assetObjects.filter { obj ->
                val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                (matchesVersionedApk(name, "GoogleDiscoverCompanion-", versionName) ||
                    matchesVersionedApk(name, "ChimeDiscoverCompanion-", versionName)) &&
                    !obj["browser_download_url"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
            }.let { candidates ->
                candidates.firstOrNull {
                    !it["name"]?.jsonPrimitive?.contentOrNull.orEmpty().contains("debug", ignoreCase = true)
                } ?: candidates.firstOrNull()
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
                publishedAt = publishedAt,
                companionDownloadUrl = companionAsset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull,
                companionFileName = companionAsset?.get("name")?.jsonPrimitive?.contentOrNull,
                companionSizeBytes = companionAsset?.get("size")?.jsonPrimitive?.longOrNull ?: 0L
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
    private val operationLock = Any()

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    @Volatile
    private var lastCheckedAtMillis: Long = 0L

    /**
     * ホーム画面復帰時などにバックグラウンドで自動チェックする（3時間に1回までスロットリング）
     */
    fun checkForUpdatesAutoIfNeeded(nowMillis: Long = System.currentTimeMillis()) {
        val currentState = _updateState.value
        if (currentState is AppUpdateState.Checking || currentState is AppUpdateState.Downloading ||
            currentState is AppUpdateState.ReadyToInstall) {
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
        synchronized(operationLock) {
            val current = _updateState.value
            if (current is AppUpdateState.Downloading || current is AppUpdateState.Checking ||
                (!manual && current is AppUpdateState.ReadyToInstall)) return
            _updateState.value = AppUpdateState.Checking
        }
        scope.launch {
            fetchLatestReleaseFromGitHub().fold(
                onSuccess = { release ->
                    lastCheckedAtMillis = System.currentTimeMillis()
                    val time = SimpleDateFormat("HH:mm", Locale.JAPAN).format(Date())
                    _updateState.value = AppUpdateParser.stateForRelease(BuildConfig.VERSION_NAME, release, time)
                },
                onFailure = { err ->
                    _updateState.value = AppUpdateState.Error(
                        "アップデート確認に失敗しました: ${err.localizedMessage ?: "通信エラー"}"
                    )
                }
            )
        }
    }

    /**
     * 最新リリースのAPKをキャッシュディレクトリへダウンロードし、完了後にインストーラーを起動する
     */
    fun downloadAndInstallRelease(requestedRelease: ReleaseUpdateInfo) {
        synchronized(operationLock) {
            if (_updateState.value is AppUpdateState.Downloading || _updateState.value is AppUpdateState.Checking) return
            _updateState.value = AppUpdateState.Checking
        }
        scope.launch {
            var selectedRelease = requestedRelease
            runCatching {
                // A button may hold old metadata. Always resolve the actual latest release before downloading.
                val release = fetchLatestReleaseFromGitHub().getOrThrow()
                selectedRelease = release
                lastCheckedAtMillis = System.currentTimeMillis()
                if (!AppUpdateParser.canInstallRelease(BuildConfig.VERSION_NAME, release)) {
                    val time = SimpleDateFormat("HH:mm", Locale.JAPAN).format(Date())
                    _updateState.value = AppUpdateParser.stateForRelease(BuildConfig.VERSION_NAME, release, time)
                    return@launch
                }
                _updateState.value = AppUpdateState.Downloading(release, 0, 0L, release.apkSizeBytes + release.companionSizeBytes)
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val totalBytes = release.apkSizeBytes + release.companionSizeBytes
                var completedBytes = 0L
                fun download(url: String, name: String, expectedBytes: Long, packageName: String, optional: Boolean = false): File? {
                    val target = File(updatesDir, name)
                    val temporary = File(updatesDir, "pending-$name")
                    try {
                        downloadFileWithRedirects(url, temporary, expectedBytes) { downloaded, _ ->
                            val progress = completedBytes + downloaded
                            _updateState.value = AppUpdateState.Downloading(
                                release,
                                if (totalBytes > 0) ((progress * 100) / totalBytes).toInt().coerceIn(0, 100) else -1,
                                progress, totalBytes
                            )
                        }
                        check(temporary.length() > 0L) { "APKが空です" }
                        check(expectedBytes <= 0L || temporary.length() == expectedBytes) { "APKのサイズが一致しません" }
                        val installable = validateUpdateApk(temporary, packageName, release.versionName)
                        completedBytes += temporary.length()
                        // An independently newer Companion must not prevent a launcher update.
                        if (optional && !installable) return null
                        check(installable) { "インストール済みより古いAPKはインストールできません" }
                        check(!target.exists() || target.delete()) { "保存済みAPKを更新できません" }
                        check(temporary.renameTo(target)) { "APKを保存できません" }
                        return target
                    } finally { temporary.delete() }
                }
                val companion = release.companionDownloadUrl?.takeIf { it.isNotBlank() }?.let {
                    download(it, "GoogleDiscoverCompanion-${release.tagName}.apk", release.companionSizeBytes,
                        "com.myenvironment.chimediscoverbridge", optional = true)
                }
                val launcher = checkNotNull(download(release.apkDownloadUrl!!, "ChimeLauncher-${release.tagName}.apk",
                    release.apkSizeBytes, context.packageName))
                launcher to companion
            }.fold(
                onSuccess = { (launcher, companion) ->
                    _updateState.value = AppUpdateState.ReadyToInstall(
                        selectedRelease, launcher.absolutePath, !canRequestPackageInstalls(), companion?.absolutePath
                    )
                    withContext(Dispatchers.Main) { triggerPackageInstaller(companion ?: launcher) }
                },
                onFailure = { err ->
                    _updateState.value = AppUpdateState.Error(
                        "APKの取得に失敗しました: ${err.localizedMessage ?: "通信エラー"}", selectedRelease.htmlUrl
                    )
                }
            )
        }
    }

    /** Validate both tag metadata and the actually installed build, including same-version downgrades. */
    private fun validateUpdateApk(file: File, packageName: String, versionName: String): Boolean {
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        check(archive?.packageName == packageName) { "APKのパッケージが一致しません" }
        check(archive?.versionName == versionName) { "APKのバージョンが一致しません" }
        val installed = try {
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) { null }
        return AppUpdateParser.canInstallApk(installed?.versionName, installed?.longVersionCode,
            archive?.versionName, archive?.longVersionCode ?: -1L)
    }

    /**
     * ダウンロード済みAPKのパッケージインストーラーを起動する。
     * 「不明なアプリのインストール」権限が未許可の場合は設定画面へ誘導する。
     */
    fun triggerPackageInstaller(apkFile: File) {
        val ready = _updateState.value as? AppUpdateState.ReadyToInstall ?: return
        val validation = runCatching {
            check(apkFile.exists()) { "ダウンロード済みAPKが見つかりません。再度ダウンロードしてください" }
            val path = apkFile.absolutePath
            val packageName = when (path) {
                ready.apkFilePath -> context.packageName
                ready.companionFilePath -> "com.myenvironment.chimediscoverbridge"
                else -> error("確認済みAPK以外はインストールできません")
            }
            check(validateUpdateApk(apkFile, packageName, ready.latestRelease.versionName)) {
                "インストール済みより古いAPKはインストールできません"
            }
        }
        if (validation.isFailure) {
            _updateState.value = AppUpdateState.Error(validation.exceptionOrNull()?.message ?: "APKの検証に失敗しました")
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
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
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
